package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.adaptive.bottomEdges
import io.kontour.ui.foundation.IndicatorEdge
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.theme.Theme

/** How a [NavBar] sits on the screen. */
enum class NavBarStyle {
    /**
     * A pill floating just above the bottom edge, inset from the sides.
     *
     * What the app uses today. It leaves the content visible behind it, which
     * matters over a map — the bottom strip of the map is still there, partly
     * covered rather than cut off.
     */
    Floating,

    /**
     * Attached to the bottom edge, full width, rounded along its top only.
     *
     * The right shape when the content beneath is a list rather than something
     * spatial: a floating bar over a list always covers a row, and the user
     * cannot tell which.
     */
    Docked,

    /**
     * No bar at all. Free-standing circles, label under each, page behind.
     *
     * The arrangement the app converged on: what makes a row of destinations
     * read as navigation is the *travelling circle*, not the container they sit
     * in — and a container over a map is a strip of the map you cannot see. The
     * marker moves between destinations exactly as it does in the other two
     * styles; it is simply the only chrome left.
     *
     * The trade is that the destinations sit on whatever the page is. That is
     * fine over a map or a photo and wrong over a list, where a row scrolls
     * underneath them with nothing to say it has — which is what [Docked] is for.
     */
    Free,
}

/**
 * How the destinations are contained.
 *
 * A shape decision, not a behaviour one — selection, semantics and the travelling
 * marker are identical either way.
 */
enum class NavBarItemStyle {
    /** All destinations inside one surface. */
    Grouped,

    /**
     * Each destination in its own circle, with no surface behind the row.
     *
     * Reads as a row of controls rather than one control with parts. Pairs with
     * [NavBar]'s `search` slot, which is where the arrangement comes from — a bar
     * whose widest element is a search field rather than a destination.
     */
    Separate,
}

object NavBarDefaults {
    /**
     * The floor, not the height.
     *
     * The bar derives its height from its content and grows past this at large
     * type. A fixed height is what used to clip the labels at 200% type, and it
     * was also what left the content pinned to the top of the bar.
     */
    val MinHeight: Dp = 64.dp
    val FloatingInset: Dp = 16.dp

    /** A floating bar wider than this is a stripe; keep it a pill and centre it. */
    val FloatingMaxWidth: Dp = 560.dp
}

/**
 * The primary navigation surface on a phone. **Goes at the bottom of the
 * screen.**
 *
 * Not a website header. On a phone the destinations belong within thumb reach,
 * which is the bottom edge — the app's own toolbar sits there today, and
 * [NavigationSuiteScaffold] places this one there for you rather than leaving it
 * to each screen.
 *
 * ```kotlin
 * NavBar(
 *     items = destinations,
 *     selectedIndex = current,
 *     action = { FloatingActionButton(Tabler.Outline.Search, "Search", ::search) },
 * )
 * ```
 *
 * ### How selection is shown
 *
 * A single pill **travels** to the current destination rather than each item
 * fading its own in and out. The movement is what carries the meaning, so the
 * accent tint is a second cue rather than the only one — selection conveyed by
 * colour alone fails WCAG 1.4.1, and it is what a colour-blind user has nothing
 * to go on.
 *
 * ### The action sits beside the bar, not inside it
 *
 * [action] is its own shape next to the pill, which is how the app's current
 * toolbar is built and the only arrangement that works: a 56dp FAB inside a 64dp
 * pill has 4dp of air around it and reads as jammed in. Keeping it outside also
 * stops it competing with the destinations for width — it used to be measured in
 * the same row, so adding an action silently narrowed every destination.
 *
 * It stays outside `selectableGroup()` either way, so a screen reader does not
 * announce the search button as "4 of 4" among three destinations.
 *
 * @param style [NavBarStyle.Floating] by default, matching the app and the site.
 */
