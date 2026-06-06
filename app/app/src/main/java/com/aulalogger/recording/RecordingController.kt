package com.aulalogger.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

object RecordingController {

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionIdFlow: kotlinx.coroutines.flow.StateFlow<String?> = _activeSessionId
    var activeSessionId: String?
        get() = _activeSessionId.value
        set(value) { _activeSessionId.value = value }

    @Volatile var startedAtMs: Long = 0L
    @Volatile var pausedAtMs: Long = 0L
    @Volatile var totalPausedMs: Long = 0L

    private val _isPaused = MutableStateFlow(false)
    val isPausedFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isPaused
    var isPaused: Boolean
        get() = _isPaused.value
        set(value) { _isPaused.value = value }

    val audioLevel = MutableStateFlow(0f)               // 0..1
    val elapsedSecFlow = MutableStateFlow(0L)           // atualizado pelo Service

    fun elapsedSec(): Long {
        if (startedAtMs == 0L) return 0
        val now = if (isPaused) pausedAtMs else System.currentTimeMillis()
        return ((now - startedAtMs - totalPausedMs) / 1000L).coerceAtLeast(0)
    }

    /**
     * Solicita start ao service. Retorna `false` se já há gravação em andamento
     * — útil para a UI não navegar para um id que nunca foi persistido (BUG-012 / NEW-013).
     * O service ainda valida internamente (handleStart também checa activeSessionId).
     */
    fun start(context: Context, sessionId: String): Boolean {
        if (_activeSessionId.value != null) return false
        ContextCompat.startForegroundService(
            context,
            Intent(context, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START
                putExtra(RecordingService.EXTRA_SESSION_ID, sessionId)
            }
        )
        return true
    }

    fun pause(context: Context) {
        if (isPaused) return
        context.startService(Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_PAUSE
        })
    }

    fun resume(context: Context) {
        if (!isPaused) return
        context.startService(Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_RESUME
        })
    }

    fun stop(context: Context) {
        context.startService(Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        })
    }

    fun reset() {
        _activeSessionId.value = null
        startedAtMs = 0L
        pausedAtMs = 0L
        totalPausedMs = 0L
        _isPaused.value = false
        audioLevel.value = 0f
        elapsedSecFlow.value = 0L
    }
}
