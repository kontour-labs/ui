package io.kontour.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
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
import io.kontour.ui.foundation.BarSurface
import io.kontour.ui.foundation.SurfaceDefaults
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.interaction.rememberTapFeedback
import io.kontour.ui.motion.ChevronTurn
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.motion.chevronTurn
import io.kontour.ui.motion.enterOrFade
import io.kontour.ui.motion.exitOrFade
import io.kontour.ui.overlay.AnchoredDropdownMenu
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.anchorBounds
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
    // Read only where it is used — the rail's own layout, and the lambdas below
    // that draw from it — so a rail growing lays out and draws every frame and
    // composes twice: when the labels start to show, and when they are the
    // names. Read here, it recomposed the rail, and everything that read
    // `LocalNavExpansion`, on every frame of the expansion.
    val railWidth = animateDpAsState(
        targetValue = if (expanded) expandedWidth else collapsedWidth,
        // `springGentle`: a rail growing to a sidebar is a large surface. At
        // once under reduced motion, where the rail and everything beside it
        // reflowing across 200dp is the movement; the labels fade in.
        animationSpec = if (motion.reduceMotion) snap() else motion.springOrTween(motion.springGentle),
        label = "navRailWidth",
    )
    val resizes = expandedWidth > collapsedWidth
    val progress: () -> Float = if (resizes) {
        remember(railWidth, collapsedWidth, expandedWidth) {
            { ((railWidth.value - collapsedWidth) / (expandedWidth - collapsedWidth)).coerceIn(0f, 1f) }
        }
    } else {
        val fixed = if (expanded) 1f else 0f
        remember(fixed) { { fixed } }
    }
    val reveal = rememberRailReveal(progress, reduceMotion = motion.reduceMotion)

    // Published to the header, the footer and anything in them, so slot content
    // answers the same question the destinations do from the animated width
    // rather than from a copy of the flag — once, with the width read through it.
    val room = remember(reveal.room, progress) {
        NavExpansion(expanded = reveal.room, progress = progress, onSurface = true)
    }

    CompositionLocalProvider(LocalNavExpansion provides room) {
        // A bar: a tap on the rail between its destinations goes no further.
        // See [BarSurface].
        BarSurface(
            modifier = modifier
                // Over what is beside it. A page sliding to or from the rail's
                // side — the one going as the next comes, both screens with the
                // rail — went over the rail, drawn after it, and was cut off
                // where the transition ended: *"the old screen goes above the
                // nav rail briefly and then cuts out of existence. it should go
                // behind the nav rail"*. A sum over the chain, so it holds
                // wherever a caller's modifier puts the rail.
                .zIndex(1f)
                .layout { measurable, constraints ->
                    val w = constraints.constrainWidth(railWidth.value.roundToPx())
                    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                    layout(w, placeable.height) { placeable.place(0, 0) }
                }
                .fillMaxHeight(),
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
                    reveal = reveal,
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
                    reveal = RailReveal.Full,
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

/**
 * A rail's scrolling destinations, with one travelling marker inside the scroll
 * container, so the marker and the rows scroll together.
 */
@Composable
private fun RailItems(
    modifier: Modifier,
    colours: NavColours,
    reveal: RailReveal,
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
    val scope = remember(keys, colours, reveal, glyph, indent, afterClick) {
        RailScope(keys, colours, reveal, glyph, indent, nest = 0, afterClick = afterClick)
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
        Column(Modifier.verticalScroll(scroll, flingBehavior = ScrollDefaults.flingBehavior())) {
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
                    scope.emit(content)
                }
            }
        }
    }
}

