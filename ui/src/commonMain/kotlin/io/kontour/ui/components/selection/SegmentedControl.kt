package io.kontour.ui.components.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.theme.Theme

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
    val trackPadding = 3.dp

    BoxWithConstraints(
        modifier
            .selectableGroup()
            .height(height)
            .clip(outerShape)
            .background(colors.surfaceSunken, outerShape)
            .padding(trackPadding)
    ) {
        val segmentWidth = maxWidth / options.size

        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex.coerceIn(0, options.lastIndex),
            animationSpec = motion.springOrTween(motion.springDefault),
            label = "segmentIndicator",
        )

        Surface(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight(),
            shape = innerShape,
            color = if (enabled) colors.surface else colors.surfaceSunken,
            shadow = if (enabled) Theme.elevation.low else io.kontour.ui.theme.Shadow.None,
        ) {}

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
                        .width(segmentWidth)
                        .fillMaxHeight()
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
                            // The sliding indicator is the feedback; a wash on
                            // top of it would fight with the movement.
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
