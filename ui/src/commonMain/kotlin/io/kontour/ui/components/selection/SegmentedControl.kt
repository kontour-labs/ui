package io.kontour.ui.components.selection

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
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
import io.kontour.ui.theme.Shadow
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.theme.Theme

object SegmentedControlDefaults {
    /** The gap between the track's edge and the thumb inside it. */
    /**
     * The inset between the track and its thumb.
     *
     * 4dp rather than 3dp so the two shapes are concentric: the track is
     * `small` (8dp) and the thumb is `extraSmall` (4dp), and 8 − 4 = 4. At 3dp
     * the thumb read one dp too square inside its track — not obviously wrong,
     * but the kind of near-miss the eye reads as a rendering fault rather than
     * as a choice.
     *
     * The general rule: **inner radius = outer radius − gap**. Where the gap is
     * on the 4dp grid, the shape scale already has the right token at both ends.
     */
    val TrackPadding: Dp = 4.dp
}

/**
 * A row of mutually exclusive options, presented as one control.
 *
 * ```
 * SegmentedControl(
 *     options = listOf("Depart", "Arrive"),
 *     selectedIndex = mode,
 *     onSelect = viewModel::setMode,
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
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return

    val colors = Theme.colors
    val motion = Theme.motion
    val outerShape = Theme.shapes.small
    val innerShape = Theme.shapes.extraSmall
    val feedback = Feedback
    val height = Theme.sizing.controlHeightMedium
    val indicator = rememberSelectionIndicatorState()

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
                modifier = Modifier.fillMaxSize(),
                shape = innerShape,
                color = if (enabled) colors.surface else colors.surfaceSunken,
                border = contrastEdge(),
                shadow = if (enabled) Theme.elevation.low else Shadow.None,
                content = {},
            )
        },
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
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
                                onSelect(index)
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
