package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A sheet's drag handle bows, a little, toward where the sheet is going while
 * it moves, and is straight again once it stops — and not at all under reduced
 * motion.
 */
class DragHandleBendTest {

    private fun scene(reduceMotion: Boolean) = Scene(width = 600, height = 900) {
        KontourTheme(reduceMotion = reduceMotion) {
            val state = rememberSheetState(detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded), initialDetent = SheetDetent.Half)
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Ground))
                BottomSheet(state = state, containerColour = SheetColour, windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) {
                    Box(Modifier.fillMaxWidth().height(1200.dp))
                }
            }
        }
    }

    private fun BufferedImage.isSheet(x: Int, y: Int) = (getRGB(x, y) and 0xFFFFFF) == SheetRgb

    private fun BufferedImage.topOfTheSheet(): Int = (0 until height).first { isSheet(width / 2, it) }

    /** The first row of the handle's ink in column [x], under the sheet's top. */
    private fun BufferedImage.handleTop(x: Int): Int {
        val top = topOfTheSheet()
        return (top until top + 80).first { !isSheet(x, it) }
    }

    /** How far the handle's middle is above its ends, in px: positive bowed up. */
    private fun BufferedImage.bow(): Int {
        val middle = width / 2
        return (handleTop(middle - EndReach) + handleTop(middle + EndReach)) / 2 - handleTop(middle)
    }

    @Test
    fun theHandleBowsTowardWhereTheSheetGoesAndStraightensWhenItStops() {
        val (during, after) = scene(reduceMotion = false).use { scene ->
            val settled = scene.frames(60)
            val handle = Offset(300f, settled.handleTop(300) + 2f)
            assertEquals(0, settled.bow(), "at rest the handle is not straight")
            scene.drag(from = handle, to = handle + Offset(0f, -300f), steps = 12, release = false)
            val moving = scene.frame()
            val bowUp = moving.bow()
            scene.release(handle + Offset(0f, -300f))
            bowUp to scene.frames(90).bow()
        }
        assertTrue(during >= 1, "dragged up, the handle's middle is ${during}px above its ends: not bowed up")
        assertTrue(during <= 6, "dragged up, the handle bowed ${during}px: floppy, not subtle")
        assertEquals(0, after, "come to rest, the handle stayed bowed")
    }

    @Test
    fun underReducedMotionTheHandleStaysStraight() {
        val bow = scene(reduceMotion = true).use { scene ->
            val settled = scene.frames(60)
            val handle = Offset(300f, settled.handleTop(300) + 2f)
            scene.drag(from = handle, to = handle + Offset(0f, -300f), steps = 12, release = false)
            scene.frame().bow()
        }
        assertEquals(0, bow, "under reduced motion, the handle bowed")
    }

    private companion object {
        val Ground = Color(0xFF3355AA)
        val SheetColour = Color(0xFF11CC55)
        const val SheetRgb = 0x11CC55

        /** Near the ends of the bar, which is 36dp, at the scene's density of 2: 30px either side of the middle. */
        const val EndReach = 30
    }
}
