package io.kontour.ui.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.a11y.optionalBorder
import io.kontour.ui.a11y.selectionEdge
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.adaptive.bottomEdges
import io.kontour.ui.adaptive.rememberHandleClearingInsets
import io.kontour.ui.foundation.LocalContainerColour
import io.kontour.ui.foundation.SelectionIndicatorState
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.IndicatorReach
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.BarSurface
import io.kontour.ui.foundation.SurfaceDefaults
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.components.selection.SliderDefaults
import io.kontour.ui.interaction.DetentTicker
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.horizontalDragOwning
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.overlay.clearedByToasts
import io.kontour.ui.overlay.AnchoredDropdownMenu
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.anchorBounds
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlin.math.roundToInt

/** What a [NavBar] takes by default. */
object NavBarDefaults {
    /** How far the bar's destinations sit in from the window's sides. */
    val Inset: Dp
        @Composable @ReadOnlyComposable get() = Theme.spacing.md

    /**
     * A destination's circle. 40dp, not the touch target's 48: the circle is
     * what is seen, and the target around it is reserved without being drawn.
     */
    val CircleSize: DpSize
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.navCircleSize.let { DpSize(it, it) }

    /**
     * The shortest the row can be: the larger of a circle and the touch target
     * around it. A first-frame estimate only — [NavigationSuiteScaffold] measures
     * the real height, which grows with type size and labels.
     */
    val MinHeight: Dp
        @Composable @ReadOnlyComposable get() = maxOf(Theme.sizing.minTouchTarget, CircleSize.height)

    /**
     * The widest a weighted [NavBarScope.slot] grows — the search, most often.
     *
     * A weighted slot takes the room the destinations leave, and on a wide window
     * that was all of it: a bar with a search in it ran the window's full width
     * however few destinations it held, a 900dp search field beside three
     * circles. Capped, the slot takes what is left up to here, and a floating
     * bar on anything wider than a phone hugs its contents again.
     */
    val SlotMaxWidth: Dp = 320.dp

    /**
     * The widest a destination grows under [NavBarItemWidth.Equal]: shares of a
     * phone's width, and on anything wider the bar hugs destinations this wide
     * rather than spreading three of them across the window.
     */
    val EqualItemMaxWidth: Dp = 168.dp

    /**
     * A circle behind the glyph, [CircleSize] across: the marker a bar of
     * [NavBarItemWidth.Natural] destinations wants.
     *
     * @param size Its size. A square is a circle, anything else a capsule.
     * @param shape Its shape, where neither is wanted.
     */
    @Composable
    fun glyphMarker(
        size: DpSize = CircleSize,
        shape: Shape = navItemShape(size),
    ): NavBarMarker = NavBarMarker.Glyph(size, shape)

    /**
     * The default marker: around the whole destination, glyph and label — a tab
     * bar's highlight rather than a circle.
     *
     * @param shape Its shape. By default it follows the bar's: a capsule in a
     *   floating bar's capsule, the same distance from its edges all round, and
     *   the theme's medium corner in a docked bar, which has no corners to
     *   follow.
     * @param inset How far in from the destination's own edges it stops.
     */
    @Composable
    fun destinationMarker(
        shape: Shape? = null,
        inset: Dp = 0.dp,
    ): NavBarMarker = NavBarMarker.Destination(shape, inset)

    /**
     * The gap between a floating bar's capsule and the action beside it: wider
     * than the gap between destinations, so the two read as neighbours rather
     * than as one bar with a bulge.
     */
    val ActionGap: Dp
        @Composable @ReadOnlyComposable get() = Theme.spacing.sm

    /** The gap between destinations when they are gathered in the middle. */
    val ItemGap: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.navBarItemGap

    /**
     * How a bar spreads its destinations: evenly across a phone's width, and
     * gathered in the middle on anything wider, where spreading three circles
     * across 1200dp stops them reading as one bar.
     *
     * Moot once a weighted slot is in the row: the slot takes the slack, up to
     * [SlotMaxWidth], and the row is packed and centred around it.
     *
     * @param spans Whether the bar has the window's whole width to itself — a
     *   [WindowWidthClass.Compact] window.
     */
    @Composable
    @ReadOnlyComposable
    fun arrangementFor(spans: Boolean): Arrangement.Horizontal =
        if (spans) Arrangement.SpaceEvenly else Arrangement.spacedBy(ItemGap, Alignment.CenterHorizontally)
}

/** Whether a [NavBar]'s marker can be dragged, and when a drag chooses a destination. */
enum class NavBarMarkerDrag {
    /** The marker cannot be dragged: a destination is chosen by a tap alone. */
    Off,

    /**
     * The marker follows the finger, and the destination under it when it lifts
     * is chosen, once. The default: a destination is a page, and each one passed
     * over is not built and thrown away.
     */
    SelectOnRelease,

    /**
     * The marker follows the finger, and each destination is chosen as the
     * marker reaches it, for pages cheap enough to scrub across.
     */
    SelectWhileDragging,
}

/** How wide a [NavBar]'s destinations are. */
enum class NavBarItemWidth {
    /**
     * As wide as each needs, spread across a phone's bar and gathered in the
     * middle of a wider one — see [NavBarDefaults.arrangementFor].
     */
    Natural,

    /**
     * All the same width, sharing the bar between them, up to
     * [NavBarDefaults.EqualItemMaxWidth] each: a tab bar's cells, and the
     * width a [NavBarMarker.Destination] wants to fill. The default.
     */
    Equal,
}

/**
 * The marker behind a [NavBar]'s current destination: what it covers, and its
 * shape. Made by [NavBarDefaults.glyphMarker] and
 * [NavBarDefaults.destinationMarker].
 */
