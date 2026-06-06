package com.aulalogger.state

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.aulalogger.transcription.ModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppStatus(
    val micGranted: Boolean,
    val notificationGranted: Boolean,
    val modelInstalled: Boolean,
    val modelDownloading: Boolean,
    val modelDownloadPct: Int
) {
    val ready: Boolean
        get() = micGranted && notificationGranted && modelInstalled
}

object AppStatusHolder {

    private val _status = MutableStateFlow(
        AppStatus(
            micGranted = false,
            notificationGranted = false,
            modelInstalled = false,
            modelDownloading = false,
            modelDownloadPct = 0
        )
    )
    val status: StateFlow<AppStatus> = _status

    fun refresh(context: Context) {
        val mic = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val modelOk = ModelManager.isModelInstalled(context, ModelManager.getSelectedProfile(context))

        _status.value = _status.value.copy(
            micGranted = mic,
            notificationGranted = notif,
            modelInstalled = modelOk
        )
    }

    fun setDownloadingProgress(pct: Int) {
        _status.value = _status.value.copy(modelDownloading = true, modelDownloadPct = pct)
    }

    fun setDownloadFinished(success: Boolean) {
        _status.value = _status.value.copy(
            modelDownloading = false,
            modelDownloadPct = if (success) 100 else 0,
            modelInstalled = success
        )
    }
}
