package io.kontour.ui.components.selection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap as snapSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.rememberDragTexture
import io.kontour.ui.interaction.rememberEndStopLatch
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.rememberRubberBand
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.interaction.horizontalDragOwning
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A range with two thumbs on one track — a departure window, a fare band.
 *
 * ```kotlin
 * RangeSlider(
 *     value = window,
 *     onValueChange = { window = it },
 *     valueRange = 0f..24f,
 *     steps = 23,
 *     stateDescription = { "${it.start.roundToInt()}:00 to ${it.endInclusive.roundToInt()}:00" },
 * )
 * ```
 *
 * Shares [Slider]'s drag accumulator and its detent feel — see that file for why
 * the raw drag position is kept separately from the reported value.
 *
 * **The two thumbs are two separate things to a screen reader**, because one
 * node cannot express two values: a `ProgressBarRangeInfo` has one `current`.
 * Each thumb is its own adjustable node with its own bounded range, so "adjust"
 * from assistive tech cannot produce an inverted range — and those bounds follow
 * [minDistance], so assistive tech is offered exactly the values a finger can
 * reach.
 *
 * ### The thumbs push rather than block
 *
 * Dragging one thumb into the other used to stop it dead at its neighbour. It
 * now shoves that neighbour along in front of it and keeps going, stopping only
 * at the end of the track. Blocking makes the control feel jammed at exactly the
 * moment the user is asking for the narrowest range there is; pushing keeps the
 * finger and the thumb together, which is the whole contract of a drag. The
 * pushed thumb lags a little as it goes and stretches while it lags, so being
 * shoved looks like being shoved.
 *
 * @param value The current range. Clamped into [valueRange], and never inverted.
 *   Not corrected against [minDistance] on arrival: a caller that starts a range
 *   narrower than its own minimum keeps it until something moves, because
 *   calling back with a different value than the one just passed in is how
 *   controlled state gets into a loop.
 * @param minDistance The narrowest the range may be, in the units of
 *   [valueRange]. `0f` lets the thumbs meet. Clamped to the range's own span, so
 *   a minimum wider than the track cannot invert the arithmetic.
 * @param steps Discrete stops *between* the ends. `0` is continuous.
 * @param stateDescription Turns the range into something a screen reader can
 *   say. Without it each thumb announces a bare percentage. Strongly
 *   recommended.
 * @param onValueChangeFinished Called when a drag ends, for a commit too
 *   expensive to do per frame.
 */
