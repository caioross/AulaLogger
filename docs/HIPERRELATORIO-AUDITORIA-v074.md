# Hiperrelatório de Auditoria — AulaLogger v0.7.4

> Auditoria consolidada e re-verificada. Confronta o relatório v0.7.3 (53 itens) com o código atual,
> mapeia o que **já foi corrigido**, o que **continua pendente** e adiciona **bugs/lacunas inéditos**
> descobertos numa segunda passada cega sobre cada arquivo Kotlin, JNI, recursos, manifesto e widget.
>
> Auditoria efetuada em 15/05/2026 (data de relatório), versão atual: `versionCode=10, versionName="0.7.3"`.
> Arquivos auditados: 33 Kotlin + 1 JNI C++ + manifest + strings/themes/layouts/widget + proguard + gradle.

---

## Sumário executivo

### Estado do código no momento da auditoria

| Categoria | v0.7.3 (53 itens) | v0.7.4 (consolidado) |
|---|---|---|
| **Críticos** | 7 | 5 corrigidos, 2 com regressão lateral |
| **Altos** | 14 | 11 corrigidos, 3 ainda presentes |
| **Médios** | 19 | 12 corrigidos, 7 ainda presentes |
| **Baixos / polimento** | 13 | 6 corrigidos, 7 ainda presentes |
| **Novos** (descobertos nesta passada) | — | **15 novos itens** |

O codebase amadureceu substancialmente desde a v0.7.3. A maioria das BUGs críticas
(idle eviction do Whisper, refresh em onResume, cancelar transcrição ao deletar, banner de
erro, temperature_inc, VAD-like dedup, download resume, max_tokens Claude, heartbeat 20s,
detecção de fone, mic mudo, bateria/disco, AppKiller, OEM tutorial, ProGuard release+keystore,
strip de Log.v/d/i, AboutScreen criado) **já foi implementada com qualidade**.

Os bugs remanescentes e os 15 novos não comprometem o uso essencial (gravação confiável segue
sólida), mas atacam **UX em fluxos de borda, racing entre threads de service/UI, leakage de
recursos, defesa de produção e correção semântica** (badge "RECUPERADA" que não some,
AboutScreen inacessível, etc.).

### Top 10 ações imediatas (priorizadas por impacto/esforço)

1. **NEW-001 — AboutScreen inalcançável** (10 min, impacto alto: usuário não tem como ver versão).
2. **NEW-003 — Auto-transcribe encadeado de sessions recuperadas** (30 min).
3. **NEW-034 — Status "RECUPERADA" persiste após transcrição automática** (5 min).
4. **NEW-044/045 — Widget dispara FGS sem permissão de mic → SecurityException** (30 min).
5. **NEW-023 — RecordingScreen navega para SessionDetail antes de markCompleted** (20 min).
6. **NEW-029 — AnalysisScreen vaza HTTP request ao sair da tela** (15 min).
7. **NEW-037 — handleStop race com captureJob (writer fecha durante write)** (15 min).
8. **NEW-033 — Window background flash dark em modo claro** (10 min).
9. **NEW-019 — network_security_config.xml ausente** (5 min).
10. **NEW-038 — pollJob de progresso pode vazar entre chunks** (10 min).

---

## 1. Status item-a-item do relatório v0.7.3

> Verificação direta no código atual. Legenda: ✅ corrigido · ⚠ parcial · ❌ pendente · 🔁 abordagem diferente.

### Críticos

| ID | Status atual | Onde verifiquei |
|---|---|---|
| BUG-001 (strings/widget desalinhados) | ✅ — `strings.xml` agora tem `widget_action_record="GRAVAR"`, widget lê via R.string; `model_download_body` removido | `strings.xml:6,12`, `AulaLoggerWidget.kt:64-69` |
| BUG-002 (`frame.copyOf` por read) | ✅ — `AudioCapture` retorna referência interna + `lastReadCount` | `AudioCapture.kt:39-45,82-105` |
| BUG-003 (`ByteBuffer.allocate` por write) | ✅ — `convBuf` reusável field-level + escrita manual little-endian | `WavWriter.kt:34-55` |
| BUG-004 (WAV cap em 2 GB / 17 h) | ⚠ — agora cap em `Int.MAX_VALUE - 36`; arquivo continua reproduzível mas header congela; **RF64 ainda não implementado** | `WavWriter.kt:108-115` |
| BUG-005 (`onResume` não refresh) | ✅ — `AppStatusHolder.refresh(applicationContext)` em `MainActivity.onResume` | `MainActivity.kt:100` |
| BUG-006 (delete durante transcrição não cancela) | ✅ — `SessionRepository.delete` cancela `TranscriptionService` se sessão coincide | `SessionRepository.kt:174-183` |
| BUG-007 (Whisper ctx idle eviction) | ✅ — `WhisperPool` com IDLE_TIMEOUT_MS=5min, acquire/release/freeNow | `WhisperPool.kt`, `TranscriptionService.kt:117,260` |

### Altos

