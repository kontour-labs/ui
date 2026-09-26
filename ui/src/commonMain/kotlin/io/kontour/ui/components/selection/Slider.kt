package io.kontour.ui.components.selection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap as snapSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.rememberRubberBand
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.interaction.rememberEndStopLatch
import io.kontour.ui.interaction.horizontalDragOwning
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// Shared with `RangeSlider`, which is the same track with a second thumb on it.
// Two copies of these would be two sliders that drift apart by a pixel.
internal val SliderTrackHeight = 4.dp
internal val SliderThumbRadius = 12.dp

/**
 * How far a detent mark crosses the track.
 *
 * Ten against a 4dp track, so a mark stands proud on both sides and reads as
 * *crossing* the track rather than as sitting on it — which is the whole reason
 * these are bars now and not dots. Still well inside the 24dp thumb that is
 * painted over the top.
 *
 * Here rather than on `ComponentDefaults` for the reason its neighbours are: it
 * is the shape of a mark, not a dimension a consumer sets.
 */
internal val SliderTickHeight = 10.dp

/**
 * Half the thumb's resting width — the distance its edge reaches from its centre.
 *
 * The track is inset by this at each end so the thumb has room to sit at 0 and
 * at 1 without being clipped by the control's own bounds. It was
 * [SliderThumbRadius], which was the same number while the thumb was a circle
 * and is 1.5× short of it now that it is a capsule.
 */
internal val SliderThumbReach = SliderThumbRadius * SliderDefaults.ThumbAspect
internal val SliderHeight = 44.dp

