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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
     * How tall the fade behind the bar is.
     *
     * Enough to cover the destinations and a little of what is above them, so
     * the content dissolves into the fade rather than meeting a line.
     */
    val BackdropHeight: Dp = 128.dp
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
    showLabels: Boolean = false,
    containerColor: Color = Theme.colors.surface,
    contentColor: Color = Theme.colors.content,
    indicatorColor: Color = Theme.colors.accent.container,
    backdrop: Boolean = false,
    backdropColor: Color = Theme.colors.background,
    indicatorSize: DpSize = NavItemDefaults.CircleSize,
    labelGap: Dp = Theme.spacing.xxs,
    search: (@Composable () -> Unit)? = null,
    /**
     * Where [search] sits among the items.
     *
     * `null` — the default — puts it after all of them, which is where it has
     * always been. An index puts it *between* two: `items.size / 2` is the
     * middle, which is the arrangement a map app wants, with destinations either
     * side of the thing people actually came to do.
     *
     * An index rather than a builder because [NavigationSuiteScaffold] hands the
     * bar, the rail and the drawer one `List<NavItem>` and expects all three to
     * show the same list. A `NavBarScope` with `item()` and `search()` would be
     * more flexible and would match `NavDrawerScope`; it would also mean the
     * suite could no longer drive them from one list. Worth revisiting if the
     * index proves too blunt.
     */
    searchIndex: Int? = null,
    action: (@Composable () -> Unit)? = null,
    /**
     * What the row keeps clear of. The gesture bar and the display cutout by
     * default.
     *
     * The row moves *up* by the inset rather than growing into it: there is no
     * surface here to extend to the window's edge, only shapes with air around
     * them.
     */
    windowInsets: WindowInsets = WindowInsets.bottomEdges,
) {
    val indicator = rememberSelectionIndicatorState()

    // The fade is *drawn*, not laid out.
    //
    // It used to be a 128dp sibling `Box` inside this one, which made the whole
    // component 128dp tall whenever `backdrop` was on — and since the `Row`
    // carried no alignment it then sat at the top of that box rather than at the
    // bottom. [NavigationSuiteScaffold] measures this component and hands its
    // height to the screen as padding, so turning the backdrop on inset the
    // content by 128dp and lifted the destinations away from the edge. Visible
    // in the gallery's own golden, in the one panel that uses it.
    //
    // Reaching above the node's own bounds is the point: the fade has to start
    // well over the row it stands behind, and nothing here clips.
    val backdropHeight = with(LocalDensity.current) { NavBarDefaults.BackdropHeight.toPx() }

    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (backdrop) {
                        // `drawWithCache`, not `drawBehind`: a `Brush` built
                        // inside the draw block is rebuilt on every frame the
                        // bar is redrawn, and a vertical gradient carries a
                        // shader that has to be recreated with it. Nothing here
                        // depends on anything but the size and the colour, so
                        // both live in the cache block and the draw block only
                        // draws.
                        Modifier.drawWithCache {
                            val top = size.height - backdropHeight
                            val brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, backdropColor),
                                startY = top,
                                endY = size.height,
                            )
                            val topLeft = Offset(0f, top)
                            val fade = Size(size.width, backdropHeight)
                            onDrawBehind {
                                drawRect(brush = brush, topLeft = topLeft, size = fade)
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .windowInsetsPadding(windowInsets)
                // Horizontal only. Every destination reserves
                // `Theme.sizing.minTouchTarget` and draws a smaller circle
                // inside it, so the air above and below the circles is already
                // there; 4dp on top of it was the same 4dp twice, and eight of
                // the bar's fifty-six were paying for it.
                .padding(horizontal = NavBarDefaults.Inset),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                modifier = if (search == null) Modifier.weight(1f) else Modifier,
                indicator = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .elevation(Theme.elevation.low, Theme.shapes.pill)
                            .clip(Theme.shapes.pill)
                            .background(indicatorColor)
                    )
                },
            ) {
                Row(
                    modifier = Modifier
                        .then(if (search == null) Modifier.fillMaxWidth() else Modifier)
                        .selectableGroup(),
                    horizontalArrangement = if (search == null) {
                        Arrangement.SpaceEvenly
                    } else {
                        Arrangement.spacedBy(Theme.spacing.xs)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        if (search != null && searchIndex == index) {
                            // `propagateMinConstraints`, so the slot's content is
                            // *given* the width rather than merely offered it.
                            // The `search` parameter has always promised to be
                            // "sized to what is left"; without this the `Box` was
                            // sized to what was left and the pill inside it wrapped
                            // its own content and sat at the start, leaving 45dp of
                            // nothing between the search and the destination after
                            // it — against 8dp everywhere else in the row.
                            Box(Modifier.weight(1f), propagateMinConstraints = true) {
                                BarSlot(search)
                            }
                        }
                        NavBarItem(
                            item = item,
                            selected = index == selectedIndex,
                            showLabel = showLabels,
                            // Only the *unselected* circles paint themselves. An
                            // item's own background is part of its content and
                            // draws over the marker behind it, so the selected
                            // one has to be the travelling circle or the marker
                            // is invisible.
                            containerColor = if (index == selectedIndex) {
                                Color.Transparent
                            } else {
                                containerColor
                            },
                            contentColor = contentColor,
                            indicatorSize = indicatorSize,
                            labelGap = labelGap,
                        )
                    }
                }
            }

            // Only when it has not already been placed among the items.
            if (search != null && searchIndex == null) {
                Box(Modifier.weight(1f), propagateMinConstraints = true) {
                    BarSlot(search)
                }
            }

            if (action != null) {
                // Aligned to the *icons*, not to the row.
                //
                // With `showLabels` the items grow a word taller, so centring
                // the action in the row dropped it half a label below the icons
                // it is meant to sit beside — a trailing FAB visibly lower than
                // everything else on the bar. Top-aligned and given the
                // indicator's height, its centre is the icons' centre whatever
                // is underneath them.
                Box(
                    modifier = Modifier.align(Alignment.Top).height(indicatorSize.height),
                    contentAlignment = Alignment.Center,
                ) {
                    BarSlot(action)
                }
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
    containerColor: Color = Theme.colors.surface,
    contentColor: Color = Theme.colors.content,
    indicatorSize: DpSize = NavItemDefaults.CircleSize,
    labelGap: Dp = Theme.spacing.xxs,
    interactionSource: MutableInteractionSource? = null,
) {
    NavDestinationItem(
        item = item,
        selected = selected,
        modifier = modifier,
        layout = NavItemLayout.Stacked,
        showLabel = showLabel,
        containerColor = containerColor,
        contentColor = contentColor,
        indicatorSize = indicatorSize,
        labelGap = labelGap,
        // Each circle is its own object floating over the page, so it casts its
        // own shadow. In a rail or a drawer the surface behind the row does that
        // job and a per-item shadow would be a second one.
        shadow = Theme.elevation.low,
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
