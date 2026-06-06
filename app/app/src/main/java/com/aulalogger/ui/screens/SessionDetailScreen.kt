package com.aulalogger.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aulalogger.AulaLoggerApp
import com.aulalogger.transcription.ModelManager
import com.aulalogger.transcription.TranscriptionService
import com.aulalogger.transcription.TranscriptionState
import com.aulalogger.ui.components.AudioPlayer
import com.aulalogger.util.ShareSaveUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onOpenAnalysis: (String) -> Unit
) {
    val app = AulaLoggerApp.get()
    val sessions by app.repo.sessions.collectAsState()
    val session = sessions.firstOrNull { it.id == sessionId }
    val context = LocalContext.current
    var deleteDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var redoDialog by remember { mutableStateOf(false) }
    val transcriptionState by TranscriptionState.state.collectAsState()
    val isTranscribing = transcriptionState.sessionId == sessionId &&
        transcriptionState.phase == TranscriptionState.Phase.RUNNING
    val transcriptionError = transcriptionState.sessionId == sessionId &&
        transcriptionState.phase == TranscriptionState.Phase.ERROR

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.name ?: "—", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { renameDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Renomear")
                    }
                    IconButton(onClick = { deleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Apagar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (session == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aula não encontrada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding).fillMaxSize().padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${formatDateTime(session.startedAt)}  •  ${formatDuration(session.durationSec)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            // Player
            AudioPlayer(filePath = session.audioFile, modifier = Modifier.fillMaxWidth())

            // Linha de ações de áudio
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { ShareSaveUtils.shareAudio(context, session) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Compartilhar áudio", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { ShareSaveUtils.saveAudioToDownloads(context, session) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Baixar áudio", fontSize = 12.sp)
                }
            }

            Text(
                "Tamanho: ${formatBytes(session.audioBytes)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Transcrição
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Transcrição",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                if (session.transcript.isNotBlank() && !isTranscribing) {
                    IconButton(onClick = { redoDialog = true }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refazer transcrição")
                    }
                    IconButton(onClick = { copyToClipboard(context, session.transcript) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar")
                    }
                    IconButton(onClick = { ShareSaveUtils.shareTranscriptText(context, session) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Compartilhar")
                    }
                    IconButton(onClick = { ShareSaveUtils.saveTranscriptToDownloads(context, session) }) {
                        Icon(Icons.Filled.Download, contentDescription = "Baixar txt")
                    }
                }
            }

            // Banner de erro de transcrição (BUG-009)
            if (transcriptionError) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Falha na transcrição",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            transcriptionState.message.ifBlank { "Erro desconhecido" },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (!ModelManager.isModelInstalled(context)) {
                                    Toast.makeText(context, "Modelo não baixado.", Toast.LENGTH_LONG).show()
                                } else {
                                    TranscriptionState.reset()
                                    TranscriptionService.start(context, session.id)
                                }
                            }
                        ) { Text("Tentar novamente") }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTranscribing) {
                    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = transcriptionState.progress,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400),
                        label = "transcribeProgress"
                    )
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            transcriptionState.message,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${formatTime(transcriptionState.processedSec)} de ${formatTime(transcriptionState.totalSec)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (transcriptionState.rate > 0.05f) {
                                Text(
                                    String.format(java.util.Locale.US, "%.1f× tempo real", transcriptionState.rate),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "App pode ser minimizado — a transcrição continua em background.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { TranscriptionService.cancel(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancelar transcrição") }
                    }
                } else if (session.transcript.isBlank()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Transcrição ainda não foi gerada.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            if (!ModelManager.isModelInstalled(context)) {
                                Toast.makeText(context,
                                    "Modelo não baixado. Vá em Configurações → Modelos.",
                                    Toast.LENGTH_LONG).show()
                            } else {
                                TranscriptionService.start(context, session.id)
                            }
                        }) { Text("Transcrever agora") }
                    }
                } else {
                    Text(
                        session.transcript,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Análise IA
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                onClick = { onOpenAnalysis(session.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Análise pedagógica via IA", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (session.analysis.isBlank())
                                "Conecte sua API (Gemini/GPT/Claude/OpenRouter) e gere uma análise"
                            else "Análise gerada via ${session.analysisProvider}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (redoDialog) {
            AlertDialog(
                onDismissRequest = { redoDialog = false },
                title = { Text("Refazer transcrição?") },
                text = {
                    Text(
                        "A transcrição será gerada novamente. " +
                            "O texto atual fica salvo até o final do processo — se algo falhar, ele continua intacto."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (!ModelManager.isModelInstalled(context)) {
                            Toast.makeText(context, "Modelo não baixado.", Toast.LENGTH_LONG).show()
                        } else {
                            // BUG-008: NÃO apaga transcript antigo agora. O service
                            // só sobrescreve ao SUCESSO. Se falhar/cancelar, o texto
                            // antigo permanece como está.
                            TranscriptionService.start(context, session.id)
                        }
                        redoDialog = false
                    }) { Text("Refazer") }
                },
                dismissButton = {
                    TextButton(onClick = { redoDialog = false }) { Text("Cancelar") }
                }
            )
        }

        if (renameDialog) {
            var newName by remember { mutableStateOf(session.name) }
            AlertDialog(
                onDismissRequest = { renameDialog = false },
                title = { Text("Renomear aula") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        // BUG-035: limita tamanho e remove quebras de linha
                        onValueChange = { newName = it.take(120).replace('\n', ' ') },
                        singleLine = true,
                        supportingText = { Text("${newName.length}/120", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val trimmed = newName.trim().take(120)
                        if (trimmed.isNotEmpty()) app.repo.renameSession(session.id, trimmed)
                        renameDialog = false
                    }) { Text("Salvar") }
                },
                dismissButton = {
                    TextButton(onClick = { renameDialog = false }) { Text("Cancelar") }
                }
            )
        }

        if (deleteDialog) {
            AlertDialog(
                onDismissRequest = { deleteDialog = false },
                title = { Text("Apagar aula?") },
                text = { Text("O áudio e a transcrição serão removidos permanentemente.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            app.repo.delete(session.id)
                            deleteDialog = false
                            onBack()
                        }
                    ) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteDialog = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText("AulaLogger", text))
    Toast.makeText(context, "Transcrição copiada", Toast.LENGTH_SHORT).show()
}

private fun formatDateTime(epoch: Long): String {
    val fmt = java.text.SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", java.util.Locale("pt", "BR"))
    return fmt.format(java.util.Date(epoch))
}
private fun formatDuration(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return when { h > 0 -> "%dh%02dmin".format(h, m); m > 0 -> "%dmin%02ds".format(m, s); else -> "%ds".format(s) }
}
private fun formatTime(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, ss)
    else String.format(java.util.Locale.US, "%d:%02d", m, ss)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1e6)
    bytes >= 1000 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1e3)
    else -> "$bytes B"
}