@Immutable
sealed interface NavBarMarker {
    /**
     * Behind the glyph, [size] and in [shape] — a 40dp circle by default, with
     * the label, when there is one, under it.
     */
    @Immutable
    data class Glyph(val size: DpSize, val shape: Shape) : NavBarMarker

    /**
     * Around the whole destination, glyph and label, [inset] in from its edges
     * and in [shape], or in the bar's own when that is null. Pair it with
     * [NavBarItemWidth.Equal] for cells that fill the bar.
     */
    @Immutable
    data class Destination(val shape: Shape?, val inset: Dp) : NavBarMarker
}

/**
 * How a [NavBar] presents itself. A container decision only: the destinations
 * are the same either way, and a destination marker follows the container's
 * shape.
 */
enum class NavBarStyle {
    /**
     * A capsule inset from every edge, hovering over the content — the default.
     * The page runs under it, so it suits content that should be seen to
     * continue past the bar, and it hugs its destinations on a wide window.
     */
    Floating,

    /**
     * One surface along the bottom edge, the window's full width. A firmer
     * footing when the content behind the bar is a list rather than a map.
     */
    Docked,
}

/**
 * The primary navigation on a phone. **Goes at the bottom of the screen.**
 *
 * ```kotlin
 * NavBar {
 *     item(here == Home, { go(Home) }, key = Home, icon = Tabler.Outline.Home) { +"Home" }
 *     item(here == Map, { go(Map) }, key = Map, icon = Tabler.Outline.Map) { +"Map" }
 *     item(here == Saved, { go(Saved) }, key = Saved, icon = Tabler.Outline.Star) { +"Saved" }
 * }
 * ```
 *
 * The destinations share the bar in equal cells, and one marker, round the whole
 * of the current one, travels between them; [itemWidth] and [marker] give
 * circles instead. The content is a [NavBarScope]: destinations are
 * [NavScope.item]s, a [NavScope.group] is a destination that opens a menu of its
 * children above the bar, sections run inline
 * and dividers are left out, and [NavBarScope.slot] holds anything else that
 * belongs in the row — a search, a button.
 *
 * A floating action goes in [action], not in a slot:
 *
 * ```kotlin
 * NavBar(
 *     action = { FloatingActionButton(onClick = ::newTrip, icon = Tabler.Outline.Plus, contentDescription = "New trip") },
 * ) {
 *     item(here == Home, { go(Home) }, key = Home, icon = Tabler.Outline.Home) { +"Home" }
 *     item(here == Map, { go(Map) }, key = Map, icon = Tabler.Outline.Map) { +"Map" }
 * }
 * ```
 *
 * @param style [NavBarStyle.Floating], a capsule over the content, or
 *   [NavBarStyle.Docked], a surface along the bottom edge.
 * @param showLabels Off by default. A word under every icon is a row of words,
 *   and the few destinations of a bar are ones its user already knows. Hidden
 *   labels are still announced.
 * @param arrangement How the row distributes its children. Null derives it from
 *   the window's width: see [NavBarDefaults.arrangementFor].
 * @param shape The bar's own shape: a capsule for a floating bar and square for
 *   a docked one when null. A floating bar in a rounded rectangle is
 *   `RoundedCornerShape(16.dp)`.
 * @param itemWidth How wide each destination is: [NavBarItemWidth.Equal] cells
 *   sharing the bar by default, or [NavBarItemWidth.Natural].
 * @param marker What marks the current destination, and in what shape: round
 *   the whole destination by default, in the bar's shape, or
 *   [NavBarDefaults.glyphMarker] behind the glyph.
 * @param markerDrag Whether the marker can be dragged from one destination to
 *   another, as a segmented control's thumb can — it follows the finger and
 *   snaps from destination to destination, with a tick at each — and when the
 *   destination is chosen: [NavBarMarkerDrag.SelectOnRelease], the one under the
 *   finger as it lifts, or [NavBarMarkerDrag.SelectWhileDragging], each as the
 *   marker reaches it. A tap is still a tap either way.
 * @param fadeUnder For a floating bar: the page's colour, solid from the
 *   bar's middle down to the window's foot and thinning out a little above
 *   the capsule, so what scrolls under it fades away rather than running
 *   behind it. On by default: a floating bar is `surface`, and the rows of a
 *   list under it are too, so without it the bar was only a shadow apart from
 *   them. With it the capsule stands on a deeper shadow; without it, it has a
 *   rim, a hairline of its own ink. Never at high contrast, where nothing that
 *   is text fades, and the bar has its edge.
 * @param windowInsets What the bar keeps clear of. A floating bar over a system
 *   handle — iOS's home indicator, Android's gesture handle — keeps clear of the
 *   handle itself rather than of the whole strip the system keeps for it, and
 *   sits just above it. Android's three buttons, and any foot taller than the
 *   handle, are kept clear of whole.
 * @param action Something the screen does rather than somewhere it goes — a
 *   [io.kontour.ui.components.action.FloatingActionButton], usually. A floating
 *   bar puts it **beside** the capsule, on the page and centred on the capsule's
 *   height, and on a phone the capsule narrows to make room; so it has to bring
 *   its own surface, which a floating action button does. A docked bar has no
 *   beside, and puts it inside, after everything else.
 */
