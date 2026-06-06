package com.aulalogger.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aulalogger.ai.AiAnalyzer
import com.aulalogger.ai.AiKeyStore
import com.aulalogger.ai.AiProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val configured = remember(refresh) {
        AiProvider.entries.filter { AiKeyStore.hasKey(context, it) }.toSet()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var clearTarget by remember { mutableStateOf<AiProvider?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Chaves de API", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Análise IA com chaves próprias",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Cole sua chave do provedor que preferir. Ela é guardada criptografada no seu celular e usada apenas para enviar a transcrição da aula direto ao provedor — nada passa por servidores nossos.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (configured.isEmpty()) "Nenhuma chave configurada ainda."
                        else "${configured.size} provedor(es) configurado(s).",
                        fontSize = 12.sp,
                        color = if (configured.isEmpty()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }

            AiProvider.entries.forEach { provider ->
                ProviderEditor(
                    provider = provider,
                    isConfigured = provider in configured,
                    onSaved = {
                        refresh++
                        scope.launch { snackbarHostState.showSnackbar("Chave salva ✓") }
                    },
                    onClearRequested = { clearTarget = provider },
                    onTestResult = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        clearTarget?.let { tgt ->
            AlertDialog(
                onDismissRequest = { clearTarget = null },
                title = { Text("Apagar chave?") },
                text = { Text("Remove a chave de ${tgt.displayName} deste celular. Você poderá colá-la de novo a qualquer momento.") },
                confirmButton = {
                    TextButton(onClick = {
                        AiKeyStore.clearKey(context, tgt)
                        clearTarget = null
                        refresh++
                        scope.launch { snackbarHostState.showSnackbar("Chave removida") }
                    }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { clearTarget = null }) { Text("Cancelar") }
                }
            )
        }
    }
}

@Composable
private fun ProviderEditor(
    provider: AiProvider,
    isConfigured: Boolean,
    onSaved: () -> Unit,
    onClearRequested: () -> Unit,
    onTestResult: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember(provider.id, isConfigured) { mutableStateOf(AiKeyStore.getKey(context, provider)) }
    var model by remember(provider.id, isConfigured) { mutableStateOf(AiKeyStore.getModel(context, provider)) }
    var visible by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isConfigured) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isConfigured) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    provider.displayName,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { openDocs(context, provider) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Como obter", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (visible) "Esconder" else "Mostrar"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Modelo (ex: ${provider.placeholderModel})") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        AiKeyStore.setKey(context, provider, key)
                        AiKeyStore.setModel(context, provider, model)
                        onSaved()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Salvar") }
                if (key.isNotBlank() && !testing) {
                    OutlinedButton(
                        onClick = {
                            AiKeyStore.setKey(context, provider, key)
                            AiKeyStore.setModel(context, provider, model)
                            testing = true
                            scope.launch {
                                val result = AiAnalyzer.testConnection(context, provider)
                                testing = false
                                val msg = when (result) {
                                    is AiAnalyzer.Result.Success -> "Conexão OK ✓"
                                    is AiAnalyzer.Result.Error -> "Falhou: ${result.message.take(80)}"
                                }
                                onTestResult(msg)
                            }
                        }
                    ) { Text("Testar") }
                } else if (testing) {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Text("Testando…")
                    }
                }
                if (isConfigured) {
                    OutlinedButton(onClick = onClearRequested) { Text("Limpar") }
                }
            }
        }
    }
}

private fun openDocs(context: android.content.Context, provider: AiProvider) {
    val url = when (provider) {
        AiProvider.GEMINI -> "https://aistudio.google.com/app/apikey"
        AiProvider.OPENAI -> "https://platform.openai.com/api-keys"
        AiProvider.CLAUDE -> "https://console.anthropic.com/settings/keys"
        AiProvider.OPENROUTER -> "https://openrouter.ai/keys"
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Throwable) {
    }
}
