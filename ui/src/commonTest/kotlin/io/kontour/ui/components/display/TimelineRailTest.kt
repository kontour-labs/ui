package io.kontour.ui.components.display

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How a connector's dots and dashes are spaced along a run.
 *
 * A run that ends at the next node ([RunEnd.Mark]) puts a mark on both ends; one
 * that ends at a seam between two rows, each drawing half of one connector
 * ([RunEnd.Seam]), stops half a gap short of it, so the two halves meet with one
 * whole gap between them. Either way the nominal spacing gives, by less than half
 * a step, to make a whole number of marks fit.
 */
class TimelineRailTest {

    private val stroke = 4f

    @Test
    fun dotsToANodeLandOnBothEnds() {
        for (run in listOf(96f, 98f, 100f, 102f, 131f)) {
            val dots = dotOffsets(run, stroke, RunEnd.Mark)
            assertEquals(0f, dots.first())
            assertNear(run, dots.last(), "the last dot of a $run px run")
            assertEvenlySpaced(dots, nominal = stroke * 2f)
        }
    }

    @Test
    fun dotsToASeamStopHalfAPitchShort() {
        for (run in listOf(96f, 98f, 100f, 102f, 131f)) {
            val dots = dotOffsets(run, stroke, RunEnd.Seam)
            assertEquals(0f, dots.first())
            val pitch = if (dots.size > 1) dots[1] - dots[0] else 2f * run
            assertNear(run - pitch / 2f, dots.last(), "the last dot of a $run px run to a seam")
            assertEvenlySpaced(dots, nominal = stroke * 2f)
        }
    }

    @Test
    fun dashesToANodeEndOnADash() {
        for (run in listOf(60f, 61f, 63f, 100f)) {
            val (on, off) = dashIntervals(run, stroke, RunEnd.Mark)!!.let { it[0] to it[1] }
            val dashes = ((run + off) / (on + off)).let { kotlin.math.round(it).toInt() }
            assertNear(run, dashes * on + (dashes - 1) * off, "$run px of dashes to a node")
        }
    }

    @Test
    fun dashesToASeamEndHalfAGapShort() {
        for (run in listOf(60f, 61f, 63f, 100f)) {
            val (on, off) = dashIntervals(run, stroke, RunEnd.Seam)!!.let { it[0] to it[1] }
            val dashes = ((run + off / 2f) / (on + off)).let { kotlin.math.round(it).toInt() }
            assertNear(run, dashes * on + (dashes - 0.5f) * off, "$run px of dashes to a seam")
            assertTrue(abs(off - stroke * 2f) <= (on + stroke * 2f) / 2f, "a gap of $off is too far from nominal")
        }
    }

    @Test
    fun aRunTooShortForADashAndAGapIsOneLine() {
        assertNull(dashIntervals(stroke * 2f, stroke, RunEnd.Mark))
        assertNull(dashIntervals(stroke, stroke, RunEnd.Seam))
    }

    /**
     * A lane passing straight through a row meets a seam at both ends, so its dots
     * sit half a pitch in from each: the rows above and below put the other halves
     * of those pitches against them.
     */
    @Test
    fun dotsBetweenTwoSeamsSitHalfAPitchFromEachEnd() {
        for (run in listOf(96f, 98f, 100f, 102f, 131f)) {
            val dots = dotOffsets(run, stroke, RunEnd.Seam, start = RunEnd.Seam)
            val pitch = if (dots.size > 1) dots[1] - dots[0] else run
            assertNear(pitch / 2f, dots.first(), "the first dot of a $run px lane")
            assertNear(run - pitch / 2f, dots.last(), "the last dot of a $run px lane")
            assertEvenlySpaced(dots, nominal = stroke * 2f)
        }
    }

    /** From a seam into a node is the node-to-seam run walked the other way. */
    @Test
    fun dotsFromASeamToANodeEndOnTheNode() {
        for (run in listOf(96f, 100f, 131f)) {
            val there = dotOffsets(run, stroke, RunEnd.Seam)
            val back = dotOffsets(run, stroke, RunEnd.Mark, start = RunEnd.Seam)
            assertNear(run, back.last(), "the dot on the node")
            for (i in there.indices) assertNear(run - there[i], back[back.lastIndex - i], "dot $i")
        }
    }

    /** Between two seams: as many gaps as dashes, and the pattern starts half a gap in. */
    @Test
    fun dashesBetweenTwoSeamsStartAndEndHalfAGapIn() {
        for (run in listOf(60f, 61f, 63f, 100f)) {
            val intervals = dashIntervals(run, stroke, RunEnd.Seam, start = RunEnd.Seam)!!
            val (on, off) = intervals[0] to intervals[1]
            val dashes = kotlin.math.round(run / (on + off)).toInt()
            assertNear(run, dashes * (on + off), "$run px of dashes between seams")
            assertNear(on + off / 2f, dashPhase(intervals, RunEnd.Seam), "the pattern's start")
        }
    }

    /** A run from a node is the spacing it always had, and starts on its dash. */
    @Test
    fun aMarkStartIsTheOldSpacing() {
        for (run in listOf(60f, 96f, 131f)) {
            for (end in RunEnd.entries) {
                assertTrue(dotOffsets(run, stroke, end).contentEquals(dotOffsets(run, stroke, end, start = RunEnd.Mark)))
                val intervals = dashIntervals(run, stroke, end, start = RunEnd.Mark)
                assertTrue(dashIntervals(run, stroke, end).contentEquals(intervals))
                if (intervals != null) assertEquals(0f, dashPhase(intervals, RunEnd.Mark))
            }
        }
    }

    private fun assertEvenlySpaced(offsets: FloatArray, nominal: Float) {
        if (offsets.size < 2) return
        val pitch = offsets[1] - offsets[0]
        for (i in 1 until offsets.size) assertNear(pitch, offsets[i] - offsets[i - 1], "the pitch")
        assertTrue(abs(pitch - nominal) < nominal / 2f, "a pitch of $pitch is more than half off $nominal")
    }

    private fun assertNear(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) < 0.01f, "$what: expected $expected, was $actual")
}
