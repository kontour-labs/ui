package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.Popover
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.ModalSideSheet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A wheel turned over a dimmed overlay's scrim leaves the overlay open. A tap
 * there still closes it.
 *
 * Reported for 0.5.6: *"if an overlay is open, then scrolling (with a
 * mouse/trackpad) over the overlay's backdrop will close the overlay. that
 * shouldn't happen. it should, however, still react to outside clicks and
 * touches"*. Every entry's `dismissOnScroll` followed `dismissOnOutside`, which
 * is right for a menu, whose page would otherwise freeze under it, and wrong
 * for anything that dims: the page is not the reader's while a dialog is up,
 * so a scroll there was never a request to go back to it. And a sheet or a
 * dialog's live `dismissible` answered for a scroll as well as a tap, so it
 * would have closed them whatever the flag said.
 *
 * Each case scrolls a few notches over the scrim's corner, then taps it, so a
 * pass is a scrim that was listening rather than one that never was. The menu
 * is the control: a light overlay still gets out of the page's way.
 */
class ScrimScrollTest {

    // The top-left corner, which every one of these leaves to its scrim.
    private val corner = Offset(20f, 20f)

    private fun scrolledThenTapped(
        what: String,
        overlay: @Composable (open: Boolean, onDismissRequest: () -> Unit) -> Unit,
    ) {
        var open = true
        var dismissals = 0
        // Not state: each case is shown once and the count is what is read.
        Scene(width = 800, height = 600) {
            OverlayHost {
                Box(Modifier.fillMaxSize()) { Text("The page") }
                overlay(open) { dismissals++; open = false }
            }
        }.use { scene ->
            scene.frames(40)
            repeat(3) {
                scene.scroll(corner, Offset(0f, 1f))
                scene.frames(4)
            }
            scene.frames(20)
            assertEquals(0, dismissals, "$what: a scroll over its scrim closed it")

            scene.tap(corner)
            scene.frames(20)
            assertEquals(1, dismissals, "$what: a tap on its scrim did not close it, so the scroll proved nothing")
        }
    }

    @Test
    fun aDialogStaysOpen() = scrolledThenTapped("Dialog") { open, dismiss ->
        Dialog(visible = open, onDismissRequest = dismiss) { Text("Leave without saving?") }
    }

    @Test
    fun aModalBottomSheetStaysOpen() = scrolledThenTapped("ModalBottomSheet") { open, dismiss ->
        ModalBottomSheet(visible = open, onDismissRequest = dismiss) { Text("Route 950") }
    }

    @Test
    fun aModalSideSheetStaysOpen() = scrolledThenTapped("ModalSideSheet") { open, dismiss ->
        // Narrower than the 400dp window, or there is no scrim beside it to tap.
        ModalSideSheet(visible = open, onDismissRequest = dismiss, width = 240.dp) { Text("Filters") }
    }

    @Test
    fun aDimmedPopoverStaysOpen() = scrolledThenTapped("a dimmed Popover") { open, dismiss ->
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.Center).size(40.dp)) {
                Popover(visible = open, onDismissRequest = dismiss, scrim = ScrimStyle.Dimmed) { Text("Rename stop") }
            }
        }
    }

    /** The control: a menu is light, and the page has to be able to scroll from under it. */
    @Test
    fun aMenuStillClosesOnAScroll() {
        var dismissals = 0
        Scene(width = 800, height = 600) {
            OverlayHost {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.Center).size(40.dp)) {
                        DropdownMenu(visible = dismissals == 0, onDismissRequest = { dismissals++ }) {
                            item("Share") {}
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(40)
            scene.scroll(corner, Offset(0f, 1f))
            scene.frames(20)
            assertEquals(1, dismissals, "a scroll past a menu no longer closes it, so the page under it is frozen")
        }
    }
}
