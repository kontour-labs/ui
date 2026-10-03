package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An open search panel keeps clear of every edge of the window, whichever end
 * it stands on.
 *
 * Reported from the gallery's chats: *"in the chat search, the results popup
 * doesn't respect the device's top safe area"*. Standing on the keyboard the
 * panel cleared the bottom and the sides, and results tall enough to reach
 * the top of the window ran up under the status bar. Hung from the top it was
 * the other way round, and long results ran under the keyboard.
 *
 * The window is 360 by 640 at a density of 1, with a 40px status bar, and a
 * 30px gesture bar or a 260px keyboard.
 */
@OptIn(ExperimentalTestApi::class)
class NavSearchInsetsTest {

    @Test
    fun overTheKeyboardTallResultsStopBelowTheStatusBar() = runComposeUiTest {
        open(NavExpandPlacement.AboveKeyboard, WindowInsets(top = StatusBar.dp, bottom = GestureBar.dp))
        val top = onAllNodesWithText("Result 0").fetchSemanticsNodes().single().boundsInRoot.top
        assertTrue(top >= StatusBar, "the first result is at ${top}px, under the ${StatusBar}px status bar")
        val field = onAllNodes(hasSetTextAction()).fetchSemanticsNodes().single().boundsInRoot
        assertTrue(field.bottom <= Window - GestureBar, "the field's foot at ${field.bottom}px is on the gesture bar")
    }

    @Test
    fun atTheTopTallResultsStopAboveTheKeyboard() = runComposeUiTest {
        open(NavExpandPlacement.Top, WindowInsets(top = StatusBar.dp, bottom = Keyboard.dp))
        val field = onAllNodes(hasSetTextAction()).fetchSemanticsNodes().single().boundsInRoot
        assertTrue(field.top >= StatusBar, "the field's top at ${field.top}px is under the status bar")
        // The results are cut off where the panel ends, and the ones past it
        // have no bounds at all.
        val foot = onAllNodesWithText("Result", substring = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .filter { it.height > 0f }
            .maxOf { it.bottom }
        assertTrue(foot <= Window - Keyboard, "results are drawn down to ${foot}px, under the keyboard's top at ${Window - Keyboard}px")
    }

    private fun ComposeUiTest.open(placement: NavExpandPlacement, insets: WindowInsets) {
        lateinit var state: NavSearchState
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.requiredSize(360.dp, Window.dp)) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.White)) {
                            state = rememberNavSearchState()
                            NavBar {
                                item(true, {}, key = "chats", icon = SystemIcons.Check) { +"Chats" }
                                slot(weight = 1f) {
                                    NavSearch(
                                        state = state,
                                        placement = placement,
                                        windowInsets = insets,
                                        results = { repeat(30) { Text("Result $it") } },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        runOnUiThread { state.expand() }
        waitForIdle()
    }

    private companion object {
        const val Window = 640
        const val StatusBar = 40
        const val GestureBar = 30
        const val Keyboard = 260
    }
}
