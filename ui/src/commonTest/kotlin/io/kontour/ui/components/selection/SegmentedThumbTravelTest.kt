package io.kontour.ui.components.selection

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a segmented control starts refusing a finger.
 *
 * Reported: *"I still feel like I have to drag it a bit further once it hits its
 * end stop before it starts squashing."* The accumulator was clamped to the
 * **track**, and the band was handed whatever fell outside it — so nothing was
 * refused until the finger reached the track's edge, while the thumb had stopped
 * half a segment earlier at the last segment's centre. The control already knew
 * where that was: the lean is clamped to the same two bounds and is exactly zero
 * once the thumb has arrived. Half a segment of finger bought nothing at all.
 *
 * ### Arithmetic, because the pixels cannot say it
 *
 * The obvious test is the rendered one — nudge past the last segment's centre and
 * watch the thumb narrow — and it does not work at this depth. The deformation is
 * a `scaleX` on a layer under a drop shadow, and measured on a 137px thumb the run
 * of fill colour wanders between 128 and 145 as the finger goes 10, 20, 30, 40, 60,
 * 80, 120 and 180px past: about ±8px of shadow gradient against the ~5px of signal
 * a quarter-segment nudge produces. The same noise is why a squash progression
 * ladder for this control was written and then deleted rather than shipped.
 *
 * `EndStopSquashTest` still measures the deformation where it is large enough to
 * see — a full push past the end of the track — and this pins where it begins.
 */
class SegmentedThumbTravelTest {

    /** A 480px track over three options: segments of 160, centres at 80 and 400. */
    @Test
    fun theRangeIsTheThumbsCentresAndNotTheTrack() {
        assertEquals(
            80f..400f,
            segmentedThumbTravel(trackLength = 480f, options = 3),
            "a 480px track of three segments refuses a finger outside " +
                "${segmentedThumbTravel(480f, 3)}. The thumb's centre can only go " +
                "from the first segment's centre to the last one's — 80 to 400 — " +
                "and every pixel past that is a pixel the control has refused. " +
                "Clamping to 0..480 instead leaves 80px of finger at each end that " +
                "moves nothing and squashes nothing.",
        )
    }

    /**
     * And the two ends are symmetric, because both walls are walls.
     *
     * The leading end matters as much as the trailing one: a control dragged back
     * past its first segment has the same dead travel mirrored, and the squash
     * there is anchored on the opposite side.
     */
    @Test
    fun bothEndsAreHalfASegmentIn() {
        val travel = segmentedThumbTravel(trackLength = 300f, options = 2)
        assertEquals(75f..225f, travel, "a 300px track of two segments gave $travel")
    }

    /**
     * A single option has nowhere to travel, and falls back to the track.
     *
     * Half of one segment is half the track, so the range would collapse to the
     * single point at its centre — and an accumulator pinned to one value cannot
     * report where a press landed. The track is the honest answer for a control
     * with nothing to choose between.
     */
    @Test
    fun aSingleOptionKeepsTheWholeTrack() {
        assertEquals(
            0f..480f,
            segmentedThumbTravel(trackLength = 480f, options = 1),
            "one option collapsed the range to a point",
        )
    }

    @Test
    fun anUnmeasuredTrackIsEmptyRatherThanNegative() {
        // Before layout the width is zero, and for a frame or two it can arrive
        // as a negative from a constraint the control was squeezed into. Neither
        // is a range a `coerceIn` can be handed.
        assertEquals(0f..0f, segmentedThumbTravel(trackLength = 0f, options = 3))
        assertEquals(0f..0f, segmentedThumbTravel(trackLength = -40f, options = 3))
        assertEquals(0f..480f, segmentedThumbTravel(trackLength = 480f, options = 0))
    }
}