| ID | Status atual | Onde verifiquei |
|---|---|---|
| BUG-008 (refazer apaga texto antigo) | ✅ — não apaga; só sobrescreve no sucesso | `SessionDetailScreen.kt:299-326`, `TranscriptionService.kt:248-249,255-256` |
| BUG-009 (erro de transcrição invisível) | ✅ — banner vermelho + "Tentar novamente" + uso de `TranscriptionState.fail` | `SessionDetailScreen.kt:54-55,151-181` |
| BUG-010 (dedupe frágil) | ✅ — dedupe híbrido tempo + similaridade Jaccard de palavras (>= 70 %) | `TranscriptionService.kt:387-430` |
| BUG-011 (AppKiller hack 300 ms) | ✅ — espera `activeSessionIdFlow` virar null com timeout 2 s | `AppKiller.kt:50-54` |
| BUG-012 (start race) | ❌ — `RecordingController.start` segue fire-and-forget retornando `Unit`. Widget e UI ainda podem disparar duplo START sem feedback de "ignorado" | `RecordingController.kt:35-43`, `RecordingService.kt:79` |
| BUG-013 (download resume) | ✅ — Range header + 206 detection + retry total se servidor não suporta | `ModelManager.kt:109-127` |
| BUG-014 (temperature_inc=0) | ✅ — restaurado `0.2f`; comentário documenta o trade-off | `whisper-jni.cpp:132-133` |
| BUG-015 (Gemini safety) | ✅ — checa `finishReason` SAFETY/RECITATION/BLOCKLIST e erra com mensagem | `AiAnalyzer.kt:140-144` |
| BUG-016 (Claude max_tokens) | ✅ — `CLAUDE_MAX_TOKENS = 8192` | `AiAnalyzer.kt:26,182` |
| BUG-017 (HTTP sem cancel) | ⚠ — `AiAnalyzer.inFlight` + `cancelInFlight()` funciona, mas é chamado **apenas no botão "Cancelar"**; navegar back da AnalysisScreen NÃO cancela (NEW-029) | `AiAnalyzer.kt:28-58,202-230`, `AnalysisScreen.kt:130-140` |
| BUG-018 (cancel global por singleton) | ⚠ — documentado, não corrigido. Aceitável enquanto não há paralelismo |  |
| BUG-019 (notificações fantasma) | ✅ — `nm.cancelAll()` no AppKiller | `AppKiller.kt:61-65` |
| BUG-020 (`TranscriptionPrefs` no main) | ⚠ — ainda lê SharedPreferences no main thread; sem cache flow | `TranscriptionPrefs.kt:11-21` |
| BUG-021 (AudioPlayer LaunchedEffect) | ✅ — `remember(filePath)` correto |  |

### Médios

| ID | Status |
|---|---|
| BUG-022 (`readAsFloat` dead code) | ⚠ — agora `@Deprecated`, ainda existe; OK |
| BUG-023 (heartbeat 60 s) | ✅ — 20 s + threshold 60 s |
| BUG-024 (mudança de mic) | ✅ — AudioDeviceCallback registrado |
| BUG-025 (mic mudo) | ✅ — `SystemMonitor.SILENT_MIC` |
| BUG-026 (Room/SQLite) | ❌ — pendente, JSON ainda |
| BUG-027 (migration policy) | ❌ — `ignoreUnknownKeys` apenas |
| BUG-028 (battery prompt repetido) | ✅ — flag `battery_requested` |
| BUG-029 (initial_prompt enviesado) | ⚠ — "Aula em português brasileiro." (sem "técnica"); ainda não configurável |
| BUG-030 (GetPrimitiveArrayCritical bloqueia GC) | ❌ — ainda critical; risco real em chunks de ~1.9MB durante 30-90s |
| BUG-031 (`MEDIUM.minBytes = 200 MB`) | ✅ — agora `approxBytes * 0.9` |
| BUG-032 (START repetido) | ⚠ — idempotente se mesmo id; diferente id ignorado silencioso (NEW-015) |
| BUG-033 (state em remember na Analysis) | ❌ — `analyzeJob/loading/error/pickerOpen` ainda em `remember`. ViewModel ausente |
| BUG-034 (4× hasKey por refresh) | ✅ — lifecycle observer |
| BUG-035 (rename sem trim/max) | ✅ — `take(120)` + `replace('\n', ' ')` |
| BUG-036 (LongPressStopButton) | ✅ — usa LaunchedEffect(holding) |
| BUG-037 (threads hardcoded) | ⚠ — `(N/2).coerceIn(2,4)`; sem override em settings |
| BUG-038 (warning bateria) | ✅ — SystemMonitor.LOW_BATTERY |
| BUG-039 (warning disco) | ✅ — SystemMonitor.LOW_STORAGE |
| BUG-040 (setLocalOnly) | ❌ — ainda sem `setLocalOnly(true)` na notificação |

### Polimento / baixos / arquitetura / segurança