@Composable
fun RangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    minDistance: Float = 0f,
    steps: Int = 0,
    /**
     * Marks drawn *between* the [steps], for a scale a finger reads by feel.
     *
     * How many go in each gap, so `steps = 4, minorTicks = 1` is a mark every
     * half step. Zero, the default, is the coarse scale alone.
     *
     * They are drawn shorter than the major marks rather than fainter, and they
     * are not detents: the value still lands on a step. What they add is a
     * finer reading of where the thumb is between two of them, which is what an
     * instrument dial's fine graduations are for.
     */
    minorTicks: Int = 0,
    /**
     * Whether to draw a dot on the track at each detent.
     *
     * **On whenever the slider is stepped**, which is what a stepped slider is
     * for: the steps are the choices, and a track that hides them makes the user
     * find them by feel. A continuous slider has no detents to draw and gets
     * none.
     *
     * This went off by default for a round, on the argument that a row of dots
     * turns a slider into a diagram of its own implementation. That is true at
     * *many* steps — thirty of them on a short track merge into a dashed line
     * that reads as texture — and it is the wrong default, because the common
     * stepped slider has four or five stops and they are the whole point of it.
     * Pass `false` for the dense case.
     */
    showTicks: Boolean = steps > 0,
    /** What the range is *of*. `null` when a label beside it already says. */
    contentDescription: String? = null,
    startContentDescription: String = Theme.strings.rangeStart,
    endContentDescription: String = Theme.strings.rangeEnd,
    stateDescription: ((ClosedFloatingPointRange<Float>) -> String)? = null,
    /**
     * The value under the finger, in a bubble above the head being held. See
     * [Slider]'s: off unless given, and drawn outside the control's bounds. One
     * label, over the thumb the finger has — a thumb being shoved along by it is
     * not the one being read.
     */
    valueLabel: ((Float) -> String)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    /**
     * The track, the thumb, the tick marks and the value label, enabled and
     * disabled. See [SliderDefaults.colours].
     */
    colours: SliderColours = SliderDefaults.colours(),
    interactionSource: MutableInteractionSource? = null,
) {
    // An inverted range has no reading, and the failure it used to cause was
    // invisible: `coerceIn` throws on one, and the call that reached it first was
    // inside the `setProgress` **semantics action** — so no screenshot could see
    // it, no gesture could reach it, and the first person to find it would be
    // using a screen reader or an automated accessibility check.
    require(valueRange.start <= valueRange.endInclusive) {
        "RangeSlider was given an inverted valueRange " +
            "(${valueRange.start}..${valueRange.endInclusive}). The start has to be at " +
            "or below the end; a range built from two computed bounds can invert when " +
            "the data behind them is empty or out of order."
    }

    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val motion = Theme.motion
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()

    val dragged by interactions.collectIsDraggedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val active = dragged || pressed

    val span = valueRange.endInclusive - valueRange.start

    /**
     * [minDistance], clamped to something the track can actually hold.
     *
     * A minimum wider than the range would put `valueRange.endInclusive - gap`
     * below `valueRange.start`, and `coerceIn` **throws** on an inverted range —
     * the same trap that took a frame down from inside `Switch`'s draw. A caller
     * that asks for more separation than exists gets the whole track instead.
     *
     * And **rounded up onto the step grid**, because a stepped slider has no
     * values between its notches.
     *
     * The minimum used to be added to the dragged thumb's value after it had
     * been snapped, so with a step of 1 and a minimum of 1.5 the shoved thumb
     * came to rest on `start + 1.5` — half a step off the grid, on a value the
     * tick marks say does not exist and that dragging *that* thumb can never
     * reproduce, because its own first delta re-snaps and jumps it half a step.
     * The dragged thumb's own ceiling was off-grid for the same reason, which
     * made the last part-step of track a dead zone it sat pinned in.
     *
     * Up rather than to nearest: a minimum that rounded down would be quietly
     * violated, and a caller who asks for 1.5 steps of clearance on a slider
     * that only has whole ones is asking for two.
     */
    val gap = run {
        val asked = minDistance.coerceIn(0f, span.coerceAtLeast(0f))
        if (steps <= 0 || span == 0f || asked == 0f) {
            asked
        } else {
            val stepSize = span / (steps + 1)
            (ceil(asked / stepSize) * stepSize).coerceAtMost(span)
        }
    }

    fun fractionOf(v: Float) = if (span == 0f) 0f else ((v - valueRange.start) / span).coerceIn(0f, 1f)

    val startFraction = fractionOf(value.start)
    val endFraction = fractionOf(value.endInclusive)

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentFinished by rememberUpdatedState(onValueChangeFinished)

    /**
     * Which thumb this drag is moving, decided once when it starts.
     *
     * Re-deciding per event would hand the drag to the other thumb the moment
     * the finger crossed the midpoint between them, which reads as the range
     * collapsing and then reopening from the other side.
     */
    var activeThumb by remember { mutableStateOf(Thumb.None) }

    // The label's thumb outlives the drag by as long as the label takes to fade:
    // `activeThumb` goes back to `None` the moment the finger lifts.
    val labelled = remember { LabelledThumb() }
    if (activeThumb != Thumb.None) labelled.thumb = activeThumb
    val labelProgress = animateFloatAsState(
        targetValue = if (valueLabel != null && active && activeThumb != Thumb.None) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "rangeSliderValueLabel",
    )

    /**
     * The grow-and-stretch, **per thumb**.
     *
     * One pair of animations used to drive both, keyed on `active`, which is a
     * fact about the *control* rather than about either thumb: touching one end
     * of the range made the other end swell at the same moment, so a range
     * slider under a finger read as two handles being held at once.
     *
     * [activeThumb] already knew which one it was — it decides `reach` and which
     * value the drag moves — and simply was not asked here. A thumb is now held
     * when the control is held *and* the gesture belongs to it, which for a press
     * that has not yet chosen a thumb is neither of them, and that is right: a
     * press on the bare track has not picked one up.
     */
    fun held(thumb: Thumb): Boolean = active && activeThumb == thumb && !motion.reduceMotion

    /**
     * The grab is bouncy; the return is not, and the return is the reported bug.
     *
     * *"Make sure it smoothly animates back to the little circle when the user
     * lets go, rather than snapping to the full-width pill before animating
     * back."* A thumb's drawn width is not one animation but two multiplied
     * together, and they used to come home on different springs:
     *
     * - the squash, through `RubberBand.release` on `springSnappy` — damping
     *   0.9, stiffness 1400, over in a few frames;
     * - the stretch, through the scale and aspect below on `springBouncy` —
     *   damping 0.45, stiffness 900, slower and oscillating.
     *
     * `sliderThumb` draws `width·(1−pull) + 1.5r·pull`, so the fast spring
     * returned `pull` to zero while `width` was still `2r·1.5`: the thumb went
     * from its squashed 1.5r **through** the full 3r pill and only then home to
     * 2r. A 100% excursion, in the direction of the shape the finger had just
     * let go of.
     *
     * Matching the two springs was the obvious answer and it is **not** the fix —
     * traced frame by frame, a shared spring still peaks 9.8% over resting size,
     * because the lerp between a wide number and a narrow one bulges in the middle
     * whatever rate the two share. The band is released on `springGentle` instead,
     * so the stretch gets home *first* and there is no bulge to have; the note on
     * `band.release` in `onEnd` has the arithmetic.
     *
     * What this does is the other half, and it is worth the line on its own: the
     * bounce belongs to the grab, where a thumb springing open under a finger
     * reads as responsive, and not to the return, where `springBouncy`'s
     * undershoot is a two-pixel wobble in the tail of a thumb that has already
     * arrived.
     */
    fun thumbReturn(thumb: Thumb) = motion.springOrTween<Float>(
        if (held(thumb)) motion.springBouncy else motion.springSnappy
    )

    val startScale = animateFloatAsState(
        targetValue = if (held(Thumb.Start)) 1.25f else 1f,
        animationSpec = thumbReturn(Thumb.Start),
        label = "rangeSliderStartThumb",
    )
    val endScale = animateFloatAsState(
        targetValue = if (held(Thumb.End)) 1.25f else 1f,
        animationSpec = thumbReturn(Thumb.End),
        label = "rangeSliderEndThumb",
    )

    // A circle at rest that lengthens into a capsule while it is held. Shares
    // the spring with the scale above, so a thumb grows and stretches as one
    // gesture rather than two overlapping ones — and comes home on the same
    // spring as the squash, which is what `thumbReturn` is for.
    val startAspect = animateFloatAsState(
        targetValue = if (held(Thumb.Start)) SliderDefaults.ThumbAspect else 1f,
        animationSpec = thumbReturn(Thumb.Start),
        label = "rangeSliderStartAspect",
    )
    val endAspect = animateFloatAsState(
        targetValue = if (held(Thumb.End)) SliderDefaults.ThumbAspect else 1f,
        animationSpec = thumbReturn(Thumb.End),
        label = "rangeSliderEndAspect",
    )

    /**
     * Which thumb the rubber band's stretch belongs to.
     *
     * **Not [activeThumb], and the difference is the whole of a reported bug.**
     * `activeThumb` is about the *gesture*: `onEnd` clears it, because it drives
     * the deferred tap emit and the painter order and both are finished when the
     * finger lifts. The band is not finished when the finger lifts — that is
     * precisely when it springs home — so a squash gated on `activeThumb`
     * vanishes on the frame the gesture ends.
     *
     * What that looked like: `band.release` runs on `springGentle` so the stretch
     * gets home before the squash unwinds, which is what keeps a released thumb
     * from passing back out through the full-width pill. `RangeSlider` was given
     * that spec and never showed it. `activeThumb = Thumb.None` is a snapshot
     * write that lands immediately, while `scope.launch` does not render its first
     * frame synchronously — so from the next composition the squash read zero for
     * the whole release, `pull` stepped from nearly one to exactly zero between
     * two frames while the thumb was still a stretched capsule, and the drawn
     * width jumped to the pill in a single frame. Not a bad animation: no
     * animation.
     *
     * Keyed here instead and cleared when `release` **returns**, so the band's
     * whole life is drawn. It also makes a second state unrepresentable: one band
     * serves two thumbs, so a leftover offset from a gesture on one used to be
     * read as the other's squash the moment a new gesture picked it up — a thumb
     * drawn pre-squashed against a wall it had never touched.
     */
    var bandThumb by remember { mutableStateOf(Thumb.None) }

    /** See [Slider]'s `dragFraction`. `NaN` when no drag is in progress. */
    var dragFraction by remember { mutableFloatStateOf(Float.NaN) }

    /** See [Slider]'s `carrying`: a press moves the value, a drag moves the thumb. */
    var carrying by remember { mutableStateOf(false) }

    /** See [Slider]'s: the shared ticker, which is also where the rate limit is. */
    val ticker = rememberDetentTicker()

    /**
     * What a drag pushing past the **track's** ends does instead of nothing.
     *
     * Only 0 and 1. The inner stop — a thumb brought up against the other one —
     * already reads as being shoved, because the pushed thumb deforms through
     * the reach below; a band there would be a second deformation of the same
     * contact and would need one instance per thumb to know which.
     */
    val band = rememberRubberBand()

    // Once each time a thumb runs into the end of the track — see `Slider`'s.
    val endStop = rememberEndStopLatch()

    // And once, lighter, each time it runs into the other thumb. That is not a
    // wall — the other one gives, and is shoved along, and the shove is drawn —
    // but it was silent, and a meeting under a finger is one the hand should
    // feel: "a light bit of feedback when the two heads collide". Latched like
    // the wall, so shoving on is not a second meeting and parting is not one.
    val bump = rememberEndStopLatch(FeedbackIntent.Bump)

    // A range without steps drags with the slider's texture. See `Slider`'s.
    val texture = rememberDragTexture()

    // The label over the held thumb. See `Slider`'s.
    val labelMeasurer = rememberTextMeasurer()
    val labelStyle = Theme.typography.labelMedium.copy(color = colours.valueLabelContent)
    val labelPaddingH = with(density) { Theme.spacing.xs.toPx() }
    val labelPaddingV = with(density) { Theme.spacing.xxs.toPx() }
    val labelGap = with(density) { SliderLabelGap.toPx() }
    // The switch's pill: round at rest, a G2 pill stretched. See `sliderThumb`.
    val pill = Theme.shapes.pill
    val rtl = layoutDirection == LayoutDirection.Rtl
    // See `Slider`: the limit is the finger's travel past the stop, not a
    // fraction of the thumb, and `sliderThumb` normalises by the same number.
    val thumbSquashPx = with(LocalDensity.current) { EndStopTravel.toPx() }

    // Read here rather than inside `drawWithCache`, which is not a composable.
    val tickSize = Theme.componentDefaults.sliderTickSize

    /**
     * Where the press landed, kept for the gesture that never moves.
     *
     * With the thumbs coincident the choice of thumb is deferred to the first
     * delta's direction — and a tap has no deltas at all. Without this, tapping
     * a collapsed range does nothing.
     */
    var pressFraction by remember { mutableFloatStateOf(Float.NaN) }

    /**
     * What this gesture last emitted, or `null` between gestures.
     *
     * See [emit]. A gesture has to build on its own last answer rather than on
     * one that has been round a recomposition, or the push rule compares against
     * a position the pushed thumb left a frame ago.
     */
    var emitted by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(0f, 1f)
        if (steps <= 0) return valueRange.start + clamped * span
        val stepCount = steps + 1
        val snapped = (clamped * stepCount).roundToInt().toFloat() / stepCount
        return valueRange.start + snapped * span
    }

    /**
     * The detent tick, if a **drag** just crossed one.
     *
     * `carrying` is the whole of the condition — see [Slider]'s `emit`, which
     * carries the reasoning. Here the tap case is even plainer: a press that
     * never moved emits from `onEnd`, so a stepped range slider fired once on
     * touch and once on release for a gesture that crossed nothing. Both are the
     * same non-event, and both are gone.
     *
     * The index is still recorded either way, so the first pixel of a drag that
     * follows a tap does not tick for the detent the thumb is already on.
     */
    fun tick(next: Float) {
        if (steps <= 0) return
        val index = ((next - valueRange.start) / span * (steps + 1)).roundToInt().toFloat()
        if (carrying) ticker.at(index) else { ticker.reset(); ticker.at(index) }
    }

    /**
     * Moves one thumb, pushing the other along ahead of it.
     *
     * An inverted range stays unrepresentable — that has not changed and is the
     * point of doing the arithmetic here rather than trusting the gesture — but
     * it is now the *track's* end that stops the drag, not the other thumb. The
     * dragged thumb goes where the finger is; its neighbour is displaced to keep
     * [gap] between them and clamped to the end; and if the neighbour runs out
     * of track, the dragged one stops [gap] short of it rather than pretending to
     * carry on.
     *
     * ### It builds on what it last emitted, not on [value]
     *
     * The push rule is `maxOf(theOtherThumb, dragged + gap)` — hold the pushed
     * thumb where it was pushed to, and only move it further. Right, and it was
     * reading the wrong number: [value] is the caller's and comes back through a
     * recomposition, so inside one gesture it is always one emit behind. The
     * `maxOf` was therefore comparing against `dragged_previous + gap`, which on
     * the way back is *below* the current position — so the pushed thumb came
     * down one step behind the thumb doing the pushing and the two never
     * separated. Measured on a `0f..10f` range: collide at 7.26, reverse 80px,
     * and the pushed thumb had followed all the way back to 6.00 with the pair
     * still exactly `gap` apart.
     *
     * [emitted] is what this gesture last produced, so the comparison is against
     * where the thumb actually is. It is cleared at the end of the gesture, so
     * anything the caller does to the value between gestures is honoured — a
     * caller that clamps *during* one is the case this deliberately does not
     * chase, because a gesture that argues with itself mid-drag is the thing
     * being fixed.
     */
    fun emit(thumb: Thumb, rawFraction: Float) {
        val next = snap(rawFraction)
        tick(next)
        val current = emitted ?: value
        val updated = when (thumb) {
            Thumb.Start -> {
                val start = next.coerceIn(valueRange.start, valueRange.endInclusive - gap)
                start..maxOf(current.endInclusive, start + gap)
                    .coerceAtMost(valueRange.endInclusive)
            }
            Thumb.End -> {
                val end = next.coerceIn(valueRange.start + gap, valueRange.endInclusive)
                minOf(current.start, end - gap).coerceAtLeast(valueRange.start)..end
            }
            Thumb.None -> return
        }
        emitted = updated
        currentOnValueChange(updated)
    }

    // Everything below mirrors `Slider` except where having two thumbs makes that
    // impossible — the weld, the painter order, and which thumb the band belongs
    // to. Two sliders in one library that answer the same gesture differently is
    // worse than either of them being wrong, and the one place that drifted is
    // worth naming rather than burying: the squash was gated on `activeThumb`,
    // which `onEnd` clears, so the band's spring home was computed and never
    // drawn. See [bandThumb].
    val detented = steps > 0 && !motion.reduceMotion

    /** The detent one thumb is on, pulled toward the finger if it is the one being dragged. */
    fun targetFor(thumb: Thumb, base: Float): Float =
        if (detented && carrying && activeThumb == thumb) {
            base + (dragFraction - base) * SliderDefaults.DetentPull
        } else {
            base
        }

    val startSettled = animateFloatAsState(
        targetValue = targetFor(Thumb.Start, startFraction),
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "rangeStartDetent",
    )
    val endSettled = animateFloatAsState(
        targetValue = targetFor(Thumb.End, endFraction),
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "rangeEndDetent",
    )

    /**
     * See `Slider`'s `tapEased`: a tapped thumb travels, a dragged one tracks.
     *
     * Per thumb, not per gesture. Snapping the spec for the whole control while
     * *any* drag was in progress meant the thumb being **pushed** snapped too —
     * arriving at its new position with no travel, so nothing lagged and nothing
     * could stretch. Only the thumb under the finger should track it exactly;
     * the one being shoved is travelling like any other thumb that was moved by
     * something other than a finger.
     */
    fun tapSpec(thumb: Thumb) =
        if (carrying && activeThumb == thumb) {
            snapSpec()
        } else {
            motion.springOrTween<Float>(motion.springSnappy)
        }

    val startTapEased = animateFloatAsState(
        targetValue = startFraction,
        animationSpec = tapSpec(Thumb.Start),
        label = "rangeStartTap",
    )
    val endTapEased = animateFloatAsState(
        targetValue = endFraction,
        animationSpec = tapSpec(Thumb.End),
        label = "rangeEndTap",
    )

    fun drawn(thumb: Thumb, base: Float, settled: Float, tapEased: Float): Float = when {
        detented -> settled.coerceIn(0f, 1f)
        // Only the thumb under the finger tracks it exactly; the other one is
        // standing still and may as well ease if something moved it.
        carrying && activeThumb == thumb -> base
        else -> tapEased.coerceIn(0f, 1f)
    }


    /**
     * The two, drawn in contact.
     *
     * A pushed thumb reaches its new value immediately and its *spring* does
     * not, and against a fast drag that spring falls a long way behind — far
     * enough that the thumb doing the pushing catches up with the drawn position
     * of the one it is pushing and the two merge into a single blob halfway
     * along the track. The reported range was correct the whole time and every
     * assertion about it passed; it only showed up in a filmstrip.
     *
     * So the pushed thumb is drawn no closer than the separation it is entitled
     * to, and keeps its lag as *stretch* instead — see [reach] below, which is
     * measured against the spring rather than against this. It rides in contact
     * and elongates in the direction it is being shoved, which is what being
     * shoved looks like.
     */
    val gapFraction = if (span == 0f) 0f else gap / span

    /**
     * Whether the dragged thumb is currently shoving the other one along.
     *
     * Answered from the *values*, which are exact, rather than from the drawn
     * positions, which are two springs. `startFraction` sits at `endFraction -
     * gapFraction` for exactly as long as the end thumb is pushing the start
     * one.
     *
     * The tolerance is **float noise and nothing else**. It used to be
     * `CoincidenceEpsilon`, a per-cent of the track, which is most of a step on a
     * ten-notch slider — so the weld engaged before the thumbs were in contact
     * and held on after they had parted. Removing it outright is the other
     * mistake, and the suite caught it: both sides come through a division, so on
     * about one frame in twenty-five the difference lands a hair above the gap,
     * the weld drops for that frame alone, and the pushed thumb is drawn at its
     * lagging spring — which is the two merging into one shape. Sized to the
     * arithmetic instead: well under a pixel on any real track, and orders above
     * the error.
     */
    val pushing = activeThumb != Thumb.None &&
        endFraction - startFraction <= gapFraction + ContactTolerance

    /**
     * Where each thumb is drawn this frame, and how it is deformed.
     *
     * A function, called from the draw pass. Every input that changes frame by
     * frame — the springs, the band — is a state read here, so a settling or
     * springing thumb repaints without recomposing the slider; read in the body
     * as they used to be, every frame of every spring recomposed all of it.
     */
    fun drawnNow(): RangeDrawn {
        val easedStart = drawn(Thumb.Start, startFraction, startSettled.value, startTapEased.value)
        val easedEnd = drawn(Thumb.End, endFraction, endSettled.value, endTapEased.value)

        /**
         * The two, drawn as one body while they are in contact.
         *
         * This used to be a `minOf` against the pushed thumb's own spring, which
         * kept them from merging but left the drawn position switching between two
         * curves with different dynamics: the spring while it lagged, the contact
         * clamp while it did not. Every time the finger paused, the spring caught
         * up, overshot past the clamp and took over — so the thumb being pushed
         * broke contact, rang, and was recaptured. That is the jumping around.
         *
         * A shoved thumb has no dynamics of its own. While it is in contact it is
         * welded to the one doing the shoving and drawn a gap away from it, and it
         * goes back to its own spring the moment the range opens again.
         *
         * **The weld holds it away and never pulls it back**, which is the second
         * half and was missing. Written as an assignment, the pushed thumb sat at
         * exactly `dragged ± gap` — and `easedStart` follows the finger continuously
         * through `DetentPull`, in *both* directions. So reversing the drag dragged
         * the pushed thumb home with it, sub-step, until the snapped value finally
         * crossed a whole notch and let go. Reported as the two being glued together
         * until the dragged one got a tick clear, which is exactly what it was.
         *
         * A `minOf` was tried once before and reverted because the pushed thumb's
         * spring could overshoot past the clamp and take the drawing back, so it
         * broke contact and rang. What makes it hold this time is the line above:
         * `pushing` is a question about the values, and it now asks it without an
         * epsilon, so the weld engages and releases on the same frame the values do
         * rather than a per-cent of the track early and late.
         */
        val drawnStart = if (activeThumb == Thumb.End && pushing) {
            minOf(easedStart, easedEnd - gapFraction).coerceIn(0f, 1f)
        } else {
            easedStart
        }
        val drawnEnd = if (activeThumb == Thumb.Start && pushing) {
            maxOf(easedEnd, easedStart + gapFraction).coerceIn(0f, 1f)
        } else {
            easedEnd
        }

        /**
         * How far a thumb is from where it is being taken. See `sliderThumb`.
         *
         * The **finger** for the thumb under it — the detent strain, which holds for
         * as long as the finger is held between two notches — and the animation's
         * target for the other one, which is the distance it still has to travel
         * while it is being pushed. Two sources, one quantity, and neither case has
         * to know about the other.
         */
        fun reach(thumb: Thumb, base: Float, drawnAt: Float): Float = when {
            carrying && activeThumb == thumb -> dragFraction - drawnAt
            detented -> targetFor(thumb, base) - drawnAt
            else -> base - drawnAt
        }

        // Against the spring, not against the contact-clamped position above: the
        // lag is exactly the signal, and clamping it away would leave nothing to
        // stretch by at the moment there is most to stretch about.
        val ownReachStart = reach(Thumb.Start, startFraction, easedStart)
        val ownReachEnd = reach(Thumb.End, endFraction, easedEnd)

        // Welded thumbs deform alike. A shoved thumb drawn rigidly against its
        // neighbour but stretching on its own spring is the same two-dynamics
        // problem one level down — the position stopped ringing and the shape
        // carried on. It takes the strain of the thumb pushing it instead.
        val reachStart = if (activeThumb == Thumb.End && pushing) ownReachEnd else ownReachStart
        val reachEnd = if (activeThumb == Thumb.Start && pushing) ownReachStart else ownReachEnd

        // The end stop's squash belongs to the thumb that ran into the wall, and to
        // that one only — the other has not hit anything. [bandThumb] rather than
        // `activeThumb`: the band outlives the gesture by exactly the length of its
        // own spring home, and gating on the gesture threw that away.
        val squashStart = if (bandThumb == Thumb.Start) band.offset else 0f
        val squashEnd = if (bandThumb == Thumb.End) band.offset else 0f

        return RangeDrawn(drawnStart, drawnEnd, reachStart, reachEnd, squashStart, squashEnd)
    }

    Box(
        modifier = modifier
            .semantics {
                isTraversalGroup = true
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                // The thumbs each say so too, but the group is what a caller
                // tags and what the contract suite reads: a disabled control
                // whose own node does not announce it is a control that looks
                // available to everything except the two nodes inside it.
                if (!enabled) disabled()
            }
            .minimumTouchTarget()
            .focusRing(interactions, Theme.shapes.small)
            .fillMaxWidth()
            .height(SliderHeight)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(SliderHeight)) {
            // Held back from each end so a thumb is not clipped there — but
            // arithmetically, not as a `padding` with the gestures inside it.
            // See `Slider`, where that layout cost the outer 18dp of the control
            // its ability to be touched at all, which is precisely where a thumb
            // sits at either end of the range.
            val insetPx = with(density) { SliderThumbReach.toPx() }
            val widthPx = (with(density) { maxWidth.toPx() } - insetPx * 2f).coerceAtLeast(1f)

            fun toFraction(x: Float): Float {
                val along = (x - insetPx) / widthPx
                return if (layoutDirection == LayoutDirection.Rtl) 1f - along else along
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SliderHeight)
                    // One gesture, owning the pointer — the same mechanism
                    // `Slider` uses, for the same reason. A `draggable` lets the
                    // vertical component of every change through to whatever is
                    // scrolling above it, so a finger that wanders off the track
                    // hands the gesture away mid-drag. See `horizontalDragOwning`.
                    .pointerCursor(enabled = enabled)
                    .horizontalDragOwning(
                        enabled = enabled,
                        interactionSource = interactions,
                        scope = scope,
                        onStart = { start ->
                            val f = toFraction(start.x).coerceIn(0f, 1f)
                            pressFraction = f
                            val toStart = abs(f - startFraction)
                            val toEnd = abs(f - endFraction)
                            activeThumb = when {
                                // Equidistant means coincident, or a press
                                // exactly between them. Defer to the first delta.
                                abs(toStart - toEnd) < CoincidenceEpsilon -> Thumb.None
                                toStart < toEnd -> Thumb.Start
                                else -> Thumb.End
                            }
                            // A press picks a thumb. It does not move one.
                            //
                            // `Slider` brings its thumb to the finger, and that
                            // is right where there is one thumb and therefore
                            // one answer. With two, "the nearer thumb comes to
                            // the finger" means every press between them drags
                            // one inwards — and near the midpoint a pixel either
                            // way picks a *different* one, so pressing to start a
                            // drag would send whichever thumb you were not
                            // aiming at off to meet your finger. Measured on a
                            // `3f..5f` range with no movement at all: a press at
                            // 0.45 of the track moved the end thumb from 5.00 to
                            // 4.43, and one at 0.65 moved it to 6.69.
                            //
                            // A range is two values, and narrowing one of them
                            // without being asked is destructive in a way that
                            // moving a single slider's only thumb is not. So the
                            // thumb still comes to the finger — on the first
                            // *delta*, below, which is where a tap and a drag
                            // become distinguishable — and a press on its own
                            // changes nothing.
                            dragFraction = when (activeThumb) {
                                Thumb.Start, Thumb.End -> f
                                Thumb.None -> Float.NaN
                            }
                            carrying = false
                            emitted = null
                            endStop.arm()
                            // Already together is not a meeting: a drag that
                            // starts in contact has to part them first.
                            bump.arm(resting = if (endFraction - startFraction - gapFraction <= ContactTolerance) 1 else 0)
                        },
                        onDelta = { delta ->
                            val signed = if (layoutDirection == LayoutDirection.Rtl) -delta else delta
                            // The thumbs can start coincident — a zero-width
                            // range, which is exactly what "no filter yet" looks
                            // like. Distance alone cannot tell them apart there,
                            // and picking either one arbitrarily leaves the range
                            // able to open in one direction only. The first
                            // delta's sign is the answer the user just gave.
                            //
                            // A zero delta carries no direction, and the first
                            // one after touch is routinely exactly zero.
                            // Treating it as "not negative" silently picks the
                            // end thumb, which is right half the time — and now
                            // that the thumbs push rather than block, the wrong
                            // half opens the range in the direction nobody
                            // asked for.
                            carrying = true
                            if (activeThumb == Thumb.None && signed != 0f) {
                                activeThumb = if (signed < 0f) Thumb.Start else Thumb.End
                                dragFraction = if (activeThumb == Thumb.Start) startFraction else endFraction
                            }
                            if (activeThumb != Thumb.None) {
                                val from = if (dragFraction.isNaN()) {
                                    if (activeThumb == Thumb.Start) startFraction else endFraction
                                } else {
                                    dragFraction
                                }
                                // Clamped to what this thumb can actually
                                // reach, not to the whole track.
                                //
                                // `emit` holds the start thumb at
                                // `valueRange.endInclusive - gap` and the end
                                // thumb at `valueRange.start + gap`, so with a
                                // `minDistance` the last `gap` of track is
                                // unreachable — and the accumulator was still
                                // climbing into it. Every pixel of finger spent
                                // up there had to be spent again on the way back
                                // before anything moved: measured at 56px of
                                // dead travel at `minDistance = 1f` on a
                                // `0f..10f` range, and past 80px at 2f. Zero at
                                // the default, which is why nothing caught it.
                                val reach = when (activeThumb) {
                                    Thumb.Start -> 0f to (1f - gapFraction)
                                    else -> gapFraction to 1f
                                }
                                val offered = signed - band.payBack(signed)
                                val raw = from + offered / widthPx
                                dragFraction = raw.coerceIn(reach.first, reach.second)
                                // Measured against the **track**, not against
                                // `reach`: running into the other thumb is a
                                // shove, reported as one below, and only the
                                // ends of the range are a wall.
                                //
                                // One gap in that, noted rather than fixed: with
                                // a `minDistance`, a thumb's own ceiling is
                                // `1f - gapFraction` while this is measured
                                // against 1, so pushing into a neighbour that is
                                // *itself* at the track's end refuses nothing and
                                // gives nothing. The stop is real — the neighbour
                                // cannot move either — and it is the one place a
                                // wall on this control has no band on it.
                                val past = when {
                                    raw > 1f -> raw - 1f
                                    raw < 0f -> raw
                                    else -> 0f
                                }
                                endStop.at(raw)
                                if (past != 0f && !motion.reduceMotion) {
                                    // The band is this thumb's until it has
                                    // sprung all the way home. See [bandThumb].
                                    bandThumb = activeThumb
                                    band.pull(past * widthPx, thumbSquashPx)
                                }
                                emit(activeThumb, dragFraction)
                                if (steps <= 0) texture.at(dragFraction)
                                // From the values just emitted, which are exact,
                                // as `pushing` is: in contact while the pair is
                                // no more than the gap apart.
                                emitted?.let { now ->
                                    val apart = fractionOf(now.endInclusive) - fractionOf(now.start)
                                    bump.touching(apart - gapFraction - ContactTolerance)
                                }
                            }
                        },
                        onEnd = {
                            scope.launch {
                                // **Slower than the stretch, and that is the whole
                                // fix.** The thumb's drawn width is
                                // `width·(1−pull) + 1.5r·pull`, so a squash that
                                // unwinds while `width` is still a stretched capsule
                                // takes the thumb *out* through the full pill on its way
                                // home — reported as "snapping to the full-width pill
                                // before animating back".
                                //
                                // Matching the two springs was the obvious answer and it
                                // is not enough: traced frame by frame, a shared spring
                                // still peaks 9.8% over resting size, because `width`
                                // starts at `2r·1.25·1.5` and the lerp between a wide
                                // number and a narrow one bulges in the middle whatever
                                // rate they share.
                                //
                                // Letting the stretch get home *first* removes the bulge
                                // rather than shrinking it. Once `width` is back to `2r`
                                // the same expression reads `2r·(1−pull) + 1.5r·pull`,
                                // which climbs from the squashed size to the resting one
                                // and cannot exceed it. `springGentle` is 300 against
                                // `springSnappy`'s 1400 — a little over twice as slow,
                                // and critically damped, so the squash eases out from
                                // under a wall that is no longer being pushed.
                                band.release(motion.springOrTween(motion.springGentle))
                                // Only now. Released before the spring finished —
                                // which is what gating on `activeThumb` amounted
                                // to — the squash is computed and never drawn.
                                bandThumb = Thumb.None
                            }
                            // A press that never moved is a tap, and a tap
                            // moves the nearer thumb to it.
                            //
                            // This is where that belongs, and it is where it now
                            // happens for *every* tap rather than only for one
                            // on a collapsed range. Doing it in `onStart`
                            // committed to an answer before the gesture had said
                            // whether it was a tap at all, so a press that was
                            // about to become a drag sent a thumb — sometimes
                            // the wrong one, near the midpoint — off to meet the
                            // finger first. Deferred here, a drag never jumps and
                            // a tap behaves exactly as it did.
                            //
                            // Ties still go to the start thumb, as they did when
                            // the tap was a separate detector: on a collapsed
                            // range no thumb was ever chosen, and one of them has
                            // to answer.
                            if (!carrying && !pressFraction.isNaN()) {
                                emit(
                                    if (activeThumb == Thumb.None) Thumb.Start else activeThumb,
                                    pressFraction,
                                )
                            }
                            ticker.reset()
                            endStop.reset()
                            bump.reset()
                            texture.reset()
                            dragFraction = Float.NaN
                            pressFraction = Float.NaN
                            carrying = false
                            activeThumb = Thumb.None
                            emitted = null
                            currentFinished?.invoke()
                        },
                    )
                    .drawWithCache {
                        val trackHeightPx = SliderTrackHeight.toPx()
                        val tickPx = tickSize.toPx()
                        val tickHeightPx = SliderTickHeight.toPx()
                        val thumbRadiusPx = SliderThumbRadius.toPx()
                        val thumbReachPx = SliderThumbReach.toPx()
                        val centreY = size.height / 2f
                        val trackTop = centreY - trackHeightPx / 2f
                        val activeColour = colours.indicator(enabled)
                        val inactiveColour = colours.track(enabled)

                        onDrawBehind {
                            // The thumb's reach, not its radius — see the
                            // note in `Slider`. Must match the pointer maths.
                            val trackLeft = thumbReachPx
                            val trackWidth = (size.width - thumbReachPx * 2f).coerceAtLeast(0f)
                            // Mirrored right to left, as the pointer maths
                            // always was — see `Slider`.
                            val now = drawnNow()
                            val startX = trackLeft + trackWidth * (if (rtl) 1f - now.start else now.start)
                            val endX = trackLeft + trackWidth * (if (rtl) 1f - now.end else now.end)
                            val bandLeft = minOf(startX, endX)
                            val bandRight = maxOf(startX, endX)
                            val sense = if (rtl) -1f else 1f

                            drawRoundRect(
                                color = inactiveColour,
                                topLeft = Offset(trackLeft, trackTop),
                                size = Size(trackWidth, trackHeightPx),
                                cornerRadius = CornerRadius(trackHeightPx / 2f),
                            )
                            // The band between the thumbs, which is the value —
                            // on a plain slider the filled part runs from the
                            // start of the track, and here it does not.
                            drawRoundRect(
                                color = activeColour,
                                topLeft = Offset(bandLeft, trackTop),
                                size = Size(bandRight - bandLeft, trackHeightPx),
                                cornerRadius = CornerRadius(trackHeightPx / 2f),
                            )

                            if (showTicks) {
                                sliderTicks(
                                    trackLeft = trackLeft,
                                    trackWidth = trackWidth,
                                    centreY = centreY,
                                    steps = steps,
                                    minorTicks = minorTicks,
                                    widthPx = tickPx,
                                    heightPx = tickHeightPx,
                                    coveredColour = colours.tickOnIndicator,
                                    uncoveredColour = colours.tick,
                                    // The band between the thumbs, not the run
                                    // up to one, which is why the shared drawing
                                    // takes a predicate. Not the *only*
                                    // difference from `Slider`: the painter order
                                    // below and [bandThumb] are the others.
                                    covered = { x -> x in bandLeft..bandRight },
                                )
                            }

                            val startThumb =
                                DrawnThumb(
                                    startX,
                                    now.reachStart * trackWidth * sense,
                                    now.squashStart * sense,
                                    startScale.value,
                                    startAspect.value,
                                )
                            val endThumb =
                                DrawnThumb(
                                    endX,
                                    now.reachEnd * trackWidth * sense,
                                    now.squashEnd * sense,
                                    endScale.value,
                                    endAspect.value,
                                )

                            // Painter order is the whole of "which one can I
                            // see", so the one under the finger goes last.
                            //
                            // This was a literal `listOf(start, end)`, which
                            // put the end thumb on top always. At
                            // `minDistance = 0` a shoved thumb is welded to the
                            // pusher and drawn at exactly the same place, so
                            // dragging the start thumb past the end one left
                            // the held thumb — the larger of the two, because
                            // it is the one scaled up — behind a smaller circle
                            // that stamped its ring across it. Measured on the
                            // row through both centres: three runs of fill,
                            // a 40px middle belonging to the thumb nobody is
                            // touching and a 21px crescent of the held one
                            // showing either side of it.
                            //
                            // `Thumb.None` at rest, so the resting order is
                            // unchanged and no render moves. Two calls rather
                            // than a list of the two, which was a list a frame.
                            fun paint(drawn: DrawnThumb) {
                                sliderThumb(
                                    centreX = drawn.x,
                                    centreY = centreY,
                                    radiusPx = thumbRadiusPx,
                                    scale = drawn.scale,
                                    aspect = drawn.aspect,
                                    reachPx = drawn.reach,
                                    squashPx = drawn.squash,
                                    ringColour = colours.thumbRing,
                                    fillColour = colours.thumb(enabled),
                                    ringPx = SliderThumbRing.toPx(),
                                    capsule = pill,
                                )
                            }
                            if (activeThumb == Thumb.Start) {
                                paint(endThumb)
                                paint(startThumb)
                            } else {
                                paint(startThumb)
                                paint(endThumb)
                            }

                            val held = when (labelled.thumb) {
                                Thumb.Start -> startThumb
                                Thumb.End -> endThumb
                                Thumb.None -> null
                            }
                            val labelShown = labelProgress.value
                            if (valueLabel != null && labelShown > 0f && held != null) {
                                sliderValueLabel(
                                    text = labelMeasurer.measure(
                                        valueLabel(if (held === startThumb) value.start else value.endInclusive),
                                        labelStyle,
                                    ),
                                    centreX = held.x,
                                    thumbTop = centreY - thumbRadiusPx * held.scale,
                                    progress = labelShown,
                                    scaleIn = !motion.reduceMotion,
                                    container = colours.valueLabel,
                                    paddingHorizontal = labelPaddingH,
                                    paddingVertical = labelPaddingV,
                                    gap = labelGap,
                                    shape = pill,
                                )
                            }
                        }
                    }
            )

            // Two adjustable nodes, one per thumb. They draw nothing — the
            // canvas above has already drawn both — and exist so assistive tech
            // has two things to adjust rather than one control with two values
            // it cannot name. One description of the range, said by both.
            val announcement = stateDescription?.invoke(value)
            ThumbSemantics(
                contentDescription = startContentDescription,
                current = value.start,
                // As far as a finger can take it, which is the end of the track
                // less the gap it has to leave — not "as far as the other thumb",
                // which is where it used to stop and no longer does.
                bounds = valueRange.start..(valueRange.endInclusive - gap)
                    .coerceAtLeast(valueRange.start),
                steps = steps,
                enabled = enabled,
                announcement = announcement,
                onSet = { emit(Thumb.Start, fractionOf(it)) },
            )
            ThumbSemantics(
                contentDescription = endContentDescription,
                current = value.endInclusive,
                bounds = (valueRange.start + gap)
                    .coerceAtMost(valueRange.endInclusive)..valueRange.endInclusive,
                steps = steps,
                enabled = enabled,
                announcement = announcement,
                onSet = { emit(Thumb.End, fractionOf(it)) },
            )
        }
    }
}

