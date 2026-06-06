# 03 — Subsistema de gravação (o crítico)

> Este é o documento **mais importante** do plano. Se a gravação não for absolutamente confiável por 4h+, nada mais importa. Tudo aqui é detalhado, paranoico, com plano de contingência.

---

## 3.1. Requisitos funcionais

| Req | Descrição | Critério de aceitação |
|-----|-----------|------------------------|
| R-G1 | Gravar áudio do microfone do dispositivo | PCM 16-bit, 16kHz, mono, sem compressão |
| R-G2 | Gravar por 4 horas ou mais sem interrupção | Sessão de 4h em celular médio: zero perda > 100ms |
| R-G3 | Sobreviver a tela apagada, app em background, lock screen | Idem |
| R-G4 | Sobreviver a Doze mode, App Standby Buckets | Idem (vamos pedir exclusão de battery optimization) |
| R-G5 | Sobreviver a fabricantes agressivos (Xiaomi, Samsung, Huawei) | Onboarding instrui usuário a desativar otimizações específicas |
| R-G6 | Sobreviver a baixo armazenamento durante gravação | Detectar < 200MB livres, alertar usuário, parar limpo |
| R-G7 | Sobreviver a crash do processo principal | Recovery na próxima abertura, perda máxima = 1 chunk (30s) |
| R-G8 | Pausar e retomar gravação | Pausa = stop temporário, resume cria novo chunk |
| R-G9 | Adicionar marcador na timeline durante gravação | Botão "marcar momento", grava timestamp |
| R-G10 | Permitir mudar nome da aula durante gravação | Hot rename na sessão ativa |
| R-G11 | Mostrar status real-time | Tempo decorrido, nível de áudio, chunks salvos, último save |
| R-G12 | Bateria: não consumir excessivamente | < 8%/h em celular médio com tela apagada |

---

## 3.2. Arquitetura do RecordingService

```
┌────────────────────────────────────────────────────────────────────┐
│ RecordingService : Service (Foreground)                            │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │ Lifecycle                                                    │  │
│   │  - onCreate: alocar resources, configurar wake lock          │  │
│   │  - onStartCommand: iniciar RecordingSession                  │  │
│   │  - onDestroy: cleanup garantido                              │  │
│   │  - onTaskRemoved: NÃO parar (sobreviver swipe-out)           │  │
│   └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │ Notification (mandatória, com tipo MICROPHONE)               │  │
│   │  - Mostra "Gravando aula X – 1:23:45"                        │  │
│   │  - Action: pausar/retomar                                    │  │
│   │  - Action: parar                                             │  │
│   │  - PendingIntent abre app na tela de gravação                │  │
│   └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │ RecordingSession                                              │  │
│   │  ┌────────────────────────────────────────────────────────┐  │  │
│   │  │ AudioRecord                                              │  │  │
│   │  │   - source: MIC                                          │  │  │
│   │  │   - sample rate: 16000Hz                                 │  │  │
│   │  │   - channel: mono                                        │  │  │
│   │  │   - format: PCM 16-bit                                   │  │  │
│   │  │   - bufferSize: 4x mínimo                                │  │  │
│   │  └────────────┬───────────────────────────────────────────┘  │  │
│   │               │ ShortArray buffer (~100ms a cada read)        │  │
│   │               ▼                                                │  │
│   │  ┌────────────────────────────────────────────────────────┐  │  │
│   │  │ RingBuffer (em memória, ~5s)                             │  │  │
│   │  │   - Permite preview de nível, VAD, detecção de pico      │  │  │
│   │  │   - Sobrevive a pequenos lags na escrita                 │  │  │
│   │  └────────────┬───────────────────────────────────────────┘  │  │
│   │               │                                                │  │
│   │               ▼                                                │  │
│   │  ┌────────────────────────────────────────────────────────┐  │  │
│   │  │ ChunkWriter (coroutine IO)                               │  │  │
│   │  │   - Acumula 30s de PCM                                   │  │  │
│   │  │   - Escreve chunk-NNNNN.wav (com header WAV)             │  │  │
│   │  │   - fsync após escrita                                   │  │  │
│   │  │   - Atualiza meta.json (atomic rename)                   │  │  │
│   │  │   - Emite evento "chunk-saved" para JS                   │  │  │
│   │  └────────────────────────────────────────────────────────┘  │  │
│   └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │ Wake locks                                                   │  │
│   │  - PARTIAL_WAKE_LOCK (manter CPU acordada)                   │  │
│   │  - Liberado em onDestroy                                     │  │
│   └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │ HealthMonitor (coroutine timer 5s)                           │  │
│   │  - Verifica espaço em disco                                   │  │
│   │  - Verifica bateria                                          │  │
│   │  - Verifica que último chunk foi salvo há < 60s              │  │
│   │  - Alerta via notificação se algo desviar                    │  │
│   └─────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
```