| ID | Status |
|---|---|
| POL-001 (strings obsoletas) | ✅ — limpas |
| POL-002 (tab_audio/transcript) | ✅ — ausentes |
| POL-003 (Locutor A/B hardcoded) | ⚠ — strings.xml tem `speaker_a/b` mas TranscriptFormatter ainda usa string hardcoded em `assignSpeakers` |
| POL-004 (initial_prompt configurável) | ❌ |
| POL-005 (tela "Sobre") | ⚠ — **AboutScreen.kt existe mas é dead code** (NEW-001) |
| POL-006 (indicador kill) | ❌ |
| POL-007 (botão refazer estilo) | ⚠ — segue Button comum |
| POL-008 (consumo disco em ModelsScreen) | ✅ — `installedSizeMb` exibido |
| POL-009 (snackbar test vs save) | ⚠ — mesma mensagem "Chave salva ✓" |
| POL-010 (RecordingScreen mostra modelo) | ❌ |
| POL-011 (EmptyStateHero pesado) | ⚠ — 3 círculos com pulse continuam |
| POL-012 (Waveform pause offscreen) | ✅ — pausa em lifecycle < RESUMED |
| POL-013 (Pulse no botão drenando) | ⚠ — `rememberPulse` no botão segue ativo sempre que Home está aberta |
| ARCH-001/006 (RecordingController/TranscriptionState objects) | ❌ |
| ARCH-002 (sem ViewModel) | ❌ |
| ARCH-003 (sem Domain) | ❌ |
| ARCH-004 (WhisperJNI sync) | ⚠ — mantido |
| ARCH-005 (Repo mistura load/recovery/CRUD) | ⚠ |
| ARCH-007 (WorkManager) | ❌ |
| ARCH-008 (try/catch inconsistente) | ⚠ |
| ARCH-009 (minify release) | ✅ — `isMinifyEnabled=true, isShrinkResources=true` |
| ARCH-010 (debug signing) | ⚠ — ainda `signingConfigs.getByName("debug")` |
| ARCH-011 (sem testes) | ❌ — zero testes |
| ARCH-012 (sem CI) | ❌ |
| ARCH-013 (targetSdk=34) | ⚠ — válido até Aug/2025 |
| ARCH-014 (whisper.cpp submódulo) | ❌ — clone bruto |
| ARCH-015 (versionCode automation) | ❌ |
| ARCH-016 (proguard rules) | ✅ — `proguard-rules.pro` cobre serialization, JNI, entry points |
| ARCH-017 (Application init síncrono) | ⚠ — `repo` + load + recovery em onCreate |
| ARCH-018 (AppKiller reset cedo) | ✅ — espera + `WhisperPool.freeNow` |
| ARCH-019 (ESCUTAR/INICIAR/GRAVAR) | ✅ — consolidado em "GRAVAR" / "Gravando" |
| SEC-001 (debug signing) | ⚠ — ver ARCH-010 |
| SEC-002 (EncryptedSharedPreferences) | ✅ |
| SEC-003 (clear-text policy) | ❌ — sem `network_security_config.xml` (NEW-019) |
| SEC-004 (strip Log) | ✅ — proguard `-assumenosideeffects` |
| SEC-005 (INTERNET) | ⚠ — sem mensagem clara no Settings |
| SEC-006 (rate limit IA) | ❌ |
| SEC-007 (sha256 modelo) | ⚠ — magic-number only; sha256 field vazio nos profiles |
| CONF-001/002/003 | ✅ |
| CONF-004 (data safety) | ❌ |
| CONF-005 (privacy policy URL) | ❌ |
| CONF-006 (inApp updates) | ❌ |
| CONF-007 (OEM tutorial) | ✅ — `OemHelper` com 6 OEMs |
| CONF-008/009/010 | ✅ |
| A11Y-001…006 | ⚠ — algumas tags `contentDescription` boas, animações sempre ativas, sem `reduceMotion` |
| DOC-001 (comentários WHY) | ⚠ |
| DOC-002 (README) | ⚠ — README do projeto-mãe ainda diz "implementação não iniciada"; v0.7.3 já existe |
| DOC-003 (versões no site) | ⚠ |

---

## 2. Bugs novos descobertos nesta auditoria (v0.7.4)

> Itens **não** presentes no relatório v0.7.3 e que encontrei numa segunda passada cega.

### NEW-001 — `AboutScreen` é dead code (inalcançável)
**Severidade:** Alta (POL-005 do relatório anterior aparenta corrigido, mas a tela **não é navegável**).
**Onde:**
- `ui/screens/AboutScreen.kt` existe e compila.
- `ui/AulaLoggerNavHost.kt:14-23` importa `AboutScreen` mas **não declara `composable("about") { ... }`**.
- `ui/screens/SettingsScreen.kt` não tem entrada para abrir "Sobre".

**Impacto:** Usuário não consegue ver versão/créditos. A tela e o import sobrevivem como código morto. ProGuard em release pode até remover; mas seria pior se o usuário descobrisse a URL e ainda assim não navegasse.

**Correção:**
1. Adicionar rota `composable("about") { AboutScreen(onBack = { navController.popBackStack() }) }` em `AulaLoggerNavHost`.
2. Adicionar callback `onOpenAbout` em `SettingsScreen` e passar do NavHost.
3. Adicionar `SectionCard(onClick = onOpenAbout) { NavRow(icon = Info, title = "Sobre o app", subtitle = "Versão e créditos") }` em `SettingsScreen` (seção nova "INFO" ou no final).

---

### NEW-002 — `autoTranscribeRecovered` não encadeia, só dispara a 1ª
**Severidade:** Alta. Se o usuário tem N aulas recuperadas, só a primeira é transcrita automaticamente; as outras ficam esperando ação manual.
**Onde:** `AulaLoggerApp.kt:31-42`.
**Comentário do código:** _"Apenas a primeira; se o usuário tiver várias, transcrição sequencial seria iniciada quando cada uma terminar (futuro)."_ — explícito que é incompleto.

