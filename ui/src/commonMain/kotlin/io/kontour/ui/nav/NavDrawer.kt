package io.kontour.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.motion.chevronTurn
import io.kontour.ui.components.display.Badge
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
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
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.sheet.SideSheet
import io.kontour.ui.adaptive.leadingEdges
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

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
 *         NavDrawerItem(selected = here == Overview, onClick = ::go, key = Overview) {
 *             leading { +Tabler.Outline.LayoutDashboard }
 *             +"Overview"
 *         }
 *         NavDrawerSection(title = { +"Content" }) {
 *             NavDrawerItem(selected = here == Routes, onClick = { go(Routes) }, key = Routes) {
 *                 leading { +Tabler.Outline.Route }
 *                 +"Routes"
 *             }
 *             NavDrawerItem(selected = here == Stops, onClick = { go(Stops) }, key = Stops) {
 *                 leading { +Tabler.Outline.MapPin }
 *                 +"Stops"
 *             }
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
    containerColour: Color = Theme.colours.surface,
    contentColour: Color = Theme.colours.content,
    indicatorColour: Color = Theme.colours.accent.container,
    /**
     * Whether opening the drawer scrolls the selected destination into view.
     *
     * A drawer opens on the page you are already on, and a sidebar of twenty
     * destinations opens showing the first five — so on a phone the row that
     * says where you are is routinely off the bottom of it. Costs nothing on a
     * list that already fits.
     */
    revealSelected: Boolean = true,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * What the drawer's *content* keeps clear of. The status bar, the gesture bar
     * and the display cutout on the leading side by default.
     */
    windowInsets: WindowInsets = WindowInsets.leadingEdges,
    content: @Composable NavDrawerScope.() -> Unit,
) {
    // A drawer is the wide end of the same spectrum a rail moves along, so its
    // slots get the same answer a fully expanded rail's do — content written for
    // one works in the other without knowing which it is in.
    CompositionLocalProvider(
        LocalNavExpansion provides
            NavExpansion(expanded = true, progress = 1f, onSurface = true)
    ) {
    Surface(
        modifier = modifier
            .width(width)
            .fillMaxHeight(),
        colour = containerColour,
        contentColour = contentColour,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                // Inside the surface, so the drawer's colour still reaches the
                // edge of the window.
                .windowInsetsPadding(windowInsets)
                // `md`, not `sm`, so a drawer row's icon sits at the same x as a
                // rail row's. A rail puts its icon at 8dp of rail padding plus
                // 12dp of item padding plus half a 48dp glyph box — 44dp — and a
                // drawer row adds 16dp of its own to whatever this is, so 12
                // here left the two surfaces the window size class swaps between
                // disagreeing by four.
                .padding(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        ) {
            header?.invoke(this)

            DrawerItems(
                modifier = Modifier.weight(1f),
                indicatorColour = indicatorColour,
                revealSelected = revealSelected,
                content = content,
            )

            footer?.invoke(this)
        }
    }
    }
}

