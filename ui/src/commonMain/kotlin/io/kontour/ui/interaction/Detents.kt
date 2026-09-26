package io.kontour.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import kotlin.math.abs
import kotlin.math.exp
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Reports each detent a drag crosses, once: a [FeedbackIntent.Tick] by default,
 * or whichever intent it was made with — a [FeedbackIntent.Snap] for a resting
 * place, a two-sided [FeedbackIntent.DragThreshold] for a threshold.
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
 * `rememberDetentTicker(FeedbackIntent.DragThreshold, back = FeedbackIntent.DragThresholdBack)`
 * and an index of 0 or 1 gives the other shape the policy allows: **one** report
 * as a drag passes the point where letting go would do something, and one more,
 * softer, if it comes back.
 *
 * ```kotlin
 * val latch = rememberDetentTicker(FeedbackIntent.DragThreshold, back = FeedbackIntent.DragThresholdBack)
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
     * What it performs on a crossing *back* — to a lower index. The same as
     * [intent] for a detent, which feels the same both ways;
     * [FeedbackIntent.DragThresholdBack] for a threshold, where coming back out
     * matters less than going in.
     */
    private val back: FeedbackIntent = intent,
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
            val crossing = if (index < last) back else intent
            last = index
            if (floor.claim(crossing)) feedback.perform(crossing)
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
 * @param back What a crossing to a lower index performs: [intent] unless told
 *   otherwise, and [FeedbackIntent.DragThresholdBack] for a threshold.
 */
@Composable
fun rememberDetentTicker(
    intent: FeedbackIntent = FeedbackIntent.Tick,
    back: FeedbackIntent = intent,
): DetentTicker {
    val feedback = LocalFeedback.current
    val floor = LocalFeedbackFloor.current
    return remember(feedback, intent, back, floor) { DetentTicker(feedback, intent, back, floor = floor) }
}

/**
 * A hold's feedback: started when the hold begins, told how far through it is,
 * and stopped when it ends — a rumble, where the platform has one.
 *
 * The one way a component sustains feedback, so the stop is guaranteed rather
 * than remembered: [rememberHoldFeedback] stops it when the component leaves
 * the composition, and starting again stops the last one first. Call [stop] in a
 * `finally` around the wait it reports, and a cancelled wait cannot leave a
 * phone rumbling.
 */
@Stable
class HoldFeedback internal constructor(
    private val feedback: FeedbackDispatcher,
    private val intent: FeedbackIntent,
) {
    private var held: SustainedFeedback? = null

    /** Begins it — ending one already going first. */
    fun start() {
        stop()
        held = feedback.sustain(intent)
    }

    /** How far through the hold is, 0 to 1. Every frame is fine. */
    fun progress(fraction: Float) {
        held?.update(fraction.coerceIn(0f, 1f))
    }

    /** Ends it. Ending one that is not going does nothing. */
    fun stop() {
        held?.stop()
        held = null
    }
}

/**
 * Remembers a [HoldFeedback] wired to the current [LocalFeedback], stopped when
 * the call site leaves the composition.
 *
 * @param intent What the hold sustains: [FeedbackIntent.Hold], the one intent
 *   with a rumble of its own.
 */
@Composable
fun rememberHoldFeedback(intent: FeedbackIntent = FeedbackIntent.Hold): HoldFeedback {
    val feedback = LocalFeedback.current
    val hold = remember(feedback, intent) { HoldFeedback(feedback, intent) }
    DisposableEffect(hold) { onDispose { hold.stop() } }
    return hold
}

/**
 * The feel of a control without steps moving under a finger: faint grains a
 * little way apart, closer and firmer the faster it goes, and nothing while it
 * is still.
 *
 * Asked for from a phone: dragging a slider or a knob "when it's not in step
 * mode, it should have some feedback, proportional to how fast it's moving". A
 * stepped control already has that — its detents go past faster the faster it
 * moves — and a continuous one had nothing at all between its two ends.
 *
 * ### Proportional twice
 *
 * A grain is felt every [GrainSpacing] of the travel, so a drag twice as fast
 * meets them twice as often, up to one every [MinimumGrainInterval]. Each grain
 * is also performed at a strength that follows the speed, from [SlowestStrength]
 * at a crawl to full at [FullSpeed] — so once the grains are as close as they
 * may come, a faster drag is still a firmer one.
 *
 * The speed is the drag's own, measured here between calls and smoothed, so a
 * caller hands over only where the control is. Hand it the *clamped* position:
 * a drag pushing against an end is not moving, and the end stop has its own
 * report.
 *
 * ```kotlin
 * val texture = rememberDragTexture()
 * // in the drag, for a control without steps:
 * texture.at(fraction)
 * // when the gesture ends:
 * texture.reset()
 * ```
 */
@Stable
internal class DragTexture(
    private val feedback: FeedbackDispatcher,
    private val floor: FeedbackFloor,
    private val clock: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    /** Where the last grain was felt; `NaN` between gestures. */
    private var grain = Float.NaN
    private var last = Float.NaN
    private var lastAt: ComparableTimeMark? = null

    /** Travel a second, smoothed. */
    internal var speed = 0f
        private set

    /** Where the control is now, as a fraction of its travel. */
    fun at(position: Float) {
        val now = clock.markNow()
        val then = lastAt
        if (grain.isNaN() || then == null) {
            grain = position
            last = position
            lastAt = now
            speed = 0f
            return
        }
        val seconds = (now - then).toDouble(DurationUnit.SECONDS).toFloat()
        // Two events in one frame: keep the earlier mark, so the next measures
        // both movements over the time they really took.
        if (seconds >= MinimumSampleSeconds) {
            val moving = abs(position - last) / seconds
            val blend = 1f - exp(-seconds / SmoothingSeconds)
            speed += (moving - speed) * blend
            last = position
            lastAt = now
        }
        if (abs(position - grain) < GrainSpacing) return
        // `grain` moves only when one is felt: a grain the floor refused is
        // owed, and comes on the next frame it may — which, at speed, is what
        // keeps them as close as they are allowed to be.
        if (!floor.claim(FeedbackIntent.Scrub)) return
        grain = position
        feedback.perform(FeedbackIntent.Scrub, strengthAt(speed))
    }

    /** Ends the gesture. The next [at] starts measuring afresh. */
    fun reset() {
        grain = Float.NaN
        last = Float.NaN
        lastAt = null
        speed = 0f
    }

    companion object {
        /** How far apart the grains are: 1/40 of the travel. */
        const val GrainSpacing = 0.025f

        /**
         * The closest two grains come: 50ms, under the detents' 80. A texture is
         * meant to run together at speed; see [minimumGap].
         */
        val MinimumGrainInterval: Duration = 50.milliseconds

        /** The speed a grain is felt at full strength: the whole travel in half a second. */
        const val FullSpeed = 2f

        /** How faint a grain is at a crawl, as a share of full. */
        const val SlowestStrength = 0.25f

        /** How quickly the speed follows the finger. */
        private const val SmoothingSeconds = 0.06f

        /** Shorter than this between two events and they are one sample. */
        private const val MinimumSampleSeconds = 0.004f

        /** A grain's strength at [speed], [SlowestStrength] to 1. */
        fun strengthAt(speed: Float): Float =
            SlowestStrength + (1f - SlowestStrength) * (speed / FullSpeed).coerceIn(0f, 1f)
    }
}

/** Remembers a [DragTexture] on the current feedback and its rate floor. */
@Composable
internal fun rememberDragTexture(): DragTexture {
    val feedback = LocalFeedback.current
    val floor = LocalFeedbackFloor.current
    return remember(feedback, floor) { DragTexture(feedback, floor) }
}

/**
 * One report each time a drag runs into a stop, and none while it stays there or
 * backs off it.
 *
 * Asked for on every slider: "a haptic in standard mode … that fires when you hit
 * the end stop". A two-sided ticker would report leaving a wall as well as
 * reaching it, which is a threshold's shape and not a wall's — so the index
 * handed to it only ever goes *up*, once per stop met.
 *
 * ### Met once, until it has been left
 *
 * **It used to re-arm the moment the drag was back inside the range**, and a
 * finger held against the end of a slider is not still: a pixel back is inside,
 * the next pixel out is a second wall, and the knock came "a few times
 * sometimes" for one push. The rubber band made it likelier, because the way
 * back pays the band first and moves nothing — so a drag could read as inside
 * without having gone anywhere.
 *
 * So the stop re-arms only once the drag has come [release] clear of it: a
 * finger's tremor is not a second push, and backing off properly and pushing in
 * again still is.
 *
 * [FeedbackIntent.Limit] at the ends of a range, a dull knock rather than a
 * detent's tick. It used to be `DragThreshold`, for the reason that still holds:
 * a tick shares the rate floor with the detents, and on a stepped slider the last
 * detent's tick and the wall arrive together, so the wall would be the one
 * dropped. A limit is not floored. [FeedbackIntent.Bump] where the stop is
 * another part of the control — a range slider's other thumb — through
 * [touching].
 *
 * Fed the *unclamped* position, so it reports under reduced motion too, where the
 * rubber band that shows the wall is switched off and the report is the only sign.
 */
internal class EndStopLatch(
    private val ticker: DetentTicker,
    /** How far clear of a stop, in the same units as the position, re-arms it. */
    private val release: Float = EndStopRelease,
) {
    /** The stop being rested against: `-1` the start, `1` the end, `0` neither. */
    private var against = 0
    private var hits = 0

    /**
     * A gesture beginning. Arms the ticker without firing.
     *
     * @param resting The stop the gesture starts against, if it starts against
     *   one it did not run into — two thumbs already together, say. Pushing on
     *   into it is not meeting it; leaving it and coming back is.
     */
    fun arm(resting: Int = 0) {
        ticker.reset()
        against = resting
        hits = 0
        ticker.at(hits)
    }

    /**
     * Where the drag is, unclamped: the stops are at [low] and [high], and a
     * position past one has run into it.
     */
    fun at(position: Float, low: Float = 0f, high: Float = 1f) {
        when {
            position > high -> reached(1)
            position < low -> reached(-1)
            against == 1 && position <= high - release -> against = 0
            against == -1 && position >= low + release -> against = 0
        }
    }

    /**
     * How far short of a single stop the drag is: at or below 0 it has met it,
     * [release] or more and it has left.
     */
    fun touching(clearance: Float) {
        when {
            clearance <= 0f -> reached(1)
            clearance >= release -> against = 0
        }
    }

    /**
     * A stop met by something other than a finger, say a spin running out at
     * one end: `1` the end, `-1` the start. Once, as a finger's would be.
     */
    fun reached(end: Int) {
        if (against == end) return
        against = end
        hits++
        ticker.at(hits)
    }

    /**
     * The drag has come clear of whatever stop it was against, by a measure the
     * caller keeps — for a stop whose position only the caller knows. The next
     * meeting counts.
     */
    fun clear() {
        against = 0
    }

    /** The gesture is over. */
    fun reset() {
        ticker.reset()
        against = 0
        hits = 0
    }

    companion object {
        /**
         * How far back from a stop a drag has to come before meeting it again
         * counts: 4% of the travel. A finger's tremor is a pixel or two; this is
         * a dozen on a phone-wide slider, which is a deliberate step back.
         */
        const val EndStopRelease = 0.04f
    }
}

/**
 * Remembers an [EndStopLatch] on the current feedback.
 *
 * @param intent What meeting a stop performs: [FeedbackIntent.Limit] for the end
 *   of a range, [FeedbackIntent.Bump] for another part of the same control.
 */
@Composable
internal fun rememberEndStopLatch(intent: FeedbackIntent = FeedbackIntent.Limit): EndStopLatch {
    val ticker = rememberDetentTicker(intent)
    return remember(ticker) { EndStopLatch(ticker) }
}

/**
 * One rate floor for every light haptic under a theme.
 *
 * The argument for the interval is on [DetentTicker.MinimumTickInterval]; the
 * argument for *sharing* it is that a floor per component is a floor per
 * component, and a hand does not feel components. A slider's ticks and a chip's
 * tap forty milliseconds later are one rattle to the person holding the phone.
 *
 * Gates the intents that arrive in streams — a press or a toggle answered
 * ([FeedbackIntent.Tap], [FeedbackIntent.ToggleOn], [FeedbackIntent.ToggleOff]), a
 * detent ([FeedbackIntent.Tick]) and a slider's texture ([FeedbackIntent.Scrub]),
 * each no sooner than its [minimumGap] after the last. An outcome has to arrive when it happens, and outcomes do
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
     * Anything that does not arrive in a stream always passes — an outcome is
     * never the report dropped — **and is recorded**, so the stream behind it
     * waits its usual gap. It used to pass without touching the clock, on the
     * argument that an outcome is not part of the stream the floor thins. That
     * was right about dropping and wrong about what follows: a phone restarts
     * its motor for each new vibration, so a detent's tick landing a frame after
     * an end stop's knock cut the knock short — the knob's last step behind its
     * wall, a date range's day behind the month it paged, a range slider's step
     * behind the thumbs meeting. The outcome is the one felt now.
     */
    fun claim(intent: FeedbackIntent): Boolean {
        if (!intent.arrivesInStreams) {
            lastFired = clock.markNow()
            return true
        }
        val since = lastFired
        if (since != null && since.elapsedNow() < intent.minimumGap) return false
        lastFired = clock.markNow()
        return true
    }
}

/**
 * Whether [FeedbackFloor] may drop this intent.
 *
 * The ones that arrive in streams, which is not the same set as the lightest
 * feels — see the note on [FeedbackFloor]. [FeedbackIntent.Snap] is deliberately
 * absent: a reorder fires one per gap a row crossed, and a reorder the hand does
 * not feel is a reorder the eye has to go looking for.
 */
internal val FeedbackIntent.arrivesInStreams: Boolean
    get() = this == FeedbackIntent.Tap ||
        this == FeedbackIntent.Tick ||
        // A slider's texture is nothing but a stream.
        this == FeedbackIntent.Scrub ||
        // A toggle is a press answered, and a column of checkboxes run down
        // with a thumb is the same rattle a chip row is.
        this == FeedbackIntent.ToggleOn ||
        this == FeedbackIntent.ToggleOff

/**
 * How soon after the last streamed haptic this one may land.
 *
 * [DetentTicker.MinimumTickInterval] for everything but a
 * [FeedbackIntent.Scrub] grain, which may come at [DragTexture.MinimumGrainInterval]:
 * a texture is meant to run together at speed — that is how a fast drag feels
 * fast — where a detent that runs together is a detent lost.
 */
internal val FeedbackIntent.minimumGap: Duration
    get() = if (this == FeedbackIntent.Scrub) DragTexture.MinimumGrainInterval else DetentTicker.MinimumTickInterval

/**
 * The floor in force. A default instance so a component outside a theme still
 * rate-limits itself rather than throwing.
 */
internal val LocalFeedbackFloor = staticCompositionLocalOf { FeedbackFloor() }
