package io.kontour.ui.a11y

import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.input.InputModality
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import io.kontour.ui.components.action.Button
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The keyboard stays in an open modal, however the page under it is built.
 *
 * Found in the November 2026 audit. The page under a modal was kept from focus
 * by `canFocus = false` on its outermost box, and focus properties reach only
 * as far as the nearest focus target beneath them: a scrolling list is one, so
 * every control in a page that scrolls could still be reached, and Tab left a
 * dialog for the page behind it. overlays.md says the content beneath "cannot
 * take focus".
 */
@OptIn(ExperimentalTestApi::class)
class ModalKeepsFocusTest {

    private val open = mutableStateOf(true)

    private val sheetOpen = mutableStateOf(false)

    @Test
    fun tabNeverLeavesADialogForAScrollingPage() = runComposeUiTest {
        setContent { Stage() }
        waitForIdle()
        onNodeWithTag("Keep").requestFocus()
        waitForIdle()
        val reached = mutableListOf<String>()
        repeat(8) {
            onRoot().performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            reached += focusedTag()
        }
        repeat(8) {
            onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
            waitForIdle()
            reached += focusedTag()
        }
        assertTrue(reached.none { it.startsWith("Page") }, "Tab left the dialog for the page under it: $reached")
        assertTrue("Keep" in reached && "Discard" in reached, "Tab did not come round the dialog's own controls: $reached")
    }

    /** A dialog over a sheet: the sheet is behind the dialog as the page is. */
    @Test
    fun tabNeverLeavesADialogForTheSheetUnderIt() = runComposeUiTest {
        sheetOpen.value = true
        setContent { Stage() }
        waitForIdle()
        onNodeWithTag("Keep").requestFocus()
        waitForIdle()
        val reached = mutableListOf<String>()
        repeat(6) {
            onRoot().performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            reached += focusedTag()
            onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
            onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
            waitForIdle()
            reached += focusedTag()
        }
        assertTrue(reached.none { it.startsWith("Page") || it.startsWith("Sheet") }, "Tab left the dialog: $reached")
    }

    /**
     * Opened from the keyboard, a dialog takes the focus, at its first
     * control: it was cleared and left nowhere, so the next Tab began from the
     * top of the page under the dialog's scrim.
     */
    @Test
    fun aDialogOpenedFromTheKeyboardTakesTheFocus() = runComposeUiTest {
        open.value = false
        setContent { Stage() }
        waitForIdle()
        onNodeWithTag("Page 1").requestFocus()
        waitForIdle()
        open.value = true
        waitForIdle()
        assertTrue(focusedTag() == "Keep", "opened from the keyboard, the dialog's first control did not take the focus: ${focusedTag()}")
    }

    @Composable
    private fun Stage() {
        KontourTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalInputModality provides InputModality.Keyboard) {
            OverlayHost(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(3) { i -> Button(onClick = {}, modifier = Modifier.testTag("Page $i")) { +"Page $i" } }
                }
                ModalBottomSheet(visible = sheetOpen.value, onDismissRequest = { sheetOpen.value = false }) {
                    Column {
                        Button(onClick = {}, modifier = Modifier.testTag("Sheet 0")) { +"Sheet 0" }
                        Button(onClick = {}, modifier = Modifier.testTag("Sheet 1")) { +"Sheet 1" }
                    }
                }
                Dialog(visible = open.value, onDismissRequest = { open.value = false }) {
                    Column {
                        Button(onClick = {}, modifier = Modifier.testTag("Keep")) { +"Keep" }
                        Button(onClick = {}, modifier = Modifier.testTag("Discard")) { +"Discard" }
                    }
                }
            }
            }
        }
    }

    private fun ComposeUiTest.focusedTag(): String =
        onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
            .firstNotNullOfOrNull { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null } } ?: "nothing"
}
