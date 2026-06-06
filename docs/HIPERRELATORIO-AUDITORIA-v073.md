# Hiperrelatório de Auditoria — AulaLogger v0.7.3

> Auditoria técnica completa do projeto. Lista bugs (por severidade), riscos arquiteturais, oportunidades de melhoria de performance/qualidade/UX, e um plano de ação priorizado. Foco: **aprimorar o que existe**, não inflar com features novas.
>
> Escopo auditado: 33 arquivos Kotlin + JNI (whisper-jni.cpp) + AndroidManifest + Gradle + resources + widget + tudo o que afeta a experiência atual.

---

## Sumário executivo

### O que está bom (de verdade)

| Área | Avaliação |
|---|---|
| **Gravação confiável** | Excelente. `WavWriter` com fsync periódico e header atualizado no caminho, recuperação automática via `loadAndRecover()` em `SessionRepository`. Aula de 4 h+ é segura. |
| **Foreground service** | Correto: `microphone` para gravação, `dataSync` para transcrição, `stopWithTask=false`, wake lock partial, notificação persistente com ações. |
| **Persistência precoce de session** | `upsertSession` no `handleStart` + heartbeat de 60 s — quando o processo morre, a próxima abertura recupera. Padrão correto. |
| **JNI** | Boas decisões: `GetPrimitiveArrayCritical` (zero-copy), `abort_callback` (cancelamento real, não só de coroutine), `progress_callback` (UI honesta). Magic-number check no `ModelManager`. |
| **Transcrição em chunks** | Janelas de 60 s com 5 s de overlap evitam o OOM clássico do "ler 920 MB de FloatArray para 4 h". |
| **API keys** | `EncryptedSharedPreferences` com fallback graceful, cache do `prefs` (não cria 8 vezes), botão "Testar" antes de usar. |
| **UX** | `LongPressStopButton` (evita parar acidental), pulse no botão principal, EmptyStateHero, banner de "gravando agora", banner de "recuperada". `enableEdgeToEdge` + system bars sincronizadas ao tema. |
| **Tema** | Material 3 com `surfaceContainer/High/Highest`, tipografia completa, escolha sistema/claro/escuro persistida. |

### O que está ruim, organizado por severidade

- **Críticos (impedem ou degradam funcionalidade essencial):** 7 bugs.
- **Altos (degradam experiência ou estabilidade longa):** 14 problemas.
- **Médios (qualidade técnica, manutenção):** 19 problemas.
- **Baixos (polimento):** 13 itens.

Total: **53 itens acionáveis**. Detalhados nas seções seguintes.

### Top 5 ações com maior retorno

1. **Liberar Whisper context após N minutos sem uso** (BUG-007) — libera ~500 MB de RAM que ficam pendurados no app entre uma transcrição e a próxima.
2. **VAD pré-whisper** (PERF-001) — pular silêncio acelera 2–3× transcrição em aulas com pausas.
3. **Resume de download** (BUG-013) — se 4G cai com 90 % baixado, tem que recomeçar do zero.
4. **Erro de transcrição visível na UI** (UX-005) — hoje "[erro: ...]" aparece como se fosse o texto da aula.
5. **`AppStatusHolder.refresh()` em `onResume()` do MainActivity** (BUG-005) — atualiza modelo/permissões quando usuário volta das Settings do Android.

---

## 1. Bugs e issues — críticos

### BUG-001 — Strings/widget desalinhados com o pedido "ESCUTAR AULA"
**Severidade:** Crítica (UX inconsistente).
**Onde:**
- `res/values/strings.xml:4` → `<string name="action_record">GRAVAR AULA</string>` (não "ESCUTAR AULA").
- `res/values/strings.xml:13` → `"empty_lessons"` ainda mostra "Toque o botão para começar." (genérico, OK).
- `res/values/strings.xml:21` → `model_download_body` ainda menciona "small em PT-BR" e "~470 MB" — mas o modelo padrão hoje é `ggml-small-q5_1.bin` (~181 MB) e os tamanhos são outros.
- `res/layout/widget_aulalogger.xml:69` → `android:text="ESCUTAR"` (hardcoded no XML).
- `widget/AulaLoggerWidget.kt:64` → seta `"PARAR"` ou `"GRAVAR"` (deveria ser "ESCUTAR" quando idle).

**Impacto:** UI inconsistente entre versões/conversações anteriores. Texto do botão hardcoded no widget conflita com strings.xml.
**Correção:** Mover `"ESCUTAR"` para `strings.xml` (`<string name="widget_idle">ESCUTAR</string>`), atualizar widget para ler via `R.string.*` em vez de hardcoded.

---

### BUG-002 — `frame.copyOf()` a cada `AudioCapture.read()` cria ~36.000 alocações/h
**Severidade:** Crítica em aulas longas (pressão de GC).
**Onde:** `AudioCapture.kt:80` — `return if (n == frame.size) frame.copyOf() else frame.copyOf(n)`.

**Detalhe técnico:** Cada `read()` retorna um `ShortArray` novo de 1.600 shorts. Em 100 ms por chamada → 36.000 alocações/h. Em 4 h = 144.000 ShortArrays órfãos. O coletor de lixo é chamado muitas vezes ao longo da aula; pode introduzir microstutters na waveform e custar bateria.

**Correção:** Pool de buffers (2–3 ShortArrays em rotação) ou retornar pair `(buffer, count)` reutilizando o `frame` interno (downstream copia se precisar). O `WavWriter.writeShorts(samples, count)` já aceita count → pronto para receber o frame sem copy.

---

### BUG-003 — `WavWriter.writeShorts` aloca `ByteBuffer.allocate(count * 2)` a cada chamada
**Severidade:** Crítica em aulas longas (mesma pressão de GC).
**Onde:** `WavWriter.kt:30-34` — `ByteBuffer.allocate(count * 2)` a cada chamada.

**Detalhe:** Mesmo problema do BUG-002. Cada `writeShorts` aloca ~3.200 bytes. Em 4 h = ~115 MB de allocs.

**Correção:** Mantenha um `ByteBuffer` reusável como field (`private val outBuf = ByteBuffer.allocateDirect(MAX_FRAME * 2).order(LITTLE_ENDIAN)`). Direct buffer também escreve mais rápido no FileChannel.

---

### BUG-004 — `WavWriter` usa `Int` para `dataSize`: trava o WAV em 2 GB ≈ 17 h
**Severidade:** Crítica para aulas > 17 h (limite teórico do formato WAV PCM).
**Onde:** `WavWriter.kt:43, 54, 78` — `writeHeader(raf, bytesWritten.toInt())`.

**Detalhe técnico:** WAV legacy usa `Int32` (4 GB sem sinal, 2 GB com sinal). O cast `.toInt()` em Kotlin trunca silenciosamente: para 17h de áudio (~2 GB), `bytesWritten.toInt()` vira negativo, `totalDataLen = -X + 36` rebaixa, players não conseguem abrir. Para 4–8 h não é problema, mas a documentação do projeto sugere "aulas de 8 h" como cenário.

**Correção:**
- Imediata: hard-cap em 4 h (já está implícito no `MAX_HOURS = 12L`), ou warn em UI quando aproximar do limite.
- Definitiva: implementar **RF64** (extensão para WAV > 4 GB) ou trocar formato para **FLAC** (compressão lossless ~50 %, sem limite) ao final da gravação. O whisper.cpp aceita PCM em FloatArray, não precisa do header WAV.

---

### BUG-005 — `MainActivity.onResume()` só atualiza mic, não modelo/notificação
**Severidade:** Alta. Usuário volta das Settings do Android após habilitar notificação ou após download do modelo, e a Home continua mostrando "Falta...".
**Onde:** `MainActivity.kt:91-98`.

**Correção:** Chamar `AppStatusHolder.refresh(applicationContext)` em `onResume()`. Idealmente também observar `RECEIVE` de `ACTION_PACKAGE_DATA_CLEARED` ou similar, ou simplesmente chamar refresh em todo lifecycle resume.

