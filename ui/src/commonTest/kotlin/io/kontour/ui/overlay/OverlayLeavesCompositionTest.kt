package io.kontour.ui.overlay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An overlay that leaves composition while it is showing goes with it.
 *
 * Reported from an app: *"if a Dialog is removed from composition while it's
 * showing, rather than set to visible = false, it stays on screen. It bit me
 * twice."* The entry lives in the host, and only `visible = false` took it out;
 * a screen navigated away from, or an `if` around the dialog turning false, left
 * it there with nothing to close it. Popovers, menus, tooltips and sheets
 * already took theirs down on dispose; the dialog, the command palette and the
 * toast host did not.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayLeavesCompositionTest {

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun assertGoesWithItsCaller(text: String, overlay: @Composable () -> Unit) = runComposeUiTest {
        var present by mutableStateOf(true)
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    if (present) overlay()
                }
            }
        }
        waitForIdle()
        assertEquals(1, count(text), "the overlay should be showing to begin with")
        present = false
        waitForIdle()
        assertEquals(0, count(text), "the overlay should leave with the composable that showed it")
    }

    @Test
    fun aDialogGoesWithItsCaller() = assertGoesWithItsCaller("Rename favourite") {
        Dialog(visible = true, onDismissRequest = {}) { Text("Rename favourite") }
    }

    @Test
    fun anAlertDialogGoesWithItsCaller() = assertGoesWithItsCaller("Remove this stop?") {
        AlertDialog(visible = true, onDismissRequest = {}, confirmLabel = "Remove", onConfirm = {}) { +"Remove this stop?" }
    }

    @Test
    fun aCommandPaletteGoesWithItsCaller() = assertGoesWithItsCaller("Open settings") {
        CommandPalette(visible = true, onDismissRequest = {}, commands = listOf(Command("settings", "Open settings", onAction = {})))
    }

    @Test
    fun aToastHostGoesWithItsCaller() = runComposeUiTest {
        var present by mutableStateOf(true)
        val toasts = ToastHostState()
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    if (present) ToastHost(toasts)
                }
            }
        }
        // A long one, so it is still up when the host goes.
        runOnIdle { toasts.show("Saved to favourites", duration = kotlin.time.Duration.parse("1h")) }
        waitForIdle()
        assertEquals(1, count("Saved to favourites"), "the toast should be showing to begin with")
        present = false
        waitForIdle()
        assertEquals(0, count("Saved to favourites"), "the toast should leave with the host that showed it")
    }

    /** A dialog whose key changes while it is up shows once, under the new key. */
    @Test
    fun aDialogRekeyedWhileUpShowsOnce() = runComposeUiTest {
        var key by mutableStateOf<Any>("first")
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Dialog(visible = true, onDismissRequest = {}, key = key) { Text("Rename favourite") }
                }
            }
        }
        waitForIdle()
        key = "second"
        waitForIdle()
        assertEquals(1, count("Rename favourite"), "the entry under the old key should have gone")
    }
}
