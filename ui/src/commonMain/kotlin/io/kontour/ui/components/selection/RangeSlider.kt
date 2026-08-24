package io.kontour.ui.components.selection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap as snapSpec
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
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
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.roundToInt

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
    /** What the range is *of*. `null` when a label beside it already says. */
    contentDescription: String? = null,
    startContentDescription: String = Theme.strings.rangeStart,
    endContentDescription: String = Theme.strings.rangeEnd,
    stateDescription: ((ClosedFloatingPointRange<Float>) -> String)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
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
        label = "rangeSliderThumb",
    )

    val span = valueRange.endInclusive - valueRange.start

    /**
     * [minDistance], clamped to something the track can actually hold.
     *
     * A minimum wider than the range would put `valueRange.endInclusive - gap`
     * below `valueRange.start`, and `coerceIn` **throws** on an inverted range —
     * the same trap that took a frame down from inside `Switch`'s draw. A caller
     * that asks for more separation than exists gets the whole track instead.
     */
    val gap = minDistance.coerceIn(0f, span.coerceAtLeast(0f))

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

    /** See [Slider]'s `dragFraction`. `NaN` when no drag is in progress. */
    var dragFraction by remember { mutableFloatStateOf(Float.NaN) }
    var lastStepIndex by remember { mutableFloatStateOf(Float.NaN) }

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(0f, 1f)
        if (steps <= 0) return valueRange.start + clamped * span
        val stepCount = steps + 1
        val snapped = (clamped * stepCount).roundToInt().toFloat() / stepCount
        return valueRange.start + snapped * span
    }

    fun tick(next: Float) {
        if (steps <= 0) return
        val index = ((next - valueRange.start) / span * (steps + 1)).roundToInt().toFloat()
        if (lastStepIndex.isNaN() || abs(index - lastStepIndex) >= 1f) {
            feedback.perform(FeedbackIntent.Tick)
            lastStepIndex = index
        }
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
     */
    fun emit(thumb: Thumb, rawFraction: Float) {
        val next = snap(rawFraction)
        tick(next)
        val updated = when (thumb) {
            Thumb.Start -> {
                val start = next.coerceIn(valueRange.start, valueRange.endInclusive - gap)
                start..maxOf(value.endInclusive, start + gap)
                    .coerceAtMost(valueRange.endInclusive)
            }
            Thumb.End -> {
                val end = next.coerceIn(valueRange.start + gap, valueRange.endInclusive)
                minOf(value.start, end - gap).coerceAtLeast(valueRange.start)..end
            }
            Thumb.None -> return
        }
        currentOnValueChange(updated)
    }

    // Everything below mirrors `Slider`. Two sliders in one library that answer
    // the same gesture differently is worse than either of them being wrong.
    val detented = steps > 0 && !motion.reduceMotion

    /** The detent one thumb is on, pulled toward the finger if it is the one being dragged. */
    fun targetFor(thumb: Thumb, base: Float): Float =
        if (detented && activeThumb == thumb && !dragFraction.isNaN()) {
            base + (dragFraction - base) * SliderDefaults.DetentPull
        } else {
            base
        }

    val startSettled by animateFloatAsState(
        targetValue = targetFor(Thumb.Start, startFraction),
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "rangeStartDetent",
    )
    val endSettled by animateFloatAsState(
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
        if (activeThumb == thumb && !dragFraction.isNaN()) {
            snapSpec()
        } else {
            motion.springOrTween<Float>(motion.springSnappy)
        }

    val startTapEased by animateFloatAsState(
        targetValue = startFraction,
        animationSpec = tapSpec(Thumb.Start),
        label = "rangeStartTap",
    )
    val endTapEased by animateFloatAsState(
        targetValue = endFraction,
        animationSpec = tapSpec(Thumb.End),
        label = "rangeEndTap",
    )

    fun drawn(thumb: Thumb, base: Float, settled: Float, tapEased: Float): Float = when {
        detented -> settled.coerceIn(0f, 1f)
        // Only the thumb under the finger tracks it exactly; the other one is
        // standing still and may as well ease if something moved it.
        activeThumb == thumb && !dragFraction.isNaN() -> base
        else -> tapEased.coerceIn(0f, 1f)
    }

    val easedStart = drawn(Thumb.Start, startFraction, startSettled, startTapEased)
    val easedEnd = drawn(Thumb.End, endFraction, endSettled, endTapEased)

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
    val drawnStart = if (activeThumb == Thumb.End) {
        minOf(easedStart, easedEnd - gapFraction)
    } else {
        easedStart
    }
    val drawnEnd = if (activeThumb == Thumb.Start) {
        maxOf(easedEnd, easedStart + gapFraction)
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
        activeThumb == thumb && !dragFraction.isNaN() -> dragFraction - drawnAt
        detented -> targetFor(thumb, base) - drawnAt
        else -> base - drawnAt
    }

    // Against the spring, not against the contact-clamped position above: the
    // lag is exactly the signal, and clamping it away would leave nothing to
    // stretch by at the moment there is most to stretch about.
    val reachStart = reach(Thumb.Start, startFraction, easedStart)
    val reachEnd = reach(Thumb.End, endFraction, easedEnd)

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
            // See `Slider`, where that layout cost the outer 11dp of the control
            // its ability to be touched at all, which is precisely where a thumb
            // sits at either end of the range.
            val insetPx = with(density) { SliderThumbRadius.toPx() }
            val widthPx = (with(density) { maxWidth.toPx() } - insetPx * 2f).coerceAtLeast(1f)

            fun toFraction(x: Float): Float {
                val along = (x - insetPx) / widthPx
                return if (layoutDirection == LayoutDirection.Rtl) 1f - along else along
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SliderHeight)
                    .pointerInput(enabled, widthPx, valueRange, steps, startFraction, endFraction) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { offset ->
                            val f = toFraction(offset.x)
                            val thumb = if (abs(f - startFraction) <= abs(f - endFraction)) {
                                Thumb.Start
                            } else {
                                Thumb.End
                            }
                            emit(thumb, f)
                            currentFinished?.invoke()
                        }
                    }
                    .draggable(
                        state = rememberDraggableState { delta ->
                            val signed = if (layoutDirection == LayoutDirection.Rtl) -delta else delta
                            // The thumbs can start coincident — a zero-width
                            // range, which is exactly what "no filter yet" looks
                            // like. Distance alone cannot tell them apart there,
                            // and picking either one arbitrarily leaves the range
                            // able to open in one direction only. The first
                            // delta's sign is the answer the user just gave.
                            if (activeThumb == Thumb.None) {
                                // A zero delta carries no direction, and the
                                // first one after touch slop is routinely
                                // exactly zero. Treating it as "not negative"
                                // silently picks the end thumb, which is right
                                // half the time — and now that the thumbs push
                                // rather than block, the wrong half opens the
                                // range in the direction nobody asked for.
                                if (signed == 0f) return@rememberDraggableState
                                activeThumb = if (signed < 0f) Thumb.Start else Thumb.End
                                dragFraction = if (activeThumb == Thumb.Start) startFraction else endFraction
                            }
                            val from = if (dragFraction.isNaN()) {
                                if (activeThumb == Thumb.Start) startFraction else endFraction
                            } else {
                                dragFraction
                            }
                            dragFraction = (from + signed / widthPx).coerceIn(0f, 1f)
                            emit(activeThumb, dragFraction)
                        },
                        orientation = Orientation.Horizontal,
                        enabled = enabled,
                        interactionSource = interactions,
                        onDragStarted = { start ->
                            val f = toFraction(start.x)
                            val toStart = abs(f - startFraction)
                            val toEnd = abs(f - endFraction)
                            activeThumb = when {
                                // Equidistant means coincident, or a tap exactly
                                // between them. Defer to the first delta.
                                abs(toStart - toEnd) < CoincidenceEpsilon -> Thumb.None
                                toStart < toEnd -> Thumb.Start
                                else -> Thumb.End
                            }
                            // The thumb comes to the finger, as `Slider`'s
                            // does — but only once one of them has been picked.
                            // With the two coincident the choice is deferred to
                            // the first delta's direction, and moving a thumb
                            // before knowing which one would move the wrong one.
                            dragFraction = when (activeThumb) {
                                Thumb.Start, Thumb.End -> f.coerceIn(0f, 1f)
                                Thumb.None -> Float.NaN
                            }
                            if (activeThumb != Thumb.None) emit(activeThumb, dragFraction)
                        },
                        onDragStopped = {
                            feedback.perform(FeedbackIntent.GestureEnd)
                            lastStepIndex = Float.NaN
                            dragFraction = Float.NaN
                            activeThumb = Thumb.None
                            currentFinished?.invoke()
                        },
                    )
                    .drawWithCache {
                        val trackHeightPx = SliderTrackHeight.toPx()
                        val thumbRadiusPx = SliderThumbRadius.toPx()
                        val centreY = size.height / 2f
                        val trackTop = centreY - trackHeightPx / 2f
                        val activeColor = if (enabled) colors.primary else colors.contentDisabled
                        val inactiveColor = if (enabled) colors.outline else colors.surfaceSunken

                        onDrawBehind {
                            val trackLeft = thumbRadiusPx
                            val trackWidth = (size.width - thumbRadiusPx * 2f).coerceAtLeast(0f)
                            val startX = trackLeft + trackWidth * drawnStart
                            val endX = trackLeft + trackWidth * drawnEnd

                            drawRoundRect(
                                color = inactiveColor,
                                topLeft = Offset(trackLeft, trackTop),
                                size = Size(trackWidth, trackHeightPx),
                                cornerRadius = CornerRadius(trackHeightPx / 2f),
                            )
                            // The band between the thumbs, which is the value —
                            // on a plain slider the filled part runs from the
                            // start of the track, and here it does not.
                            drawRoundRect(
                                color = activeColor,
                                topLeft = Offset(startX, trackTop),
                                size = Size(endX - startX, trackHeightPx),
                                cornerRadius = CornerRadius(trackHeightPx / 2f),
                            )

                            if (steps > 0) {
                                val stepCount = steps + 1
                                for (i in 0..stepCount) {
                                    val x = trackLeft + trackWidth * i / stepCount
                                    val inBand = x in startX..endX
                                    drawCircle(
                                        color = if (inBand) colors.onPrimary else colors.contentSubtle,
                                        radius = trackHeightPx * 0.22f,
                                        center = Offset(x, centreY),
                                    )
                                }
                            }

                            for ((x, reachPx) in listOf(
                                startX to reachStart * trackWidth,
                                endX to reachEnd * trackWidth,
                            )) {
                                sliderThumb(
                                    centreX = x,
                                    centreY = centreY,
                                    radiusPx = thumbRadiusPx,
                                    scale = thumbScale,
                                    reachPx = reachPx,
                                    ringColor = colors.surface,
                                    fillColor = activeColor,
                                    ringPx = SliderThumbRing.toPx(),
                                )
                            }
                        }
                    }
            )

            // Two adjustable nodes, one per thumb. They draw nothing — the
            // canvas above has already drawn both — and exist so assistive tech
            // has two things to adjust rather than one control with two values
            // it cannot name.
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
                announcement = stateDescription?.invoke(value),
                onSet = { emit(Thumb.Start, fractionOf(it)) },
            )
            ThumbSemantics(
                contentDescription = endContentDescription,
                current = value.endInclusive,
                bounds = (valueRange.start + gap)
                    .coerceAtMost(valueRange.endInclusive)..valueRange.endInclusive,
                steps = steps,
                enabled = enabled,
                announcement = stateDescription?.invoke(value),
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

/** Which thumb a gesture is moving. */
private enum class Thumb { Start, End, None }

/**
 * How close two thumbs must be for a drag to defer to direction.
 *
 * A fraction of the track, not a distance in pixels: the question is whether the
 * finger is meaningfully nearer one thumb than the other, and on a 320dp track a
 * 1% difference is three pixels — inside anyone's aim.
 */
private const val CoincidenceEpsilon = 0.01f
