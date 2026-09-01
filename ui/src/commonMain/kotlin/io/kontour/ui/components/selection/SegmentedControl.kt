package io.kontour.ui.components.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.foundation.selectionIndicatorItem
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.theme.Shadow
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.inset
import kotlin.math.abs

object SegmentedControlDefaults {
    /**
     * The inset between the track and its thumb.
     *
     * One rung of the shape scale, so the thumb's shape is the track's stepped
     * down by exactly this much — see [io.kontour.ui.theme.inset], which is what
     * derives it. At 3dp the thumb read one dp too square inside its track: not
     * obviously wrong, but the kind of near-miss the eye reads as a rendering
     * fault rather than as a choice.
     */
    val TrackPadding: Dp = 6.dp
}

/**
 * The furthest the thumb elongates while straining toward the next segment.
 *
 * A fifth again its own width. The thumb is a whole segment wide rather than a
 * 22dp circle, so it needs far less proportional stretch than a slider's does
 * before it reads as give.
 */
private const val MaxSegmentStretch = 0.2f

/**
 * A row of mutually exclusive options, presented as one control.
 *
 * ```
 * SegmentedControl(
 *     options = listOf("Depart", "Arrive"),
 *     selected = mode,
 *     onSelectedChange = viewModel::setMode,
 * )
 * ```
 *
 * Best for two to four short options that the user switches between often — a
 * depart/arrive toggle, a day/week/month range. Beyond four, or with long
 * labels, use a [io.kontour.ui.components.selection.RadioGroup] or a `Select`;
 * segments get too narrow to read and too narrow to hit.
 *
 * The selected indicator is a single surface that **slides** between positions
 * rather than each segment fading its own background. That is what makes the
 * control read as one physical thing with a moving part, and it is why the
 * indicator is drawn behind the labels rather than per segment.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return

    val colors = Theme.colors
    val motion = Theme.motion
    val outerShape = Theme.shapes.field
    // Concentric by construction rather than by picking the token one rung down
    // and trusting the padding to match the step.
    val innerShape = outerShape.inset(SegmentedControlDefaults.TrackPadding)
    val feedback = Feedback
    // At least a fingertip tall, whatever the control height token says.
    //
    // A segmented control is one control made of parts, so it owns the touch
    // target for all of them — the same bargain `ButtonGroup` strikes. It used
    // to pin `controlHeightMedium` and opt out of `minimumTouchTarget`
    // altogether, which on Android left it 4dp shorter than any `Button` beside
    // it and quietly broke the promise in `Sizing`'s KDoc that a row of mixed
    // controls lines up. Invisible on desktop, where the minimum is 24dp.
    val height = maxOf(Theme.sizing.controlHeightMedium, Theme.sizing.minTouchTarget)
    val indicator = rememberSelectionIndicatorState()

    var trackWidth by remember { mutableFloatStateOf(0f) }
    val currentSelected by rememberUpdatedState(selected)
    val currentChange by rememberUpdatedState(onSelectedChange)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ticker = rememberDetentTicker()

    /**
     * Where the finger is along the track, or `NaN` before the first drag.
     *
     * Kept after the finger lifts rather than cleared, so the thumb relaxes back
     * out of its lean instead of losing the number it was leaning by. [engaged]
     * is what says whether there is a finger; this only says where it was.
     */
    var fingerX by remember { mutableFloatStateOf(Float.NaN) }
    var dragging by remember { mutableStateOf(false) }

    /**
     * How much of the lean toward the finger is applied — 1 while dragging, 0 at
     * rest, and springing between the two.
     *
     * The lean itself is worked out in the thumb's `graphicsLayer` from the
     * finger and from where the indicator has actually reached, because both of
     * those change every frame and neither is worth a recomposition.
     *
     * It leans by [SliderDefaults.DetentPull], the same fraction the sliders
     * use and for the same reason: far enough to read as the thumb straining
     * toward where you are going, never so far that it is closer to the next
     * segment than to its own.
     *
     * Off under reduced motion, as the sliders' detent pull is. The thumb still
     * travels to the segment you picked; it just stops reaching for you.
     */
    val engaged by animateFloatAsState(
        targetValue = if (dragging && !motion.reduceMotion) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "segmentStrain",
    )

    SelectionIndicatorBox(
        state = indicator,
        // The thumb is exactly the segment it marks. Sized from the measured
        // segment rather than `maxWidth / options.size`, so segments no longer
        // have to be equal width — which the previous implementation required.
        sizing = IndicatorSizing.Fill,
        modifier = modifier
            .selectableGroup()
            .height(height)
            .clip(outerShape)
            .background(colors.surfaceSunken, outerShape)
            .then(
                contrastEdge()?.let { Modifier.border(it, outerShape) } ?: Modifier
            )
            .padding(SegmentedControlDefaults.TrackPadding),
        indicator = {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Everything here is read inside the layer rather than
                        // in composition: the finger moves every frame and so
                        // does the indicator underneath, and neither of them
                        // changes anything but this transform.
                        val segments = options.size
                        val here = indicator.drawn
                        val lean = if (
                            fingerX.isNaN() || trackWidth <= 0f ||
                            segments == 0 || here.width <= 0f
                        ) {
                            0f
                        } else {
                            val segment = trackWidth / segments
                            // From where the thumb *is*, not from where the
                            // segment it belongs to would put it. Crossing a
                            // boundary moves the selection and starts the
                            // indicator travelling; measured from the new
                            // segment's centre the lean would flip sign on that
                            // same frame and throw the thumb backwards past the
                            // segment it just left. See `drawn`.
                            val base = here.center.x
                            val pulled = (fingerX - base) * SliderDefaults.DetentPull * engaged
                            // Never off the track: at either end the wall is the
                            // answer.
                            pulled.coerceIn(
                                segment / 2f - base,
                                (trackWidth - segment / 2f - base).coerceAtLeast(segment / 2f - base),
                            )
                        }

                        translationX = lean
                        // Anchored on the edge it is leaving, so the thumb
                        // elongates toward the segment it is heading for rather
                        // than swelling in place. The slider's thumb does the
                        // same thing with the same signal.
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (lean >= 0f) 0f else 1f,
                            pivotFractionY = 0.5f,
                        )
                        val reach = if (trackWidth <= 0f || options.isEmpty()) {
                            0f
                        } else {
                            abs(lean) / (trackWidth / options.size)
                        }
                        scaleX = 1f + reach.coerceAtMost(MaxSegmentStretch)
                    },
                shape = innerShape,
                color = if (enabled) colors.surface else colors.surfaceSunken,
                border = contrastEdge(),
                shadow = if (enabled) Theme.elevation.low else Shadow.None,
                content = {},
            )
        },
    ) {
        /**
         * Drag across the segments and the thumb comes with you.
         *
         * The thumb slides, so a finger put on it and moved sideways should
         * carry it — and until now the whole control could only be tapped, which
         * is the one gesture that does not use the thing that makes it a
         * segmented control rather than three buttons.
         *
         * On the track rather than on each segment: a drag that starts on
         * "Depart" and ends on "Arrive" leaves the segment it began in, and a
         * per-segment gesture loses the pointer at the boundary. The taps stay
         * per-segment, and `detectHorizontalDragGestures` waits for touch slop,
         * so a press that never travels is still a tap on the segment under it.
         */
        fun selectAt(x: Float) {
            if (trackWidth <= 0f) return
            val fraction = (x / trackWidth).coerceIn(0f, 1f)
            val raw = (fraction * options.size).toInt().coerceIn(options.indices)
            val index = if (isRtl) options.size - 1 - raw else raw
            // Once per segment crossed, the way a stepped slider ticks: a user
            // dragging without looking can feel where the boundaries are. The
            // ticker owns the guard now — it used to be "the index changed",
            // which is the same thing said once per component rather than once.
            ticker.at(index)
            if (index == currentSelected) return
            currentChange(index)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .onSizeChanged { trackWidth = it.width.toFloat() }
                .then(
                    if (enabled) {
                        Modifier.pointerInput(options.size, isRtl) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    fingerX = offset.x
                                    dragging = true
                                    selectAt(offset.x)
                                },
                                onHorizontalDrag = { change, _ ->
                                    fingerX = change.position.x
                                    selectAt(change.position.x)
                                },
                                onDragEnd = {
                                    dragging = false
                                    ticker.reset()
                                    feedback.perform(FeedbackIntent.GestureEnd)
                                },
                                onDragCancel = {
                                    dragging = false
                                    ticker.reset()
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selected
                val interactions = remember { MutableInteractionSource() }

                val labelColor by animateColorAsState(
                    targetValue = when {
                        !enabled -> colors.contentDisabled
                        selected -> colors.content
                        else -> colors.contentMuted
                    },
                    animationSpec = motion.tweenFast(),
                    label = "segmentLabel",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectionIndicatorItem(option, selected)
                        .focusRing(interactions, innerShape)
                        .clip(innerShape)
                        .selectable(
                            selected = selected,
                            onClick = {
                                feedback.perform(FeedbackIntent.Selection)
                                onSelectedChange(index)
                            },
                            enabled = enabled,
                            role = Role.RadioButton,
                            interactionSource = interactions,
                            // The sliding thumb is the feedback; a wash on top of
                            // it would fight with the movement.
                            indication = null,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ProvideTextStyle(Theme.typography.labelMedium) {
                        Text(text = option, color = labelColor, maxLines = 1)
                    }
                }
            }
        }
    }
}
