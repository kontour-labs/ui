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
import kotlin.math.abs
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
    colour: Color = Theme.colours.primary,
    trackColour: Color = Theme.colours.outline,
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
        drawRoundRect(color = trackColour, cornerRadius = radius)

        if (progress != null) {
            drawRoundRect(
                color = colour,
                size = Size(size.width * animatedProgress, size.height),
                cornerRadius = radius,
            )
        } else if (motion.reduceMotion) {
            // Static, so it still reads as "working" without the travel.
            drawRoundRect(
                color = colour,
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
                color = colour,
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
fun CircularProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 40.dp,
    colour: Color = Theme.colours.primary,
    trackColour: Color = Theme.colours.outline,
    strokeWidth: Dp = 4.dp,
) {
    // Indeterminate is the [Spinner], not a second sweep of the same arc.
    //
    // Two components drawing a rotating arc drift: one gets a new duration or a
    // rounded cap and the other does not, and an app showing both at once shows
    // two different products. `Spinner` is the one loader in the library and
    // every other indeterminate state in it — a button, `LoadMore`,
    // `PullToRefresh` — already goes through it.
    if (progress == null) {
        Spinner(
            modifier = modifier,
            size = size,
            colour = colour,
            strokeWidth = strokeWidth,
            contentDescription = contentDescription,
        )
        return
    }

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
            color = trackColour,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = colour,
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
    current: Int?,
    total: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    colour: Color = Theme.colours.primary,
    trackColour: Color = Theme.colours.outline,
    height: Dp = 4.dp,
    gap: Dp = 4.dp,
) {
    if (total <= 0) return
    val motion = Theme.motion
    val indeterminate = current == null

    val animated by animateFloatAsState(
        targetValue = (current ?: 0).coerceIn(0, total).toFloat(),
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "stepProgress",
    )

    // Indeterminate walks one lit segment along the track.
    //
    // "How many steps there are, but not which one you are on" is a real state —
    // a wizard restoring a session, a checkout waiting on a server that has not
    // said where it got to — and the alternative was to pass `0`, which claims
    // you are at the start rather than that nobody knows.
    //
    // Under reduced motion it stops on the first segment: the travel is what
    // says "working", and a looping animation is exactly what that setting is
    // asking to be spared.
    val sweep by rememberInfiniteTransition(label = "stepSweep").animateFloat(
        initialValue = 0f,
        targetValue = total.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 320 * total, easing = LinearEasing),
        ),
        label = "stepSweepValue",
    )
    val lit = if (motion.reduceMotion) 0f else sweep

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                this.contentDescription = contentDescription
                    ?: if (indeterminate) "In progress" else "Step $current of $total"
                // `Indeterminate` rather than a made-up position: a screen reader
                // saying "step 0 of 4" would be stating something the caller
                // explicitly said it does not know.
                progressBarRangeInfo = if (indeterminate) {
                    ProgressBarRangeInfo.Indeterminate
                } else {
                    ProgressBarRangeInfo(
                        current = animated,
                        range = 0f..total.toFloat(),
                        steps = total - 1,
                    )
                }
            }
    ) {
        val gapPx = gap.toPx()
        val segmentWidth = (size.width - gapPx * (total - 1)) / total
        val radius = CornerRadius(size.height / 2f)

        for (index in 0 until total) {
            val left = index * (segmentWidth + gapPx)
            drawRoundRect(
                color = trackColour,
                topLeft = Offset(left, 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = radius,
            )
            // Partial fill on the segment currently in progress, so a step that
            // is halfway does not read as not started. Indeterminate lights one
            // segment at a time instead, walking it along.
            val fill = if (indeterminate) {
                (1f - abs(lit - index - 0.5f)).coerceIn(0f, 1f)
            } else {
                (animated - index).coerceIn(0f, 1f)
            }
            if (fill > 0f) {
                drawRoundRect(
                    color = colour,
                    topLeft = Offset(left, 0f),
                    size = Size(segmentWidth * fill, size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}