/**
 * One thumb's accessibility node.
 *
 * Its `bounds` are the same limits the drag has: the track's end, less whatever
 * separation the two thumbs must keep. Expressed where assistive tech can see
 * them, so "adjust to maximum" on the start thumb takes it as far as a finger
 * could — pushing the end thumb ahead of it — rather than inverting the range or
 * stopping somewhere a finger would not have stopped.
 */
@Composable
private fun ThumbSemantics(
    contentDescription: String,
    current: Float,
    bounds: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    announcement: String?,
    onSet: (Float) -> Unit,
) {
    Box(
        Modifier
            .minimumTouchTarget()
            .semantics {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = current.coerceIn(bounds.start, bounds.endInclusive),
                    range = bounds,
                    steps = steps,
                )
                if (announcement != null) stateDescription = announcement
                // Withheld rather than marked when disabled, for the same reason
                // as `Slider`: a disabled control assistive tech can still move
                // is a control whose value changes while it looks inert.
                if (enabled) {
                    setProgress { target ->
                        onSet(target.coerceIn(bounds.start, bounds.endInclusive))
                        true
                    }
                }
            }
    )
}

/**
 * One thumb's drawing, so the loop below reads as two thumbs rather than as a
 * list of quintuples. Each carries its own scale and aspect: a range slider has
 * two handles and only one of them is ever in your hand.
 *
 * [reach] and [squash] are separate for the reason `sliderThumb` gives: one
 * lengthens the thumb towards where it is trying to be and the other shortens it
 * against a wall it cannot pass, and they used to be added together.
 */