@Composable
fun NavBar(
    modifier: Modifier = Modifier,
    style: NavBarStyle = NavBarStyle.Floating,
    showLabels: Boolean = false,
    colours: NavColours = NavDefaults.colours(),
    arrangement: Arrangement.Horizontal? = null,
    shape: Shape? = null,
    itemWidth: NavBarItemWidth = NavBarItemWidth.Equal,
    marker: NavBarMarker = NavBarDefaults.destinationMarker(),
    markerDrag: NavBarMarkerDrag = NavBarMarkerDrag.SelectOnRelease,
    fadeUnder: Boolean = true,
    windowInsets: WindowInsets = WindowInsets.bottomEdges,
    action: (@Composable () -> Unit)? = null,
    content: @Composable NavBarScope.() -> Unit,
) {
    NavBarLayout(
        modifier = modifier,
        style = style,
        showLabels = showLabels,
        colours = colours,
        arrangement = arrangement,
        windowInsets = windowInsets,
        search = null,
        searchIndex = null,
        action = action,
        shape = shape,
        itemWidth = itemWidth,
        marker = marker,
        markerDrag = markerDrag,
        fadeUnder = fadeUnder,
        content = content,
    )
}

/**
 * The bar, with a search placed among its destinations by counting them: after
 * [searchIndex] of them, or after all of them when [searchIndex] is null or at
 * or past their number. Counted as the row is measured rather than as it is
 * composed, so a destination shown or hidden later cannot move the search out of
 * its place.
 */
@Composable
internal fun NavBarLayout(
    modifier: Modifier,
    style: NavBarStyle,
    showLabels: Boolean,
    colours: NavColours,
    arrangement: Arrangement.Horizontal?,
    windowInsets: WindowInsets,
    search: (@Composable () -> Unit)?,
    searchIndex: Int?,
    action: (@Composable () -> Unit)?,
    shape: Shape? = null,
    itemWidth: NavBarItemWidth = NavBarItemWidth.Equal,
    marker: NavBarMarker = NavBarDefaults.destinationMarker(),
    markerDrag: NavBarMarkerDrag = NavBarMarkerDrag.SelectOnRelease,
    fadeUnder: Boolean = true,
    content: @Composable NavBarScope.() -> Unit,
) {
    val indicator = rememberSelectionIndicatorState()
    val motion = Theme.motion
    val drag = remember { MarkerDrag() }
    val ticker = rememberDetentTicker(FeedbackIntent.Snap)
    val dragScope = rememberCoroutineScope()
    // The marker's lean toward the finger is the indicator's own now, so it
    // stretches as it leans, as a press on another destination makes it — see
    // `SelectionIndicatorBox`. The drag tells it where the finger is.
    drag.indicator = indicator
    val keys = remember { NavKeys() }
    // The glyph's box: the marker's size where the marker is behind the glyph,
    // and a circle's where it is around the whole destination.
    val circle = (marker as? NavBarMarker.Glyph)?.size ?: NavBarDefaults.CircleSize
    val markerShape = when (marker) {
        is NavBarMarker.Glyph -> marker.shape
        // Concentric with the bar: a capsule inside a floating bar's capsule,
        // [Destinations] keeping it the same distance from every edge, where a
        // fixed corner was a box in a pill once labels made the bar tall. A
        // docked bar is straight-edged, and a caller's own bar shape is theirs
        // to match.
        is NavBarMarker.Destination -> marker.shape ?: when {
            style == NavBarStyle.Floating && shape == null -> Theme.shapes.pill
            else -> Theme.shapes.medium
        }
    }
    val coversItem = marker is NavBarMarker.Destination
    val markerInset = (marker as? NavBarMarker.Destination)?.inset ?: 0.dp
    val equal = itemWidth == NavBarItemWidth.Equal
    val labelGap = Theme.spacing.xxs

    // A phone spans; anything wider takes only the room it needs.
    val spans = LocalWindowSizeClass.current.width == WindowWidthClass.Compact
    val rowArrangement = arrangement ?: NavBarDefaults.arrangementFor(spans)
    val hug = style == NavBarStyle.Floating && !spans

    // **The capsule's ends, as much room as between two circles.** They were
    // `sm` on top of the arrangement's own spacing, which on a phone spreads the
    // destinations *evenly* — with a share of the slack before the first and
    // after the last as well as between — so each end had its share *and* the
    // padding: about a third more room than between two circles, reported as
    // too much width on either side of the items. What is seen is the circle,
    // not its touch target, so the sum is done in circles: a destination's
    // target is wider than its circle by `inset` each side. Spread evenly, the
    // arrangement's own share is at the ends already, and the padding only has
    // to match what the targets add between two circles. Gathered in the middle
    // of a wider window, the ends take the whole gap. A caller's own
    // arrangement keeps the old padding, since it is not this sum.
    //
    // Cells that share the bar have no slack to spread: their ends are the
    // capsule's own padding, the same as its top and bottom, so a marker around
    // a whole cell sits the same distance from every edge.
    val inset = ((Theme.sizing.minTouchTarget - circle.width) / 2).coerceAtLeast(0.dp)
    val floatingEnds = when {
        equal -> Theme.spacing.xs
        arrangement != null -> Theme.spacing.sm
        spans -> inset
        else -> NavBarDefaults.ItemGap + inset
    }

    // A new scope when anything it draws with changes: the destinations are
    // composed from inside it, and a scope that changed in place would leave
    // them skipped with the old values.
    drag.selectsWhileDragging = markerDrag == NavBarMarkerDrag.SelectWhileDragging
    val dragging = if (markerDrag != NavBarMarkerDrag.Off) DragWiring(drag, ticker, dragScope) else null
    val scope = remember(keys, showLabels, colours, circle, labelGap, markerShape, coversItem, markerInset, dragging) {
        BarScope(keys, showLabels, colours, circle, labelGap, markerShape, coversItem, markerInset, drag, dragging)
    }

    @Composable
    fun Destinations(rowModifier: Modifier, inlineAction: (@Composable () -> Unit)?) {
        SelectionIndicatorBox(
            state = indicator,
            sizing = when (marker) {
                // The circle is the marker, sized to the glyph rather than the
                // destination, and against the top when a label hangs under it.
                is NavBarMarker.Glyph -> IndicatorSizing.Fixed(
                    width = circle.width,
                    height = circle.height,
                    verticalBias = if (showLabels) 0f else 0.5f,
                )
                // Or the destination itself, glyph and label.
                is NavBarMarker.Destination -> IndicatorSizing.Inset(marker.inset, marker.inset)
            },
            modifier = rowModifier,
            indicator = {
                // No shadow: the bar is the raised thing, and a marker casting
                // its own inside it is a second surface on the first.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(markerShape)
                        .background(colours.indicator)
                        .optionalBorder(selectionEdge(), markerShape)
                )
            },
        ) {
            BarRow(
                arrangement = rowArrangement,
                fill = !hug,
                equal = equal,
                searchIndex = searchIndex,
                slotsCentred = !showLabels,
                // The row itself takes no pointer input: a press between two
                // destinations has to fall through to the surface's blocker
                // under it. The drag is each destination's; see `BarScope.item`.
                modifier = Modifier.selectableGroup().onPlaced { drag.row = it },
            ) {
                scope.emit(content)
                if (search != null) {
                    Box(Modifier.layoutId(BarChild.Search), propagateMinConstraints = true) { BarSlot(search) }
                }
                if (inlineAction != null) {
                    // A slot at the end, but at least a circle's height rather
                    // than exactly one: a floating action button is taller than
                    // a circle, and a slot's fixed height would squash it.
                    Box(
                        Modifier.layoutId(BarChild.Slot(0f)).heightIn(min = circle.height),
                        contentAlignment = Alignment.Center,
                    ) { inlineAction() }
                }
            }
        }
    }

    // Clear of a system handle rather than of the whole strip kept for it: see
    // `HandleClearingInsets`. A docked bar's colour runs under the handle and
    // keeps its destinations above all of it, as iOS's tab bar does.
    val clear = rememberHandleClearingInsets(windowInsets, HandleReach)
    val fades = fadeUnder && !highContrast()
    val fadeColour = LocalContainerColour.current.takeOrElse { Theme.colours.background }

    when (style) {
        // The surface reaches the window's edge and the insets are applied
        // inside it, so its colour runs under the gesture bar.
        NavBarStyle.Docked -> BarGround(
            modifier = modifier.fillMaxWidth(),
            shape = shape ?: RectangleShape,
            colours = colours,
            shadow = Theme.elevation.low,
        ) {
            Destinations(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets)
                    .padding(horizontal = NavBarDefaults.Inset, vertical = Theme.spacing.xs),
                inlineAction = action,
            )
        }

        // Inset from every edge, so the page visibly continues underneath.
        NavBarStyle.Floating -> FloatingBar(
            modifier = modifier
                .fillMaxWidth()
                .then(if (fades) Modifier.fadeUnderBar(fadeColour, clear) else Modifier)
                .windowInsetsPadding(clear)
                .padding(horizontal = NavBarDefaults.Inset, vertical = Theme.spacing.xs),
            hug = hug,
            gap = NavBarDefaults.ActionGap,
            action = action,
        ) {
            // A capsule, concentric with the circles it holds: 20dp of their
            // radius and `xs` around them is half the bar's height.
            BarGround(
                modifier = Modifier,
                shape = shape ?: Theme.shapes.pill,
                colours = colours,
                // With the page faded out under it, the bar stands on the page's
                // own colour, and its shadow parts it from that rather than a
                // hairline: the rim was there to tell it from rows of `surface`
                // running behind it, and the fade leaves none there to tell it
                // from. A shadow alone has more to do then, so it is a little
                // deeper — the theme's own `high`, darker, rather than another
                // rank.
                shadow = if (fades) Theme.elevation.high.deepened(BarShadowDepth) else Theme.elevation.high,
                rim = !fades,
            ) {
                Destinations(
                    Modifier.padding(horizontal = floatingEnds, vertical = Theme.spacing.xs),
                    inlineAction = null,
                )
            }
        }
    }
}

