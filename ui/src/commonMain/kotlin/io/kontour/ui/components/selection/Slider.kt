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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.horizontalDragOwning
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.roundToInt

// Shared with `RangeSlider`, which is the same track with a second thumb on it.
// Two copies of these would be two sliders that drift apart by a pixel.
internal val SliderTrackHeight = 4.dp
internal val SliderThumbRadius = 12.dp

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
 * step crossed on a stepped slider fires a tick haptic, so a user changing a
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
     * Whether to draw a dot on the track at each detent.
     *
     * Off by default, which is a change: [steps] used to imply them. A row of
     * dots turns a slider into a diagram of its own implementation, and on a
     * short track with many steps they merge into a dashed line that reads as
     * texture rather than as information. The detent is still there — the thumb
     * still resists and still ticks — it is just no longer drawn.
     *
     * Turn them on where the count is small and *is* the point: five ratings,
     * four zoom levels.
     */
    showTicks: Boolean = false,
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
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val colors = Theme.colors
    val motion = Theme.motion
    val feedback = Feedback
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val dragged by interactions.collectIsDraggedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val active = dragged || pressed

    val thumbScale by animateFloatAsState(
        targetValue = if (active && !motion.reduceMotion) 1.25f else 1f,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "sliderThumb",
    )


    val range = valueRange.endInclusive - valueRange.start
    val fraction = if (range == 0f) 0f else ((value - valueRange.start) / range).coerceIn(0f, 1f)

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentFinished by rememberUpdatedState(onValueChangeFinished)

    // Remembered so the tick haptic fires once per step crossed, not once per
    // frame while the thumb sits on a step.
    var lastStepIndex by remember { mutableFloatStateOf(Float.NaN) }

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
    val thumbTarget = if (detented && !dragFraction.isNaN()) {
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
        animationSpec = if (dragFraction.isNaN()) {
            motion.springOrTween(motion.springSnappy)
        } else {
            snapSpec()
        },
        label = "sliderTap",
    )

    // Coerced because `springSnappy` is underdamped and a thumb that overshoots
    // the end of its own track reads as a bug rather than as bounce.
    val drawnFraction = when {
        detented -> settled.coerceIn(0f, 1f)
        dragFraction.isNaN() -> tapEased.coerceIn(0f, 1f)
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
        if (dragFraction.isNaN()) thumbTarget - drawnFraction else dragFraction - drawnFraction

    fun emit(newFraction: Float) {
        val next = snap(newFraction)
        if (steps > 0) {
            val index = ((next - valueRange.start) / range * (steps + 1)).roundToInt().toFloat()
            if (lastStepIndex.isNaN() || abs(index - lastStepIndex) >= 1f) {
                feedback.perform(FeedbackIntent.Tick)
                lastStepIndex = index
            }
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
                        emit(dragFraction)
                    },
                    onDelta = { delta ->
                        val signed = if (layoutDirection == LayoutDirection.Rtl) -delta else delta
                        // Accumulate, then emit. Never the other way around: the
                        // emitted value is quantised and the caller may not take
                        // it at all, and either would lose the remainder.
                        val from = if (dragFraction.isNaN()) fraction else dragFraction
                        dragFraction = (from + signed / widthPx).coerceIn(0f, 1f)
                        emit(dragFraction)
                    },
                    onEnd = {
                        feedback.perform(FeedbackIntent.GestureEnd)
                        lastStepIndex = Float.NaN
                        // Releasing hands the thumb back to the settled value, so
                        // it springs the last of the way onto the detent rather
                        // than staying wherever the finger let go.
                        dragFraction = Float.NaN
                        currentFinished?.invoke()
                    },
                )
                .drawWithCache {
                    val trackHeightPx = SliderTrackHeight.toPx()
                    val thumbRadiusPx = SliderThumbRadius.toPx()
                    val thumbReachPx = SliderThumbReach.toPx()
                    val centreY = size.height / 2f
                    val trackTop = centreY - trackHeightPx / 2f
                    val activeColor = if (enabled) colors.primary else colors.contentDisabled
                    val inactiveColor = if (enabled) colors.outline else colors.surfaceSunken
                    val thumbColor = if (enabled) colors.primary else colors.contentDisabled

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
                        val thumbX = trackLeft + trackWidth * drawnFraction

                        drawRoundRect(
                            color = inactiveColor,
                            topLeft = Offset(trackLeft, trackTop),
                            size = Size(trackWidth, trackHeightPx),
                            cornerRadius = CornerRadius(trackHeightPx / 2f),
                        )
                        drawRoundRect(
                            color = activeColor,
                            topLeft = Offset(trackLeft, trackTop),
                            size = Size(thumbX - trackLeft, trackHeightPx),
                            cornerRadius = CornerRadius(trackHeightPx / 2f),
                        )

                        if (showTicks && steps > 0) {
                            val stepCount = steps + 1
                            for (i in 0..stepCount) {
                                val x = trackLeft + trackWidth * i / stepCount
                                drawCircle(
                                    color = if (x <= thumbX) colors.onPrimary else colors.contentSubtle,
                                    radius = trackHeightPx * 0.22f,
                                    center = Offset(x, centreY),
                                )
                            }
                        }

                        sliderThumb(
                            centreX = thumbX,
                            centreY = centreY,
                            radiusPx = thumbRadiusPx,
                            scale = thumbScale,
                            reachPx = thumbReach * trackWidth,
                            // A ring of the page colour keeps the thumb legible
                            // where it overlaps the filled track.
                            ringColor = colors.surface,
                            fillColor = thumbColor,
                            ringPx = SliderThumbRing.toPx(),
                        )
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

/** The page-coloured ring around a thumb. Constant, not scaled — see `sliderThumb`. */
internal val SliderThumbRing = 2.dp

/** Kept so callers can reserve the same height when laying out around a slider. */
val SliderVisualHeight = SliderHeight

private fun Float.coerceIn(range: ClosedFloatingPointRange<Float>): Float =
    coerceIn(range.start, range.endInclusive)
