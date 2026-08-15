package io.kontour.ui.nav

import androidx.compose.foundation.layout.Box
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
 * ### Where things go
 *
 * | Window | Surface | Placement |
 * |---|---|---|
 * | Compact | [NavBar] | **Bottom of the screen**, over the content |
 * | Medium | [NavRail] | **Leading edge**, beside the content |
 * | Expanded and up | [NavDrawer] | **Leading edge**, beside the content, labels always shown |
 *
 * That is not configurable by accident and it is not a website's layout.
 * Navigation lives at the bottom on a phone because that is where a thumb
 * reaches, and moves to the leading edge on a wide window because a horizontal
 * bar there would eat the dimension there is least of. A top bar in this system
 * is a [TopBar] — a title and its actions, not a place to put destinations.
 *
 * The bar **overlays** the content rather than sitting below it, matching the
 * floating toolbar the app uses over its map. Read [contentPadding] and inset
 * your own scrolling content by it, the same way a map insets its controls by a
 * sheet's `visibleHeight`.
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
    barStyle: NavBarStyle = NavBarStyle.Floating,
    showLabels: Boolean = true,
    containerColor: Color = Theme.colors.background,
    action: (@Composable () -> Unit)? = null,
    drawerHeader: (@Composable ColumnScope.() -> Unit)? = null,
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
                    style = barStyle,
                    showLabels = showLabels,
                    action = action,
                )
            }

            NavigationSuiteType.Rail -> Row(Modifier.fillMaxSize()) {
                NavRail(
                    items = items,
                    selectedIndex = selectedIndex,
                    showLabels = showLabels,
                    header = drawerHeader,
                    action = action?.let { { it() } },
                )
                Box(Modifier.weight(1f)) { content(0.dp) }
            }

            NavigationSuiteType.Drawer -> Row(Modifier.fillMaxSize()) {
                NavDrawer(
                    header = drawerHeader,
                    footer = action?.let { { it() } },
                ) {
                    items.forEachIndexed { index, item ->
                        NavDrawerItem(
                            label = item.label,
                            selected = index == selectedIndex,
                            onClick = item.onClick,
                            icon = item.iconFor(index == selectedIndex),
                            badge = item.badge,
                            enabled = item.enabled,
                            contentDescription = item.contentDescription,
                        )
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
