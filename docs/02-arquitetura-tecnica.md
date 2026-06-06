# 02 — Arquitetura técnica geral

> Como tudo se encaixa. Stack, camadas, fluxo de dados, decisões de bibliotecas, integração com o módulo nativo Android, e por que cada peça está onde está.

---

## 2.1. Visão geral em camadas

```
┌──────────────────────────────────────────────────────────────────┐
│ LAYER 1 — Apresentação (React Native + Expo)                     │
│   • Telas, navegação, componentes, animações                      │
│   • Estado UI (Zustand) e estado de servidor (TanStack Query)    │
│   • Linguagem: TypeScript                                         │
└──────────────────────────────────────────────────────────────────┘
                          ↕ Expo Modules API (TS ↔ Kotlin)
┌──────────────────────────────────────────────────────────────────┐
│ LAYER 2 — Bridge / Domínio (Expo Modules wrappers TS)            │
│   • API de alto nível para a UI: startRecording(), getSession()  │
│   • Eventos (NativeEventEmitter): onChunkSaved, onProgress       │
│   • Tipos compartilhados via TypeScript                           │
└──────────────────────────────────────────────────────────────────┘
                          ↕
┌──────────────────────────────────────────────────────────────────┐
│ LAYER 3 — Núcleo Nativo Android (Kotlin)                         │
│   • RecordingService (Foreground Service)                         │
│   • AudioPipeline (RNNoise, normalização, VAD)                   │
│   • TranscriptionEngine (whisper.cpp via JNI)                    │
│   • DiarizationEngine (sherpa-onnx)                              │
│   • LocalLLMEngine (llama.cpp via JNI)                           │
│   • CloudLLMClient (Ktor/OkHttp)                                  │
│   • Repository (SQLite via Room)                                  │
└──────────────────────────────────────────────────────────────────┘
                          ↕
┌──────────────────────────────────────────────────────────────────┐
│ LAYER 4 — Persistência                                            │
│   • SQLite (Room) — metadata: aulas, segmentos, speakers, análise │
│   • Filesystem — chunks .wav, modelos ML, exports                 │
│   • EncryptedSharedPreferences — config sensível, API keys         │
│   • Tudo em /data/data/<pkg>/ (privado ao app)                    │
└──────────────────────────────────────────────────────────────────┘
```

### Por que essa separação?

A regra de ouro: **JavaScript do React Native não toca em nada que não pode falhar**.

JS é ótimo para UI declarativa, péssimo para:
- Foreground services persistentes (RN bridge pode ser pausado).
- Manipular grandes buffers de áudio (overhead serialização).
- Rodar modelos ML pesados (single-threaded, garbage collector imprevisível).
- Chamar libs C++ (whisper.cpp, llama.cpp).

Por isso, **toda a parte crítica está em Kotlin nativo**. O JS só decora.

---

## 2.2. Stack detalhada por camada

### Layer 1 — Apresentação (TypeScript / React Native)

| Item | Escolha | Versão alvo (maio/2026) | Justificativa |
|------|---------|-------------------------|---------------|
| Framework | **Expo SDK 53+** | latest stable | Bare workflow + EAS Build. Suporte robusto a módulos nativos via Expo Modules API. |
| Linguagem | **TypeScript estrito** | 5.x | Catch de erros antes de runtime. `strict: true`. |
| Navegação | **Expo Router** | latest | File-based, type-safe, sem boilerplate React Navigation. |
| Estado UI | **Zustand** | 5.x | Simples, sem boilerplate, persistente fácil. |
| Estado servidor | **TanStack Query** | 5.x | Cache, retry, sync — usaremos para "estado nativo cached" (lista de aulas, etc). |
| Componentes | **Tamagui** ou **React Native Reusables** (shadcn-like) | latest | Componentes performáticos, theming, dark mode embutido. **Decisão pendente entre os dois — recomendo Tamagui pela performance.** |
| Forms | **React Hook Form + Zod** | latest | Validação, type-safe. |
| Internacionalização | **i18next + expo-localization** | latest | PT-BR primeiro, EN depois. |
| Ícones | **lucide-react-native** | latest | Consistência, tree-shakeable. |
| Animações | **Reanimated 3 + Moti** | latest | Animações sem cair pra JS thread. |
| Áudio playback | **expo-av** ou **react-native-audio-player** | latest | Para reprodução das gravações. |
| Tipagem ML | **Schemas Zod** | — | Validar saída de LLMs (JSON estruturado). |

