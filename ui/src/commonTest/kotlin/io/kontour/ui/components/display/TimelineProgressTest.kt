package io.kontour.ui.components.display

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

    private fun stop(colour: Color = Color.Unspecified) = TimelineStop(
        nodeColour = colour,
        filled = true,
        loading = false,
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
