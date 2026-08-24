package io.kontour.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * Fires one [FeedbackIntent.Tick] each time a drag crosses a detent.
 *
 * Six components had a hand-rolled version of this and they had drifted: the
 * slider guarded on a step index, the wheel picker on the item under the
 * window, the segmented control on the selected index, the tab bar fired
 * `Selection` per step instead of `Tick`, and the swipe row and the sheet fired
 * nothing at all while being dragged past their anchors. A gesture that clicks
 * on some controls and is silent on others does not read as a system with a
 * feel; it reads as some of it being finished.
 *
 * ### One per detent, not one per frame
 *
 * The guard is the whole of it. A drag sits between two notches for many frames
 * and the value it reports does not change while it does, so anything firing on
 * "the value is on a detent" fires sixty times a second. Firing on *the detent
 * index changing* is once per crossing, which is what the user's finger is
 * doing.
 *
 * That is also the rate limit. A flick across twenty-four steps does fire
 * twenty-four ticks — that is what "every detent crossed" means, and it is what
 * a physical detent would do — but it can never fire more than once per frame,
 * because an index can only change once between two reads of it.
 *
 * ```kotlin
 * val ticker = rememberDetentTicker()
 * // in the drag:
 * ticker.at(stepIndex)
 * // when the gesture ends:
 * ticker.reset()
 * ```
 */
@Stable
class DetentTicker internal constructor(private val feedback: FeedbackDispatcher) {

    private var last: Float = Float.NaN

    /**
     * Reports which detent the gesture is now on, ticking if it has changed.
     *
     * The first call after a [reset] arms the ticker without firing: a drag that
     * starts on a detent has not crossed one, and a click as the finger lands is
     * a click for something that has not happened.
     */
    fun at(index: Float) {
        if (last.isNaN()) {
            last = index
            return
        }
        if (abs(index - last) >= 1f) {
            feedback.perform(FeedbackIntent.Tick)
            last = index
        }
    }

    /** Same, for a detent identified by something other than a number. */
    fun at(index: Int) = at(index.toFloat())

    /** Ends the gesture. The next [at] arms rather than fires. */
    fun reset() {
        last = Float.NaN
    }
}

/** Remembers a [DetentTicker] wired to the current [LocalFeedback]. */
@Composable
fun rememberDetentTicker(): DetentTicker {
    val feedback = LocalFeedback.current
    return remember(feedback) { DetentTicker(feedback) }
}

/**
 * How far a drag actually travels once it is past where it is allowed to go.
 *
 * A drag pinned hard at its limit reads as the gesture having been dropped —
 * the finger keeps moving and nothing does. Letting it travel a fraction of the
 * overshoot says "this is as far as it goes" while keeping the contact.
 *
 * Two of these already existed, at two different values and in two different
 * files: `PullToRefresh.Resistance` at 0.4 and `Toast.Resistance` at 0.33. This
 * is the third, and it exists so that it is not a fourth constant.
 *
 * @param overshoot How far past the limit the finger has gone.
 * @param factor The share of it the element takes. Lower is stiffer.
 */
fun rubberBand(overshoot: Float, factor: Float = DefaultResistance): Float =
    overshoot * factor

/** Stiff enough to read as a wall, loose enough to read as contact. */
const val DefaultResistance: Float = 0.35f
