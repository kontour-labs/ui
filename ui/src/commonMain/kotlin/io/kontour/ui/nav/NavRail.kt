package io.kontour.ui.nav

import io.kontour.ui.motion.exitOrFade
import io.kontour.ui.motion.enterOrFade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.optionalBorder
import io.kontour.ui.a11y.selectionEdge
import io.kontour.ui.adaptive.leadingEdges
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.LocalSelectionIndicatorLeaving
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.SurfaceDefaults
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.interaction.rememberTapFeedback
import io.kontour.ui.motion.ChevronTurn
import io.kontour.ui.motion.chevronTurn
import io.kontour.ui.overlay.AnchoredDropdownMenu
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.anchorBounds
import io.kontour.ui.sheet.LocalSideSheetGlass
import io.kontour.ui.sheet.ModalSideSheet
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** What a [NavRail] and a [ModalNavRail] take by default. */
object NavRailDefaults {
    /** A rail with room for its icons and nothing else. */
    val CollapsedWidth: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.navRailCollapsedWidth

    /** A rail with room for its labels: the width a sidebar is. */
    val ExpandedWidth: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.navRailExpandedWidth

    /**
     * The glyph's box in a rail's row, at every width.
     *
     * As wide as a collapsed rail holds without squeezing: a box that gets
     * clamped is a box the icon sits in differently at every width, which is
     * most of what made the icons move as a rail grew.
     *
     * As tall as the icon, and no taller. It was square, 48 by 48, which with
     * the row's own padding made every row 56dp and the sidebar a quarter
     * looser than the drawer it replaced. The height a row needs is the touch
     * target's, which the row reserves itself — so an icon row, a text row and
     * a group are all 48dp, and a sidebar reads at a drawer's density.
     */
    val GlyphSize: DpSize
        @Composable @ReadOnlyComposable get() =
            DpSize(Theme.componentDefaults.navInlineGlyph, Theme.sizing.iconLarge)

    /** How far a group's destinations sit in from the group, in a rail wide enough to show them. */
    val NestIndent: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.navNestIndent
}

/**
 * The primary navigation on a window with room beside the content. **Goes down
 * the leading edge.** Collapsed it is a column of icons; expanded it is a
 * sidebar, with labels, section titles and groups that open in place.
 *
 * ```kotlin
 * Row(Modifier.fillMaxSize()) {
 *     NavRail(expanded = railOpen, onExpandedChange = { railOpen = it }) {
 *         item(here == Stops, { go(Stops) }, key = Stops, icon = Tabler.Outline.MapPin) { +"Stops" }
 *         item(here == Routes, { go(Routes) }, key = Routes, icon = Tabler.Outline.Route) { +"Routes" }
 *     }
 *     Content(Modifier.weight(1f))
 * }
 * ```
 *
 * A rail and a sidebar are one component at two widths. Growing, the icons stay
 * where they are and the rail reveals the labels beside them; section titles
 * take the place of the hairlines that stood for them; a group's icon, which
 * opened a menu of its destinations while there was no room, becomes a row that
 * opens and closes them below it.
 *
 * @param expanded Whether the rail is wide. Honoured with or without a toggle:
 *   `NavRail(expanded = true)` is a fixed sidebar.
 * @param onExpandedChange Gives the rail its toggle. `null` fixes it at
 *   [expanded]. Hoisted, because the app decides whether the rail is open.
 * @param revealSelected Whether the selected destination is scrolled into view
 *   when the rail first shows, for a long list whose current row is further
 *   down than the rail is tall.
 * @param itemAlignment Where the destinations sit when they are fewer than the
 *   rail is tall. `Top` by default; `CenterVertically` for a few icons on a tall
 *   window.
 * @param header Above the destinations, at their leading edge: a logo, an
 *   account. A column, laid out as one.
 * @param footer Below them: settings, a sign-out.
 * @param windowInsets What the rail's content keeps clear of — the status bar,
 *   the gesture bar, and the cutout on the leading side, which a landscape
 *   phone puts exactly where the rail is.
 */
