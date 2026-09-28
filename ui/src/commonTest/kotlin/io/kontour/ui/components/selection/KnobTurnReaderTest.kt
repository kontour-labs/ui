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
 * left/right and up/down motion?" and then "take the current position of the knob
 * into account". Paths here are drawn by hand in pixels about a knob whose middle
 * is the origin, whose track has a radius of 100, whose scale starts at 135° and
 * sweeps 270°, and whose drag covers the range in 400. The value is at the middle of
 * the scale — the notch straight up — unless a test says otherwise.
 */
class KnobTurnReaderTest {

    /**
     * With the notch at the top, a straight drag goes the way it points, from
     * anywhere on the knob, and never the other way first: right pulls the notch
     * clockwise, and up and down run across the arc there, so up is more.
     */
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

    /**
     * At the end of the scale, low on the right, dragging up pulls the notch back
     * round towards zero — and keeps pulling, past the top and on.
     *
     * "If it started on the very end (so like bottom-right of the circle) and I try
     * to drag it up, I'm expecting to have the turning gesture to pull it back around
     * to 0." And "if it starts as dragging up, then we probably want to pull the knob
     * up, but then keep pulling it in the same direction."
     */
    @Test
    fun atTheEndDraggingUpPullsItBackAndKeepsPulling() {
        val (total, _, highest) = read(line(Offset(20f, 20f), Offset(20f, -380f), steps = 80), fraction = 1f)
        assertTrue(abs(total + 1f) < 0.005f, "400 up from the end turned the knob by $total, not all the way back, -1")
        assertTrue(highest <= 0.0001f, "and went up first, by $highest")
    }

    /** Brought back the way it came, a drag turns the knob back. */
    @Test
    fun aDragBroughtBackTurnsItBack() {
        val out = line(Offset(20f, 20f), Offset(20f, -180f))
        val back = line(Offset(20f, -180f), Offset(20f, -80f)).drop(1)
        val (total, _, _) = read(out + back, fraction = 1f)
        assertTrue(abs(total + 0.25f) < 0.005f, "200 up and 100 down from the end turned it by $total, not -0.25")
    }

    /**
     * With the notch high on the right, down pulls it clockwise, so down is more —
     * the way GarageBand's knobs turn to a drag down their right-hand side.
     */
    @Test
    fun highOnTheRightDownIsMore() {
        val (down, _, _) = read(line(Offset.Zero, Offset(0f, 100f)), fraction = 0.75f)
        assertTrue(abs(down - 0.25f) < 0.005f, "a drag down with the notch high on the right turned it by $down, not 0.25")
        val (up, _, _) = read(line(Offset.Zero, Offset(0f, -100f)), fraction = 0.75f)
        assertTrue(abs(up + 0.25f) < 0.005f, "and a drag up by $up, not -0.25")
    }

    /**
     * At zero, right pulls the notch into its end stop, which would refuse the drag
     * outright; the ordinary rule takes over, and right is more.
     */
    @Test
    fun atAnEndADragIsNeverRefused() {
        val (right, _, _) = read(line(Offset.Zero, Offset(100f, 0f)), fraction = 0f)
        assertTrue(abs(right - 0.25f) < 0.005f, "a drag right at zero turned it by $right, not 0.25")
        val (left, _, _) = read(line(Offset.Zero, Offset(-100f, 0f)), fraction = 1f)
        assertTrue(abs(left + 0.25f) < 0.005f, "a drag left at the end turned it by $left, not -0.25")
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
    private fun read(
        path: List<Offset>,
        reader: KnobTurnReader = KnobTurnReader(),
        fraction: Float = 0.5f,
    ): Triple<Float, Float, Float> {
        // The claim hands over where the finger is, and then the move that got it there.
        reader.start(path[1], Offset.Zero, Radius, Start, Sweep, fraction, Travel, DecideAfter)
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
        const val Start = 135f
        const val Sweep = 270f
        const val Travel = 400f
        const val DecideAfter = 24f
    }
}
