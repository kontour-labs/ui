package io.kontour.ui.nav

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.ScaffoldPadding
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
 * @param markerDrag Whether the marker can be dragged between destinations, and
 *   when a drag chooses one; see [NavMarkerDrag]. The bar's, the rail's and the
 *   modal rail's alike: whether choosing while dragging suits depends on how
 *   cheap the app's pages are to show, and they are the same pages whichever
 *   surface the window has. A rail drags its marker only while it has nothing
 *   to scroll.
 * @param barFadeUnder Whether the page fades out under a floating bar; see
 *   [NavBar]'s `fadeUnder`.
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
    markerDrag: NavMarkerDrag = NavMarkerDrag.SelectOnRelease,
    showLabels: Boolean? = null,
    railExpandable: Boolean = false,
    railExpandedWidth: Dp = NavRailDefaults.ExpandedWidth,
    // After the 0.4.1 parameters, and after one of another type, so a call that
    // passed them by position cannot land in it.
    barFadeUnder: Boolean = true,
    containerColour: Color = Theme.colours.background,
    colours: NavColours = NavDefaults.colours(),
    searchIndex: Int? = null,
    windowInsets: WindowInsets? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    search: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    // **The content is one composable, whichever surface the window has.** It
    // was called from a different branch for each — inside a `Box` beside the
    // bar, a `Row` beside the rail — and a composable that changes branch is a
    // new one, so a window crossing a breakpoint started the whole screen again:
    // a phone turned on its side, a foldable opened, a desktop window dragged
    // past 600dp, and everything the screen remembered, saved state and scroll
    // positions included, was gone. Here the content and the navigation are two
    // slots of one layout, and only the navigation's changes with the surface.
    //
    // **The bar is measured before the content, and its height handed over in
    // the same pass.** It was measured after, from a first frame laid out
    // against an estimate — the shortest a bar can be, 44 or 48dp, where a
    // floating bar with its margins is half as tall again and more over an
    // iPhone's home indicator — so anything anchored to the content's foot drew
    // under the bar for a frame and then jumped clear of it. Content composed
    // after the bar is measured reads the real height even in composition, and
    // the padding is one object written in measure, as `Scaffold`'s is, so the
    // bar changing height re-measures a list rather than recomposing the screen.
    val padding = remember { ScaffoldPadding() }
    val currentContent by rememberUpdatedState(content)
    val contentSlot: @Composable () -> Unit = remember(padding) {
        {
            CompositionLocalProvider(LocalNavigationSuitePadding provides padding) { currentContent(padding) }
        }
    }
    val navigation: @Composable () -> Unit = {
        NavigationSurface(
            type = type,
            state = state,
            barStyle = barStyle,
            barShape = barShape,
            barItemWidth = barItemWidth,
            barMarker = barMarker,
            markerDrag = markerDrag,
            barFadeUnder = barFadeUnder,
            showLabels = showLabels,
            railExpandable = railExpandable,
            railExpandedWidth = railExpandedWidth,
            colours = colours,
            searchIndex = searchIndex,
            windowInsets = windowInsets,
            header = header,
            search = search,
            action = action,
            destinations = destinations,
        )
    }
    // Remembered, as `Scaffold`'s slots are: a new lambda handed to `subcompose`
    // is a slot it composes again.
    val currentNavigation by rememberUpdatedState(navigation)
    val navigationSlot: @Composable () -> Unit = remember { { currentNavigation() } }

    Surface(modifier = modifier.fillMaxSize(), containerColour = containerColour) {
        SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            // A modal rail is drawn in the overlay host and lays out nothing here.
            val surface = subcompose(SuiteSlot.Navigation, navigationSlot).map { it.measure(loose) }

            when (type) {
                NavigationSuiteType.Bar -> {
                    // Measured, not calculated: the bar's height comes from its
                    // content, and grows with type size and labels.
                    val barHeight = surface.maxOfOrNull { it.height } ?: 0
                    padding.update(start = 0.dp, top = 0.dp, end = 0.dp, bottom = barHeight.toDp())
                    val body = subcompose(SuiteSlot.Content, contentSlot).map { it.measure(loose) }
                    layout(width, height) {
                        // The content first, so the bar draws over it: the bar
                        // overlays the content rather than sitting below it.
                        body.forEach { it.placeRelative(0, 0) }
                        surface.forEach { it.placeRelative((width - it.width) / 2, height - it.height) }
                    }
                }

                NavigationSuiteType.Rail, NavigationSuiteType.ExpandedRail -> {
                    // Beside the content, which takes the rest of the row and
                    // is handed no padding.
                    val railWidth = surface.maxOfOrNull { it.width } ?: 0
                    padding.update(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 0.dp)
                    val rest = (width - railWidth).coerceAtLeast(0)
                    val body = subcompose(SuiteSlot.Content, contentSlot)
                        .map { it.measure(loose.copy(minWidth = rest, maxWidth = rest)) }
                    layout(width, height) {
                        // Relative, so the rail is on the leading edge — the
                        // right, in right to left — and first in the order
                        // focus moves in, as it was in a `Row`.
                        surface.forEach { it.placeRelative(0, 0) }
                        body.forEach { it.placeRelative(railWidth, 0) }
                    }
                }

                NavigationSuiteType.ModalRail -> {
                    padding.update(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 0.dp)
                    val body = subcompose(SuiteSlot.Content, contentSlot).map { it.measure(loose) }
                    layout(width, height) {
                        body.forEach { it.placeRelative(0, 0) }
                        surface.forEach { it.placeRelative(0, 0) }
                    }
                }
            }
        }
    }
}

/** The surface [NavigationSuiteScaffold] puts its destinations in, for [type]. */
@Composable
private fun NavigationSurface(
    type: NavigationSuiteType,
    state: NavigationSuiteState,
    barStyle: NavBarStyle,
    barShape: Shape?,
    barItemWidth: NavBarItemWidth,
    barMarker: NavBarMarker,
    markerDrag: NavMarkerDrag,
    barFadeUnder: Boolean,
    showLabels: Boolean?,
    railExpandable: Boolean,
    railExpandedWidth: Dp,
    colours: NavColours,
    searchIndex: Int?,
    windowInsets: WindowInsets?,
    header: (@Composable ColumnScope.() -> Unit)?,
    search: (@Composable () -> Unit)?,
    action: (@Composable () -> Unit)?,
    destinations: @Composable NavScope.() -> Unit,
) {
    when (type) {
        NavigationSuiteType.Bar -> NavBarLayout(
            modifier = Modifier.fillMaxWidth(),
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
            markerDrag = markerDrag,
            fadeUnder = barFadeUnder,
        ) {
            destinations()
        }

        NavigationSuiteType.Rail, NavigationSuiteType.ExpandedRail -> {
            val wide = type == NavigationSuiteType.ExpandedRail
            NavRail(
                expanded = wide || (railExpandable && state.railExpanded),
                onExpandedChange = if (!wide && railExpandable) {
                    { if (it) state.expandRail() else state.collapseRail() }
                } else {
                    null
                },
                markerDrag = markerDrag,
                expandedWidth = railExpandedWidth,
                colours = colours,
                windowInsets = windowInsets ?: WindowInsets.leadingEdges,
                header = header,
                footer = action?.let { { it() } },
                content = destinations,
            )
        }

        NavigationSuiteType.ModalRail -> ModalNavRail(
            visible = state.modalVisible,
            onDismissRequest = state::hideModal,
            width = railExpandedWidth,
            markerDrag = markerDrag,
            // A panel over the page is raised, as every modal one is, unless
            // the app chose its ground.
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

private enum class SuiteSlot { Navigation, Content }

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