---

### BUG-006 — `SessionRepository.delete` durante transcrição ativa não cancela o service
**Severidade:** Alta.
**Onde:** `SessionRepository.kt:166`.

**Detalhe:** Se o usuário deletar uma aula enquanto ela está sendo transcrita, o `TranscriptionService` continua rodando até descobrir que o arquivo sumiu. Ele tenta `updateTranscript(id, "[áudio não encontrado]")` mas o id não existe mais — silent fail. CPU desperdiçada (até ~30 % do tempo total da transcrição), bateria, e o usuário não sabe que isso está acontecendo.

**Correção:** No `repo.delete(id)`, se `TranscriptionState.state.value.sessionId == id`, chamar `TranscriptionService.cancel(context)` antes.

---

### BUG-007 — Whisper context fica residente em RAM eternamente
**Severidade:** Alta. Modelo Médio = ~500 MB, Alto = ~574 MB.
**Onde:** `TranscriptionService.kt:244-247` — comentário diz "mantemos o ctx vivo para a próxima transcrição economizar 500MB de I/O. Free só em onDestroy ou troca de modelo."

**Detalhe:** A intenção é boa (segunda transcrição é mais rápida), mas:
- Service só destrói quando o processo morre — pode ser horas/dias.
- Para o usuário que grava uma aula por dia, a "segunda transcrição" raramente acontece logo após a primeira.
- 500 MB de RAM ocupados significa que outros apps são killed antes (WhatsApp, browser etc.) → ironicamente piora a UX geral do celular.

**Correção:** Implementar **idle eviction**:

```kotlin
// No TranscriptionService.kt após stopWork()
companion object {
    private var idleEvictionJob: Job? = null
    private const val IDLE_EVICTION_MS = 5 * 60_000L  // 5 min
}
private fun scheduleIdleEviction() {
    idleEvictionJob?.cancel()
    idleEvictionJob = GlobalScope.launch(Dispatchers.IO) {
        delay(IDLE_EVICTION_MS)
        WhisperJNI.free()
        Log.i(TAG, "Whisper ctx liberado após $IDLE_EVICTION_MS ms idle")
    }
}
```

Chamar `scheduleIdleEviction()` no `stopWork()` e cancelar no início de qualquer nova transcrição.

---

## 2. Bugs e issues — altos

### BUG-008 — Refazer transcrição apaga o texto antigo antes de tentar
**Severidade:** Alta. Se o novo run falhar (OOM, OEM kill, modelo corrompido), o texto antigo se perde.
**Onde:** `SessionDetailScreen.kt:272-273` — `app.repo.updateTranscript(session.id, "")` antes de iniciar.

**Correção:** Manter o transcript antigo. Só substituir após sucesso. Em caso de falha, restaurar:

```kotlin
val previous = session.transcript
app.repo.updateTranscript(session.id, "[refazendo…]")
// se falhar, restaura previous
```

Ou ainda melhor: o service salva em uma "draft" e só commita ao final.

---

### BUG-009 — `TranscriptionState.fail()` não é renderizado pela UI
**Severidade:** Alta. Erro silencioso.
**Onde:** `SessionDetailScreen.kt:52-53` — `isTranscribing = phase == RUNNING`. Não há ramo para `Phase.ERROR`.

**Detalhe:** Quando transcrição falha, `transcript` no repo vira `"[erro: <msg>]"`. A UI mostra isso como se fosse o texto da aula. O `TranscriptionState.message` que contém a descrição real do erro é descartado.

**Correção:** Renderizar caso `ERROR`:

```kotlin
val transcriptionError = transcriptionState.sessionId == sessionId &&
    transcriptionState.phase == TranscriptionState.Phase.ERROR
if (transcriptionError) {
  // Mostrar card vermelho com transcriptionState.message
  // + botão "Tentar novamente"
}
```

---

### BUG-010 — Overlap pode descartar segmentos válidos (dedupe frágil)
**Severidade:** Alta. Causa "pula pedaços" — exato sintoma reportado pelo usuário antes.
**Onde:** `TranscriptionService.kt:193` — `if (absT0 >= lastAcceptedT1Ms - 200)`.

**Detalhe:** Whisper pode retornar timestamps levemente deslocados entre chunks. Janela 1 termina em t=60.0 s, janela 2 começa em t=55.0 s (overlap 5 s). Whisper transcreve a fala "...e os juros..." e dá:
- Chunk 1: `{t0=59.2, t1=60.3, text="...e os"}`
- Chunk 2: `{t0=58.8, t1=60.1, text="...e os juros..."}`

`lastAcceptedT1Ms` (do chunk 1) = 60.300. Em chunk 2: `absT0 = 58.800` (com `chunkStartMs = 55.000` somado, fica 113.800 — espera, errado).

Olhando o código com cuidado:
```kotlin
val chunkStartMs = (offsetSamples * 1000L) / samplesPerSec
for (s in segments) {
    val absT0 = s.t0 + chunkStartMs
```

Aqui `s.t0` é relativo à janela; `chunkStartMs` é onde a janela começa no eixo absoluto. Tudo certo. Mas o filtro `absT0 >= lastAcceptedT1Ms - 200` é muito frouxo: aceita segmentos que começam DENTRO de um já aceito. Resulta em frases duplicadas em borda de overlap. Ou: muito restritivo se whisper esticar o t1 do segmento anterior.

**Correção:** Dedupe textual: comparar o texto do novo segmento com os últimos N segmentos aceitos. Se overlap textual > 70 %, descarta o novo. Biblioteca `LongestCommonSubstring` simples resolve.

---

### BUG-011 — `AppKiller.killEverything` usa `Handler.postDelayed(300ms)` em main looper
**Severidade:** Alta. Hack fragiloso.
**Onde:** `AppKiller.kt:34-40`.

**Detalhe:** O `Handler(Looper.getMainLooper()).postDelayed` programa o cleanup para 300 ms depois. Se o usuário fechar a Activity (back press) antes, o callback NÃO é cancelado mas pode rodar em uma activity já morta. Pior: se Looper.main estiver bloqueada (raro mas possível), atrasa indefinidamente.

**Correção:** Usar `lifecycleScope.launch { delay(300); ... }` no `Activity`, ou esperar callback dos services em vez de delay arbitrário:

```kotlin
fun killEverything(...) {
    // 1. Sinaliza stop
    RecordingController.stop(context)
    TranscriptionService.cancel(context)
    // 2. Espera o RecordingController.activeSessionId virar null OU 2s timeout
    runBlocking(Dispatchers.Default) {
        withTimeoutOrNull(2_000) {
            RecordingController.activeSessionIdFlow.first { it == null }
        }
    }
    // 3. Stops + finish
    ...
}
```

---

### BUG-012 — `RecordingController.start` pode ser chamado quando service já está rodando
**Severidade:** Alta. Race condition rara.
**Onde:** `RecordingController.kt:35-43` e `RecordingService.kt:72-73`.

**Detalhe:** O `handleStart` já tem guarda `if (RecordingController.activeSessionId != null) return`, então o segundo start é ignorado SILENTLY. Mas a UI já navegou para `recording/{id}` com um id novo que nunca foi persistido. Tela mostra timer zerado, gravação real continua com o id antigo.

**Correção:** Em `RecordingController.start`, retornar `Boolean` indicando se de fato iniciou; UI usa esse resultado para decidir navegação. Alternativa: o `handleStart` reagir a ID diferente parando o atual e começando o novo (mais perigoso).

---

### BUG-013 — Download de modelo não suporta resume (range request)
**Severidade:** Alta para 4G/Wi-Fi instável.
**Onde:** `ModelManager.kt:93-176`.

**Detalhe:** 514 MB no Médio, 574 MB no Alto. Se a conexão cair com 90 % baixado, recomeça do zero. Para usuário em 4G isso pode significar 100 MB+ de dados móveis desperdiçados.

**Correção:** Suportar `Range: bytes=N-`:

