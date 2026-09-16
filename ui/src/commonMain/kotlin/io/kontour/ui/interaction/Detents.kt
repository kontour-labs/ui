package io.kontour.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
 * ### One per detent is not a rate limit
 *
 * That used to be claimed here: a flick across twenty-four steps fires
 * twenty-four ticks, "which is what a physical detent would do", bounded only by
 * an index changing at most once per frame. Once per frame is sixty a second.
 *
 * A physical detent is bounded by something this is not: the wheel has mass, and
 * the notches go past at a speed a finger set. A vibration motor has no such
 * limit — the platform simply restarts it — so twenty-four ticks in a third of a
 * second is not twenty-four detents, it is a continuous buzz. That is the
 * infinite wheel picker's report ("less punchy"), and it is the same component
 * that crosses the most detents per gesture.
 *
 * So there is a floor between ticks, [MinimumTickInterval]. Crossings inside it
 * are dropped rather than queued: a tick reports *where the finger is now*, and
 * a backlog of them arriving after the fact reports where it was.
 *
 * ```kotlin
 * val ticker = rememberDetentTicker()
 * // in the drag:
 * ticker.at(stepIndex)
 * // when the gesture ends:
 * ticker.reset()
 * ```
 *
 * ### A threshold is a detent with two sides
 *
 * `rememberDetentTicker(FeedbackIntent.DragThreshold)` and an index of 0 or 1
 * gives the other shape the policy allows: **one** report as a drag passes the
 * point where letting go would do something, and one more if it comes back.
 *
 * ```kotlin
 * val latch = rememberDetentTicker(FeedbackIntent.DragThreshold)
 * // in the drag:
 * latch.at(if (past) 1 else 0)
 * ```
 *
 * It is the same mechanism and not an analogy for it: what makes a threshold
 * report once is exactly the guard that makes a detent report once, and the
 * hand-rolled `var armed = true` that every threshold would otherwise carry is
 * the thing this class exists to stop being written six times. `Toast` uses it
 * this way; so can a caller — see the theming guide's note on adding your own.
 */
@Stable
class DetentTicker internal constructor(
    private val feedback: FeedbackDispatcher,
    /**
     * What it performs on a crossing.
     *
     * [FeedbackIntent.Tick] for a detent, which is what this is for and the
     * default. [FeedbackIntent.DragThreshold] for a two-sided threshold, which
     * is the same guard doing the same job for the other row of the policy
     * table.
     */
    private val intent: FeedbackIntent = FeedbackIntent.Tick,
    private val clock: TimeSource = TimeSource.Monotonic,
) {

    private var last: Float = Float.NaN
    private var lastFired: TimeMark? = null

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
            // `last` moves whether or not the tick fires. The alternative is
            // that a dropped crossing leaves the ticker armed for it, so the
            // *next* crossing fires immediately and the limit does nothing on a
            // fast drag — which is the only place it is needed.
            last = index
            val since = lastFired
            if (since == null || since.elapsedNow() >= MinimumTickInterval) {
                feedback.perform(intent)
                lastFired = clock.markNow()
            }
        }
    }

    /** Same, for a detent identified by something other than a number. */
    fun at(index: Int) = at(index.toFloat())

    /** Ends the gesture. The next [at] arms rather than fires. */
    fun reset() {
        last = Float.NaN
        // Deliberately *not* clearing `lastFired`. Two gestures a few
        // milliseconds apart are one continuous rattle to the hand, whatever
        // they are to the code.
    }

    companion object {
        /**
         * The shortest gap between two ticks.
         *
         * 80ms, and it is derived from the pulse rather than tuned by ear.
         * [FeedbackIntent.Tick] is 20ms of motor time on the web, so ticks 40ms
         * apart would leave the motor running half the time — which is a buzz
         * with gaps in it, not a sequence of taps. 20ms on and 60 off is a
         * quarter duty cycle, and 12 a second is about where a hand stops
         * resolving separate events anyway.
         *
         * Nothing a deliberate gesture does comes near it: the stepped slider
         * measured on the built site crossed its detents about 160ms apart, so
         * this never fires there. What it catches is the flung wheel, which
         * crosses a row roughly every 8ms and was firing every one of them.
         *
         * It is a floor on the *feel*, not a budget. Nothing is queued — see
         * [at].
         */
        val MinimumTickInterval: Duration = 80.milliseconds
    }
}

/**
 * Remembers a [DetentTicker] wired to the current [LocalFeedback].
 *
 * @param intent What a crossing performs. Leave it for a detent; pass
 *   [FeedbackIntent.DragThreshold] for a threshold, with an index of 0 or 1.
 */
@Composable
fun rememberDetentTicker(intent: FeedbackIntent = FeedbackIntent.Tick): DetentTicker {
    val feedback = LocalFeedback.current
    return remember(feedback, intent) { DetentTicker(feedback, intent) }
}
