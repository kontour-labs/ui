package io.kontour.ui.components.selection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

// Shared with `RangeSlider`, which is the same track with a second thumb on it.
// Two copies of these would be two sliders that drift apart by a pixel.
internal val SliderTrackHeight = 6.dp
internal val SliderThumbRadius = 11.dp
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

    /**
     * The detent the thumb is currently *on*, eased rather than jumped to.
     *
     * A stepped slider's value is quantised, so without this the thumb teleports
     * a whole step the instant the finger crosses a midpoint. Springing it is
     * what turns that into the thumb being let go of and landing on the next one.
     *
     * Continuous sliders read `fraction` directly below — there is nothing to
     * land on, and easing a value that already tracks the finger is only lag.
     */
    val settled by animateFloatAsState(
        targetValue = fraction,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "sliderDetent",
    )

    /**
     * Where the thumb is drawn, which on a stepped slider is neither the value
     * nor the finger.
     *
     * It sits on the settled detent, pulled part of the way toward the finger by
     * [SliderDefaults.DetentPull]. The finger can be at most half a step past a
     * detent before [snap] moves on, so the thumb strains a little further from
     * the notch the longer the drag is held there, and springs when it goes.
     * Pulling *all* the way would just be a continuous slider that reports
     * quantised values, which is the thing detents exist not to be.
     */
    val drawnFraction = when {
        steps <= 0 || dragFraction.isNaN() -> fraction
        motion.reduceMotion -> fraction
        else -> (settled + (dragFraction - fraction) * SliderDefaults.DetentPull)
            .coerceIn(0f, 1f)
    }

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
            .padding(horizontal = SliderThumbRadius)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(SliderHeight)
                .pointerInput(enabled, widthPx, valueRange, steps) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val f = if (layoutDirection == LayoutDirection.Rtl) {
                            1f - offset.x / widthPx
                        } else {
                            offset.x / widthPx
                        }
                        emit(f)
                        currentFinished?.invoke()
                    }
                }
                .draggable(
                    state = rememberDraggableState { delta ->
                        val signed = if (layoutDirection == LayoutDirection.Rtl) -delta else delta
                        // Accumulate, then emit. Never the other way around: the
                        // emitted value is quantised and the caller may not take
                        // it at all, and either would lose the remainder.
                        val from = if (dragFraction.isNaN()) fraction else dragFraction
                        dragFraction = (from + signed / widthPx).coerceIn(0f, 1f)
                        emit(dragFraction)
                    },
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    interactionSource = interactions,
                    onDragStarted = { dragFraction = fraction },
                    onDragStopped = {
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
                    val centreY = size.height / 2f
                    val trackTop = centreY - trackHeightPx / 2f
                    val activeColor = if (enabled) colors.primary else colors.contentDisabled
                    val inactiveColor = if (enabled) colors.outline else colors.surfaceSunken
                    val thumbColor = if (enabled) colors.primary else colors.contentDisabled

                    onDrawBehind {
                        val thumbX = size.width * drawnFraction

                        drawRoundRect(
                            color = inactiveColor,
                            topLeft = Offset(0f, trackTop),
                            size = Size(size.width, trackHeightPx),
                            cornerRadius = CornerRadius(trackHeightPx / 2f),
                        )
                        drawRoundRect(
                            color = activeColor,
                            topLeft = Offset(0f, trackTop),
                            size = Size(thumbX, trackHeightPx),
                            cornerRadius = CornerRadius(trackHeightPx / 2f),
                        )

                        if (steps > 0) {
                            val stepCount = steps + 1
                            for (i in 0..stepCount) {
                                val x = size.width * i / stepCount
                                drawCircle(
                                    color = if (x <= thumbX) colors.onPrimary else colors.contentSubtle,
                                    radius = trackHeightPx * 0.22f,
                                    center = Offset(x, centreY),
                                )
                            }
                        }

                        // A ring of the page colour behind the thumb keeps it
                        // legible where it overlaps the filled track.
                        drawCircle(
                            color = colors.surface,
                            radius = thumbRadiusPx * thumbScale,
                            center = Offset(thumbX, centreY),
                        )
                        drawCircle(
                            color = thumbColor,
                            radius = (thumbRadiusPx - 2.dp.toPx()) * thumbScale,
                            center = Offset(thumbX, centreY),
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
}

/** Kept so callers can reserve the same height when laying out around a slider. */
val SliderVisualHeight = SliderHeight

private fun Float.coerceIn(range: ClosedFloatingPointRange<Float>): Float =
    coerceIn(range.start, range.endInclusive)
