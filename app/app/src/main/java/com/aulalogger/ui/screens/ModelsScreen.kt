package com.aulalogger.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aulalogger.state.AppStatusHolder
import com.aulalogger.transcription.ModelManager
import com.aulalogger.transcription.ModelProfile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(ModelManager.getSelectedProfile(context)) }
    var installed by remember { mutableStateOf(ModelManager.installedProfiles(context)) }
    val status by AppStatusHolder.status.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modelos de IA", fontWeight = FontWeight.SemiBold) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Hero card
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Qualidade × velocidade × espaço",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Modelos maiores transcrevem melhor — especialmente nomes técnicos e sotaques fortes — mas levam mais tempo.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            ModelProfile.entries.forEach { profile ->
                val isSelected = profile == selected
                val isInstalled = profile in installed
                val isDownloading = status.modelDownloading && selected == profile

                ModelCard(
                    profile = profile,
                    selected = isSelected,
                    installed = isInstalled,
                    downloading = isDownloading,
                    downloadPct = status.modelDownloadPct,
                    onSelect = {
                        ModelManager.setSelectedProfile(context, profile)
                        selected = profile
                        AppStatusHolder.refresh(context)
                    },
                    onDownload = {
                        scope.launch {
                            ModelManager.setSelectedProfile(context, profile)
                            selected = profile
                            val ok = ModelManager.downloadModel(context, profile) {
                                AppStatusHolder.setDownloadingProgress(it)
                            }
                            if (ok) installed = ModelManager.installedProfiles(context)
                            AppStatusHolder.refresh(context)
                        }
                    },
                    onDelete = {
                        ModelManager.deleteModel(context, profile)
                        installed = ModelManager.installedProfiles(context)
                        AppStatusHolder.refresh(context)
                    }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModelCard(
    profile: ModelProfile,
    selected: Boolean,
    installed: Boolean,
    downloading: Boolean,
    downloadPct: Int,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else Color.Transparent
    val borderWidth by animateFloatAsState(
        targetValue = if (selected) 2f else 0f,
        label = "border"
    )
    val (icon, badgeColor) = when (profile) {
        ModelProfile.MINI -> Icons.Filled.Bolt to MaterialTheme.colorScheme.tertiaryContainer
        ModelProfile.MEDIUM -> Icons.Filled.Speed to MaterialTheme.colorScheme.primaryContainer
        ModelProfile.HIGH -> Icons.Filled.AutoAwesome to MaterialTheme.colorScheme.secondaryContainer
    }

    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        border = if (selected) androidx.compose.foundation.BorderStroke(borderWidth.dp, borderColor) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "EM USO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        profile.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (installed) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Instalado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (downloading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { downloadPct / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Baixando · $downloadPct%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (installed) {
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Apagar (${ModelManager.installedSizeMb(LocalContext.current, profile)} MB)", fontSize = 12.sp)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onDownload,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Baixar (~${profile.approxBytes / 1024 / 1024} MB)", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
