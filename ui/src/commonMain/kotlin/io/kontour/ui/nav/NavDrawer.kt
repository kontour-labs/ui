package io.kontour.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.display.Badge
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.IndicatorEdge
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.LocalSelectionIndicator
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.foundation.selectionIndicatorItem
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.sheet.SideSheet
import io.kontour.ui.theme.Theme

object NavDrawerDefaults {
    val Width: Dp = 280.dp

    /** How far a nested item is indented from its parent. */
    val NestIndent: Dp = 24.dp
}

/**
 * A list of destinations down the side of a window.
 *
 * ```kotlin
 * // Always visible, on a window with room for it:
 * Row(Modifier.fillMaxSize()) {
 *     NavDrawer(header = { Logo() }) {
 *         NavDrawerItem("Overview", Tabler.Outline.LayoutDashboard, selected, ::go)
 *         NavDrawerSection("Content") {
 *             NavDrawerItem("Routes", Tabler.Outline.Route, …)
 *             NavDrawerItem("Stops", Tabler.Outline.MapPin, …)
 *         }
 *     }
 *     Content(Modifier.weight(1f))
 * }
 * ```
 *
 * A slot rather than a [NavItem] list, unlike [NavBar] and [NavRail], because a
 * drawer is where the destinations stop being a flat set of three: the admin
 * panel's sidebar nests, groups and separates, and flattening that into a list
 * model would mean a model that is really a tree wearing a list's shape.
 *
 * For a drawer that slides in over the content, use [ModalNavDrawer].
 */
@Composable
fun NavDrawer(
    modifier: Modifier = Modifier,
    width: Dp = NavDrawerDefaults.Width,
    containerColor: Color = Theme.colors.surface,
    contentColor: Color = Theme.colors.content,
    indicatorColor: Color = Theme.colors.accentContainer,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .width(width)
            .fillMaxHeight(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(Theme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        ) {
            header?.invoke(this)

            DrawerItems(
                modifier = Modifier.weight(1f),
                indicatorColor = indicatorColor,
                content = content,
            )

            footer?.invoke(this)
        }
    }
}

/**
 * A [NavDrawer] that slides in over the content.
 *
 * ```kotlin
 * ModalNavDrawer(visible = menuOpen, onDismissRequest = { menuOpen = false }) {
 *     NavDrawerItem("Overview", Tabler.Outline.LayoutDashboard, selected, ::go)
 * }
 * ```
 *
 * The phone and small-tablet form. Renders through
 * [io.kontour.ui.sheet.SideSheet], so it stacks correctly with dialogs, dims
 * what is behind it and closes on a back gesture — all of which a hand-rolled
 * sliding panel has to reimplement.
 *
 * Comes in from the **leading** edge by default, which is where every platform
 * puts primary navigation, unlike a filter or detail panel.
 */
@Composable
fun ModalNavDrawer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    side: SheetSide = SheetSide.Start,
    width: Dp = NavDrawerDefaults.Width,
    dismissLabel: String = "Close navigation",
    paneTitle: String = "Navigation",
    indicatorColor: Color = Theme.colors.accentContainer,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SideSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        side = side,
        width = width,
        dismissLabel = dismissLabel,
        paneTitle = paneTitle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Theme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        ) {
            header?.invoke(this)
            DrawerItems(
                modifier = Modifier.weight(1f),
                indicatorColor = indicatorColor,
                content = content,
            )
            footer?.invoke(this)
        }
    }
}

/**
 * The drawer's scrolling destination list, with one travelling marker.
 *
 * The indicator group sits **inside** the scroll container, so the anchor and the
 * rows scroll together and the scroll offset never enters the arithmetic.
 *
 * Because a row reports through a composition local rather than a parameter, a
 * destination nested inside a collapsible [NavDrawerGroup] reports correctly too
 * — which a mechanism that only saw its direct children could not manage.
 */
@Composable
private fun DrawerItems(
    modifier: Modifier,
    indicatorColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val indicator = rememberSelectionIndicatorState()

    Box(modifier.verticalScroll(rememberScrollState())) {
        SelectionIndicatorBox(
            state = indicator,
            // A pill around the whole row, matching the rail. The two surfaces
            // show the same list at different widths and should mark it the
            // same way.
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
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                content = content,
            )
        }
    }
}

