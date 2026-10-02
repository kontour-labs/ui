package io.kontour.ui.components.selection

import io.kontour.ui.input.focusRing
import androidx.compose.foundation.interaction.MutableInteractionSource
import io.kontour.ui.foundation.shape
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.components.list.ListItemImpl
import io.kontour.ui.components.list.ListItemDefaults
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.DisableSelection
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
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.rememberToggleFeedback
import io.kontour.ui.interaction.rememberTapFeedback
import io.kontour.ui.interaction.LocalRowEnabled
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
 * @param position Where the row sits in a group of rows. Given one, it is a
 *   list row: a [io.kontour.ui.components.list.ListItem]'s sunken ground, with
 *   the corners that meet a neighbour squared, so it sits among a group's other
 *   rows as one of them. Null, the default, is a bare row with no ground.
 */
@Composable
fun SelectionRow(
    selected: Boolean,
    onSelectedChange: ((Boolean) -> Unit)?,
    role: Role,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    position: GroupPosition? = null,
    interactionSource: MutableInteractionSource? = null,
    content: ListItemScope.() -> Unit,
) {
    val slots = listItemSlots(content)
    // A row answers its press as the control in it would: it owns the click,
    // and the control inside, handed a null callback, stays silent. It used to
    // be silent too, so a checkbox in a row said nothing where the same checkbox
    // alone answered.
    val tap = rememberTapFeedback()
    val toggled = rememberToggleFeedback()
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = rowShape(position)

    val selectionModifier = when {
        // Inert, but still a checkbox that reads as checked. Dropping the
        // modifier entirely would leave a row a screen reader announces as
        // plain text.
        onSelectedChange == null -> Modifier.semantics {
            this.role = role
            toggleableState = ToggleableState(selected)
        }

        else -> when (role) {
        Role.RadioButton -> Modifier.pointerCursor(enabled = enabled).selectable(
            selected = selected,
            onClick = {
                // A radio row answers only a change, like a bare radio button.
                if (!selected) tap()
                onSelectedChange(true)
            },
            enabled = enabled,
            role = role,
            interactionSource = interactions,
            // A whole-row press-shrink looks wrong on something this wide; the
            // tonal wash alone carries the feedback.
            indication = kontourIndication(shape, pressScale = 1f),
        )

        else -> Modifier.pointerCursor(enabled = enabled).toggleable(
            value = selected,
            onValueChange = { now ->
                toggled(now)
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
            onSelectedChange(now)
        }
    }

    SelectionRowFrame(
        modifier = modifier,
        enabled = enabled,
        position = position,
        shape = shape,
        interactions = interactions,
        focusable = enabled && onSelectedChange != null,
        interaction = selectionModifier,
        rowToggle = rowToggle,
        slots = slots,
    )
}

/**
 * A row that opens a choice rather than making one: *Theme · Match system*,
 * pressed to go and pick another.
 *
 * ```kotlin
 * SelectionRow(onClick = { openThemePicker() }) {
 *     +"Theme"
 *     leading { +Tabler.Outline.Moon }
 *     trailing { +"Match system" }
 * }
 * ```
 *
 * The other half of a settings screen. A setting whose value flips where it
 * stands is the first [SelectionRow], a `Role.Checkbox` or `Role.Switch` with the
 * control in it; a setting whose value is chosen somewhere else — a sheet, a
 * menu, a screen of its own — is this one, a button with the current value in
 * its trailing slot. There used to be a `SettingRow` for it, and two components
 * for one settings screen was one too many: it was never clear which a row with
 * a switch in it was.
 *
 * Silent when pressed, as a [io.kontour.ui.components.list.ListItem] is: what
 * the press opens is the answer.
 *
 * @param onClick What pressing it opens. `null` shows a value that cannot be
 *   changed here, announced as plain text.
 * @param position See the other [SelectionRow]: given one, a list row among a
 *   group's others; null, a bare row.
 */
@Composable
fun SelectionRow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    position: GroupPosition? = null,
    interactionSource: MutableInteractionSource? = null,
    content: ListItemScope.() -> Unit,
) {
    val slots = listItemSlots(content)
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = rowShape(position)
    val click = if (onClick == null) {
        Modifier
    } else {
        Modifier.pointerCursor(enabled = enabled).clickable(
            interactionSource = interactions,
            indication = kontourIndication(shape, pressScale = 1f),
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    }
    SelectionRowFrame(
        modifier = modifier,
        enabled = enabled,
        position = position,
        shape = shape,
        interactions = interactions,
        focusable = enabled && onClick != null,
        interaction = click,
        rowToggle = null,
        slots = slots,
    )
}

/** A bare row's shape, or a grouped one's: see `position`. */
@Composable
private fun rowShape(position: GroupPosition?): Shape =
    position?.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical)
        ?: Theme.shapes.container

/**
 * What both [SelectionRow]s draw: in a group, a list row with [interaction] in
 * place of its click; alone, the bare row.
 */
@Composable
private fun SelectionRowFrame(
    modifier: Modifier,
    enabled: Boolean,
    position: GroupPosition?,
    shape: Shape,
    interactions: MutableInteractionSource,
    focusable: Boolean,
    interaction: Modifier,
    rowToggle: ((Boolean) -> Unit)?,
    slots: ListItemScope,
) {
    val colours = Theme.colours
    if (position != null) {
        CompositionLocalProvider(LocalRowToggle provides rowToggle, LocalRowEnabled provides enabled) {
            DisableSelection {
                ListItemImpl(
                    modifier = modifier,
                    enabled = enabled,
                    onClick = null,
                    selected = false,
                    role = Role.Button,
                    shape = shape,
                    containerColour = colours.surfaceSunken,
                    selectedContainerColour = colours.accent.container,
                    contentColour = colours.content,
                    minHeight = Dp.Unspecified,
                    interactionSource = interactions,
                    slots = slots,
                    interaction = interaction,
                )
            }
        }
        return
    }
    CompositionLocalProvider(
        LocalRowInteractionSource provides interactions,
        LocalRowToggle provides rowToggle,
        LocalRowEnabled provides enabled,
    ) {
        DisableSelection {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    // The row is the control, so the row shows where the keyboard is.
                    .focusRing(interactions, shape, enabled = focusable)
                    .clip(shape)
                    .then(interaction)
                    .padding(horizontal = Theme.spacing.sm, vertical = Theme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val labelColour = if (enabled) colours.content else colours.contentDisabled
                val muted = if (enabled) colours.contentMuted else colours.contentDisabled

                slots.leading?.let { leading ->
                    Box(contentAlignment = Alignment.Center) {
                        ProvideContentColour(muted) {
                            ContentSlot(iconSize = Theme.sizing.iconLarge, content = leading)
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    slots.overline?.let { overline ->
                        ProvideContentColour(muted) {
                            ProvideTextStyle(Theme.typography.labelSmall) {
                                ContentSlot(maxLines = 1, content = overline)
                            }
                        }
                    }
                    slots.label?.let { label ->
                        ProvideContentColour(labelColour) {
                            ProvideTextStyle(Theme.typography.bodyMedium) {
                                ContentSlot(maxLines = 2, content = label)
                            }
                        }
                    }
                    slots.supporting?.let { supporting ->
                        ProvideContentColour(muted) {
                            ProvideTextStyle(Theme.typography.bodySmall) {
                                ContentSlot(maxLines = 2, content = supporting)
                            }
                        }
                    }
                }

                slots.trailing?.let { trailing ->
                    Box(contentAlignment = Alignment.Center) {
                        // A value — "4 min", "Kilometres" — in the row's muted
                        // body style, dimming with the row. A control in the slot
                        // sets its own colours and is unaffected.
                        ProvideContentColour(muted) {
                            ProvideTextStyle(Theme.typography.bodyMedium) {
                                ContentSlot(maxLines = 1, content = trailing)
                            }
                        }
                    }
                }
            }
        }
    }
}
