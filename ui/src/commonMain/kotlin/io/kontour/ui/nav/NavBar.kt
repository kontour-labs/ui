package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.bottomEdges
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.elevation
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme

object NavBarDefaults {
    /** How far the row sits in from the window's edges. */
    val Inset: Dp = 16.dp

    /**
     * The shortest the row can be — whichever is larger of a destination's
     * circle and the touch target reserved around it.
     *
     * It used to be `CircleSize.height + 8.dp`, which described the row's old
     * padding rather than what actually sets the floor. A destination calls
     * `minimumTouchTarget()`, so on Android the row cannot measure under 48dp
     * however small the circle is drawn — and deriving this from a decoration is
     * how it went wrong the moment the decoration changed.
     *
     * A starting estimate only: [NavigationSuiteScaffold] measures the real
     * height, which grows with the user's type size and with a label under each
     * icon. It is here so the first frame of a screen does not lay its content
     * out under a bar of no height at all and then jump.
     */
    val MinHeight: Dp
        @Composable get() = maxOf(Theme.sizing.minTouchTarget, NavItemDefaults.CircleSize.height)

    /**
     * The gap between destinations when they are not spread across the window.
     *
     * Four. The bar's complaint has always been that its buttons are too far
     * apart, and on the styles with a surface behind them there is no reason to
     * spread: a docked bar's shape already says where the navigation is, so the
     * items can sit together and read as one group.
     */
    val ItemGap: Dp = 4.dp

    /**
     * How a bar in [style] distributes its children, unless the caller says
     * otherwise.
     *
     * [NavBarStyle.Free] spreads, because free-standing circles with nothing
     * behind them need the window's width to read as a bar at all. The other two
     * group in the middle, because their surface has already done that job — and
     * spreading them is what made the buttons feel too far apart.
     *
     * Moot the moment a weighted slot is in the row: the slot takes the slack,
     * so there is none left for an arrangement to distribute.
     */
    fun arrangementFor(style: NavBarStyle): Arrangement.Horizontal = when (style) {
        NavBarStyle.Free -> Arrangement.SpaceEvenly
        NavBarStyle.Docked, NavBarStyle.Floating ->
            Arrangement.spacedBy(ItemGap, Alignment.CenterHorizontally)
    }

    /**
     * How tall the fade behind the bar is.
     *
     * Enough to cover the destinations and a little of what is above them, so
     * the content dissolves into the fade rather than meeting a line.
     */
    val BackdropHeight: Dp = 128.dp
}

/**
 * How a [NavBar] presents itself. **A container decision, and only that.**
 *
 * The item's own presentation — circle, label, badge — is derived from the style
 * rather than chosen beside it, and that restraint is the whole reason this is
 * an enum and not two. The bar has been here before: it "used to be three
 * container styles and two item styles — nine combinations, of which the product
 * wanted one". Every one of those nine looked defensible on its own, which is
 * exactly how a matrix gets built. A second axis is the thing to refuse.
 */
enum class NavBarStyle {
    /**
     * Free-standing circles over the page, with nothing behind them.
     *
     * The default, and what the app ships. Each circle carries its own
     * elevation, so the bar works over a map without a surface separating it
     * from one.
     */
    Free,

    /**
     * One surface spanning the window, sitting on the bottom edge.
     *
     * The arrangement most apps use, and the one to reach for when the content
     * behind the bar is a list rather than a map — a surface that meets the
     * window's edge is a firmer footing than shapes floating over a scroll.
     */
    Docked,

    /**
     * A capsule inset from every edge, hovering over the content.
     *
     * [Docked]'s footing without its commitment: the page runs under it, so it
     * suits content that should be seen to continue past the bar.
     */
    Floating,
}

