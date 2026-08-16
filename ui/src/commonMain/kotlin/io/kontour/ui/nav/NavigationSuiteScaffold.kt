package io.kontour.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.bottomEdges
import io.kontour.ui.adaptive.leadingEdges
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.Theme

/** Which navigation surface a window gets. */
enum class NavigationSuiteType {
    /** A [NavBar] across the bottom. Phones. */
    Bar,

    /** A [NavRail] down the leading edge. Landscape phones, small tablets. */
    Rail,

    /** A [NavDrawer] down the leading edge, labels always visible. Desktop. */
    Drawer,
}

/**
 * Puts the app's destinations wherever the window has room for them.
 *
 * ```kotlin
 * NavigationSuiteScaffold(
 *     items = destinations,
 *     selectedIndex = current,
 *     action = { FloatingActionButton(Tabler.Outline.Search, "Search", ::search) },
 * ) {
 *     CurrentScreen()
 * }
 * ```
 *
 * A screen declares its destinations once and this decides where they go. The
 * Android app already writes the same three destinations into `MainToolbar` and
 * `MainNavigationRail` and picks between them by hand; this is that, with one
 * declaration.
 *
 * Which surface goes where, and why it is not configurable, are on the
 * navigation page: `docs/app/design-system/using/components/navigation.md`.
 *
 * The surface **overlays** the content rather than sitting below it, so read
 * [contentPadding] and inset your own scrolling content by it — the same way a
 * map insets its controls by a sheet's `visibleHeight`.
 *
 * @param type Override the automatic choice. For a screen that genuinely needs a
 *   different surface than its window size implies — rare, and worth a comment
 *   at the call site.
 */
@Composable
fun NavigationSuiteScaffold(
    items: List<NavItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    type: NavigationSuiteType = navigationSuiteTypeFor(LocalWindowSizeClass.current.width),
    /**
     * Whether destinations are named as well as drawn.
     *
     * `null` — the default — lets each surface answer for itself, because they
     * do not agree and should not: a rail is a column of icons on the edge of a
     * wide window and wants its words, a bar is four circles over the content
     * and the destinations of an app this size are ones its user already knows.
     * Set it to override both.
     */
    showLabels: Boolean? = null,
    containerColor: Color = Theme.colors.background,
    action: (@Composable () -> Unit)? = null,
    drawerHeader: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * What the navigation surface keeps clear of, passed through to whichever one
     * this window gets. Sided automatically: a bar takes the bottom and the sides,
     * a rail or drawer takes the leading side and the top and bottom.
     */
    windowInsets: WindowInsets? = null,
    content: @Composable (contentPadding: Dp) -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = containerColor) {
        when (type) {
            NavigationSuiteType.Bar -> Box(Modifier.fillMaxSize()) {
                // Measured, not calculated. A constant was wrong twice over: it
                // added the floating inset to a docked bar that has none, and the
                // bar's height is now derived from its content, so it grows at
                // large type and no arithmetic here could predict it.
                var barHeight by remember { mutableStateOf(NavBarDefaults.MinHeight) }
                val density = LocalDensity.current

                content(barHeight)

                // Anchored to the bottom, over the content. The one placement
                // decision this component exists to make.
                NavBar(
                    items = items,
                    selectedIndex = selectedIndex,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged {
                            barHeight = with(density) { it.height.toDp() }
                        },
                    showLabels = showLabels ?: false,
                    action = action,
                    windowInsets = windowInsets ?: WindowInsets.bottomEdges,
                )
            }

            NavigationSuiteType.Rail -> Row(Modifier.fillMaxSize()) {
                NavRail(
                    items = items,
                    selectedIndex = selectedIndex,
                    showLabels = showLabels ?: true,
                    header = drawerHeader,
                    action = action?.let { { it() } },
                    windowInsets = windowInsets ?: WindowInsets.leadingEdges,
                )
                Box(Modifier.weight(1f)) { content(0.dp) }
            }

            NavigationSuiteType.Drawer -> Row(Modifier.fillMaxSize()) {
                NavDrawer(
                    header = drawerHeader,
                    footer = action?.let { { it() } },
                    windowInsets = windowInsets ?: WindowInsets.leadingEdges,
                ) {
                    items.forEachIndexed { index, item ->
                        NavDrawerItem(
                            selected = index == selectedIndex,
                            onClick = item.onClick,
                            key = item.label,
                            badge = item.badge,
                            enabled = item.enabled,
                            contentDescription = item.contentDescription,
                        ) {
                            +item.label
                            item.iconFor(index == selectedIndex)?.let { icon ->
                                leading { +icon }
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f)) { content(0.dp) }
            }
        }
    }
}

/**
 * Which surface a window of this width should get.
 *
 * Pure, and tested, because the breakpoints are the whole behaviour — and a
 * layout that switches one class too early looks fine on the two devices anyone
 * checks and wrong on the third.
 */
fun navigationSuiteTypeFor(width: WindowWidthClass): NavigationSuiteType = when (width) {
    WindowWidthClass.Compact -> NavigationSuiteType.Bar
    WindowWidthClass.Medium -> NavigationSuiteType.Rail
    WindowWidthClass.Expanded, WindowWidthClass.Large -> NavigationSuiteType.Drawer
}