/**
 * One destination in a drawer.
 *
 * @param nestLevel How deep this sits under a [NavDrawerGroup]. Indents, and
 *   nothing else: a nested item is still a destination, so it keeps its full
 *   target and its own tonal pill.
 */
@Composable
fun NavDrawerItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    badge: Int? = null,
    enabled: Boolean = true,
    nestLevel: Int = 0,
    key: Any = label,
    contentDescription: String? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = Theme.colors
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = Theme.shapes.medium
    // Inside a group the travelling marker carries selection; on its own the row
    // still needs to say which one it is.
    val grouped = LocalSelectionIndicator.current != null

    val container by animateColorAsState(
        targetValue = if (selected && !grouped) colors.accentContainer else Color.Transparent,
        animationSpec = motion.tweenFast(),
        label = "drawerItemContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> colors.contentDisabled
            selected -> colors.onAccentContainer
            // `contentMuted`, matching the bar and the rail. This used to be
            // `content`, which left the selected/unselected difference here
            // weaker than in either of its siblings.
            else -> colors.contentMuted
        },
        animationSpec = motion.tweenFast(),
        label = "drawerItemContent",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = NavDrawerDefaults.NestIndent * nestLevel)
            .selectionIndicatorItem(key, selected)
            .semantics(mergeDescendants = true) {
                contentDescription?.let { this.contentDescription = it }
            }
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .clip(shape)
            .background(container, shape)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = kontourIndication(shape, pressScale = 1f),
                enabled = enabled,
                role = Role.Tab,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    onClick()
                },
            )
            .padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                size = Theme.sizing.iconLarge,
                tint = content,
            )
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = Theme.typography.bodyMedium,
            color = content,
            maxLines = 1,
        )
        if (badge != null) {
            Badge(count = badge)
        }
    }
}

/**
 * A drawer item that expands to reveal its children.
 *
 * ```kotlin
 * NavDrawerGroup("Content", icon = Tabler.Outline.Folder, expanded = open, onExpandedChange = { open = it }) {
 *     NavDrawerItem("Routes", …, nestLevel = 1)
 *     NavDrawerItem("Stops", …, nestLevel = 1)
 * }
 * ```
 *
 * The shape the admin panel's sidebar needs, and the reason [NavDrawer] takes a
 * slot rather than a list.
 *
 * Expansion is hoisted, so the app decides whether a group opens because the
 * user asked or because the current destination is inside it. Auto-expanding the
 * group containing the current page is nearly always right and is not something
 * the component can know.
 */
@Composable
fun NavDrawerGroup(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    nestLevel: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Theme.colors
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val shape = Theme.shapes.medium

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "drawerGroupChevron",
    )

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = NavDrawerDefaults.NestIndent * nestLevel)
                .minimumTouchTarget()
                .focusRing(interactions, shape)
                .clip(shape)
                .selectable(
                    selected = expanded,
                    interactionSource = interactions,
                    indication = kontourIndication(shape, pressScale = 1f),
                    // Not a Tab: expanding a group does not navigate anywhere,
                    // and announcing it as a destination is a lie a screen
                    // reader user has to act on to discover.
                    role = Role.Button,
                    onClick = {
                        feedback.perform(FeedbackIntent.Selection)
                        onExpandedChange(!expanded)
                    },
                )
                .padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    size = Theme.sizing.iconLarge,
                    tint = colors.contentMuted,
                )
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = Theme.typography.bodyMedium,
                maxLines = 1,
            )
            Icon(
                imageVector = SystemIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
                size = Theme.sizing.iconMedium,
                tint = colors.contentMuted,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.tweenFast()),
            exit = shrinkVertically(motion.tweenFast()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                content = content,
            )
        }
    }
}

/** A labelled divider between groups of drawer items. */
@Composable
fun NavDrawerSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .padding(
                    start = Theme.spacing.md,
                    top = Theme.spacing.md,
                    bottom = Theme.spacing.xxs,
                )
                .semantics { heading() },
            style = Theme.typography.labelSmall,
            color = Theme.colors.contentMuted,
        )
        content()
    }
}
