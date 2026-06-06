package com.aulalogger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.aulalogger.state.AppStatusHolder
import com.aulalogger.ui.AulaLoggerNavHost
import com.aulalogger.ui.theme.AulaLoggerTheme
import com.aulalogger.ui.theme.ThemeMode
import com.aulalogger.ui.theme.ThemePrefs

class MainActivity : ComponentActivity() {

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        micGrantedFlow.value = granted
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* opcional para Android 13+ */ }

    private var micGranted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        micGrantedFlow.value = micGranted

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val themeMode by ThemePrefs.mode.collectAsState()
            AulaLoggerTheme(mode = themeMode) {
                ApplySystemBars()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasMic by micGrantedFlow.collectAsState()
                    AulaLoggerNavHost(
                        hasMicPermission = hasMic,
                        requestMicPermission = {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        requestBatteryOptimizationExemption = {
                            requestIgnoreBatteryOptimizations()
                        }
                    )
                }
            }
        }
    }

    private val micGrantedFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun onResume() {
        super.onResume()
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        micGranted = granted
        micGrantedFlow.value = granted
        // Reflete mudanças vindas das Settings do Android (mic, notif, modelo).
        AppStatusHolder.refresh(applicationContext)
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        // BUG-028: só pede uma vez por instalação. Se usuário recusar, respeita.
        val sp = getSharedPreferences("battery_prefs", MODE_PRIVATE)
        if (sp.getBoolean("battery_requested", false)) return
        sp.edit().putBoolean("battery_requested", true).apply()
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (_: Throwable) {
            // alguns OEMs bloqueiam — tudo bem
        }
    }
}

/**
 * Sincroniza ícones da status bar / nav bar com o tema atual.
 * Roda só quando `lightIcons` muda — não a cada recomposição.
 */
@androidx.compose.runtime.Composable
private fun ApplySystemBars() {
    val view = LocalView.current
    val onSurfaceLuminance = MaterialTheme.colorScheme.onSurface.luminance()
    val lightIcons = onSurfaceLuminance < 0.5f
    if (!view.isInEditMode) {
        androidx.compose.runtime.LaunchedEffect(lightIcons) {
            val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightIcons
            controller.isAppearanceLightNavigationBars = lightIcons
        }
    }
}

