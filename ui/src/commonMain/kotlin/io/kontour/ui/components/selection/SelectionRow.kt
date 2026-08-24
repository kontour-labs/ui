package io.kontour.ui.components.selection

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.ProvideContentColor
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalRowInteractionSource
import io.kontour.ui.interaction.LocalRowToggle
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
 * ```kotlin
 * SelectionRow(
 *     selected = notifyOnDelay,
 *     onSelectedChange = viewModel::setNotifyOnDelay,
 *     role = Role.Checkbox,
 * ) {
 *     +"Notify me about delays"
 *     supporting { +"Only for favourited routes" }
 *     trailing { Checkbox(notifyOnDelay, onCheckedChange = null) }
 * }
 * ```
 *
 * The nested control takes `onClick = null` / `onCheckedChange = null`: the row
 * owns the interaction, and the control is there to *show* state.
 *
 * The control is a slot like any other. There used to be a required `control`
 * parameter and a `controlPosition` enum beside it; both are gone, because
 * which of [ListItemScope.leading] or [ListItemScope.trailing] you fill *is*
 * the position. This takes [io.kontour.ui.components.list.ListItem]'s builder
 * rather than one of its own — but not its `onClick`, since being `toggleable`
 * or `selectable` by role is the whole reason it is a separate component.
 *
 * @param onSelectedChange What the row should now be, not that it was pressed.
 *   `null` makes the row non-interactive while still announcing its state — for
 *   a row whose press is handled by a parent, the same as [Checkbox] and
 *   [Switch].
 *   The row is doing the negating either way — `toggleable` hands it the new
 *   value — and a callback that threw it away made every toggle call site write
 *   `{ x = !x }`, which is the shape that reads `x` twice and can read a stale
 *   one. Under [Role.RadioButton] it is always `true`: a radio is turned on by
 *   pressing it and off by pressing a sibling, so there is no other value to
 *   report, and the group is what owns the pair.
 * @param role [Role.Checkbox], [Role.RadioButton] or [Role.Switch]. Drives what
 *   a screen reader calls the row, so it must match the control inside it.
 */
@Composable
fun SelectionRow(
    selected: Boolean,
    onSelectedChange: ((Boolean) -> Unit)?,
    role: Role,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: ListItemScope.() -> Unit,
) {
    val slots = listItemSlots(content)
    val colors = Theme.colors
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = Theme.shapes.small
    val feedback = Feedback

    val selectionModifier = when {
        // Inert, but still a checkbox that reads as checked. Dropping the
        // modifier entirely would leave a row a screen reader announces as
        // plain text.
        onSelectedChange == null -> Modifier.semantics {
            this.role = role
            toggleableState = ToggleableState(selected)
        }

        else -> when (role) {
        Role.RadioButton -> Modifier.selectable(
            selected = selected,
            onClick = {
                feedback.perform(FeedbackIntent.Selection)
                onSelectedChange(true)
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
            onValueChange = { now ->
                feedback.perform(FeedbackIntent.Selection)
                onSelectedChange(now)
            },
            enabled = enabled,
            role = role,
            interactionSource = interactions,
            indication = kontourIndication(shape, pressScale = 1f),
        )
        }
    }

    // Published so a control inside the row can *show* the row's press — a
    // switch stretches its thumb while held, and it reads that from an
    // interaction source it would otherwise own alone and nobody would push to.
    //
    // The toggle goes with it so a switch in the row can be dragged as well as
    // shown. Only for a genuinely toggleable row: a radio row can only ever set
    // true, and a drag has nothing to express there.
    val rowToggle: ((Boolean) -> Unit)? = when {
        onSelectedChange == null || role == Role.RadioButton -> null
        else -> { now ->
            feedback.perform(FeedbackIntent.Selection)
            onSelectedChange(now)
        }
    }

    CompositionLocalProvider(
        LocalRowInteractionSource provides interactions,
        LocalRowToggle provides rowToggle,
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .then(selectionModifier)
                .padding(horizontal = Theme.spacing.sm, vertical = Theme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val labelColor = if (enabled) colors.content else colors.contentDisabled
            val muted = if (enabled) colors.contentMuted else colors.contentDisabled

            slots.leading?.let { leading ->
                Box(contentAlignment = Alignment.Center) {
                    ProvideContentColor(muted) {
                        ContentSlot(iconSize = Theme.sizing.iconLarge, content = leading)
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                slots.overline?.let { overline ->
                    ProvideContentColor(muted) {
                        ProvideTextStyle(Theme.typography.labelSmall) {
                            ContentSlot(maxLines = 1, content = overline)
                        }
                    }
                }
                slots.label?.let { label ->
                    ProvideContentColor(labelColor) {
                        ProvideTextStyle(Theme.typography.bodyMedium) {
                            ContentSlot(maxLines = 2, content = label)
                        }
                    }
                }
                slots.supporting?.let { supporting ->
                    ProvideContentColor(muted) {
                        ProvideTextStyle(Theme.typography.bodySmall) {
                            ContentSlot(maxLines = 2, content = supporting)
                        }
                    }
                }
            }

            slots.trailing?.let { trailing ->
                Box(contentAlignment = Alignment.Center) {
                    ContentSlot(content = trailing)
                }
            }
        }
    }
}
