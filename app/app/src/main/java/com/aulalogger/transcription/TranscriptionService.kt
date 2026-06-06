package com.aulalogger.transcription

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aulalogger.AulaLoggerApp
import com.aulalogger.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Foreground Service de TRANSCRIÇÃO.
 *
 * Chunked transcription: divide o áudio em janelas de WINDOW_SEC com OVERLAP_SEC
 * de sobreposição (descartado depois). Cada janela = ~7.7 MB de FloatArray
 * (120s @ 16kHz), em vez dos ~920 MB do áudio inteiro. Janela maior = menos
 * bordas entre chunks e menos reprocessamento de overlap.
 *
 * Cancelamento real: `WhisperJNI.cancel()` seta um flag que o native checa
 * entre tokens. O whisper_full encerra em <2s.
 */
class TranscriptionService : Service() {

    private val job: Job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private var workerJob: Job? = null
    @Volatile private var currentSessionId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sid = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                if (currentSessionId == sid) return START_STICKY
                currentSessionId = sid
                startForegroundCompat(progress = 0f, sessionName = "")
                workerJob?.cancel()
                workerJob = scope.launch { runTranscription(sid) }
            }
            ACTION_CANCEL -> {
                Log.i(TAG, "Cancelamento solicitado")
                // Sinaliza ao native (whisper_full encerra na próxima chance);
                // stopWork() abaixo cancela o worker e libera o serviço.
                try { WhisperJNI.cancel() } catch (_: Throwable) {}
                stopWork()
            }
        }
        return START_STICKY
    }

    private suspend fun runTranscription(sessionId: String) {
        val app = applicationContext as AulaLoggerApp
        val session = app.repo.sessions.value.firstOrNull { it.id == sessionId }
            ?: run {
                Log.e(TAG, "Session $sessionId não encontrada")
                stopWork()
                return
            }

        TranscriptionState.beginFor(sessionId, session.name)
        updateNotification(0f, session.name, "Iniciando…")

        val wav = File(session.audioFile)
        if (!wav.exists() || wav.length() < 1024) {
            // Não sobrescreve transcript existente — só sinaliza erro via State.
            TranscriptionState.fail(sessionId, "Áudio não encontrado")
            stopWork()
            return
        }

        val profile = ModelManager.getSelectedProfile(this)
        val modelFile = ModelManager.modelFile(this, profile)
        if (!modelFile.exists()) {
            TranscriptionState.fail(sessionId, "Modelo não baixado — abra Configurações > Modelos")
            stopWork()
            return
        }

        try {
            val info = WavReader.parseHeader(wav)
            if (info == null || info.bitsPerSample != 16) {
                TranscriptionState.fail(sessionId, "WAV inválido")
                stopWork()
                return
            }

            val samplesPerSec = info.sampleRate
            val totalSamples = (info.dataBytes / 2L).toInt()
            val totalSec = (totalSamples / samplesPerSec).toLong()

            // Inicializa estado já com totalSec para a UI mostrar "Restam ~N min"
            TranscriptionState.beginFor(sessionId, session.name, totalSec)
            TranscriptionState.updateProgress(sessionId, 0.01f, "Carregando modelo…")
            updateNotification(0.01f, session.name, "Carregando modelo…")

            // VAD: baixa o modelo Silero (best-effort). Se já existe, retorna na hora.
            // Pula silêncio → 30-50% mais rápido + sem alucinação nas pausas.
            TranscriptionState.updateProgress(sessionId, 0.005f, "Preparando VAD…")
            val vadOk = try { ModelManager.ensureVadModel(this) } catch (_: Throwable) { false }
            val vadPath = if (vadOk) ModelManager.vadModelFile(this).absolutePath else ""
            if (vadPath.isEmpty()) Log.w(TAG, "VAD indisponível — transcrição sem skip de silêncio")

            // Reserva o ctx do Whisper (cancela eventual idle eviction agendada).
            WhisperPool.acquire()
            WhisperJNI.ensureLoaded()
            WhisperJNI.resetCancel()
            if (!WhisperJNI.init(modelFile.absolutePath)) {
                TranscriptionState.fail(sessionId, "Falha ao carregar modelo")
                stopWork()
                return
            }

            // Usa só os núcleos de alta performance (big cores). Incluir os
            // "little" faria as threads rápidas esperarem as lentas → mais
            // lento, não mais rápido. Ver CpuConfig.
            val threads = CpuConfig.preferredThreadCount()

            Log.i(TAG, "Transcrevendo: ${totalSec}s, ${samplesPerSec}Hz, threads=$threads")

            val windowSamples = WINDOW_SEC * samplesPerSec
            val stepSamples = (WINDOW_SEC - OVERLAP_SEC) * samplesPerSec

            val allSegments = mutableListOf<WhisperJNI.Segment>()
            var offsetSamples = 0
            var lastAcceptedT1Ms = 0L
            var failedChunks = 0
            var processedChunks = 0

            // Tracking de tempo para ETA
            val startMs = System.currentTimeMillis()
            val processedSamples = AtomicLong(0L)
            maxPublishedProgress = 0f  // reset do guard monótono por sessão

            // Stream stateful: 1 file open para a aula toda (em vez de N opens)
            val wavStream = WavReader.Stream(wav, info)
            try {
            while (offsetSamples < totalSamples) {
                if (workerJob?.isCancelled == true) {
                    Log.i(TAG, "Worker cancelado — parando loop")
                    WhisperJNI.cancel()
                    break
                }

                val end = (offsetSamples + windowSamples).coerceAtMost(totalSamples)
                val chunk = wavStream.readRange(offsetSamples, end)
                if (chunk.isEmpty()) {
                    // Vazio perto do fim = EOF normal. Vazio no meio do arquivo =
                    // erro de I/O: registra para não truncar em silêncio.
                    if (offsetSamples < totalSamples - samplesPerSec) {
                        Log.w(TAG, "Leitura vazia em offset=$offsetSamples/$totalSamples — possível I/O truncado")
                    }
                    break
                }

                // Avanço LÍQUIDO desta janela (descontando o overlap). É a base
                // correta para o progresso intra-chunk: usar chunk.size (que inclui
                // o overlap reprocessado) fazia o efetivo passar do real e a barra
                // "pular" pra frente e travar até o próximo chunk alcançar.
                val advanceThisChunk = ((offsetSamples + stepSamples).coerceAtMost(totalSamples) - offsetSamples).toLong()

                // Zera o progresso nativo ANTES de pollar: senão a 1ª leitura
                // pega o 100 residual do chunk anterior e a barra "pula".
                WhisperJNI.resetProgress()

                // Polling intra-chunk: lança coroutine paralela que lê
                // WhisperJNI.getProgress() (0..100 do chunk atual) e atualiza UI.
                val pollJob = scope.launch {
                    while (isActive) {
                        val intra = WhisperJNI.getProgress().coerceIn(0, 100)
                        // samples efetivamente "concluídos" para fins de UI:
                        // os já transcritos + fração do avanço do chunk atual
                        val effectiveSamples = processedSamples.get() + (advanceThisChunk * intra / 100L)
                        publishProgress(
                            sessionId = sessionId,
                            sessionName = session.name,
                            effectiveSamples = effectiveSamples,
                            totalSamples = totalSamples.toLong(),
                            samplesPerSec = samplesPerSec,
                            startMs = startMs
                        )
                        delay(POLL_INTERVAL_MS)
                    }
                }

                processedChunks++
                val segments = try {
                    try {
                        WhisperJNI.transcribe(chunk, language = "pt", threads = threads, vadModelPath = vadPath)
                    } catch (e: Throwable) {
                        // Falha do motor neste trecho (ex.: problema no VAD). Tenta
                        // de novo SEM VAD antes de desistir — assim uma falha
                        // pontual não derruba a transcrição inteira.
                        Log.w(TAG, "Chunk em offset=$offsetSamples falhou (vad=${vadPath.isNotEmpty()}); retry sem VAD", e)
                        WhisperJNI.transcribe(chunk, language = "pt", threads = threads, vadModelPath = "")
                    }
                } catch (e: Throwable) {
                    // Falhou até sem VAD: pula o trecho em vez de abortar tudo.
                    Log.e(TAG, "Chunk em offset=$offsetSamples falhou mesmo sem VAD — pulando", e)
                    failedChunks++
                    emptyList()
                } finally {
                    pollJob.cancel()
                }
                yield()

                // Reposiciona timestamps relativos ao início do áudio total
                // e descarta sobreposição com a janela anterior usando dedup
                // híbrido (tempo + texto).
                val chunkStartMs = (offsetSamples * 1000L) / samplesPerSec
                for (s in segments) {
                    val absT0 = s.t0 + chunkStartMs
                    val absT1 = s.t1 + chunkStartMs
                    val candidate = WhisperJNI.Segment(absT0, absT1, s.text)
                    // 1. Aceita se está fora do overlap temporal:
                    val outsideOverlap = absT0 >= lastAcceptedT1Ms - 200
                    if (outsideOverlap) {
                        allSegments.add(candidate)
                        lastAcceptedT1Ms = absT1
                        continue
                    }
                    // 2. Está dentro do overlap. Verifica se já existe segmento
                    // com texto similar (>= 70%) num intervalo de tempo próximo.
                    // Se sim, descarta (já foi capturado). Se não, aceita —
                    // Whisper pode ter "deslocado" o timestamp mas com texto novo.
                    val isDuplicate = isLikelyDuplicate(candidate, allSegments)
                    if (!isDuplicate) {
                        allSegments.add(candidate)
                        if (absT1 > lastAcceptedT1Ms) lastAcceptedT1Ms = absT1
                    }
                }

                // Avança contadores
                val advanced = (offsetSamples + stepSamples).coerceAtMost(totalSamples) - offsetSamples
                processedSamples.addAndGet(advanced.toLong())
                offsetSamples += stepSamples

                // Update final do chunk com valores precisos
                publishProgress(
                    sessionId = sessionId,
                    sessionName = session.name,
                    effectiveSamples = processedSamples.get(),
                    totalSamples = totalSamples.toLong(),
                    samplesPerSec = samplesPerSec,
                    startMs = startMs
                )
            }
            } finally {
                try { wavStream.close() } catch (_: Throwable) {}
            }

            if (workerJob?.isCancelled == true) {
                TranscriptionState.fail(sessionId, "Cancelada pelo usuário")
                // Não sobrescreve — texto antigo continua intacto.
                stopWork()
                return
            }

            // Se NENHUM trecho produziu texto E houve falhas do motor, é um erro
            // real (não silêncio). Mostra mensagem acionável em vez de salvar
            // "[sem fala detectada]" silenciosamente.
            if (allSegments.isEmpty() && failedChunks > 0) {
                Log.e(TAG, "Todos os $failedChunks/$processedChunks trechos falharam no motor")
                TranscriptionState.fail(
                    sessionId,
                    "O motor de transcrição falhou neste áudio. Tente outro modelo em Configurações → Modelos."
                )
                stopWork()
                return
            }

            TranscriptionState.updateProgress(
                sessionId, 0.98f, "Finalizando…",
                processedSec = totalSec, etaSec = 0
            )
            updateNotification(0.98f, session.name, "Finalizando…")

            val withTs = TranscriptionPrefs.timestampsEnabled(this)
            val withSpk = TranscriptionPrefs.speakersEnabled(this)
            val text = TranscriptFormatter.format(
                allSegments,
                withTimestamps = withTs,
                withSpeakers = withSpk,
                speakerALabel = getString(com.aulalogger.R.string.speaker_a),
                speakerBLabel = getString(com.aulalogger.R.string.speaker_b)
            )

            val finalText = if (text.isBlank()) "[sem fala detectada]" else text
            app.repo.updateTranscript(sessionId, finalText)
            TranscriptionState.complete(sessionId)
            updateNotification(1f, session.name, "Concluída")
            Log.i(TAG, "Transcrição finalizada com ${allSegments.size} segmentos em ${(System.currentTimeMillis() - startMs) / 1000}s")

            // NEW-002: encadeia transcrição da próxima sessão recuperada (se houver).
            // Evita que o usuário precise abrir cada uma manualmente após um crash.
            // Só encadeia se a transcrição automática estiver ligada.
            try {
                if (TranscriptionPrefs.autoTranscribeEnabled(this)) {
                    val next = app.repo.nextRecoveredAwaitingTranscription()
                    if (next != null && next.id != sessionId) {
                        Log.i(TAG, "Encadeando transcrição da próxima recuperada: ${next.id}")
                        start(applicationContext, next.id)
                    }
                }
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao transcrever", t)
            TranscriptionState.fail(sessionId, t.message ?: "Erro desconhecido")
            // Não sobrescreve texto existente em caso de falha (BUG-008).
        } finally {
            // Agenda eviction: se em IDLE_TIMEOUT_MS não houver outra transcrição,
            // o ctx é liberado e ~500 MB de RAM voltam pro sistema.
            WhisperPool.release()
            stopWork()
        }
    }

    /**
     * Publica progresso, ETA e taxa, com throttle interno para não martelar a UI.
     * UI Compose lida com estado fluído via animateFloatAsState.
     */
    @Volatile private var lastPublishMs: Long = 0L
    /** Guard monótono: progresso nunca anda pra trás na mesma sessão. */
    @Volatile private var maxPublishedProgress: Float = 0f
    private fun publishProgress(
        sessionId: String,
        sessionName: String,
        effectiveSamples: Long,
        totalSamples: Long,
        samplesPerSec: Int,
        startMs: Long
    ) {
        val now = System.currentTimeMillis()
        if (now - lastPublishMs < 250L) return  // máx. 4 updates/s para UI
        lastPublishMs = now

        val elapsedMs = (now - startMs).coerceAtLeast(1L)
        val totalSec = totalSamples / samplesPerSec
        val rawProgress = (effectiveSamples.toDouble() / totalSamples).toFloat().coerceIn(0f, 1f)
        // Guard monótono: o poller pode ler progresso nativo levemente menor
        // entre chunks (race) e o overlap de janelas reprocessa 5s. A barra
        // NUNCA deve voltar — clampa pra cima do máximo já publicado.
        val progress = maxOf(rawProgress, maxPublishedProgress)
        maxPublishedProgress = progress
        // processedSec derivado do progresso monótono p/ ficar consistente com a barra.
        val processedSec = (progress * totalSec).toLong()

        // rate = audio_seconds / wall_seconds. >1 = mais rápido que tempo real.
        val rate = if (elapsedMs > 0) (processedSec * 1000f / elapsedMs) else 0f
        val etaSec = if (rate > 0.05f) {
            ((totalSec - processedSec) / rate).toLong().coerceAtLeast(0L)
        } else {
            -1L  // ainda calculando
        }

        val etaText = if (etaSec >= 0) "Restam ~${formatEta(etaSec)}" else "Calculando tempo…"
        val pctText = "${(progress * 100).toInt()}%"
        val message = "$etaText · $pctText"

        TranscriptionState.updateProgress(
            sessionId, progress, message,
            processedSec = processedSec, etaSec = etaSec, rate = rate
        )
        // Notification throttle separado: só atualiza a cada 1s
        maybeUpdateNotification(progress, sessionName, "$pctText · $etaText")
    }

    @Volatile private var lastNotifMs: Long = 0L
    private fun maybeUpdateNotification(progress: Float, sessionName: String, status: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotifMs < 1000L && progress < 0.99f) return
        lastNotifMs = now
        updateNotification(progress, sessionName, status)
    }

    private fun stopWork() {
        currentSessionId = null
        workerJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { WhisperJNI.cancel() } catch (_: Throwable) {}
        try { workerJob?.cancel() } catch (_: Throwable) {}
        try { scope.cancel() } catch (_: Throwable) {}
        // Pool faz eviction agendada; aqui só garante que o pool sabe que
        // não há transcribe ativa. NÃO chamamos free() direto: a janela de
        // idle ainda é desejável caso o user reabra logo.
        try { WhisperPool.release() } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun startForegroundCompat(progress: Float, sessionName: String) {
        val notif = buildNotification(progress, sessionName, "Iniciando…")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notif, type)
    }

    private fun updateNotification(progress: Float, sessionName: String, status: String = "Transcrevendo") {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(progress, sessionName, status))
    }

    private fun buildNotification(progress: Float, sessionName: String, status: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = Intent(this, TranscriptionService::class.java).setAction(ACTION_CANCEL)
        val pendingCancel = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val progressInt = (progress * 100).toInt().coerceIn(0, 100)
        return NotificationCompat.Builder(this, AulaLoggerApp.CHANNEL_TRANSCRIPTION)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (sessionName.isBlank()) "Transcrevendo aula" else "Transcrevendo: $sessionName")
            .setContentText(status)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)  // BUG-040
            .setProgress(100, progressInt, progress < 0.02f)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", pendingCancel)
            .build()
    }

    companion object {
        private const val TAG = "TranscriptionService"
        private const val NOTIFICATION_ID = 2042
        private const val WINDOW_SEC = 120
        private const val OVERLAP_SEC = 4
        private const val POLL_INTERVAL_MS = 250L
        const val ACTION_START = "com.aulalogger.transcribe.START"
        const val ACTION_CANCEL = "com.aulalogger.transcribe.CANCEL"
        const val EXTRA_SESSION_ID = "sessionId"

        /**
         * BUG-010: heurística para detectar segmento duplicado vindo do overlap
         * entre janelas. Compara texto normalizado (sem pontuação/case) com
         * segmentos cuja janela temporal toca a do candidato.
         */
        fun isLikelyDuplicate(
            candidate: WhisperJNI.Segment,
            existing: List<WhisperJNI.Segment>,
            timeToleranceMs: Long = 3_000L,
            textSimilarityThreshold: Float = 0.7f
        ): Boolean {
            val candText = normalizeText(candidate.text)
            if (candText.isEmpty()) return true  // texto vazio = duplicado
            // Procura segmentos próximos no tempo (limita a últimos 10 para velocidade)
            val recent = existing.takeLast(10)
            for (s in recent) {
                val temporallyClose = candidate.t0 in (s.t0 - timeToleranceMs)..(s.t1 + timeToleranceMs)
                if (!temporallyClose) continue
                val existingText = normalizeText(s.text)
                if (existingText.isEmpty()) continue
                if (textSimilarity(candText, existingText) >= textSimilarityThreshold) {
                    return true
                }
            }
            return false
        }

        private fun normalizeText(s: String): String =
            s.lowercase()
                .replace(Regex("[\\p{Punct}\\s]+"), " ")
                .trim()

        /**
         * Similaridade simples baseada em Jaccard de palavras (1.0 = idêntico).
         * Suficiente para detectar duplicação em overlap; mais barato que
         * Levenshtein para textos curtos típicos de segmentos Whisper.
         */
        private fun textSimilarity(a: String, b: String): Float {
            val wordsA = a.split(' ').filter { it.isNotEmpty() }.toSet()
            val wordsB = b.split(' ').filter { it.isNotEmpty() }.toSet()
            if (wordsA.isEmpty() && wordsB.isEmpty()) return 1f
            if (wordsA.isEmpty() || wordsB.isEmpty()) return 0f
            val intersection = wordsA.intersect(wordsB).size
            val union = wordsA.union(wordsB).size
            return intersection.toFloat() / union.toFloat()
        }

        /** Formata tempo em texto humanizado: "45 seg", "12 min", "1h 30min". */
        fun formatEta(sec: Long): String {
            if (sec < 60) return "${sec.coerceAtLeast(0)} seg"
            val totalMin = sec / 60
            if (totalMin < 60) return "$totalMin min"
            val h = totalMin / 60
            val m = totalMin % 60
            return if (m == 0L) "${h}h" else "${h}h ${m}min"
        }

        fun start(context: Context, sessionId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TranscriptionService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_SESSION_ID, sessionId)
                }
            )
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, TranscriptionService::class.java).setAction(ACTION_CANCEL)
            )
        }
    }
}