### Layer 2 — Bridge (Expo Modules API)

A Expo Modules API é a forma moderna e recomendada de criar módulos nativos para Expo. Substitui o velho "Native Modules" do React Native, oferece type generation automática e melhor performance.

Estrutura do módulo nativo `aulalogger-native`:

```
modules/aulalogger-native/
├── src/
│   ├── AulalogerNativeModule.ts         # Tipos + API alto nível
│   ├── AulalogerNativeModule.types.ts   # Schemas
│   └── index.ts
├── android/
│   ├── build.gradle
│   └── src/main/java/expo/modules/aulalogger/
│       ├── AulalogerNativeModule.kt     # Pontos de entrada
│       ├── recording/
│       │   ├── RecordingService.kt
│       │   ├── RecordingSession.kt
│       │   ├── ChunkWriter.kt
│       │   └── RecordingNotification.kt
│       ├── audio/
│       │   ├── AudioPipeline.kt
│       │   ├── RNNoiseProcessor.kt
│       │   └── VadProcessor.kt
│       ├── transcription/
│       │   ├── TranscriptionEngine.kt
│       │   ├── WhisperCppBridge.kt        # JNI
│       │   └── ModelManager.kt
│       ├── diarization/
│       │   ├── DiarizationEngine.kt
│       │   ├── SherpaOnnxBridge.kt
│       │   └── SpeakerEnrollment.kt
│       ├── llm/
│       │   ├── LocalLLMEngine.kt
│       │   ├── LlamaCppBridge.kt          # JNI
│       │   └── PromptBuilder.kt
│       ├── cloud/
│       │   ├── CloudLLMClient.kt
│       │   ├── ClaudeProvider.kt
│       │   ├── OpenAIProvider.kt
│       │   └── GeminiProvider.kt
│       ├── storage/
│       │   ├── AppDatabase.kt             # Room
│       │   ├── dao/...
│       │   └── entities/...
│       ├── export/
│       │   ├── PdfExporter.kt
│       │   ├── DocxExporter.kt
│       │   └── SubtitleExporter.kt        # SRT, VTT
│       └── util/
│           ├── Crypto.kt                  # Keystore
│           ├── Logger.kt
│           └── PowerManager.kt
└── expo-module.config.json
```

### Layer 3 — Núcleo Nativo (Kotlin + JNI para C++)

| Item | Lib/Tech | Versão | Justificativa |
|------|----------|--------|---------------|
| Linguagem | **Kotlin** | 2.x | Padrão moderno Android. |
| Áudio capture | **AudioRecord (Android SDK)** | API 29+ | Mais baixo nível e controlável que MediaRecorder. Permite capturar PCM bruto. |
| Foreground service | **Android SDK (com FGS_TYPE_MICROPHONE)** | API 34 obrigatório | Tipos exigidos a partir do Android 14. |
| Coroutines | **kotlinx.coroutines** | 1.8+ | Async sem bloquear. |
| Persistência | **Room** (SQLite ORM) | 2.6+ | Type-safe, migrations, queries assíncronas. |
| Transcrição | **whisper.cpp** via JNI wrapper | latest | Implementação C++ portável de Whisper. Suporta NEON (ARM SIMD), GPU via OpenCL. |
| Diarização | **sherpa-onnx** (Kaldi/k2-fsa) | latest | Pipeline completo: VAD + segment + embedding + clustering. ONNX Runtime cross-platform. |
| LLM local | **llama.cpp** via JNI wrapper | latest | Suporta GGUF (Gemma, Phi, Llama, Qwen). NEON otimizado. |
| ML Runtime alternativo | **ONNX Runtime Mobile** | latest | Para sherpa-onnx e fallback. |
| Aceleração HW | **NNAPI delegate** | API 29+ | Quando disponível, transcrição ~2x mais rápida. |
| Denoise | **RNNoise** (Xiph) | latest | Lib C de denoise neural, leve, bem testada. |
| HTTP client | **Ktor Client** | 2.x | Para chamadas cloud LLM opcionais. |
| Crypto | **Android Keystore + Tink** | latest | Encriptar áudio at-rest e API keys. |
| PDF | **iText 7 (AGPL)** ou **PDFBox-Android** | latest | Geração de PDF. PDFBox é Apache 2.0, preferir. |
| DOCX | **POI-shadow** ou geração manual | — | Apache POI tem peso enorme. Avaliar geração manual via templates. |
| Logging | **Timber + arquivo rotativo** | latest | Para diagnóstico de campo. |