@Composable
fun NavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    style: NavBarStyle = NavBarStyle.Floating,
    itemStyle: NavBarItemStyle = NavBarItemStyle.Grouped,
    showLabels: Boolean = true,
    containerColor: Color = Theme.colors.surfaceRaised,
    contentColor: Color = Theme.colors.content,
    indicatorColor: Color = Theme.colors.accent.container,
    /**
     * The travelling marker's size, and the box each glyph sits in.
     *
     * `null` takes the style's own: a circle for [NavBarStyle.Free], a shorter
     * box for a labelled bar, the icon pill for one without labels.
     */
    indicatorSize: DpSize? = null,
    /** Between a destination's icon and its label. */
    labelGap: Dp = Theme.spacing.xxs,
    search: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    /**
     * What the bar keeps clear of. The gesture bar and the display cutout by
     * default; pass `WindowInsets(0)` when something outside has already
     * accounted for them.
     *
     * A [NavBarStyle.Floating] bar moves *up* by the inset, because it is a shape
     * with air around it and there is nothing to extend. A [NavBarStyle.Docked]
     * bar is full bleed, so its surface reaches the bottom of the window and only
     * its content is inset — the gesture bar sits on the bar rather than on a
     * strip of the screen behind it.
     */
    windowInsets: WindowInsets = WindowInsets.bottomEdges,
) {
    val indicator = rememberSelectionIndicatorState()
    val glyphSize = indicatorSize ?: when (style) {
        NavBarStyle.Free -> NavItemDefaults.CircleSize
        else -> if (showLabels) NavItemDefaults.GlyphSize else DpSize(
            NavItemDefaults.IndicatorWidth,
            NavItemDefaults.IndicatorHeight,
        )
    }
    val sizing = when {
        // The circle *is* the marker. Sized to the glyph rather than to the item,
        // so it stays a circle behind the icon and does not stretch to take in
        // the label underneath it.
        style == NavBarStyle.Free -> IndicatorSizing.Fixed(
            width = glyphSize.width,
            height = glyphSize.height,
            // Against the top of the item, because the item is a circle with a
            // word under it and the circle is the thing being marked.
            verticalBias = if (showLabels) 0f else 0.5f,
        )

        // Separate circles: the marker cannot be a pill *behind* an icon that
        // already sits in its own circle — that is two concentric shapes. The
        // container becomes the marker instead.
        itemStyle == NavBarItemStyle.Separate -> IndicatorSizing.Fill

        // With labels the marker wraps the whole destination. Sized to the icon
        // it leaves the label hanging outside it, which reads as a blob behind
        // the glyph rather than as the destination being marked.
        showLabels -> IndicatorSizing.Inset(Theme.spacing.xxs)

        // Icon only: there is nothing else in the item, so the pill is the icon's.
        else -> IndicatorSizing.Fixed(
            width = NavItemDefaults.IndicatorWidth,
            height = NavItemDefaults.IndicatorHeight,
        )
    }
    val shape: Shape = when (style) {
        NavBarStyle.Floating, NavBarStyle.Free -> Theme.shapes.pill
        NavBarStyle.Docked -> Theme.shapes.sheet
    }

    val bar = @Composable { barModifier: Modifier ->
        Surface(
            modifier = barModifier
                .then(
                    if (style == NavBarStyle.Floating) {
                        Modifier.widthIn(max = NavBarDefaults.FloatingMaxWidth)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
                // A minimum, not a height: at 200% type the content is taller than
                // 64dp and the bar has to grow rather than clip it.
                .defaultMinSize(minHeight = NavBarDefaults.MinHeight)
                // Docked only: a floating bar is inset as a whole below, and
                // doing both would count the gesture bar twice.
                .then(
                    if (style == NavBarStyle.Docked) {
                        Modifier.windowInsetsPadding(windowInsets)
                    } else {
                        Modifier
                    }
                ),
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = contrastEdge(),
            shadow = if (style == NavBarStyle.Floating) {
                // `medium` is the token whose doc says "nav bars, raised cards";
                // `high` is for menus and popovers, which sit above this.
                Theme.elevation.medium
            } else {
                Theme.elevation.low
            },
            // `Surface` defaults to `TopStart`, and with a minimum size that pins
            // the content to the top of the bar with all the slack below it. Its
            // own docs warn about exactly this.
            contentAlignment = Alignment.Center,
        ) {
            SelectionIndicatorBox(
                state = indicator,
                // A tonal pill that *travels* between destinations rather than
                // fading in on one and out on the other. The movement is what
                // stops selection depending on colour: the shape arrives, and the
                // accent tint is the second cue.
                sizing = sizing,
                modifier = Modifier.padding(horizontal = Theme.spacing.xs),
                indicator = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(Theme.shapes.pill)
                            .background(indicatorColor)
                    )
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        if (itemStyle == NavBarItemStyle.Separate) Theme.spacing.xs
                        else Theme.spacing.xxs
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .then(if (search == null) Modifier.weight(1f) else Modifier)
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(
                            if (itemStyle == NavBarItemStyle.Separate) Theme.spacing.xs
                            else Theme.spacing.xxs
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items.forEachIndexed { index, item ->
                            NavBarItem(
                                item = item,
                                selected = index == selectedIndex,
                                showLabel = showLabels,
                                // Only the *unselected* circles paint themselves.
                                // An item's own background is part of the
                                // content, so it draws over the indicator behind
                                // it — the selected circle has to be the
                                // travelling marker, or the marker is invisible.
                                containerColor = when {
                                    itemStyle != NavBarItemStyle.Separate -> Color.Transparent
                                    index == selectedIndex -> Color.Transparent
                                    else -> Theme.colors.surfaceSunken
                                },
                                // Destinations divide the bar only when nothing
                                // else is competing for it. With a search field
                                // present they size to content and it takes the
                                // rest — which is the arrangement's whole point.
                                modifier = if (search == null) Modifier.weight(1f) else Modifier,
                                indicatorSize = glyphSize,
                                labelGap = labelGap,
                            )
                        }
                    }

                    // Inside the bar and outside the selectable group: it is not a
                    // destination, and a screen reader must not count it as one.
                    if (search != null) Box(Modifier.weight(1f)) { search() }
                }
            }
        }
    }

    when (style) {
        NavBarStyle.Floating -> Row(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .padding(NavBarDefaults.FloatingInset),
            horizontalArrangement = Arrangement.spacedBy(
                Theme.spacing.xs,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // `fill = false` so the pill takes what it needs up to its maximum and
            // leaves the action its own room, rather than stretching to the edge.
            bar(Modifier.weight(1f, fill = false))
            action?.invoke()
        }

        // No surface, so no `bar` — the destinations go straight onto the page
        // with the travelling circle as the only chrome. The row still owns the
        // insets and the horizontal padding a bar would have given it.
        NavBarStyle.Free -> Row(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .padding(
                    horizontal = NavBarDefaults.FloatingInset,
                    vertical = Theme.spacing.xxs,
                ),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionIndicatorBox(
                state = indicator,
                sizing = sizing,
                modifier = Modifier.weight(1f),
                indicator = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(Theme.shapes.pill)
                            .background(indicatorColor)
                    )
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        NavBarItem(
                            item = item,
                            selected = index == selectedIndex,
                            showLabel = showLabels,
                            indicatorSize = glyphSize,
                            labelGap = labelGap,
                        )
                    }
                }
            }

            if (search != null) Box(Modifier.weight(1f)) { search() }
            action?.invoke()
        }

        // A full-bleed bar has no "beside", so a docked bar keeps its action
        // inside. Each style gets the arrangement that is right for its shape.
        NavBarStyle.Docked -> Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bar(Modifier.weight(1f))
            if (action != null) {
                Box(Modifier.padding(end = Theme.spacing.xs)) { action() }
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
    showLabel: Boolean = true,
    containerColor: Color = Color.Transparent,
    indicatorSize: DpSize = NavItemDefaults.GlyphSize,
    labelGap: Dp = Theme.spacing.xxs,
    interactionSource: MutableInteractionSource? = null,
) {
    NavDestinationItem(
        item = item,
        indicatorSize = indicatorSize,
        labelGap = labelGap,
        selected = selected,
        modifier = modifier,
        layout = NavItemLayout.Stacked,
        showLabel = showLabel,
        containerColor = containerColor,
        interactionSource = interactionSource,
    )
}
