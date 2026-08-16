package io.kontour.ui.components.action

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.theme.Theme

/**
 * A row of related actions that reads as one control.
 *
 * ```kotlin
 * ButtonGroup {
 *     action(onClick = ::zoomOut, contentDescription = "Zoom out") { +Tabler.Outline.Minus }
 *     action(onClick = ::recentre, contentDescription = "Recentre") { +Tabler.Outline.CurrentLocation }
 *     action(onClick = ::zoomIn, contentDescription = "Zoom in") { +Tabler.Outline.Plus }
 * }
 * ```
 *
 * The buttons sit flush and only the outside corners round, the same treatment
 * [io.kontour.ui.components.list.ListItemPosition] gives a group of rows. That is
 * the whole visual idea: three separate buttons say "three things", one joined
 * group says "one thing, three ways".
 *
 * ### Not a `SegmentedControl`
 *
 * They look almost identical and mean opposite things:
 *
 * | | `ButtonGroup` | `SegmentedControl` |
 * |---|---|---|
 * | Each item is | an action | an option |
 * | Something is selected | no | always exactly one |
 * | Role | `Button` | `RadioButton` |
 * | Pressing one | does something | changes a value |
 *
 * A segmented control with no selection is a broken segmented control; a button
 * group with a selection is a segmented control wearing the wrong clothes. If
 * the row answers a question, it is a
 * [io.kontour.ui.components.selection.SegmentedControl].
 *
 * ### Not `TopBar`'s `actions`
 *
 * That slot is the screen's actions, laid out by the bar and separated from each
 * other. This is a *cluster* of actions that belong together — zoom in and zoom
 * out, or a set of formatting toggles — and the joining is what says so. A
 * `TopBar` can hold one of these in its `actions` slot.
 *
 * @param size Applied to every button, so a group cannot end up ragged.
 * @param variant Applied to every button, for the same reason. `Tertiary` by
 *   default: a joined group is already a strong shape and solid buttons in a row
 *   read as a wall.
 */
@Composable
fun ButtonGroup(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Medium,
    variant: ButtonVariant = ButtonVariant.Tertiary,
    shape: CornerBasedShape = Theme.shapes.small,
    content: ButtonGroupScope.() -> Unit,
) {
    val actions = buttonGroupActions(content)

    Row(
        modifier = modifier.semantics { isTraversalGroup = true },
        // Hairline rather than zero. Flush buttons of the same fill become one
        // undifferentiated slab, and the join is meant to read as a seam.
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.Seam),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            val position = ButtonGroupPosition.of(index, actions.size)
            val enabledHere = enabled && action.enabled

            if (action.icon != null && action.content == null) {
                IconButton(
                    icon = action.icon,
                    contentDescription = action.contentDescription.orEmpty(),
                    onClick = action.onClick,
                    enabled = enabledHere,
                    variant = variant,
                    size = size,
                    shape = position.shape(shape),
                    interactionSource = action.interactionSource,
                )
            } else {
                Button(
                    onClick = action.onClick,
                    enabled = enabledHere,
                    variant = variant,
                    size = size,
                    shape = position.shape(shape),
                    interactionSource = action.interactionSource,
                    content = action.content ?: {},
                )
            }
        }
    }
}

/** Where a button sits in its group, and therefore which corners round. */
enum class ButtonGroupPosition {
    Only, First, Middle, Last;

    companion object {
        fun of(index: Int, count: Int): ButtonGroupPosition = when {
            count <= 1 -> Only
            index == 0 -> First
            index == count - 1 -> Last
            else -> Middle
        }
    }
}

/**
 * The group's outside corners round; the ones facing a neighbour go square.
 *
 * `start`/`end` rather than left/right, so the first button rounds the corners
 * the reader starts from in either direction. A group built with left and right
 * is a group whose seams are on the wrong side in Arabic.
 */
fun ButtonGroupPosition.shape(
    shape: CornerBasedShape,
    square: Dp = ButtonGroupDefaults.InnerCorner,
): Shape {
    val flat = CornerSize(square)
    return when (this) {
        ButtonGroupPosition.Only -> shape
        ButtonGroupPosition.First -> shape.copy(topEnd = flat, bottomEnd = flat)
        ButtonGroupPosition.Middle ->
            shape.copy(topStart = flat, topEnd = flat, bottomStart = flat, bottomEnd = flat)

        ButtonGroupPosition.Last -> shape.copy(topStart = flat, bottomStart = flat)
    }
}

/** One collected action. */
internal class ButtonGroupAction(
    val onClick: () -> Unit,
    val enabled: Boolean,
    val icon: ImageVector?,
    val contentDescription: String?,
    val content: (@Composable RowContentScope.() -> Unit)?,
    val interactionSource: MutableInteractionSource?,
)

/**
 * Collects the actions.
 *
 * A builder rather than a plain row of `Button`s, because the shape of each one
 * depends on how many there are — which is not known until they have all been
 * declared. Passing `position` by hand at each call site is the arithmetic
 * `ListItemPosition.of` exists to remove, and it goes wrong the same way: a
 * group with two rounded buttons in the middle reads as a rendering fault.
 *
 * The lambda is plain Kotlin, so a `@Composable` helper has to be hoisted above
 * the group rather than called in the block. That is the cost of collecting,
 * and it is the smaller cost — see
 * [io.kontour.ui.components.list.ListGroupScope] for what annotating the
 * builder `@Composable` actually does to a scope that collects.
 */
class ButtonGroupScope internal constructor() {
    internal val actions = mutableListOf<ButtonGroupAction>()

    /** An icon-only action. `contentDescription` is required, as ever. */
    fun action(
        onClick: () -> Unit,
        contentDescription: String,
        icon: ImageVector,
        enabled: Boolean = true,
        interactionSource: MutableInteractionSource? = null,
    ) {
        actions += ButtonGroupAction(
            onClick = onClick,
            enabled = enabled,
            icon = icon,
            contentDescription = contentDescription,
            content = null,
            interactionSource = interactionSource,
        )
    }

    /** An action with content — a label, or a label and an icon. */
    fun action(
        onClick: () -> Unit,
        enabled: Boolean = true,
        interactionSource: MutableInteractionSource? = null,
        content: @Composable RowContentScope.() -> Unit,
    ) {
        actions += ButtonGroupAction(
            onClick = onClick,
            enabled = enabled,
            icon = null,
            contentDescription = null,
            content = content,
            interactionSource = interactionSource,
        )
    }
}

internal fun buttonGroupActions(content: ButtonGroupScope.() -> Unit): List<ButtonGroupAction> =
    ButtonGroupScope().apply(content).actions

object ButtonGroupDefaults {
    /**
     * The gap between two joined buttons.
     *
     * Not zero. Two `Tertiary` buttons flush against each other share a fill and
     * become one slab with no visible division, so the group reads as a single
     * wide button that happens to have icons in it. A hairline is enough to say
     * where one ends.
     */
    val Seam: Dp = 1.dp

    /**
     * The radius on a corner facing a neighbour.
     *
     * `ListItemPosition` uses 4dp for the same job and it is wrong here. A list
     * row is 300dp wide, so 4dp of round on its inside corners is a hairline; a
     * 40dp icon button with 4dp on both sides of a seam is a rounded rectangle,
     * and three of them in a row read as three separate buttons — which is the
     * one thing this component exists not to look like. Found by rendering it.
     */
    val InnerCorner: Dp = 1.dp
}
