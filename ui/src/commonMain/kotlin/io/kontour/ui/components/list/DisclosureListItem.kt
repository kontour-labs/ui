package io.kontour.ui.components.list

import io.kontour.ui.theme.clipProxy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.optionalBorder
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.shape
import io.kontour.ui.interaction.rememberTapFeedback
import io.kontour.ui.motion.chevronTurn
import io.kontour.ui.motion.enterOrFade
import io.kontour.ui.motion.exitOrFade
import io.kontour.ui.theme.Theme

/**
 * A row that opens onto more of itself.
 *
 * ```kotlin
 * DisclosureListItem(
 *     expanded = open,
 *     onExpandedChange = { open = it },
 *     header = {
 *         +"Why is my train late?"
 *         leading { +Tabler.Outline.HelpCircle }
 *     },
 * ) {
 *     Text("Trains give way to freight between Midland and Bellevue, …")
 * }
 * ```
 *
 * One row, one ground: the header is a [ListItem]'s, and pressing it grows the
 * row downward to show [content] inside the same card, under the header's words.
 * Nothing new appears beneath it.
 *
 * **Not an [ExpandingListItem]**, which opens onto *rows*: each child is a list
 * row of its own, in the group's seam, and the header becomes the first of a run.
 * Reach for that when what is inside is more places to go — platforms, stops,
 * files. Reach for this when it is more of the same thing — an answer, a note,
 * the settings of the one item — and should read as part of it.
 *
 * **Not an [io.kontour.ui.components.display.Accordion]** either: an accordion
 * is a section of a page with a header of its own; this is a row of a list, and
 * sits in a [ListSection] or a `ListGroup` with its neighbours as any row does,
 * taking its corners from [position].
 *
 * @param header The always-visible part, in a [ListItem]'s own shape. `+` fills
 *   its title. A `trailing` set here is replaced by the chevron when there is one.
 * @param position Where the row sits in a group; its corners, open or shut.
 * @param chevron The glyph that turns as it opens. Null leaves none — only for a
 *   header that says in words that it opens.
 * @param content What opens: under the header, lined up with its words, in the
 *   row's muted body style.
 */
@Composable
fun DisclosureListItem(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    header: ListItemScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    position: GroupPosition = GroupPosition.Only,
    chevron: ImageVector? = Theme.icons.chevronDown,
    expandedLabel: String = Theme.strings.expanded,
    collapsedLabel: String = Theme.strings.collapsed,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tap = rememberTapFeedback()
    val motion = Theme.motion
    val colours = Theme.colours

    // The press wash is the header's, and once the row is open the header's
    // bottom corners meet the body rather than the page: they travel with the
    // opening, as `ExpandingListItem`'s do.
    val openness by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = motion.tweenDefault(),
        label = "disclosureCorners",
    ).inMorphSteps()
    val card = position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical)
    val slots = listItemSlots {
        header()
        if (chevron != null) {
            trailing {
                Icon(
                    imageVector = chevron,
                    contentDescription = null,
                    modifier = Modifier.chevronTurn(expanded, label = "disclosureChevron"),
                    tint = if (enabled) colours.contentMuted else colours.contentDisabled,
                )
            }
        }
    }
    // Under the header's words, not its glyph: a leading icon and the gap after
    // it, as the row lays them out.
    val indent = if (slots.leading != null) {
        Theme.spacing.md + Theme.sizing.iconLarge + Theme.spacing.md
    } else {
        Theme.spacing.md
    }

    Column(
        // A row to the section it sits in, open or not: its body is part of
        // the card, not something else under a row.
        ListRowEdges
            .then(modifier)
            .fillMaxWidth()
            .clip(card.clipProxy())
            .background(if (enabled) colours.surfaceSunken else colours.surfaceSunken.copy(alpha = 0.5f), card)
            // One edge round the whole card at high contrast, header and body
            // together, rather than one round the header alone.
            .optionalBorder(contrastEdge(), card),
    ) {
        ListItemImpl(
            modifier = Modifier.semantics {
                stateDescription = if (expanded) expandedLabel else collapsedLabel
            },
            enabled = enabled,
            onClick = {
                tap()
                onExpandedChange(!expanded)
            },
            selected = false,
            role = Role.Button,
            shape = headerShape(position, openness),
            containerColour = Color.Transparent,
            selectedContainerColour = Color.Transparent,
            contentColour = colours.content,
            minHeight = Dp.Unspecified,
            interactionSource = interactionSource,
            slots = slots,
            edged = false,
            disabledContainerColour = Color.Transparent,
        )
        AnimatedVisibility(
            visible = expanded,
            // Under reduced motion the body takes its room at once and fades.
            enter = motion.enterOrFade(expandVertically(motion.tweenDefault()) + fadeIn(motion.tweenFast())),
            exit = motion.exitOrFade(shrinkVertically(motion.tweenDefault()) + fadeOut(motion.tweenFast())),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = indent, end = Theme.spacing.md, bottom = Theme.spacing.md),
            ) {
                ProvideTextStyle(Theme.typography.bodyMedium) {
                    ProvideContentColour(if (enabled) colours.contentMuted else colours.contentDisabled) {
                        content()
                    }
                }
            }
        }
    }
}