/**
 * What goes in a [NavBar], in the order it goes in.
 *
 * Two primitives, because two is what the cases need. [item] is a destination —
 * it takes part in the selection marker and the screen reader's count of them.
 * [slot] is everything else, and its [slot]'s `weight` is Compose's own `Row`
 * vocabulary rather than a private one: at zero it is sized to its content, and
 * above zero it takes that share of whatever is left over.
 *
 * ```kotlin
 * NavBar(style = NavBarStyle.Docked, selectedIndex = current) {
 *     item(home)
 *     item(browse)
 *     slot(weight = 1f) { SearchField(query, placeholder = "Search") }
 *     item(orders)
 *     item(account)
 * }
 * ```
 *
 * That replaces `search` and `searchIndex`, whose own documentation predicted
 * this: "an index rather than a builder… worth revisiting if the index proves
 * too blunt". It did. An index can put one thing in one place; it cannot say
 * that the search should be twice the width of the wide button beside it, or
 * that two destinations should be pushed to the far edge by an empty
 * `slot(weight = 1f) {}`.
 */
@Stable
@LayoutScopeMarker
interface NavBarScope {

    /**
     * One destination.
     *
     * `selectedIndex` counts **these and nothing else**, so putting a search in
     * the middle of a bar does not shift the index of the items after it. That
     * is the trap an entry list of one type would set.
     */
    fun item(item: NavItem)

    /**
     * Anything that is not a destination: a search field, a wide button, an
     * avatar, a floating action.
     *
     * Outside the destinations' `selectableGroup`, so a screen reader does not
     * announce a search field as "4 of 4" among three places to go.
     *
     * @param weight Zero — the default — sizes it to its content. Above zero it
     *   takes that share of the room the fixed children leave, which is what a
     *   search field wants and what makes an empty slot a spacer.
     */
    fun slot(weight: Float = 0f, content: @Composable () -> Unit)
}

internal sealed interface NavBarEntry

internal data class NavBarDestination(val item: NavItem) : NavBarEntry

internal class NavBarSlotEntry(
    val weight: Float,
    val content: @Composable () -> Unit,
) : NavBarEntry

internal class NavBarScopeImpl : NavBarScope {
    val entries = mutableListOf<NavBarEntry>()

    override fun item(item: NavItem) {
        entries += NavBarDestination(item)
    }

    override fun slot(weight: Float, content: @Composable () -> Unit) {
        entries += NavBarSlotEntry(weight, content)
    }
}

/**
 * The primary navigation surface on a phone. **Goes at the bottom of the
 * screen.**
 *
 * Free-standing circles, one per destination, floating over whatever the screen
 * is showing. No bar behind them.
 *
 * ```kotlin
 * NavBar(
 *     items = destinations,
 *     selectedIndex = current,
 *     search = { SearchField(state, placeholder = "Search") },
 * )
 * ```
 *
 * Why there is no bar, and how the travelling marker works, are on the
 * navigation page: `ui-docs/content/components/navigation.md`.
 *
 * This used to be three container styles and two item styles — nine
 * combinations, of which the product wanted one. It is one now, and that is
 * worth leaving written down: every one of those styles looked defensible on
 * its own.
 *
 * @param showLabels Off by default. A word under every icon is a row of words,
 *   and the destinations of an app this size are the four or five its user
 *   already knows. Turn it on for an app whose icons are not obvious.
 * @param backdrop A vertical fade from transparent to the page colour behind
 *   the whole row. Each circle carries its own elevation, which separates it
 *   from a map; over a photo or a promotional banner it does not.
 * @param search Its own shape in the row, sized to what is left. Outside
 *   `selectableGroup()`, so a screen reader does not announce it as "4 of 4"
 *   among three destinations.
 * @param action A trailing control — a FAB, usually. Also outside the group.
 */