/**
 * A slider over a continuous or stepped range.
 *
 * ```
 * Slider(
 *     value = walkSpeed,
 *     onValueChange = viewModel::setWalkSpeed,
 *     valueRange = 2f..6f,
 *     stateDescription = { "${it.roundToInt()} km/h" },
 * )
 * ```
 *
 * The thumb grows while dragged and settles back with a bounce on release. Each
 * step **dragged** across on a stepped slider fires a tick haptic, so a user changing a
 * value without looking can feel the detents — which is most of the point of
 * having steps at all.
 *
 * @param steps Number of discrete stops *between* the ends. `0` is continuous.
 *   A 1–5 rating is `steps = 3`.
 * @param stateDescription Turns the raw value into something a screen reader can
 *   say. Without it the announcement is a bare percentage, which is rarely what
 *   the number means. Strongly recommended.
 * @param onValueChangeFinished Called when the drag ends — for committing a
 *   value that is expensive to apply on every frame.
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
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
    /**
     * What the slider is *of*, when nothing beside it says.
     *
     * `null` — the default — is right whenever a label sits next to it, which
     * is the common case and why this is not required. A slider on its own
     * announces as an unnamed slider without it, and until now there was no
     * parameter to fix that with: [RangeSlider] could name both its thumbs and
     * this could name nothing.
     */
    contentDescription: String? = null,
    stateDescription: ((Float) -> String)? = null,
    /**
     * The value, in a bubble above the head while it is held.
     *
     * Asked for as "the option to display a label above the head as you're
     * dragging it". Off unless given: most sliders sit beside a number that
     * already says what they are set to. Shown from the moment the head is
     * pressed until it is let go, and drawn outside the slider's own bounds — so
     * give the slider room above it, or a clipping parent will cut the bubble.
     */
    valueLabel: ((Float) -> String)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    // An inverted range has no reading, and the failure it used to cause was
    // invisible: `coerceIn` throws on one, and the call that reached it first was
    // inside the `setProgress` **semantics action** — so no screenshot could see
    // it, no gesture could reach it, and the first person to find it would be
    // using a screen reader or an automated accessibility check.
    require(valueRange.start <= valueRange.endInclusive) {
        "Slider was given an inverted valueRange " +
            "(${valueRange.start}..${valueRange.endInclusive}). The start has to be at " +
            "or below the end; a range built from two computed bounds can invert when " +
            "the data behind them is empty or out of order."
    }

    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val colours = Theme.colours
    val motion = Theme.motion
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val dragged by interactions.collectIsDraggedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val active = dragged || pressed

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
    val thumbReturn = motion.springOrTween<Float>(
        if (active) motion.springBouncy else motion.springSnappy
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (active && !motion.reduceMotion) 1.25f else 1f,
        animationSpec = thumbReturn,
        label = "sliderThumb",
    )

    // A circle at rest that lengthens into a capsule while it is held. Shares
    // `active` and the spring with the scale above, so the thumb grows and
    // stretches as one gesture rather than two overlapping ones.
    val thumbAspect by animateFloatAsState(
        targetValue = if (active && !motion.reduceMotion) SliderDefaults.ThumbAspect else 1f,
        animationSpec = thumbReturn,
        label = "sliderThumbAspect",
    )


    val range = valueRange.endInclusive - valueRange.start

    // The label above the head, measured in composition and drawn in the draw
    // pass. See `sliderValueLabel`.
    val labelMeasurer = rememberTextMeasurer()
    val labelStyle = Theme.typography.labelMedium.copy(color = colours.onSurfaceInverse)
    val labelProgress by animateFloatAsState(
        targetValue = if (valueLabel != null && active) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "sliderValueLabel",
    )
    val labelPaddingH = with(density) { Theme.spacing.xs.toPx() }
    val labelPaddingV = with(density) { Theme.spacing.xxs.toPx() }
    val labelGap = with(density) { SliderLabelGap.toPx() }
    // The switch's pill: round at rest, a G2 pill stretched. See `sliderThumb`.
    val pill = Theme.shapes.pill
    val rtl = layoutDirection == LayoutDirection.Rtl
    val fraction = if (range == 0f) 0f else ((value - valueRange.start) / range).coerceIn(0f, 1f)

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentFinished by rememberUpdatedState(onValueChangeFinished)

    // Once per step dragged across, not once per frame while the thumb sits on a
    // step — and no faster than a hand can tell two ticks apart, which is the
    // ticker's own rate limit rather than anything this component decides.
    val ticker = rememberDetentTicker()

    /**
     * What a drag pushing past either end of the range does instead of nothing.
     *
     * Scaled to the **thumb**, not to the track: the limit is the thumb's own
     * maximum deformation, so the squash is the same proportion of itself on a
     * 60dp slider and a 600dp one, and it feeds the reach the thumb already
     * stretches by.
     */
    val band = rememberRubberBand()
    // [EndStopTravel] rather than a fraction of the thumb: the limit is how far
    // the *finger* goes past the stop, and a thumb-sized one was crossed inside
    // a frame. `sliderThumb` normalises by the same number.
    val thumbSquashPx = with(LocalDensity.current) { EndStopTravel.toPx() }

    // **An end stop reports once, when the drag runs into it.**
    //
    // It reported nothing for a while, on the argument that the thumb squashing
    // against the wall already says so. That lost to use: asked for as "a haptic
    // in standard mode to all sliders that fires when you hit the end stop". The
    // thumb is under the finger that is pushing it. Latched on the wall — see
    // `EndStopLatch` — so holding against it is one report, not a buzz.
    val endStop = rememberEndStopLatch()

    // Read here rather than inside `drawWithCache`, which is not a composable.
    val tickSize = Theme.componentDefaults.sliderTickSize

    /**
     * Where the finger actually is, in fractions of the track. `NaN` when no
     * drag is in progress.
     *
     * This is the whole of the drag fix. `draggable` calls `onDelta` once per
     * *pointer event* and `fraction` only refreshes once per *composition*, so
     * computing `fraction + delta` threw away every delta but the last one in
     * each frame — a 1000Hz mouse against 60fps frames kept about a
     * sixteenth of its travel. Accumulating here instead means every delta
     * lands.
     *
     * On a stepped slider it was worse than lossy, it was fatal: `snap` quantises
     * before the value goes back to the caller, so the sub-step remainder was
     * destroyed on every event and advancing one detent needed a *single* event
     * carrying half a step — 55dp on the catalog's slider, against real deltas of
     * a few pixels. It could not move at all.
     *
     * It is also what the detent feel is drawn from: the gap between this and the
     * snapped value is exactly how far the finger has pulled past the detent.
     */
    var dragFraction by remember { mutableFloatStateOf(Float.NaN) }

    /**
     * Whether the finger has actually *moved* since it went down.
     *
     * The value goes to wherever a press lands — that is what a slider is — but
     * the thumb only stops easing and starts tracking exactly once the finger
     * is carrying it. Without the distinction, taking ownership of the pointer
     * on the down (see `horizontalDragOwning`) also made every tap a drag of
     * length zero, and the thumb was simply at the far end of the track on the
     * next frame. `tapEased` below is the whole reason that is wrong: a tap has
     * nothing between one frame and the next to say which way the thumb went.
     *
     * So this, rather than "is `dragFraction` set", is what the four drawing
     * decisions below ask.
     */
    var carrying by remember { mutableStateOf(false) }

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(0f, 1f)
        if (steps <= 0) return valueRange.start + clamped * range
        val stepCount = steps + 1
        val snapped = (clamped * stepCount).roundToInt().toFloat() / stepCount
        return valueRange.start + snapped * range
    }

    // Only a stepped slider has detents to strain against. A continuous one
    // reads `fraction` straight through: there is nothing to land on, and easing
    // a value that already tracks the finger is only lag.
    val detented = steps > 0 && !motion.reduceMotion

    /**
     * Where the thumb is trying to be, which on a stepped slider is neither the
     * value nor the finger.
     *
     * It is the detent, pulled part of the way toward the finger by
     * [SliderDefaults.DetentPull]. The finger can be at most half a step past a
     * detent before [snap] moves on, so the thumb strains a little further from
     * the notch the longer the drag is held there, and lets go when it goes.
     * Pulling *all* the way would just be a continuous slider that reports
     * quantised values, which is the thing detents exist not to be.
     *
     * The pull is folded into the animation's **target**, not added on top of its
     * output, and that is the whole of this fix. Added on top, the two terms move
     * in opposite directions the instant a detent is crossed: the finger is now
     * half a step *behind* the new detent, so the pull flips from `+0.225` of a
     * step to `−0.225` while the eased term is still sitting on the old detent it
     * has yet to leave. The thumb jumped back most of half a step and then
     * animated the whole way forward — the reported "snaps back before it
     * advances". Inside the target, crossing a detent moves the target forward
     * and only forward, so the thumb carries on from wherever it had got to.
     */
    val thumbTarget = if (detented && carrying) {
        fraction + (dragFraction - fraction) * SliderDefaults.DetentPull
    } else {
        fraction
    }

    val settled by animateFloatAsState(
        targetValue = thumbTarget,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "sliderDetent",
    )

    /**
     * A continuous slider's thumb, which eases to a tap and tracks a drag.
     *
     * Tapping a track used to teleport the thumb — the value is the value, and
     * there is nothing between one frame and the next to say it travelled.
     * Springing it is what turns "the number changed" into "the thumb went
     * there", and on a slider that is the whole of the feedback.
     *
     * The spec becomes `snap()` while a finger is down, because a thumb that
     * eases toward the finger holding it reads as lag rather than as polish. And
     * `drawnFraction` below takes the raw value during a drag anyway, so this is
     * only kept in step so that letting go does not hand the thumb back to a
     * stale animation — the mistake stage 1 found in the detents.
     */
    val tapEased by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (carrying) snapSpec() else motion.springOrTween(motion.springSnappy),
        label = "sliderTap",
    )

    // Coerced because `springSnappy` is underdamped and a thumb that overshoots
    // the end of its own track reads as a bug rather than as bounce.
    val drawnFraction = when {
        detented -> settled.coerceIn(0f, 1f)
        !carrying -> tapEased.coerceIn(0f, 1f)
        else -> fraction
    }

    /**
     * How far the thumb is from where it is being taken. See `sliderThumb`.
     *
     * The **finger** while there is one, and the animation's target otherwise.
     * Not the target in both cases, which was the first attempt: `settled` is a
     * spring converging on `thumbTarget`, so the gap between them decays to zero
     * within a few frames and a drag *held* between two detents — the whole
     * situation the strain is supposed to depict — would sit there perfectly
     * round. Against the finger it holds for as long as the finger does, and
     * lets go when the detent does.
     *
     * A continuous drag has the two coincident and stays round, which is right:
     * a thumb pinned to the finger is not straining against anything.
     */
    val thumbReach =
        if (carrying) dragFraction - drawnFraction else thumbTarget - drawnFraction

    /**
     * The value, snapped, with a detent tick if a drag just crossed one.
     *
     * `carrying` is the whole of the condition, and it is the difference between
     * a slider that reports *travel* and one that reports *touch*. Pressing a
     * stepped track lands on a detent, and that used to fire — so did letting go
     * on the next one, from `onEnd`'s reset. Two buzzes for a gesture that
     * crossed nothing. A tap is a tap: the value it sets is not a step the finger
     * felt on the way past.
     *
     * The index is still recorded on a tap, and has to be. Without it the ticker
     * would still be unarmed when the drag began, and the first pixel of
     * movement would fire a tick for the detent the finger is already standing
     * on.
     */
    fun emit(newFraction: Float) {
        val next = snap(newFraction)
        if (steps > 0) {
            val index = ((next - valueRange.start) / range * (steps + 1)).roundToInt().toFloat()
            // Through the shared ticker rather than the hand-rolled guard this
            // used to keep. `DetentTicker` already described itself as the thing
            // six components including this one had drifted apart from, which
            // was only true of five of them; and it is where the rate limit
            // lives, which a slider needs as much as a wheel does. A flick
            // across a fifty-step slider crosses a detent every few
            // milliseconds, and a tick is twenty milliseconds of motor time.
            //
            // A tap arms it instead of firing: `carrying` is false until the
            // first delta, and a value set by landing on it has crossed nothing.
            if (carrying) ticker.at(index) else { ticker.reset(); ticker.at(index) }
        }
        currentOnValueChange(next)
    }

    BoxWithConstraints(
        modifier = modifier
            .semantics {
                if (!enabled) disabled()
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = valueRange,
                    steps = steps,
                )
                if (stateDescription != null) this.stateDescription = stateDescription(value)
                // The action is *withheld* when disabled, not just marked. The
                // pointer path already returns early, so leaving `setProgress`
                // attached would mean a disabled slider that assistive tech can
                // still change — the value moving under a control that looks
                // inert.
                if (enabled) {
                    setProgress { target ->
                        currentOnValueChange(target.coerceIn(valueRange))
                        true
                    }
                }
            }
            .minimumTouchTarget()
            .focusRing(interactions, Theme.shapes.small)
            .fillMaxWidth()
            .height(SliderHeight)
    ) {
        /**
         * The thumb's radius, held back from each end so it is not clipped there.
         *
         * It used to be a `padding` modifier, and the gestures lived *inside* it.
         * So the outer [SliderThumbRadius] at each end of the control was dead to
         * touch — and that is exactly where the thumb sits at either end of the
         * range, which meant **half the thumb could not be grabbed** when the
         * value was at its minimum or its maximum. On a 120dp slider it is 18% of
         * the control, and it includes the two places a finger goes most often.
         *
         * Nothing could see it: every slider test presses in the middle, and a
         * still of a slider with a dead margin looks exactly like one without.
         * `NarrowGestureTest` found it by pressing 5% of the way in — which is
         * inside the margin below 220dp and outside it above.
         *
         * Arithmetic rather than layout now. The pointer handlers are on the
         * full-width box and subtract the inset themselves; the drawing insets
         * the track by the same amount, so nothing moved on screen.
         */
        val insetPx = with(density) { SliderThumbReach.toPx() }
        val widthPx = (with(density) { maxWidth.toPx() } - insetPx * 2f).coerceAtLeast(1f)

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                // The gesture box is taller than the control draws.
                //
                // Tapping the *bar* — which the thumb then animates to — was a
                // target as tall as the track plus the thumb, and on a phone
                // that is a thin ribbon to hit with a fingertip. The box takes
                // the touch minimum now and the drawing is unchanged, so the
                // slider looks identical and is a great deal easier to hit.
                .heightIn(min = Theme.sizing.minTouchTarget)
                .height(SliderHeight)
                // One gesture, owning the pointer.
                //
                // This was a `detectTapGestures` beside a `draggable`, and the
                // `draggable` let the vertical component of every change through
                // to whatever was scrolling above it. A finger that wandered off
                // the track while dragging handed the gesture to the parent and
                // the slider stopped following it — without the finger lifting.
                // See `horizontalDragOwning`.
                //
                // A tap falls out of the same gesture: pressing emits a value
                // and letting go finishes, with no movement in between.
                .pointerCursor(enabled = enabled)
                .horizontalDragOwning(
                    enabled = enabled,
                    interactionSource = interactions,
                    scope = scope,
                    onStart = { start ->
                        // The thumb comes to the finger, rather than the finger
                        // having to go and find the thumb. Pressing at 80% of a
                        // slider sitting at 20% and dragging used to move it
                        // from 20%, so the first part of every drag was spent
                        // catching up to where the press already was.
                        val along = (start.x - insetPx) / widthPx
                        val at = if (layoutDirection == LayoutDirection.Rtl) 1f - along else along
                        dragFraction = at.coerceIn(0f, 1f)
                        // Not carrying yet: the value is at the finger, and the
                        // thumb travels there. See [carrying].
                        carrying = false
                        endStop.arm()
                        emit(dragFraction)
                    },
                    onDelta = { delta ->
                        val signed = if (layoutDirection == LayoutDirection.Rtl) -delta else delta
                        // The gesture only becomes a drag here. `onDelta` is
                        // called on real horizontal movement and nothing else.
                        carrying = true
                        // Accumulate, then emit. Never the other way around: the
                        // emitted value is quantised and the caller may not take
                        // it at all, and either would lose the remainder.
                        val from = if (dragFraction.isNaN()) fraction else dragFraction
                        // A finger coming back closes the stretch it opened
                        // before the value starts moving again, or one gesture
                        // reads as two motions.
                        val offered = signed - band.payBack(signed)
                        val raw = from + offered / widthPx
                        dragFraction = raw.coerceIn(0f, 1f)
                        // Emitted first and always. A press at the very end of
                        // the track has to answer before anything is pulled, or
                        // the last pixel of the slider stops reporting.
                        emit(dragFraction)
                        // On the unclamped position, so it reports under reduced
                        // motion as well, where the band is switched off.
                        endStop.at(if (raw > 1f) 1 else if (raw < 0f) -1 else 0)
                        if (!motion.reduceMotion) {
                            band.pull((raw - dragFraction) * widthPx, thumbSquashPx)
                        }
                    },
                    onEnd = {
                        ticker.reset()
                        endStop.reset()
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
                        scope.launch { band.release(motion.springOrTween(motion.springGentle)) }
                        // Releasing hands the thumb back to the settled value, so
                        // it springs the last of the way onto the detent rather
                        // than staying wherever the finger let go.
                        dragFraction = Float.NaN
                        carrying = false
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
                    val activeColour = if (enabled) colours.primary else colours.contentDisabled
                    val inactiveColour = if (enabled) colours.outline else colours.surfaceSunken
                    val thumbColour = if (enabled) colours.primary else colours.contentDisabled

                    onDrawBehind {
                        // The track is inset by the thumb's *reach* at each
                        // end — how far its edge sits from its centre — so a
                        // thumb parked at 0 or 1 is not clipped by the control's
                        // own bounds. The box around it is not inset, because
                        // that is the hit area. This has to be the same number
                        // the pointer maths above uses, or a tap lands on a
                        // fraction the track does not draw at.
                        val trackLeft = thumbReachPx
                        val trackWidth = (size.width - thumbReachPx * 2f).coerceAtLeast(0f)
                        // **Mirrored right to left**, as the pointer maths above
                        // always was. The drawing was not: a right-to-left slider
                        // took a touch at its right-hand end as its minimum and
                        // then drew the thumb at its left.
                        val along = if (rtl) 1f - drawnFraction else drawnFraction
                        val thumbX = trackLeft + trackWidth * along
                        // The physical direction the value grows in, for the two
                        // signals below that are measured along the value.
                        val sense = if (rtl) -1f else 1f

                        drawRoundRect(
                            color = inactiveColour,
                            topLeft = Offset(trackLeft, trackTop),
                            size = Size(trackWidth, trackHeightPx),
                            cornerRadius = CornerRadius(trackHeightPx / 2f),
                        )
                        val filledFrom = if (rtl) thumbX else trackLeft
                        val filledTo = if (rtl) trackLeft + trackWidth else thumbX
                        drawRoundRect(
                            color = activeColour,
                            topLeft = Offset(filledFrom, trackTop),
                            size = Size(filledTo - filledFrom, trackHeightPx),
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
                                coveredColour = colours.onPrimary,
                                uncoveredColour = colours.contentSubtle,
                                covered = { x -> if (rtl) x >= thumbX else x <= thumbX },
                            )
                        }

                        sliderThumb(
                            centreX = thumbX,
                            centreY = centreY,
                            radiusPx = thumbRadiusPx,
                            scale = thumbScale,
                            aspect = thumbAspect,
                            reachPx = thumbReach * trackWidth * sense,
                            // The end stop, in its own channel. It used to be
                            // summed into the reach above, which made a thumb
                            // pushed into the end of the track grow backwards
                            // away from the wall — see `sliderThumb`.
                            squashPx = band.offset * sense,
                            // A ring of the page colour keeps the thumb legible
                            // where it overlaps the filled track.
                            ringColour = colours.surface,
                            fillColour = thumbColour,
                            ringPx = SliderThumbRing.toPx(),
                            capsule = pill,
                        )

                        if (valueLabel != null && labelProgress > 0f) {
                            sliderValueLabel(
                                text = labelMeasurer.measure(valueLabel(value), labelStyle),
                                centreX = thumbX,
                                thumbTop = centreY - thumbRadiusPx * thumbScale,
                                progress = labelProgress,
                                scaleIn = !motion.reduceMotion,
                                container = colours.surfaceInverse,
                                paddingHorizontal = labelPaddingH,
                                paddingVertical = labelPaddingV,
                                gap = labelGap,
                                shape = pill,
                            )
                        }
                    }
                }
        ) {}
    }
}