@Composable
fun NavRail(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    revealSelected: Boolean = true,
    itemAlignment: Alignment.Vertical = Alignment.Top,
    collapsedWidth: Dp = NavRailDefaults.CollapsedWidth,
    expandedWidth: Dp = NavRailDefaults.ExpandedWidth,
    colours: NavColours = NavDefaults.colours(),
    expandLabel: String = Theme.strings.expandNavigation,
    collapseLabel: String = Theme.strings.collapseNavigation,
    windowInsets: WindowInsets = WindowInsets.leadingEdges,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable NavScope.() -> Unit,
) {
    val motion = Theme.motion
    val width by animateDpAsState(
        targetValue = if (expanded) expandedWidth else collapsedWidth,
        // `springGentle`: a rail growing to a sidebar is a large surface. At
        // once under reduced motion, where the rail and everything beside it
        // reflowing across 200dp is the movement; the labels fade in.
        animationSpec = if (motion.reduceMotion) snap() else motion.springOrTween(motion.springGentle),
        label = "navRailWidth",
    )

    // Whether the rail is *currently* wide enough for a label — not whether it
    // is heading there. A label composed on the frame the flag flips appears in
    // a rail with no room for it. This is the one thing that changes across an
    // expansion: the rows are the same rows at every width, so the icons do not
    // move (`NavRailStillnessTest`).
    val roomForLabels = if (expandedWidth <= collapsedWidth) {
        expanded
    } else {
        width > (collapsedWidth + expandedWidth) / 2
    }

    // Published to the header, the footer and anything in them, so slot content
    // answers the same question the destinations do from the animated width
    // rather than from a copy of the flag.
    val room = NavExpansion(
        expanded = roomForLabels,
        progress = if (expandedWidth <= collapsedWidth) {
            if (expanded) 1f else 0f
        } else {
            ((width - collapsedWidth) / (expandedWidth - collapsedWidth)).coerceIn(0f, 1f)
        },
        onSurface = true,
    )

    CompositionLocalProvider(LocalNavExpansion provides room) {
        Surface(
            modifier = modifier.width(width).fillMaxHeight(),
            containerColour = colours.container,
            contentColour = SurfaceDefaults.contentColour(colours.container),
            // The rail's ground against the page's is the tier's 1.00:1, so the
            // rail and the content beside it were one field of white.
            border = contrastEdge(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    // Inside the surface, so the rail's colour reaches the window's
                    // edge and the cutout sits on the rail.
                    .windowInsetsPadding(windowInsets)
                    .padding(horizontal = Theme.spacing.xs, vertical = Theme.spacing.md),
                // Leading-aligned at every width, as the rows are, so nothing
                // moves at the halfway frame of an expansion.
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            ) {
                if (onExpandedChange != null) {
                    // Centred on the rows' icons: a row's leading padding, then
                    // its glyph box.
                    Box(
                        modifier = Modifier
                            .padding(start = Theme.spacing.sm)
                            .width(NavRailDefaults.GlyphSize.width),
                        contentAlignment = Alignment.Center,
                    ) {
                        RailToggle(
                            expanded = expanded,
                            onExpandedChange = onExpandedChange,
                            expandLabel = expandLabel,
                            collapseLabel = collapseLabel,
                        )
                    }
                }
                if (header != null) {
                    // A column of its own, at the rows' content edge.
                    Column(Modifier.padding(start = Theme.spacing.sm), content = header)
                }
                RailItems(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    colours = colours,
                    room = roomForLabels,
                    revealSelected = revealSelected,
                    itemAlignment = itemAlignment,
                    afterClick = null,
                    content = content,
                )
                if (footer != null) {
                    Column(Modifier.padding(start = Theme.spacing.sm), content = footer)
                }
            }
        }
    }
}

