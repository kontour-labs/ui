package io.kontour.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
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
    /**
     * The rate floor, shared with every other light haptic in the composition.
     *
     * It used to be a `TimeMark` on each ticker, which was already quietly wrong
     * — a `TimePicker` is three `WheelPicker`s with three tickers, and the hand
     * holding it feels one rattle rather than three. With a dozen components now
     * reporting a tap it would be plainly wrong.
     *
     * **Required, and it took the ticker's `clock` with it.** There used to be a
     * `clock: TimeSource = TimeSource.Monotonic` here for a test to drive the
     * limit with a `TestTimeSource`, and once the limit moved out the clock was
     * a parameter whose only purpose was to build the default for this one.
     * A ticker does not own a clock any more; the thing that rate-limits owns
     * the clock, and a test that drives the limit constructs the floor it is
     * driving.
     */
    private val floor: FeedbackFloor,
) {

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
            // `last` moves whether or not the tick fires. The alternative is
            // that a dropped crossing leaves the ticker armed for it, so the
            // *next* crossing fires immediately and the limit does nothing on a
            // fast drag — which is the only place it is needed.
            last = index
            if (floor.claim(intent)) feedback.perform(intent)
        }
    }

    /** Same, for a detent identified by something other than a number. */
    fun at(index: Int) = at(index.toFloat())

    /** Ends the gesture. The next [at] arms rather than fires. */
    fun reset() {
        last = Float.NaN
        // Deliberately *not* clearing the floor. Two gestures a few milliseconds
        // apart are one continuous rattle to the hand, whatever they are to the
        // code — and the floor is shared now, so it is not this ticker's to
        // clear in any case.
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
    val floor = LocalFeedbackFloor.current
    return remember(feedback, intent, floor) { DetentTicker(feedback, intent, floor = floor) }
}

/**
 * One rate floor for every light haptic under a theme.
 *
 * The argument for the interval is on [DetentTicker.MinimumTickInterval]; the
 * argument for *sharing* it is that a floor per component is a floor per
 * component, and a hand does not feel components. A slider's ticks and a chip's
 * tap forty milliseconds later are one rattle to the person holding the phone.
 *
 * Gates the two intents that arrive in streams — [FeedbackIntent.Tap] and
 * [FeedbackIntent.Tick]. An outcome has to arrive when it happens, and outcomes do
 * not come in streams: a threshold swallowed because a slider ticked forty
 * milliseconds ago is a gesture that silently changed meaning. So [claim] takes the
 * intent and answers yes to anything else, which is also why the switch's midpoint
 * can go through a ticker without becoming droppable.
 *
 * **This is a question about *rate*, not about weight.** It used to ask whether the
 * intent `isLight`, and once [io.kontour.ui.interaction.FeedbackFeel] existed that
 * was two different things wearing one word: [FeedbackIntent.Tap] is
 * [FeedbackFeel.Medium] and is still thinned here, because a chip answering three
 * presses in eighty milliseconds is one rattle to the hand whatever each press
 * weighs.
 *
 * It is here rather than in the dispatcher because the tests install their own
 * dispatcher: a floor there would be invisible to them, and the stepped-slider
 * assertion that counts ticks across a 40-step drag exists precisely because
 * this is in common code on a wall clock.
 */
@Stable
internal class FeedbackFloor(private val clock: TimeSource = TimeSource.Monotonic) {

    private var lastFired: TimeMark? = null

    /**
     * True if [intent] may fire now, recording the firing when it does.
     *
     * Anything that does not arrive in a stream passes straight through and does
     * not reset the clock either — an outcome is not part of the stream the floor
     * is thinning.
     */
    fun claim(intent: FeedbackIntent): Boolean {
        if (!intent.arrivesInStreams) return true
        val since = lastFired
        if (since != null && since.elapsedNow() < DetentTicker.MinimumTickInterval) return false
        lastFired = clock.markNow()
        return true
    }
}

/**
 * Whether [FeedbackFloor] may drop this intent.
 *
 * The two that arrive in streams, which is not the same set as the two lightest
 * feels — see the note on [FeedbackFloor]. [FeedbackIntent.Selection] is
 * deliberately absent: it fires once per reorder, which is once per gap a row
 * crossed, and a reorder the hand does not feel is a reorder the eye has to go
 * looking for.
 */
internal val FeedbackIntent.arrivesInStreams: Boolean
    get() = this == FeedbackIntent.Tap || this == FeedbackIntent.Tick

/**
 * The floor in force. A default instance so a component outside a theme still
 * rate-limits itself rather than throwing.
 */
internal val LocalFeedbackFloor = staticCompositionLocalOf { FeedbackFloor() }
