package com.aulalogger.ui.components

import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Player de áudio. Abre o arquivo via ParcelFileDescriptor e prepara em
 * Dispatchers.IO (não bloqueia a UI). O PFD vive enquanto o composable existir
 * e é liberado em onDispose junto com o MediaPlayer.
 *
 * Bugs históricos corrigidos:
 *  - FileInputStream.use{} fechava o fd antes do prepare → IllegalStateException
 *    intermitente
 *  - prepare() rodava no Main thread → ANR em arquivos grandes
 */
@Composable
fun AudioPlayer(filePath: String, modifier: Modifier = Modifier) {
    val tag = "AudioPlayer"
    var pfd by remember(filePath) { mutableStateOf<ParcelFileDescriptor?>(null) }
    var player by remember(filePath) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(filePath) { mutableStateOf(false) }
    var prepareError by remember(filePath) { mutableStateOf<String?>(null) }
    var isPlaying by remember(filePath) { mutableStateOf(false) }
    var positionMs by remember(filePath) { mutableIntStateOf(0) }
    var durationMs by remember(filePath) { mutableIntStateOf(0) }

    LaunchedEffect(filePath) {
        val file = File(filePath)
        if (!file.exists()) {
            prepareError = "Arquivo não existe: ${file.name}"
            Log.e(tag, "Arquivo não existe: $filePath")
            return@LaunchedEffect
        }
        if (file.length() < 100) {
            prepareError = "Arquivo de áudio vazio (${file.length()} bytes)"
            return@LaunchedEffect
        }

        data class Prepared(val pfd: ParcelFileDescriptor, val mp: MediaPlayer, val durationMs: Int)

        val outcome: kotlin.Result<Prepared> = withContext(Dispatchers.IO) {
            runCatching {
                val openedPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val mp = MediaPlayer()
                try {
                    mp.setDataSource(openedPfd.fileDescriptor)
                    mp.prepare()
                    Prepared(openedPfd, mp, mp.duration.coerceAtLeast(0))
                } catch (t: Throwable) {
                    try { mp.release() } catch (_: Throwable) {}
                    try { openedPfd.close() } catch (_: Throwable) {}
                    throw t
                }
            }
        }

        outcome.onSuccess { p ->
            p.mp.setOnCompletionListener {
                isPlaying = false
                positionMs = 0
            }
            p.mp.setOnErrorListener { _, what, extra ->
                prepareError = "Erro do player ($what/$extra)"
                isPlaying = false
                true
            }
            pfd = p.pfd
            player = p.mp
            durationMs = p.durationMs
            prepared = true
        }.onFailure { t ->
            Log.e(tag, "Falha ao preparar player", t)
            prepareError = "Falha ao abrir áudio: ${t.message ?: "desconhecido"}"
        }
    }

    DisposableEffect(filePath) {
        onDispose {
            try { player?.stop() } catch (_: Throwable) {}
            try { player?.release() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying && player != null) {
            try { positionMs = player?.currentPosition ?: 0 } catch (_: Throwable) {}
            delay(200)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (prepareError != null) {
                Text(
                    prepareError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp).weight(1f)
                )
                return@Row
            }
            FilledIconButton(
                onClick = {
                    val p = player ?: return@FilledIconButton
                    if (!prepared) return@FilledIconButton
                    try {
                        if (isPlaying) {
                            p.pause(); isPlaying = false
                        } else {
                            p.start(); isPlaying = true
                        }
                    } catch (t: Throwable) {
                        Log.e(tag, "play/pause falhou", t)
                        prepareError = "Erro ao reproduzir: ${t.message}"
                    }
                },
                enabled = prepared
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir"
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = positionMs.toFloat(),
                    onValueChange = {
                        if (!prepared) return@Slider
                        positionMs = it.toInt()
                        try { player?.seekTo(it.toInt()) } catch (_: Throwable) {}
                    },
                    valueRange = 0f..(durationMs.coerceAtLeast(1)).toFloat(),
                    enabled = prepared
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatMs(positionMs),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (prepared) formatMs(durationMs) else "carregando…",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val sec = ms / 1000
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
