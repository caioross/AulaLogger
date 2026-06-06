package com.aulalogger.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aulalogger.ai.AiKeyStore
import com.aulalogger.ai.AiProvider
import com.aulalogger.state.AppStatusHolder
import com.aulalogger.transcription.ModelManager
import com.aulalogger.transcription.TranscriptionPrefs
import com.aulalogger.ui.theme.ThemeMode
import com.aulalogger.ui.theme.ThemePrefs
import com.aulalogger.util.AppKiller
import com.aulalogger.util.OemHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenApiKeys: () -> Unit,
    onOpenAbout: () -> Unit,
    requestMicPermission: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var killDialog by remember { mutableStateOf(false) }
    val status by AppStatusHolder.status.collectAsState()
    val themeMode by ThemePrefs.mode.collectAsState()

    LaunchedEffect(Unit) { AppStatusHolder.refresh(context) }

    var timestampsOn by remember { mutableStateOf(TranscriptionPrefs.timestampsEnabled(context)) }
    var speakersOn by remember { mutableStateOf(TranscriptionPrefs.speakersEnabled(context)) }
    var autoTranscribeOn by remember { mutableStateOf(TranscriptionPrefs.autoTranscribeEnabled(context)) }

    var configuredKeys by remember { mutableIntStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                configuredKeys = AiProvider.entries.count { AiKeyStore.hasKey(context, it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ─── Hero de onboarding (só se status não estiver ready) ────────────
            if (!status.ready) {
                OnboardingHero(
                    micGranted = status.micGranted,
                    notificationGranted = status.notificationGranted,
                    modelInstalled = status.modelInstalled
                )
            }

            // ─── Pré-requisitos ─ um card por item, ALINHADOS ───────────────────
            SectionLabel("PRÉ-REQUISITOS")

            RequirementCard(
                icon = Icons.Filled.Mic,
                title = "Microfone",
                subtitle = if (status.micGranted) "Permissão concedida"
                else "Precisamos para gravar a aula",
                ok = status.micGranted,
                action = if (!status.micGranted) "Conceder" else null,
                onAction = { requestMicPermission() }
            )

            RequirementCard(
                icon = Icons.Filled.Notifications,
                title = "Notificações",
                subtitle = if (status.notificationGranted) "Notificações ativas"
                else "Mostra o progresso quando o app está em segundo plano",
                ok = status.notificationGranted
            )

            val profile = ModelManager.getSelectedProfile(context)
            val modelSubtitle = when {
                status.modelInstalled -> "${profile.displayName} · ${ModelManager.installedSizeMb(context, profile)} MB instalados"
                status.modelDownloading -> "Baixando ${profile.displayName}…"
                else -> "${profile.displayName} · não baixado (~${profile.approxBytes / 1024 / 1024} MB)"
            }
            RequirementCard(
                icon = Icons.Filled.Psychology,
                title = "Modelo de transcrição",
                subtitle = modelSubtitle,
                ok = status.modelInstalled,
                progress = if (status.modelDownloading) status.modelDownloadPct else null,
                action = when {
                    status.modelInstalled -> null
                    status.modelDownloading -> null
                    else -> "Baixar"
                },
                actionIcon = Icons.Filled.Download,
                onAction = {
                    scope.launch {
                        ModelManager.downloadModel(context, profile) {
                            AppStatusHolder.setDownloadingProgress(it)
                        }
                        AppStatusHolder.refresh(context)
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            // ─── Aparência ──────────────────────────────────────────────────────
            SectionLabel("APARÊNCIA")
            SectionCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tema", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "Escolha o visual que prefere — ou siga o do celular.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    ThemePicker(
                        current = themeMode,
                        onSelect = { ThemePrefs.set(context, it) }
                    )
                }
            }

            // ─── Transcrição ────────────────────────────────────────────────────
            SectionLabel("TRANSCRIÇÃO")
            SectionCard(onClick = onOpenModels) {
                NavRow(
                    icon = Icons.Filled.Psychology,
                    title = "Qualidade do modelo",
                    subtitle = "Mínimo · Médio · Alto · gerenciar download"
                )
            }
            SectionCard {
                Column {
                    SwitchLine(
                        title = "Transcrever automaticamente",
                        description = "Ao terminar uma gravação, inicia a transcrição sozinha. Desligue para transcrever quando quiser (ex.: várias aulas de uma vez à noite).",
                        checked = autoTranscribeOn,
                        onCheckedChange = {
                            autoTranscribeOn = it
                            TranscriptionPrefs.setAutoTranscribeEnabled(context, it)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchLine(
                        title = "Mostrar timestamps",
                        description = "Insere [00:00] no texto para localizar trechos",
                        checked = timestampsOn,
                        onCheckedChange = {
                            timestampsOn = it
                            TranscriptionPrefs.setTimestampsEnabled(context, it)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchLine(
                        title = "Diferenciar locutores",
                        description = "Tenta separar Locutor A / B por pausas (heurística)",
                        checked = speakersOn,
                        onCheckedChange = {
                            speakersOn = it
                            TranscriptionPrefs.setSpeakersEnabled(context, it)
                        }
                    )
                }
            }

            // ─── Tutorial OEM agressivo (Xiaomi, Vivo, etc) ─────────────────────
            if (OemHelper.isAggressive()) {
                SectionLabel("BACKGROUND CONFIÁVEL")
                val oem = OemHelper.detect()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Detectamos ${oem.displayName}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            "Este fabricante mata apps em background mesmo com permissão de bateria. " +
                                "Para gravar aulas longas com a tela apagada, libere o auto-start abaixo.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            oem.manualPath,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = {
                                val opened = OemHelper.openAutoStartSettings(context)
                                if (!opened) {
                                    // fallback: instruções manuais já visíveis no card
                                }
                            }
                        ) { Text("Abrir Configurações do fabricante") }
                    }
                }
            }

            // ─── Análise IA ─────────────────────────────────────────────────────
            SectionLabel("ANÁLISE COM IA")
            SectionCard(onClick = onOpenApiKeys) {
                NavRow(
                    icon = Icons.Filled.Key,
                    title = "Chaves de API",
                    subtitle = if (configuredKeys == 0) "Nenhuma configurada — opcional"
                    else "$configuredKeys provedor(es) configurado(s)"
                )
            }

            Spacer(Modifier.height(8.dp))

            // ─── Sobre ──────────────────────────────────────────────────────────
            SectionLabel("INFO")
            SectionCard(onClick = onOpenAbout) {
                NavRow(
                    icon = Icons.Filled.Info,
                    title = "Sobre o AulaLogger",
                    subtitle = "Versão, créditos e política de privacidade"
                )
            }

            Spacer(Modifier.height(16.dp))

            // ─── Encerrar ───────────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                onClick = { killDialog = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Encerrar AulaLogger",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Para gravação, cancela transcrição e fecha o app",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        if (killDialog) {
            AlertDialog(
                onDismissRequest = { killDialog = false },
                title = { Text("Encerrar AulaLogger?") },
                text = { Text("Isso vai parar gravação ativa, cancelar transcrição em curso e fechar o app.\n\nGravações já salvas continuam disponíveis quando você abrir de novo.") },
                confirmButton = {
                    TextButton(onClick = {
                        killDialog = false
                        AppKiller.killEverything(context, activity)
                    }) { Text("Encerrar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { killDialog = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

/**
 * Hero de onboarding: aparece no topo se ainda faltam pré-requisitos.
 * Card largo com gradient + checklist de progresso.
 */
@Composable
private fun OnboardingHero(
    micGranted: Boolean,
    notificationGranted: Boolean,
    modelInstalled: Boolean
) {
    val total = 3
    val done = listOf(micGranted, notificationGranted, modelInstalled).count { it }
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
    )
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(gradient)
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.RocketLaunch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Vamos preparar tudo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Faltam $done de $total passos para você começar a gravar.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.92f)
                )
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { done / total.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
            }
        }
    }
}

/**
 * Card de pré-requisito: leading status icon (40dp), 2 linhas de texto, trailing
 * action ou check. Inline progress bar quando baixando algo.
 *
 * Layout em uma única coluna garantida → SEM desalinhamento entre rows.
 */
@Composable
private fun RequirementCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    ok: Boolean,
    progress: Int? = null,
    action: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar de status (40dp colorido)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (ok) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (ok) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.width(8.dp))
                when {
                    ok -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Pronto",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    action != null && onAction != null -> FilledTonalButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        if (actionIcon != null) {
                            Icon(actionIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "$progress%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
    )
}

@Composable
private fun SectionCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val mod = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    if (onClick != null) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            onClick = onClick,
            modifier = mod
        ) { content() }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = mod
        ) { content() }
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemePicker(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, Icons.Filled.PhoneAndroid, "Sistema"),
        Triple(ThemeMode.LIGHT, Icons.Filled.LightMode, "Claro"),
        Triple(ThemeMode.DARK, Icons.Filled.DarkMode, "Escuro"),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (mode, icon, label) ->
            val selected = mode == current
            Surface(
                onClick = { onSelect(mode) },
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchLine(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