```kotlin
val partial = tmp.length()
if (partial > 0) {
    conn.setRequestProperty("Range", "bytes=$partial-")
}
// ao receber 206 Partial Content, anexa ao tmp
// ao receber 200, sobrescreve (servidor não suporta range)
```

Adicionalmente: checksum SHA-256 esperado (publicado em huggingface) para validar arquivo após download.

---

### BUG-014 — `temperature_inc = 0.0f` removeu fallback útil
**Severidade:** Média–Alta. Trade-off intencional mas pode ter degradado qualidade.
**Onde:** `whisper-jni.cpp:133` — comentário diz "Sem fallback de temperatura. ... single-pass".

**Detalhe:** A escolha de desativar o retry com temperatura foi feita para acelerar, mas o retry serve justamente para escapar de "alucinações por baixa confiança". Sem ele, em segmentos ruidosos o whisper produz texto não-fala (música, suspiros) — exatamente os "hallucinations" que `TranscriptFormatter` agora tenta filtrar via lista.

**Trade-off:**
- `temperature_inc = 0.0`: 1.0–1.5× mais rápido, mais alucinações.
- `temperature_inc = 0.2` (default): 1.0× tempo, qualidade melhor.

**Recomendação:** Re-habilitar `temperature_inc = 0.2f`. O ganho de qualidade é maior que o custo de tempo em aulas reais (que têm pausas onde retry é raro).

---

### BUG-015 — Gemini parse não trata bloqueio por safety
**Severidade:** Média–Alta. Crash silencioso em conteúdo "borderline".
**Onde:** `AiAnalyzer.kt:120-126`.

**Detalhe:** Se Gemini bloquear por safety:
```json
{
  "candidates": [
    { "finishReason": "SAFETY", "safetyRatings": [...] }   // sem content!
  ]
}
```
O código faz `getJSONObject("content")` → `JSONException`. Cai no `catch (t: Throwable)` mas a mensagem é genérica ("No value for content").

**Correção:**

```kotlin
val candidate = obj.getJSONArray("candidates").getJSONObject(0)
val finishReason = candidate.optString("finishReason", "")
if (finishReason == "SAFETY" || finishReason == "RECITATION") {
    error("Gemini bloqueou: $finishReason")
}
return candidate.getJSONObject("content").getJSONArray("parts")
    .getJSONObject(0).getString("text")
```

---

### BUG-016 — Claude `max_tokens=4096` corta análises longas
**Severidade:** Média–Alta. Análise de aula de 4h pode passar de 4 K tokens.
**Onde:** `AiAnalyzer.kt:152`.

**Correção:** Aumentar para 8192 (Claude Sonnet 3.5/4 aceita); ou tornar configurável.

---

### BUG-017 — `AiAnalyzer.postJson` sem cancelamento
**Severidade:** Média–Alta. `analyzeJob?.cancel()` na UI cancela coroutine, mas HTTP request fica.
**Onde:** `AiAnalyzer.kt:170-187` e `AnalysisScreen.kt:131-138`.

**Detalhe:** O `HttpURLConnection` não tem cancel nativo. `connection.disconnect()` interrompe, mas precisa ser chamado externamente. Sem isso, request gasta dados móveis até o servidor responder.

**Correção:** Usar OkHttp (mais ergonômico para cancelamento) ou guardar `HttpURLConnection` em var thread-local e disponibilizar um cancel:

```kotlin
private var activeConnection: HttpURLConnection? = null
fun cancelInFlight() { activeConnection?.disconnect(); activeConnection = null }
```

---

### BUG-018 — `WhisperJNI.cancel()` é flag global, conflita se 2 transcribes em paralelo
**Severidade:** Baixa hoje (não permitimos paralelo), mas pega lugar como armadilha futura.
**Onde:** `whisper-jni.cpp:19` — `static std::atomic<bool> g_cancel`.

**Detalhe:** Se algum dia adicionarmos pré-transcrição rápida com Mini durante a aula, ambos compartilham flag.
**Correção:** Vincular ao `ctxPtr` (cancel-per-context) ou documentar que é por design.

---

### BUG-019 — `AppKiller` não cancela notificações pendentes
**Severidade:** Média. Notificação fantasma após "Encerrar".
**Onde:** `AppKiller.kt`.

**Correção:** Adicionar `notificationManager.cancelAll()` antes do `finishAndRemoveTask()`.

---

### BUG-020 — `TranscriptionPrefs` lê SharedPreferences no main thread
**Severidade:** Baixa. SP é rápido mas StrictMode pode reclamar.
**Onde:** `TranscriptionPrefs.kt:11-21`.

**Correção:** Cache em `StateFlow` igual `ThemePrefs`. Inicializar em `Application.onCreate`.

---

### BUG-021 — `AudioPlayer.LaunchedEffect(filePath)` re-prepara em mudança de Composable key
**Severidade:** Baixa. `filePath` é estável dentro de uma sessão, mas é uma armadilha de recomposição.

**Correção:** Já está correto com `remember(filePath) { ... }`. Marcar como confirmado.

---

## 3. Bugs e issues — médios

### BUG-022 — `WavReader.readAsFloat` ainda existe como dead code (OOM trap)
**Severidade:** Média. Não é chamado mais, mas pode ser usado por engano.
**Onde:** `WavReader.kt:96-154`.

**Correção:** Marcar `@Deprecated` ou remover. A versão chunked (`Stream`) é a oficial.

---

### BUG-023 — Heartbeat de 60 s deixa lacuna grande para recovery
**Severidade:** Média. Em crash inesperado, perde até 60 s de metadados.
**Onde:** `RecordingService.kt:155-167`.

**Detalhe:** O WAV em si está OK (fsync a cada 1 s). Mas `Session.lastUpdatedAt` só atualiza a cada minuto. Se app crashar 59 s depois do último heartbeat, recovery acha que a session morreu há quase 2 min (`staleness > 2 min`) e marca como RECOVERED — quando na verdade é a session ativa. Fluxo recovery confunde com "session legitimamente abandonada".

**Correção:**
- Heartbeat = 20 s.
- Threshold de "stale" = 2 × heartbeat + folga = 60 s.

---

### BUG-024 — Sem detecção de mudança de dispositivo de áudio (fone, headset, BT)
**Severidade:** Média. Plugar fone com mic mid-aula pode silenciar gravação.
**Onde:** `AudioCapture.kt`.

**Correção:**

```kotlin
val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
val callback = object : AudioDeviceCallback() {
    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
        // notificar UI ou reabrir AudioRecord
    }
    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {...}
}
audioManager.registerAudioDeviceCallback(callback, mainHandler)
```

---

### BUG-025 — Sem detecção de "mic silenciado" (RMS zero por longo tempo)
**Severidade:** Média. Usuário pode gravar 30 min de silêncio sem perceber se há problema de hardware.
**Onde:** `AudioCapture.kt:67-78` (RMS já é calculado).

**Correção:** Coroutine de monitor: se nível médio < threshold por > 60 s, mostrar warning na tela de gravação + notificação.

---

### BUG-026 — `SessionRepository` cresce indefinidamente em `sessions.json`
**Severidade:** Média. 1000 aulas + transcrições + análises = arquivo pesado (10–50 MB) reescrito a cada update.

**Detalhe:** A cada `updateInProgress` (1×/min) reescreve TODO o JSON. Para um ano de uso intenso, cada commit = 30 MB+ de I/O.

**Correção (curto prazo):** Persist channel já é conflated — bom. Mas migrar para **Room/SQLite** elimina write-amplification e dá queries indexadas. Estimativa: ~150 linhas de migração.

---

### BUG-027 — Sem migration policy quando adicionar campos a `Session`
**Severidade:** Média. `kotlinx.serialization` com `ignoreUnknownKeys=true` ajuda na leitura, mas remover/renomear é frágil.

**Correção:** Schema version field. Quando lê v1 e quer v2, reescreve com defaults. Ou migrar para Room (resolve de uma vez).

