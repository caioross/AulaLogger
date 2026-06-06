package com.aulalogger.ui.anim

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/** Tokens de motion alinhados com Material 3 Expressive. */
object Motion {
    val DurationShort = 200
    val DurationMedium = 300
    val DurationLong = 500

    val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0f, 1f)
    val StandardEasing = FastOutSlowInEasing
}

/**
 * Pulse contínuo sutil. Gera um valor entre [min, max] ondulando em [periodMs].
 * Usar em scale/alpha para destaque sem distrair.
 */
@Composable
fun rememberPulse(
    min: Float = 1f,
    max: Float = 1.04f,
    periodMs: Int = 1800
): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val v by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = Motion.StandardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseValue"
    )
    return v
}
