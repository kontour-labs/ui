package io.kontour.ui.components.selection

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a finger's path on a knob is read: as a drag in a line, or as a turn round
 * the middle.
 *
 * "Can we somehow combine the circular spinning motion of the knob with the
 * left/right and up/down motion?" Paths here are drawn by hand in pixels about a
 * knob whose middle is the origin, whose track has a radius of 100, whose scale
 * sweeps 270°, and whose drag covers the range in 400.
 */
class KnobTurnReaderTest {

    /** A straight drag goes the way it points, from anywhere, and never the other way first. */
    @Test
    fun aStraightDragGoesTheWayItPointsFromAnywhere() {
        val places = listOf(Offset.Zero, Offset(-70f, 0f), Offset(70f, 0f), Offset(0f, -70f), Offset(0f, 70f))
        val ways = listOf(Offset(0f, -100f) to 0.25f, Offset(100f, 0f) to 0.25f, Offset(0f, 100f) to -0.25f, Offset(-100f, 0f) to -0.25f)
        for (from in places) for ((by, expected) in ways) {
            val (total, lowest, highest) = read(line(from, from + by))
            assertTrue(
                abs(total - expected) < 0.005f,
                "a drag by $by from $from turned the knob by $total, not $expected",
            )
            val wrongWay = if (expected > 0f) lowest else -highest
            assertTrue(wrongWay >= -0.0001f, "a drag by $by from $from went the wrong way first, by $wrongWay")
        }
    }

    /** Round the knob from the top, clockwise: the knob follows the finger's angle. */
    @Test
    fun aCircleTurnsTheKnobByItsAngle() {
        val (total, lowest, _) = read(arc(from = -90.0, by = 180.0))
        assertTrue(abs(total - 180f / 270f) < 0.03f, "half a turn clockwise turned the knob by $total, not ${180f / 270f}")
        assertTrue(lowest >= -0.0001f, "and went the wrong way on the way, by $lowest")
    }

    /**
     * Started where the two readings disagree — down the right-hand side, which is
     * less as a drag and more as a turn — a circle still only ever goes up.
     */
    @Test
    fun aCircleStartedWhereTheReadingsDisagreeNeverGoesTheWrongWay() {
        val (clockwise, dip, _) = read(arc(from = 0.0, by = 90.0))
        assertTrue(abs(clockwise - 90f / 270f) < 0.03f, "a quarter turn clockwise from the right turned it by $clockwise")
        assertTrue(dip >= -0.0001f, "and dipped first, by $dip")

        val (back, _, rise) = read(arc(from = 90.0, by = -90.0))
        assertTrue(abs(back + 90f / 270f) < 0.03f, "a quarter turn back from the bottom turned it by $back")
        assertTrue(rise <= 0.0001f, "and rose first, by $rise")
    }

    /** A line passing close by the middle sweeps a lot of angle, and is still a drag. */
    @Test
    fun aLineAcrossTheMiddleIsADrag() {
        val reader = KnobTurnReader()
        val (total, _, _) = read(line(Offset(-100f, -35f), Offset(100f, -35f)), reader)
        assertTrue(!reader.turning, "a straight line across the knob was read as a turn")
        assertTrue(abs(total - 0.5f) < 0.005f, "200 to the right turned it by $total, not 0.5")
    }

    /** A drag that turns into a circle becomes a turn. */
    @Test
    fun aDragThatBecomesACircleBecomesATurn() {
        val reader = KnobTurnReader()
        val start = Offset(-80f, -40f)
        val straight = line(start, Offset(0f, -70f))
        read(straight + arc(from = -90.0, by = 150.0, radius = 70.0).drop(1), reader)
        assertTrue(reader.turning, "a drag that went on round the knob was never read as a turn")
    }

    /** Let go moving, it spins on the way the gesture was read; slowly, it does not. */
    @Test
    fun aThrowSpinsTheWayTheGestureWasRead() {
        val turned = KnobTurnReader()
        read(arc(from = -90.0, by = 90.0), turned)
        // At the right-hand side, moving down: clockwise, at 1000px a second 70 out.
        val spin = turned.release(Offset(0f, 1000f), minimumSpeed = 400f)
        assertTrue(spin != null && spin > 0f, "thrown clockwise, it should spin up, and gave $spin")
        assertEquals(null, turned.release(Offset(0f, 100f), minimumSpeed = 400f), "a slow release spun it")

        val dragged = KnobTurnReader()
        read(line(Offset.Zero, Offset(0f, -100f)), dragged)
        val thrown = dragged.release(Offset(0f, -1000f), minimumSpeed = 400f)
        assertTrue(thrown != null && abs(thrown - 2.5f) < 0.001f, "thrown up at 1000px a second it should spin at 2.5 a second, gave $thrown")
    }

    @Test
    fun aTurnIsTheShortWayRound() {
        // Across the gap at the bottom, where the angle jumps from 180 to -180.
        val turn = turnBetween(Offset(-1f, 0.05f), Offset(-1f, -0.05f))
        assertTrue(abs(turn) < 10f, "a few pixels across the jump should be a few degrees, was $turn")
        assertTrue(abs(turnBetween(Offset(0f, -1f), Offset(1f, 0f)) - 90f) < 0.01f, "top to right is a quarter turn clockwise")
    }

    /** The total a path turns the knob by, and the lowest and highest the running total went. */
    private fun read(path: List<Offset>, reader: KnobTurnReader = KnobTurnReader()): Triple<Float, Float, Float> {
        // The claim hands over where the finger is, and then the move that got it there.
        reader.start(path[1], Offset.Zero, Radius, Sweep, Travel, DecideAfter)
        var total = 0f
        var lowest = 0f
        var highest = 0f
        for (index in 1..path.lastIndex) {
            total += reader.move(path[index] - path[index - 1])
            lowest = minOf(lowest, total)
            highest = maxOf(highest, total)
        }
        return Triple(total, lowest, highest)
    }

    private fun line(from: Offset, to: Offset, steps: Int = 40): List<Offset> =
        List(steps + 1) { from + (to - from) * (it.toFloat() / steps) }

    private fun arc(from: Double, by: Double, radius: Double = 70.0, steps: Int = 60): List<Offset> =
        List(steps + 1) {
            val degrees = (from + by * it / steps) * PI / 180
            Offset((cos(degrees) * radius).toFloat(), (sin(degrees) * radius).toFloat())
        }

    private companion object {
        const val Radius = 100f
        const val Sweep = 270f
        const val Travel = 400f
        const val DecideAfter = 24f
    }
}
