# 05 — Transcrição on-device (Whisper)

> Como rodar Whisper no Android, qual modelo escolher, como otimizar para PT-BR técnico, como dar ao usuário controle sobre velocidade vs qualidade.

---

## 5.1. Por que Whisper?

[Whisper](https://github.com/openai/whisper) (OpenAI, 2022) é hoje o melhor modelo open-weight para transcrição multilíngue. Características:

- **Multilíngue:** 99 idiomas (PT-BR é um dos melhor suportados).
- **Robusto:** treinado em 680K horas de áudio diverso, lida bem com sotaques, ruído, pausas.
- **Open weights:** Apache 2.0, usável comercialmente.
- **Famílias de tamanho:** tiny (39M), base (74M), small (244M), medium (769M), large-v3 (1550M).
- **Bem suportado:** [whisper.cpp](https://github.com/ggerganov/whisper.cpp) é uma implementação C++ portável e otimizada (NEON, OpenCL, NNAPI), perfeita para mobile.
- **Outputs ricos:** texto + timestamps (palavra ou segmento) + idioma detectado + nível de confiança.

**Alternativas consideradas:**
- **Deepgram, AssemblyAI, Soniox:** cloud-only, contradiz princípio de privacidade.
- **Vosk:** offline mas WER significativamente pior.
- **Wav2Vec 2.0 / Conformer:** ótimos para inglês, suporte limitado para PT.
- **Distil-Whisper:** versões destiladas mais rápidas. Promissor para v2.

---

## 5.2. Escolha de modelo

### Comparativo de modelos Whisper para PT-BR

| Modelo | Tamanho FP16 | Quantizado Q5 | RAM em runtime | Velocidade celular médio | WER PT-BR técnico estimado |
|--------|--------------|---------------|----------------|---------------------------|---------------------------|
| tiny | 75MB | 30MB | ~150MB | 5x real-time | ~25–30% |
| base | 142MB | 60MB | ~300MB | 3x real-time | ~18–22% |
| **small** ⭐ | 466MB | 200MB | ~700MB | 1.0x real-time | **~10–14%** |
| medium | 1.5GB | 600MB | ~1.6GB | 0.4x real-time | ~7–10% |
| large-v3 | 3.1GB | 1.2GB | ~3GB | 0.15x real-time | ~5–8% |

**Recomendação default:** **small** (Q5_K_M).
- Cabe em qualquer celular moderno (200MB modelo, 700MB RAM).
- WER de 10–14% é bom para uso real (sentenças entendíveis, palavras-chave certas).
- Roda em ~tempo real (4h de áudio = ~4h de transcrição, em background OK).

**Opções para o usuário:**
- "Rápido" (base): celulares antigos ou para pré-visualização.
- "Padrão" (small): default.
- "Alta qualidade" (medium): celulares com 4GB+ RAM, transcrição ~3x mais lenta mas WER ~7%.
- "Máxima qualidade" (large-v3): celulares topo de linha (12GB+ RAM), transcrição ~10x mais lenta.

### Quantização

Quantização reduz precisão dos pesos para economizar memória, com pequena perda de qualidade.

| Quantização | Tamanho | Qualidade vs FP16 |
|-------------|---------|---------------------|
| FP16 | 100% | 100% (baseline) |
| Q8_0 | ~50% | 99.5% |
| Q5_K_M | ~33% | 99% |
| Q4_K_M | ~25% | 97% |

**Default:** Q5_K_M (excelente balanço). Avançado: usuário escolhe Q4 (mais leve) ou Q8 (mais qualidade).

---

## 5.3. Integração whisper.cpp

[whisper.cpp](https://github.com/ggerganov/whisper.cpp) tem build pronto para Android. Estrutura:

```
modules/aulalogger-native/android/src/main/cpp/
├── CMakeLists.txt
├── whisper-jni.cpp          ← nosso wrapper JNI
└── whisper.cpp/             ← submódulo git (sources)
    ├── whisper.h
    ├── whisper.cpp
    ├── ggml.c
    └── ...
```

**CMakeLists.txt:**

```cmake
cmake_minimum_required(VERSION 3.22)
project(whisperjni)

# Habilita NEON para ARM
add_compile_options(-mfpu=neon -O3 -ffast-math)

# Habilita NNAPI delegate (se disponível)
option(WHISPER_NNAPI "Enable NNAPI delegate" ON)

add_subdirectory(whisper.cpp)

add_library(whisperjni SHARED whisper-jni.cpp)
target_link_libraries(whisperjni 
    whisper 
    log 
    android
)
```

**whisper-jni.cpp (esqueleto):**

```cpp
#include <jni.h>
#include "whisper.h"

extern "C" JNIEXPORT jlong JNICALL
Java_expo_modules_aulalogger_transcription_WhisperCppBridge_nativeInit(
    JNIEnv* env, jobject thiz, jstring modelPath
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = true;  // tenta GPU/NNAPI quando disponível
    
    struct whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_expo_modules_aulalogger_transcription_WhisperCppBridge_nativeTranscribe(
    JNIEnv* env, jobject thiz, jlong ctxPtr,
    jfloatArray samples, jstring language, jboolean wordTimestamps
) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    
    jfloat* samplePtr = env->GetFloatArrayElements(samples, nullptr);
    jsize sampleCount = env->GetArrayLength(samples);
    
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    
    const char* lang = env->GetStringUTFChars(language, nullptr);
    params.language = lang;
    params.translate = false;
    params.token_timestamps = wordTimestamps;
    params.max_len = wordTimestamps ? 1 : 0;
    params.print_progress = false;
    params.print_special = false;
    params.n_threads = 4;  // ajustar conforme device
    
    int result = whisper_full(ctx, params, samplePtr, sampleCount);
    
    // Coleta segmentos
    std::string output = "[";
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        if (i > 0) output += ",";
        const char* text = whisper_full_get_segment_text(ctx, i);
        int64_t t0 = whisper_full_get_segment_t0(ctx, i);  // 10ms units
        int64_t t1 = whisper_full_get_segment_t1(ctx, i);
        output += "{\"start_ms\":" + std::to_string(t0 * 10) +
                  ",\"end_ms\":" + std::to_string(t1 * 10) +
                  ",\"text\":\"" + escapeJson(text) + "\"}";
    }
    output += "]";
    
    env->ReleaseFloatArrayElements(samples, samplePtr, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);
    
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_expo_modules_aulalogger_transcription_WhisperCppBridge_nativeFree(
    JNIEnv* env, jobject thiz, jlong ctxPtr
) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    whisper_free(ctx);
}
```

**WhisperCppBridge.kt (lado Kotlin):**

```kotlin
class WhisperCppBridge(modelPath: String) : Closeable {
    init {
        System.loadLibrary("whisperjni")
    }
    
    private val ctxPtr: Long = nativeInit(modelPath)
    
    fun transcribe(samples: FloatArray, language: String = "pt", wordTimestamps: Boolean = false): List<TranscriptSegment> {
        val json = nativeTranscribe(ctxPtr, samples, language, wordTimestamps)
        return JsonParser.parseSegments(json)
    }
    
    override fun close() {
        nativeFree(ctxPtr)
    }
    
    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(ctxPtr: Long, samples: FloatArray, language: String, wordTimestamps: Boolean): String
    private external fun nativeFree(ctxPtr: Long)
}

data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
```

---

## 5.4. Pipeline de transcrição

```
Cleaned chunks (cleaned-NNNNN.wav) + VAD segments
            │
            ▼
   ┌─────────────────────────────────────┐
   │ TranscribeJob (WorkManager)          │
   │                                       │
   │   1. Carregar modelo Whisper          │
   │      (lazy: só uma vez por sessão)    │
   │                                       │
   │   2. Para cada chunk em ordem:        │
   │      - Converter PCM → Float (-1, 1)  │
   │      - Concatenar com 5s do anterior │
   │        (overlap, evita perder palavra │
   │         em borda)                     │
   │      - whisper.transcribe(samples)    │
   │      - Ajustar timestamps (offset    │
   │        global da sessão)              │
   │      - Deduplicar overlap             │
   │      - Persistir no DB segmentos      │
   │      - Emitir progresso pra UI        │
   │                                       │
   │   3. Liberar modelo                   │
   │   4. Marcar sessão como transcrita    │
   └─────────────────────────────────────┘
```

### 5.4.1. Estratégia de chunking para Whisper

Whisper trabalha melhor com janelas de 30s. Como nossos chunks já são de 30s (do gravador), simples?

**Não exatamente.** Problema: palavras na borda do chunk podem ser cortadas no meio. Ex: chunk N termina em "...a função pode ser exec" e chunk N+1 começa com "utada de várias formas".

**Solução: overlap de 5s.**
- Para transcrever chunk N, alimentamos Whisper com `[últimos 5s do chunk N-1] + [chunk N]`.
- Pegamos só os segmentos que começam após os 5s iniciais (descartar overlap).
- Custo: 5/30 = 17% de processamento extra. Aceitável.

```kotlin
suspend fun transcribeSession(session: Session) {
    val whisper = WhisperCppBridge(modelPath)
    val chunks = session.cleanedChunkFiles.sortedBy { it.name }
    var lastChunkSamples: FloatArray? = null
    var globalOffsetMs = 0L
    
    for (chunkFile in chunks) {
        val chunkSamples = readWavAsFloat(chunkFile)
        val input = if (lastChunkSamples != null) {
            // Concatena últimos 5s + chunk atual
            val overlapSamples = 5 * 16000
            lastChunkSamples!!.takeLast(overlapSamples).toFloatArray() + chunkSamples
        } else {
            chunkSamples
        }
        
        val segments = whisper.transcribe(input, language = "pt", wordTimestamps = true)
        
        // Ajustar timestamps e descartar segmentos do overlap
        val overlapMs = if (lastChunkSamples != null) 5000 else 0
        val adjusted = segments
            .filter { it.startMs >= overlapMs }
            .map { it.copy(
                startMs = it.startMs - overlapMs + globalOffsetMs,
                endMs = it.endMs - overlapMs + globalOffsetMs
            )}
        
        db.segmentDao().insertAll(adjusted.map { TranscriptSegmentEntity.from(session.id, it) })
        
        globalOffsetMs += chunkSamples.size * 1000L / 16000
        lastChunkSamples = chunkSamples
        
        emitProgress(session.id, processedChunks = chunks.indexOf(chunkFile) + 1, total = chunks.size)
    }
    
    whisper.close()
    session.copy(status = "transcribed").persist()
}
```

### 5.4.2. Threading

- `n_threads` no whisper.cpp: começar com `Runtime.getRuntime().availableProcessors() / 2`. Mais threads ≠ mais rápido (cache thrashing). Benchmark depois.
- Job inteiro roda em coroutine `Dispatchers.Default` (CPU-bound).

### 5.4.3. NNAPI delegate (aceleração HW)

whisper.cpp suporta GPU via OpenCL/Vulkan, mas inconsistente em Android. **Mais importante:** delegate NNAPI ainda é experimental no whisper.cpp.

**Plano:** habilitar NNAPI via flag, fallback para CPU. Medir empiricamente. Se ganho > 30%, manter ON; se < 10%, manter OFF (custos de inicialização).

---

## 5.5. Otimizações de qualidade para PT-BR técnico

### 5.5.1. Vocabulário customizado (initial_prompt)

Whisper aceita um "initial prompt" que viesa o output. Útil para palavras técnicas raras:

```
"VLOOKUP, PROCV, PROCH, sklearn, pandas, numpy, matplotlib, 
TensorFlow, PyTorch, JSON, API, REST, GraphQL, Docker, 
Kubernetes, Git, GitHub, IDE, framework, deploy, debug..."
```

**UX:** usuário pode adicionar termos do domínio dele em Configurações → Transcrição → Vocabulário customizado.

```
[Configurações > Transcrição > Vocabulário customizado]

Adicione termos técnicos, nomes próprios ou jargão que aparecem 
nas suas aulas. Isso ajuda o reconhecimento.

[textarea livre, separado por vírgula ou linha]

[Sugestão: o app pode aprender automaticamente os termos mais 
frequentes nas suas transcrições e sugerir adicioná-los aqui]
```

### 5.5.2. Pós-processamento

Após Whisper, aplicamos correções comuns para PT-BR:

- **Pontuação:** Whisper inclui mas às vezes erra. Modelo BERT-base-pt fine-tunned para repunctuation pode rodar pós-processo.
- **Capitalização:** Whisper costuma acertar, mas pós-processa nomes de pessoas, siglas em maiúsculo.
- **Números:** "vinte e três" → "23" opcionalmente (config).
- **Substituições conhecidas:** "machine learning" (não "maxim learning"), "pandas" (não "panda's"), etc.

**Implementação:** dicionário de substituições + regex.

### 5.5.3. Spell-check com vocabulário do domínio

Após transcrição, palavras de baixa confiança podem ser sugeridas via spell-checker contra o vocabulário customizado.

**Decisão:** v1.1 sem spell-check automático. Em v1.3, oferecer "revisar trechos com baixa confiança" — UI lista segmentos com `avg_logprob < threshold` para usuário revisar.

---

## 5.6. Word-level timestamps

Whisper.cpp suporta timestamps por **palavra** (não só por segmento) com flag `token_timestamps = true` + `max_len = 1`.

**Custo:** ~10% mais lento, mas viabiliza:
- Karaoke-style highlight no player de áudio.
- Edição precisa de timestamps.
- Análise de ritmo (palavras por minuto exatas).
- Geração de SRT/VTT linha-por-linha precisa.

**Decisão:** ativar por padrão, configurável.

---

## 5.7. Detecção de idioma

Por default, falamos `language = "pt"` (você dá aulas em português).

**Mas** se aluno fizer pergunta em inglês ou se você usar muito inglês técnico, Whisper pode performar pior se "forçado" para PT.

**Estratégia:**
- **Modo padrão:** PT-BR forçado (mais preciso para PT, ignora outros idiomas).
- **Modo "multilíngue":** detect automático por chunk. Custos: detecção custa ~1-2s extra/chunk; mais flexível.
- **Configuração:** usuário escolhe.

---

## 5.8. Gerenciamento de modelos

Modelos Whisper são grandes (200MB–1.2GB). Não embutimos no APK.

### Download na primeira execução

```
Primeira abertura:
  ✋ "Para transcrição de aulas, precisamos baixar um modelo de IA"
  
  Modelos disponíveis:
    ○ Rápido (60MB) — celulares antigos
    ● Padrão (200MB) — recomendado [✓]
    ○ Alta qualidade (600MB) — celulares modernos
    ○ Máxima qualidade (1.2GB) — celulares topo de linha
  
  [Baixar agora — apenas em Wi-Fi]  [Lembrar depois]
```

### Onde baixar de

Hugged Hugging Face hospeda os modelos em GGUF format já convertidos:
- https://huggingface.co/ggerganov/whisper.cpp

Nosso CDN: replicar no nosso GitHub Releases ou Cloudflare R2 para garantir disponibilidade.

### Verificação de integridade

SHA-256 dos modelos publicado em metadata. Após download, verificamos hash. Se mismatch, retentar.

### Atualização de modelos

Quando lançamos versão nova do app com modelo melhor, oferecemos atualização opcional.

```
[Configurações > Modelos]

Whisper Small — instalado (200MB)
  ⓘ Atualização disponível: Whisper Small v3 (precisão melhor)
  [Atualizar]

Llama 3.2 1B — instalado (700MB)
  ✓ Atualizado

[+ Instalar modelo adicional]
```

---

## 5.9. Tempo estimado e UX de progresso

Para uma aula de 4h, com modelo Small em celular médio:
- Pipeline áudio: ~20 min
- Transcrição: ~3h30min
- Diarização (próximo doc): ~1h
- Análise IA local: ~30min

**Total:** ~5h20min de processamento pós-aula.

**UX:**
- Iniciar tudo automaticamente após "parar gravação".
- Notificação persistente: "Processando aula de Python — 23%".
- Tela "Em processamento" mostrando ETA realista.
- Possível pausar processamento (resume depois).
- Possível pular etapas (ex: só transcrever, sem análise IA).

```
[Tela: Em Processamento]

Aula de Python — 03/05 às 20:30 (4h12)

  ● Limpando áudio                 ✓ Concluído
  ● Transcrevendo                  47% — ~1h50 restantes
  ○ Identificando vozes            aguardando
  ○ Análise IA                     aguardando

  [Pausar]  [Cancelar]  [Pular análise IA]
  
  ⓘ Você pode usar o app normalmente. 
     Te avisamos quando estiver pronto.
```

---

## 5.10. Plano de implementação

| Sprint | Entrega |
|--------|---------|
| Sprint 8 (sem 11) | Whisper.cpp build Android, JNI bridge funcionando |
| Sprint 9 (sem 12) | Pipeline transcrição com chunking + overlap |
| Sprint 9 (sem 12) | Persistência de segmentos, progresso |
| Sprint 10 (sem 13) | Word-level timestamps, vocabulário customizado |
| Sprint 10 (sem 13) | Pós-processamento (substituições, pontuação) |
| Sprint 11 (sem 14) | UI de viewer, busca, exportação SRT/VTT |
| Sprint 11 (sem 14) | Gerenciamento de modelos (download, troca, atualização) |

---

## 5.11. Métricas e benchmarks

Antes do release v1.1, medir empiricamente:

| Métrica | Meta |
|---------|------|
| WER em PT-BR conversacional | < 10% |
| WER em PT-BR técnico (Excel/Python) | < 14% |
| Tempo transcrição em Pixel 7 | < 1.0x real-time (Small) |
| Tempo transcrição em Galaxy A54 | < 1.5x real-time (Small) |
| RAM máxima durante transcrição | < 800MB (Small) |
| Tamanho de modelo Small Q5 | ~200MB |
| Acurácia de timestamps | < 200ms de erro médio |

Suite de áudio para benchmark: gravar 10 amostras de 5min cada, com diversos sotaques e barulhos, e ground truth manual.
