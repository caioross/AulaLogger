package com.aulalogger.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aulalogger.AulaLoggerApp
import com.aulalogger.R
import com.aulalogger.data.Session
import com.aulalogger.recording.RecordingController
import com.aulalogger.state.AppStatusHolder
import com.aulalogger.transcription.TranscriptionState
import androidx.compose.ui.draw.clip
import com.aulalogger.ui.anim.rememberPulse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    hasMicPermission: Boolean,
    requestMicPermission: () -> Unit,
    onStartRecording: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val app = AulaLoggerApp.get()
    val sessions by app.repo.sessions.collectAsState()
    val status by AppStatusHolder.status.collectAsState()
    val context = LocalContext.current
    val activeSessionId by RecordingController.activeSessionIdFlow.collectAsState()
    val txState by TranscriptionState.state.collectAsState()
    // Mostra banner só de recuperadas SEM transcrição. Transcritas/limpas somem.
    val pendingRecovered = sessions.count {
        it.status == Session.STATUS_RECOVERED && it.transcript.isBlank()
    }

    LaunchedEffect(Unit) { AppStatusHolder.refresh(context) }
    LaunchedEffect(hasMicPermission) { AppStatusHolder.refresh(context) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AulaLogger", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configurações")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Banner de gravação ativa: aparece se RecordingController está ativo
            // e leva direto para a tela de gravação.
            if (activeSessionId != null) {
                item {
                    RecordingActiveBanner(onClick = onStartRecording)
                }
            }

            // Banner de aulas recuperadas — só enquanto houver pendentes
            if (pendingRecovered > 0) {
                item {
                    RecoveredBanner(count = pendingRecovered)
                }
            }

            item { RecordButton(status.ready, onStart = {
                when {
                    activeSessionId != null -> onStartRecording()  // já está gravando — abre tela
                    status.ready -> onStartRecording()
                    !status.micGranted -> requestMicPermission()
                    else -> onOpenSettings()
                }
            }) }

            if (!status.ready) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        onClick = onOpenSettings,
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
                                pendingMessage(status),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "Resolver",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Aulas (${sessions.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            if (sessions.isEmpty()) {
                item { EmptyStateHero(ready = status.ready) }
            } else {
                items(sessions, key = { it.id }) { s ->
                    val txActive = txState.sessionId == s.id &&
                        txState.phase == TranscriptionState.Phase.RUNNING
                    SessionCard(
                        session = s,
                        transcribing = txActive,
                        txProgress = if (txActive) txState.progress else 0f,
                        txMessage = if (txActive) txState.message else ""
                    ) { onOpenSession(s.id) }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                AppFooter()
            }
        }
    }
}

@Composable
private fun RecordingActiveBanner(onClick: () -> Unit) {
    val pulse = rememberPulse(min = 0.6f, max = 1f, periodMs = 1100)
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(10.dp)
                    .scale(pulse)
            ) {}
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Gravando agora",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Toque para abrir a tela de gravação",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun RecoveredBanner(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (count == 1) "1 aula recuperada após fechamento inesperado"
                else "$count aulas recuperadas após fechamento inesperado",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun RecordButton(ready: Boolean, onStart: () -> Unit) {
    val pulse = if (ready) rememberPulse(min = 1f, max = 1.03f, periodMs = 1900) else 1f
    val pressedScale by animateFloatAsState(
        targetValue = pulse,
        animationSpec = tween(120),
        label = "pressed"
    )
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(220.dp)
                .scale(pressedScale),
            shape = CircleShape,
            color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            onClick = onStart,
            shadowElevation = if (ready) 12.dp else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (ready) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.action_record),
                        color = if (ready) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun pendingMessage(status: com.aulalogger.state.AppStatus): String {
    val faltam = mutableListOf<String>()
    if (!status.micGranted) faltam.add("microfone")
    if (!status.notificationGranted) faltam.add("notificações")
    if (!status.modelInstalled) faltam.add("modelo de IA")
    return when (faltam.size) {
        1 -> "Falta autorizar: ${faltam[0]}"
        else -> "Faltam: ${faltam.joinToString(", ")}"
    }
}

@Composable
private fun SessionCard(
    session: Session,
    transcribing: Boolean = false,
    txProgress: Float = 0f,
    txMessage: String = "",
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                when {
                    transcribing -> StatusBadge(
                        "TRANSCREVENDO",
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    session.status == Session.STATUS_RECOVERED -> StatusBadge(
                        "RECUPERADA",
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer
                    )
                    session.status == Session.STATUS_IN_PROGRESS -> StatusBadge(
                        "GRAVANDO",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatDate(session.startedAt)} • ${formatDuration(session.durationSec)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (transcribing) {
                // Progresso ao vivo da transcrição, sem precisar abrir a aula.
                Spacer(Modifier.height(10.dp))
                val animated by animateFloatAsState(
                    targetValue = txProgress.coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 400),
                    label = "homeTxProgress"
                )
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                if (txMessage.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        txMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (session.transcript.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    session.transcript.take(140) + if (session.transcript.length > 140) "…" else "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AppFooter() {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("criado por ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "caiorossi.casa",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://caiorossi.casa"))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try { context.startActivity(intent) } catch (_: Throwable) {}
            }
        )
    }
}

@Composable
private fun EmptyStateHero(ready: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ícone "ondas" usando 3 círculos concêntricos animados
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                val pulseSlow = rememberPulse(min = 0.8f, max = 1.15f, periodMs = 2200)
                val pulseMid = rememberPulse(min = 0.85f, max = 1.1f, periodMs = 1700)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.size(80.dp).scale(pulseSlow)
                ) {}
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(60.dp).scale(pulseMid)
                ) {}
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Pronto para sua primeira aula?",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (ready)
                    "Toque o botão acima para começar a gravar. A transcrição é feita automaticamente quando você terminar."
                else
                    "Resolva os pré-requisitos em Configurações para liberar a gravação.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun stringResource(id: Int): String = androidx.compose.ui.res.stringResource(id = id)

private val dateFmt = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
private fun formatDate(epoch: Long): String = dateFmt.format(Date(epoch))
private fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return when {
        h > 0 -> String.format(Locale.US, "%dh%02d", h, m)
        m > 0 -> String.format(Locale.US, "%dmin", m)
        else -> String.format(Locale.US, "%ds", s)
    }
}