/**
 * A floating bar: the capsule, and the action beside it at its end.
 *
 * Laid out rather than put in a `Row`, because the two are sized against each
 * other: the action takes what it needs first, and the capsule has what is left
 * — all of it when the bar spans a phone, so the capsule narrows as an action
 * arrives rather than the action being pushed off the edge. When the bar hugs,
 * the two are centred together, as one group. Each is centred on the other's
 * height, so a floating action button taller than the capsule stands level with
 * it rather than on the same floor.
 */
@Composable
private fun FloatingBar(
    modifier: Modifier,
    hug: Boolean,
    gap: Dp,
    action: (@Composable () -> Unit)?,
    capsule: @Composable () -> Unit,
) {
    Layout(contents = listOf(capsule, action ?: {}), modifier = modifier) { (capsules, actions), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val beside = actions.firstOrNull()?.measure(loose)
        val gapPx = if (beside != null) gap.roundToPx() else 0
        val taken = (beside?.width ?: 0) + gapPx
        val bounded = constraints.hasBoundedWidth
        val room = if (bounded) (constraints.maxWidth - taken).coerceAtLeast(0) else Constraints.Infinity
        val bar = capsules.first().measure(
            loose.copy(minWidth = if (hug || !bounded) 0 else room, maxWidth = room),
        )
        val group = bar.width + taken
        val width = if (bounded) constraints.maxWidth else group
        val height = max(max(bar.height, beside?.height ?: 0), constraints.minHeight)
        layout(width, height) {
            val start = ((width - group) / 2).coerceAtLeast(0)
            bar.placeRelative(start, (height - bar.height) / 2)
            beside?.placeRelative(start + bar.width + gapPx, (height - beside.height) / 2)
        }
    }
}

