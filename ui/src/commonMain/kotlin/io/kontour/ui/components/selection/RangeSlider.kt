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
import androidx.compose.ui.unit.dp
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
 * Each thumb is its own adjustable node with its own bounded range — the start
 * thumb's range ends at the end thumb and vice versa — so "adjust" from
 * assistive tech cannot produce an inverted range.
 *
 * @param value The current range. Clamped into [valueRange], and never inverted.
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
     * Moves one thumb, clamped by the other.
     *
     * The clamp is what makes an inverted range unrepresentable rather than
     * merely unlikely: dragging the start thumb past the end stops it at the
     * end instead of swapping the two, because a range that swaps under the
     * finger is a range the user has to drag twice to fix.
     */
    fun emit(thumb: Thumb, rawFraction: Float) {
        val next = snap(rawFraction)
        tick(next)
        val updated = when (thumb) {
            Thumb.Start -> next.coerceAtMost(value.endInclusive)..value.endInclusive
            Thumb.End -> value.start..next.coerceAtLeast(value.start)
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

    /** See `Slider`'s `tapEased`: a tapped thumb travels, a dragged one tracks. */
    val startTapEased by animateFloatAsState(
        targetValue = startFraction,
        animationSpec = if (dragFraction.isNaN()) motion.springOrTween(motion.springSnappy) else snapSpec(),
        label = "rangeStartTap",
    )
    val endTapEased by animateFloatAsState(
        targetValue = endFraction,
        animationSpec = if (dragFraction.isNaN()) motion.springOrTween(motion.springSnappy) else snapSpec(),
        label = "rangeEndTap",
    )

    fun drawn(thumb: Thumb, base: Float, settled: Float, tapEased: Float): Float = when {
        detented -> settled.coerceIn(0f, 1f)
        // Only the thumb under the finger tracks it exactly; the other one is
        // standing still and may as well ease if something moved it.
        activeThumb == thumb && !dragFraction.isNaN() -> base
        else -> tapEased.coerceIn(0f, 1f)
    }

    val drawnStart = drawn(Thumb.Start, startFraction, startSettled, startTapEased)
    val drawnEnd = drawn(Thumb.End, endFraction, endSettled, endTapEased)

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
            .padding(horizontal = SliderThumbRadius)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(SliderHeight)) {
            val widthPx = with(density) { maxWidth.toPx() }

            fun toFraction(x: Float) =
                if (layoutDirection == LayoutDirection.Rtl) 1f - x / widthPx else x / widthPx

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
                                // half the time — a leftward drag then moves a
                                // thumb the clamp immediately pins, and the
                                // control looks dead rather than wrong.
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
                            val startX = size.width * drawnStart
                            val endX = size.width * drawnEnd

                            drawRoundRect(
                                color = inactiveColor,
                                topLeft = Offset(0f, trackTop),
                                size = Size(size.width, trackHeightPx),
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
                                    val x = size.width * i / stepCount
                                    val inBand = x in startX..endX
                                    drawCircle(
                                        color = if (inBand) colors.onPrimary else colors.contentSubtle,
                                        radius = trackHeightPx * 0.22f,
                                        center = Offset(x, centreY),
                                    )
                                }
                            }

                            for (x in listOf(startX, endX)) {
                                drawCircle(
                                    color = colors.surface,
                                    radius = thumbRadiusPx * thumbScale,
                                    center = Offset(x, centreY),
                                )
                                drawCircle(
                                    color = activeColor,
                                    radius = (thumbRadiusPx - 2.dp.toPx()) * thumbScale,
                                    center = Offset(x, centreY),
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
                bounds = valueRange.start..value.endInclusive,
                steps = steps,
                enabled = enabled,
                announcement = stateDescription?.invoke(value),
                onSet = { emit(Thumb.Start, fractionOf(it)) },
            )
            ThumbSemantics(
                contentDescription = endContentDescription,
                current = value.endInclusive,
                bounds = value.start..valueRange.endInclusive,
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
 * Its `bounds` are narrower than the slider's own range: the start thumb cannot
 * be set past the end thumb. That is the same clamp the drag applies, expressed
 * where assistive tech can see it, so "adjust to maximum" on the start thumb
 * lands on the end thumb rather than inverting the range.
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
