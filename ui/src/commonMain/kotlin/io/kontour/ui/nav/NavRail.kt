package io.kontour.ui.nav

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.IndicatorEdge
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.adaptive.leadingEdges
import io.kontour.ui.theme.Theme

object NavRailDefaults {
    val CollapsedWidth: Dp = 88.dp

    /**
     * Matches [NavDrawerDefaults.Width].
     *
     * An expanded rail *is* a drawer's width, and lining the two up is what stops
     * the switch between them reading as a jump.
     */
    val ExpandedWidth: Dp = 280.dp
}

/**
 * The primary navigation surface on a window with room beside the content.
 * **Goes down the leading edge.**
 *
 * ```kotlin
 * Row(Modifier.fillMaxSize()) {
 *     NavRail(
 *         items = destinations,
 *         selectedIndex = current,
 *         expanded = railOpen,
 *         onExpandedChange = { railOpen = it },
 *     )
 *     Content(Modifier.weight(1f))
 * }
 * ```
 *
 * Pass [onExpandedChange] to get the toggle; leave it null for a rail fixed at
 * whatever [expanded] says. The state is hoisted, matching [NavDrawerGroup] —
 * the app decides whether the rail is open because the user asked or because
 * the current destination implies it, and the component cannot know which.
 *
 * A collapsed *expandable* rail drops its labels rather than stacking them
 * under the icons. Stacked-then-inline would move the label to a different side
 * of the icon mid-animation, which is a pop nothing hides; icon-only keeps the
 * icon still and slides the label out from behind it. A rail with no toggle
 * keeps its stacked labels.
 *
 * @param itemAlignment Where the destinations sit vertically. `Top` by default;
 *   `Center` for a rail whose few destinations look stranded at the top of a tall
 *   window.
 */
@Composable
fun NavRail(
    items: List<NavItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    showLabels: Boolean = true,
    itemAlignment: Alignment.Vertical = Alignment.Top,
    collapsedWidth: Dp = NavRailDefaults.CollapsedWidth,
    expandedWidth: Dp = NavRailDefaults.ExpandedWidth,
    containerColor: Color = Theme.colors.surface,
    contentColor: Color = Theme.colors.content,
    indicatorColor: Color = Theme.colors.accent.container,
    expandLabel: String = "Expand navigation",
    collapseLabel: String = "Collapse navigation",
    header: (@Composable ColumnScope.() -> Unit)? = null,
    action: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * What the rail's *content* keeps clear of. The status bar, the gesture bar
     * and the display cutout on the leading side by default — a landscape phone
     * puts the cutout exactly where the rail is.
     */
    windowInsets: WindowInsets = WindowInsets.leadingEdges,
) {
    val indicator = rememberSelectionIndicatorState()
    val motion = Theme.motion
    val expandable = onExpandedChange != null

    val width by animateDpAsState(
        targetValue = if (expandable && expanded) expandedWidth else collapsedWidth,
        // `springGentle` — its token doc says "sheets and large surfaces", and a
        // rail growing from 88dp to 280dp is a large surface.
        animationSpec = motion.springOrTween(motion.springGentle),
        label = "navRailWidth",
    )

    // Inline once there is room for a label beside the icon. A collapsed
    // expandable rail shows no label at all; see the note on the function.
    val layout = if (expandable && expanded) NavItemLayout.Inline else NavItemLayout.Stacked
    val itemLabels = showLabels && !(expandable && !expanded)

    Surface(
        modifier = modifier.width(width).fillMaxHeight(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                // Inside the surface, so the rail's colour still reaches the edge
                // of the window and the cutout sits on the rail rather than on a
                // strip of whatever is behind it.
                .windowInsetsPadding(windowInsets)
                // Horizontal padding as well as vertical: the destinations run
                // full-width so the leading-edge marker sits at a consistent x,
                // and without this that x is the rail's own rounded edge, which
                // clips the marker in half.
                .padding(horizontal = Theme.spacing.xs, vertical = Theme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            if (onExpandedChange != null) {
                RailToggle(
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                    expandLabel = expandLabel,
                    collapseLabel = collapseLabel,
                )
            }

            header?.invoke(this)

            // Always emitted, so the destinations can be centred or pushed. The
            // old version only added a spacer when there was an action, which
            // left the items top-aligned with no way to move them.
            if (itemAlignment != Alignment.Top) Spacer(Modifier.weight(1f))

            SelectionIndicatorBox(
                state = indicator,
                // A pill around the whole row, travelling between destinations.
                // The bar's pill is sized to its icon; a rail row is wider than
                // that, so the marker follows the row instead.
                sizing = IndicatorSizing.Inset(Theme.spacing.xxs),
                indicator = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(Theme.shapes.pill)
                            .background(indicatorColor)
                    )
                },
            ) {
                Column(
                    modifier = Modifier.selectableGroup(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                ) {
                    items.forEachIndexed { index, item ->
                        NavRailItem(
                            item = item,
                            selected = index == selectedIndex,
                            showLabel = itemLabels,
                            expanded = expandable && expanded,
                            // Full width whether stacked or inline, so the
                            // leading-edge marker sits at the same x for every
                            // destination. Sized to content instead, the bar
                            // would shift sideways as it moved between a short
                            // label and a long one.
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            action?.invoke(this)
        }
    }
}

/**
 * The rail's expand/collapse control.
 *
 * `Role.Button`, not `Role.Tab` — expanding the rail does not navigate anywhere,
 * which is the same argument [NavDrawerGroup] makes for itself. The chevron
 * points the way the rail will grow, which is the trailing direction, so it has
 * to follow the layout direction rather than always pointing right.
 */
@Composable
private fun RailToggle(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandLabel: String,
    collapseLabel: String,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val icon = when {
        expanded && rtl -> SystemIcons.ChevronRight
        expanded -> SystemIcons.ChevronLeft
        rtl -> SystemIcons.ChevronLeft
        else -> SystemIcons.ChevronRight
    }

    IconButton(
        icon = icon,
        contentDescription = if (expanded) collapseLabel else expandLabel,
        onClick = { onExpandedChange(!expanded) },
        modifier = Modifier.semantics {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        },
    )
}

/** One destination in a [NavRail]. */
@Composable
fun NavRailItem(
    item: NavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    expanded: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
) {
    NavDestinationItem(
        item = item,
        selected = selected,
        modifier = modifier,
        layout = if (expanded) NavItemLayout.Inline else NavItemLayout.Stacked,
        showLabel = showLabel,
        interactionSource = interactionSource,
    )
}