@Composable
fun NavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    style: NavBarStyle = NavBarStyle.Free,
    showLabels: Boolean = false,
    containerColour: Color = Theme.colours.surface,
    contentColour: Color = Theme.colours.content,
    indicatorColour: Color = Theme.colours.accent.container,
    backdrop: Boolean = false,
    backdropColour: Color = Theme.colours.background,
    indicatorSize: DpSize = NavItemDefaults.CircleSize,
    labelGap: Dp = Theme.spacing.xxs,
    search: (@Composable () -> Unit)? = null,
    /**
     * Where [search] sits among the items.
     *
     * `null` — the default — puts it after all of them. An index puts it
     * *between* two: `items.size / 2` is the middle, which is the arrangement a
     * map app wants, with destinations either side of the thing people actually
     * came to do.
     *
     * An index because [NavigationSuiteScaffold] hands the bar, the rail and the
     * drawer one `List<NavItem>` and expects all three to show the same list.
     * That constraint is real and this overload keeps it; the [NavBarScope]
     * overload below is where a bar arranges itself freely, and this one is
     * written in terms of it.
     */
    searchIndex: Int? = null,
    action: (@Composable () -> Unit)? = null,
    arrangement: Arrangement.Horizontal = NavBarDefaults.arrangementFor(style),
    windowInsets: WindowInsets = WindowInsets.bottomEdges,
) {
    NavBar(
        selectedIndex = selectedIndex,
        modifier = modifier,
        style = style,
        showLabels = showLabels,
        containerColour = containerColour,
        contentColour = contentColour,
        indicatorColour = indicatorColour,
        backdrop = backdrop,
        backdropColour = backdropColour,
        indicatorSize = indicatorSize,
        labelGap = labelGap,
        arrangement = arrangement,
        windowInsets = windowInsets,
    ) {
        items.forEachIndexed { index, destination ->
            if (search != null && searchIndex == index) slot(weight = 1f) { search() }
            item(destination)
        }
        if (search != null && searchIndex == null) slot(weight = 1f) { search() }
        if (action != null) slot { action() }
    }
}

/**
 * The primary navigation surface on a phone, arranged by the caller.
 *
 * ```kotlin
 * NavBar(style = NavBarStyle.Docked, selectedIndex = current) {
 *     item(home)
 *     item(browse)
 *     slot(weight = 1f) { SearchField(query, placeholder = "Search") }
 *     item(orders)
 *     item(account)
 * }
 * ```
 *
 * See [NavBarScope] for what goes in it and [NavBarStyle] for how it presents
 * itself. The `List<NavItem>` overload above is this one with the list laid out
 * left to right, and is what [NavigationSuiteScaffold] drives so that the bar,
 * the rail and the drawer keep showing the same destinations.
 *
 * @param selectedIndex Counts `item`s and nothing else, so a slot in the middle
 *   does not shift the indices of the destinations after it.
 * @param showLabels Off by default. A word under every icon is a row of words,
 *   and the destinations of an app this size are the four or five its user
 *   already knows. Turn it on for an app whose icons are not obvious.
 * @param backdrop A vertical fade from transparent to the page colour behind the
 *   whole row. A [NavBarStyle.Free] concern only: the other two styles have a
 *   surface, which is what a backdrop is standing in for.
 * @param arrangement How the row distributes its children. Defaults per style —
 *   see [NavBarDefaults.arrangementFor].
 */
