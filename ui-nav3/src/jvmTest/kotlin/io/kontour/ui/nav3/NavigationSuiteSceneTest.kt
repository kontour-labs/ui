package io.kontour.ui.nav3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.BackStyle
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.motion.pageBack
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.NavigationSuiteType
import io.kontour.ui.nav.navigationSuitePadding
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One back stack, with pages inside the nav bar and pages over it.
 *
 * Asked for as *"can we ensure there's an easy way to put screens above nav
 * components"*. Pages inside the navigation move under a bar that stays where
 * it is; a page marked `coversNavigation()` slides in over the bar as well,
 * and going back from it uncovers both.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationSuiteSceneTest {

    private object Home
    private object Stop
    private object Player

    private class Back {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
    }

    private val seen = mutableMapOf<String, (() -> Unit)?>()
    private val padding = mutableMapOf<String, Float>()

    @Composable
    private fun Page(name: String) {
        val back = pageBack()
        val bottom = navigationSuitePadding().calculateBottomPadding()
        SideEffect {
            seen[name] = back
            padding[name] = bottom.value
        }
        var taps by rememberSaveable { mutableIntStateOf(0) }
        Column(Modifier.fillMaxSize().testTag(name)) {
            Text("$name $taps", Modifier.testTag("$name-count"))
        }
        SideEffect { counters[name] = { taps++ } }
    }

    private val counters = mutableMapOf<String, () -> Unit>()
    private var depth = 0

    @Composable
    private fun Display(back: Back, backStack: SnapshotStateList<Any>, twoPane: Boolean = false, readsStack: Boolean = false) {
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides back.owner,
            LocalBackStyle provides BackStyle.Predictive,
        ) {
            KontourTheme(darkTheme = false) {
                OverlayHost {
                    // A host that reads its own stack — for a title, or which
                    // chat is open, as the gallery's Messages does — recomposes
                    // on every push and pop, and builds its entries' metadata
                    // afresh each time.
                    if (readsStack) {
                        val size = backStack.size
                        SideEffect { depth = size }
                    }
                    val suite = rememberNavigationSuiteSceneStrategy<Any>(
                        sceneStrategies = listOf(ListDetailSceneStrategy(twoPane, 0.4f, resizable = false, showDivider = true)),
                    ) { pages ->
                        NavigationSuiteScaffold(
                            type = NavigationSuiteType.Bar,
                            destinations = {
                                item(true, {}, key = "home", icon = null, modifier = Modifier.testTag("bar")) { +"Home" }
                                item(false, {}, key = "saved", icon = null) { +"Saved" }
                            },
                        ) { pages() }
                    }
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        sceneStrategies = listOf(suite),
                        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
                        entryProvider = entryProvider {
                            entry<Home>(metadata = listPane()) { Page("home") }
                            entry<Stop>(metadata = detailPane()) { Page("stop") }
                            entry<Player>(metadata = coversNavigation()) { Page("player") }
                        },
                    )
                }
            }
        }
    }

    private fun ComposeUiTest.bar(): Rect? {
        val nodes = onAllNodesWithTag("bar", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(nodes.size <= 1, "${nodes.size} nav bars on screen at once")
        return nodes.firstOrNull()?.boundsInRoot
    }

    /** A page pushed inside the navigation moves; the bar does not, on any frame. */
    @Test
    fun aPageInsideTheNavigationLeavesTheBarWhereItIs() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Home)
        setContent { Display(back, backStack) }
        waitForIdle()
        val rest = assertNotNull(bar(), "no bar at the navigation's root")
        assertTrue(padding.getValue("home") > 40f, "the page inside the navigation was not told the bar's height")

        mainClock.autoAdvance = false
        runOnUiThread { backStack += Stop }
        repeat(30) {
            mainClock.advanceTimeByFrame()
            assertEquals(rest, bar(), "the bar moved during a push inside the navigation, frame $it")
        }
        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithTag("stop", useUnmergedTree = true).assertExists()

        mainClock.autoAdvance = false
        runOnUiThread { back.input.backCompleted() }
        repeat(30) {
            mainClock.advanceTimeByFrame()
            assertEquals(rest, bar(), "the bar moved during a back inside the navigation, frame $it")
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(listOf<Any>(Home), backStack.toList())
    }

    /** A covering page is over the bar; going back from it, the bar is under it where it was. */
    @Test
    fun aCoveringPageGoesOverTheBarAndUncoversItGoingBack() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Home, Stop)
        setContent { Display(back, backStack) }
        waitForIdle()
        val rest = assertNotNull(bar())

        runOnUiThread { backStack += Player }
        waitForIdle()
        assertNull(bar(), "the bar is still drawn under a page that covers it")
        assertEquals(0f, padding.getValue("player"), "a covering page was told about a bar it covers")
        val player = onNodeWithTag("player", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(800f, player.bottom, 1f, "the covering page does not reach the bottom of the window")

        runOnUiThread { back.input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f)) }
        waitForIdle()
        runOnUiThread { back.input.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, 0.5f, 0f, 0f)) }
        waitForIdle()
        // Under the covering page, with the page it belongs to: that page slides
        // in beneath by the platform's parallax, the bar with it, as a tab bar
        // comes back with its page on iOS. Sideways only, and the same bar.
        val mid = assertNotNull(bar(), "mid-gesture, there is no bar under the covering page")
        assertEquals(rest.top, mid.top, 0.5f, "the bar under the covering page moved up or down")
        assertEquals(rest.width, mid.width, 0.5f, "the bar under the covering page changed size")
        assertTrue(abs(mid.left - rest.left) < 400f / 3f, "the bar under the covering page is ${mid.left - rest.left}px from rest")
        onNodeWithTag("stop", useUnmergedTree = true).assertExists()

        runOnUiThread { back.input.backCompleted() }
        waitForIdle()
        assertEquals(listOf<Any>(Home, Stop), backStack.toList())
        assertEquals(rest, bar())
    }

    /**
     * Back from a covering page to a list, finished by the gesture: the list
     * comes to rest once, from where the gesture left it.
     *
     * Reported from the gallery's Messages as *"when i try to go back and it
     * does the back animation, when the animation completes, it sort of
     * 'snaps' back to being like 75% complete and then does the end of the
     * animation again"*. Its host reads the stack, so the pop rebuilt the
     * list's `listPane()` metadata, and a role that was only equal to itself
     * made the scene the pop settled on unequal to the one the gesture had
     * been carrying in — the same key, so the same slot, told it was not the
     * target, went back toward where it enters from and came in again.
     */
    @Test
    fun aListComesToRestOnceAfterAFinishedBackGesture() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Home, Player)
        setContent { Display(back, backStack, readsStack = true) }
        waitForIdle()

        mainClock.autoAdvance = false
        runOnUiThread { back.input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f)) }
        mainClock.advanceTimeByFrame()
        // All the way, as a swipe's release carries it before the pop.
        for (step in 1..10) {
            runOnUiThread { back.input.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, step / 10f, 0f, 0f)) }
            mainClock.advanceTimeByFrame()
        }
        runOnUiThread { back.input.backCompleted() }

        // Frame by frame from the release: the list's distance from rest only
        // ever shrinks.
        var last = Float.MAX_VALUE
        repeat(90) { frame ->
            mainClock.advanceTimeByFrame()
            val left = onAllNodesWithTag("home", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.left
            if (left != null) {
                val away = abs(left)
                assertTrue(
                    away <= last + 0.5f,
                    "frame $frame after the release: the list moved back away from rest, ${last}px to ${away}px",
                )
                last = away
            }
        }
        assertEquals(listOf<Any>(Home), backStack.toList())
        assertEquals(1, depth)
        assertTrue(last < 0.5f, "the list did not come to rest: ${last}px off")
    }

    /** What a page saved survives a page inside the navigation, and one over it. */
    @Test
    fun aPagesSavedStateSurvivesBothKindsOfPage() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Home)
        setContent { Display(back, backStack) }
        waitForIdle()
        runOnUiThread { counters.getValue("home")() }
        waitForIdle()
        onNodeWithTag("home-count", useUnmergedTree = true).assertExists()

        runOnUiThread { backStack += Stop }
        waitForIdle()
        runOnUiThread { backStack.removeLastOrNull() }
        waitForIdle()
        assertTrue(textOf("home-count").endsWith(" 1"), "home lost what it saved going to a page inside the navigation: ${textOf("home-count")}")

        runOnUiThread { backStack += Player }
        waitForIdle()
        runOnUiThread { backStack.removeLastOrNull() }
        waitForIdle()
        assertTrue(textOf("home-count").endsWith(" 1"), "home lost what it saved under a covering page: ${textOf("home-count")}")
    }

    /** `pageBack()` is null at the navigation's root, and set on a page inside it and on one over it. */
    @Test
    fun pageBackKnowsWhereEachPageIs() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Home)
        setContent { Display(back, backStack) }
        waitForIdle()
        assertTrue("home" in seen)
        assertNull(seen["home"], "the navigation's root offered a back")

        runOnUiThread { backStack += Stop }
        waitForIdle()
        assertNotNull(seen["stop"], "a page inside the navigation had no back")

        runOnUiThread { backStack += Player }
        waitForIdle()
        val fromPlayer = assertNotNull(seen["player"], "a covering page had no back")
        runOnUiThread { fromPlayer() }
        waitForIdle()
        assertEquals(listOf<Any>(Home, Stop), backStack.toList())

        runOnUiThread { assertNotNull(seen["stop"])() }
        waitForIdle()
        assertEquals(listOf<Any>(Home), backStack.toList())
    }

    /** The pages inside the navigation take the strategies given for them: a list beside its detail. */
    @Test
    fun aListBesideItsDetailInsideTheNavigation() = runDesktopComposeUiTest(width = 1000, height = 700) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Home, Stop)
        setContent { Display(back, backStack, twoPane = true) }
        waitForIdle()
        onNodeWithTag("home", useUnmergedTree = true).assertExists()
        onNodeWithTag("stop", useUnmergedTree = true).assertExists()
        assertNotNull(bar(), "no bar round a list and its detail")
        assertNull(seen["home"], "the list offered a back with its detail beside it")

        runOnUiThread { assertNotNull(seen["stop"])() }
        waitForIdle()
        assertEquals(listOf<Any>(Home), backStack.toList())
        assertNotNull(bar())
    }

    private fun ComposeUiTest.textOf(tag: String): String {
        val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val text = androidx.compose.ui.semantics.SemanticsProperties.Text
        return if (text in node.config) node.config[text].joinToString { it.text } else ""
    }
}