/** A rail's [NavScope], at one nesting depth. */
private class RailScope(
    private val keys: NavKeys,
    private val colours: NavColours,
    private val reveal: RailReveal,
    private val glyph: DpSize,
    private val indent: Dp,
    private val nest: Int,
    private val afterClick: (() -> Unit)?,
) : NavScope {

    /** Whether the rail is wide enough for labels to be the names. */
    private val room: Boolean get() = reveal.room

    /**
     * In from the rail's content edge by a group's depth — by as much of it as
     * the rail has opened, so nested rows slide into their indent as the rail
     * makes room for it rather than jumping there.
     */
    private fun Modifier.nested(): Modifier = startIndent(indent * nest, reveal.reveal)

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
        emitter().let { if (it !== this) return it.item(selected, onClick, key, icon, modifier, enabled, selectedIcon, contentDescription, interactionSource, badge, label) }
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
            modifier = modifier.fillMaxWidth().nested(),
            enabled = enabled,
            selectedIcon = selectedIcon,
            contentDescription = contentDescription,
            badge = badge,
            interactionSource = interactionSource,
            labelFade = reveal.fade,
            labelDrawn = reveal.labels,
            initialFade = reveal.initial,
        )
    }

    @Composable
    override fun section(title: String, modifier: Modifier, content: @Composable NavScope.() -> Unit) {
        emitter().let { if (it !== this) return it.section(title, modifier, content) }
        // One height whether it is a title or the hairline standing in for one,
        // so the icons below do not move as the rail grows. And both, while it
        // grows: the title fades in over the hairline as the labels do, rather
        // than the one replacing the other in a frame at the halfway point.
        val reveal = reveal
        val wide = room
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(SectionHeight)
                .nested(),
            contentAlignment = Alignment.BottomStart,
        ) {
            if (!reveal.settled) {
                HorizontalDivider(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = Theme.spacing.sm)
                        .graphicsLayer { alpha = 1f - reveal.fade() }
                )
            }
            if (reveal.labels) {
                Text(
                    text = title,
                    style = Theme.typography.labelSmall,
                    colour = Theme.colours.contentMuted,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = Theme.spacing.sm, bottom = Theme.spacing.xxs)
                        .graphicsLayer { alpha = reveal.fade() }
                        .then(if (wide) Modifier.semantics { heading() } else Modifier.clearAndSetSemantics {}),
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
        emitter().let { if (it !== this) return it.group(expanded, onExpandedChange, key, icon, header, modifier, enabled, interactionSource, badge, content) }
        val text = navLabel(header)
        ClaimNavKey(keys, key)
        // With room for labels, a disclosure; without, a menu beside the rail.
        val disclose = room
        val showing = disclose && expanded
        // An open group folds with the rail: its destinations close up as the
        // rail narrows, and are gone at the halfway point where the group
        // becomes a menu, rather than all vanishing on that frame. While they
        // are part-folded the group carries the marker, as it does when shut —
        // a destination half under the fold is not somewhere to point at.
        val folding = showing && reveal.folding
        val hidden = !showing || folding
        var menuOpen by remember { mutableStateOf(false) }
        var bounds by remember { mutableStateOf<Rect?>(null) }
        val selection = rememberGroupSelection(hidden = hidden, content = content)
        // A disclosure taps both ways, as an `Accordion` does; a menu opening is
        // silent, as a menu's trigger is.
        val tap = rememberTapFeedback()
        LaunchedEffect(disclose) { if (disclose) menuOpen = false }

        val motion = Theme.motion
        val deeper = RailScope(keys, colours, reveal, glyph, indent, nest + 1, afterClick)
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
        val rowReveal = reveal
        val start = Modifier.nested()
        Column {
            NavDestination(
                label = text,
                icon = icon,
                selected = false,
                marked = hidden && selection.any,
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
                modifier = modifier.fillMaxWidth().then(start).anchorBounds { bounds = it },
                action = if (disclose) NavAction.Disclosure(expanded) else NavAction.Menu,
                enabled = enabled,
                badge = badge,
                interactionSource = interactionSource,
                labelFade = rowReveal.fade,
                labelDrawn = rowReveal.labels,
                initialFade = rowReveal.initial,
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
                CompositionLocalProvider(LocalSelectionIndicatorLeaving provides hidden) {
                    Column(
                        Modifier
                            .foldTo(rowReveal.reveal)
                            .padding(top = Theme.spacing.xxs),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                    ) {
                        deeper.emit(content)
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
                NavMenuScope(this, dismiss = { menuOpen = false }).emit(content)
            }
        }
    }

    @Composable
    override fun divider(modifier: Modifier) {
        emitter().let { if (it !== this) return it.divider(modifier) }
        HorizontalDivider(
            modifier
                .nested()
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

/**
 * How far a rail has opened, in the three readings its rows need.
 *
 * @property room Whether labels are the names yet: past halfway. Composed, so it
 *   changes once per expansion.
 * @property labels Whether labels are drawn at all — for as long as they have
 *   any opacity, which under reduced motion outlasts [room] while they fade.
 * @property settled Whether the labels are fully shown, so the hairlines under
 *   section titles can go.
 * @property folding Whether an open group is part-folded.
 * @property fade How visible labels, titles and chevrons are, frame by frame.
 * @property reveal How far open the rail's second half is: 0 at halfway, 1 at
 *   its widest. What nested rows are indented and open groups unfolded by.
 * @property initial How visible an initial standing in for a missing icon is.
 */
@Stable
private class RailReveal(
    val room: Boolean,
    val labels: Boolean,
    val settled: Boolean,
    val folding: Boolean,
    val fade: () -> Float,
    val reveal: () -> Float,
    val initial: () -> Float,
) {
    companion object {
        /** A modal rail's: always at its widest. */
        val Full = RailReveal(
            room = true, labels = true, settled = true, folding = false,
            fade = { 1f }, reveal = { 1f }, initial = { 0f },
        )
    }
}

/**
 * The rail's reveal, from its width's [progress].
 *
 * Labels fade in over the second half of an expansion, where there is room for
 * them, and out over the same half of a collapse: the width decides, so a rail
 * dragged halfway shows half-faded labels rather than labels on a timer. Under
 * reduced motion the width jumps, and the labels fade on a clock instead.
 */
@Composable
private fun rememberRailReveal(progress: () -> Float, reduceMotion: Boolean): RailReveal {
    val reveal = remember(progress) { { ((progress() - LabelsFrom) / (1f - LabelsFrom)).coerceIn(0f, 1f) } }
    val room by remember(progress) { derivedStateOf { progress() > LabelsFrom } }
    val fade: () -> Float = if (reduceMotion) {
        val clock = animateFloatAsState(
            targetValue = if (room) 1f else 0f,
            animationSpec = Theme.motion.tweenDefault(),
            label = "navRailLabels",
        )
        remember(clock) { { clock.value } }
    } else {
        reveal
    }
    val initial = remember(progress) {
        { 1f - ((progress() - InitialFrom) / (LabelsFrom - InitialFrom)).coerceIn(0f, 1f) }
    }
    val shown by remember(fade) { derivedStateOf { fade() > 0f } }
    val settled by remember(fade) { derivedStateOf { fade() >= 1f } }
    val folding by remember(reveal) { derivedStateOf { reveal() < 1f } }
    val labels = room || shown
    return remember(room, labels, settled, folding, fade, reveal, initial) {
        RailReveal(room, labels, settled, folding, fade, reveal, initial)
    }
}

/**
 * Where across its growth a rail has room for labels: the halfway point, where
 * a row's glyph and the gap beside it have been paid for and a word starts to fit.
 */
private const val LabelsFrom = 0.5f

/** Where an initial standing in for a missing icon starts giving way to its word. */
private const val InitialFrom = 0.25f

/**
 * Pads the start by [share] of [indent], in layout, so a changing share moves
 * the content without recomposing it.
 */
private fun Modifier.startIndent(indent: Dp, share: () -> Float): Modifier =
    if (indent == 0.dp) {
        this
    } else {
        layout { measurable, constraints ->
            val px = (indent.toPx() * share()).roundToInt().coerceIn(0, constraints.maxWidth)
            val placeable = measurable.measure(constraints.offset(horizontal = -px))
            layout(placeable.width + px, placeable.height) { placeable.placeRelative(px, 0) }
        }
    }

/**
 * Shows [share] of the content's height, from its top, and clips the rest: a
 * run of rows closing up like a blind, measured once at full height. Clipped
 * only while part-folded, so a focus ring on the last row is whole at rest.
 */
private fun Modifier.foldTo(share: () -> Float): Modifier =
    drawWithContent {
        if (share() < 1f) clipRect { this@drawWithContent.drawContent() } else drawContent()
    }.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height * share()).roundToInt().coerceIn(constraints.minHeight, placeable.height)
        layout(placeable.width, height) { placeable.place(0, 0) }
    }