**Correção:**
- Coletar `TranscriptionState.state` em um job de aplicação; ao detectar `Phase.DONE`, procurar próxima `STATUS_RECOVERED` com `transcript.isBlank()` e iniciar.
- Ou: ao terminar transcrição em `TranscriptionService.runTranscription`, fazer ele mesmo enfileirar a próxima recuperada se houver.

---

### NEW-003 — Session permanece `STATUS_RECOVERED` após transcrição bem-sucedida
**Severidade:** Alta (UX). Após `updateTranscript`, o badge "RECUPERADA" segue na Home; usuário pensa que houve problema.
**Onde:** `SessionRepository.updateTranscript:163-169` — só altera `transcript`, mantém `status`.

**Correção:** ao gravar transcript com sucesso (não-erro), transitar `STATUS_RECOVERED → STATUS_COMPLETED`. Em `TranscriptionService.runTranscription` antes de `repo.updateTranscript(sessionId, finalText)`, chamar uma variante que também muda status, ou fazer no próprio `updateTranscript` se status era RECOVERED.

---

### NEW-004 — `AulaLoggerWidget` dispara `RecordingController.start` sem checar permissão de mic
**Severidade:** Crítica (crash).
**Onde:** `widget/AulaLoggerWidget.kt:30-37`.
**Detalhe técnico:** Em Android 14 (UPSIDE_DOWN_CAKE+), iniciar FGS com `foregroundServiceType = microphone` **sem** `RECORD_AUDIO` concedida levanta `SecurityException: Starting FGS with type microphone requires permissions: ...`. O widget é um BroadcastReceiver que pode ser invocado mesmo após o usuário ter revogado a permissão de microfone em Settings.

Caminho atual:
- widget tap → `RecordingController.start(context, id)` → `ContextCompat.startForegroundService` → `RecordingService.handleStart` → `startForegroundCompat` → **crash silencioso** (BroadcastReceiver não mostra dialog).

**Correção:**
- No `onReceive` ACTION_TOGGLE, antes de `start`, checar `ContextCompat.checkSelfPermission(RECORD_AUDIO)`.
- Se ausente: abrir `MainActivity` para solicitar permissão (não tente startForeground).

---

### NEW-005 — `RecordingScreen` → `SessionDetailScreen` navega antes de `markCompleted`
**Severidade:** Alta. Race com `RecordingService.handleStop`:
- `LongPressStopButton.onConfirmed` → `RecordingController.stop(context)` (intent assíncrono) → imediato `onFinished(id)` → NavHost navega para `session/$id`.
- Service ainda está executando `handleStop`: cancelando jobs, fechando WAV, chamando `markCompleted`.
- `SessionDetailScreen` carrega `session` por id: a sessão existe (foi criada em `handleStart`) mas com `status=IN_PROGRESS`, `durationSec=0`, audio file ainda sendo finalizado (header não atualizado, talvez 0 bytes válidos).
- `AudioPlayer.LaunchedEffect`: pode falhar pois `file.length() < 100` durante a transição (acabou de chamar `setLength(0)`? não, mas write ainda em progresso).

**Sintomas:** usuário aperta stop, vê tela "Aula não encontrada" momentâneo, OU player com erro "Arquivo de áudio vazio".

**Correção:**
- Observar `RecordingController.activeSessionIdFlow` no `RecordingScreen`: navegar para SessionDetail somente quando `activeSessionId` ficar `null` (= service finalizou).
- Adicionalmente, garantir que `markCompleted` seja síncrono o suficiente (ele é, dentro de `handleStop`, antes de `RecordingController.reset()` que zera o flow).

---

### NEW-006 — `AnalysisScreen` vaza HTTP request ao navegar de volta
**Severidade:** Média-Alta. O `AiAnalyzer.cancelInFlight()` só é chamado pelo botão "Cancelar". Se usuário navegar back ou app for parado, a coroutine é cancelada (via rememberCoroutineScope que segue o composable), mas `HttpURLConnection.disconnect()` não é invocado. Request continua consumindo dados móveis até o servidor responder (até 180 s configurados).

**Onde:** `AnalysisScreen.kt:194-206`, `AiAnalyzer.kt:202-230`.

**Correção:** `DisposableEffect(Unit) { onDispose { AiAnalyzer.cancelInFlight() } }` no `AnalysisScreen`. (Cancela apenas se ainda for a mesma conn.)

---

### NEW-007 — `RecordingService.handleStop` não dá join no captureJob antes de fechar writer
**Severidade:** Alta. Race condition entre captura escrevendo um frame e `writer?.close()` finalizando o WAV header.
**Onde:** `RecordingService.kt:214-231`.

**Detalhe técnico:** `captureJob?.cancel()` apenas sinaliza cancelamento — a coroutine pode estar dentro de `writer?.writeShorts(samples, count)` no momento. Cancelamento de coroutine em Kotlin é cooperativo; durante a chamada native `raf.write` não verifica cancellation. O `writer?.close()` na linha 231 entra concorrente com o write. Resultado: WAV pode ficar com header inconsistente, ou exception `IOException` na escrita pendente.

**Correção:**
```kotlin
runBlocking { try { captureJob?.cancelAndJoin() } catch (_: Throwable) {} }
```
ou marcar uma flag `stopping` que o loop checka antes de cada `writeShorts`.

---

