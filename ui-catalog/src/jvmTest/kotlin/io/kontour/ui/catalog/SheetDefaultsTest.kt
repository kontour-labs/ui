package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.ModalNavDrawer
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.ModalSideSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SideSheet
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every sheet floats unless it is asked not to.
 *
 * Each of these is built with no `presentation` at all, and each should come out
 * `sheetFloatingInset` — 12dp, 24px at this density — clear of the window's edge it
 * belongs to. `Edge` is still there for a caller who wants a drawer out of the
 * window rather than a panel over it; it is no longer what you get without saying.
 *
 * Measured in a colour only the sheet paints, for the reason every sheet test here
 * gives: a modal dims the page and recedes it, so "not the sheet" is the only
 * honest reading of the space around one.
 */
class SheetDefaultsTest {

    @Test
    fun aModalBottomSheetFloats() {
        val image = scene(600, 900) {
            ModalBottomSheet(visible = true, onDismissRequest = {}, containerColour = SheetColour) {
                Text("Rename favourite")
            }
        }
        assertEquals(Margin, image.gapUnder(), "a ModalBottomSheet with no presentation is not floating")
    }

    @Test
    fun aBottomSheetFloats() {
        val image = scene(600, 900) {
            val state = rememberSheetState(
                detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
                initialDetent = SheetDetent.Half,
            )
            BottomSheet(state = state, containerColour = SheetColour) {
                Box(Modifier.fillMaxWidth().height(1200.dp))
            }
        }
        assertEquals(Margin, image.gapUnder(), "a BottomSheet with no presentation is not floating")
        assertEquals(Margin, image.gapLeft(image.height - 100), "a BottomSheet with no presentation is flush to the side")
    }

    @Test
    fun aSideSheetFloats() {
        val image = scene(1400, 900) {
            SideSheet(visible = true, width = 300.dp, containerColour = SheetColour) {}
        }
        assertEquals(Margin, image.gapRight(), "a SideSheet with no presentation is not floating")
    }

    @Test
    fun aModalSideSheetFloats() {
        val image = scene(1400, 900) {
            ModalSideSheet(visible = true, onDismissRequest = {}, width = 300.dp, containerColour = SheetColour) {}
        }
        assertEquals(Margin, image.gapRight(), "a ModalSideSheet with no presentation is not floating")
    }

    /**
     * The drawer is built on the modal side sheet and takes its default: the
     * navigation floats too, from the leading edge. It has no colour parameter, so
     * its own surface is sampled well inside it, and the gap is the columns before
     * that colour starts — the page there is dimmed and receded, and neither is it.
     */
    @Test
    fun aModalNavDrawerFloats() {
        val image = scene(1400, 900) {
            ModalNavDrawer(visible = true, onDismissRequest = {}) {}
        }
        val y = image.height * 3 / 4
        val drawer = image.getRGB(200, y) and 0xFFFFFF
        var gap = image.width
        for (x in 0 until image.width) {
            if ((image.getRGB(x, y) and 0xFFFFFF) == drawer) { gap = x; break }
        }
        assertEquals(Margin, gap, "a ModalNavDrawer is not floating off its leading edge")
    }

    private fun scene(width: Int, height: Int, content: @Composable () -> Unit): BufferedImage =
        Scene(width = width, height = height) {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Ground))
                    content()
                }
            }
        }.use { scene -> scene.frames(60) }

    private fun BufferedImage.isSheet(x: Int, y: Int): Boolean = (getRGB(x, y) and 0xFFFFFF) == SheetRgb

    /** Rows between the sheet's lowest ink and the bottom of the window, down the middle. */
    private fun BufferedImage.gapUnder(): Int {
        for (y in height - 1 downTo 0) if (isSheet(width / 2, y)) return height - 1 - y
        return height
    }

    private fun BufferedImage.gapLeft(y: Int): Int {
        for (x in 0 until width) if (isSheet(x, y)) return x
        return width
    }

    /** Columns between the window's right edge and the sheet, a quarter of the way down. */
    private fun BufferedImage.gapRight(): Int {
        for (x in width - 1 downTo 0) if (isSheet(x, height / 4)) return width - 1 - x
        return width
    }

    private companion object {
        val Ground = Color(0xFF3355AA)
        val SheetColour = Color(0xFF11CC55)
        const val SheetRgb = 0x11CC55

        /** `componentDefaults.sheetFloatingInset`, 12dp, at the scene's density of 2. */
        const val Margin = 24
    }
}
