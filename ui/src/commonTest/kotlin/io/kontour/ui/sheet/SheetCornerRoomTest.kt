package io.kontour.ui.sheet

import androidx.compose.foundation.shape.CornerBasedShape
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

    /** A corner of [shape], resolved on [box]. */
    private fun radius(shape: CornerBasedShape, start: Boolean = true): Dp =
        with(density) { (if (start) shape.topStart else shape.topEnd).toPx(box, this).toDp() }

    /**
     * Reported: *"the sheet corner radius looks a bit funny, especially on very
     * rounded devices. on iphones and newer pixels, it starts to look like the
     * rounded corners get a bit close to the text in the sheet header"*. An edge
     * sheet took the display's corners whole — 55pt on an iPhone — and wore them
     * at half height too, in the middle of the screen.
     */
    @Test
    fun anEdgeSheetsCornersStopAtTheCapOnARoundDisplay() {
        val iphone = edgeSheetShape(theme, DeviceCorners.uniform(55.dp), LayoutDirection.Ltr)
        near(EdgeSheetCornerCap, radius(iphone), "an iPhone's 55pt, at the start")
        near(EdgeSheetCornerCap, radius(iphone, start = false), "and at the end")
        near(0.dp, with(density) { iphone.bottomStart.toPx(box, this).toDp() }, "the bottom, against the window, stays square")
        near(EdgeSheetCornerCap, radius(edgeSheetShape(theme, DeviceCorners.uniform(51.dp), LayoutDirection.Ltr)), "a newer Pixel's 51dp")
    }

    @Test
    fun underTheCapAnEdgeSheetIsStillConcentricWithTheDisplay() {
        near(38.dp, radius(edgeSheetShape(theme, DeviceCorners.uniform(38.dp), LayoutDirection.Ltr)), "a display a little rounder than the theme")
        near(34.dp, radius(edgeSheetShape(theme, DeviceCorners.uniform(20.dp), LayoutDirection.Ltr)), "a gentler display leaves the theme's")
        near(34.dp, radius(edgeSheetShape(theme, null, LayoutDirection.Ltr)), "a desktop, which reports none")
        val rounder = SquircleShape(48.dp).topCornersOnly()
        near(48.dp, radius(edgeSheetShape(rounder, DeviceCorners.uniform(55.dp), LayoutDirection.Ltr)), "a theme rounder than the cap keeps its own")
    }

    @Test
    fun anEdgeSheetOnAnIphoneTakesTheCappedCornersRoomAtBothCorners() {
        val edge = edgeSheetShape(theme, DeviceCorners.uniform(55.dp), LayoutDirection.Ltr)
        val room = sheetCornerRoom(listOf(edge), theme, box, density)
        val capped = cornerRoom(EdgeSheetCornerCap, 34.dp)
        near(capped, room.start, "start")
        near(capped, room.end, "end")
        near(capped, room.top, "above")
        assertTrue(capped < 2.dp, "the header moves in by under 2dp now, where it moved 6: $capped")
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
        near(6.15.dp, sheetCornerRoom(listOf(floating, edge), theme, box, density).start, "floating, becoming an uncapped edge sheet")
        near(0.dp, sheetCornerRoom(listOf(floating), theme, box, density).start, "floating only")
    }
}