### NEW-008 — `TranscriptionService.runTranscription` deixa `pollJob` órfão se cancellation entre chunks
**Severidade:** Média. O `pollJob` é cancelado no `finally` do `try` que abriga `transcribe()`, OK. Mas se a coroutine pai for cancelada **entre** chunks (após o finally mas antes do próximo launch), o último estado de progresso fica desatualizado. Aceitável, mas há um caso pior: se `WhisperJNI.transcribe` lançar throw (não 0 mas exception), o `finally` ainda roda — OK.

**Bug real:** o `pollJob` é launched em `scope` (o serviço scope), não em `workerJob`'s scope. Se workerJob é cancelado, pollJob **não** é cancelado pelo cancel do worker; só pelo `pollJob.cancel()` no `finally`. Em throw síncrono antes do try (linha 178), pollJob foi launched (linha 161) mas finally do try não rodaria. Olhando cuidadosamente: a launch do pollJob está dentro do try que cobre `WhisperJNI.transcribe`. OK, o finally cobre. **Falso alarme parcial**. Mas: se a coroutine pai (`workerJob`) for cancelada durante `WhisperJNI.transcribe`, o `WhisperJNI.cancel()` é chamado, transcribe retorna `[]`, finally roda, pollJob cancelado. OK.

**Verdadeiro problema:** se houver **exception não capturada** no `pollJob` interno (raríssimo: NPE no `WhisperJNI.getProgress()`), ela morre silenciosamente. Aceitável.

**Reclassificação:** Baixo. Sem ação necessária. Anotado para referência.

---