object SliderDefaults {
    /**
     * How far the thumb follows the finger past a detent, as a fraction of the
     * overshoot.
     *
     * `0f` is a thumb that teleports between notches; `1f` is a continuous
     * slider that happens to report quantised values. 0.45 is enough movement to
     * read as resistance without ever putting the thumb closer to the next
     * detent than to the one it is on.
     */
    const val DetentPull: Float = 0.45f

    /**
     * The furthest a thumb stretches, as a fraction of its own radius.
     *
     * Bounded because the signal driving it is not: a tap on the far end of the
     * track has a whole track's worth of travel still to go on its first frame,
     * and a thumb allowed to answer that in full would be a worm. 0.6 puts the
     * longest capsule at about one and a half thumbs, which reads as give.
     */
    const val MaxStretch: Float = 0.6f

    /**
     * How much wider than tall the thumb is at rest.
     *
     * 1.5 rather than a circle's 1.0: a round thumb reads as a dot sitting on
     * the track, and a capsule reads as a handle for it — and points along the
     * one axis the thumb travels on. Kept modest because the stretch on top of
     * it is what carries the sense of strain, and a thumb that starts long has
     * less room to say anything by getting longer.
     */
    const val ThumbAspect: Float = 1.5f
}

/** Between the bottom of a value label and the top of the head it belongs to. */
internal val SliderLabelGap = 6.dp

/** The page-coloured ring around a thumb. Constant, not scaled — see `sliderThumb`. */
internal val SliderThumbRing = 2.dp

/** Kept so callers can reserve the same height when laying out around a slider. */
val SliderVisualHeight = SliderHeight

private fun Float.coerceIn(range: ClosedFloatingPointRange<Float>): Float =
    coerceIn(range.start, range.endInclusive)