---

## 3.3. Detalhamento técnico de cada componente

### 3.3.1. AudioRecord configuration

```kotlin
val sampleRate = 16000
val channelConfig = AudioFormat.CHANNEL_IN_MONO
val audioFormat = AudioFormat.ENCODING_PCM_16BIT
val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
val bufferSize = minBufferSize * 4   // 4x para folga

val audioRecord = AudioRecord.Builder()
    .setAudioSource(MediaRecorder.AudioSource.MIC)  // ou VOICE_RECOGNITION pra fala mais limpa
    .setAudioFormat(
        AudioFormat.Builder()
            .setEncoding(audioFormat)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()
    )
    .setBufferSizeInBytes(bufferSize)
    .build()
```

**Decisões justificadas:**
- **16kHz mono:** Whisper roda nativamente nessa taxa. Voz humana cabe < 8kHz Nyquist. Estéreo seria desperdício para fala.
- **PCM 16-bit:** padrão de qualidade, formato mais simples, sem perda.
- **Source MIC vs VOICE_RECOGNITION:** VOICE_RECOGNITION aplica processamento (AGC, noise suppression) do hardware. **Vamos oferecer escolha em config avançada:** default = MIC (áudio cru, fazemos nosso próprio processamento), opcional = VOICE_RECOGNITION (deixa fabricante fazer).

### 3.3.2. ChunkWriter — escrita atômica e segura

```kotlin
class ChunkWriter(
    private val sessionDir: File,
    private val sampleRate: Int = 16000
) {
    private var chunkIndex = 0
    private val accumulator = ByteArrayOutputStream(CHUNK_SIZE_BYTES)
    
    suspend fun feed(samples: ShortArray, count: Int) = withContext(Dispatchers.IO) {
        // Converte short → bytes little-endian
        val bytes = ByteBuffer.allocate(count * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            bytes.putShort(samples[i])
        }
        accumulator.write(bytes.array())
        
        if (accumulator.size() >= CHUNK_SIZE_BYTES) {
            flushChunk()
        }
    }
    
    private suspend fun flushChunk() = withContext(Dispatchers.IO) {
        val tmpFile = File(sessionDir, "chunk-${chunkIndex.padded()}.wav.tmp")
        val finalFile = File(sessionDir, "chunk-${chunkIndex.padded()}.wav")
        
        FileOutputStream(tmpFile).use { fos ->
            // 1. Escreve WAV header
            writeWavHeader(fos, accumulator.size(), sampleRate, 1, 16)
            // 2. Escreve PCM data
            fos.write(accumulator.toByteArray())
            // 3. fsync para garantir disco
            fos.fd.sync()
        }
        
        // 4. Atomic rename (se aqui crashar, .tmp fica órfão e é limpo no recovery)
        if (!tmpFile.renameTo(finalFile)) {
            throw IOException("rename failed: $tmpFile -> $finalFile")
        }
        
        // 5. Atualiza manifesto (também atomic)
        updateManifest(chunkIndex)
        
        chunkIndex++
        accumulator.reset()
    }
    
    companion object {
        const val CHUNK_DURATION_SEC = 30
        const val CHUNK_SIZE_BYTES = 16000 * 2 * CHUNK_DURATION_SEC // ~960KB
    }
}
```

