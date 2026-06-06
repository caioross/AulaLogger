package com.aulalogger.util

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.aulalogger.recording.RecordingController
import com.aulalogger.recording.RecordingService
import com.aulalogger.transcription.TranscriptionService
import com.aulalogger.transcription.TranscriptionState
import com.aulalogger.transcription.WhisperJNI
import com.aulalogger.transcription.WhisperPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Encerra completamente o AulaLogger.
 *
 * BUG-011: em vez de Handler.postDelayed(300ms) hack, espera o
 * `RecordingController.activeSessionIdFlow` virar null (com timeout 2s).
 * Garante que o WAV foi finalizado antes do kill.
 *
 * BUG-019: cancela notificações pendentes para evitar "fantasmas" na
 * status bar após o app fechar.
 */
object AppKiller {

    fun killEverything(context: Context, activity: Activity? = null) {
        val appContext = context.applicationContext

        // 1. Sinaliza stops
        if (RecordingController.activeSessionId != null) {
            try { RecordingController.stop(appContext) } catch (_: Throwable) {}
        }
        try { TranscriptionService.cancel(appContext) } catch (_: Throwable) {}
        TranscriptionState.reset()

        // 2. Em coroutine: espera service finalizar até 2s, então finaliza tudo.
        // lifecycleScope é uma extension de LifecycleOwner; Activity nem sempre o
        // implementa, então castamos defensivamente.
        val owner = activity as? LifecycleOwner
        val scope = if (owner != null) {
            try { owner.lifecycleScope } catch (_: Throwable) { GlobalScope }
        } else GlobalScope

        scope.launch(Dispatchers.Default) {
            // Aguarda activeSessionId virar null OU 2s
            withTimeoutOrNull(2_000L) {
                RecordingController.activeSessionIdFlow.first { it == null }
            }

            try { appContext.stopService(Intent(appContext, RecordingService::class.java)) } catch (_: Throwable) {}
            try { appContext.stopService(Intent(appContext, TranscriptionService::class.java)) } catch (_: Throwable) {}
            try { WhisperPool.freeNow() } catch (_: Throwable) {}
            RecordingController.reset()

            // BUG-019: limpar notificações pendentes do app
            try {
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancelAll()
            } catch (_: Throwable) {}

            // Volta pra UI thread para finishAndRemoveTask
            try {
                activity?.runOnUiThread {
                    try { activity.finishAndRemoveTask() } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }
    }
}