### Layer 4 — Persistência

```
/data/data/com.aulalogger.app/
├── databases/
│   └── aulalogger.db                  # SQLite via Room
├── files/
│   ├── recordings/
│   │   └── <session-uuid>/
│   │       ├── meta.json
│   │       ├── chunk-00000.wav
│   │       ├── chunk-00001.wav
│   │       └── ...
│   ├── transcripts/
│   │   └── <session-uuid>.json
│   ├── analyses/
│   │   └── <session-uuid>.json
│   ├── exports/                        # Gerados sob demanda
│   ├── models/
│   │   ├── whisper-small-q5_k_m.bin
│   │   ├── sherpa-vad.onnx
│   │   ├── sherpa-segment.onnx
│   │   ├── sherpa-embedding.onnx
│   │   └── gemma-2-2b-it-q4.gguf
│   └── logs/
└── shared_prefs/
    └── encrypted_prefs.xml             # API keys, voice fingerprint
```

---

## 2.3. Fluxo de dados — caso de uso "gravar + transcrever + analisar uma aula"

```
   Usuário aperta INICIAR
            │
            ▼
   [TS] startRecording() → módulo nativo
            │
            ▼
   [Kotlin] Inicia RecordingService (foreground)
            │
            ▼
   AudioRecord captura PCM 16-bit @ 16kHz mono
            │
            ▼
   ChunkWriter agrega 30s → grava chunk-NNNNN.wav
            │
            ▼
   Atualiza meta.json + emite evento "chunk-saved" para JS
            │
            ▼
   ... loop por 4h ...
            │
   Usuário aperta PARAR
            │
            ▼
   Service finaliza, escreve manifesto final, libera wake lock
            │
            ▼
   [Kotlin] enqueue WorkManager job: "TranscribeSession"
            │
            ▼
   AudioPipeline aplica RNNoise + normalização → chunks limpos
            │
            ▼
   TranscriptionEngine consome chunks via whisper.cpp
            │
            ▼
   Resultado: transcript.json com [{start_ms, end_ms, text, lang}, ...]
            │
            ▼
   [Kotlin] enqueue WorkManager job: "DiarizeSession"
            │
            ▼
   DiarizationEngine produz [{start_ms, end_ms, speaker_id}, ...]
            │
            ▼
   Merge transcript + diarization → transcript_final.json
            │
            ▼
   [Kotlin] (se habilitado) enqueue "AnalyzeSession"
            │
            ▼
   LocalLLMEngine OU CloudLLMClient produz analysis.json
            │
            ▼
   Tudo persistido em SQLite + filesystem
            │
            ▼
   [Kotlin] emite evento "session-ready" → JS atualiza UI
```

**Pontos importantes:**
- Cada etapa pode falhar e ser retomada (WorkManager retries automáticos).
- O usuário pode usar o app normalmente enquanto transcrição roda em background.
- Notificação persistente mostra "Transcrevendo aula de 03/05 — 47%".

---

## 2.4. Decisões de arquitetura justificadas

### D-A1. Por que Expo Modules API e não bare React Native?

- **Expo Modules** são significativamente mais ergonômicos do que escrever NativeModules manualmente.
- Type-safety: tipos TS são gerados a partir do Kotlin.
- Eventos via `EventEmitter` declarativos.
- Funciona com EAS Build (build na nuvem do Expo) e localmente.
- Custos: adiciona ~3MB ao APK final, aceitável.

### D-A2. Por que AudioRecord e não MediaRecorder?

- **MediaRecorder** é mais alto nível (encapsula compressão), mas:
  - Não dá controle sobre buffers.
  - Não permite chunking real-time (você abre, grava, fecha — não chunk).
  - Recovery em caso de crash é praticamente impossível (arquivo final só é finalizado no stop).
- **AudioRecord** dá PCM bruto, controle total. Comprimimos depois (FLAC ou Opus) ou mantemos WAV.
- **Decisão:** AudioRecord PCM 16-bit, 16kHz mono (Whisper roda nessa taxa nativamente, qualidade de fala é suficiente). Mantém WAV para ter integridade de cada chunk; opcionalmente o usuário ativa "comprimir após sessão" para FLAC ou Opus.

### D-A3. Por que WAV em vez de MP3/Opus durante gravação?