@Composable
fun NavBar(
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    style: NavBarStyle = NavBarStyle.Free,
    showLabels: Boolean = false,
    containerColour: Color = Theme.colours.surface,
    contentColour: Color = Theme.colours.content,
    indicatorColour: Color = Theme.colours.accent.container,
    backdrop: Boolean = false,
    backdropColour: Color = Theme.colours.background,
    indicatorSize: DpSize = NavItemDefaults.CircleSize,
    labelGap: Dp = Theme.spacing.xxs,
    arrangement: Arrangement.Horizontal = NavBarDefaults.arrangementFor(style),
    windowInsets: WindowInsets = WindowInsets.bottomEdges,
    content: NavBarScope.() -> Unit,
) {
    val indicator = rememberSelectionIndicatorState()
    val entries = NavBarScopeImpl().also(content).entries

    // The fade is *drawn*, not laid out.
    //
    // It used to be a 128dp sibling `Box` inside this one, which made the whole
    // component 128dp tall whenever `backdrop` was on — and since the `Row`
    // carried no alignment it then sat at the top of that box rather than at the
    // bottom. [NavigationSuiteScaffold] measures this component and hands its
    // height to the screen as padding, so turning the backdrop on inset the
    // content by 128dp and lifted the destinations away from the edge.
    //
    // Reaching above the node's own bounds is the point: the fade has to start
    // well over the row it stands behind, and nothing here clips.
    val backdropHeight = with(LocalDensity.current) { NavBarDefaults.BackdropHeight.toPx() }
    val fade = if (backdrop && style == NavBarStyle.Free) {
        // `drawWithCache`, not `drawBehind`: a `Brush` built inside the draw
        // block is rebuilt on every frame, and a vertical gradient carries a
        // shader that has to be recreated with it.
        Modifier.drawWithCache {
            val top = size.height - backdropHeight
            val brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, backdropColour),
                startY = top,
                endY = size.height,
            )
            val topLeft = Offset(0f, top)
            val area = Size(size.width, backdropHeight)
            onDrawBehind { drawRect(brush = brush, topLeft = topLeft, size = area) }
        }
    } else {
        Modifier
    }

    // Only the free style's items paint their own circles. The other two have a
    // surface behind them doing that job, and an item painting over it is a
    // second ground on top of the first — which is also what would hide the
    // travelling marker.
    val itemContainer = if (style == NavBarStyle.Free) containerColour else Color.Transparent

    @Composable
    fun BarRow(rowModifier: Modifier) {
        SelectionIndicatorBox(
            state = indicator,
            // The circle *is* the marker, so it is sized to the glyph rather
            // than to the item — otherwise it stretches to take in a label
            // underneath and stops being a circle.
            sizing = IndicatorSizing.Fixed(
                width = indicatorSize.width,
                height = indicatorSize.height,
                // Against the top when there is a label below the icon: the
                // circle marks the glyph, and centring it on the whole item
                // drops it into the gap between the icon and the word.
                verticalBias = if (showLabels) 0f else 0.5f,
            ),
            modifier = rowModifier,
            indicator = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .elevation(Theme.elevation.low, Theme.shapes.pill)
                        .clip(Theme.shapes.pill)
                        .background(indicatorColour)
                )
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = arrangement,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var ordinal = 0
                entries.forEach { entry ->
                    when (entry) {
                        is NavBarDestination -> {
                            val index = ordinal++
                            NavBarItem(
                                item = entry.item,
                                selected = index == selectedIndex,
                                showLabel = showLabels,
                                // Only the *unselected* circles paint
                                // themselves: an item's own background draws
                                // over the marker behind it, so the selected
                                // one has to be the travelling circle or the
                                // marker is invisible.
                                containerColour = if (index == selectedIndex) {
                                    Color.Transparent
                                } else {
                                    itemContainer
                                },
                                contentColour = contentColour,
                                indicatorSize = indicatorSize,
                                labelGap = labelGap,
                                shadow = style == NavBarStyle.Free,
                            )
                        }

                        is NavBarSlotEntry -> if (entry.weight > 0f) {
                            // `propagateMinConstraints`, so the slot's content
                            // is *given* the width rather than merely offered
                            // it. Without it a search pill wrapped its own
                            // content and sat at the start of the space it had
                            // been handed.
                            Box(
                                modifier = Modifier.weight(entry.weight),
                                propagateMinConstraints = true,
                            ) {
                                BarSlot(entry.content)
                            }
                        } else {
                            // Aligned to the *icons*, not to the row. With
                            // `showLabels` the items grow a word taller, so
                            // centring a trailing action in the row dropped it
                            // half a label below the icons it sits beside.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Top)
                                    .height(indicatorSize.height),
                                contentAlignment = Alignment.Center,
                            ) {
                                BarSlot(entry.content)
                            }
                        }
                    }
                }
            }
        }
    }

    when (style) {
        // No surface at all: shapes with air around them, so the row moves *up*
        // by the inset rather than growing into it.
        NavBarStyle.Free -> Box(modifier.fillMaxWidth()) {
            BarRow(
                Modifier
                    .fillMaxWidth()
                    .then(fade)
                    .windowInsetsPadding(windowInsets)
                    // Horizontal only. Every destination reserves
                    // `Theme.sizing.minTouchTarget` and draws a smaller circle
                    // inside it, so the air above and below is already there.
                    .padding(horizontal = NavBarDefaults.Inset)
            )
        }

        // The surface reaches the window's edge and the insets are applied
        // inside it, so its colour runs under the gesture bar rather than
        // stopping short of it.
        NavBarStyle.Docked -> Surface(
            modifier = modifier.fillMaxWidth(),
            colour = containerColour,
            contentColour = contentColour,
            shadow = Theme.elevation.low,
        ) {
            BarRow(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets)
                    // Vertical too, unlike the free style. A destination's own
                    // touch target already carries air above and below it, which
                    // is enough when there is nothing behind it — but a surface
                    // wrapping that air sits skin-tight against it, and with
                    // labels on the words touch the bar's own edge.
                    .padding(
                        horizontal = NavBarDefaults.Inset,
                        vertical = Theme.spacing.xs,
                    )
            )
        }

        // Inset from every edge, so the page is visibly continuing underneath.
        NavBarStyle.Floating -> Box(
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .padding(horizontal = NavBarDefaults.Inset, vertical = Theme.spacing.xs)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = Theme.shapes.control,
                colour = containerColour,
                contentColour = contentColour,
                shadow = Theme.elevation.high,
            ) {
                BarRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Theme.spacing.sm, vertical = Theme.spacing.xs)
                )
            }
        }
    }
}

