package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    showLabels: Boolean = true,
    containerColor: Color = Theme.colors.surfaceRaised,
    contentColor: Color = Theme.colors.content,
    indicatorColor: Color = Theme.colors.accent,
    action: (@Composable () -> Unit)? = null,
) {
    val indicator = rememberSelectionIndicatorState()
    val shape: Shape = when (style) {
        NavBarStyle.Floating -> Theme.shapes.pill
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
                .defaultMinSize(minHeight = NavBarDefaults.MinHeight),
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
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
                // A bar beneath the destination, not a pill behind its icon.
                // Sliding along the row is a movement the eye follows; a tonal
                // blob appearing behind an icon is a colour change with extra
                // steps, and it is the pattern that made selection here depend on
                // colour in the first place.
                sizing = IndicatorSizing.Edge(
                    edge = IndicatorEdge.Bottom,
                    thickness = Theme.sizing.selectionIndicator,
                    inset = Theme.spacing.md,
                ),
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
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        NavBarItem(
                            item = item,
                            selected = index == selectedIndex,
                            showLabel = showLabels,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    when (style) {
        NavBarStyle.Floating -> Row(
            modifier = modifier
                .fillMaxWidth()
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
    interactionSource: MutableInteractionSource? = null,
) {
    NavDestinationItem(
        item = item,
        selected = selected,
        modifier = modifier,
        layout = NavItemLayout.Stacked,
        showLabel = showLabel,
        interactionSource = interactionSource,
    )
}
