package io.kontour.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.PhaseCounts
import io.kontour.ui.countPhases
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The page under an opening overlay is not drawn again for every frame of the
 * overlay's arrival.
 *
 * Each overlay with a scrim draws it from a fraction read in the draw pass, so
 * the scrim is redrawn on every frame the overlay moves — which is right, and
 * costs a rectangle. What it cost instead was the page: a node with no layer of
 * its own is recorded into its nearest ancestor's, and the scrim's nearest was
 * the one the whole page is recorded into. Only a backdrop that recedes or
 * blurs gave the page a layer apart from it, so a menu, a popover, a sheet that
 * does not recede, and a dialog wherever the backdrop cannot blur — Android 10
 * and 11, or a theme with `backdropBlur = false` — recorded every row of the
 * page again on every frame they opened and closed. Counted on the page's own
 * node: 12 of a menu's first 13 frames, 20 of a sheet's. With the scrim in a
 * layer of its own, once, on the frame the overlay mounts and the host's
 * children change.
 *
 * Counted rather than timed, as `SheetFramePressureTest` is, and for its
 * reason: a draw re-recorded is the same count here as on a phone, where it is
 * far dearer.
 */
@OptIn(ExperimentalTestApi::class)
class ScrimLayerPressureTest {

    @Test
    fun aMenuOpeningDoesNotDrawThePageEachFrame() = assertPageDrawnOnce("a menu") { visible, close, body ->
        Box(Modifier.size(80.dp)) {
            DropdownMenu(visible = visible, onDismissRequest = close) { Box(Modifier.size(120.dp).countPhases(body)) }
        }
    }

    @Test
    fun aPopoverOpeningDoesNotDrawThePageEachFrame() = assertPageDrawnOnce("a popover") { visible, close, body ->
        Box(Modifier.size(80.dp)) {
            Popover(visible = visible, onDismissRequest = close) { Box(Modifier.size(120.dp).countPhases(body)) }
        }
    }

    @Test
    fun aDialogThatCannotBlurDoesNotDrawThePageEachFrame() =
        assertPageDrawnOnce("a dialog with no blur behind it", backdropBlur = false) { visible, close, body ->
            Dialog(visible = visible, onDismissRequest = close) { Box(Modifier.size(120.dp).countPhases(body)) }
        }

    @Test
    fun aSheetThatDoesNotRecedeThePageDoesNotDrawItEachFrame() =
        assertPageDrawnOnce("a modal sheet that leaves the page where it is") { visible, close, body ->
            ModalBottomSheet(visible = visible, onDismissRequest = close, recedes = false) {
                Box(Modifier.size(120.dp).countPhases(body))
            }
        }

    private fun assertPageDrawnOnce(
        what: String,
        backdropBlur: Boolean = true,
        overlay: @Composable (visible: Boolean, close: () -> Unit, body: PhaseCounts) -> Unit,
    ) {
        val page = PhaseCounts()
        val body = PhaseCounts()
        var opening = 0
        var closing = 0
        var bodyDrawn = 0
        runComposeUiTest {
            var visible by mutableStateOf(false)
            mainClock.autoAdvance = false
            setContent {
                KontourTheme(backdropBlur = backdropBlur) {
                    Box(Modifier.size(400.dp, 800.dp)) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize().background(Color.White).countPhases(page)) {
                                repeat(Rows) { Text("Stop $it, Fremantle line") }
                            }
                            overlay(visible, { visible = false }, body)
                        }
                    }
                }
            }
            repeat(4) { mainClock.advanceTimeByFrame() }

            page.reset()
            visible = true
            repeat(Frames) { mainClock.advanceTimeByFrame() }
            opening = page.draws
            bodyDrawn = body.draws

            page.reset()
            visible = false
            repeat(Frames) { mainClock.advanceTimeByFrame() }
            closing = page.draws
        }

        assertTrue(bodyDrawn > 0, "$what never drew its content, so this measured an overlay that did not open")
        assertTrue(
            opening <= Budget && closing <= Budget,
            "the page under $what was drawn $opening times while it opened and $closing while it closed, " +
                "over $Frames frames each: the scrim is being recorded into the page's layer, so every " +
                "frame of its fade records the whole page again",
        )
    }

    private companion object {
        const val Rows = 30

        /** Past the end of any overlay's arrival or departure. */
        const val Frames = 60

        /**
         * The frame the overlay mounts or goes, which changes what the host
         * holds, and one to spare. Before the scrim had a layer: 12 and 11 for a
         * menu, a popover and a dialog that cannot blur, 20 and 20 for a sheet.
         */
        const val Budget = 2
    }
}