**Decisões justificadas:**
- **30s/chunk:** balanço entre frequência de escrita (overhead) e perda máxima em caso de crash. Ajustável via config (default 30s, faixa 10–120s).
- **WAV header em cada chunk:** chunks são reproduzíveis individualmente, ferramentas externas reconhecem.
- **`.tmp` + rename:** garantia atômica. POSIX garante rename atômico no mesmo filesystem.
- **`fd.sync()`:** força flush para disco físico. Custa ~10–50ms, mas garante que após retorno o dado está persistido.

### 3.3.3. Manifest (meta.json)

A cada chunk salvo, atualizamos um manifest com:

```json
{
  "session_id": "uuid-v4",
  "name": "Aula de Python — 03/05",
  "started_at": "2026-05-03T20:30:00.000Z",
  "last_chunk_at": "2026-05-03T22:14:30.123Z",
  "sample_rate": 16000,
  "channels": 1,
  "bit_depth": 16,
  "chunk_duration_sec": 30,
  "chunks_count": 209,
  "total_duration_sec": 6270,
  "status": "recording",  // recording | paused | stopped | crashed
  "markers": [
    { "ts_ms": 142000, "label": "Início tópico VLOOKUP" },
    { "ts_ms": 1873000, "label": "Pausa" }
  ],
  "audio_source": "MIC",
  "device_info": {
    "model": "Pixel 7",
    "android_version": "14",
    "app_version": "1.0.0"
  }
}
```

Atualização também via tmp + rename.

### 3.3.4. Foreground Service e tipo

**Android 14 (API 34)** exige que foreground services declarem **tipo**. Para gravação de mic:

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<service
    android:name=".recording.RecordingService"
    android:foregroundServiceType="microphone"
    android:exported="false" />
```

E no startForeground:

```kotlin
ServiceCompat.startForeground(
    this,
    NOTIFICATION_ID,
    notification,
    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
)
```

### 3.3.5. Wake lock

```kotlin
val powerManager = getSystemService(POWER_SERVICE) as PowerManager
val wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    "AulaLogger::Recording"
)
wakeLock.acquire(MAX_RECORDING_HOURS * 3600 * 1000L)
// ...
wakeLock.release()  // SEMPRE em onDestroy / em finally
```

`PARTIAL_WAKE_LOCK` mantém CPU ligada mesmo com tela apagada. Não mantém tela ligada (bateria desnecessária). Tempo limite 12h é segurança contra leak.

### 3.3.6. Battery optimization exclusion

A primeira vez que o usuário abre o app, vamos:
1. Explicar em modal por que precisamos.
2. Solicitar `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` via intent.
3. Se negado, mostrar alerta amarelo "gravações longas podem ser interrompidas".

```kotlin
fun requestBatteryOptimizationExemption(activity: Activity) {
    val pm = activity.getSystemService(POWER_SERVICE) as PowerManager
    val pkg = activity.packageName
    if (!pm.isIgnoringBatteryOptimizations(pkg)) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$pkg")
        }
        activity.startActivity(intent)
    }
}
```

### 3.3.7. Fabricantes agressivos

Xiaomi (MIUI), Samsung (One UI), Huawei (EMUI), Oppo, Vivo agressivamente matam apps em background mesmo com foreground service. **Não há solução técnica universal.**

**Mitigação:** documentar explicitamente em onboarding e em modal de "tudo pronto?":

```
🔋 Para garantir que sua aula não seja interrompida:

Caso 1: Xiaomi/MIUI
  Configurações → Apps → AulaLogger → Bateria → Sem restrições
  Configurações → Apps → AulaLogger → Permissões → Auto-início

Caso 2: Samsung/One UI
  Configurações → Manutenção do dispositivo → Bateria → Limites → 
  Apps que nunca dormem → adicionar AulaLogger