/**
 * A [NavDrawer] that slides in over the content.
 *
 * ```kotlin
 * ModalNavDrawer(visible = menuOpen, onDismissRequest = { menuOpen = false }) {
 *     NavDrawerItem(selected = here == Overview, onClick = ::go, key = Overview) {
 *         +"Overview"
 *     }
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
    dismissLabel: String = Theme.strings.closeNavigation,
    paneTitle: String = Theme.strings.navigation,
    indicatorColour: Color = Theme.colours.accent.container,
    /**
     * Whether opening the drawer scrolls the selected destination into view.
     *
     * A drawer opens on the page you are already on, and a sidebar of twenty
     * destinations opens showing the first five — so on a phone the row that
     * says where you are is routinely off the bottom of it. Costs nothing on a
     * list that already fits.
     */
    revealSelected: Boolean = true,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable NavDrawerScope.() -> Unit,
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
                .padding(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        ) {
            header?.invoke(this)
            DrawerItems(
                modifier = Modifier.weight(1f),
                indicatorColour = indicatorColour,
                revealSelected = revealSelected,
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
    indicatorColour: Color,
    revealSelected: Boolean,
    content: @Composable NavDrawerScope.() -> Unit,
) {
    val indicator = rememberSelectionIndicatorState()
    val scroll = rememberScrollState()

    // A drawer opens on the page you are already on, and on a phone a list of
    // twenty destinations opens showing the first five of them.
    //
    // The rect to scroll to is one the drawer already has: the selected row
    // reports itself to the indicator, in the anchor's space, and the anchor is
    // *inside* the scroll container — so the reported top is the row's offset
    // down the scrolling content and no coordinate conversion is needed. That
    // is the same property `SelectionIndicatorBox` relies on to keep the marker
    // under its row while the list scrolls.
    //
    // It waits for the report rather than reading it now: nothing has been laid
    // out on the first composition, so the rect arrives a frame or two later.
    // And it snaps rather than animating — the row should already be in place
    // when the drawer arrives, not slide there once it has.
    if (revealSelected) {
        LaunchedEffect(scroll) {
            val row = snapshotFlow { indicator.target }.filterNotNull().first()
            val margin = row.height / 2f
            val top = (row.top - margin).roundToInt().coerceAtLeast(0)
            val bottom = (row.bottom + margin).roundToInt()
            val destination = when {
                top < scroll.value -> top
                bottom > scroll.value + scroll.viewportSize ->
                    bottom - scroll.viewportSize
                else -> return@LaunchedEffect
            }
            scroll.scrollTo(destination.coerceIn(0, scroll.maxValue))
        }
    }

    Box(modifier.verticalScroll(scroll)) {
        SelectionIndicatorBox(
            state = indicator,
            // A pill around the whole row, matching the rail. The two surfaces
            // show the same list at different widths and should mark it the
            // same way: narrower than the row, and exactly as tall. Inset on
            // both axes the pill lost 8dp of its height, and the label sat
            // hard against its edge.
            sizing = IndicatorSizing.Inset(
                horizontal = Theme.spacing.xxs,
                vertical = 0.dp,
            ),
            indicator = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(Theme.shapes.pill)
                        .background(indicatorColour)
                )
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
            ) {
                NavDrawerScopeImpl(this).content()
            }
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
    selected: Boolean,
    onClick: () -> Unit,
    /**
     * This destination's identity, for the travelling selection marker.
     *
     * Required, where it used to default to `label`. With the label in a slot
     * there is nothing to default from — and a key derived from a *translated*
     * string was never right anyway: it changed under the marker whenever the
     * user changed language, which is exactly when it must not.
     */
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: Int? = null,
    nestLevel: Int = 0,
    contentDescription: String? = null,
    interactionSource: MutableInteractionSource? = null,
    content: ListItemScope.() -> Unit,
) {
    val slots = listItemSlots(content)
    val colours = Theme.colours
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = Theme.shapes.container
    // Inside a group the travelling marker carries selection; on its own the row
    // still needs to say which one it is.
    val grouped = LocalSelectionIndicator.current != null

    // Animated only where it can move. Inside a group the travelling pill
    // carries selection, so this target is `Transparent` whatever the row is
    // doing — and an `animateColorAsState` holding a constant is still an
    // `Animatable` and a launched coroutine per row. The documentation site's
    // index is 122 rows in one group, so that was 122 of each, allocated on the
    // first composition to animate nothing.
    val container = if (grouped) {
        Color.Transparent
    } else {
        val animated by animateColorAsState(
            targetValue = if (selected) colours.accent.container else Color.Transparent,
            animationSpec = motion.tweenFast(),
            label = "drawerItemContainer",
        )
        animated
    }
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> colours.contentDisabled
            selected -> colours.accent.onContainer
            // `contentMuted`, matching the bar and the rail. This used to be
            // `content`, which left the selected/unselected difference here
            // weaker than in either of its siblings.
            else -> colours.contentMuted
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
            .pointerCursor(enabled = enabled)
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
        ProvideContentColour(content) {
            slots.leading?.let { leading ->
                ContentSlot(iconSize = Theme.sizing.iconLarge, content = leading)
            }
            Box(Modifier.weight(1f)) {
                slots.label?.let { label ->
                    ProvideTextStyle(Theme.typography.bodyMedium) {
                        ContentSlot(maxLines = 1, content = label)
                    }
                }
            }
        }
        if (badge != null) {
            Badge(count = badge)
        }
    }
}

/**
 * A drawer item that expands to reveal its children.
 *
 * ```kotlin
 * NavDrawerGroup(
 *     expanded = open,
 *     onExpandedChange = { open = it },
 *     label = { +"Content" },
 *     icon = Tabler.Outline.Folder,
 * ) {
 *     NavDrawerItem(here == Routes, ::goRoutes, key = Routes, nestLevel = 1) { +"Routes" }
 *     NavDrawerItem(here == Stops, ::goStops, key = Stops, nestLevel = 1) { +"Stops" }
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
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    label: @Composable ContentScope.() -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    nestLevel: Int = 0,
    content: @Composable NavDrawerScope.() -> Unit,
) {
    val colours = Theme.colours
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val shape = Theme.shapes.container

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = NavDrawerDefaults.NestIndent * nestLevel)
                .minimumTouchTarget()
                .focusRing(interactions, shape)
                .clip(shape)
                .pointerCursor()
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
                    tint = colours.contentMuted,
                )
            }
            Box(Modifier.weight(1f)) {
                ProvideTextStyle(Theme.typography.bodyMedium) {
                    ContentSlot(maxLines = 1, content = label)
                }
            }
            Icon(
                imageVector = SystemIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.chevronTurn(expanded, label = "drawerGroupChevron"),
                size = Theme.sizing.iconMedium,
                tint = colours.contentMuted,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.tweenFast()),
            exit = shrinkVertically(motion.tweenFast()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
                // At level zero: a caller using the components directly passes
                // `nestLevel` per item, and the DSL's `group` re-wraps this with
                // the depth it is tracking.
                NavDrawerScopeImpl(this).content()
            }
        }
    }
}

/** A labelled divider between groups of drawer items. */
@Composable
fun NavDrawerSection(
    title: @Composable ContentScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable NavDrawerScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
    ) {
        Box(
            Modifier
                .padding(
                    start = Theme.spacing.md,
                    top = Theme.spacing.md,
                    bottom = Theme.spacing.xxs,
                )
                .semantics(mergeDescendants = true) { heading() }
        ) {
            ProvideTextStyle(Theme.typography.labelSmall) {
                ProvideContentColour(Theme.colours.contentMuted) {
                    ContentSlot(iconSize = Theme.sizing.iconSmall, content = title)
                }
            }
        }
        NavDrawerScopeImpl(this).content()
    }
}