/** The bar's surface. */
@Composable
private fun BarGround(
    modifier: Modifier,
    shape: Shape,
    colours: NavColours,
    shadow: Shadow,
    rim: Boolean = false,
    content: @Composable () -> Unit,
) {
    val contentColour = SurfaceDefaults.contentColour(colours.container)
    // **A tap on the bar is the bar's.** Between two destinations there is
    // nothing to press, and with nothing there to take it a tap went through to
    // whatever the page had under the bar — a row opened from underneath its
    // navigation. A bar surface keeps what lands on it, inside the capsule's
    // shape: just off the rounded ends is still the page. See [BarSurface].
    BarSurface(
        // Where it is, for a bottom toast stack to stand above it.
        modifier = modifier.clearedByToasts(),
        shape = shape,
        containerColour = colours.container,
        contentColour = contentColour,
        shadow = shadow,
        // A floating bar is `surface` on `background` with a shadow for an
        // edge, and those two grounds are 1.00:1 at the enhanced light tier.
        //
        // And a rim at any tier where the page does not fade out under it, as a
        // toast's pill has, for the same reason: over the rows of a list, which
        // are `surface` too, a shadow alone did not part the bar from them —
        // *"it blends in a lot with content behind it, especially when you're
        // scrolling over list items"*. A hairline of the bar's own ink, faint,
        // reads as the edge catching the light.
        border = contrastEdge() ?: if (rim) BorderStroke(Theme.sizing.borderWidth, contentColour.copy(alpha = BarRim)) else null,
    ) {
        content()
    }
}

/**
 * The page's [colour] under a floating bar, solid from the bar's middle down to
 * the window's foot, through [insets], and thinning out to nothing [FadeAbove]
 * above the bar. Drawn behind the bar, over whatever scrolls under it.
 */
private fun Modifier.fadeUnderBar(colour: Color, insets: WindowInsets): Modifier = drawWithCache {
    val foot = insets.getBottom(this).toFloat()
    // On a whole pixel, so the solid part and the fade meet without a seam.
    val middle = ((size.height - foot) / 2f).toInt().toFloat()
    val top = -FadeAbove.toPx()
    val brush = easedFade(colour, solidAt = middle, clearAt = top)
    onDrawBehind {
        drawRect(brush, topLeft = Offset(0f, top), size = Size(size.width, middle - top))
        drawRect(colour, topLeft = Offset(0f, middle), size = Size(size.width, size.height - middle))
    }
}

/**
 * How far above a floating bar its fade reaches: far enough that the rows
 * beside its rounded ends are mostly gone, and near enough that the fade is
 * the bar's and not a wash over the page.
 */
private val FadeAbove = 16.dp

/** How strongly a floating bar's rim is drawn: a toast's pill's, for the same reason. */
private const val BarRim = 0.22f

/**
 * How much darker a floating bar's shadow is than `high` when the page fades
 * out under it and the rim is left off.
 */
private const val BarShadowDepth = 1.5f

/** [this] shadow with every layer [by] times as strong. */
private fun Shadow.deepened(by: Float): Shadow =
    Shadow(layers.map { it.copy(alpha = (it.alpha * by).coerceAtMost(1f)) })

/**
 * How far up from the foot of the window a floating bar keeps clear of a
 * system handle: where the handle ends, and some air above it. iOS's home
 * indicator tops out about 13pt up, and Android's gesture handle, centred in its
 * strip, a little higher, at about 14. With the bar's own `xs` of padding on
 * top, the capsule's foot is 28dp up.
 *
 * It was 14, the handle's top alone, so the bar's padding was the whole gap:
 * *"the floating navbar needs to sit just a bit higher, it's now too close to
 * the home bar (at least on android, maybe a bit on ios too)"*. Before that it
 * was the whole strip, 42dp up on an iPhone and 32 over Android's gestures,
 * which was too high the other way.
 */
private val HandleReach = 20.dp

/** What a child of the bar's row is, so the row can count destinations and share out room. */
private sealed interface BarChild {
    data object Destination : BarChild

    data class Slot(val weight: Float) : BarChild

    data object Search : BarChild
}

private val Measurable.barChild: BarChild get() = layoutId as? BarChild ?: BarChild.Slot(0f)

private val BarChild.weight: Float
    get() = when (this) {
        BarChild.Destination -> 0f
        is BarChild.Slot -> weight
        BarChild.Search -> 1f
    }

/**
 * A row that is a `Row` with one addition: the search goes directly after the
 * right number of destinations, wherever it was composed.
 *
 * Directly after, and so ahead of any slot that follows them. An index at or
 * past the count means after the *last destination*, not after everything: the
 * suite adds its action as a slot after the destinations, and "at the end" used
 * to put the search behind the action it is supposed to precede.
 */
