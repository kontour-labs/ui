package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A menu walked from the keyboard: the arrows between the rows that can be
 * chosen, round from one end to the other, Home and End to the ends, and typing
 * to the row whose words begin with what was typed.
 *
 * On the desktop runtime only Tab moved between a menu's rows before. The arrows,
 * Home, End and typing did nothing at all, though the documentation said they
 * did.
 */
@OptIn(ExperimentalTestApi::class)
class MenuKeyboardTest {

    private var open by mutableStateOf(true)

    /** Down goes to the next row that can be chosen, past a disabled one, a heading and a rule. */
    @Test
    fun downMovesToTheNextRowAndSkipsWhatCannotBeChosen() = runComposeUiTest {
        show {
            item("Copy") {}
            item("Cut", enabled = false) {}
            divider()
            section("Share")
            item("Email") {}
            item("Message") {}
        }
        focused().assertTextIs("Copy")
        press(Key.DirectionDown)
        focused().assertTextIs("Email")
        press(Key.DirectionDown)
        focused().assertTextIs("Message")
    }

    /**
     * Past the last row, Down goes round to the first, and Up the other way: as
     * a menu on the Mac does, and as WAI-ARIA's menu does.
     */
    @Test
    fun theArrowsGoRoundFromOneEndToTheOther() = runComposeUiTest {
        show {
            item("Copy") {}
            item("Paste") {}
            item("Delete") {}
        }
        press(Key.DirectionUp)
        focused().assertTextIs("Delete")
        press(Key.DirectionDown)
        focused().assertTextIs("Copy")
    }

    @Test
    fun homeAndEndGoToTheFirstAndLastRowsThatCanBeChosen() = runComposeUiTest {
        show {
            item("Undo", enabled = false) {}
            item("Copy") {}
            item("Paste") {}
            item("Delete") {}
            item("Report", enabled = false) {}
        }
        press(Key.MoveEnd)
        focused().assertTextIs("Delete")
        press(Key.MoveHome)
        focused().assertTextIs("Copy")
    }

    /** Typing goes to the row that begins with it; the same letter again goes on to the next. */
    @Test
    fun typingGoesToTheRowThatBeginsWithIt() = runComposeUiTest {
        show {
            item("Copy") {}
            item("Cut") {}
            item("Paste") {}
            item("Paste and match style") {}
            item("Delete") {}
        }
        press(Key.D)
        focused().assertTextIs("Delete")
        pause()
        press(Key.C)
        focused().assertTextIs("Copy")
        press(Key.C)
        focused().assertTextIs("Cut")
        // Letters typed together are one word: "pa", not a "p" and then an "a".
        pause()
        type(listOf(Key.P, Key.A, Key.S, Key.T, Key.E, Key.Spacebar, Key.A))
        focused().assertTextIs("Paste and match style")
        // A space with no word under way is the row's, and picks it.
        pause()
        press(Key.Spacebar)
        assertTrue(!open, "Space should pick the row the keyboard is on")
    }

    /**
     * Escape in a submenu that cascades closes it and puts the focus back on its
     * row, as Left does, so the keyboard is still in the menu.
     */
    @Test
    fun escapeInASubmenuGoesBackToItsRow() = runComposeUiTest {
        show {
            item("Sort by") {}
            submenu("Group by") {
                item("Route") {}
                item("Stop") {}
            }
        }
        press(Key.DirectionDown)
        focused().assertTextIs("Group by")
        press(Key.DirectionRight)
        focused().assertTextIs("Route")
        press(Key.Escape)
        onNodeWithText("Route").assertDoesNotExist()
        assertTrue(open, "Escape in a submenu should close the submenu alone")
        onNodeWithText("Group by").assertIsFocused()
    }

    /** Right to left, Left opens a submenu and Right comes back out of it. */
    @Test
    fun rightToLeftTheArrowsIntoASubmenuAreMirrored() = runComposeUiTest {
        show(rtl = true) {
            submenu("Group by") {
                item("Route") {}
            }
            item("Sort by") {}
        }
        focused().assertTextIs("Group by")
        press(Key.DirectionLeft)
        focused().assertTextIs("Route")
        press(Key.DirectionRight)
        onNodeWithText("Route").assertDoesNotExist()
        onNodeWithText("Group by").assertIsFocused()
    }

    private fun ComposeUiTest.show(rtl: Boolean = false, content: @androidx.compose.runtime.Composable MenuScope.() -> Unit) {
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(
                    LocalInputModality provides InputModality.Keyboard,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.padding(24.dp)) {
                            DropdownMenu(visible = open, onDismissRequest = { open = false }, content = content)
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.focused() = onNode(isFocused())

    /** Longer than type-ahead waits for the next letter of a word. */
    private fun ComposeUiTest.pause() {
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
    }

    private fun ComposeUiTest.press(key: Key) = type(listOf(key))

    private fun ComposeUiTest.type(keys: List<Key>) {
        onRoot().performKeyInput { keys.forEach { pressKey(it) } }
        waitForIdle()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertTextIs(text: String) {
        assertEquals(text, fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text })
    }
}
