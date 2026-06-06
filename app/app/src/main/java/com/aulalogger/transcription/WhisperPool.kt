package com.aulalogger.transcription

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Idle eviction do contexto do Whisper.
 *
 * Whisper Medium ocupa ~500 MB de RAM enquanto carregado. Manter o ctx vivo
 * entre transcrições acelera a 2ª chamada (~5s a 10s economizados em I/O),
 * mas se o usuário não transcreve nada por X minutos, isso é desperdício de
 * memória que faz o Android matar outros apps antes do AulaLogger.
 *
 * Solução: após cada `release()`, agendamos um free em IDLE_TIMEOUT_MS.
 * Qualquer nova chamada de `acquire()` cancela o job pendente e reusa o ctx.
 */
object WhisperPool {

    private const val TAG = "WhisperPool"
    private const val IDLE_TIMEOUT_MS = 5 * 60_000L  // 5 minutos

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var idleJob: Job? = null

    /** Cancela job de eviction pendente. Chamar antes de transcrever. */
    @Synchronized
    fun acquire() {
        idleJob?.cancel()
        idleJob = null
    }

    /** Agenda eviction para daqui IDLE_TIMEOUT_MS. Pode ser cancelado por novo acquire. */
    @Synchronized
    fun release() {
        idleJob?.cancel()
        idleJob = scope.launch {
            try {
                delay(IDLE_TIMEOUT_MS)
                if (WhisperJNI.isInitialized()) {
                    WhisperJNI.free()
                    Log.i(TAG, "Whisper ctx liberado após ${IDLE_TIMEOUT_MS / 1000}s idle")
                }
            } catch (_: Throwable) {
                // cancelado — ok
            }
        }
    }

    /** Força free imediato (ex.: troca de modelo, app encerrar). */
    @Synchronized
    fun freeNow() {
        idleJob?.cancel()
        idleJob = null
        try { WhisperJNI.free() } catch (_: Throwable) {}
    }
}
