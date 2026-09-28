package io.kontour.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Once a modal has been dismissed, the page answers at once.
 *
 * Reported from a phone: closing a sheet, "or anything modal for that matter,
 * it takes just an extra split second for the page to become interactable again
 * after the backdrop disappears". A sheet slid itself out and only then told
 * the host it had gone, and the host then ran its own fade on an entry nobody
 * could see — its scrim still taking every tap — before removing it. A dialog's
 * scrim took taps for the whole of its fade.
 *
 * Measured by tapping the page itself, near the top, where no overlay's panel
 * is: a tap the scrim swallows never reaches it.
 */
@OptIn(ExperimentalTestApi::class)
class ModalLetGoTest {

    /**
     * Three frames in — the close is carried out by an effect, which lets go a
     * frame later — and with the sheet still sliding down, a tap is the page's.
     */
    @Test
    fun aClosingSheetLetsThePageBeTappedAtOnce() = runComposeUiTest {
        var open by mutableStateOf(true)
        lateinit var host: OverlayHostState
        val taps = page(onHost = { host = it }) { visible ->
            ModalBottomSheet(visible = visible && open, onDismissRequest = { open = false }) {
                Text("Stop 42", Modifier.height(200.dp))
            }
        }
        tapPage()
        assertEquals(0, taps(), "while the sheet is open, the page behind its scrim takes no taps")

        mainClock.autoAdvance = false
        open = false
        repeat(3) { mainClock.advanceTimeByFrame() }
        assertEquals(1, host.rendered.size, "the sheet should still be on its way down")
        tapPage()
        assertEquals(1, taps(), "a tap on the page just after the sheet was closed went to the scrim")
        mainClock.autoAdvance = true
    }

    @Test
    fun aClosingDialogLetsThePageBeTappedAtOnce() = runComposeUiTest {
        var open by mutableStateOf(true)
        val taps = page { visible ->
            Dialog(visible = visible && open, onDismissRequest = { open = false }) { Text("Leave now?") }
        }
        tapPage()
        assertEquals(0, taps(), "while the dialog is open, the page behind its scrim takes no taps")

        mainClock.autoAdvance = false
        open = false
        mainClock.advanceTimeByFrame()
        tapPage()
        assertEquals(1, taps(), "a tap on the page as the dialog faded went to its scrim")
        mainClock.autoAdvance = true
    }

    /** After a sheet has slid out, nothing of it is left in the host: no second fade. */
    @Test
    fun aSheetThatHasLandedIsGoneFromTheHost() = runComposeUiTest {
        var open by mutableStateOf(true)
        lateinit var host: OverlayHostState
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                host = rememberOverlayHostState()
                OverlayHost(Modifier.fillMaxSize(), state = host) {
                    ModalBottomSheet(visible = open, onDismissRequest = { open = false }) {
                        Text("Stop 42", Modifier.height(200.dp))
                    }
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        open = false
        var frames = 0
        while (host.rendered.isNotEmpty() && frames < 120) {
            mainClock.advanceTimeByFrame()
            frames++
        }
        mainClock.autoAdvance = true
        assertEquals(emptyList(), host.rendered.map { it.key }, "the sheet never left the host")
        assertEquals(false, host.isBusy)
    }

    private fun ComposeUiTest.tapPage() {
        onRoot().performTouchInput { click(Offset(width / 2f, 24f)) }
    }

    /** A page that counts its taps, under [overlay]; hands back the count. */
    private fun ComposeUiTest.page(
        onHost: (OverlayHostState) -> Unit = {},
        overlay: @Composable (Boolean) -> Unit,
    ): () -> Int {
        var taps = 0
        var shown by mutableStateOf(false)
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                val host = rememberOverlayHostState().also(onHost)
                OverlayHost(Modifier.fillMaxSize(), state = host) {
                    Box(Modifier.fillMaxSize().background(Color.White).clickable { taps++ })
                    overlay(shown)
                }
            }
        }
        waitForIdle()
        shown = true
        waitForIdle()
        return { taps }
    }
}
