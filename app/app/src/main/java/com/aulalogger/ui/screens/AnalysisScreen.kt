package com.aulalogger.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
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
import com.aulalogger.ai.AiAnalyzer
import com.aulalogger.ai.AiKeyStore
import com.aulalogger.ai.AiProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(sessionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = AulaLoggerApp.get()
    val sessions by app.repo.sessions.collectAsState()
    val session = sessions.firstOrNull { it.id == sessionId }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var error: String? by remember { mutableStateOf(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var analyzeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // NEW-006: cancela request HTTP em vôo se o usuário sair da tela.
    // Sem isso, navegar back deixa a conexão aberta até timeout (180s),
    // gastando dados móveis e dinheiro do usuário.
    DisposableEffect(Unit) {
        onDispose {
            analyzeJob?.cancel()
            AiAnalyzer.cancelInFlight()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Análise da aula", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (session?.analysis?.isNotBlank() == true) {
                        IconButton(onClick = { copyToClipboard(context, session.analysis) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (session == null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("Aula não encontrada.") }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding).fillMaxSize().padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (session.transcript.isBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Esta aula ainda não tem transcrição. Volte e gere a transcrição primeiro.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            val configuredProviders = AiProvider.entries.filter { AiKeyStore.hasKey(context, it) }

            if (configuredProviders.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Nenhuma API key configurada.",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Vá em Configurações e cole sua API key do Gemini, OpenAI, Claude ou OpenRouter.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Button(
                    onClick = { pickerOpen = true },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (session.analysis.isBlank()) "Analisar com IA" else "Refazer análise")
                }
            }

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "Enviando transcrição para o provedor de IA…",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        // Cancela coroutine + HTTP request em curso (BUG-017)
                        analyzeJob?.cancel()
                        AiAnalyzer.cancelInFlight()
                        loading = false
                        error = "Cancelado"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancelar") }
            }

            error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Erro: $it",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp
                    )
                }
            }

            if (session.analysis.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (session.analysisProvider.isNotBlank()) {
                            Text(
                                "Gerada via ${session.analysisProvider} · ${session.analysisModel}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            session.analysis,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (pickerOpen) {
                AlertDialog(
                    onDismissRequest = { pickerOpen = false },
                    title = { Text("Escolher provedor") },
                    text = {
                        Column {
                            configuredProviders.forEach { provider ->
                                TextButton(
                                    onClick = {
                                        pickerOpen = false
                                        loading = true
                                        error = null
                                        analyzeJob = scope.launch {
                                            val r = AiAnalyzer.analyze(context, provider, session.transcript)
                                            if (!kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]!!.isActive) return@launch
                                            loading = false
                                            when (r) {
                                                is AiAnalyzer.Result.Success -> {
                                                    app.repo.updateAnalysis(
                                                        session.id, r.text, r.provider.displayName, r.model
                                                    )
                                                }
                                                is AiAnalyzer.Result.Error -> error = r.message
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${provider.displayName} (${AiKeyStore.getModel(context, provider)})")
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { pickerOpen = false }) { Text("Cancelar") } }
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText("AulaLogger", text))
    Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
}