Caso 3: Huawei/EMUI
  Configurações → Bateria → Inicialização de aplicativo → 
  AulaLogger → desativar gerenciamento automático

[Eu já fiz isso no meu celular]
```

Detectamos o fabricante via `Build.MANUFACTURER` e mostramos só o relevante. Link direto para a tela quando possível (deeplinks específicos por fabricante).

### 3.3.8. HealthMonitor

Roda em coroutine, verifica a cada 5s:

| Verificação | Threshold | Ação |
|-------------|-----------|------|
| Espaço livre em disco | < 200MB | Notificação amarela "pouco espaço" |
| Espaço livre em disco | < 50MB | Parar gravação limpa, alerta vermelho |
| Bateria | < 15% e não carregando | Notificação amarela |
| Bateria | < 5% e não carregando | Parar gravação limpa |
| Último chunk salvo | > 60s atrás | Notificação amarela "atraso na escrita" |
| Último chunk salvo | > 180s atrás | Tentativa de auto-recovery + alerta |

---

## 3.4. Recovery em caso de crash

Cenário: o processo do app morre durante gravação (OOM, crash do JNI, kill do sistema).

**O que acontece:**
1. Foreground service tenta restart automático (Android oferece, mas não garante).
2. `meta.json` ficou com `"status": "recording"` — flag de "interrompida abruptamente".
3. Chunks anteriores estão íntegros em disco (graças ao fsync + rename atômico).
4. Quando usuário reabre o app:
   - Detectamos sessões com `status="recording"`.
   - Marcamos como `status="crashed"`.
   - Mostramos diálogo: "Encontramos uma gravação de 1h47 do dia X que foi interrompida. Os chunks foram preservados e podem ser unidos."
   - Botões: "Recuperar e processar" (concatena chunks, marca como completa) | "Ver chunks" (lista) | "Descartar".

**Perda máxima:** o chunk em escrita no momento do crash (até 30s) + RingBuffer (até 5s).

**Implementação do recovery:**
```kotlin
class CrashRecovery(private val context: Context) {
    suspend fun findInterruptedSessions(): List<Session> = withContext(Dispatchers.IO) {
        val sessionsDir = File(context.filesDir, "recordings")
        return@withContext sessionsDir.listFiles()
            ?.mapNotNull { it.readManifestOrNull() }
            ?.filter { it.status == "recording" }
            ?: emptyList()
    }
    
