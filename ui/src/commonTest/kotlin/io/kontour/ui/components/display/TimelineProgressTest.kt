package io.kontour.ui.components.display

import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where a journey is along a timeline, counted in stops, and what each row draws
 * of it: which stops it has reached, the one it is at, how much of each leg is
 * behind it, and where the band runs on the leg ahead.
 */
class TimelineProgressTest {

    @Test
    fun reachedAndHereFollowTheStop() {
        assertNull(stopProgress(null, 0), "a timeline not being travelled has no progress")
        val atTwo = List(4) { stopProgress(2f, it)!! }
        assertEquals(listOf(true, true, true, false), atTwo.map { it.reached })
        assertEquals(listOf(false, false, true, false), atTwo.map { it.here })
        // Float arithmetic that lands a hair either side of a stop still counts.
        assertTrue(stopProgress(1.9999f, 2)!!.here)
        assertTrue(stopProgress(2.0004f, 2)!!.here)
        assertFalse(stopProgress(1.5f, 2)!!.reached)
    }

    @Test
    fun aLegIsPassedByTheFraction() {
        assertEquals(1f, stopProgress(1.5f, 0)!!.legPassed, "the leg after the first stop is behind")
        assertEquals(0.5f, stopProgress(1.5f, 1)!!.legPassed, "and the next is half travelled")
        assertEquals(0f, stopProgress(1.5f, 2)!!.legPassed)
    }

    @Test
    fun aBandOnlyBetweenStops() {
        assertTrue(stopProgress(1.5f, 1)!!.legBand, "halfway along the leg")
        assertFalse(stopProgress(1f, 1)!!.legBand, "at a stop, the stop pulses instead")
        assertFalse(stopProgress(1f, 0)!!.legBand)
        assertFalse(stopProgress(1.5f, 0)!!.legBand, "a leg behind has no band")
        assertFalse(stopProgress(1.5f, 2)!!.legBand, "nor one not started")
    }

    /**
     * The band sweeps the whole leg, node to node, a third of it long and never
     * outside it — however far along the leg the journey is.
     */
    @Test
    fun theBandSweepsTheWholeLeg() {
        var longest = 0f
        var first = 1f
        var last = 0f
        for (step in 0..200) {
            val band = bandRange(step / 200f) ?: continue
            assertTrue(band.start >= 0f && band.endInclusive <= 1f, "$band outside the leg")
            longest = maxOf(longest, band.endInclusive - band.start)
            first = minOf(first, band.start)
            last = maxOf(last, band.endInclusive)
        }
        assertNear(BandFraction, longest, "the band's length")
        assertTrue(first < 0.01f && last > 0.99f, "the band should reach both ends, went $first to $last")
    }

    @Test
    fun theBandEntersAndLeaves() {
        assertNull(bandRange(0f), "at the start of its loop it has not come in yet")
        assertNull(bandRange(1f), "and at the end it has gone")
        assertNotNull(bandRange(0.5f))
    }

    /**
     * A list draws a leg in two rows, the half below one node and the half above
     * the next. Each maps the connector onto its own half, and between them they
     * cover it: the band crosses the seam rather than skipping or doubling it.
     */
    @Test
    fun theHalvesOfALegShowOneBand() {
        val rows = timelineRows(listOf(stop(), stop()), ConnectorStyle.None, ConnectorStyle.None)
        val below = rows[0].rail(0.3f, Color.Black, Color.Gray, Color.Red, 2.dp).legs.single().travel!!
        val above = rows[1].rail(0.3f, Color.Black, Color.Gray, Color.Red, 2.dp).legs.single().travel!!
        assertEquals(0f to 0.5f, below.from to below.to)
        assertEquals(1f to 0.5f, above.from to above.to)
        assertEquals(0.3f, below.passed)
        assertEquals(0.3f, above.passed, "both halves of one leg know how far along all of it is")
        assertTrue(below.band && above.band)
    }

    /** Both halves of a leg carry the arrows when they are asked for, and neither does by default. */
    @Test
    fun theHalvesOfALegBothKnowToDrawArrows() {
        val rows = timelineRows(listOf(stop(), stop()), ConnectorStyle.None, ConnectorStyle.None)
        fun travel(row: Int, motion: TimelineMotion) =
            rows[row].rail(0.3f, Color.Black, Color.Gray, Color.Red, 2.dp, motion).legs.single().travel!!
        assertTrue(travel(0, TimelineMotion.Arrows).arrows && travel(1, TimelineMotion.Arrows).arrows)
        assertTrue(!travel(0, TimelineMotion.Band).arrows && !travel(1, TimelineMotion.Band).arrows)
    }

    /** The passed colour, a stop's node colour and the halo, as a row resolves them. */
    @Test
    fun aRowResolvesItsNodeFromWhereTheJourneyIs() {
        val rows = timelineRows(listOf(stop(), stop(), stop(Color.Blue)), ConnectorStyle.None, ConnectorStyle.None)
        fun node(index: Int, progress: Float?) = rows[index].rail(progress, Color.Black, Color.Gray, Color.Red, 2.dp).node!!
        assertEquals(Color.Black, node(0, null).colour, "no progress: the timeline's node colour")
        assertEquals(Color.Red, node(0, 1f).colour, "reached")
        assertEquals(Color.Gray, node(1, 0.5f).colour, "not reached")
        assertEquals(Color.Blue, node(2, 0f).colour, "a stop's own colour whatever the progress")
        assertTrue(node(1, 1f).here)
        assertFalse(node(1, 1.5f).here)
    }

