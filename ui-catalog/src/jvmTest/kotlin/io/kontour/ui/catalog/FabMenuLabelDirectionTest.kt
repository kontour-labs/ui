package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Plus
import io.kontour.icons.tabler.outline.Star
import io.kontour.ui.components.action.FabMenu
import io.kontour.ui.components.action.FabMenuDefaults
import io.kontour.ui.components.action.FabMenuLayout
import io.kontour.ui.overlay.OverlayHost
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A horizontal FAB menu's turned labels read up and away from their buttons the
 * way the text runs: up and to the right left to right, and up and to the left
 * right to left. They always turned up and to the right, so an Arabic or Hebrew
 * label read downhill into its button, and near the left wall the clamp, worked
 * out for the other turn, let it run off the screen. Found in the November 2026
 * audit.
 *
 * A chip turned about its bottom corner has that corner as its lowest point, and
 * its far top corner as its highest, `(w − h) / √2` along. So the top of the ink
 * is to the right of the bottom of it when the chip turns up and to the right, and
 * to the left when it turns up and to the left.
 */
class FabMenuLabelDirectionTest {

    /** The rows above the button row, where only the labels are. */
    private var band = 0

    @Test
    fun leftToRightTheLabelTurnsUpAndToTheRight() {
        val frame = render(LayoutDirection.Ltr)
        assertTrue(frame.topInk() > frame.bottomInk(), "the top of the label is not to the right of its foot")
    }

    @Test
    fun rightToLeftTheLabelTurnsUpAndToTheLeft() {
        val frame = render(LayoutDirection.Rtl)
        assertTrue(frame.topInk() < frame.bottomInk(), "the top of the label is not to the left of its foot")
    }

    /** By the left wall, right to left, the label stays its margin inside the window. */
    @Test
    fun rightToLeftByTheWallTheLabelStaysOnScreen() {
        val frame = render(LayoutDirection.Rtl)
        val margin = FabMenuDefaults.ScreenMargin.value * Density
        val leftmost = (0 until band).minOf { y -> (0 until frame.width).firstOrNull { frame.chip(it, y) } ?: Int.MAX_VALUE }
        assertTrue(leftmost >= margin - 2, "the label reaches ${leftmost}px from the left wall, inside its ${margin}px margin")
    }

    /** The mean x of the chip's ink in the first and last rows that have any. */
    private fun BufferedImage.topInk(): Double = inkIn(rows().first())
    private fun BufferedImage.bottomInk(): Double = inkIn(rows().last())

    private fun BufferedImage.rows(): List<Int> = (0 until band).filter { y -> (0 until width).any { chip(it, y) } }

    private fun BufferedImage.inkIn(y: Int): Double = (0 until width).filter { chip(it, y) }.average()

    private fun BufferedImage.chip(x: Int, y: Int): Boolean = (getRGB(x, y) shr 8 and 0xFF) > SurfaceChannel

    /** One item, in the bottom corner on the end side: by the left wall right to left. */
    private fun render(direction: LayoutDirection): BufferedImage {
        val expanded = mutableStateOf(true)
        var anchor = Rect.Zero
        var frame: BufferedImage? = null
        Scene(width = Width, height = Height, density = Density.toFloat()) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Page), Alignment.BottomEnd) {
                        FabMenu(
                            expanded = expanded.value,
                            onExpandedChange = { expanded.value = it },
                            icon = Tabler.Outline.Plus,
                            contentDescription = "Add",
                            layout = FabMenuLayout.Horizontal,
                            modifier = Modifier.reportBounds { anchor = it },
                        ) {
                            item(Tabler.Outline.Star, "Somewhere nearby", onClick = {})
                        }
                    }
                }
            }
        }.use { scene ->
            frame = scene.frames(80)
        }
        band = (anchor.center.y - ItemClearance * Density).toInt()
        return requireNotNull(frame)
    }

    private companion object {
        const val Density = 2
        const val Width = 800
        const val Height = 420

        /** Past the buttons' circles and below the labels, in dp above the anchor's centre: see `FabMenuLabelTest`. */
        const val ItemClearance = 32

        val Page = Color(0xFF101820)
        const val SurfaceChannel = 0x90
    }
}