    suspend fun recoverSession(session: Session): RecoveryResult = withContext(Dispatchers.IO) {
        val chunks = session.dir.listFiles { f -> f.name.matches(Regex("chunk-\\d+\\.wav")) }
            ?.sortedBy { it.name }
            ?: emptyList()
        
        // Verifica integridade de cada chunk (header WAV válido, tamanho >= 44 bytes)
        val validChunks = chunks.filter { isValidWav(it) }
        val invalidChunks = chunks - validChunks.toSet()
        
        // Atualiza manifesto
        session.copy(
            status = "recovered",
            chunks_count = validChunks.size,
            total_duration_sec = validChunks.size * session.chunk_duration_sec
        ).writeManifest()
        
        // Limpa .tmp órfãos
        session.dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
        
        RecoveryResult(
            recoveredChunks = validChunks.size,
            corruptedChunks = invalidChunks.size,
            recoveredDurationSec = validChunks.size * session.chunk_duration_sec
        )
    }
}
```

---

## 3.5. Pause / resume

Pausar = parar AudioRecord, manter Service vivo, manter notificação como "Pausado".
Retomar = iniciar novo `AudioRecord`, novo chunk começa do zero.

**No transcript final**, gaps de pausa são representados como `[PAUSA — 2min]`.

---

## 3.6. Marcadores em tempo real

Botão "Marcar momento" na tela de gravação (e na notificação).

```kotlin
fun addMarker(label: String? = null) {
    val tsMs = currentSessionElapsedMs()
    val marker = Marker(tsMs, label ?: "Marcador ${markers.size + 1}")
    markers.add(marker)
    persistMarker(marker)  // salva imediatamente em meta.json
}
```

Marcadores aparecem na timeline do viewer. Útil para "marcar onde fiz uma transição importante".

---

## 3.7. Métricas de bateria — como medir

Antes do release, medir empiricamente:

1. Carregar 100% bateria, modo avião ON (isola variável de rede).
2. Iniciar gravação, tela apagada, deixar por 1h.
3. Medir consumo: `adb shell dumpsys batterystats`.
4. Repetir em 5 celulares (Pixel, Samsung Médio, Xiaomi médio, Motorola, Galaxy A).

**Meta:** consumo < 8%/h (configurações default).
**Otimizações se acima:** reduzir frequência de eventos, reduzir frequência de UI updates, audit de wake locks.

---

## 3.8. Plano de testes do subsistema de gravação

### Testes unitários (JUnit, em `app/modules/aulalogger-native/android/src/test/`)

- ChunkWriter: escreve N chunks corretamente, header WAV válido, recovery após falha simulada.
- Manifest: serialização/deserialização, atomic update.
- HealthMonitor: triggers corretos para cada threshold.
- Recovery: detecta sessões corrompidas, recupera chunks válidos, descarta inválidos.

### Testes de integração (Espresso/UIAutomator + ferramentas custom)

- Gravar 5min em emulador, verificar 10 chunks gerados, verificar duração total.
- Gravar 5min, simular kill do processo, verificar recovery.
- Gravar 5min, encher armazenamento (criar arquivo dummy de 50GB), verificar parada limpa.

### Testes de stress (manuais com instrumentação)

- Gravar 4h em celular real (tela apagada, modo avião). Verificar ausência de gaps.
- Gravar 4h enquanto usuário usa outros apps. Verificar service sobrevive.
- Gravar 4h com bateria começando em 30%. Verificar ou término limpo ou alerta correto.
- Gravar 1h em cada um dos 5 celulares-alvo.

### Testes de regressão a cada release

Suite automatizada de gravação 30min com kill simulado em momentos aleatórios. Roda em CI com emulador.

---

## 3.9. Edge cases conhecidos

| Cenário | Tratamento |
|---------|------------|
| Usuário recebe ligação durante gravação | AudioRecord retorna null/erro de focus. Pausamos auto, marcamos no manifesto, retomamos quando chamada acabar |
| Outro app começa a gravar mic | Mesma coisa: focus loss, pause + retomar |
| Bluetooth headset desconecta | Detectamos via `AudioManager.AudioDeviceCallback`, alertamos usuário, continuamos com mic do device |
| Volume zerado | Detectamos via análise de RMS dos buffers, alertamos "áudio muito baixo, verifique microfone" |
| Plug-in fone | Som muda fonte mas continua gravando (se permitido pelo source) |
| Idioma do sistema mudado mid-session | Sem efeito |
| Date/time mudado mid-session | Usamos elapsed time monotônico, não wall clock |
| Memória RAM muito baixa | OOM killer pode matar app. Recovery cobre isso |
| Storage full mid-write | Erro de I/O capturado, tentativa de cleanup, parada limpa |
| Permission revogada mid-session | Detectamos, paramos limpo, mensagem clara |

---

## 3.10. Sprint plan do subsistema de gravação

| Sprint | Entrega |
|--------|---------|
| Sprint 1 (sem 1–2) | Setup projeto + AudioRecord básico funcionando, escreve 1 arquivo WAV de 1min |
| Sprint 2 (sem 3–4) | RecordingService completo, foreground notification, chunking, persistência |
| Sprint 3 (sem 5–6) | Wake lock, battery optimization, fabricantes, edge cases (call, focus loss) |
| Sprint 4 (sem 7) | Recovery em caso de crash, HealthMonitor, métricas |
| Sprint 5 (sem 8) | Stress test 4h+, ajustes finos, documentação para usuário |

**Marco:** ao final do sprint 5, gravação está validada. Fase 2 (transcrição) pode começar.