/**
 * A [NavRail] that slides in over the content: the sidebar on a phone.
 *
 * ```kotlin
 * ModalNavRail(visible = menuOpen, onDismissRequest = { menuOpen = false }) {
 *     item(here == Stops, { go(Stops) }, key = Stops, icon = Tabler.Outline.MapPin) { +"Stops" }
 * }
 * ```
 *
 * Always at its widest, so its groups open in place. Renders through
 * [ModalSideSheet], so it stacks with dialogs, dims the page and closes on back
 * or by being dragged towards its edge.
 *
 * @param side The leading edge by default, where every platform puts primary
 *   navigation.
 * @param glass Frosted rather than painted: the page shows through the panel.
 * @param dismissOnItemClick Whether choosing a destination closes the rail. It
 *   calls the destination's `onClick` first and closes a frame later, so the new
 *   page is composed behind a panel that has not started moving: the expensive
 *   frame lands where nothing is animating, rather than inside the exit.
 */
@Composable
fun ModalNavRail(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    side: SheetSide = SheetSide.Start,
    width: Dp = NavRailDefaults.ExpandedWidth,
    glass: Boolean = false,
    colours: NavColours = NavDefaults.colours(container = Theme.colours.surfaceRaised),
    revealSelected: Boolean = true,
    dismissOnItemClick: Boolean = true,
    dismissLabel: String = Theme.strings.closeNavigation,
    paneTitle: String = Theme.strings.navigation,
    windowInsets: WindowInsets = WindowInsets.leadingEdges,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable NavScope.() -> Unit,
) {
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        // The destination is composed by now but not drawn. One frame, with
        // nothing moving on it, buys it that before the exit starts.
        withFrameNanos { }
        onDismissRequest()
        closing = false
    }

    CompositionLocalProvider(LocalSideSheetGlass provides glass) {
        ModalSideSheet(
            visible = visible,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            side = side,
            width = width,
            containerColour = colours.container,
            contentColour = SurfaceDefaults.contentColour(colours.container),
            dismissLabel = dismissLabel,
            paneTitle = paneTitle,
            windowInsets = windowInsets,
        ) {
            CompositionLocalProvider(LocalNavExpansion provides NavExpansion.Drawer) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Theme.spacing.xs, vertical = Theme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                ) {
                    if (header != null) Column(Modifier.padding(start = Theme.spacing.sm), content = header)
                    RailItems(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        colours = colours,
                        room = true,
                        revealSelected = revealSelected,
                        itemAlignment = Alignment.Top,
                        afterClick = if (dismissOnItemClick) ({ closing = true }) else null,
                        content = content,
                    )
                    if (footer != null) Column(Modifier.padding(start = Theme.spacing.sm), content = footer)
                }
            }
        }
    }
}

/**
 * A rail's scrolling destinations, with one travelling marker inside the scroll
 * container, so the marker and the rows scroll together.
 */
@Composable
private fun RailItems(
    modifier: Modifier,
    colours: NavColours,
    room: Boolean,
    revealSelected: Boolean,
    itemAlignment: Alignment.Vertical,
    afterClick: (() -> Unit)?,
    content: @Composable NavScope.() -> Unit,
) {
    val indicator = rememberSelectionIndicatorState()
    val scroll = rememberScrollState()
    val keys = remember { NavKeys() }
    val glyph = NavRailDefaults.GlyphSize
    val indent = NavRailDefaults.NestIndent
    val scope = remember(keys, colours, room, glyph, indent, afterClick) {
        RailScope(keys, colours, room, glyph, indent, nest = 0, afterClick = afterClick)
    }

    // A rail opens on the page you are on, and a list of twenty opens showing the
    // first five. The selected row reports itself to the marker in the scroll
    // content's own coordinates, so its rect is the offset to scroll to. Snapped:
    // the row should be in place when the rail arrives, not slide there after.
    if (revealSelected) {
        LaunchedEffect(scroll) {
            val row = snapshotFlow { indicator.target }.filterNotNull().first()
            val margin = row.height / 2f
            val top = (row.top - margin).roundToInt().coerceAtLeast(0)
            val bottom = (row.bottom + margin).roundToInt()
            val destination = when {
                top < scroll.value -> top
                bottom > scroll.value + scroll.viewportSize -> bottom - scroll.viewportSize
                else -> return@LaunchedEffect
            }
            scroll.scrollTo(destination.coerceIn(0, scroll.maxValue))
        }
    }

    // One box for all the room between the header and the footer, the rows
    // scrolling inside it: a `Column` measures its children against what is
    // left, so rows past the end of a short rail were measured against nothing
    // and drawn squashed, or not at all.
    Box(
        modifier = modifier,
        contentAlignment = when (itemAlignment) {
            Alignment.Bottom -> Alignment.BottomStart
            Alignment.CenterVertically -> Alignment.CenterStart
            else -> Alignment.TopStart
        },
    ) {
        Column(Modifier.verticalScroll(scroll)) {
            SelectionIndicatorBox(
                state = indicator,
                // A pill around the whole row, narrower than it and exactly as
                // tall, travelling between rows.
                sizing = IndicatorSizing.Inset(horizontal = Theme.spacing.xxs, vertical = 0.dp),
                indicator = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(Theme.shapes.capsule)
                            .background(colours.indicator)
                            .optionalBorder(selectionEdge(), Theme.shapes.capsule)
                    )
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    // `xxs`, a drawer's gap: rows are already 48dp targets, and
                    // `xs` between them spread a sidebar of twenty pages over a
                    // screen and a half.
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                ) {
                    scope.content()
                }
            }
        }
    }
}