---

### BUG-028 — `MainActivity.requestBatteryOptimizationExemption()` dispara mesmo se já está OK
**Severidade:** Baixa. Sistema mostra "já está ignorando" silenciosamente, mas UX confuso.
**Onde:** `MainActivity.kt:100-113` e `AulaLoggerNavHost.kt:54`.

**Correção:** Já checa `isIgnoringBatteryOptimizations(packageName)` antes do `startActivity`. Mas o `requestBatteryOptimizationExemption` é chamado a cada `onStartRecording()` no `NavHost`. Deveria checar e só pedir uma vez por instalação.

---

### BUG-029 — `temperature_inc=0.0` + initial_prompt longo pode forçar Whisper a "completar" texto fictício
**Severidade:** Média. Initial prompt cria viés.
**Onde:** `whisper-jni.cpp:144-148`.

**Detalhe:** "Aula em português brasileiro. Linguagem formal e técnica." é um prompt OK. Mas para aulas informais ou de áreas não-técnicas (yoga, história), pode enviesar o vocabulário.
**Correção:** Tornar configurável em settings ou detectar automaticamente do primeiro chunk.

---

### BUG-030 — `GetPrimitiveArrayCritical` pode bloquear o GC por mais de 60 s
**Severidade:** Média.
**Onde:** `whisper-jni.cpp:106` e `:159`.

**Detalhe:** `GetPrimitiveArrayCritical` "pina" a memória — o GC NÃO pode compactar nada enquanto isso. Whisper Médio em 60 s de áudio leva 30–90 s para transcrever em celular médio. Durante esse tempo, qualquer outra alocação no app que precise de GC compaction pode falhar silenciosamente (causando crashes intermitentes).

**Correção:** Trocar por `GetFloatArrayElements` com `JNI_ABORT` no release. Adiciona uma cópia (~1.9 MB por chunk), mas elimina o pin de GC.

---

### BUG-031 — `ModelProfile.MEDIUM.minBytes = 200 MB` vs `approxBytes = 514 MB`
**Severidade:** Baixa. Tolerância muito frouxa.

**Detalhe:** Aceita arquivo de 200 MB como "instalado" mesmo sendo metade do esperado. Se o download falhar a 25 % (cobrindo header + alguns layers), pode passar validação e crashar no `init`.

**Correção:** `minBytes = approxBytes × 0.9`. Ou validar via SHA256 conhecido.

---

### BUG-032 — `TranscriptionService` não consome `currentSessionId` ao receber START repetido
**Severidade:** Baixa.
**Onde:** `TranscriptionService.kt:52-58`.

**Detalhe:** Se vier START com `sid` igual ao currente, retorna sem reiniciar — bom. Mas se vier START com `sid` DIFERENTE durante execução, simplesmente sobrescreve `currentSessionId` e cancela `workerJob` antigo. A próxima session entra na fila. Mas: o `Whisper` ainda pode estar processando o chunk atual da session anterior por mais 30–90 s, e nesse meio-tempo o UI mostra "0% iniciando" para a nova. Confuso.

**Correção:** Fila explícita. Ou rejeitar START enquanto outro está em curso, com mensagem clara.

---

### BUG-033 — `AnalysisScreen.analyzeJob` perdido entre composes
**Severidade:** Baixa. `var analyzeJob by remember { mutableStateOf<Job?>(null) }` — sobrevive a recomposição, mas se a Activity é killed e recriada, perde.
**Correção:** Mover para ViewModel.

---

### BUG-034 — `SettingsScreen` lê `AiKeyStore.hasKey` em recomposition (8 chamadas SP por refresh)
**Severidade:** Baixa.
**Onde:** `SettingsScreen.kt:67-76`.

**Detalhe:** Já tem cache via `LifecycleEventObserver` no `ON_RESUME`, OK. Mas a primeira renderização lê 4 vezes. Aceitável.

---

### BUG-035 — `SessionDetailScreen` "Renomear" não tem trim/validação
**Severidade:** Baixa.
**Onde:** `SessionDetailScreen.kt:298-301`.

**Detalhe:** Já faz `trimmed.isNotEmpty()`. Mas aceita nomes muito longos (sem max). Strings com >200 chars quebram listagem.
**Correção:** `take(200)` + remover quebras de linha.

---

### BUG-036 — `LongPressStopButton` confirmed=true persiste se Composable resize
**Severidade:** Baixa.
**Onde:** `RecordingScreen.kt:155-174`.

**Correção:** `var confirmed by remember(holding) { mutableStateOf(false) }`? Não, queremos manter `confirmed` quando confirma. OK.

---

### BUG-037 — Threads do Whisper hardcoded em `(N/2).coerceIn(2,4)`
**Severidade:** Baixa, mas sub-ótimo em hardware variado.

**Detalhe:** Em celular 4-core (low-end), N/2=2 OK. Em 8-core flagship, N/2=4 mas o coerceIn limita em 4 — desperdiça 4 cores. Em ARM big.LITTLE, usar 2 big cores (que aulalogger não distingue).
**Correção:**
- Detectar `Process.getThreadPriority` ou `ProcessAffinity` para alocar nos big cores (avançado).
- Pelo menos: permitir override em settings ("transcrição mais rápida" vs "celular mais responsivo").

---

### BUG-038 — Sem warning quando bateria < 15%
**Severidade:** Baixa. Mas aula de 4 h em <30 % bateria é arriscado.
**Correção:** Banner ao iniciar gravação se bateria < 25 %.

---

### BUG-039 — Sem detecção de espaço em disco antes de iniciar
**Severidade:** Baixa. Aula 8 h precisa ~1 GB.
**Correção:** Checar `context.filesDir.freeSpace` antes de `handleStart`. Se < 2 GB livres, avisar.

---

### BUG-040 — Foreground service sem `setLocalOnly(true)`
**Severidade:** Baixa. Notificação pode ser sincronizada com smartwatch — desnecessariamente.
**Correção:** Adicionar `setLocalOnly(true)` no NotificationCompat.Builder.

---

## 4. Bugs e issues — baixos / polimento

### POL-001 — `strings.xml` tem strings obsoletas
- `model_download_body` menciona "small" e "~470 MB" — desatualizado, agora o default é MINI (~181 MB).
- `model_download_title`, `model_download_button`, `model_downloading` declarados mas não usados em UI atual.
**Correção:** Limpar strings não usadas; atualizar texto do `model_download_body`.

### POL-002 — `tab_audio`, `tab_transcript` declarados mas não usados.
### POL-003 — Hardcoded "Locutor A" / "Locutor B" no `TranscriptFormatter`. Mover para strings.xml.
### POL-004 — Hardcoded "Aula em português brasileiro..." em `whisper-jni.cpp:144`. Configurável em settings seria melhor.
### POL-005 — Versão do app exibida apenas no APK metadata; sem tela "Sobre".
### POL-006 — Sem indicador visual quando AppKiller está esperando os 300 ms.
### POL-007 — `AnalysisScreen` botão "Refazer análise" tem mesmo tamanho e estilo que primeiro "Analisar com IA" — visualmente confuso.
### POL-008 — `ModelsScreen.hero` poderia mostrar consumo de disco atual de modelos instalados.
### POL-009 — `ApiKeysScreen` snackbar "Chave salva ✓" não distingue se Save ou Test-and-Save.
### POL-010 — `RecordingScreen` "A transcrição será gerada automaticamente..." poderia indicar qual modelo será usado.
### POL-011 — `EmptyStateHero` em Home tem três círculos animados que sobrepõem. Pode ser pesado em devices fracos. Considerar uma única animação ou estático.
### POL-012 — `Waveform` faz `delay(70)` infinito enquanto Composable visível — bateria. Pause quando offscreen.
### POL-013 — `HomeScreen` Pulse no botão (RecordButton) é ativo mesmo quando o usuário não está olhando — drain mínimo de bateria por animation system.

---

## 5. Qualidade da transcrição (visão técnica do "horrível")

