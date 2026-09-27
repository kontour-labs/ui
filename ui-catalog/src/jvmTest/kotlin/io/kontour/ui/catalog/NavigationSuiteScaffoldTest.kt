package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Route
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowSizeClass
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.nav.NavScope
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.NavigationSuiteState
import io.kontour.ui.nav.NavigationSuiteType
import io.kontour.ui.nav.navigationSuiteTypeFor
import io.kontour.ui.nav.rememberNavigationSuiteState
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One set of destinations, the right surface for the window, and the state
 * that has to survive between them.
 *
 * `NavigationSuiteScaffold` is where the rewrite's promise is kept or broken: a
 * screen writes its destinations once, as a `NavScope`, and the scaffold draws
 * them as a bar, a rail, a sidebar or a modal rail. So each surface is asked
 * for through the window size class — the way an app gets it — and recognised
 * by what it does with the same tree: whether the destinations run across or
 * down, whether labels and section titles are drawn, and whether a group is a
 * disclosure or opens a menu.
 *
 * The rest is the scaffold's own behaviour, none of which shows at rest: the
 * modal rail's order of events when a destination is chosen, the rail's
 * expansion living in the hoisted state, and the search being placed by
 * *counting* destinations rather than by where it was composed.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationSuiteScaffoldTest {

    /** A destination, a section holding one and a group, all findable by tag. */
    @Composable
    private fun NavScope.tree(here: String, go: (String) -> Unit) {
        item(here == "home", { go("home") }, key = "home", icon = Tabler.Outline.Home, modifier = Modifier.testTag("home")) {
            +"Home"
        }
        section("Saved") {
            item(here == "stops", { go("stops") }, key = "stops", icon = Tabler.Outline.Star, modifier = Modifier.testTag("stops")) {
                +"Stops"
            }
            group(false, {}, key = "routes", icon = Tabler.Outline.Route, header = { +"Routes" }, modifier = Modifier.testTag("routes")) {
                item(here == "950", { go("950") }, key = "950", icon = null) { +"950" }
            }
        }
    }

    /**
     * Each width class gets its surface, chosen by the scaffold itself: a bar
     * along the bottom that inlines the section and turns the group into a
     * menu; a narrow rail that does the same down the side; a sidebar with
     * labels, a section title and a disclosure.
     */
    @Test
    fun eachWindowWidthGetsItsSurface() = runComposeUiTest {
        val widths = listOf(
            400.dp to NavigationSuiteType.Bar,
            700.dp to NavigationSuiteType.Rail,
            1000.dp to NavigationSuiteType.ExpandedRail,
            1600.dp to NavigationSuiteType.ExpandedRail,
        )
        var width by mutableStateOf(widths.first().first)
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                CompositionLocalProvider(LocalWindowSizeClass provides WindowSizeClass.of(width, 800.dp)) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        NavigationSuiteScaffold(destinations = { tree("home") {} }) {}
                    }
                }
            }
        }

        for ((w, expected) in widths) {
            val widthClass = WindowWidthClass.of(w)
            assertEquals(expected, navigationSuiteTypeFor(widthClass), "a $widthClass window's type")
            width = w
            waitForIdle()

            val root = onRoot().fetchSemanticsNode().boundsInRoot
            val home = bounds("home")
            val stops = bounds("stops")
            val routes = onNodeWithTag("routes").fetchSemanticsNode().config
            val where = "at ${w.value.toInt()}dp ($widthClass, $expected)"

            assertEquals(Role.Button, routes.getOrNull(SemanticsProperties.Role), "$where the group is not a button")
            when (expected) {
                NavigationSuiteType.Bar -> {
                    assertTrue(
                        abs(home.center.y - stops.center.y) <= 1f && stops.left > home.right,
                        "$where the destinations do not run across one row: Home $home, Stops $stops",
                    )
                    assertTrue(home.top > root.height / 2, "$where the bar is not at the bottom: Home at $home")
                    onAllNodesWithText("Home").assertCountEquals(0)
                    onAllNodesWithText("Saved").assertCountEquals(0)
                    assertNull(
                        routes.getOrNull(SemanticsProperties.StateDescription),
                        "$where the group reports a disclosure state, but a bar's group opens a menu",
                    )
                }

                NavigationSuiteType.Rail, NavigationSuiteType.ExpandedRail -> {
                    val wide = expected == NavigationSuiteType.ExpandedRail
                    assertTrue(
                        abs(home.left - stops.left) <= 1f && stops.top > home.bottom,
                        "$where the destinations do not run down one column: Home $home, Stops $stops",
                    )
                    assertTrue(home.left < root.width / 4, "$where the rail is not at the leading edge: Home at $home")
                    if (wide) {
                        assertTrue(home.width > px(200.dp), "$where the sidebar's rows are ${home.width}px wide")
                        onNodeWithText("Home").fetchSemanticsNode()
                        val title = onNodeWithText("Saved").fetchSemanticsNode()
                        assertTrue(SemanticsProperties.Heading in title.config, "$where the section title is not a heading")
                        assertNotNull(
                            routes.getOrNull(SemanticsProperties.StateDescription),
                            "$where the group reports no expanded state, so it is not the sidebar's disclosure",
                        )
                    } else {
                        assertTrue(home.width < px(100.dp), "$where the collapsed rail's rows are ${home.width}px wide")
                        onAllNodesWithText("Home").assertCountEquals(0)
                        onAllNodesWithText("Saved").assertCountEquals(0)
                        onNodeWithContentDescription("Home").fetchSemanticsNode()
                        assertNull(
                            routes.getOrNull(SemanticsProperties.StateDescription),
                            "$where the group reports a disclosure state, but a collapsed rail's group opens a menu",
                        )
                    }
                }

                NavigationSuiteType.ModalRail -> error("never chosen automatically")
            }
        }
    }

    /**
     * No navigation on screen until `showModal()`; choosing a destination runs
     * its `onClick` while the rail is still open, and the rail closes a frame
     * later.
     */
    @Test
    fun theModalRailOpensOnRequestAndClosesAfterAChoice() = runComposeUiTest {
        var state: NavigationSuiteState? = null
        var here by mutableStateOf("home")
        var openWhenChosen: Boolean? = null
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    val suite = rememberNavigationSuiteState()
                    state = suite
                    NavigationSuiteScaffold(
                        destinations = {
                            tree(here) {
                                openWhenChosen = suite.modalVisible
                                here = it
                            }
                        },
                        state = suite,
                        type = NavigationSuiteType.ModalRail,
                    ) {}
                }
            }
        }
        waitForIdle()
        val suite = requireNotNull(state)

        onAllNodesWithText("Stops").assertCountEquals(0)
        onAllNodesWithContentDescription("Stops").assertCountEquals(0)

        suite.showModal()
        waitForIdle()
        onNodeWithText("Stops").fetchSemanticsNode()

        mainClock.autoAdvance = false
        onNodeWithText("Stops").performClick()
        assertEquals("stops", here, "choosing Stops in the modal rail did not run its onClick")
        assertEquals(true, openWhenChosen, "the destination's onClick ran after the modal rail had already closed")
        assertTrue(suite.modalVisible, "the modal rail closed on the frame of the click, before the page could change behind it")

        var frames = 0
        while (suite.modalVisible && frames < 10) {
            mainClock.advanceTimeByFrame()
            frames++
        }
        assertTrue(
            !suite.modalVisible && frames <= 3,
            "the modal rail was still asked to stay open $frames frames after the choice " +
                "(visible = ${suite.modalVisible}) — it should close on the frame after",
        )

        mainClock.autoAdvance = true
        waitForIdle()
        onAllNodesWithText("Stops").assertCountEquals(0)
    }

    /**
     * `state.railExpanded` is what the rail's toggle drives, and it holds
     * through recompositions while the window stays a rail.
     */
    @Test
    fun theRailStaysExpandedWhileItIsARail() = runComposeUiTest {
        var state: NavigationSuiteState? = null
        var here by mutableStateOf("home")
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    val suite = rememberNavigationSuiteState()
                    state = suite
                    NavigationSuiteScaffold(
                        destinations = { tree(here) { here = it } },
                        state = suite,
                        type = NavigationSuiteType.Rail,
                        railExpandable = true,
                    ) {}
                }
            }
        }
        waitForIdle()
        val suite = requireNotNull(state)

        assertTrue(!suite.railExpanded, "the rail started expanded")
        onAllNodesWithText("Home").assertCountEquals(0)

        onNodeWithContentDescription("Expand navigation").performClick()
        waitForIdle()
        assertTrue(suite.railExpanded, "the rail's toggle did not reach state.railExpanded")
        onNodeWithText("Home").fetchSemanticsNode()

        // Choosing somewhere recomposes every destination and the scaffold's
        // content; the rail is still a rail, so it stays as wide as it was.
        onNodeWithText("Stops").performClick()
        waitForIdle()
        assertEquals("stops", here)
        assertTrue(suite.railExpanded, "choosing a destination collapsed the rail")
        onNodeWithText("Home").fetchSemanticsNode()

        onNodeWithContentDescription("Collapse navigation").performClick()
        waitForIdle()
        assertTrue(!suite.railExpanded, "the rail's toggle did not collapse state.railExpanded")
        onAllNodesWithText("Home").assertCountEquals(0)
    }

    /**
     * `searchIndex` counts destinations: before the end it sits after that many,
     * and at the count, past it, or null it sits after all of them.
     */
    @Test
    fun theSearchGoesAfterSearchIndexDestinations() = runComposeUiTest {
        var index by mutableStateOf<Int?>(1)
        setContent { Bar(index, action = false) }

        for (at in listOf(1, 3, 5, null)) {
            index = at
            waitForIdle()
            val (first, second, third) = listOf("d0", "d1", "d2").map { bounds(it) }
            val search = bounds("search")
            if (at == 1) {
                assertTrue(
                    search.left >= first.right && search.right <= second.left,
                    "searchIndex = 1 put the search at ${search.left}..${search.right}, not between the " +
                        "first destination (..${first.right}) and the second (${second.left}..)",
                )
            } else {
                assertTrue(
                    search.left >= third.right,
                    "searchIndex = $at put the search at ${search.left}..${search.right}, not after the " +
                        "last of three destinations (..${third.right})",
                )
            }
        }
    }

    /**
     * The action is "in the bar, after everything" — after the search too,
     * wherever `searchIndex` put it.
     */
    @Test
    fun theActionStaysLastWhateverTheSearchIndex() = runComposeUiTest {
        var index by mutableStateOf<Int?>(1)
        setContent { Bar(index, action = true) }

        val placements = mutableListOf<String>()
        for (at in listOf(1, 3, null)) {
            index = at
            waitForIdle()
            val last = bounds("d2")
            val search = bounds("search")
            val action = bounds("action")
            if (action.left < search.right || action.left < last.right) {
                placements += "searchIndex = $at: action at ${action.left}..${action.right}, search at " +
                    "${search.left}..${search.right}, last destination ..${last.right}"
            }
        }
        assertTrue(
            placements.isEmpty(),
            "the action is documented as after everything in the bar, and was not: " + placements.joinToString("; "),
        )
    }

    /** A bar of three destinations with a search after [index] of them, and optionally an action. */
    @Composable
    private fun Bar(index: Int?, action: Boolean) {
        KontourTheme(darkTheme = false, reduceMotion = true) {
            OverlayHost(Modifier.fillMaxSize()) {
                NavigationSuiteScaffold(
                    destinations = {
                        listOf("Home", "Map", "Saved").forEachIndexed { i, name ->
                            item(i == 0, {}, key = name, icon = Tabler.Outline.Bus, modifier = Modifier.testTag("d$i")) {
                                +name
                            }
                        }
                    },
                    type = NavigationSuiteType.Bar,
                    searchIndex = index,
                    search = { Box(Modifier.size(24.dp).testTag("search")) },
                    action = if (action) ({ Box(Modifier.size(40.dp).testTag("action")) }) else null,
                ) {}
            }
        }
    }

    private fun ComposeUiTest.bounds(tag: String): Rect = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.px(dp: Dp): Float = with(density) { dp.toPx() }
}