/**
 * One destination in a [NavBar].
 *
 * Exposed for a bar that needs a destination the [NavItem] model does not cover.
 * Prefer passing items to [NavBar]: it is what keeps the bar, the rail and the
 * drawer showing the same list.
 */
@Composable
fun NavBarItem(
    item: NavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    containerColour: Color = Theme.colours.surface,
    contentColour: Color = Theme.colours.content,
    indicatorSize: DpSize = NavItemDefaults.CircleSize,
    labelGap: Dp = Theme.spacing.xxs,
    /**
     * Whether the circle casts its own shadow.
     *
     * True for [NavBarStyle.Free], where each circle is its own object floating
     * over the page. On a bar with a surface behind it that surface casts the
     * shadow, and a per-item one is a second shadow inside the first.
     */
    shadow: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    NavDestinationItem(
        item = item,
        selected = selected,
        modifier = modifier,
        layout = NavItemLayout.Stacked,
        showLabel = showLabel,
        containerColour = containerColour,
        contentColour = contentColour,
        indicatorSize = indicatorSize,
        labelGap = labelGap,
        shadow = if (shadow) Theme.elevation.low else Shadow.None,
        interactionSource = interactionSource,
    )
}

/**
 * A bar's slot, told how much room it has.
 *
 * A bar does not resize, so `progress` is 1 — but it is a *narrow* place, a
 * hundred-odd dp between two pairs of destinations, and content that adapts to
 * its surface needs to hear that. [LocalNavExpansion] defaults to expanded for
 * anything outside a navigation surface, which is right there and wrong here:
 * without this a [NavSearch] in a bar would decide it had room to be a live text
 * field, and raise the keyboard inside the navigation bar.
 */
@Composable
private fun BarSlot(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalNavExpansion provides
            NavExpansion(expanded = false, progress = 1f, onSurface = false),
        content = content,
    )
}