- WAV não tem compressão = nunca há "header corrompido" em chunk parcial.
- Qualquer player do mundo abre WAV.
- Whisper.cpp lê WAV nativo, sem decoding intermediário.
- Tamanho: 16kHz mono 16-bit = 32KB/s = ~115MB/h. 4h = ~460MB. Aceitável.
- Pós-sessão, opcionalmente comprime para FLAC (~50% do WAV, lossless) ou Opus (~10% do WAV, lossy mas excelente para voz).

### D-A4. Por que SQLite (Room) e não Realm/MMKV/etc?

- SQLite é universal, robusto, transacional.
- Room dá type-safety + migrations.
- MMKV é mais rápido para key-value, usaremos para preferences.
- Realm tem licenciamento confuso e overhead.

### D-A5. Por que WorkManager para tarefas pós-gravação?

- WorkManager garante execução mesmo se app for morto.
- Retries automáticos com backoff.
- Constraints (só com bateria carregando, só com Wi-Fi, etc).
- API moderna, recomendada pelo Google.

### D-A6. Por que sherpa-onnx para diarização e não pyannote direto?

- **pyannote** é state of art mas roda em PyTorch, não viável em Android sem export complexo.
- **sherpa-onnx** já tem pipeline pronto de diarização exportado para ONNX, com modelos pyannote convertidos. Inferência via ONNX Runtime, que funciona bem em Android.
- Trade-off: qualidade ligeiramente inferior ao pyannote nativo, mas operacional.

### D-A7. Por que Gemma 2 2B / Phi-3 mini para LLM local?

- Tamanho quantizado (Q4): ~1.5–2GB RAM em runtime. Cabe em celulares de 6GB+.
- Qualidade para tarefas tipo "resumir esta transcrição" é suficiente.
- Velocidade em CPU: ~10–20 tokens/s em celular médio.
- Alternativas: Llama 3.2 1B (menor mas pior), Qwen 2.5 1.5B (similar), Mistral 7B (melhor mas só celulares topo de linha).
- **Recomendação:** oferecer 2 modelos: "Pequeno" (Llama 3.2 1B — qualquer celular) e "Grande" (Gemma 2 2B — celulares 6GB+). Usuário escolhe.

### D-A8. Por que Cloud LLM como backend opcional?

- Análises profundas (ex: "compare esta aula com as 5 anteriores", "extraia todos os exemplos de código mencionados") se beneficiam de modelos grandes.
- Custo é baixo (Claude Haiku ~ $0.25/MTok input — uma aula transcrita de 4h tem ~50K tokens, então ~$0.01 por análise).
- Trade-off de privacidade: só **texto** sai, nunca áudio. Usuário consente explicitamente.

### D-A9. Por que GitHub Releases + F-Droid + APK no site, sem Play Store?

- **Play Store** tem políticas restritivas para apps de gravação (precisa justificar permissão MICROPHONE, exige declaração de uso, pode rejeitar). Não vale o atrito na v1.
- **F-Droid** dá visibilidade na comunidade open-source.
- **GitHub Releases** é canal natural para early adopters.
- **APK no site** dá controle total (sempre a última versão, sem revisão de terceiros).

---

## 2.5. Estrutura de pastas do projeto

