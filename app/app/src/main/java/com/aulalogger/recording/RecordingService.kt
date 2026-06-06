package com.aulalogger.recording

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aulalogger.AulaLoggerApp
import com.aulalogger.MainActivity
import com.aulalogger.data.Session
import com.aulalogger.transcription.TranscriptionPrefs
import com.aulalogger.transcription.TranscriptionService
import com.aulalogger.widget.AulaLoggerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground Service de GRAVAÇÃO somente.
 *
 * - Persiste a Session no repo IMEDIATAMENTE (status=in_progress).
 *   Heartbeat a cada 10s atualiza durationSec/audioBytes para que, em caso de
 *   crash do processo, a próxima abertura recupere a session.
 * - Em falha de captura (read() retornando null), auto-stop limpo.
 * - Wake lock partial pelo tempo da gravação.
 */
class RecordingService : Service() {

    private val job: Job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private var captureJob: Job? = null
    private var tickJob: Job? = null
    private var heartbeatJob: Job? = null

    private var capture: AudioCapture? = null
    private var writer: WavWriter? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    private var sessionId: String = ""
    private var startedAt: Long = 0L
    private var audioFile: File? = null

    private val stopping = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Enquanto true, o loop de captura roda. Setar false (+ capture.signalStop) o encerra. */
    @Volatile private var capturing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStop(autoTriggered = false)
            else -> Log.w(TAG, "Action desconhecida: ${intent?.action}")
        }
        return START_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (RecordingController.activeSessionId != null) return

        sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        startedAt = System.currentTimeMillis()

        RecordingController.activeSessionId = sessionId
        RecordingController.startedAtMs = startedAt
        RecordingController.totalPausedMs = 0L
        RecordingController.isPaused = false

        val app = applicationContext as AulaLoggerApp
        val dir = app.repo.recordingsDir()
        audioFile = File(dir, "${sessionId}.wav")

        // Persistência precoce: session já aparece como in_progress.
        // Se o processo for morto, a próxima abertura recupera.
        try {
            app.repo.upsertSession(
                Session(
                    id = sessionId,
                    name = defaultName(startedAt),
                    startedAt = startedAt,
                    endedAt = 0L,
                    durationSec = 0L,
                    audioFile = audioFile!!.absolutePath,
                    audioBytes = 0L,
                    status = Session.STATUS_IN_PROGRESS,
                    lastUpdatedAt = startedAt
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao persistir session inicial", t)
        }

        startForegroundCompat()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AulaLogger:Rec").apply {
            setReferenceCounted(false)
            acquire(MAX_HOURS * 3600 * 1000L)
        }

        capture = AudioCapture(sampleRate = 16000)
        writer = WavWriter(audioFile!!, sampleRate = 16000, channels = 1, bitDepth = 16)

        if (capture?.start() != true) {
            Log.e(TAG, "Falha ao iniciar captura")
            handleStop(autoTriggered = true, reason = "Falha ao acessar microfone")
            return
        }

        // BUG-038/039: monitorar bateria, espaço e silêncio de mic
        SystemMonitor.start(applicationContext)

        // BUG-024: detecta plug/unplug de fone, BT, etc.
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cb = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    SystemMonitor.raiseAudioDeviceChanged(applicationContext)
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    SystemMonitor.raiseAudioDeviceChanged(applicationContext)
                }
            }
            audioDeviceCallback = cb
            am.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        } catch (t: Throwable) {
            Log.w(TAG, "Falha ao registrar AudioDeviceCallback", t)
        }

        // Captura em Dispatchers.IO: rec.read() é bloqueante (READ_BLOCKING) e o
        // writer faz fsync. Em Dispatchers.Default isso prenderia uma thread do
        // pool de CPU (compartilhado com a transcrição) e podia gerar glitches.
        //
        // O loop sai quando `capturing` vira false. Como rec.read() é bloqueante
        // e cancelamento de corrotina NÃO o interrompe, handleStop() chama
        // capture.signalStop() (= AudioRecord.stop()) para o read retornar na
        // hora; aí o `if (!capturing) break` encerra de forma limpa.
        capturing = true
        captureJob = scope.launch(Dispatchers.IO) {
            try {
                while (capturing) {
                    val cap = capture ?: break
                    val samples = cap.read()
                    if (!capturing) break  // parado durante o read — sai sem ruído
                    if (samples == null) {
                        Log.w(TAG, "Captura retornou null — encerrando gravação")
                        scope.launch { handleStop(autoTriggered = true, reason = "Áudio interrompido") }
                        break
                    }
                    val count = cap.lastReadCount  // BUG-002: usa count real, não tamanho do buffer
                    RecordingController.audioLevel.value = cap.lastLevel
                    if (!RecordingController.isPaused && count > 0) {
                        writer?.writeShorts(samples, count)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Loop captura crashou", t)
                scope.launch { handleStop(autoTriggered = true, reason = "Erro na captura") }
            }
        }

        tickJob = scope.launch {
            while (true) {
                runCatching {
                    RecordingController.elapsedSecFlow.value = RecordingController.elapsedSec()
                    updateNotification()
                }
                delay(1000)
            }
        }

        heartbeatJob = scope.launch {
            while (true) {
                // BUG-023: 20s em vez de 60s. Threshold de recovery = 60s.
                // Reduz lacuna de "session ainda viva ou abandonada" para 2× heartbeat.
                delay(20_000)
                runCatching {
                    val a = applicationContext as AulaLoggerApp
                    a.repo.updateInProgress(
                        id = sessionId,
                        durationSec = RecordingController.elapsedSec(),
                        audioBytes = audioFile?.length() ?: 0L
                    )
                }
            }
        }
    }

    private fun handlePause() {
        if (RecordingController.isPaused) return
        RecordingController.isPaused = true
        RecordingController.pausedAtMs = System.currentTimeMillis()
        runCatching { updateNotification() }
    }

    private fun handleResume() {
        if (!RecordingController.isPaused) return
        val now = System.currentTimeMillis()
        RecordingController.totalPausedMs += (now - RecordingController.pausedAtMs)
        RecordingController.isPaused = false
        runCatching { updateNotification() }
    }

    private fun handleStop(autoTriggered: Boolean, reason: String? = null) {
        if (!stopping.compareAndSet(false, true)) {
            Log.i(TAG, "handleStop ignorado — já em curso")
            return
        }
        // Encerramento FORA da main thread. handleStop é chamado de
        // onStartCommand (main thread); fazer cancelAndJoin + fsync +
        // persistência aqui bloquearia a UI e causaria ANR ao apertar "Parar".
        scope.launch(Dispatchers.IO) {
            // Encerra o loop de captura de forma DETERMINÍSTICA:
            //  1. capturing=false sinaliza o loop a sair;
            //  2. signalStop() desbloqueia o read() bloqueante pendente;
            //  3. join (com timeout de segurança) espera o loop terminar de fato.
            // O cancelamento de corrotina sozinho NÃO funcionava: o read() em
            // READ_BLOCKING não é ponto de cancelamento, então cancelAndJoin
            // travava pra sempre e a gravação nunca parava.
            // NEW-007: o join também garante que não há writeShorts em voo
            // quando fechamos o WavWriter (senão o header final corromperia).
            capturing = false
            try { capture?.signalStop() } catch (_: Throwable) {}
            try { withTimeoutOrNull(3_000L) { captureJob?.join() } } catch (_: Throwable) {}
            try { tickJob?.cancel() } catch (_: Throwable) {}
            try { heartbeatJob?.cancel() } catch (_: Throwable) {}
            try { SystemMonitor.stop() } catch (_: Throwable) {}
            try {
                audioDeviceCallback?.let {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.unregisterAudioDeviceCallback(it)
                }
                audioDeviceCallback = null
            } catch (_: Throwable) {}
            try { capture?.release() } catch (_: Throwable) {}
            try { writer?.close() } catch (_: Throwable) {}

            val endedAt = System.currentTimeMillis()
            val duration = ((endedAt - startedAt - RecordingController.totalPausedMs) / 1000L).coerceAtLeast(0)
            val file = audioFile

            if (file != null && sessionId.isNotEmpty() && duration > 0 && file.exists()) {
                try {
                    val app = applicationContext as AulaLoggerApp
                    app.repo.markCompleted(
                        id = sessionId,
                        durationSec = duration,
                        audioBytes = file.length(),
                        endedAt = endedAt
                    )
                    Log.i(TAG, "Session finalizada: $sessionId (${duration}s, ${file.length()} bytes, auto=$autoTriggered)")
                    // Enfileira transcrição automática se o usuário deixou ligado.
                    if (TranscriptionPrefs.autoTranscribeEnabled(applicationContext)) {
                        TranscriptionService.start(applicationContext, sessionId)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Falha ao salvar session", t)
                }
            } else if (autoTriggered) {
                Log.w(TAG, "Auto-stop sem áudio aproveitável: reason=$reason")
            }

            RecordingController.reset()
            stopSelfClean()
        }
    }

    private fun stopSelfClean() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "Task removida — gravação continua")
    }

    override fun onDestroy() {
        capturing = false
        captureJob?.cancel()
        tickJob?.cancel()
        heartbeatJob?.cancel()
        scope.cancel()
        try { capture?.release() } catch (_: Throwable) {}
        try { writer?.close() } catch (_: Throwable) {}
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notif = buildNotification()
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notif, fgsType)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPaused = RecordingController.isPaused
        val elapsed = RecordingController.elapsedSec()
        val statusText = if (isPaused) "Pausado" else "Gravando"

        val pauseAction = pendingService(if (isPaused) ACTION_RESUME else ACTION_PAUSE, REQ_PAUSE)
        val stopAction = pendingService(ACTION_STOP, REQ_STOP)

        return NotificationCompat.Builder(this, AulaLoggerApp.CHANNEL_RECORDING)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("$statusText  •  ${formatHms(elapsed)}")
            .setContentText("AulaLogger")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)  // BUG-040: não sincroniza com smartwatch (sem valor)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingOpen)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Retomar" else "Pausar",
                pauseAction
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Parar",
                stopAction
            )
            .build()
    }

    private fun pendingService(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
        try { AulaLoggerWidget.refreshAll(applicationContext) } catch (_: Throwable) {}
    }

    private fun defaultName(epoch: Long): String {
        val fmt = SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR"))
        return "Aula de ${fmt.format(Date(epoch))}"
    }

    private fun formatHms(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    companion object {
        private const val TAG = "RecordingService"
        private const val NOTIFICATION_ID = 1042
        private const val MAX_HOURS = 12L
        private const val REQ_PAUSE = 100
        private const val REQ_STOP = 101

        const val ACTION_START = "com.aulalogger.action.START"
        const val ACTION_PAUSE = "com.aulalogger.action.PAUSE"
        const val ACTION_RESUME = "com.aulalogger.action.RESUME"
        const val ACTION_STOP = "com.aulalogger.action.STOP"

        const val EXTRA_SESSION_ID = "sessionId"
    }
}