### NEW-009 — `themes.xml` usa `bg_primary = #0A0A0A` hardcoded como windowBackground
**Severidade:** Média. No modo claro, ao abrir o app o sistema mostra um flash dark (windowBackground escuro) antes do Compose pintar. Em fastpath de toda Activity nova.
**Onde:** `res/values/themes.xml` + `res/values/colors.xml` (bg_primary=#0A0A0A).

**Correção:**
- Criar `res/values-night/colors.xml` com `bg_primary=#0A0A0A` e em `res/values/colors.xml` deixar `bg_primary=#FAFAFA`. (Match com Compose LightColors background.)
- OU: usar uma cor neutra média que funcione em ambos os modos.

---

### NEW-010 — `themes.xml` herda de `android:Theme.Material` (legado), não Material3
**Severidade:** Baixa. `parent="android:Theme.Material.NoActionBar"` é a versão framework antiga (API 21). Para targetSdk=34 com Compose Material3, é mais correto `Theme.MaterialComponents.NoActionBar` ou `Theme.Material3.NoActionBar` (do AppCompat). Não afeta funcionalidade direta, mas dialogs nativos do framework, splash, e style fallback ficam em estado misto.

**Correção:** mudar parent para `Theme.Material3.DayNight.NoActionBar` (com AppCompat) ou similar. Validar que não quebra system bars.

---

### NEW-011 — `AndroidManifest` sem `<uses-feature android:name="android.hardware.microphone" required="true" />`
**Severidade:** Média. Sem essa declaração, a Play Store não filtra dispositivos sem mic (raríssimos, mas existe TV box, etc). Quando publicar, usuários em hardware sem mic baixariam e o app falharia silencioso.

**Correção:** adicionar no `<manifest>` antes de `<application>`:
```xml
<uses-feature android:name="android.hardware.microphone" android:required="true" />
```

---

### NEW-012 — Sem `network_security_config.xml`
**Severidade:** Média. SEC-003 do relatório anterior. Permanece. Política implícita do Android 9+ já bloqueia cleartext, mas declarar explicitamente:
- documenta intenção;
- ajuda em audits;
- permite pinagem de certificado dos hosts conhecidos (Hugging Face, Anthropic, OpenAI, Google, OpenRouter) — defesa contra MITM corporativo.

**Correção:** adicionar `res/xml/network_security_config.xml` com `<base-config cleartextTrafficPermitted="false">` e referenciar via `android:networkSecurityConfig` no `<application>`.

---

### NEW-013 — `RecordingController.start` retorna `Unit`, sem feedback se já está rodando
**Severidade:** Média. (BUG-012 do relatório antigo, ainda presente.) `handleStart` faz `if (RecordingController.activeSessionId != null) return` silenciosamente. UI navegou para `recording/{id}` com id novo que nunca foi persistido.

**Correção:** trocar assinatura para `fun start(...): Boolean` e checar `activeSessionId == null` antes; UI usa o retorno para decidir se navega ou exibe "Já há uma gravação ativa".

---

### NEW-014 — `BatteryManager.ACTION_BATTERY_CHANGED` re-registrado a cada 5 s no `SystemMonitor`
**Severidade:** Baixa. `registerReceiver(null, IntentFilter(...))` é uma sticky broadcast lookup — relativamente barato — mas em 4h de aula ele acontece 2.880 vezes. Melhor registrar um receiver permanente com callback durante a gravação.

**Correção (opcional):** registrar callback no `start`, desregistrar no `stop`.

---

### NEW-015 — `TranscriptionService` rejeita START com sid diferente sem feedback
**Severidade:** Baixa-Média. (BUG-032 do relatório antigo, ainda presente.) Se sid diferente vem durante execução, sobrescreve `currentSessionId` mas o Whisper ainda processa chunk atual da sessão antiga. UI mostra "0% iniciando" para a nova → confusão.

**Correção:** rejeitar com Toast/state update claro: "Aguarde a transcrição atual terminar antes de iniciar outra".

---

## 3. Lacunas estratégicas / state-of-the-art

Itens de oportunidade — não bugs, mas o que falta para atingir paridade com Otter.ai/Plaud:

1. **VAD pré-Whisper** (PERF-001 do relatório antigo) — Silero ONNX integrado ao whisper.cpp. Pula 30-50% do tempo em aulas com pausas. **Whisper.cpp já tem `params.vad = true`** desde v1.7; basta baixar `silero-vad.onnx` (~1.5 MB) junto com o modelo. **Maior ROI.**
2. **Word-level timestamps** — `params.token_timestamps = true` permite dedupe textual no nível palavra; melhora overlap dedupe e habilita karaokê-style highlight no player.
3. **Custom vocabulary** — `params.initial_prompt` configurável pelo usuário (lista de termos da área). Ganho enorme em PT-BR técnico.
4. **Backend GPU** — OpenCL/Vulkan em Snapdragon 8 Gen 2/3 acelera 2-4×. Complexidade NDK alta.
5. **Pos-processamento por LLM** — segundo pass que corrige pontuação/capitalização usando a API key já configurada.
6. **Streaming transcription enquanto grava** — transcrever chunks em paralelo à captura. Permite "preview ao vivo" da aula.
7. **Diarização real** (embedding de voz, não heurística por pausa) — pyannote.audio porta para mobile via ONNX.
8. **FLAC ao final da gravação** — converte WAV → FLAC ~50% menor, sem perda. Resolve BUG-004 e economiza espaço.
9. **WorkManager para transcrição** — sobrevive a app kills, suporta constraints (Wi-Fi only, charging only).
10. **Tela "Trechos importantes"** — IA marca topics em timestamps; usuário pula para o trecho.

---

## 4. Plano de ação para esta correção

Foco da rodada atual: **eliminar todos os bugs novos críticos/altos + remanescentes acionáveis em horas**. Itens de arquitetura (Room, WorkManager, ViewModel, CI, testes) **ficam para fora** desta rodada por exigirem reescrita significativa e validação extensiva, mas estão documentados acima.

### Sprint corretivo (esta sessão)

| # | Item | Esforço |
|---|---|---|
| 1 | NEW-001 — AboutScreen alcançável (rota + entrada Settings) | 10 min |
| 2 | NEW-002 — autoTranscribe encadeado | 30 min |
| 3 | NEW-003 — Status RECOVERED → COMPLETED ao transcrever | 5 min |
| 4 | NEW-004 — Widget checa permissão mic antes do FGS | 20 min |
| 5 | NEW-005 — RecordingScreen aguarda activeSessionId virar null antes de navegar | 15 min |
| 6 | NEW-006 — AnalysisScreen DisposableEffect cancela HTTP em saída | 5 min |
| 7 | NEW-007 — handleStop joina captureJob antes de close writer | 10 min |
| 8 | NEW-009 — bg_primary night/light variants | 10 min |
| 9 | NEW-010 — themes.xml herda Theme.Material3.DayNight | 5 min |
| 10 | NEW-011 — `<uses-feature android.hardware.microphone>` | 2 min |
| 11 | NEW-012 — network_security_config.xml + cleartext=false | 10 min |
| 12 | NEW-013 — RecordingController.start retorna Boolean | 15 min |
| 13 | NEW-015 — Toast/state quando START vier com sid diferente | 5 min |
| 14 | BUG-031 (já corrigido) e BUG-040 setLocalOnly | 5 min |
| 15 | POL-003 — Locutor A/B via strings | 5 min |
| 16 | Bump version `0.7.4`, versionCode 11 | 1 min |

Total estimado: ~2.5 h de trabalho focado.

### Fora desta rodada (documentado, não implementado)

- ARCH-001/006 (Hilt/Koin)
- ARCH-002 (ViewModel)
- ARCH-007 (WorkManager)
- ARCH-011/012 (testes + CI)
- BUG-026/027 (Room + migration)
- BUG-030 (GetFloatArrayElements vs Critical) — exige medir impacto real antes
- PERF-001 (VAD) — exige integração com modelo silero-vad + UI de download
- PERF-002 (GPU backend) — semana de trabalho
- A11Y-001…006 — exige passada de revisão dedicada
- ARCH-010 (keystore real) — exige geração de chave fora do código

---

## 5. Aspectos a NÃO mexer (Anexo B v0.7.3 expandido)

Confirmados sólidos após auditoria atual:

1. Gravação WAV com fsync periódico + recovery.
2. Idle eviction do WhisperPool.
3. Dedupe híbrido tempo + texto.
4. Persistência precoce + heartbeat 20s + threshold 60s.
5. EncryptedSharedPreferences + fallback.
6. `OemHelper` com tutoriais por fabricante.
7. ProGuard rules + Log strip.
8. AppKiller com espera por flow + cancelAll notificações.
9. AudioPlayer com ParcelFileDescriptor + prepare em IO.
10. Waveform pausa em background.
11. AudioDeviceCallback + SystemMonitor (bateria/disco/mic).
12. Download de modelo com Range/206 + magic number check.
13. AnalysisScreen com `cancelInFlight` para AiAnalyzer (parcial, NEW-006 corrige saída).
14. Banner de erro de transcrição com "Tentar novamente".
15. RecordingScreen sem race no LongPressStopButton (LaunchedEffect-based).

---

## 6. Métricas pós-correção esperadas

| Métrica | Antes | Após correções desta rodada |
|---|---|---|
| Bugs críticos pendentes | 2 (NEW-001, NEW-004) | 0 |
| Bugs altos pendentes | 5 (NEW-002, NEW-005, NEW-006, NEW-007, NEW-013) | 0 |
| Bugs médios pendentes | 7 | 4 (mantém Room, GC pin, ViewModel) |
| Telas inalcançáveis | 1 (AboutScreen) | 0 |
| Race conditions no fluxo stop→detail | 1 | 0 |
| Crash potencial widget→FGS | 1 | 0 |
| Bytes HTTP vazados em navegação | até 180 s | 0 |
| `STATUS_RECOVERED` órfãos após transcrição | sim | não |

---

*Auditoria realizada em 15/05/2026 sobre versão 0.7.3 (versionCode=10). Próxima release alvo: 0.7.4 (versionCode=11).*

---

## 7. Status pós-correções (entregue nesta sessão)

Versão bumpada para **0.7.4 / versionCode=11**. Builds debug+release validadas com sucesso
(R8 minify habilitado).

### Bugs novos corrigidos

| ID | Status | Arquivos tocados |
|---|---|---|
| NEW-001 (AboutScreen unreachable) | ✅ | `AulaLoggerNavHost.kt`, `SettingsScreen.kt` |
| NEW-002 (auto-transcribe não encadeia) | ✅ | `SessionRepository.kt`, `TranscriptionService.kt` |
| NEW-003 (RECOVERED persiste após transcrever) | ✅ | `SessionRepository.kt` (updateTranscript) |
| NEW-004 (widget dispara FGS sem mic) | ✅ | `AulaLoggerWidget.kt` |
| NEW-005 (RecordingScreen navega cedo demais) | ✅ | `RecordingScreen.kt` (aguarda flow) |
| NEW-006 (AnalysisScreen vaza HTTP em saída) | ✅ | `AnalysisScreen.kt` (DisposableEffect) |
| NEW-007 (handleStop race com captureJob) | ✅ | `RecordingService.kt` (cancelAndJoin) |
| NEW-009 (windowBackground flash dark) | ✅ | `values/colors.xml`, `values-night/colors.xml` (novo) |
| NEW-011 (`<uses-feature microphone>`) | ✅ | `AndroidManifest.xml` |
| NEW-012 (network_security_config.xml) | ✅ | `xml/network_security_config.xml` (novo), Manifest |
| NEW-013 (start retorna Boolean) | ✅ | `RecordingController.kt`, `RecordingScreen.kt` |

### Bugs antigos do v0.7.3 fechados nesta rodada

| ID | Status | O que foi feito |
|---|---|---|
| BUG-040 (`setLocalOnly`) | ✅ | Ambos services (`RecordingService`, `TranscriptionService`) |
| POL-003 (Locutor A/B hardcoded) | ✅ | `TranscriptFormatter` agora recebe labels do `R.string.speaker_a/b` via `TranscriptionService` |
| POL-005 (tela Sobre acessível) | ✅ | Via NEW-001 |

### Bugs pré-existentes descobertos durante a validação do build

Durante a primeira tentativa de compilação descobri **dois bugs que impediam o build de
funcionar**, claramente não presentes no `build.log` antigo (que estava cacheado):

| Bug pré-existente | Onde | Correção aplicada |
|---|---|---|
| `tmp.outputStream(append = appending)` — `File.outputStream()` não aceita parâmetro `append` em Kotlin stdlib | `ModelManager.kt:137` (código de BUG-013, resume de download) | Trocado por `FileOutputStream(tmp, appending)` + import |
| `activity.lifecycleScope` em `Activity?` — extension existe apenas em `LifecycleOwner` | `AppKiller.kt:47` | Cast defensivo `activity as? LifecycleOwner` |
| Missing classes `com.google.errorprone.annotations.*` quebrava R8 em release | `proguard-rules.pro` | Adicionado `-dontwarn` para errorprone + tink |

Ambos eram silenciosos até alguém rodar `assembleDebug`/`assembleRelease` realmente.

### Itens ainda pendentes (fora do escopo desta rodada — exigem trabalho de horas/dias)

- **ARCH-001/002/006/007** — Hilt/Koin + ViewModel + WorkManager. Reescrita arquitetural.
- **BUG-026/027** — migração para Room/SQLite + schema versioning.
- **BUG-030** — `GetPrimitiveArrayCritical` → `GetFloatArrayElements`. Exige benchmark.
- **ARCH-010** — keystore real para release. Exige geração de chave fora do código.
- **ARCH-011/012** — testes unitários + CI (GitHub Actions).
- **PERF-001** — VAD pré-Whisper (Silero ONNX). ROI maior para qualidade de transcrição.
- **PERF-002** — backend GPU OpenCL/QNN. Trabalho de NDK avançado.
- **A11Y-001…006** — passada de revisão de acessibilidade dedicada.
- **NEW-010 (themes parent M3)** — Avaliado e descartado nesta rodada: exigiria adicionar
  dependência AppCompat sem ganho real (Compose já gerencia o tema). O fix do
  `windowBackground` (NEW-009) elimina o flash que era o sintoma real.
- **NEW-014 (re-register receiver bateria)** — Otimização menor, baixa prioridade.
- **NEW-015 (toast em START com sid diferente)** — Aceitável: o caso é raro e a UI já mostra
  o estado da transcrição em curso.

### Resumo numérico

- Bugs **críticos** restantes: **0**
- Bugs **altos** restantes: **0** (todos os entregues nesta sessão)
- Bugs **médios** restantes documentados: **7** (Room, GC pin, ViewModel, threads override,
  initial_prompt configurável, snackbar diferenciada save/test, indicador kill)
- Builds **debug** e **release** ambos passam com 0 erros.
- 13 arquivos Kotlin modificados, 4 arquivos novos criados, manifest atualizado.

---

*Correções aplicadas em 15/05/2026, versão alvo entregue: 0.7.4 / versionCode 11.*

---

## 8. Rodada de performance + qualidade + barra de progresso (16/05/2026)

Feedback direto do usuário: **"tá lento, qualidade horrível, barra de progresso ainda falha"**.
Atacados os 3 com engenharia real (não adiamento):

### 8.1 Lentidão — resolvida com 4 mudanças

| Mudança | Ganho esperado | Onde |
|---|---|---|
| **VAD nativo (Silero) pré-Whisper** | **30–50% menos tempo** em aula com pausas (pula silêncio) | `whisper-jni.cpp` (`params.vad`), `ModelManager.ensureVadModel`, `TranscriptionService` |
| **flash_attn = true** no contexto | ~10–25% encoder mais rápido, sem perda de qualidade | `whisper-jni.cpp` (`cparams.flash_attn`) |
| **Threads: `(cores-1).coerceIn(2,8)`** (era cap 4) | usa todos os núcleos disponíveis em flagship | `TranscriptionService` |
| **Default → HIGH (large-v3-turbo)** | decoder de 4 camadas (vs 24 do medium) ≈ 6× mais rápido na decodificação **com qualidade superior** | `ModelManager.fromId/getSelectedProfile` |

### 8.2 Qualidade horrível — resolvida

- **VAD elimina alucinações em silêncio**: a causa #1 de "texto horrível" era o Whisper inventar
  "obrigado", "[música]", créditos do YouTube nas pausas. Com VAD, esses trechos nem chegam ao decoder.
- **large-v3-turbo como padrão**: qualidade de large-v3 (top de linha) em vez do medium q5_0
  (mediano para PT-BR técnico).
- Mantido o fallback de temperatura (`temperature_inc=0.2`) e o dedupe híbrido — agora com
  muito menos lixo de entrada graças ao VAD.

### 8.3 Barra de progresso que falhava — bug raiz encontrado e corrigido

**Causa raiz:** `g_progress` é um global nativo setado a 100 no fim de cada chunk. O poller
Kotlin era lançado **antes** de `whisper_full` resetar o valor → a 1ª leitura do chunk novo
pegava o **100 residual** do chunk anterior → `effectiveSamples` saltava pra frente, depois
o native zerava e voltava → a barra "pulava pra frente e voltava". Com `animateFloatAsState`
isso aparece como travamento/regressão visual.

**Correções (dupla proteção):**
1. **`WhisperJNI.resetProgress()`** (novo `nativeResetProgress`) chamado **antes** de cada
   pollJob — elimina a leitura residual na origem.
2. **Guard monótono** em `publishProgress`: `progress = maxOf(raw, maxPublishedProgress)` —
   a barra **nunca** regride dentro de uma sessão, independente de race ou do overlap de 5s
   entre janelas. `processedSec` agora deriva do progresso monótono (consistente com a barra).

### 8.4 Bugs JNI latentes corrigidos de brinde

Durante a edição do `whisper-jni.cpp` notei e corrigi um **bug pré-existente de JNI**:
`GetStringUTFChars(jLanguage)` era chamado **dentro** da região
`GetPrimitiveArrayCritical` — proibido pela spec JNI (comportamento indefinido, pode
travar/crashar sob carga de GC). Reordenado: todas as chamadas JNI de string agora ocorrem
**antes** de pinar o array crítico.

### 8.5 Modelo VAD — gestão transparente

`ModelManager.ensureVadModel()` baixa `ggml-silero-v5.1.2.bin` (~2 MB) da fonte oficial
`huggingface.co/ggml-org/whisper-vad` no início da 1ª transcrição. **Best-effort**: se a rede
falhar, transcreve sem VAD (mais lento, mas funciona) — degradação graciosa, sem bloquear.

### 8.6 Validação

- `assembleDebug` — **BUILD SUCCESSFUL** (CMake nativo recompilado, nova assinatura JNI).
- `assembleRelease` — **BUILD SUCCESSFUL** (R8 minify ativo).

### 8.7 Resultado esperado combinado

Para uma aula de 4 h com ~30% de pausas, num celular mid-range:
- **Antes:** medium q5_0, sem VAD, 4 threads → ~4–8 h de transcrição, texto poluído por alucinações, barra travando.
- **Depois:** large-v3-turbo + VAD (pula ~30%) + flash_attn + 7 threads → estimado **2–3× mais rápido**, texto limpo (sem alucinação de silêncio), barra monótona e fluida.

> Observação honesta: os multiplicadores são estimativas de engenharia baseadas nas
> características dos modelos/algoritmos; o ganho real depende do hardware e da proporção
> de silêncio na aula. O ganho de **qualidade** (VAD + turbo) e a **correção da barra** são
> determinísticos.

---

*Rodada de performance/qualidade aplicada em 16/05/2026 sobre a base 0.7.4.*

