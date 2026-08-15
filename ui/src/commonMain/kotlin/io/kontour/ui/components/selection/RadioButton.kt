package io.kontour.ui.components.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.theme.Theme

private val RadioSize = 20.dp

/**
 * One option in a set where exactly one may be chosen.
 *
 * Almost always used through [RadioGroup], which owns the selection and applies
 * the `selectableGroup` semantics a screen reader needs to announce "option 2
 * of 5". A bare `RadioButton` outside a group is announced without that context.
 *
 * The inner dot scales in with a spring rather than fading, so selection lands
 * with a small physical click rather than a dissolve.
 *
 * @param onClick Pass `null` when a parent row owns the click.
 */
@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val colors = Theme.colors
    val motion = Theme.motion
    val feedback = Feedback

    val stroke = selectionStroke(enabled)

    val ring by animateColorAsState(
        targetValue = when {
            !enabled -> colors.contentDisabled
            selected -> colors.primary
            else -> colors.outlineStrong
        },
        animationSpec = motion.tweenFast(),
        label = "radioRing",
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "radioDot",
    )

    Canvas(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, Theme.shapes.pill)
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected = selected,
                        onClick = {
                            feedback.perform(FeedbackIntent.Selection)
                            onClick()
                        },
                        enabled = enabled,
                        role = Role.RadioButton,
                        interactionSource = interactions,
                        indication = null,
                    )
                } else {
                    // Not interactive, but still *state*. A button handed a
                    // null callback is showing what a row decided, and a row
                    // that is `clickable` rather than `selectable` publishes no
                    // selection of its own, so without this a screen reader
                    // reads the row as a button with a name and no indication
                    // of whether it is the chosen one. `SelectionRow` publishes
                    // it too; the same value merged twice is harmless.
                    Modifier.semantics {
                        role = Role.RadioButton
                        this.selected = selected
                    }
                }
            )
            .size(RadioSize)
    ) {
        val strokeWidth = stroke.toPx()
        val radius = (size.minDimension - strokeWidth) / 2f

        drawCircle(color = ring, radius = radius, style = Stroke(width = strokeWidth))

        if (dotScale > 0f) {
            drawCircle(
                color = if (enabled) colors.primary else colors.contentDisabled,
                radius = radius * 0.55f * dotScale,
            )
        }
    }
}

/**
 * A set of mutually exclusive options.
 *
 * ```
 * RadioGroup(
 *     options = listOf(Depart.Now, Depart.At, Depart.ArriveBy),
 *     selected = departMode,
 *     onSelect = viewModel::setDepartMode,
 *     label = { it.label },
 * )
 * ```
 *
 * Owning the selection here rather than at each button is what lets the group
 * apply `selectableGroup()`, which is what makes a screen reader announce the
 * position within the set. It also makes the invalid state — two selected, or
 * none — unrepresentable.
 *
 * @param label Renders each option's row. Defaults to a plain [SelectionRow],
 *   which makes the whole row tappable.
 */
@Composable
fun <T> RadioGroup(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (T) -> String,
    supporting: ((T) -> String?)? = null,
) {
    Column(modifier.selectableGroup()) {
        options.forEach { option ->
            val isSelected = option == selected
            val supportingText = supporting?.invoke(option)
            SelectionRow(
                selected = isSelected,
                onSelectedChange = { onSelect(option) },
                enabled = enabled,
                role = Role.RadioButton,
            ) {
                +label(option)
                if (supportingText != null) supporting { +supportingText }
                trailing {
                    RadioButton(
                        selected = isSelected,
                        // The row owns the click; a nested clickable would give
                        // a screen reader two targets for one choice.
                        onClick = null,
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

/** Kept internal so the row layout can be reused by checkbox and switch rows. */
internal val RadioVisualSize = RadioSize
