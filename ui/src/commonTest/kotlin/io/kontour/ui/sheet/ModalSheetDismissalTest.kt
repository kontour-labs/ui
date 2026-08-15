package io.kontour.ui.sheet

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.OverlayHostState
import io.kontour.ui.overlay.rememberOverlayHostState
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `visible` belongs to the caller, and the sheet has to agree with it.
 *
 * Both cases here were live bugs, and the catalog's `onDismissRequest = {}` is
 * why neither was noticed: with nothing listening, a sheet that asks to be
 * dismissed and a sheet that never asks look exactly the same.
 */
@OptIn(ExperimentalTestApi::class)
class ModalSheetDismissalTest {

    @Test
    fun aSheetThatStartsVisibleStaysOpen() {
        runComposeUiTest {
            var visible by mutableStateOf(true)
            lateinit var host: OverlayHostState

            setContent {
                host = Harness(visible = visible, onDismissRequest = { visible = false })
            }
            waitForIdle()

            // `snapshotFlow` hands over the current detent before anything has
            // moved, and for a sheet that is `Hidden`. Reported as a dismissal it
            // told the caller to close a sheet the user had not seen yet, so a
            // sheet declared `visible = true` shut itself on the frame it
            // appeared and never opened at all.
            assertTrue(visible, "the sheet asked to be dismissed before it had opened")
            assertEquals(1, host.visible.size, "the sheet never made it into the host")
        }
    }

    @Test
    fun aDeclinedDismissalPutsTheSheetBack() {
        runComposeUiTest {
            // A caller that refuses — unsaved changes, a choice that has to be
            // made. Legitimate, and `visible` is what the sheet must obey.
            var swipeShut by mutableStateOf(false)
            lateinit var host: OverlayHostState
            lateinit var sheet: SheetState

            setContent {
                host = Harness(
                    visible = true,
                    onDismissRequest = { },
                    hide = swipeShut,
                    onState = { sheet = it },
                )
            }
            waitForIdle()
            assertEquals(SheetDetent.Expanded, sheet.currentDetent, "the sheet did not open")

            swipeShut = true
            waitForIdle()
            // The sheet gives the caller `SheetDismissalFrames` to respond before
            // concluding the dismissal was declined; a few more than that covers
            // the animation back up.
            repeat(SheetDismissalFrames + 8) { mainClock.advanceTimeByFrame() }
            waitForIdle()

            // Left alone, the sheet sits off the bottom of the window with its
            // scrim still up: the screen dimmed and blocked by a sheet nobody can
            // see. That is the half of the report that was not the dead callback.
            assertEquals(
                SheetDetent.Expanded,
                sheet.currentDetent,
                "the sheet stayed shut after the caller declined the dismissal, " +
                    "leaving a scrim over nothing",
            )
            assertEquals(1, host.visible.size, "the sheet left the host anyway")
        }
    }
}

/**
 * @param hide Drives the sheet shut from inside the composition, which is the
 *   nearest thing to a swipe that does not depend on gesture timings —
 *   `SheetState.hide()` is what the drag settles into either way.
 */
@Composable
private fun Harness(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    hide: Boolean = false,
    onState: (SheetState) -> Unit = {},
): OverlayHostState {
    val host = rememberOverlayHostState()
    KontourTheme(reduceMotion = true) {
        OverlayHost(Modifier.fillMaxSize(), state = host) {
            val sheet = rememberSheetState(
                detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                initialDetent = SheetDetent.Hidden,
            )
            onState(sheet)
            LaunchedEffect(hide) { if (hide) sheet.hide() }

            ModalBottomSheet(
                visible = visible,
                onDismissRequest = onDismissRequest,
                state = sheet,
            ) {
                Text("Rename favourite")
            }
        }
    }
    return host
}
