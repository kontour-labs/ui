package io.kontour.ui.motion

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme

/** Timing for [Modifier.marquee]. */
object MarqueeDefaults {

    /**
     * How long it waits before setting off, and between passes.
     *
     * Long enough to read the beginning standing still. A label that starts
     * moving the moment it appears is one the eye never gets a fixed look at,
     * and the first word is usually the one that identifies the thing —
     * "Elizabeth Quay Bus Station" is answered by the first two words.
     */
    const val PauseMillis: Int = 1600

    /** Reading pace, not conveyor-belt pace. */
    val Velocity: Dp = 40.dp

    /** The gap between the end of one pass and the start of the next. */
    val Gap: Dp = 48.dp
}

/**
 * Scrolls this content sideways when it is too wide, and does nothing when it fits.
 *
 * ```
 * Text(
 *     text = stop.name,
 *     maxLines = 1,
 *     modifier = Modifier.marquee(),
 * )
 * ```
 *
 * For the label that is *usually* short and occasionally is not — a stop name, a
 * route headsign, a now-playing line. Safe to apply unconditionally: it measures
 * first and animates only when the content is wider than the space, so the
 * common case costs a comparison.
 *
 * Pair it with `maxLines = 1`. Without that the text wraps instead of
 * overflowing, there is nothing to scroll, and the modifier sits there doing
 * nothing forever.
 *
 * ### It is off under reduced motion, and that is not a degradation
 *
 * Everything else in this library softens under `reduceMotion` — a spring
 * becomes a tween, a slide becomes a fade. This one stops entirely, because it
 * is the one animation in the library that never ends. Perpetual motion in the
 * corner of the eye is the specific thing that preference exists to stop, and
 * there is no gentler version of "forever". The text truncates instead, which is
 * what it would have done without this modifier at all.
 *
 * ### Why it wraps foundation's rather than replacing it
 *
 * `basicMarquee` already measures, already stops when the content fits, already
 * handles right-to-left and already does the right thing when the node leaves
 * the composition. What it does not have is this library's opinion about pace,
 * or any knowledge of `reduceMotion`. Those are the two things worth owning, and
 * they are both a handful of lines — reimplementing the rest would be a second
 * copy of a solved problem, to be kept in step with the first.
 *
 * @param iterations How many passes before it settles. `Int.MAX_VALUE` for a
 *   ticker that never stops — appropriate for a live status line, and not for a
 *   list of labels, where a dozen rows all scrolling at once is a screen nobody
 *   can read.
 */
@Composable
fun Modifier.marquee(
    iterations: Int = 3,
    pauseMillis: Int = MarqueeDefaults.PauseMillis,
    velocity: Dp = MarqueeDefaults.Velocity,
    gap: Dp = MarqueeDefaults.Gap,
): Modifier = if (Theme.motion.reduceMotion) {
    this
} else {
    this.basicMarquee(
        iterations = iterations,
        repeatDelayMillis = pauseMillis,
        initialDelayMillis = pauseMillis,
        spacing = MarqueeSpacing(gap),
        velocity = velocity,
    )
}
