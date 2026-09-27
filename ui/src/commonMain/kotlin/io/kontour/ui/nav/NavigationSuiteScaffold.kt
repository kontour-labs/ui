package io.kontour.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.adaptive.bottomEdges
import io.kontour.ui.adaptive.leadingEdges
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.Theme

/** Which navigation surface a window gets. */
enum class NavigationSuiteType {
    /** A [NavBar] across the bottom. Phones. */
    Bar,

    /** A collapsed [NavRail] down the leading edge, expandable if asked. Small tablets, landscape phones. */
    Rail,

    /** A [NavRail] at its widest, labels always showing: a sidebar. Desktops. */
    ExpandedRail,

    /**
     * A [ModalNavRail] the app opens — from a menu button in its top bar — with
     * no navigation on screen until it does. A phone whose destinations are too
     * many, or too nested, for a bar.
     */
    ModalRail,
}

/**
 * What a [NavigationSuiteScaffold] remembers across the surfaces it switches
 * between: whether the modal rail is open, and whether the rail is expanded.
 * Hoisted, so a top bar's menu button can open the modal rail.
 */
@Stable
class NavigationSuiteState internal constructor(railExpanded: Boolean) {

    /** Whether the [NavigationSuiteType.ModalRail] is showing. */
    var modalVisible: Boolean by mutableStateOf(false)
        private set

    /** Whether the [NavigationSuiteType.Rail] is expanded, when it may be. */
    var railExpanded: Boolean by mutableStateOf(railExpanded)
        private set

    fun showModal() {
        modalVisible = true
    }

    fun hideModal() {
        modalVisible = false
    }

    fun expandRail() {
        railExpanded = true
    }

    fun collapseRail() {
        railExpanded = false
    }

    internal companion object {
        /** The rail's width survives a configuration change; an open modal rail does not need to. */
        val Saver: Saver<NavigationSuiteState, Boolean> = Saver(
            save = { it.railExpanded },
            restore = { NavigationSuiteState(it) },
        )
    }
}

/** Remembers a [NavigationSuiteState], its rail starting at [railExpanded]. */
@Composable
fun rememberNavigationSuiteState(railExpanded: Boolean = false): NavigationSuiteState =
    rememberSaveable(saver = NavigationSuiteState.Saver) { NavigationSuiteState(railExpanded) }

/**
 * Puts the app's destinations wherever the window has room for them.
 *
 * ```kotlin
 * val suite = rememberNavigationSuiteState()
 * NavigationSuiteScaffold(
 *     destinations = {
 *         item(here == Home, { go(Home) }, key = Home, icon = Tabler.Outline.Home) { +"Home" }
 *         item(here == Saved, { go(Saved) }, key = Saved, icon = Tabler.Outline.Star) { +"Saved" }
 *     },
 *     state = suite,
 * ) { padding ->
 *     CurrentScreen(Modifier.padding(padding))
 * }
 * ```
 *
 * The destinations are written once, as a [NavScope], and drawn by a [NavBar]
 * on a phone, a [NavRail] on a tablet, a sidebar on a desktop, or a
 * [ModalNavRail] the app opens: see [NavigationSuiteType] and
 * [navigationSuiteTypeFor].
 *
 * The bar **overlays** the content rather than sitting below it, so read the
 * padding handed to [content] and inset scrolling content by it. A rail sits
 * beside the content and hands it no padding.
 *
 * @param type Override the automatic choice — [NavigationSuiteType.ModalRail],
 *   which is never chosen automatically, is the common reason.
 * @param barStyle The bar's style, when there is one.
 * @param glass A frosted bar and modal rail, rather than painted ones.
 * @param showLabels Whether the bar names its destinations. `null` leaves it to
 *   the bar, which does not; a rail shows labels whenever it is wide enough.
 * @param railExpandable Whether the [NavigationSuiteType.Rail] has a toggle to
 *   grow into a sidebar. Its state lives in [state], so it persists while the
 *   window stays a rail.
 * @param railExpandedWidth The sidebar's width, expanded or modal.
 * @param searchIndex Where [search] goes among the bar's destinations, counted:
 *   `2` after the second. `null`, or a number at or past the count, puts it
 *   after all of them.
 * @param header Above the destinations in a rail or a modal rail.
 * @param search In the bar, among the destinations — usually a [NavSearch]. A
 *   rail's is the app's to put in [header].
 * @param action In the bar, after everything; at the foot of a rail.
 */
