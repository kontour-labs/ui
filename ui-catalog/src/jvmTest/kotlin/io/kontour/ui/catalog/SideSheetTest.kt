package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalSideSheet
import io.kontour.ui.sheet.SideSheet
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The non-modal side sheet shares the screen: no scrim, nothing blocked.
 *
 * `BottomSheet` has always had `ModalBottomSheet` beside it, and the side sheet had
 * only the modal half — a filter rail or an inspector that should sit beside a
 * page the reader goes on using had to be a modal sheet with `ScrimStyle.None`,
 * still living in the overlay host. `SideSheet` is now that rail, in the caller's
 * own layout, and `ModalSideSheet` is what `SideSheet` used to be.
 *
 * Every arm that is about the difference runs against the modal sheet too, as the
 * control: the same click, the same pixel, and the opposite answer.
 */
class SideSheetTest {

    @Test
    fun thePageBehindItStaysUsable() {
        assertEquals(1, clicksThrough(modal = false), "a click on the page beside a non-modal side sheet was not delivered")
        assertEquals(0, clicksThrough(modal = true), "a click on the page beside a modal side sheet was delivered — the control is broken")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun clicksThrough(modal: Boolean): Int {
        var clicks = 0
        var button = Rect.Zero
        runDesktopComposeUiTest(width = 1400, height = 900) {
            setContent {
                KontourTheme(reduceMotion = true) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(40.dp)
                                    .size(120.dp, 48.dp)
                                    .onGloballyPositioned { button = it.boundsInRoot() }
                                    .clickable { clicks++ }
                            )
                            Sheet(modal = modal, visible = true)
                        }
                    }
                }
            }
            waitForIdle()
            onRoot().performTouchInput { click(button.center) }
            waitForIdle()
        }
        return clicks
    }

    @Test
    fun itDrawsNoScrim() {
        val plain = scene(modal = false)
        val modal = scene(modal = true)
        assertEquals(GroundRgb, plain.rgb(100, 450), "the page beside a non-modal side sheet is not its own colour")
        assertTrue(modal.rgb(100, 450) != GroundRgb, "the page beside a modal side sheet is not dimmed — the control is broken")
    }

    @Test
    fun itSlidesInAndOut() {
        var visible by mutableStateOf(true)
        var composed = false
        var shown = -1
        var gone = -1
        Scene(width = 1400, height = 900) {
            KontourTheme(reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Ground)) {
                    SideSheet(visible = visible, width = 300.dp, containerColour = SheetColour) {
                        DisposableEffect(Unit) {
                            composed = true
                            onDispose { composed = false }
                        }
                    }
                }
            }
        }.use { scene ->
            shown = scene.frames(60).sheetColumns()
            visible = false
            gone = scene.frames(60).sheetColumns()
        }
        assertTrue(shown > 500, "a visible SideSheet drew $shown columns of itself")
        assertEquals(0, gone, "a SideSheet set invisible is still on screen")
        assertFalse(composed, "a SideSheet that has slid away is still composed")
    }

    @Composable
    private fun Sheet(modal: Boolean, visible: Boolean) {
        if (modal) {
            ModalSideSheet(visible = visible, onDismissRequest = {}, width = 300.dp, containerColour = SheetColour) {}
        } else {
            SideSheet(visible = visible, width = 300.dp, containerColour = SheetColour) {}
        }
    }

    private fun scene(modal: Boolean): BufferedImage = Scene(width = 1400, height = 900) {
        KontourTheme(reduceMotion = true) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Ground)) {
                    Sheet(modal = modal, visible = true)
                }
            }
        }
    }.use { scene -> scene.frames(60) }

    private fun BufferedImage.rgb(x: Int, y: Int): Int = getRGB(x, y) and 0xFFFFFF

    /** How many columns of the sheet's own colour cross the window a quarter of the way down. */
    private fun BufferedImage.sheetColumns(): Int = (0 until width).count { rgb(it, height / 4) == SheetRgb }

    private companion object {
        val Ground = Color(0xFF3355AA)
        const val GroundRgb = 0x3355AA
        val SheetColour = Color(0xFF11CC55)
        const val SheetRgb = 0x11CC55
    }
}
