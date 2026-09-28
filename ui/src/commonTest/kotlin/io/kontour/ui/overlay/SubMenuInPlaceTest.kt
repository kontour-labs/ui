package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * On touch a submenu opens in place — over its own row, in the same overlay, with
 * the row kept as the way back — as an iOS menu nests. Asked for from a phone, with
 * screenshots of the iOS menus. With a mouse or the keyboard it still cascades.
 */
@OptIn(ExperimentalTestApi::class)
class SubMenuInPlaceTest {

    @Test
    fun aTappedSubmenuOpensOverItsRowInTheSameOverlay() = runComposeUiTest {
        val menu = open()
        val row = onNodeWithText("Group by").fetchSemanticsNode().boundsInRoot
        onNodeWithText("Group by").performTouchInput { click() }
        waitForIdle()
        assertEquals(1, menu.host.visible.size, "a submenu on touch is drawn in the menu, not as a second overlay")
        onNodeWithText("Route").assertExists()
        val header = onNodeWithText("Group by").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(header.top - row.top) < 2f, "the submenu's own row should be where the row was: ${header.top} against ${row.top}")
        onNodeWithText("Sort by").assertDoesNotExist()
    }

    @Test
    fun itsRowTakesItBack() = runComposeUiTest {
        open()
        onNodeWithText("Group by").performTouchInput { click() }
        waitForIdle()
        onNodeWithText("Group by").performTouchInput { click() }
        waitForIdle()
        onNodeWithText("Route").assertDoesNotExist()
        onNodeWithText("Sort by").assertExists()
    }

    @Test
    fun anItemInItRunsAndClosesTheWholeMenu() = runComposeUiTest {
        val menu = open()
        onNodeWithText("Group by").performTouchInput { click() }
        waitForIdle()
        onNodeWithText("Route").performTouchInput { click() }
        waitForIdle()
        assertEquals("Route", menu.picked)
        assertTrue(menu.dismissed, "picking from a submenu closes the menu it is in")
    }

    @Test
    fun escapeGoesBackALevelAndThenCloses() = runComposeUiTest {
        val menu = open()
        onNodeWithText("Group by").performTouchInput { click() }
        waitForIdle()
        onNodeWithText("Route").requestFocus()
        onNodeWithText("Route").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithText("Route").assertDoesNotExist()
        assertTrue(!menu.dismissed, "the first Escape goes back a level")
        onNodeWithText("Sort by").requestFocus()
        onNodeWithText("Sort by").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertTrue(menu.dismissed, "the second closes the menu")
    }

    @Test
    fun levelsNest() = runComposeUiTest {
        open()
        onNodeWithText("Group by").performTouchInput { click() }
        waitForIdle()
        onNodeWithText("Line").performTouchInput { click() }
        waitForIdle()
        onNodeWithText("Train").assertExists()
        onNodeWithText("Route").assertDoesNotExist()
        onNodeWithText("Line").performTouchInput { click() }
        waitForIdle()
        onNodeWithText("Route").assertExists()
    }

    @Test
    fun withAPointerAndTheKeyboardItStillCascades() = runComposeUiTest {
        val menu = open()
        // A pointer on the page makes it a pointer's menu.
        onRoot().performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithText("Group by").requestFocus()
        onNodeWithText("Group by").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(2, menu.host.visible.size, "Right should open the submenu beside its row")
        onNodeWithText("Route").requestFocus()
        onNodeWithText("Route").performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertEquals(1, menu.host.visible.size, "Left should close it")
        onNodeWithText("Group by").assertIsFocused()
    }

    private class Menu(val host: OverlayHostState) {
        var picked: String? = null
        var dismissed = false
    }

    private fun ComposeUiTest.open(): Menu {
        lateinit var menu: Menu
        setContent {
            val host = rememberOverlayHostState()
            menu = remember { Menu(host) }
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize(), state = host) {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        DropdownMenu(visible = !menu.dismissed, onDismissRequest = { menu.dismissed = true }) {
                            item("Sort by") {}
                            submenu("Group by") {
                                item("Route") { menu.picked = "Route" }
                                item("Stop") {}
                                submenu("Line") {
                                    item("Train") {}
                                    item("Bus") {}
                                }
                            }
                            item("Share") {}
                            item("Copy") {}
                            item("Report") {}
                            item("Hide") {}
                        }
                    }
                }
            }
        }
        waitForIdle()
        return menu
    }
}