private class DrawnThumb(
    val x: Float,
    val reach: Float,
    val squash: Float,
    val scale: Float,
    val aspect: Float,
)

/** Both thumbs' drawn positions, as fractions, and their deformation. See `drawnNow`. */
private class RangeDrawn(
    val start: Float,
    val end: Float,
    val reachStart: Float,
    val reachEnd: Float,
    val squashStart: Float,
    val squashEnd: Float,
)

/** Which thumb a gesture is moving. */
private enum class Thumb { Start, End, None }

/** Which thumb the value label is over. Not state: only the draw reads it. */
private class LabelledThumb {
    var thumb: Thumb = Thumb.None
}

/**
 * How close two thumbs must be for a drag to defer to direction.
 *
 * A fraction of the track, not a distance in pixels: the question is whether the
 * finger is meaningfully nearer one thumb than the other, and on a 320dp track a
 * 1% difference is three pixels — inside anyone's aim.
 */
private const val CoincidenceEpsilon = 0.01f

/**
 * How far apart two fractions may be and still count as touching.
 *
 * A thousandth of a per-cent of the track — 0.07px on a 700px slider, so it can
 * never be seen, and four orders above the error left by dividing a value by its
 * range. Deliberately *not* [CoincidenceEpsilon], which answers a different
 * question — which thumb a press belongs to — and is a per-cent of the track
 * because a finger is.
 */
private const val ContactTolerance = 1e-4f
