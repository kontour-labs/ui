package io.kontour.ui.components.display

import androidx.compose.animation.core.LinearEasing
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
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.theme.Theme
import kotlin.math.PI
import kotlin.math.cos

/**
 * An indeterminate activity indicator.
 *
 * The arc sweeps *and* breathes — its length grows and shrinks as it rotates,
 * so the tail chases the head rather than a rigid segment going round.
 *
 * ### The head never goes backwards
 *
 * This is the whole of it, and the previous version had it the wrong way round.
 * `drawArc` sweeps *clockwise from* `startAngle`, so pinning `startAngle` to the
 * rotation pins the **tail** and leaves the head at `rotation + sweep` — which
 * retreats every time the sweep shrinks. That is what read as the spinner
 * collapsing into itself and popping back: not a fade, an arc running backwards
 * for half of every cycle.
 *
 * Anchoring the head instead — `startAngle = rotation - sweep` — leaves the
 * leading edge advancing at a constant rate forever, and the tail lagging and
 * catching up behind it, which is the motion the shape is supposed to suggest.
 *
 * The breathe is a real cosine of a linear driver rather than an eased tween,
 * because an easing curve's rate is not bounded by anything in particular and
 * the tail's velocity is `rotation' − sweep'`. A cosine's peak rate is
 * `amplitude × π / period`, which is a number you can hold against the rotation
 * rate and pick constants that keep the tail moving forwards too.
 *
 * The two periods are deliberately not multiples of each other, so the arc does
 * not return to the same length at the same angle every cycle and the whole
 * thing never reads as a loop.
 *
 * Under reduced motion the breathing stops and the arc holds a constant length,
 * rotating steadily — still clearly "working", without the pulsing.
 *
 * ```
 * Spinner()                                   // inherits content colour
 * Spinner(size = 32.dp, colour = Theme.colours.accent.solid)
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
    colour: Color = LocalContentColour.current,
    strokeWidth: Dp = (size.value / 9f).dp.coerceAtLeast(1.5.dp),
    contentDescription: String? = null,
) {
    val reduceMotion = Theme.motion.reduceMotion
    val transition = rememberInfiniteTransition(label = "spinner")

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SpinnerDefaults.RotationMillis, easing = LinearEasing),
        ),
        label = "spinnerRotation",
    )

    // A linear driver, turned into a cosine below. `RepeatMode.Reverse` on an
    // eased tween would do something similar and would corner at each end,
    // because the easing's rate is not zero where the direction changes.
    val phase by transition.animateFloat(
        // Starts a quarter in, so the arc appears at half length rather than at
        // its shortest. A spinner that begins as a stub and grows reads as
        // popping in, and it is also what a screenshot catches on frame six.
        initialValue = SpinnerDefaults.OpeningPhase,
        targetValue = SpinnerDefaults.OpeningPhase + 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SpinnerDefaults.BreatheMillis, easing = LinearEasing),
        ),
        label = "spinnerBreathe",
    )

    val effectiveSweep =
        if (reduceMotion) SpinnerDefaults.RestingSweep else spinnerSweep(phase)

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
            color = colour,
            // The head is at `rotation`; the tail trails it.
            startAngle = rotation - effectiveSweep,
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

/**
 * The arc's length at a point in its breathe cycle, for [phase] in `0..1`.
 *
 * Pulled out of the composable so the two things that actually matter can be
 * asserted: that it never reaches zero, and that the tail — `rotation − sweep` —
 * never runs backwards. Neither is visible in a screenshot, because a golden is
 * one frame of something whose whole problem was what it did across frames.
 */
internal fun spinnerSweep(phase: Float): Float {
    val breathe = (1f - cos(phase * 2f * PI.toFloat())) / 2f
    return SpinnerDefaults.MinSweep +
        (SpinnerDefaults.MaxSweep - SpinnerDefaults.MinSweep) * breathe
}

object SpinnerDefaults {
    /** One turn of the head. */
    const val RotationMillis: Int = 1000

    /** One grow-and-shrink of the tail. Coprime-ish with [RotationMillis]. */
    const val BreatheMillis: Int = 1400

    /**
     * Never a bare dot, never a closed ring.
     *
     * The amplitude is what keeps the tail moving forwards: a cosine's peak rate
     * is `amplitude × π / period`, so 150° over 1400ms peaks at about 337°/s
     * against the head's 360°/s.
     */
    const val MinSweep: Float = 40f
    const val MaxSweep: Float = 190f

    /** The length it holds when the user has asked for less motion. */
    const val RestingSweep: Float = 90f

    /**
     * Where in the breathe cycle a spinner starts.
     *
     * A quarter in, so the arc appears at half its length rather than at its
     * shortest: one that begins as a stub and grows reads as popping in.
     *
     * Named because a second thing depends on it. `PullToRefresh` grows an arc
     * with the finger and swaps this in when the pull commits, and the swap is
     * only invisible if the two are the same length at the moment it happens —
     * so the pull stops at [OpeningSweep] rather than closing the circle, and
     * neither number can drift from the other.
     */
    const val OpeningPhase: Float = 0.25f

    /** The arc's length, in degrees, at the instant a spinner appears. */
    val OpeningSweep: Float get() = spinnerSweep(OpeningPhase)
}