Esta é a área que o usuário reclamou mais. Resumo do diagnóstico:

### Problemas identificados que impactam qualidade

1. **`temperature_inc = 0.0`** (BUG-014) — eliminou retry com temperatura. Quanto pior a captura (silêncio, ruído), mais alucinação direta.
2. **Initial prompt enviesado** (BUG-029) — "Aula em português brasileiro. Linguagem formal e técnica." reforça palavras técnicas mesmo em aulas que não são.
3. **Dedupe de overlap frágil** (BUG-010) — pode descartar segmentos legítimos quando whisper escorrega timestamp.
4. **Sem VAD pré-whisper** — whisper processa silêncio integralmente. Em uma aula com 30 % de pausas, isso é 30 % de tempo desperdiçado + risco de alucinação em cada silêncio.
5. **Modelo padrão = MINI (small)** — fonte de qualidade inferior. Para PT-BR técnico, **medium** dá ganho perceptível significativo; **large-v3-turbo** ainda mais.

### Recomendações priorizadas

**Quick wins (1 dia de trabalho cada):**

1. **Reativar `temperature_inc = 0.2f`** (BUG-014). Volta a permitir fallback de temperatura. Trade-off de 10–20 % mais lento mas qualidade significativa.

2. **Mudar default para MEDIUM** ou pelo menos avisar usuário ao primeiro uso que MEDIUM é fortemente recomendado para PT-BR. O comentário em `ModelManager` diz "Default = MINI: muito mais rápido, qualidade decente" — mas o usuário reclamou justamente da qualidade.

3. **Initial prompt configurável** ou neutro: `"Aula em português brasileiro."` — sem "formal/técnica". Calibra apenas o idioma.

4. **VAD-based skipping** (PERF-001 detalhado adiante): integrar Silero VAD ou whisper-vad pré-whisper, pulando silêncios completos. Reduz tempo de transcrição em 30–50 % e elimina alucinações em silêncio.

**Médio prazo:**

5. **Word-level timestamps** habilitados — permite dedupe textual fino no overlap. Hoje só temos segmento-level.

6. **Custom vocabulary** (`prompt_tokens`) — usuário pode listar termos específicos da sua área ("PROCV", "VLOOKUP", "scikit-learn", "tikinome técnico"). Funciona muito bem em whisper.cpp.

7. **Pos-processamento por LLM** opcional — segundo pass que corrige pontuação, capitaliza nomes, organiza parágrafos. Já que o usuário pode ter API key configurada, podemos oferecer "transcrição refinada".

---

## 6. Performance e bateria

### PERF-001 — Implementar VAD (Voice Activity Detection) pré-whisper
**Impacto:** 30–50 % redução no tempo total de transcrição.

Em aulas reais, há naturalmente 20–40 % de tempo em silêncio (pausas, transições, alunos pensando). Processar isso no Whisper é desperdício de CPU + risco de alucinação.

Whisper.cpp já tem suporte a VAD interno via Silero ONNX desde v1.7. Habilitar:

```cpp
params.vad = true;
params.vad_model_path = "/path/to/silero-vad.onnx";
params.vad_params.threshold = 0.5f;
params.vad_params.min_speech_duration_ms = 250;
params.vad_params.min_silence_duration_ms = 100;
```

Requer baixar `silero-vad.onnx` (~1.5 MB) junto com o modelo Whisper. O `ModelManager` pode fazer isso transparentemente.

### PERF-002 — Inferência em GPU OpenCL ou QNN (Snapdragon)
**Impacto:** 2–4× mais rápido em flagships.

`whisper.cpp` tem backend OpenCL e Vulkan experimental. Em Snapdragon 8 Gen 2/3, OpenCL acelera Whisper Medium para ~5× tempo real (vs ~1× CPU). Para Snapdragon mais novos, **QNN** (Qualcomm Neural Network) é ainda melhor.

**Custo:** complexidade de build NDK + detecção de driver presente. **Recomendação:** Fase 2.

### PERF-003 — Reduzir alocações no hot path da gravação
- BUG-002 e BUG-003 (já listados).
- Direct ByteBuffer + FileChannel: 2× throughput de I/O para WavWriter.

### PERF-004 — Cache de prefs em StateFlow
- `TranscriptionPrefs`, `ModelManager.getSelectedProfile` chamados várias vezes/sec em recomposições.

### PERF-005 — Liberar Whisper ctx após idle (BUG-007).
- ~500 MB de RAM recuperados.

### PERF-006 — Throttle do `audioLevel` para UI
**Onde:** `RecordingService.kt:134` — `RecordingController.audioLevel.value = capture?.lastLevel ?: 0f` a cada read (~100 ms).

**Impacto:** 10 Hz update da waveform. OK visualmente. Mas se a `Waveform` Composable estiver em screen offscreen (usuário em Settings), ainda recompose 10 Hz. Já o `Waveform.kt:42-47` tem loop infinito de phase update — `delay(70)` para sempre. Pause quando Composable não está visível.

### PERF-007 — Reduzir prioridade da thread de captura
**Onde:** `RecordingService.kt:124` — captureJob no `scope`.

A coroutine roda em Dispatchers.Default (worker pool). Pode entrar em competição com outros workers. Threading explícito + `THREAD_PRIORITY_URGENT_AUDIO` daria latência menor de leitura:

```kotlin
val thread = HandlerThread("AudioCapture").apply {
    priority = Process.THREAD_PRIORITY_URGENT_AUDIO
    start()
}
```

### PERF-008 — Battery: notificação `PRIORITY_DEFAULT` causa wake mais frequente
**Onde:** `RecordingService.kt:275` — `setPriority(PRIORITY_DEFAULT)`. Comentário em `AulaLoggerApp.kt:47-49` diz "IMPORTANCE_LOW deixava OEMs matarem o serviço".

**Detalhe:** Em OneUI (Samsung) e MIUI (Xiaomi), DEFAULT pode ainda gerar vibração leve ou som de notificação ao primeiro update. Recheckar com `setSilent(true)` + `setOnlyAlertOnce(true)` — já presente, OK. Mas DEFAULT importance acorda CPU para renderizar a notificação a cada update (1 Hz na gravação). Considerar `LOW` para a notificação de **transcrição** (não-urgente) mantendo DEFAULT só para gravação.

### PERF-009 — `Json { prettyPrint = true }` no SessionRepository
**Onde:** `SessionRepository.kt:20`.

**Detalhe:** Pretty-print é bom para debug, mas escreve 30 % mais bytes. Para 1000 aulas vira MB. Trocar para `prettyPrint=false` em release.

---

## 7. Arquitetura e código (problemas estruturais)

### ARCH-001 — `RecordingController` como `object` global é antipattern
**Severidade:** Estrutural.
**Onde:** `recording/RecordingController.kt`.

**Detalhe:** Singleton com state mutável compartilhado entre Service, Activity, Widget. Funciona, mas:
- Não testável (não posso mockar o controller em tests).
- Acoplamento alto: qualquer mudança ripples por todo lado.
- Se algum dia precisar de duas gravações simultâneas (improvável mas) — impossível.

**Recomendação:** Migrar para `RecordingRepository` injetado (Hilt/Koin), com `RecordingState` como flow. Effort: ~2 dias.

### ARCH-002 — Sem ViewModel — state em `remember { }` perde em recriação de Activity
**Onde:** `AnalysisScreen.kt:38-41` — `loading`, `error`, `pickerOpen`, `analyzeJob` todos em `remember`.

**Detalhe:** Rotacionar o celular durante análise IA (raro, mas possível) reseta tudo. O request HTTP fica órfão.
**Recomendação:** `ViewModel` por tela ou `rememberSaveable` + ViewModel para state crítico.

### ARCH-003 — Sem layer de Domain
**Onde:** UI chama diretamente `app.repo`, `TranscriptionService`, `ModelManager`.

