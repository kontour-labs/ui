package io.kontour.ui.components.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.kontour.ui.motion.chevronTurn
import io.kontour.ui.foundation.Icon
import androidx.compose.foundation.shape.CornerBasedShape
import io.kontour.ui.theme.lerpCorners
import io.kontour.ui.theme.Theme

/**
 * A row that unfolds more rows beneath it.
 *
 * ```kotlin
 * ExpandingListItem(
 *     expanded = open,
 *     onExpandedChange = { open = it },
 *     chevron = Tabler.Outline.ChevronDown,
 *     header = {
 *         +"Perth Underground"
 *         supporting { +"4 platforms" }
 *     },
 * ) {
 *     item("Platform 1", supporting = "Mandurah line")
 *     item("Platform 2", supporting = "Joondalup line")
 * }
 * ```
 *
 * **Not an [io.kontour.ui.components.display.Accordion].** The two are close
 * enough that the difference is worth stating, because picking the wrong one
 * looks fine until there is a list around it:
 *
 * | | `ExpandingListItem` | `Accordion` |
 * |---|---|---|
 * | The header is | a [ListItem] | a header of its own |
 * | What opens | more rows | arbitrary content |
 * | In a [ListGroup] | continues the group's seam | sits in it as a foreign object |
 *
 * An accordion draws its own frame, so its body is a block *under* a row. This
 * one hands its children to the same [ListGroupScope] a `ListGroup` uses, so
 * they are rows in the same run: the header rounds as
 * [ListItemPosition.First] while it is open and [ListItemPosition.Only] while it
 * is shut, the children take `Middle`, and the last of them closes the group.
 * The whole thing reads as one object opening rather than as a card appearing.
 *
 * ### It owns its children's positions, and that is why they come from a scope
 *
 * A caller emitting bare `ListItem`s would have to work out the seams itself —
 * which row is last, what the row *after* the group needs, what changes when the
 * group is shut. That arithmetic is the single thing this component exists to
 * do, so the children are declared rather than composed, exactly as
 * [ListGroup]'s are and for the reason argued at length on [ListGroupScope].
 *
 * @param position Where the **whole group** sits in a longer list. The header
 *   and the last child derive theirs from it; nothing else needs to know.
 * @param header The always-visible row, in a [ListItem]'s own shape. `+` fills
 *   its title. A `trailing` set here is replaced by the chevron.
 */
@Composable
fun ExpandingListItem(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    header: ListItemScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    position: ListItemPosition = ListItemPosition.Only,
    chevron: ImageVector? = null,
    spacing: Dp = ListItemDefaults.Spacing,
    expandedLabel: String = Theme.strings.expanded,
    collapsedLabel: String = Theme.strings.collapsed,
    interactionSource: MutableInteractionSource? = null,
    content: ListGroupScope.() -> Unit,
) {
    val motion = Theme.motion
    val children = ListGroupScope().apply(content).rows

    // Nothing to unfold is not an error, and it is not a disclosure either — a
    // chevron on a row that opens onto nothing is a promise the row cannot keep.
    val opens = children.isNotEmpty()
    val open = expanded && opens

    val openness by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = motion.tweenDefault(),
        label = "expandingListItemCorners",
    )

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        ListItem(
            enabled = enabled,
            onClick = if (opens) {
                { onExpandedChange(!expanded) }
            } else {
                null
            },
            position = position.opening(open),
            // The corners travel rather than switch.
            //
            // A header's bottom corners are round while the group is shut and
            // square once it has rows under it, and it used to be exactly that:
            // one value on one frame and the other on the next, under a body
            // that was busy animating open. The disclosure was smooth and the
            // thing disclosing it clicked.
            shape = headerShape(position, openness),
            interactionSource = interactionSource,
            modifier = Modifier.semantics {
                if (opens) stateDescription = if (open) expandedLabel else collapsedLabel
            },
            role = Role.Button,
        ) {
            header()
            if (chevron != null && opens) {
                trailing {
                    Icon(
                        imageVector = chevron,
                        contentDescription = null,
                        modifier = Modifier.chevronTurn(expanded, label = "expandingListChevron"),
                        tint = if (enabled) {
                            Theme.colors.contentMuted
                        } else {
                            Theme.colors.contentDisabled
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically(motion.tweenDefault()) + fadeIn(motion.tweenFast()),
            exit = shrinkVertically(motion.tweenDefault()) + fadeOut(motion.tweenFast()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                children.forEachIndexed { index, row ->
                    row(position.closing(index, children.size))
                }
            }
        }
    }
}

/**
 * The header's shape, [fraction] of the way from shut to open.
 *
 * Both ends come from the same [ListItemPosition.shape] the rest of the list
 * uses, so an animated header and a static row still agree about what a corner
 * is. Where opening does not change the position — a header that was already a
 * `First` or a `Middle` — the two ends are equal and the lerp is a no-op.
 */
@Composable
private fun headerShape(position: ListItemPosition, fraction: Float): CornerBasedShape {
    val base = ListItemDefaults.Shape
    val inner = ListItemDefaults.InnerCorner
    return position.shape(base, inner)
        .lerpCorners(position.opening(true).shape(base, inner), fraction)
}

/**
 * What the header's own corners do once the group is open.
 *
 * An open group has rows after its header, so a header that was the only row in
 * its run becomes the first of one, and a header that was the last becomes a
 * middle. Shut, it is exactly what the caller said it was.
 */
private fun ListItemPosition.opening(expanded: Boolean): ListItemPosition =
    if (!expanded) {
        this
    } else {
        when (this) {
            ListItemPosition.Only, ListItemPosition.First -> ListItemPosition.First
            ListItemPosition.Middle, ListItemPosition.Last -> ListItemPosition.Middle
        }
    }

/**
 * What a child's corners do.
 *
 * Every child but the last is a middle. The last one inherits the *group's*
 * ending: a group that was the only thing in its list closes it, and one with
 * more rows below carries on.
 */
private fun ListItemPosition.closing(index: Int, count: Int): ListItemPosition =
    if (index < count - 1) {
        ListItemPosition.Middle
    } else {
        when (this) {
            ListItemPosition.Only, ListItemPosition.Last -> ListItemPosition.Last
            ListItemPosition.First, ListItemPosition.Middle -> ListItemPosition.Middle
        }
    }
