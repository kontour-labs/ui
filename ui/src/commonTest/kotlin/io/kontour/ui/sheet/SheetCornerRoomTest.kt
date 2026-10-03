package io.kontour.ui.sheet

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.platform.DeviceCorners
import io.kontour.ui.theme.SquircleShape
import io.kontour.ui.theme.concentricWith
import io.kontour.ui.theme.topCornersOnly
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How much further in a sheet's parts sit when its top corners are rounder than
 * the theme's: what the rounder corner cuts into its diagonal, 1 − 1/√2 of the
 * difference, and nothing for a corner no rounder.
 */
class SheetCornerRoomTest {

    private val density = Density(2f)
    private val box = Size(1280f, 1280f)
    private val theme = SquircleShape(34.dp).topCornersOnly()

    private fun near(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs((expected - actual).value) < 0.01f, "$what: expected $expected, was $actual")

    @Test
    fun aCornerNoRounderThanTheThemesNeedsNoRoom() {
        assertEquals(0.dp, cornerRoom(28.dp, 34.dp), "a floating sheet's panel corner")
        assertEquals(0.dp, cornerRoom(34.dp, 34.dp), "the theme's own")
    }

    @Test
    fun aRounderCornerNeedsWhatItCutsIntoTheDiagonal() {
        near(4.98.dp, cornerRoom(51.dp, 34.dp), "a newer Pixel's")
        near(6.15.dp, cornerRoom(55.dp, 34.dp), "an iPhone's")
    }

    @Test
    fun anEdgeSheetOnAnIphoneTakesRoomAtBothCorners() {
        val edge = theme.concentricWith(DeviceCorners.uniform(55.dp))
        val room = sheetCornerRoom(listOf(edge), theme, box, density)
        near(6.15.dp, room.start, "start")
        near(6.15.dp, room.end, "end")
        near(6.15.dp, room.top, "above")
    }

    @Test
    fun eachSideFromItsOwnCornerInEitherDirection() {
        // Rounder at the top left than the top right.
        val corners = DeviceCorners(topLeft = 60.dp, topRight = 20.dp, bottomRight = 20.dp, bottomLeft = 60.dp)
        val ltr = sheetCornerRoom(listOf(theme.concentricWith(corners, direction = LayoutDirection.Ltr)), theme, box, density)
        near(cornerRoom(60.dp, 34.dp), ltr.start, "left to right, the start is the rounder left")
        near(0.dp, ltr.end, "left to right, the end")
        val rtl = sheetCornerRoom(listOf(theme.concentricWith(corners, direction = LayoutDirection.Rtl)), theme, box, density)
        near(0.dp, rtl.start, "right to left, the start is the right")
        near(cornerRoom(60.dp, 34.dp), rtl.end, "right to left, the end is the rounder left")
        near(cornerRoom(60.dp, 34.dp), rtl.top, "above, the deeper of the two")
    }

    @Test
    fun aMorphingSheetTakesTheRoundestOfItsShapes() {
        val floating = RoundedCornerShape(28.dp)
        val edge = theme.concentricWith(DeviceCorners.uniform(55.dp))
        near(6.15.dp, sheetCornerRoom(listOf(floating, edge), theme, box, density).start, "floating, becoming an edge sheet")
        near(0.dp, sheetCornerRoom(listOf(floating), theme, box, density).start, "floating only")
    }
}
