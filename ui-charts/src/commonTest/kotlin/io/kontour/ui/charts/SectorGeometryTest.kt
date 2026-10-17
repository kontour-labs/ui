package io.kontour.ui.charts

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which sector a point is in: half, then three tenths, then a fifth, from the
 * top, clockwise or anticlockwise, and in a box that is not square.
 */
class SectorGeometryTest {

    private val shares = listOf(0.5f, 0.3f, 0.2f)

    @Test
    fun clockwiseTheRightIsAQuarterOfTheWayRound() {
        val pie = SectorGeometry(shares, innerRadius = 0f, direction = 1f)
        assertEquals(0, pie.partAt(Offset(150f, 100f), 200f, 200f), "the right, a quarter round")
        assertEquals(1, pie.partAt(Offset(50f, 120f), 200f, 200f), "low on the left, two thirds round")
        assertEquals(2, pie.partAt(Offset(96f, 50f), 200f, 200f), "just left of the top, the end")
    }

    @Test
    fun anticlockwiseTheRightIsThreeQuartersOfTheWayRound() {
        val pie = SectorGeometry(shares, innerRadius = 0f, direction = -1f)
        assertEquals(1, pie.partAt(Offset(150f, 100f), 200f, 200f), "the right, three quarters round")
        assertEquals(0, pie.partAt(Offset(50f, 100f), 200f, 200f), "the left, a quarter round")
        assertEquals(2, pie.partAt(Offset(104f, 50f), 200f, 200f), "just right of the top, the end")
    }

    /** A wide box draws its circle in the middle, and that is where a tap is read. */
    @Test
    fun aWideBoxHasItsCircleInTheMiddle() {
        val pie = SectorGeometry(shares, innerRadius = 0f, direction = 1f)
        assertEquals(0, pie.partAt(Offset(250f, 100f), 400f, 200f))
        assertNull(pie.partAt(Offset(40f, 100f), 400f, 200f), "left of the circle is outside it")
    }

    @Test
    fun theHoleIsNoSector() {
        val donut = SectorGeometry(shares, innerRadius = 0.5f, direction = 1f)
        assertNull(donut.partAt(Offset(100f, 100f), 200f, 200f))
        assertEquals(0, donut.partAt(Offset(180f, 100f), 200f, 200f))
    }
}
