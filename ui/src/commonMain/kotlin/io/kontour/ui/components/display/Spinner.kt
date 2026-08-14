package io.kontour.ui.components.display

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.LocalContentColor
import io.kontour.ui.theme.Theme

/**
 * An indeterminate activity indicator.
 *
 * The arc sweeps *and* breathes — its length grows and shrinks as it rotates,
 * so the tail chases the head rather than a rigid segment going round. It costs
 * one extra animated value and it is the difference between an indicator that
 * looks alive and one that looks like a loading GIF from 2009.
 *
 * Under reduced motion the breathing stops and the arc holds a constant length,
 * rotating steadily — still clearly "working", without the pulsing.
 *
 * ```
 * Spinner()                                   // inherits content colour
 * Spinner(size = 32.dp, color = Theme.colors.accent)
 * ```
 *
 * @param contentDescription Announced by a screen reader. Pass `null` when the
 *   spinner sits inside something that already announces itself as busy — a
 *   loading [io.kontour.ui.components.action.Button] does, so its spinner is
 *   silent.
 */
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    size: Dp = Theme.sizing.iconMedium,
    color: Color = LocalContentColor.current,
    strokeWidth: Dp = (size.value / 9f).dp.coerceAtLeast(1.5.dp),
    contentDescription: String? = null,
) {
    val reduceMotion = Theme.motion.reduceMotion
    val transition = rememberInfiniteTransition(label = "spinner")

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
        ),
        label = "spinnerRotation",
    )

    // The sweep breathes between a stub and three-quarters of the circle. Held
    // constant when the user has asked for less motion.
    val sweep by transition.animateFloat(
        initialValue = 30f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = Theme.motion.standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spinnerSweep",
    )

    val effectiveSweep = if (reduceMotion) 90f else sweep

    Canvas(
        modifier
            .size(size)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }
            }
    ) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = effectiveSweep,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                width = this.size.width - stroke,
                height = this.size.height - stroke,
            ),
            style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}