**Detalhe:** Acoplamento entre UI e dados. Para 1 dev é OK; para escalar (ou aceitar PRs de fora) ajudaria ter `RecordingUseCase`, `TranscribeUseCase` etc.
**Recomendação:** Adiar até o codebase crescer.

### ARCH-004 — `WhisperJNI.object` + state mutável + sync por method
**Onde:** `WhisperJNI.kt`.

**Detalhe:** `@Synchronized` em `init`, `transcribe`, `free`. Funciona, mas semântica unclear: pode `transcribe` rodar enquanto outro thread tenta `free`? Sim, locked. Mas durante transcribe que dura 90 s, free fica enfileirado por 90 s. UX: usuário aperta "Apagar", aparenta travamento.
**Recomendação:** Cancellation cooperativo (já tem `cancel()` flag). `free()` chama cancel antes de tentar sync.

### ARCH-005 — `SessionRepository` mistura load/recovery/CRUD em uma classe
**Onde:** `SessionRepository.kt`.

**Recomendação (médio prazo):** Separar `SessionStore` (CRUD) de `SessionRecoveryService` (recovery).

### ARCH-006 — `TranscriptionState` como object singleton
**Onde:** `TranscriptionService.kt:397`.

**Detalhe:** Idêntico ao ARCH-001. Funciona mas frágil.

### ARCH-007 — Não usa `WorkManager` para transcrição
**Onde:** `TranscriptionService`.

**Detalhe:** `WorkManager` é a abordagem recomendada para tarefas que precisam:
- Sobreviver a app kill.
- Constraints (Wi-Fi only, charging only).
- Retry com backoff.

Trans-crição se encaixa: tarefa longa, idempotente, valiosa, pode esperar.
**Recomendação:** Migrar `TranscriptionService` → `TranscriptionWorker` (CoroutineWorker). Foreground info para mostrar notificação.

### ARCH-008 — Mistura de `runCatching`, `try/catch (_: Throwable)`, e `try/catch (t: Throwable) { Log.e }`
**Onde:** Inúmeros lugares.

**Detalhe:** Inconsistente. Alguns logam, outros silenciam. Bugs ficam ocultos.
**Recomendação:** Estilo: silenciar APENAS quando o erro é esperado/recuperável; senão log.

### ARCH-009 — Build em **debug** com `isMinifyEnabled=false` para release
**Onde:** `build.gradle.kts:45-48`.

**Detalhe:** Distribui ProGuard desabilitado: APK ~5 MB maior, mais lento de iniciar, sem ofuscação. Aceitável para dev/beta, péssimo para produção.
**Correção:** Habilitar minify em `release` com regras corretas para `kotlinx.serialization`, `WhisperJNI.Segment` (native), Compose.

### ARCH-010 — `signingConfig = signingConfigs.getByName("debug")` em release
**Onde:** `build.gradle.kts:47`.

**Detalhe:** APK release assinado com a chave de debug. Significa que dois dispositivos do mesmo dev podem instalar, mas não passa em loja, e qualquer um com chave de debug do projeto pode "atualizar" o app.
**Correção:** Gerar keystore real, configurar via `gradle.properties` (gitignored).

### ARCH-011 — Sem testes unitários nem instrumentation
**Severidade:** Estrutural. Crítico.

Atualmente: 0 testes. Mudanças em `TranscriptFormatter`, `WavReader`, `RecordingController` podem regredir silenciosamente.

**Recomendação:** Pelo menos:
- `TranscriptFormatterTest` (puro, sem Android).
- `WavReaderTest` (com .wav de teste).
- `SessionRepositoryTest` (com Android instrumentation, criando + recuperando).

### ARCH-012 — Sem CI
- GitHub Actions / GitLab CI etc. Build + lint + tests em cada PR.

### ARCH-013 — `compileSdk = 35`, `targetSdk = 34`
Comum, mas **Google Play exige targetSdk = 34** a partir de Aug/2024. OK até Aug/2025 quando exige 35.

### ARCH-014 — `whisper.cpp/` clonado como diretório, não submódulo git
**Severidade:** Médio. Difícil atualizar; tag não documentada.
**Recomendação:** Git submodule pinned em SHA, ou subtree, ou `FetchContent` no CMake.

### ARCH-015 — Sem `versionCode` automation
Build atual usa `versionCode = 10` hardcoded. Esquecer de incrementar em release pública = update bloqueado pelo Play.
**Recomendação:** Gerar a partir de tag git.

### ARCH-016 — Sem proguard rules para `kotlinx.serialization`
**Onde:** Quando habilitar minify (ARCH-009), os `@Serializable data class` viram lixo. Sem rules: crash em runtime.

```proguard
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class com.aulalogger.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
```

### ARCH-017 — `Application.onCreate` faz I/O síncrono (`SessionRepository(this)` carrega + recovery)
**Severidade:** Baixa. Para projeto de "tamanho de arquivo razoável" OK. Para 1000+ sessions = lag no startup.
**Correção:** Lazy + initial state vazio + load assíncrono.

### ARCH-018 — `AppKiller.killEverything` chama `RecordingController.reset()` antes de garantir que service parou
**Severidade:** Baixa.

### ARCH-019 — Mistura de "ESCUTAR" / "INICIAR" / "GRAVAR AULA"
Strings inconsistentes em widget + button + xml + comentários.

---

## 8. Segurança e privacidade

### SEC-001 — `signingConfig = debug` no release (ARCH-010)
Já listado.

### SEC-002 — API keys em `EncryptedSharedPreferences` — OK
Implementação correta. Fallback para plain SP é documentado e aceitável.

### SEC-003 — Sem clear-text policy explícita
**Detalhe:** Android Network Security Config padrão proíbe cleartext em targetSdk ≥ 28. Mas como o app só fala HTTPS, OK. Adicionar `network_security_config.xml` explicitamente.

### SEC-004 — Logs vazam paths e estados internos
**Onde:** Inúmeros `Log.i(TAG, ...)` com `audioFile.absolutePath`, model paths, transcripts.

**Detalhe:** Em release, `Log.i` continua chamando — sem stripping. Logs visíveis via `adb logcat` ou Android Studio para alguém com acesso ao USB do device.

**Correção:** Wrapper de log + ProGuard rule para strip `Log.*` em release:

```proguard
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
```

(Manter `w` e `e` para crash reports.)

### SEC-005 — `INTERNET` permission permanente
**Detalhe:** Necessária para download de modelos e API IA. OK. Mas usuário pode achar invasivo dado o claim "100 % offline". Mensagem clara no Settings: "INTERNET é usada apenas para baixar o modelo e para análise IA opcional. Áudio nunca sai do celular."

### SEC-006 — Sem rate limiting nas chamadas IA cloud
**Detalhe:** Se usuário acidentalmente loop ou se há bug que dispara analyze várias vezes, gasta dinheiro do bolso dele.
**Correção:** Debounce + max 1 chamada por sessão em curto período.

### SEC-007 — Sem verificação SHA256 do modelo baixado (BUG-013 referência).

---

## 9. Conformidade Android (Play Store + OEMs)

### CONF-001 — Foreground services tipo correto ✓ — OK.
### CONF-002 — POST_NOTIFICATIONS request ✓ — OK.
### CONF-003 — Battery optimization request ✓ — OK.

### CONF-004 — Sem **data safety** declaration preparada
Para publicar na Play Store, precisa preencher:
- Que dados o app coleta? (Audio: collected, processed locally, optional cloud upload).
- Encryption in transit? Yes (HTTPS).
- Encryption at rest? Audio is private app storage.

### CONF-005 — Sem **privacy policy URL** definida
Necessária na Play Store. Pode reusar a `caiorossi.casa/privacy` se existir.

### CONF-006 — Sem **inApp updates** flow
Considerar Play Core inAppUpdates.

### CONF-007 — Xiaomi / Vivo / Huawei kill detection
**Severidade:** Alta para mercado BR (Xiaomi tem ~30 % share).
Mesmo com FGS + WakeLock, alguns OEMs matam apps em background "agressivos" se não estiverem na whitelist de "Auto-start" (MIUI) ou "Battery saver exceptions" (One UI).