```
E:\AulaLogger\
├── PLANO_DE_DESENVOLVIMENTO.md
├── docs/
│   ├── 01-visao-produto.md
│   ├── 02-arquitetura-tecnica.md     ← você está aqui
│   └── ...
├── app/                                  ← Aplicativo Android (RN+Expo)
│   ├── package.json
│   ├── app.json (Expo config)
│   ├── tsconfig.json
│   ├── eas.json
│   ├── app/                              ← Expo Router (telas)
│   │   ├── _layout.tsx
│   │   ├── index.tsx                     ← Home / lista de aulas
│   │   ├── record.tsx                    ← Gravação ativa
│   │   ├── session/
│   │   │   ├── [id].tsx                  ← Detalhe de uma aula
│   │   │   └── [id]/transcript.tsx       ← Viewer de transcrição
│   │   ├── settings/
│   │   │   ├── index.tsx
│   │   │   ├── advanced.tsx
│   │   │   ├── voice-enrollment.tsx
│   │   │   └── cloud-providers.tsx
│   │   └── library.tsx                   ← Biblioteca / busca
│   ├── components/                       ← Componentes UI
│   ├── hooks/                            ← Hooks reutilizáveis
│   ├── lib/                              ← Utils JS
│   ├── stores/                           ← Zustand stores
│   ├── modules/
│   │   └── aulalogger-native/            ← Módulo nativo Expo
│   │       ├── android/...
│   │       └── src/...
│   ├── assets/
│   └── __tests__/
├── site/                                 ← Landing page + docs
│   ├── package.json
│   ├── astro.config.mjs                  ← Recomendação: Astro
│   ├── src/
│   │   ├── pages/
│   │   ├── content/
│   │   │   ├── docs/                     ← Docs do app em MD
│   │   │   └── blog/
│   │   ├── components/
│   │   └── layouts/
│   └── public/
├── .github/
│   ├── workflows/
│   │   ├── app-build.yml                 ← CI app
│   │   ├── app-release.yml               ← Release APK
│   │   ├── site-deploy.yml               ← Deploy site
│   │   └── tests.yml
│   ├── ISSUE_TEMPLATE/
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── CODEOWNERS
├── tools/                                ← Scripts auxiliares
│   ├── download-models.sh
│   ├── benchmark-transcription.sh
│   └── generate-test-audio.sh
├── README.md
├── LICENSE
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── SECURITY.md
└── .gitignore
```

---

## 2.6. Diagrama de dependências entre módulos

```
                              ┌──────────────┐
                              │  UI (RN/TS)  │
                              └──────┬───────┘
                                     │
                   ┌─────────────────┴─────────────────┐
                   │      AulalogerNativeModule        │
                   │    (Expo Module / Kotlin facade)  │
                   └─┬────┬────┬────┬────┬────┬────────┘
                     │    │    │    │    │    │
              ┌──────┘    │    │    │    │    └────────┐
              ▼           ▼    ▼    ▼    ▼             ▼
        ┌─────────┐ ┌────────┐│   ┌────┐ ┌────┐    ┌──────┐
        │Recording│ │Transcr.││   │Diar│ │ LLM│    │Export│
        │ Service │ │ Engine ││   │Eng │ │Eng │    │      │
        └────┬────┘ └────┬───┘│   └─┬──┘ └─┬──┘    └───┬──┘
             │           │    │     │      │           │
             ▼           ▼    │     ▼      ▼           ▼
        ┌─────────┐ ┌────────┐│ ┌──────┐ ┌──────┐  ┌──────┐
        │ Chunk   │ │whisper ││ │sherpa│ │llama │  │ PDF/ │
        │ Writer  │ │  .cpp  ││ │-onnx │ │ .cpp │  │ DOCX │
        └─────────┘ └────────┘│ └──────┘ └──────┘  └──────┘
                              │
                              ▼
                       ┌──────────────┐
                       │ Audio Pipeline│
                       │ (RNNoise/VAD) │
                       └──────────────┘

                       ┌──────────────┐
                       │   Storage    │
                       │ (Room+Files) │
                       └──────────────┘
                              ▲
                              │
                       (todos acessam)
```

---

## 2.7. Threading e concorrência

**Regra:** UI thread nunca bloqueia. JS thread nunca bloqueia. Trabalho pesado em coroutines com `Dispatchers.Default` ou `Dispatchers.IO`.

| Trabalho | Thread/Pool |
|----------|-------------|
| UI (RN render) | JS thread |
| Comandos rápidos para nativo (ex: startRecording) | Module thread (Expo) |
| Gravação (loop AudioRecord) | Thread dedicada do RecordingService |
| Escrita de chunks | IO dispatcher (coroutine) |
| Whisper inference | Default dispatcher (CPU bound) |
| ONNX Runtime (diarização) | Default dispatcher |
| LLM inference | Default dispatcher |
| Database queries | IO dispatcher (Room já gerencia) |
| HTTP cloud | IO dispatcher (Ktor) |

**Concorrência crítica:** apenas **uma** sessão de gravação ativa por vez. Apenas **uma** transcrição/análise rodando por vez (WorkManager unique work).

---

## 2.8. Tratamento de erros e degradação

### Princípios
1. **Erro de gravação = catastrófico**, deve ser raríssimo e tratado com máxima cerimônia.
2. **Erro de transcrição = recuperável**, pode tentar de novo, pode usar modelo diferente.
3. **Erro de IA = ignorável**, tarefa pode ficar pendente sem afetar o resto.
4. **Erro de rede (cloud) = esperado**, sempre fallback para local.

