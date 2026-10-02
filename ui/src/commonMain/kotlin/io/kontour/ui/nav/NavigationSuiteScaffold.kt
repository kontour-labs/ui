package io.kontour.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Shape
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
 * beside the content and hands it no padding. Content that is not written in
 * [content] itself — a page that a navigation host shows inside it — reads the
 * same padding with [navigationSuitePadding].
 *
 * @param type Override the automatic choice — [NavigationSuiteType.ModalRail],
 *   which is never chosen automatically, is the common reason.
 * @param barStyle The bar's style, when there is one.
 * @param barShape The bar's shape; see [NavBar]'s `shape`.
 * @param barItemWidth How wide the bar's destinations are; see [NavBarItemWidth].
 * @param barMarker What marks the bar's current destination; see [NavBarMarker].
 *   A rail's marker is its own.
 * @param barMarkerDrag Whether the bar's marker can be dragged between
 *   destinations, and when a drag chooses one; see [NavBarMarkerDrag]. A
 *   rail's cannot be dragged: it scrolls.
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
 * @param action A floating action button, usually. Beside a floating bar's
 *   capsule, inside a docked bar after everything else, at the foot of a rail:
 *   see [NavBar]'s `action`.
 */
@Composable
fun NavigationSuiteScaffold(
    destinations: @Composable NavScope.() -> Unit,
    modifier: Modifier = Modifier,
    state: NavigationSuiteState = rememberNavigationSuiteState(),
    type: NavigationSuiteType = navigationSuiteTypeFor(LocalWindowSizeClass.current.width),
    barStyle: NavBarStyle = NavBarStyle.Floating,
    barShape: Shape? = null,
    barItemWidth: NavBarItemWidth = NavBarItemWidth.Equal,
    barMarker: NavBarMarker = NavBarDefaults.destinationMarker(),
    barMarkerDrag: NavBarMarkerDrag = NavBarMarkerDrag.SelectOnRelease,
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

                val padding = PaddingValues(bottom = barHeight)
                CompositionLocalProvider(LocalNavigationSuitePadding provides padding) { content(padding) }

                NavBarLayout(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                    style = barStyle,
                    showLabels = showLabels ?: false,
                    colours = colours,
                    arrangement = null,
                    windowInsets = windowInsets ?: WindowInsets.bottomEdges,
                    search = search,
                    searchIndex = searchIndex,
                    action = action,
                    shape = barShape,
                    itemWidth = barItemWidth,
                    marker = barMarker,
                    markerDrag = barMarkerDrag,
                ) {
                    destinations()
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
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalNavigationSuitePadding provides NoPadding) { content(NoPadding) }
                }
            }

            NavigationSuiteType.ModalRail -> Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalNavigationSuitePadding provides NoPadding) { content(NoPadding) }
                ModalNavRail(
                    visible = state.modalVisible,
                    onDismissRequest = state::hideModal,
                    width = railExpandedWidth,
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
 * What a [NavigationSuiteScaffold] around this content asks it to keep clear
 * of: the bar's height at the bottom, and nothing for a rail, which sits beside
 * the content. Nothing outside a scaffold.
 *
 * The same padding the scaffold hands its `content`, for a page that is not
 * written there — one that a navigation host shows inside the scaffold, such as
 * `:ui-nav3`'s navigation-suite scene:
 *
 * ```kotlin
 * LazyColumn(contentPadding = navigationSuitePadding()) { stopRows() }
 * ```
 */
@Composable
@ReadOnlyComposable
fun navigationSuitePadding(): PaddingValues = LocalNavigationSuitePadding.current

/** See [navigationSuitePadding]. */
internal val LocalNavigationSuitePadding = compositionLocalOf<PaddingValues> { NoPadding }

private val NoPadding = PaddingValues(0.dp)

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