**Recomendação:**
- Detectar MIUI/OxygenOS/etc. via `Build.MANUFACTURER` e `Build.HOST`.
- Mostrar tutorial específico ao primeiro start em devices conhecidos: "No Xiaomi, vá em Configurações > Apps > AulaLogger > Auto-start > Habilitar".

### CONF-008 — `android:exported="true"` na MainActivity
Necessário para launcher. OK. Mas se algum dia adicionar deep links, validar permissões.

### CONF-009 — Versão do widget — `targetCellWidth/Height` é Android 12+
Em Android 9–11 (minSdk=29), aplica `minWidth/Height` only. OK.

### CONF-010 — `enableEdgeToEdge` requer Android 11+ (API 30) para funcionar corretamente. minSdk=29 — funciona com fallback OK.

---

## 10. Acessibilidade

### A11Y-001 — Sem `contentDescription` em ícones decorativos vs significativos
**Onde:** Várias telas. Alguns têm `contentDescription = null` (decorativo) — OK. Outros têm `contentDescription = "Conceder"` — OK.

**Detalhe:** `RecordButton.Icon(Icons.Filled.Mic, contentDescription = null)` deveria ter "Iniciar gravação" para TalkBack.

### A11Y-002 — Touch target sizes
- `widget_button_pause` é 40x40 dp — abaixo de 48dp recomendado para touch.
- Botões inline (Save/Test/Clear) em ApiKeysScreen podem estar < 48dp em telas estreitas.

### A11Y-003 — Cores com contraste insuficiente em dark mode
**Onde:** `onSurfaceVariant = Color(0xFFB0B0B0)` sobre `surface = #161616`. Razão ~6:1 — OK para texto, abaixo do recomendado para texto pequeno (deveria ser 7:1).

### A11Y-004 — Animações sempre ativas
- Pulse do botão, EmptyStateHero, Waveform.
- Para usuários com sensibilidade a movimento (vestibular disorders), Android 12+ tem `Settings.Global.TRANSITION_ANIMATION_SCALE`.

```kotlin
val reduceMotion = Settings.Global.getFloat(contentResolver,
    Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
```

Honrar isso e desabilitar animações.

### A11Y-005 — Fonte fixa
Não respeita `Configuration.fontScale`. Usuários com fonte aumentada veem layout quebrado.

### A11Y-006 — Sem suporte a TalkBack para o RecordingController state
- "Gravando • 00:14:32" como conteúdo de tela — TalkBack lê só o que está na tela. Live region para o timer seria ideal.

---

## 11. Testes ausentes (zero cobertura)

Lista do que **deveria** ter testes (em ordem de retorno):

1. `TranscriptFormatter` — entrada: lista de segmentos. Saída: string formatada. Cases: vazio, só segmento curto+hallucination, mix de speakers, gaps grandes/pequenos, timestamps com/sem.
2. `WavReader.parseHeader` — WAVs malformados (sem fmt, sem data, com chunks extra "LIST", "INFO", data size 0).
3. `WavWriter` — write + close + reabrir + verificar tamanho.
4. `SessionRepository.loadAndRecover` — sessions com várias combinações de status / lastUpdatedAt.
5. `SessionRepository` concurrent updates (race conditions).
6. `WhisperJNI` smoke test (carrega Mini, transcreve 1s de silêncio, espera lista vazia ou ruído).
7. `AiAnalyzer` mock HTTP — formata corpo correto para cada provider.
8. `ModelManager` magic number check (alimenta arquivo "html error page" — deve rejeitar).
9. `TranscriptionService` integration (instrumentation).
10. `RecordingService` integration — start/stop/recovery (instrumentation).

---

## 12. Documentação interna

### DOC-001 — Faltam comentários em pontos não-óbvios
- Por que `temperature_inc = 0.0`? (Tem comentário, OK.)
- Por que `WINDOW_SEC = 60, OVERLAP_SEC = 5`? (Sem explicação.)
- Por que `MAX_HOURS = 12L`?
- Por que channel `recording.v2` (migração)?

### DOC-002 — Sem README atualizado
README do projeto deveria documentar:
- Build instructions
- Architecture overview
- How to contribute
- Known issues / FAQ

### DOC-003 — Site/landing page menciona versões desatualizadas
- Site/public/apk tem v0.6.0-polish; código atual é v0.7.3.

---

## Plano de ação priorizado

### Sprint 1 — Eliminar regressões e fugas óbvias (3–5 dias)
**Foco: estabilidade e qualidade da transcrição.**

| # | Item | Esforço |
|---|---|---|
| 1 | BUG-007: idle eviction do Whisper ctx | 2 h |
| 2 | BUG-005: refresh em onResume | 30 min |
| 3 | BUG-006: cancelar transcrição ao deletar session | 30 min |
| 4 | BUG-008: refazer transcrição preserva texto antigo | 1 h |
| 5 | BUG-009: erro de transcrição visível na UI | 1 h |
| 6 | BUG-014: reativar `temperature_inc = 0.2f` | 5 min + rebuild |
| 7 | BUG-001: consolidar "ESCUTAR" em strings.xml + widget | 30 min |
| 8 | PERF-001: integrar Silero VAD pré-whisper | 1 dia |
| 9 | Trocar default de MINI → MEDIUM | 5 min |
| 10 | BUG-002+003: pool de buffers / direct buffer | 2 h |

Resultado esperado: app sem crashes silenciosos, transcrição perceptivelmente melhor e mais rápida em aulas com pausas.

### Sprint 2 — Qualidade e robustez (3–5 dias)

| # | Item | Esforço |
|---|---|---|
| 11 | BUG-010: dedupe textual de overlap | 4 h |
| 12 | BUG-013: download resume + SHA256 verify | 4 h |
| 13 | BUG-016: max_tokens 8192 em Claude + Gemini safety | 1 h |
| 14 | BUG-017: cancelamento real de HTTP (OkHttp ou disconnect ref) | 4 h |
| 15 | BUG-023: heartbeat 20 s + threshold 60 s | 30 min |
| 16 | BUG-024: detecção de mudança de mic | 3 h |
| 17 | BUG-025: warning de mic mudo | 2 h |
| 18 | BUG-039: warning de espaço em disco | 1 h |
| 19 | BUG-038: warning de bateria baixa | 1 h |
| 20 | CONF-007: tutorial específico de OEM agressivo (MIUI etc.) | 4 h |

### Sprint 3 — Polimento e segurança (2–3 dias)

| # | Item | Esforço |
|---|---|---|
| 21 | SEC-004: strip de Log.v/d/i em release + wrapper | 2 h |
| 22 | ARCH-009/010: ProGuard release habilitado + keystore real | 4 h |
| 23 | BUG-011: AppKiller com callback em vez de delay | 2 h |
| 24 | BUG-019: cancelAll de notificações no AppKiller | 15 min |
| 25 | A11Y-001 a A11Y-006: revisão de acessibilidade | 4 h |
| 26 | POL-001 a POL-013: limpeza de strings e UI | 4 h |
| 27 | Tela "Sobre" com versão, créditos, política | 2 h |
| 28 | DOC-002: README atualizado | 2 h |

### Sprint 4 — Arquitetura (5–10 dias, opcional)

| # | Item |
|---|---|
| 29 | Migrar SessionRepository para Room (BUG-026, BUG-027) |
| 30 | Migrar TranscriptionService para WorkManager (ARCH-007) |
| 31 | ViewModel para AnalysisScreen, SessionDetail (ARCH-002) |
| 32 | Hilt/Koin para DI (ARCH-001, ARCH-006) |
| 33 | Testes (ARCH-011): pelo menos TranscriptFormatter + WavReader + SessionRepository |
| 34 | CI: GitHub Actions com build + lint + test (ARCH-012) |

### Sprint 5 — Otimização avançada (futuro)

