package io.kontour.ui.nav3

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.BackStyle
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.motion.pageBack
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `pageBack()`: a back button's action, from the page that shows it.
 *
 * Reported as a back button on the root page while the page over it was being
 * swiped away. The app showed its button while `backStack.size > 1`, which is
 * true for the whole of the gesture: `NavDisplay` pops only once the gesture is
 * finished, and builds the root page under it from the entries behind the one
 * leaving. A page knows which of those it is; the back stack does not.
 */
@OptIn(ExperimentalTestApi::class)
class PageBackActionTest {

    private object Stops
    private object Stop
    private data class Detail(val name: String)

    private class Back {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
    }

    /** What each page read as its `pageBack()`, at its latest composition. */
    private val seen = mutableMapOf<String, (() -> Unit)?>()

    @Composable
    private fun Page(name: String) {
        val back = pageBack()
        SideEffect { seen[name] = back }
        Text(name, Modifier.fillMaxSize())
    }

    @Composable
    private fun Display(
        back: Back,
        backStack: SnapshotStateList<Any>,
        twoPane: Boolean = false,
        stopContent: @Composable () -> Unit = { Page("stop") },
    ) {
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides back.owner,
            LocalBackStyle provides BackStyle.Predictive,
        ) {
            KontourTheme(darkTheme = false) {
                OverlayHost {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        sceneStrategies = listOf(
                            ListDetailSceneStrategy(twoPane = twoPane, 0.4f, resizable = false, showDivider = true),
                        ),
                        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
                        entryProvider = entryProvider {
                            entry<Stops>(metadata = listPane()) { Page("stops") }
                            entry<Stop> { stopContent() }
                            entry<Detail>(metadata = detailPane()) { Page(it.name) }
                        },
                    )
                }
            }
        }
    }

    /** What [name] read, having been composed: a page never composed reads nothing. */
    private fun backOf(name: String): (() -> Unit)? {
        assertTrue(name in seen, "$name was never composed, so this measures nothing")
        return seen[name]
    }

    private fun ComposeUiTest.press(back: () -> Unit) {
        runOnUiThread { back() }
        waitForIdle()
    }

    /** The report: under the gesture, the page being revealed has no back. */
    @Test
    fun theRootPageHasNoBackWhileThePageOverItIsSwipedAway() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Stops, Stop)
        setContent { Display(back, backStack) }
        waitForIdle()
        assertNotNull(backOf("stop"), "the top page has somewhere to go back to")

        runOnUiThread { back.input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f)) }
        waitForIdle()
        runOnUiThread { back.input.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, 0.5f, 0f, 0f)) }
        waitForIdle()

        assertEquals(listOf<Any>(Stops, Stop), backStack.toList(), "the stack still holds both pages mid-gesture")
        assertNull(backOf("stops"), "the root page, revealed under the gesture, offered a back")
        assertNotNull(backOf("stop"), "the page being swiped away lost its back mid-gesture")
    }

    /** Pressed, it goes back as the system's back does: one page, animated, then nothing more. */
    @Test
    fun itPopsThePage() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Stops, Stop)
        setContent { Display(back, backStack) }
        waitForIdle()

        press(assertNotNull(backOf("stop")))
        assertEquals(listOf<Any>(Stops), backStack.toList())
        assertNull(backOf("stops"), "the root page offered a back with nothing behind it")
    }

    /** Through the page's own handlers first: a page that asks before leaving is asked. */
    @Test
    fun aHandlerInThePageTakesItFirst() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Stops, Stop)
        var asked = 0
        setContent {
            Display(back, backStack, stopContent = {
                val owner = LocalNavigationEventDispatcherOwner.current
                DisposableEffect(owner) {
                    val handler = object : NavigationEventHandler<NavigationEventInfo>(Ask, true) {
                        override fun onBackCompleted() {
                            asked++
                        }
                    }
                    owner?.navigationEventDispatcher?.addHandler(handler)
                    onDispose { handler.remove() }
                }
                Page("stop")
            })
        }
        waitForIdle()

        press(assertNotNull(backOf("stop")))
        assertEquals(1, asked, "the page's own handler was not asked")
        assertEquals(listOf<Any>(Stops, Stop), backStack.toList(), "the page left without its handler's say")
    }

    private object Ask : NavigationEventInfo()

    /**
     * Beside its list, a detail goes back to the list, and the list has no back
     * of its own while the detail is there: back would take the detail.
     */
    @Test
    fun onTwoPanesTheDetailHasTheBackAndTheListDoesNot() = runDesktopComposeUiTest(width = 1000, height = 700) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Stops, Detail("Perth Busport"))
        setContent { Display(back, backStack, twoPane = true) }
        waitForIdle()

        assertNull(backOf("stops"), "the list offered a back with a detail beside it")
        press(assertNotNull(backOf("Perth Busport"), "the detail had no back"))
        assertEquals(listOf<Any>(Stops), backStack.toList())
        assertNull(backOf("stops"), "the list, at the root, offered a back")
    }

    /** The control: a list with pages behind it keeps their back while it is alone. */
    @Test
    fun aListWithPagesBehindItKeepsTheirBack() = runDesktopComposeUiTest(width = 1000, height = 700) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Stop, Stops)
        setContent { Display(back, backStack, twoPane = true) }
        waitForIdle()

        press(assertNotNull(backOf("stops"), "a list with a page behind it had no back"))
        assertEquals(listOf<Any>(Stop), backStack.toList())
    }
}
