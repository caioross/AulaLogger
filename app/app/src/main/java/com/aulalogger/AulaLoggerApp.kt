package com.aulalogger

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.aulalogger.data.Session
import com.aulalogger.data.SessionRepository
import com.aulalogger.transcription.ModelManager
import com.aulalogger.transcription.TranscriptionPrefs
import com.aulalogger.ui.theme.ThemePrefs

class AulaLoggerApp : Application() {

    lateinit var repo: SessionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = SessionRepository(this)
        ThemePrefs.init(this)
        createNotificationChannels()
        autoTranscribeRecovered()
    }

    /**
     * Aulas recuperadas (que crashearam no meio) ficam com transcript vazio.
     * Disparamos transcrição automaticamente — usuário vai ver o progresso.
     * Só funciona se o modelo estiver baixado; senão, espera ação manual.
     */
    private fun autoTranscribeRecovered() {
        try {
            if (!ModelManager.isModelInstalled(this)) return
            // Respeita o toggle: sem isso o app pegava ~500 MB e maxava a CPU já
            // na abertura, parecendo "travado". Desligado → o usuário inicia
            // manualmente pela tela da aula recuperada.
            if (!TranscriptionPrefs.autoTranscribeEnabled(this)) return
            val pending = repo.sessions.value.firstOrNull {
                it.status == Session.STATUS_RECOVERED && it.transcript.isBlank()
            } ?: return
            // Apenas a primeira; se o usuário tiver várias, transcrição
            // sequencial seria iniciada quando cada uma terminar (futuro).
            com.aulalogger.transcription.TranscriptionService.start(this, pending.id)
        } catch (_: Throwable) {
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Channel "recording" antigo era IMPORTANCE_LOW; OEMs (Xiaomi, Vivo) matavam
        // o serviço com mais facilidade. Migramos para v2 com IMPORTANCE_DEFAULT
        // (silencioso por configuração, mas mais resistente a kills).
        try { nm.deleteNotificationChannel("aulalogger.recording") } catch (_: Throwable) {}

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDING,
                getString(R.string.notification_channel_recording),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_recording_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRANSCRIPTION,
                getString(R.string.notification_channel_transcription),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_transcription_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    companion object {
        const val CHANNEL_RECORDING = "aulalogger.recording.v2"
        const val CHANNEL_TRANSCRIPTION = "aulalogger.transcription"

        @Volatile
        private var instance: AulaLoggerApp? = null
        fun get(): AulaLoggerApp = instance ?: error("App not initialized")
    }
}
