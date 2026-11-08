package io.kontour.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A sheet's handle moves it between its detents from the keyboard.
 *
 * bottom-sheet.md says the detents are reachable without the gesture, and they
 * were by a screen reader's actions alone: the handle took no focus, so a
 * keyboard had no way to them. Found in the November 2026 audit.
 */
@OptIn(ExperimentalTestApi::class)
class SheetHandleKeysTest {

    private lateinit var state: SheetState
    private var labels: List<String> = emptyList()

    private fun ComposeUiTest.sheet(detents: List<SheetDetent>) {
        setContent {
            KontourTheme(reduceMotion = true) {
                labels = listOf(Theme.strings.expandSheet, Theme.strings.collapseSheet)
                OverlayHost(Modifier.fillMaxSize()) {
                    state = rememberSheetState(detents = detents, initialDetent = SheetDetent.Hidden)
                    ModalBottomSheet(visible = true, onDismissRequest = {}, state = state) {
                        // Taller than the window, so `Half` and `Expanded` are two heights.
                        Box(Modifier.fillMaxWidth().height(1200.dp))
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.handle() =
        onNode(hasContentDescription(labels[0]) or hasContentDescription(labels[1]), useUnmergedTree = true)

    private fun ComposeUiTest.press(key: Key): SheetDetent {
        handle().performKeyInput { pressKey(key) }
        waitForIdle()
        return state.currentDetent
    }

    @Test
    fun theArrowsStepTheDetentAndHomeAndEndGoToTheEnds() = runComposeUiTest {
        sheet(listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded))
        handle().requestFocus()
        // A modal sheet opens at its tallest.
        assertEquals(SheetDetent.Expanded, state.currentDetent)
        assertEquals(SheetDetent.Expanded, press(Key.DirectionUp), "Up at the tallest went somewhere")
        assertEquals(SheetDetent.Half, press(Key.DirectionDown))
        assertEquals(SheetDetent.Half, press(Key.PageDown), "Down at the shortest hid the sheet")
        assertEquals(SheetDetent.Expanded, press(Key.PageUp))
        assertEquals(SheetDetent.Expanded, press(Key.MoveHome), "Home at the tallest went somewhere")
        assertEquals(SheetDetent.Half, press(Key.MoveEnd))
        assertEquals(SheetDetent.Expanded, press(Key.MoveHome))
    }

    /** A sheet with one size to open to has nothing for the keys to do, and is not a Tab stop. */
    @Test
    fun aSheetWithOneSizeIsNotATabStop() = runComposeUiTest {
        sheet(listOf(SheetDetent.Hidden, SheetDetent.Expanded))
        assertFalse(
            SemanticsProperties.Focused in handle().fetchSemanticsNode().config,
            "a handle with no detent to step to takes the focus",
        )
    }
}
