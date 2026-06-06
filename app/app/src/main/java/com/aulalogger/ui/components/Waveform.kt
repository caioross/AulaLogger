package com.aulalogger.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * Waveform discreta: 40 barras animadas que ondulam ao redor do nível atual.
 * As barras usam um gradient vertical (cor.copy(alpha=0.3) → cor) para um
 * "glow" sutil quando o som sobe.
 */
@Composable
fun Waveform(
    level: Float,
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 40
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(durationMillis = 80),
        label = "level"
    )

    var phase by remember { mutableStateOf(0f) }
    // PERF-006: pausa animação quando Composable não está em RESUMED.
    // Em background (transcrição rolando em outra tela), economiza bateria.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        while (true) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                phase += 0.4f
            }
            delay(70)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            val w = size.width
            val h = size.height
            val barW = w / (bars * 2f)
            val center = h / 2f
            val brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.45f),
                    color,
                    color.copy(alpha = 0.45f),
                )
            )
            for (i in 0 until bars) {
                val x = (i * 2 + 1) * barW
                val mod = (sin(phase + i * 0.45f) + 1f) / 2f
                val variance = 0.25f + 0.75f * animatedLevel
                val barHeight = (h * 0.18f) + (h * 0.45f * mod * variance)
                drawLine(
                    brush = brush,
                    start = Offset(x, center - barHeight / 2f),
                    end = Offset(x, center + barHeight / 2f),
                    strokeWidth = barW * 0.9f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}