### Categorias de erro
| Categoria | Exemplo | Resposta |
|-----------|---------|----------|
| Permission denied | Mic não autorizado | Tela explicativa, link para settings |
| Storage full | < 100MB livre | Bloqueio de gravação, mensagem clara |
| OOM | Memória insuficiente para Whisper Large | Sugerir modelo menor |
| Model not downloaded | Whisper não baixado | Tela de download com estimativa |
| Audio device error | Mic em uso por outro app | Mensagem + retry |
| Crash de chunk write | Falha I/O | Retry + alerta na notificação |
| Crash do JNI (whisper.cpp) | Memory corruption | Capturar nativo, marcar transcrição como falha, oferecer retry |
| Network timeout (cloud) | Sem internet | Análise fica pendente, retry via WorkManager |
| Cloud auth fail | API key inválida | Mensagem + link configurações |

---

## 2.9. Observabilidade local

Sem telemetria externa (princípio de privacidade). Mas para diagnóstico do **próprio usuário**:

- **Logs em arquivo rotativo** (`/data/data/<pkg>/files/logs/`).
- **Painel de diagnóstico** em Configurações → Sobre → Diagnóstico, mostrando:
  - Versão do app, OS, modelo do device.
  - Espaço livre, memória disponível.
  - Modelos ML instalados e tamanho.
  - Última gravação (status, duração, problemas).
  - Botão "Exportar diagnóstico" → ZIP com logs (sem áudio nem texto de aulas — só logs técnicos).
- **Logs detalhados de cada gravação** salvos junto com a sessão (timing de chunks, falhas, recoveries).

---

## 2.10. Considerações sobre a escolha de RN + Expo (riscos e mitigações)

> Você escolheu RN + Expo (decisão D1). Vou ser explícito sobre os riscos e como mitigá-los, porque essa escolha tem implicações concretas.

### Risco R1: RN bridge pode pausar quando app vai para background

**Mitigação:** TODA a gravação acontece no Foreground Service nativo Kotlin. JS apenas inicia/para. Se JS thread morrer ou app for killed, gravação continua porque o Service tem ciclo de vida próprio.

### Risco R2: Comunicação JS↔nativo via JSON pode introduzir lag

**Mitigação:** Eventos do Service para JS são throttled (no máximo 1/segundo: "X chunks salvos"). UI não precisa de updates mais frequentes.

### Risco R3: Bundle JS gigante (com tantas libs) torna app lento de abrir

**Mitigação:** Hermes engine (default no RN moderno), code splitting onde possível, lazy loading de telas pesadas.

### Risco R4: Modelos ML (whisper, llama) só rodam via JNI, não via JS

**Mitigação:** Sempre foi assim. Esse é o trabalho do módulo nativo.

### Risco R5: Atualizações de RN/Expo podem quebrar o módulo nativo

**Mitigação:** Pinning de versões. Updates de Expo SDK são "eventos" que envolvem teste completo, não atualizações automáticas.

### Risco R6: Debugging de issues nativos é mais penoso

**Mitigação:** Logs estruturados, ProGuard mappings preservados, testes nativos isolados (JUnit) para o módulo, separados dos testes JS (Jest).

### Risco R7: Tamanho do APK fica grande (RN + Expo + libs nativas + modelos)

**Mitigação:** APK base ficará na faixa de **40–60MB** (aceitável). Modelos ML (Whisper, Sherpa, Gemma) são **baixados sob demanda** na primeira execução, não embutidos. App splits por ABI (arm64-v8a apenas, dropping x86 e armv7).

---

## 2.11. Critério para "arquitetura validada"

A arquitetura está validada quando:

- [ ] Foreground service grava 4h ininterruptamente em 3 celulares diferentes.
- [ ] Comunicação JS↔nativo funciona com eventos throttled.
- [ ] Whisper.cpp transcreve um WAV de 1h em < 1h em celular médio.
- [ ] Sherpa-onnx executa pipeline de diarização sem erro em uma sessão de 30min.
- [ ] Llama.cpp carrega Gemma 2B Q4 em celular com 6GB RAM e gera resposta < 30s para um prompt de 4K tokens.
- [ ] Crash do JS não para o RecordingService.
- [ ] Crash do RecordingService permite recovery na próxima abertura com perda < 30s.

Esses são os "marcos zero" do v1.0 — sem eles validados, escalar features é prematuro.