    /**
     * A journey running back up the list: the stops at and after the position
     * are the ones reached, and the leg it is on is travelled from its far end,
     * in both of the rows that draw it. Asked for for 0.5.1 as *"the ability
     * to reverse the direction of the progress"*.
     */
    @Test
    fun reversedTheStopsAfterThePositionAreReachedAndTheLegFromItsFarEnd() {
        val rows = timelineRows(listOf(stop(), stop(), stop(), stop()), ConnectorStyle.None, ConnectorStyle.None)
        fun node(index: Int) = rows[index].rail(1.25f, Color.Black, Color.Gray, Color.Red, 2.dp, reversed = true).node!!
        assertEquals(listOf(Color.Gray, Color.Gray, Color.Red, Color.Red), (0..3).map { node(it).colour })
        val below = rows[1].rail(1.25f, Color.Black, Color.Gray, Color.Red, 2.dp, reversed = true).legs.last().travel!!
        val above = rows[2].rail(1.25f, Color.Black, Color.Gray, Color.Red, 2.dp, reversed = true).legs.first().travel!!
        assertTrue(below.reversed && above.reversed, "a half of the leg did not know the journey runs back up it")
        assertNear(0.75f, below.passed, "how much of the leg is travelled, from its far end")
        assertEquals(0.25f..1f, below.travelled, "the stretch travelled is at the leg's far end")
        assertEquals(below.travelled, above.travelled, "both halves of the leg agree on the stretch travelled")
        assertTrue(below.band && above.band)
        // Forward, unchanged.
        val forward = rows[1].rail(1.25f, Color.Black, Color.Gray, Color.Red, 2.dp).legs.last().travel!!
        assertEquals(0f..0.25f, forward.travelled)
    }

    /**
     * Turned round while it runs, the rail's chevrons stand where they are and
     * turn in place on the bouncy spring, overshooting as a chevron does, then
     * run the other way, and settle back onto the plain clock. Asked for as
     * *"have the arrows flip in-place … and then go the other way"*.
     */
    @Test
    fun turnedRoundTheChevronsTurnInPlaceAndThenRunTheOtherWay() {
        val motion = RailMotion()
        val spec = spring<Float>(dampingRatio = 0.45f, stiffness = 900f)
        motion.time.longValue = 10_000L
        motion.steer(reversed = false, flips = true, period = 1.0, spec = spec)
        val before = motion.travel()
        assertEquals(0f, motion.heading())
        motion.time.longValue = 10_016L
        assertTrue(motion.travel() > before, "running forward, the travel goes on down its loop")

        motion.steer(reversed = true, flips = true, period = 1.0, spec = spec)
        val at = motion.travel()
        val headings = ArrayList<Float>()
        val travels = ArrayList<Float>()
        var t = 10_016L
        // Every frame of the turn, to where it rests facing the other way.
        while (t < 10_016L + 2_000L) {
            t += 16L
            motion.time.longValue = t
            val heading = motion.heading()
            if (heading == 180f) break
            headings += heading
            travels += motion.travel()
        }
        assertTrue(headings.size > 5, "the turn was over in ${headings.size} frames: it did not turn so much as jump")
        assertTrue(travels.all { it == at }, "the chevrons moved while they turned: ${travels.distinct().take(4)}")
        assertTrue(headings.zipWithNext().any { (a, b) -> b > a }, "the chevrons did not turn: $headings")
        assertTrue(headings.max() > 180f, "the turn did not overshoot, as the library's bouncy chevrons do: ${headings.max()}")

        // Past the turn: the other way, up the loop.
        t += 1_000L
        motion.time.longValue = t
        val first = motion.travel()
        motion.time.longValue = t + 16L
        val second = motion.travel()
        assertEquals(180f, motion.heading())
        val step = ((first - second) % 1f + 1f) % 1f
        assertTrue(step > 0f && step < 0.1f, "after the turn the chevrons did not run back up their loop: $first, then $second")

        // Settled back onto the plain clock, as a row composed afresh would be.
        motion.time.longValue = t + 5_000L
        val plain = ((-(t + 5_000L)).toDouble() / BandTravel).let { (it - kotlin.math.floor(it)).toFloat() }
        assertNear(plain, motion.travel(), "the travel after a turn, against the plain clock's")
    }

    /** Stopped, under reduced motion, there is nothing to turn: it simply faces the other way. */
    @Test
    fun stoppedTheChevronsSimplyFaceTheOtherWay() {
        val motion = RailMotion()
        motion.steer(reversed = true, flips = true, period = 1.0, spec = spring())
        assertEquals(180f, motion.heading())
        assertTrue(motion.travel().isNaN())
    }

    private fun stop(colour: Color = Color.Unspecified) = TimelineStop(
        nodeColour = colour,
        filled = true,
        loading = false,
        icon = null,
        connector = ConnectorStyle.Solid,
        connectorColour = Color.Unspecified,
        connectorWidth = Dp.Unspecified,
        enabled = true,
        selected = false,
        role = Role.Button,
        onClick = null,
        content = {},
    )

    private fun assertNear(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) < 0.02f, "$what: expected $expected, was $actual")
}