@Composable
fun NavigationSuiteScaffold(
    destinations: @Composable NavScope.() -> Unit,
    modifier: Modifier = Modifier,
    state: NavigationSuiteState = rememberNavigationSuiteState(),
    type: NavigationSuiteType = navigationSuiteTypeFor(LocalWindowSizeClass.current.width),
    barStyle: NavBarStyle = NavBarStyle.Floating,
    glass: Boolean = false,
    showLabels: Boolean? = null,
    railExpandable: Boolean = false,
    railExpandedWidth: Dp = NavRailDefaults.ExpandedWidth,
    containerColour: Color = Theme.colours.background,
    colours: NavColours = NavDefaults.colours(),
    searchIndex: Int? = null,
    windowInsets: WindowInsets? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    search: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), containerColour = containerColour) {
        when (type) {
            NavigationSuiteType.Bar -> Box(Modifier.fillMaxSize()) {
                // Measured, not calculated: the bar's height comes from its
                // content, and grows with type size and labels.
                val estimate = NavBarDefaults.MinHeight
                var barHeight by remember { mutableStateOf(estimate) }
                val density = LocalDensity.current

                content(PaddingValues(bottom = barHeight))

                NavBarLayout(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                    style = barStyle,
                    glass = glass,
                    showLabels = showLabels ?: false,
                    colours = colours,
                    arrangement = null,
                    windowInsets = windowInsets ?: WindowInsets.bottomEdges,
                    search = search,
                    searchIndex = searchIndex,
                ) {
                    destinations()
                    if (action != null) slot { action() }
                }
            }

            NavigationSuiteType.Rail, NavigationSuiteType.ExpandedRail -> Row(Modifier.fillMaxSize()) {
                val wide = type == NavigationSuiteType.ExpandedRail
                NavRail(
                    expanded = wide || (railExpandable && state.railExpanded),
                    onExpandedChange = if (!wide && railExpandable) {
                        { if (it) state.expandRail() else state.collapseRail() }
                    } else {
                        null
                    },
                    expandedWidth = railExpandedWidth,
                    colours = colours,
                    windowInsets = windowInsets ?: WindowInsets.leadingEdges,
                    header = header,
                    footer = action?.let { { it() } },
                    content = destinations,
                )
                Box(Modifier.weight(1f)) { content(PaddingValues(0.dp)) }
            }

            NavigationSuiteType.ModalRail -> Box(Modifier.fillMaxSize()) {
                content(PaddingValues(0.dp))
                ModalNavRail(
                    visible = state.modalVisible,
                    onDismissRequest = state::hideModal,
                    width = railExpandedWidth,
                    glass = glass,
                    // A panel over the page is raised, as every modal one is,
                    // unless the app chose its ground.
                    colours = if (colours.container == Theme.colours.surface) {
                        colours.copy(container = Theme.colours.surfaceRaised)
                    } else {
                        colours
                    },
                    windowInsets = windowInsets ?: WindowInsets.leadingEdges,
                    header = header,
                    footer = action?.let { { it() } },
                    content = destinations,
                )
            }
        }
    }
}

/**
 * Which surface a window of this width gets: a bar on a phone, a rail on a
 * small tablet, a sidebar from there up. [NavigationSuiteType.ModalRail] is
 * never automatic; an app chooses it.
 *
 * Pure, and tested, because the breakpoints are the whole behaviour.
 */
fun navigationSuiteTypeFor(width: WindowWidthClass): NavigationSuiteType = when (width) {
    WindowWidthClass.Compact -> NavigationSuiteType.Bar
    WindowWidthClass.Medium -> NavigationSuiteType.Rail
    WindowWidthClass.Expanded, WindowWidthClass.Large -> NavigationSuiteType.ExpandedRail
}
