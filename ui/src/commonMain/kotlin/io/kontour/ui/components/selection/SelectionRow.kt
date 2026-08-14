package io.kontour.ui.components.selection

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

/**
 * A labelled row wrapping a selection control.
 *
 * The whole row is the target, not just the 20dp box at the end of it. This is
 * the form almost every checkbox, radio and switch in an app should take: a
 * bare control with a `Text` beside it gives the user a small target and gives
 * a screen reader two nodes for one choice.
 *
 * ```
 * SelectionRow(
 *     label = "Notify me about delays",
 *     supporting = "Only for favourited routes",
 *     selected = notifyOnDelay,
 *     onClick = { viewModel.setNotifyOnDelay(!notifyOnDelay) },
 *     role = Role.Checkbox,
 *     control = { Checkbox(notifyOnDelay, onCheckedChange = null) },
 * )
 * ```
 *
 * The nested control takes `onClick = null` / `onCheckedChange = null`: the row
 * owns the interaction, and the control is there to *show* state.
 *
 * @param role [Role.Checkbox], [Role.RadioButton] or [Role.Switch]. Drives what
 *   a screen reader calls the row, so it must match the control inside it.
 * @param controlPosition Where the control sits. Trailing is the default and is
 *   right for settings lists; leading suits a list of options being picked from.
 */
@Composable
fun SelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    role: Role,
    control: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    controlPosition: ControlPosition = ControlPosition.Trailing,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = Theme.shapes.small
    val feedback = Feedback

    val selectionModifier = when (role) {
        Role.RadioButton -> Modifier.selectable(
            selected = selected,
            onClick = {
                feedback.perform(FeedbackIntent.Selection)
                onClick()
            },
            enabled = enabled,
            role = role,
            interactionSource = interactions,
            // A whole-row press-shrink looks wrong on something this wide; the
            // tonal wash alone carries the feedback.
            indication = kontourIndication(shape, pressScale = 1f),
        )

        else -> Modifier.toggleable(
            value = selected,
            onValueChange = {
                feedback.perform(FeedbackIntent.Selection)
                onClick()
            },
            enabled = enabled,
            role = role,
            interactionSource = interactions,
            indication = kontourIndication(shape, pressScale = 1f),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(selectionModifier)
            .padding(horizontal = Theme.spacing.sm, vertical = Theme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (controlPosition == ControlPosition.Leading) {
            // The row already announces the state; the control repeating it
            // would make a screen reader say "checked" twice.
            Row(Modifier.semantics { }) { control() }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = Theme.typography.bodyMedium,
                color = if (enabled) Theme.colors.content else Theme.colors.contentDisabled,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = Theme.typography.bodySmall,
                    color = if (enabled) Theme.colors.contentMuted else Theme.colors.contentDisabled,
                )
            }
        }

        if (controlPosition == ControlPosition.Trailing) {
            Row(Modifier.semantics { }) { control() }
        }
    }
}

/** Which side of a [SelectionRow] the control sits on. */
enum class ControlPosition { Leading, Trailing }
