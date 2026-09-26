package io.kontour.ui.components.action

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.LocalTouchTargetOwnedByParent
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.shape
import io.kontour.ui.theme.Theme

/**
 * A row of related actions that reads as one control.
 *
 * ```kotlin
 * ButtonGroup {
 *     item(onClick = ::zoomOut, contentDescription = "Zoom out") { +Tabler.Outline.Minus }
 *     item(onClick = ::recentre, contentDescription = "Recentre") { +Tabler.Outline.CurrentLocation }
 *     item(onClick = ::zoomIn, contentDescription = "Zoom in") { +Tabler.Outline.Plus }
 * }
 * ```
 *
 * The buttons sit flush and only the outside corners round, the same treatment
 * [io.kontour.ui.components.list.GroupPosition] gives a group of rows. That is
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
    shape: CornerBasedShape = Theme.shapes.control,
    content: ButtonGroupScope.() -> Unit,
) {
    val actions = buttonGroupActions(content)

    // The group is one control, so the group owns the touch target.
    //
    // Left to themselves each button reserves `minTouchTarget` and centres its
    // visual inside it, and that reserved slack lands *between* the buttons: on
    // Android a 40dp icon button becomes a 48dp box, so the 1dp seam draws at
    // 9dp and the join stops reading as a join. Sizing the row instead keeps the
    // seam a seam, and a full-height segment is still a target a finger can
    // hit — it is the same guarantee, made by the thing that is actually one
    // target. Invisible on desktop, where the minimum is 24dp and no button is
    // smaller; see [LocalTouchTargetOwnedByParent].
    CompositionLocalProvider(LocalTouchTargetOwnedByParent provides true) {
        Row(
            modifier = modifier
                .semantics { isTraversalGroup = true }
                .defaultMinSize(minHeight = Theme.sizing.minTouchTarget),
            // Hairline rather than zero. Flush buttons of the same fill become one
            // undifferentiated slab, and the join is meant to read as a seam.
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.Seam),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupedButtons(actions, enabled, size, variant, shape, Orientation.Horizontal)
        }
    }
}

/**
 * A [ButtonGroup] stacked top to bottom, for a cluster that lives down the side
 * of something — a map's zoom controls, a canvas's tools.
 *
 * ```kotlin
 * VerticalButtonGroup {
 *     item(onClick = ::zoomIn, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
 *     item(onClick = ::zoomOut, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
 * }
 * ```
 *
 * The same rule turned on its side: only the top of the first button and the
 * bottom of the last round, and the seams run across. Every button takes the
 * width of the widest, so a column of labelled buttons is one straight-sided
 * shape rather than a ragged stack. The first action is at the top in either
 * layout direction — reading order down a column does not mirror.
 *
 * @param size Applied to every button, so a group cannot end up ragged.
 * @param variant Applied to every button. `Tertiary` by default, as for
 *   [ButtonGroup].
 * @param shape The group's outside corners; the ones facing a neighbour take
 *   [ButtonGroupDefaults.InnerCorner].
 */
@Composable
fun VerticalButtonGroup(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Medium,
    variant: ButtonVariant = ButtonVariant.Tertiary,
    shape: CornerBasedShape = Theme.shapes.control,
    content: ButtonGroupScope.() -> Unit,
) {
    val actions = buttonGroupActions(content)
    // The group owns the touch target here too, across rather than down: see
    // [ButtonGroup] for why the buttons must not each reserve it.
    CompositionLocalProvider(LocalTouchTargetOwnedByParent provides true) {
        Column(
            modifier = modifier
                .semantics { isTraversalGroup = true }
                .width(IntrinsicSize.Max)
                .defaultMinSize(minWidth = Theme.sizing.minTouchTarget),
            verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.Seam),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GroupedButtons(actions, enabled, size, variant, shape, Orientation.Vertical)
        }
    }
}

/** The buttons of a group, each shaped for where it sits along [orientation]. */
@Composable
private fun GroupedButtons(
    actions: List<ButtonGroupAction>,
    enabled: Boolean,
    size: ButtonSize,
    variant: ButtonVariant,
    shape: CornerBasedShape,
    orientation: Orientation,
) {
    // Down a column every button stretches to the widest; along a row each keeps
    // its own width.
    val each = if (orientation == Orientation.Vertical) Modifier.fillMaxWidth() else Modifier
    actions.forEachIndexed { index, action ->
        val position = GroupPosition.of(index, actions.size)
        val enabledHere = enabled && action.enabled

        if (action.icon != null && action.content == null) {
            IconButton(
                icon = action.icon,
                contentDescription = action.contentDescription.orEmpty(),
                onClick = action.onClick,
                modifier = each,
                enabled = enabledHere,
                variant = variant,
                size = size,
                shape = position.shape(shape, ButtonGroupDefaults.InnerCorner, orientation),
                interactionSource = action.interactionSource,
            )
        } else {
            Button(
                onClick = action.onClick,
                modifier = each,
                enabled = enabledHere,
                variant = variant,
                size = size,
                shape = position.shape(shape, ButtonGroupDefaults.InnerCorner, orientation),
                interactionSource = action.interactionSource,
                content = action.content ?: {},
            )
        }
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
 * `GroupPosition.of` exists to remove, and it goes wrong the same way: a
 * group with two rounded buttons in the middle reads as a rendering fault.
 *
 * The lambda is plain Kotlin, so a `@Composable` helper has to be hoisted above
 * the group rather than called in the block. That is the cost of collecting,
 * and it is the smaller cost — see
 * [io.kontour.ui.components.list.ListGroupScope] for what annotating the
 * builder `@Composable` actually does to a scope that collects.
 */
@LayoutScopeMarker
@Stable
class ButtonGroupScope internal constructor() {
    internal val actions = mutableListOf<ButtonGroupAction>()

    /**
     * An icon-only action. `contentDescription` is required, as ever.
     *
     * The action comes last, as it does on every other scope's shorthand, so
     * it can trail: `item("Zoom in", Tabler.Outline.Plus) { zoomIn() }`.
     */
    fun item(
        contentDescription: String,
        icon: ImageVector,
        enabled: Boolean = true,
        interactionSource: MutableInteractionSource? = null,
        onClick: () -> Unit,
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
    fun item(
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
     * `ListItemDefaults.InnerCorner` is 4dp for the same job and it is wrong
     * here. A list row is 300dp wide, so 4dp of round on its inside corners is a
     * hairline; a 40dp icon button with 4dp on both sides of a seam is a rounded
     * rectangle, and three of them in a row read as three separate buttons —
     * which is the one thing this component exists not to look like. Found by
     * rendering it.
     *
     * 2dp, up from 1dp, is as far as that finding allows. The seam should be
     * *visible* as a notch rather than inferred from a hairline, and at this size
     * the distance between "visible" and "three separate buttons" is about two
     * dp wide. Proportion is what differs between the two components, not taste:
     * 4dp is 1.3% of a list row and 10% of an icon button.
     */
    val InnerCorner: Dp = 2.dp
}
