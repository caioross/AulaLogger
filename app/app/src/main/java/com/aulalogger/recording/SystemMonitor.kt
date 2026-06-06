package com.aulalogger.recording

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import com.aulalogger.AulaLoggerApp
import com.aulalogger.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Monitora durante a gravação:
 * - **Bateria baixa** (< 15% e não carregando) → warn uma vez.
 * - **Disco quase cheio** (< 200 MB livres em filesDir) → warn uma vez.
 * - **Mic mudo** (audioLevel < threshold por > 30s) → warn periódico.
 *
 * Não interrompe a gravação por conta própria — só sinaliza. A UI lê
 * `warnings` e mostra.
 */
object SystemMonitor {

    data class Warning(val kind: Kind, val message: String, val raisedAt: Long = System.currentTimeMillis())

    enum class Kind { LOW_BATTERY, LOW_STORAGE, SILENT_MIC, AUDIO_DEVICE_CHANGED }

    private val _warnings = MutableStateFlow<List<Warning>>(emptyList())
    val warnings: StateFlow<List<Warning>> = _warnings

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Já alertados: evita repetir o mesmo Kind. */
    private val seen = mutableSetOf<Kind>()

    fun start(context: Context) {
        if (monitorJob?.isActive == true) return
        seen.clear()
        _warnings.value = emptyList()
        val appContext = context.applicationContext
        monitorJob = scope.launch {
            var silentMicSec = 0
            while (true) {
                try {
                    if (Kind.LOW_BATTERY !in seen && isBatteryLow(appContext)) {
                        raise(Kind.LOW_BATTERY, appContext.getString(R.string.warn_low_battery))
                    }
                    if (Kind.LOW_STORAGE !in seen && isStorageLow(appContext)) {
                        raise(Kind.LOW_STORAGE, appContext.getString(R.string.warn_low_storage))
                    }
                    // Mic mudo (BUG-025): se level médio < 0.02 por > 30s
                    val level = RecordingController.audioLevel.value
                    if (!RecordingController.isPaused && level < 0.02f) {
                        silentMicSec += 5
                        if (silentMicSec >= 30 && Kind.SILENT_MIC !in seen) {
                            raise(Kind.SILENT_MIC,
                                appContext.getString(R.string.warn_mic_silent, silentMicSec))
                        }
                    } else {
                        silentMicSec = 0
                        // Se voltou a captar, perdoa o warning silencioso (permite re-alertar depois)
                        seen.remove(Kind.SILENT_MIC)
                    }
                } catch (_: Throwable) {
                }
                delay(5_000)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        seen.clear()
        _warnings.value = emptyList()
    }

    fun raiseAudioDeviceChanged(context: Context) {
        if (Kind.AUDIO_DEVICE_CHANGED in seen) return
        raise(Kind.AUDIO_DEVICE_CHANGED, context.getString(R.string.warn_audio_device_changed))
    }

    private fun raise(kind: Kind, message: String) {
        if (kind in seen) return
        seen.add(kind)
        _warnings.value = _warnings.value + Warning(kind, message)
    }

    fun dismiss(kind: Kind) {
        _warnings.value = _warnings.value.filterNot { it.kind == kind }
    }

    private fun isBatteryLow(context: Context): Boolean {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return false
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
            pct in 0..14 && !charging
        } catch (_: Throwable) { false }
    }

    private fun isStorageLow(context: Context): Boolean {
        return try {
            val path: File = context.filesDir
            val stat = StatFs(path.absolutePath)
            val freeBytes = stat.availableBytes
            freeBytes < 200L * 1024 * 1024  // < 200 MB
        } catch (_: Throwable) { false }
    }
}