@Composable
private fun BarRow(
    arrangement: Arrangement.Horizontal,
    fill: Boolean,
    /** Destinations share the room, as a weighted slot does: [NavBarItemWidth.Equal]. */
    equal: Boolean,
    searchIndex: Int?,
    slotsCentred: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    // Between equal cells and anything else in the row: see `gapAfter`.
    val cellToSlot = NavBarDefaults.ItemGap
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val search = measurables.firstOrNull { it.barChild == BarChild.Search }
        val rest = measurables.filter { it !== search }
        val ordered = if (search == null) {
            rest
        } else {
            val destinations = rest.indices.filter { rest[it].barChild == BarChild.Destination }
            val after = minOf(searchIndex ?: Int.MAX_VALUE, destinations.size)
            val at = when {
                destinations.isEmpty() -> rest.size
                after == 0 -> destinations.first()
                else -> destinations[after - 1] + 1
            }
            ArrayList<Measurable>(measurables.size).apply {
                addAll(rest)
                add(at, search)
            }
        }

        // Equal cells touch: the space between them is inside them. A slot or an
        // action beside one keeps the usual gap, or a marker round the last
        // destination met the action beside it edge to edge.
        val gap = arrangement.spacing.roundToPx()
        val slotGap = cellToSlot.roundToPx()
        fun gapAfter(i: Int): Int = when {
            i >= ordered.lastIndex -> 0
            !equal -> gap
            ordered[i].barChild == BarChild.Destination && ordered[i + 1].barChild == BarChild.Destination -> 0
            else -> slotGap
        }
        val gaps = ordered.indices.sumOf { gapAfter(it) }
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = arrayOfNulls<Placeable>(ordered.size)
        // With a weighted slot in the row — a search — equal cells are equal to
        // each other but no wider than the widest needs, and the slot has the
        // rest. Sharing the room with it, four destinations left a phone's search
        // a fifth of the bar.
        val slotted = ordered.any { it.barChild != BarChild.Destination && it.barChild.weight > 0f }
        val cellWidth = if (equal && slotted) {
            ordered.filter { it.barChild == BarChild.Destination }.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
        } else {
            0
        }
        fun weightOf(child: Measurable): Float =
            if (equal && !slotted && child.barChild == BarChild.Destination) 1f else child.barChild.weight
        var used = gaps
        ordered.forEachIndexed { i, child ->
            if (weightOf(child) == 0f) {
                val room = if (constraints.hasBoundedWidth) (constraints.maxWidth - used).coerceAtLeast(0) else Constraints.Infinity
                placeables[i] = if (equal && slotted && child.barChild == BarChild.Destination) {
                    val width = cellWidth.coerceAtMost(room)
                    child.measure(loose.copy(minWidth = width, maxWidth = width))
                } else {
                    child.measure(loose.copy(maxWidth = room))
                }
                used += placeables[i]!!.width
            }
        }
        val totalWeight = ordered.sumOf { weightOf(it).toDouble() }.toFloat()
        // A weighted child's share of the room left over, but never more than
        // `SlotMaxWidth`: see there. An equal destination's cap is its own.
        val slotCap = NavBarDefaults.SlotMaxWidth.roundToPx()
        val cellCap = NavBarDefaults.EqualItemMaxWidth.roundToPx()
        if (totalWeight > 0f) {
            val left = if (constraints.hasBoundedWidth) (constraints.maxWidth - used).coerceAtLeast(0) else 0
            ordered.forEachIndexed { i, child ->
                val weight = weightOf(child)
                if (weight > 0f) {
                    val cap = if (child.barChild == BarChild.Destination) cellCap else slotCap
                    val width = (left * weight / totalWeight).roundToInt().coerceAtMost(cap)
                    placeables[i] = child.measure(loose.copy(minWidth = width, maxWidth = width))
                    used += width
                }
            }
        }
        val all = placeables.map { it!! }
        val contentWidth = all.sumOf { it.width } + gaps
        // A weighted child no longer implies the full width: capped, it can
        // leave room over, and a bar that hugs its contents should.
        val width = if (fill && constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            contentWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        }
        val height = max(all.maxOfOrNull { it.height } ?: 0, constraints.minHeight)
        val aligned = ordered.map { it.barChild }

        layout(width, height) {
            val x = IntArray(all.size)
            if (totalWeight > 0f || !fill) {
                // Packed, gap apart, and centred in what is left.
                var at = ((width - contentWidth) / 2).coerceAtLeast(0)
                for (i in all.indices) {
                    x[i] = at
                    at += all[i].width + gapAfter(i)
                }
                all.forEachIndexed { i, p -> p.placeRelative(x[i], childTop(aligned[i], p, height, slotsCentred)) }
            } else {
                with(arrangement) { arrange(width, IntArray(all.size) { all[it].width }, layoutDirection, x) }
                all.forEachIndexed { i, p -> p.place(x[i], childTop(aligned[i], p, height, slotsCentred)) }
            }
        }
    }
}

/**
 * Where a child sits vertically: level with the circles. A fixed-width slot sits
 * against the top when labels hang under the circles, which keep their place as
 * a label makes the row taller; without labels the circles are centred in their
 * targets, and so is it. Everything else is centred.
 */
private fun childTop(child: BarChild, placeable: Placeable, height: Int, slotsCentred: Boolean): Int =
    if (child is BarChild.Slot && child.weight == 0f && !slotsCentred) 0 else (height - placeable.height) / 2

