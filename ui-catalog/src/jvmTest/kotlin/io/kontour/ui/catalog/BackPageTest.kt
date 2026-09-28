package io.kontour.ui.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test

/**
 * The Back page drives real stacks and real overlays, and its readout names
 * what the dispatcher says back would reach.
 *
 * The readout matches handlers by their class names, which are the library's
 * own and internal to it — so a rename there shows up here, as a readout that
 * prints a raw class name, rather than on a phone as a page that says so.
 */
@OptIn(ExperimentalTestApi::class)
class BackPageTest {

    /** The gallery's own dispatcher, with a real back input on it, as a phone's would be. */
    private class Gallery {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
    }

    @Test
    fun aStackIsPoppedAndThenBackIsNotTheFrames() = runDesktopComposeUiTest(width = 900, height = 1600) {
        val gallery = Gallery()
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides gallery.owner) {
                KontourTheme(darkTheme = false) { BackPage() }
            }
        }
        onNodeWithText("Back goes to: nothing in the frame — it would go past it").assertExists()
        onNodeWithText("Back", useUnmergedTree = false).assertIsNotEnabled()

        onNodeWithText("Perth Busport").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Back goes to: a Navigation 3 stack, which pops a page").assertExists()

        // The simulator's back, into the frame's dispatcher.
        onNodeWithText("Back").performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithText("Route 950").assertDoesNotExist()
        onNodeWithText("Back goes to: nothing in the frame — it would go past it").assertExists()
    }

    @Test
    fun anOpenSheetTakesBackBeforeThePage() = runDesktopComposeUiTest(width = 900, height = 1600) {
        val gallery = Gallery()
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides gallery.owner) {
                KontourTheme(darkTheme = false) { BackPage() }
            }
        }
        onNodeWithText("Bottom sheet").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Departures").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Back goes to: the overlay on top").assertExists()

        // A real back, from the gallery's own input, as the phone's gesture
        // arrives: it reaches into the frame.
        runOnUiThread { gallery.input.backCompleted() }
        waitForIdle()
        onAllNodesWithText("12:04 · 950").fetchSemanticsNodes().let { check(it.isEmpty()) { "back did not close the sheet" } }
        onNodeWithText("Back goes to: nothing in the frame — it would go past it").assertExists()
    }

    @Test
    fun aDetailInItsPaneIsNamedAsThePane() = runDesktopComposeUiTest(width = 1100, height = 1600) {
        val gallery = Gallery()
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides gallery.owner) {
                KontourTheme(darkTheme = false) { BackPage() }
            }
        }
        onNodeWithText("Tablet").performClick()
        onNodeWithText("List and detail").performScrollTo().performClick()
        waitForIdle()
        onAllNodesWithText("Claremont").onFirst().performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Back goes to: the pane, which closes itself").assertExists()
    }

    @Test
    fun aDetailOnOnePaneIsNamedAsAStack() = runDesktopComposeUiTest(width = 900, height = 1600) {
        val gallery = Gallery()
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides gallery.owner) {
                KontourTheme(darkTheme = false) { BackPage() }
            }
        }
        onNodeWithText("List and detail").performScrollTo().performClick()
        waitForIdle()
        onAllNodesWithText("Claremont").onFirst().performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Back goes to: a Navigation 3 stack, which pops a page").assertExists()
    }
}