/** Estado global observável da transcrição em curso. */
object TranscriptionState {
    enum class Phase { IDLE, RUNNING, DONE, ERROR }

    data class Snapshot(
        val sessionId: String?,
        val sessionName: String,
        /** Progresso fracionário [0f, 1f] — para progress bar suave. */
        val progress: Float,
        /** Frase para exibir ao usuário (ex: "Restam ~12 min · 38%"). */
        val message: String,
        val phase: Phase,
        /** Segundos de áudio total da aula. */
        val totalSec: Long,
        /** Segundos de áudio já transcritos. */
        val processedSec: Long,
        /** Estimativa de tempo restante em segundos (-1 se ainda calculando). */
        val etaSec: Long,
        /** Velocidade observada: 1.0 = tempo real, 2.0 = 2× mais rápido. */
        val rate: Float
    )

    private val initial = Snapshot(null, "", 0f, "", Phase.IDLE, 0, 0, -1, 0f)
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<Snapshot> = _state

    fun beginFor(sessionId: String, name: String, totalSec: Long = 0) {
        _state.value = Snapshot(sessionId, name, 0f, "Iniciando", Phase.RUNNING, totalSec, 0, -1, 0f)
    }
    fun updateProgress(
        sessionId: String,
        progress: Float,
        message: String,
        processedSec: Long = _state.value.processedSec,
        etaSec: Long = _state.value.etaSec,
        rate: Float = _state.value.rate
    ) {
        if (_state.value.sessionId == sessionId) {
            _state.value = _state.value.copy(
                progress = progress.coerceIn(0f, 1f),
                message = message,
                phase = Phase.RUNNING,
                processedSec = processedSec,
                etaSec = etaSec,
                rate = rate
            )
        }
    }
    fun complete(sessionId: String) {
        if (_state.value.sessionId == sessionId) {
            _state.value = _state.value.copy(progress = 1f, message = "Concluída", phase = Phase.DONE, etaSec = 0)
        }
    }
    fun fail(sessionId: String, message: String) {
        if (_state.value.sessionId == sessionId) {
            _state.value = _state.value.copy(message = message, phase = Phase.ERROR)
        }
    }
    fun reset() {
        _state.value = initial
    }
}
