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
import androidx.compose.ui.graphics.drawscope.clipRect
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
            animation = tween(durationMillis = BandTravel, easing = LinearEasing),
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
            val bandWidth = size.width * BandFraction
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
 *
 * @param current Which step is in progress, counting from one, or `null` for
 *   "somewhere in this sequence, not yet known" — a wizard restoring a session,
 *   a checkout waiting on a server that has not said where it got to. `null`
 *   walks one lit segment along the row rather than claiming a position; it is
 *   not the same as `1`, which says the first step is under way.
 * @param working Whether [current] is *itself* still going. The step's position
 *   is known and its progress within that step is not, so the segment carries a
 *   travelling band instead of a fill — the same band [LinearProgress] runs
 *   indeterminate, at the width of one segment. Ignored when [current] is
 *   `null`, which is already animating.
 */
@Composable
fun StepProgress(
    current: Int?,
    total: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    working: Boolean = false,
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

    // Two different unknowns, and they animate differently.
    //
    // `current == null` is "how many steps there are, but not which one you are
    // on" — a wizard restoring a session, a checkout waiting on a server that
    // has not said where it got to. The alternative was to pass `0`, which
    // claims you are at the start rather than that nobody knows.
    //
    // `working` is the other one: the step *is* known and it is the step that is
    // busy. That wants a band travelling inside one segment, the way
    // [LinearProgress] indeterminate does, and it shares that arithmetic below
    // rather than restating it.
    //
    // The walk used to light several segments at once through a triangular
    // falloff, which read as a glow crossing the row rather than as one step
    // working. It lights one at a time now.
    //
    // Under reduced motion both stop: the travel is what says "working", and a
    // looping animation is exactly what that setting is asking to be spared.
    val animating = (indeterminate || working) && !motion.reduceMotion
    val sweep by rememberInfiniteTransition(label = "stepSweep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (indeterminate) StepWalkPerSegment * total else BandTravel,
                easing = LinearEasing,
            ),
        ),
        label = "stepSweepValue",
    )
    val phase = if (animating) sweep else 0f

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                this.contentDescription = contentDescription ?: when {
                    indeterminate -> "In progress"
                    working -> "Step $current of $total, in progress"
                    else -> "Step $current of $total"
                }
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
            // The step that is working shows a travelling band instead of a
            // fill: a segment that is both solid and animated says two things.
            val busy = working && !indeterminate && index == current.coerceIn(1, total) - 1

            // Partial fill on the segment currently in progress, so a step that
            // is halfway does not read as not started.
            val fill = when {
                // One segment at a time, walking. `phase` runs 0..1 across the
                // whole row, so scaling by `total` gives the segment it is on.
                indeterminate ->
                    if ((phase * total).toInt().coerceAtMost(total - 1) == index) 1f else 0f
                // Static under reduced motion, so a busy step still reads as
                // working without the travel — the same answer `LinearProgress`
                // gives, and for the same reason. Without it the segment would
                // be neither filled nor animated, which is to say invisible.
                busy -> if (animating) 0f else BandFraction
                else -> (animated - index).coerceIn(0f, 1f)
            }
            if (fill > 0f) {
                drawRoundRect(
                    color = colour,
                    topLeft = Offset(left, 0f),
                    size = Size(segmentWidth * fill, size.height),
                    cornerRadius = radius,
                )
            }

            // The busy step gets a band travelling inside it, on top of whatever
            // it is filled to. Same construction as `LinearProgress` — a band
            // 35% as wide as its container, entering one end as it leaves the
            // other — confined to this segment instead of the whole track.
            if (busy && animating) {
                val bandWidth = segmentWidth * BandFraction
                val bandLeft = left - bandWidth + (segmentWidth + bandWidth) * phase
                clipRect(
                    left = left,
                    right = left + segmentWidth,
                    top = 0f,
                    bottom = size.height,
                ) {
                    drawRoundRect(
                        color = colour,
                        topLeft = Offset(bandLeft, 0f),
                        size = Size(bandWidth, size.height),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}

/**
 * How wide the travelling indeterminate band is, as a fraction of what it runs
 * inside.
 *
 * Shared by [LinearProgress], which runs it across the whole track, and by
 * [StepProgress]'s `working`, which confines it to one segment. One number
 * because they are meant to read as the same animation at two scales.
 */
private const val BandFraction = 0.35f

/** How long that band takes to cross, whatever it is crossing. */
private const val BandTravel = 1400

/** How long the indeterminate walk rests on each step. */
private const val StepWalkPerSegment = 320