/** A rail's [NavScope], at one nesting depth and one width. */
private class RailScope(
    private val keys: NavKeys,
    private val colours: NavColours,
    private val room: Boolean,
    private val glyph: DpSize,
    private val indent: Dp,
    private val nest: Int,
    private val afterClick: (() -> Unit)?,
) : NavScope {

    /** Indented only while the rail is wide enough to show a group's destinations at all. */
    private val rowIndent: Dp get() = if (room) indent * nest else 0.dp

    @Composable
    override fun item(
        selected: Boolean,
        onClick: () -> Unit,
        key: Any,
        icon: ImageVector?,
        modifier: Modifier,
        enabled: Boolean,
        selectedIcon: ImageVector?,
        contentDescription: String?,
        interactionSource: MutableInteractionSource?,
        badge: (@Composable () -> Unit)?,
        label: NavLabelScope.() -> Unit,
    ) {
        val text = navLabel(label)
        ClaimNavKey(keys, key)
        val after = afterClick
        NavDestination(
            label = text,
            icon = icon,
            selected = selected,
            onClick = {
                // The page first, the close second: see `ModalNavRail`.
                onClick()
                after?.invoke()
            },
            markerKey = key,
            colours = colours,
            layout = NavLayout.Inline,
            showLabel = room,
            indicatorSize = glyph,
            // Full width, so the marker sits at the same x for every row.
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            selectedIcon = selectedIcon,
            contentDescription = contentDescription,
            badge = badge,
            interactionSource = interactionSource,
            indent = rowIndent,
        )
    }

    @Composable
    override fun section(title: String, modifier: Modifier, content: @Composable NavScope.() -> Unit) {
        // One height whether it is a title or the hairline standing in for one,
        // so the icons below do not move as the rail grows.
        val wide = room
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(SectionHeight)
                .padding(start = rowIndent),
            contentAlignment = Alignment.BottomStart,
        ) {
            if (wide) {
                Text(
                    text = title,
                    style = Theme.typography.labelSmall,
                    colour = Theme.colours.contentMuted,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = Theme.spacing.sm, bottom = Theme.spacing.xxs)
                        .semantics { heading() },
                )
            } else {
                HorizontalDivider(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = Theme.spacing.sm)
                )
            }
        }
        content()
    }

    @Composable
    override fun group(
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        key: Any,
        icon: ImageVector?,
        header: NavLabelScope.() -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        interactionSource: MutableInteractionSource?,
        badge: (@Composable () -> Unit)?,
        content: @Composable NavScope.() -> Unit,
    ) {
        val text = navLabel(header)
        ClaimNavKey(keys, key)
        // With room for labels, a disclosure; without, a menu beside the rail.
        val disclose = room
        val showing = disclose && expanded
        var menuOpen by remember { mutableStateOf(false) }
        var bounds by remember { mutableStateOf<Rect?>(null) }
        val selection = rememberGroupSelection(hidden = !showing, content = content)
        // A disclosure taps both ways, as an `Accordion` does; a menu opening is
        // silent, as a menu's trigger is.
        val tap = rememberTapFeedback()
        LaunchedEffect(disclose) { if (disclose) menuOpen = false }

        val motion = Theme.motion
        val deeper = RailScope(keys, colours, room, glyph, indent, nest + 1, afterClick)
        // One child of the rail's spaced column, not two. As two, the header and
        // its children were separated by the column's gap whatever their height,
        // so a collapsing group held an extra gap until it was removed —
        // and every row below jumped up by it on that frame. The gap belongs to
        // the children and grows and shrinks with them.
        // Locals, because `Column` is a layout scope too, and the DSL marker
        // hides this scope's members inside it.
        val rowColours = colours
        val wide = room
        val size = glyph
        val start = rowIndent
        Column {
            NavDestination(
                label = text,
                icon = icon,
                selected = false,
                marked = !showing && selection.any,
                onClick = {
                    if (disclose) {
                        tap()
                        onExpandedChange(!expanded)
                    } else {
                        menuOpen = !menuOpen
                    }
                },
                markerKey = key,
                colours = rowColours,
                layout = NavLayout.Inline,
                showLabel = wide,
                indicatorSize = size,
                modifier = modifier.fillMaxWidth().anchorBounds { bounds = it },
                action = if (disclose) NavAction.Disclosure(expanded) else NavAction.Menu,
                enabled = enabled,
                badge = badge,
                interactionSource = interactionSource,
                indent = start,
                trailing = {
                    Icon(
                        imageVector = SystemIcons.ChevronDown,
                        contentDescription = null,
                        modifier = Modifier.chevronTurn(expanded, label = "navGroupChevron"),
                        size = Theme.sizing.iconMedium,
                        tint = if (enabled) rowColours.content else rowColours.contentDisabled,
                    )
                },
            )

            AnimatedVisibility(
                visible = showing,
                // A group's children take their room at once under reduced motion.
                enter = motion.enterOrFade(expandVertically(motion.tweenFast())),
                exit = motion.exitOrFade(shrinkVertically(motion.tweenFast())),
            ) {
                // Still composed while the exit runs, reporting smaller, higher rects
                // every frame; the marker must not follow them up the rail.
                CompositionLocalProvider(LocalSelectionIndicatorLeaving provides !showing) {
                    Column(
                        Modifier.padding(top = Theme.spacing.xxs),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                    ) {
                        deeper.content()
                    }
                }
            }
        }

        if (!disclose) {
            AnchoredDropdownMenu(
                visible = menuOpen,
                anchor = bounds,
                onDismissRequest = { menuOpen = false },
                side = OverlaySide.End,
            ) {
                NavMenuScope(this, dismiss = { menuOpen = false }).content()
            }
        }
    }

    @Composable
    override fun divider(modifier: Modifier) {
        HorizontalDivider(
            modifier
                .padding(start = rowIndent)
                .padding(horizontal = Theme.spacing.sm, vertical = Theme.spacing.xxs)
        )
    }
}

/**
 * The rail's expand and collapse control: a `Role.Button`, since growing the
 * rail goes nowhere. One chevron turned round rather than two swapped, so it
 * moves rather than changes, and it points the way the rail grows.
 */
@Composable
private fun RailToggle(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandLabel: String,
    collapseLabel: String,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val strings = Theme.strings
    IconButton(
        icon = if (rtl) SystemIcons.ChevronLeft else SystemIcons.ChevronRight,
        contentDescription = if (expanded) collapseLabel else expandLabel,
        onClick = { onExpandedChange(!expanded) },
        rotation = if (expanded) ChevronTurn else 0f,
        modifier = Modifier.semantics {
            stateDescription = if (expanded) strings.expanded else strings.collapsed
        },
    )
}

/** A section's title, or the hairline that stands in for it. */
private val SectionHeight: Dp = 32.dp
