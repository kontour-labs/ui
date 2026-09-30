package io.kontour.ui.nav

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.optionalBorder
import io.kontour.ui.a11y.selectionEdge
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.adaptive.bottomEdges
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.SurfaceDefaults
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.overlay.AnchoredDropdownMenu
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.anchorBounds
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme
import kotlin.math.max
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

/**
 * How a [NavBar] presents itself. A container decision only: the destinations
 * are circles either way.
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
 * Each destination is a circle; one marker travels between them. The content is
 * a [NavBarScope]: destinations are [NavScope.item]s, a [NavScope.group] is a
 * circle that opens a menu of its children above the bar, sections run inline
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
    content: @Composable NavBarScope.() -> Unit,
) {
    val indicator = rememberSelectionIndicatorState()
    val keys = remember { NavKeys() }
    val circle = NavBarDefaults.CircleSize
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
    val inset = ((Theme.sizing.minTouchTarget - circle.width) / 2).coerceAtLeast(0.dp)
    val floatingEnds = when {
        arrangement != null -> Theme.spacing.sm
        spans -> inset
        else -> NavBarDefaults.ItemGap + inset
    }

    // A new scope when anything it draws with changes: the destinations are
    // composed from inside it, and a scope that changed in place would leave
    // them skipped with the old values.
    val scope = remember(keys, showLabels, colours, circle, labelGap) {
        BarScope(keys, showLabels, colours, circle, labelGap)
    }

    @Composable
    fun Destinations(rowModifier: Modifier, inlineAction: (@Composable () -> Unit)?) {
        SelectionIndicatorBox(
            state = indicator,
            // The circle is the marker, sized to the glyph rather than the
            // destination, and against the top when a label hangs under it.
            sizing = IndicatorSizing.Fixed(
                width = circle.width,
                height = circle.height,
                verticalBias = if (showLabels) 0f else 0.5f,
            ),
            modifier = rowModifier,
            indicator = {
                // No shadow: the bar is the raised thing, and a marker casting
                // its own inside it is a second surface on the first.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(navItemShape(circle))
                        .background(colours.indicator)
                        .optionalBorder(selectionEdge(), navItemShape(circle))
                )
            },
        ) {
            BarRow(
                arrangement = rowArrangement,
                fill = !hug,
                searchIndex = searchIndex,
                slotsCentred = !showLabels,
                modifier = Modifier.selectableGroup(),
            ) {
                scope.content()
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

    when (style) {
        // The surface reaches the window's edge and the insets are applied
        // inside it, so its colour runs under the gesture bar.
        NavBarStyle.Docked -> BarGround(
            modifier = modifier.fillMaxWidth(),
            shape = RectangleShape,
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
                .windowInsetsPadding(windowInsets)
                .padding(horizontal = NavBarDefaults.Inset, vertical = Theme.spacing.xs),
            hug = hug,
            gap = NavBarDefaults.ActionGap,
            action = action,
        ) {
            // A capsule, concentric with the circles it holds: 20dp of their
            // radius and `xs` around them is half the bar's height.
            BarGround(
                modifier = Modifier,
                shape = Theme.shapes.pill,
                colours = colours,
                shadow = Theme.elevation.high,
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
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        containerColour = colours.container,
        contentColour = SurfaceDefaults.contentColour(colours.container),
        shadow = shadow,
        // A floating bar is `surface` on `background` with a shadow for an
        // edge, and those two grounds are 1.00:1 at the enhanced light tier.
        border = contrastEdge(),
    ) {
        // **A tap on the bar is the bar's.** Between two destinations there is
        // nothing to press, and with nothing there to take it a tap went
        // through to whatever the page had under the bar — a row opened from
        // underneath its navigation. So the surface has a floor: a layer under
        // the destinations that takes every tap reaching it, which is only
        // those that land where no destination is. Under them rather than
        // around them, because a parent that took the taps would take them from
        // the destinations too — a parent consuming after its children is a
        // press they give up on. And inside the surface, so it is the capsule's
        // shape that stops a tap and not its box: just off the rounded ends is
        // still the page.
        Box(propagateMinConstraints = true) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
            )
            content()
        }
    }
}

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
    searchIndex: Int?,
    slotsCentred: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
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

        val gap = arrangement.spacing.roundToPx()
        val gaps = gap * (ordered.size - 1).coerceAtLeast(0)
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = arrayOfNulls<Placeable>(ordered.size)
        var used = gaps
        ordered.forEachIndexed { i, child ->
            if (child.barChild.weight == 0f) {
                val room = if (constraints.hasBoundedWidth) (constraints.maxWidth - used).coerceAtLeast(0) else Constraints.Infinity
                placeables[i] = child.measure(loose.copy(maxWidth = room))
                used += placeables[i]!!.width
            }
        }
        val totalWeight = ordered.sumOf { it.barChild.weight.toDouble() }.toFloat()
        // A weighted child's share of the room left over, but never more than
        // `SlotMaxWidth`: see there.
        val slotCap = NavBarDefaults.SlotMaxWidth.roundToPx()
        if (totalWeight > 0f) {
            val left = if (constraints.hasBoundedWidth) (constraints.maxWidth - used).coerceAtLeast(0) else 0
            ordered.forEachIndexed { i, child ->
                val weight = child.barChild.weight
                if (weight > 0f) {
                    val width = (left * weight / totalWeight).roundToInt().coerceAtMost(slotCap)
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
                    at += all[i].width + gap
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
        val text = navLabel(label)
        ClaimNavKey(keys, key)
        NavDestination(
            label = text,
            icon = icon,
            selected = selected,
            onClick = onClick,
            markerKey = key,
            colours = colours,
            layout = NavLayout.Stacked,
            showLabel = showLabels,
            indicatorSize = circle,
            modifier = modifier.layoutId(BarChild.Destination),
            enabled = enabled,
            selectedIcon = selectedIcon,
            contentDescription = contentDescription,
            badge = badge,
            interactionSource = interactionSource,
            labelGap = labelGap,
        )
    }

    /** Inline: a bar has no room for a title, and its destinations are few. */
    @Composable
    override fun section(title: String, modifier: Modifier, content: @Composable NavScope.() -> Unit) {
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
        var open by remember { mutableStateOf(false) }
        var bounds by remember { mutableStateOf<Rect?>(null) }
        val selection = rememberGroupSelection(hidden = true, content = content)
        NavDestination(
            label = text,
            icon = icon,
            selected = false,
            marked = selection.any,
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
        )
        // Above the bar, the only side with room.
        AnchoredDropdownMenu(
            visible = open,
            anchor = bounds,
            onDismissRequest = { open = false },
            side = OverlaySide.Top,
        ) {
            NavMenuScope(this, dismiss = { open = false }).content()
        }
    }

    /** Left out: a bar's destinations are one run. */
    @Composable
    override fun divider(modifier: Modifier) = Unit

    @Composable
    override fun slot(weight: Float, content: @Composable () -> Unit) {
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
