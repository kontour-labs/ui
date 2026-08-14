package io.kontour.ui.components.display

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme

/**
 * A horizontal progress bar.
 *
 * Pass a [progress] for determinate progress; pass `null` when the duration is
 * unknown and it becomes an indeterminate sweep.
 *
 * ```
 * LinearProgress(progress = uploaded / total, contentDescription = "Uploading photo")
 * LinearProgress(progress = null, contentDescription = "Loading departures")
 * ```
 *
 * Prefer determinate whenever you can compute it. An indeterminate bar tells the
 * user nothing except "still working", and after a few seconds that reads as
 * "stuck".
 *
 * Under reduced motion the indeterminate sweep stops and the bar shows a static
 * partial fill — still visibly "in progress", without the looping travel.
 */
@Composable
fun LinearProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    color: Color = Theme.colors.primary,
    trackColor: Color = Theme.colors.outline,
    height: Dp = 6.dp,
) {
    val motion = Theme.motion
    val transition = rememberInfiniteTransition(label = "linearProgress")

    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
        ),
        label = "linearSweep",
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = motion.tweenDefault(),
        label = "linearProgressValue",
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = if (progress != null) {
                    ProgressBarRangeInfo(current = progress.coerceIn(0f, 1f), range = 0f..1f)
                } else {
                    ProgressBarRangeInfo.Indeterminate
                }
            }
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)

        if (progress != null) {
            drawRoundRect(
                color = color,
                size = Size(size.width * animatedProgress, size.height),
                cornerRadius = radius,
            )
        } else if (motion.reduceMotion) {
            // Static, so it still reads as "working" without the travel.
            drawRoundRect(
                color = color,
                size = Size(size.width * 0.35f, size.height),
                cornerRadius = radius,
            )
        } else {
            // A fixed-width band travelling the full track, entering and leaving
            // off the ends so it never appears to bounce.
            val bandWidth = size.width * 0.35f
            val travel = size.width + bandWidth
            val left = -bandWidth + travel * sweep
            drawRoundRect(
                color = color,
                topLeft = Offset(left.coerceAtLeast(0f), 0f),
                size = Size(
                    width = (bandWidth + minOf(left, 0f))
                        .coerceAtMost(size.width - left.coerceAtLeast(0f))
                        .coerceAtLeast(0f),
                    height = size.height,
                ),
                cornerRadius = radius,
            )
        }
    }
}

/**
 * A circular progress ring.
 *
 * For progress that sits inside something round — a trip's completion around an
 * avatar, a download around an icon. For plain "we are working on it", use
 * [Spinner], which is lighter and reads as activity rather than as a measurable
 * quantity.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 40.dp,
    color: Color = Theme.colors.primary,
    trackColor: Color = Theme.colors.outline,
    strokeWidth: Dp = 4.dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = Theme.motion.tweenDefault(),
        label = "progressRing",
    )

    Canvas(
        modifier
            .size(size)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(current = clamped, range = 0f..1f)
            }
    ) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            // From twelve o'clock, like every other progress ring a user has seen.
            startAngle = -90f,
            sweepAngle = 360f * animated,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * Progress through a known number of discrete steps.
 *
 * ```
 * StepProgress(current = 2, total = 4, contentDescription = "Step 2 of 4")
 * ```
 *
 * Segments rather than a continuous bar, because the user can count them and
 * know how much is left — which a percentage does not tell them as directly.
 */
@Composable
fun StepProgress(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    color: Color = Theme.colors.primary,
    trackColor: Color = Theme.colors.outline,
    height: Dp = 4.dp,
    gap: Dp = 4.dp,
) {
    if (total <= 0) return
    val motion = Theme.motion

    val animated by animateFloatAsState(
        targetValue = current.coerceIn(0, total).toFloat(),
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "stepProgress",
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                this.contentDescription = contentDescription ?: "Step $current of $total"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = current.toFloat(),
                    range = 0f..total.toFloat(),
                    steps = total - 1,
                )
            }
    ) {
        val gapPx = gap.toPx()
        val segmentWidth = (size.width - gapPx * (total - 1)) / total
        val radius = CornerRadius(size.height / 2f)

        for (index in 0 until total) {
            val left = index * (segmentWidth + gapPx)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = radius,
            )
            // Partial fill on the segment currently in progress, so a step that
            // is halfway does not read as not started.
            val fill = (animated - index).coerceIn(0f, 1f)
            if (fill > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, 0f),
                    size = Size(segmentWidth * fill, size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}
