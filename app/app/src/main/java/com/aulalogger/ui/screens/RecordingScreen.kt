package com.aulalogger.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Warning
import com.aulalogger.recording.RecordingController
import com.aulalogger.recording.SystemMonitor
import com.aulalogger.ui.anim.rememberPulse
import com.aulalogger.ui.components.Waveform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onFinished: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val elapsedSec by RecordingController.elapsedSecFlow.collectAsState()
    val paused by RecordingController.isPausedFlow.collectAsState()
    val activeIdState by RecordingController.activeSessionIdFlow.collectAsState()
    val audioLevel by RecordingController.audioLevel.collectAsState()
    val warnings by SystemMonitor.warnings.collectAsState()
    var sessionId: String? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        val existing = RecordingController.activeSessionId
        if (existing == null) {
            val id = UUID.randomUUID().toString()
            // NEW-013: start retorna false se outra gravação iniciou nesse meio-tempo
            // (corrida com widget, por exemplo). Sincroniza com o id real do service.
            val started = RecordingController.start(context, id)
            sessionId = if (started) id else RecordingController.activeSessionId
        } else {
            sessionId = existing
        }
    }

    // Sincroniza sessionId quando o Service publica activeSessionId
    LaunchedEffect(activeIdState) {
        if (activeIdState != null && sessionId == null) sessionId = activeIdState
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gravando", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Timer
            Text(
                formatHms(elapsedSec),
                fontSize = 80.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground
            )

            Waveform(
                level = audioLevel,
                color = if (paused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
            )

            // Status com pulso quando gravando
            val pulse = if (!paused) rememberPulse(min = 0.7f, max = 1f, periodMs = 1100) else 1f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (paused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(8.dp).scale(pulse)
                ) {}
                Spacer(Modifier.width(8.dp))
                Text(
                    if (paused) "PAUSADO" else "GRAVANDO",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "A transcrição será gerada automaticamente ao terminar a gravação.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Warnings de bateria/disco/mic mudo (BUG-038/039/025)
            if (warnings.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    warnings.forEach { w ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    w.message,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { SystemMonitor.dismiss(w.kind) }) {
                                    Text("OK", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleControl(
                    icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    label = if (paused) "Retomar" else "Pausar",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (paused) RecordingController.resume(context) else RecordingController.pause(context)
                    },
                    background = MaterialTheme.colorScheme.surfaceContainer,
                    iconColor = MaterialTheme.colorScheme.onSurface,
                    size = 72
                )
                val coScope = rememberCoroutineScope()
                LongPressStopButton(
                    onConfirmed = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val id = sessionId ?: RecordingController.activeSessionId
                        RecordingController.stop(context)
                        // NEW-005: espera o RecordingService finalizar (activeSessionId vira null
                        // após markCompleted + reset). Sem isso navegamos para SessionDetail
                        // enquanto o WAV ainda está sendo finalizado, mostrando "Arquivo vazio".
                        coScope.launch {
                            withTimeoutOrNull(3_000L) {
                                RecordingController.activeSessionIdFlow.first { it == null }
                            }
                            if (id != null) onFinished(id) else onCancel()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LongPressStopButton(onConfirmed: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var holding by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (holding && !confirmed) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "stopProgress"
    )

    // Trigger via LaunchedEffect: aguarda 800ms enquanto holding está true.
    // Sem race com finishedListener da animação.
    LaunchedEffect(holding) {
        if (holding && !confirmed) {
            kotlinx.coroutines.delay(800)
            if (holding && !confirmed) {
                confirmed = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirmed()
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(108.dp),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.error,
                trackColor = Color.Transparent
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(96.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                holding = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                try { tryAwaitRelease() } finally {
                                    holding = false
                                }
                            }
                        )
                    },
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "Parar (segurar)",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (holding) "Continue segurando…" else "Segurar 1s para parar",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CircleControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    background: Color,
    iconColor: Color,
    size: Int
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = background,
            modifier = Modifier.size(size.dp),
            onClick = onClick,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size((size / 2).dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatHms(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
