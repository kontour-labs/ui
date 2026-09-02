package io.kontour.ui.components.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.motion.chevronTurn
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideContentColor
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

/**
 * A header that reveals content when tapped.
 *
 * ```
 * Accordion(
 *     expanded = section == Section.Accessibility,
 *     onExpandedChange = { section = if (it) Section.Accessibility else null },
 *     header = { +"Accessibility" },
 * ) {
 *     SelectionRow(reduceMotion, viewModel::setReduceMotion, Role.Switch) { +"Reduce motion" }
 * }
 * ```
 *
 * State is hoisted rather than internal, which is what lets a caller enforce
 * "only one open at a time" — the common arrangement for a settings screen —
 * without the component knowing about its siblings.
 *
 * The chevron rotates rather than swapping between two icons; a rotation reads
 * as the same object turning, a swap reads as a flicker. It uses the bouncy
 * spring, so it overshoots a couple of degrees and settles.
 *
 * Announces its state, so a screen reader says "Accessibility, collapsed" rather
 * than leaving the user to tap and find out.
 */
@Composable
fun Accordion(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    /**
     * The row that is always visible, in the same shape a [io.kontour.ui.components.list.ListItem] takes —
     * because that is what it is. `+` fills its title.
     *
     * A named builder rather than the trailing lambda, since the trailing one is
     * the body: an accordion is the one component here with two regions of
     * caller content, and the body is the one you read.
     */
    header: ListItemScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    chevron: ImageVector? = null,
    expandedLabel: String = Theme.strings.expanded,
    collapsedLabel: String = Theme.strings.collapsed,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val slots = listItemSlots(header)
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val motion = Theme.motion
    val feedback = Feedback
    val shape = Theme.shapes.container

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    stateDescription = if (expanded) expandedLabel else collapsedLabel
                }
                .minimumTouchTarget()
                .focusRing(interactions, shape)
                .clip(shape)
                .clickable(
                    interactionSource = interactions,
                    // A header spanning the screen should not shrink on press.
                    indication = kontourIndication(shape, pressScale = 1f),
                    enabled = enabled,
                    onClick = {
                        feedback.perform(FeedbackIntent.Selection)
                        onExpandedChange(!expanded)
                    },
                )
                .padding(Theme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val muted = if (enabled) Theme.colors.contentMuted else Theme.colors.contentDisabled

            slots.leading?.let { leading ->
                ProvideContentColor(muted) {
                    ContentSlot(content = leading)
                }
            }

            Column(Modifier.weight(1f)) {
                slots.label?.let { title ->
                    ProvideContentColor(
                        if (enabled) Theme.colors.content else Theme.colors.contentDisabled
                    ) {
                        ProvideTextStyle(Theme.typography.titleSmall) {
                            ContentSlot(content = title)
                        }
                    }
                }
                slots.supporting?.let { supporting ->
                    ProvideContentColor(muted) {
                        ProvideTextStyle(Theme.typography.bodySmall) {
                            ContentSlot(content = supporting)
                        }
                    }
                }
            }

            if (chevron != null) {
                Icon(
                    imageVector = chevron,
                    contentDescription = null,
                    tint = if (enabled) Theme.colors.contentMuted else Theme.colors.contentDisabled,
                    modifier = Modifier.chevronTurn(expanded, label = "accordionChevron"),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.tweenDefault()) + fadeIn(motion.tweenFast()),
            exit = shrinkVertically(motion.tweenDefault()) + fadeOut(motion.tweenFast()),
        ) {
            Column(
                Modifier.padding(
                    start = Theme.spacing.sm,
                    end = Theme.spacing.sm,
                    bottom = Theme.spacing.sm,
                ),
                content = content,
            )
        }
    }
}