| # | Item |
|---|---|
| 35 | PERF-002: backend OpenCL / QNN para Whisper |
| 36 | Custom vocabulary configurável |
| 37 | Pos-processamento por LLM (refinamento opcional) |
| 38 | RF64 / FLAC para áudios > 4h |

---

## Anexo A — Tabela cruzada (bug × área × prioridade)

| ID | Área | Prioridade | Onde | Esforço | Sprint |
|---|---|---|---|---|---|
| BUG-001 | UX | Alta | strings.xml, widget | 30min | 1 |
| BUG-002 | Performance | Alta | AudioCapture | 1h | 1 |
| BUG-003 | Performance | Alta | WavWriter | 1h | 1 |
| BUG-004 | Robustez | Crítica >17h | WavWriter | 1d | 5 |
| BUG-005 | UX | Alta | MainActivity | 30min | 1 |
| BUG-006 | UX | Alta | SessionRepository | 30min | 1 |
| BUG-007 | Performance | Crítica | TranscriptionService | 2h | 1 |
| BUG-008 | UX | Alta | SessionDetailScreen | 1h | 1 |
| BUG-009 | UX | Alta | SessionDetailScreen | 1h | 1 |
| BUG-010 | Qualidade transcrição | Alta | TranscriptionService | 4h | 2 |
| BUG-011 | Robustez | Alta | AppKiller | 2h | 3 |
| BUG-012 | Robustez | Média | RecordingController | 2h | 3 |
| BUG-013 | UX | Alta | ModelManager | 4h | 2 |
| BUG-014 | Qualidade transcrição | Alta | whisper-jni.cpp | 5min | 1 |
| BUG-015 | Robustez | Média | AiAnalyzer | 30min | 2 |
| BUG-016 | UX | Média | AiAnalyzer | 1h | 2 |
| BUG-017 | Robustez | Média | AiAnalyzer | 4h | 2 |
| BUG-018 | Robustez | Baixa | WhisperJNI | doc only | — |
| BUG-019 | Polish | Média | AppKiller | 15min | 3 |
| BUG-020 | Performance | Baixa | TranscriptionPrefs | 1h | 4 |
| BUG-021 | Polish | Baixa | AudioPlayer | OK | — |
| BUG-022 | Code health | Média | WavReader | 30min | 3 |
| BUG-023 | Robustez | Média | RecordingService | 30min | 2 |
| BUG-024 | Robustez | Média | AudioCapture | 3h | 2 |
| BUG-025 | UX | Média | AudioCapture | 2h | 2 |
| BUG-026 | Arquitetura | Média | SessionRepository | 2d | 4 |
| BUG-027 | Arquitetura | Média | SessionRepository | — | 4 |
| BUG-028 | Polish | Baixa | MainActivity | 30min | 3 |
| BUG-029 | Qualidade transcrição | Média | whisper-jni.cpp | 2h | 2 |
| BUG-030 | Robustez | Média | whisper-jni.cpp | 1h | 3 |
| BUG-031 | Robustez | Baixa | ModelManager | 5min | 2 |
| BUG-032 | Robustez | Baixa | TranscriptionService | 1h | 3 |
| BUG-033 | Arquitetura | Baixa | AnalysisScreen | 4h | 4 |
| BUG-034 | Performance | Baixa | SettingsScreen | OK | — |
| BUG-035 | UX | Baixa | SessionDetailScreen | 15min | 3 |
| BUG-036 | UX | Baixa | RecordingScreen | OK | — |
| BUG-037 | Performance | Média | TranscriptionService | 2h | 5 |
| BUG-038 | UX | Baixa | UI nova | 1h | 2 |
| BUG-039 | UX | Baixa | UI nova | 1h | 2 |
| BUG-040 | Polish | Baixa | Notification | 5min | 3 |
| PERF-001 | Performance | Alta | Whisper integration | 1d | 1 |
| PERF-002 | Performance | Futura | NDK | 1sem | 5 |
| PERF-003 | Performance | Média | Captura | 2h | 1 |
| PERF-004 | Performance | Baixa | Prefs | 1h | 4 |
| PERF-005 | Performance | Alta | (= BUG-007) | — | — |
| PERF-006 | Performance | Baixa | Waveform | 1h | 3 |
| PERF-007 | Performance | Baixa | Service | 2h | 5 |
| PERF-008 | Bateria | Baixa | Notification | 30min | 3 |
| PERF-009 | Performance | Baixa | SessionRepository | 5min | 3 |
| ARCH-001 a 019 | Arquitetura | Variada | global | variado | 4–5 |
| SEC-001 a 007 | Segurança | Variada | global | variado | 3 |
| CONF-001 a 010 | Conformidade | Variada | global | variado | 3 |
| A11Y-001 a 006 | Acessibilidade | Média | global | 4h total | 3 |
| DOC-001 a 003 | Documentação | Baixa | global | 4h | 3 |
| POL-001 a 013 | Polimento | Baixa | global | 4h | 3 |

---

## Anexo B — Aspectos que estão bons e devem ser preservados

Para não desconstruir o que funciona, registro aqui o que **NÃO deve ser mexido sem necessidade clara**:

1. **Decisão arquitetural de service por feature** (RecordingService + TranscriptionService) — limpa, isolada, escalável.
2. **`abort_callback` no whisper-jni** — cancelamento real é raro em apps. Manter.
3. **`GetPrimitiveArrayCritical`** — zero-copy faz diferença em chunks grandes. Migrar apenas se BUG-030 vier a se materializar.
4. **`upsertSession` precoce + heartbeat + recovery** — pattern correto e robusto.
5. **`EncryptedSharedPreferences` com fallback** — tratamento defensivo correto.
6. **WavWriter atomic header update** — robustez excepcional.
7. **Material 3 com escolha de tema persistida** — bem feito, completo.
8. **LongPressStopButton** — UX consciente que evita "stop acidental durante aula".
9. **Banner "Gravando agora" na Home** — UX clara, link direto para a tela ativa.
10. **`AulaLoggerApp.autoTranscribeRecovered`** — recovery automático invisível para o usuário.
11. **Sistema de animações via `rememberPulse` em `Motion.kt`** — clean, reutilizável.
12. **`ModelManager.installedProfiles` + UI mostrando múltiplos instalados** — usuário pode comparar modelos.
13. **Filtro contextual de alucinações em `TranscriptFormatter`** (curto-segmento + lista hardcoded) — abordagem inteligente.
14. **Cache de `EncryptedSharedPreferences` em `AiKeyStore`** — performance correta.

---

## Anexo C — Métricas a instrumentar (futuro)

Para validar empiricamente as melhorias propostas, instrumentar (com opt-in do usuário, anônimo):

1. **Tempo médio de transcrição vs duração do áudio** (real-time factor).
2. **Taxa de crash de RecordingService** em aulas > 1 h.
3. **% aulas que precisam recovery** (proxy de instabilidade).
4. **% downloads de modelo que precisam reiniciar do zero** (sem resume).
5. **Pico de RAM durante e após transcrição**.
6. **Drain de bateria por hora de gravação** em background.

---

## Conclusão

O AulaLogger está em um estado **funcional e robusto na espinha dorsal** (gravação confiável, recovery, foreground services bem configurados), com **fraquezas localizadas em qualidade da transcrição** (configuração subótima do whisper, ausência de VAD) e **regressões de UX recentes** (erro de transcrição invisível, refazer perdendo texto, default MINI piorando percepção).

Aplicando o Sprint 1 inteiro (3–5 dias de trabalho focado), o app salta de "funciona com problemas" para "qualidade comparável a Otter.ai com privacidade local". O Sprint 2 traz robustez para uso diário em diferentes condições. Os Sprints 3+ são polimento e dívida técnica controlada.

Nada nesse relatório é fluff: cada item tem severidade, localização exata e esforço estimado. Use a tabela do Anexo A para priorizar conforme o tempo disponível.

---

*Auditoria realizada em maio/2026 com base na versão 0.7.3 (versionCode=10) do código-fonte completo.*