/** A bar's [NavBarScope]. */
private class BarScope(
    private val keys: NavKeys,
    private val showLabels: Boolean,
    private val colours: NavColours,
    private val circle: DpSize,
    private val labelGap: Dp,
    private val markerShape: Shape,
    private val coversItem: Boolean,
    private val markerInset: Dp,
    private val drag: MarkerDrag,
    /** Null when the marker cannot be dragged. */
    private val dragging: DragWiring?,
) : NavBarScope {

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
        val target = remember(drag) { DragTarget() }
        SideEffect {
            target.onClick = onClick
            target.enabled = enabled
            target.selected = selected
        }
        DisposableEffect(drag, key) {
            drag.targets[key] = target
            onDispose { drag.targets.remove(key) }
        }
        NavDestination(
            label = text,
            icon = icon,
            selected = selected,
            // While the marker is dragged, the destination under the finger
            // wears it — colours and all — and the chosen one waits for the lift.
            marked = drag.marks(key, selected),
            onClick = onClick,
            markerKey = key,
            colours = colours,
            layout = NavLayout.Stacked,
            showLabel = showLabels,
            indicatorSize = circle,
            modifier = modifier
                .layoutId(BarChild.Destination)
                .onPlaced { target.coordinates = it }
                .then(if (dragging != null) Modifier.markerDrag(dragging, target, enabled) else Modifier),
            enabled = enabled,
            selectedIcon = selectedIcon,
            contentDescription = contentDescription,
            badge = badge,
            interactionSource = interactionSource,
            labelGap = labelGap,
            markerShape = markerShape,
            markerCoversItem = coversItem,
            markerInset = markerInset,
        )
    }

    /** Inline: a bar has no room for a title, and its destinations are few. */
    @Composable
    override fun section(title: String, modifier: Modifier, content: @Composable NavScope.() -> Unit) {
        emitter().let { if (it !== this) return it.section(title, modifier, content) }
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
        var open by remember { mutableStateOf(false) }
        var bounds by remember { mutableStateOf<Rect?>(null) }
        val selection = rememberGroupSelection(hidden = true, content = content)
        NavDestination(
            label = text,
            icon = icon,
            selected = false,
            // Not a place the marker can be dragged to: while it is being
            // dragged it is wherever the finger is, never on the group.
            marked = selection.any && !drag.previewing,
            onClick = { open = !open },
            markerKey = key,
            colours = colours,
            layout = NavLayout.Stacked,
            showLabel = showLabels,
            indicatorSize = circle,
            modifier = modifier.layoutId(BarChild.Destination).anchorBounds { bounds = it },
            action = NavAction.Menu,
            enabled = enabled,
            badge = badge,
            interactionSource = interactionSource,
            labelGap = labelGap,
            markerShape = markerShape,
            markerCoversItem = coversItem,
            markerInset = markerInset,
        )
        // Above the bar, the only side with room.
        AnchoredDropdownMenu(
            visible = open,
            anchor = bounds,
            onDismissRequest = { open = false },
            side = OverlaySide.Top,
        ) {
            NavMenuScope(this, dismiss = { open = false }).emit(content)
        }
    }

    /** Left out: a bar's destinations are one run. */
    @Composable
    override fun divider(modifier: Modifier) {
        emitter().let { if (it !== this) return it.divider(modifier) }
    }

    @Composable
    override fun slot(weight: Float, content: @Composable () -> Unit) {
        // Called on this scope from inside a group, through a helper, it draws
        // where it is called from — and if that is a group's menu or the probe
        // counting a hidden group's destinations, it is dropped on purpose. A
        // slot is the bar's own room, a search or a spacer, and neither a menu
        // nor a count has room for one: drawn there it would be a row that is
        // not a destination.
        emitter().let { if (it !== this) return (it as? NavBarScope)?.slot(weight, content) ?: Unit }
        if (weight > 0f) {
            // `propagateMinConstraints`, so the content is given the width rather
            // than offered it.
            Box(Modifier.layoutId(BarChild.Slot(weight)), propagateMinConstraints = true) { BarSlot(content) }
        } else {
            // A circle's height, level with the circles rather than centred in a
            // row a label may have made taller.
            Box(
                Modifier.layoutId(BarChild.Slot(0f)).height(circle.height),
                contentAlignment = Alignment.Center,
            ) { BarSlot(content) }
        }
    }
}

/** A destination the marker can be dragged to. Written in composition's apply phase, read by the gesture. */
private class DragTarget {
    var coordinates: LayoutCoordinates? = null
    var onClick: () -> Unit = {}
    var enabled: Boolean = true
    var selected: Boolean = false
}

/** What a destination needs to start dragging the marker. Stable per bar. */
@Immutable
private data class DragWiring(val drag: MarkerDrag, val ticker: DetentTicker, val scope: CoroutineScope)

/**
 * The marker's drag, started from this destination.
 *
 * On the destination rather than on the row: a gesture stays with the node it
 * went down on however far the finger then goes, so the drag carries on across
 * the others all the same — and the row is left with no pointer input of its
 * own, so a press *between* destinations still falls through to the surface's
 * blocker rather than stopping on the row and reaching the page. Sideways only:
 * a tap stays a tap. A slot — a search — has none, and keeps its own gestures.
 */
@Composable
private fun Modifier.markerDrag(wiring: DragWiring, target: DragTarget, enabled: Boolean): Modifier {
    val drag = wiring.drag
    val ticker = wiring.ticker
    return horizontalDragOwning(
        enabled = enabled,
        interactionSource = null,
        scope = wiring.scope,
        claimsOn = DragClaim.Direction,
        onStart = { at -> drag.begin(drag.inRow(target, at), ticker) },
        onDelta = { dx -> drag.move(dx, ticker) },
        onRelease = { drag.released = true },
        onEnd = { drag.end(ticker) },
    )
}

/**
 * The bar's marker, dragged: which destination it is over and where the finger
 * is, as a segmented control's thumb is.
 *
 * **By default the choice waits for the finger to lift.** A segmented control
 * changes its value at every boundary it crosses, because a value is cheap to
 * change and change back. A destination is a page: choosing each one the finger
 * passed over would build and throw away every screen on the way. So the
 * marker, and the colours that go with it, follow the finger at once, a tick
 * marks each crossing, and the destination under the finger when it lifts has
 * its `onClick` run, once. A drag that ends where it started, or is taken away
 * by something else, changes nothing.
 *
 * **Or as the marker reaches each one**, under [NavBarMarkerDrag.SelectWhileDragging]
 * — asked for as an option: "when you drag the navbar selection indicator, it
 * can switch tabs as you're dragging". For pages that are cheap to show, where
 * scrubbing across them is the point: each destination's `onClick` runs as the
 * marker reaches it, once, and the lift chooses nothing more.
 *
 * Only the bar's: a rail scrolls, and a sideways drag is the one thing a
 * vertical list cannot be sure is not a scroll.
 */
