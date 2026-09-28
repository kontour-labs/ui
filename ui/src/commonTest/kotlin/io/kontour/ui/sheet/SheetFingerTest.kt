package io.kontour.ui.sheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a release counts as when the platform's velocity tracker reports nothing:
 * the finger's own path, unless the finger had stopped. See [SheetFinger].
 */
class SheetFingerTest {

    /** A thumb laid on the sheet, rested 80ms, then flicked 30ms: UIKit's tracker calls that no throw. */
    @Test
    fun aRestThenAFlickIsReadOffThePath() {
        val finger = SheetFinger()
        finger.down(0)
        finger.move(80, 30f)
        finger.move(88, 30f)
        finger.move(96, 30f)
        finger.move(104, 30f)
        finger.lift(110)
        val velocity = finger.releaseVelocity(reported = 0f)
        assertTrue(velocity > 2500f, "four moves of 30px 8ms apart after a rest read as $velocity px/s")
    }

    @Test
    fun aFingerThatStoppedBeforeLiftingThrewNothing() {
        val finger = SheetFinger()
        finger.down(0)
        finger.move(16, 60f)
        finger.move(32, 60f)
        finger.lift(120)
        assertEquals(0f, finger.releaseVelocity(reported = 0f))
    }

    /** A slow frame mid-throw is a gap in the samples, not a stop. */
    @Test
    fun aGapInTheSamplesIsReadAcross() {
        val finger = SheetFinger()
        finger.down(0)
        finger.move(16, 40f)
        finger.move(32, 40f)
        finger.move(82, 125f)
        finger.lift(90)
        val velocity = finger.releaseVelocity(reported = 0f)
        assertTrue(velocity > 2000f, "a throw across a 50ms frame read as $velocity px/s")
    }

    /** Whatever the platform did report is left alone. */
    @Test
    fun aReportedVelocityIsKept() {
        val finger = SheetFinger()
        finger.down(0)
        finger.move(16, 60f)
        finger.lift(20)
        assertEquals(1234f, finger.releaseVelocity(reported = 1234f))
    }

    @Test
    fun aTouchWithNoMovementHasNoOpinion() {
        val finger = SheetFinger()
        finger.down(0)
        finger.lift(10)
        assertEquals(0f, finger.releaseVelocity(reported = 0f))
    }
}
