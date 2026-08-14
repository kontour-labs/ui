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

private val TrackHeight = 6.dp
private val ThumbRadius = 11.dp
private val SliderHeight = 44.dp

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

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(0f, 1f)
        if (steps <= 0) return valueRange.start + clamped * range
        val stepCount = steps + 1
        val snapped = (clamped * stepCount).roundToInt().toFloat() / stepCount
        return valueRange.start + snapped * range
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
            .padding(horizontal = ThumbRadius)
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
                        emit(fraction + signed / widthPx)
                    },
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    interactionSource = interactions,
                    onDragStopped = {
                        feedback.perform(FeedbackIntent.GestureEnd)
                        lastStepIndex = Float.NaN
                        currentFinished?.invoke()
                    },
                )
                .drawWithCache {
                    val trackHeightPx = TrackHeight.toPx()
                    val thumbRadiusPx = ThumbRadius.toPx()
                    val centreY = size.height / 2f
                    val trackTop = centreY - trackHeightPx / 2f
                    val activeColor = if (enabled) colors.primary else colors.contentDisabled
                    val inactiveColor = if (enabled) colors.outline else colors.surfaceSunken
                    val thumbColor = if (enabled) colors.primary else colors.contentDisabled

                    onDrawBehind {
                        val thumbX = size.width * fraction

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

/** Kept so callers can reserve the same height when laying out around a slider. */
val SliderVisualHeight = SliderHeight

private fun Float.coerceIn(range: ClosedFloatingPointRange<Float>): Float =
    coerceIn(range.start, range.endInclusive)