@Stable
private class MarkerDrag {
    /** The destinations, by key. Not state: read when a finger moves, never in composition. */
    val targets = LinkedHashMap<Any, DragTarget>()

    /** The row's coordinates: the finger's, and the marker's before its lean. */
    var row: LayoutCoordinates? = null

    /** The destination under the finger while it drags the marker, or null. */
    var over: Any? by mutableStateOf(null)
        private set

    val previewing: Boolean get() = over != null

    /** Whether a finger is dragging the marker. */
    var dragging: Boolean by mutableStateOf(false)
        private set

    /** The drag ended with the finger lifting, rather than being taken away. */
    var released: Boolean = false

    /** Whether each destination is chosen as the marker reaches it, rather than on the lift. */
    var selectsWhileDragging: Boolean = false

    /**
     * The destination last chosen during this drag, by [selectsWhileDragging]:
     * the one that was current when it began, and then each one reached. Its
     * own record rather than each target's `selected`, which catches up a
     * composition later — a finger back over the first destination in the same
     * frame would otherwise find it still marked as current and not choose it.
     */
    private var chosen: Any? = null

    /**
     * Where the finger is along the row, or NaN before the first drag. Kept
     * after it lifts, so the lean relaxes out of where it was rather than
     * losing the number.
     */
    private var fingerAt by mutableFloatStateOf(Float.NaN)

    /**
     * The bar's indicator, told where the finger is so the marker leans and
     * stretches toward it, by [SliderDefaults.DetentPull] of the way: far enough
     * to read as straining toward the next destination, never so far that it
     * is closer to that one than to its own.
     */
    var indicator: SelectionIndicatorState? = null

    private fun reachFinger() {
        val state = indicator ?: return
        // The drag's now, not the press it started as: a press that the drag
        // takes over is cancelled, and must not take the lean with it.
        state.reachKey = null
        state.reach = if (fingerAt.isNaN()) null else IndicatorReach(Offset(fingerAt, Float.NaN), SliderDefaults.DetentPull)
    }

    fun marks(key: Any, selected: Boolean): Boolean = over?.let { it == key } ?: selected

    private fun boundsOf(target: DragTarget): Rect? {
        val row = row ?: return null
        val at = target.coordinates ?: return null
        if (!row.isAttached || !at.isAttached) return null
        return row.localBoundingBoxOf(at, clipBounds = false)
    }

    /** The enabled destinations, start to end of the row as it is drawn. */
    private fun ordered(): List<Pair<Any, Rect>> = targets.mapNotNull { (key, target) ->
        if (!target.enabled) null else boundsOf(target)?.let { key to it }
    }.sortedBy { it.second.left }

    /** [at], in [target]'s coordinates, along the row. */
    fun inRow(target: DragTarget, at: Offset): Float {
        val row = row ?: return at.x
        val own = target.coordinates ?: return at.x
        if (!row.isAttached || !own.isAttached) return at.x
        return row.localPositionOf(own, at).x
    }

    private fun pick(ticker: DetentTicker, all: List<Pair<Any, Rect>> = ordered()) {
        if (all.isEmpty()) return
        val index = all.indices.minBy { kotlin.math.abs(all[it].second.center.x - fingerAt) }
        ticker.at(index)
        val key = all[index].first
        over = key
        if (selectsWhileDragging && key != chosen) {
            chosen = key
            targets[key]?.takeIf { it.enabled }?.onClick?.invoke()
        }
    }

    /**
     * The delta that arrives with the claim, already counted: the drag claims
     * where the finger *is* once it has decided the direction, and then hands
     * over the movement it gathered deciding — which [begin] has in its
     * position. Counted twice it put the finger a touch slop ahead of itself.
     */
    private var claimed = false

    fun begin(x: Float, ticker: DetentTicker) {
        released = false
        claimed = true
        chosen = targets.entries.firstOrNull { it.value.selected }?.key
        fingerAt = x.coerceIn(0f, (row?.size?.width ?: 0).toFloat())
        dragging = true
        reachFinger()
        val all = ordered()
        // **Armed on the marker's destination, not the one under the finger.** A
        // drag that starts on another destination has already crossed to it, and
        // that jump is the first detent. Armed under the finger, the first pick
        // only armed, so pressing the third destination and dragging on moved
        // the marker there in silence. With nothing selected in the bar — the
        // current page is in a group, say — it arms where the finger is, as
        // before.
        ticker.reset()
        all.indexOfFirst { it.first == chosen }.takeIf { it >= 0 }?.let(ticker::at)
        pick(ticker, all)
    }

    fun move(dx: Float, ticker: DetentTicker) {
        if (claimed) {
            claimed = false
            return
        }
        fingerAt = (fingerAt + dx).coerceIn(0f, (row?.size?.width ?: 0).toFloat())
        reachFinger()
        pick(ticker)
    }

    fun end(ticker: DetentTicker) {
        val target = over?.let { targets[it] }
        val lifted = released
        released = false
        dragging = false
        indicator?.reach = null
        ticker.reset()
        // In one breath with the app's own change, so the marker heads straight
        // for the destination rather than back to the old one first.
        over = null
        // Chosen already, as the marker reached it, when it selects while dragging.
        if (selectsWhileDragging) return
        if (lifted && target != null && target.enabled && !target.selected) target.onClick()
    }
}

/**
 * A bar's slot, told how much room it has: a narrow place, a hundred-odd dp
 * between destinations, on the bar's surface. Without it a [NavSearch] in a bar
 * would decide it had room to be a live field and raise the keyboard inside the
 * navigation.
 */
@Composable
private fun BarSlot(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavExpansion provides NavExpansion.Bar, content = content)
}
