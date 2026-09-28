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
    /** A finger that only rolled off the glass threw nothing, however fast it left. */
    @Test
    fun aRollOffIsNoThrow() {
        val finger = SheetFinger()
        finger.down(0)
        finger.move(16, 8f)
        finger.move(32, 8f)
        finger.move(48, 8f)
        finger.lift(50)
        assertEquals(0f, finger.throwOf(reported = 500f, atLeast = 28f), "24px of roll-off counted as a throw")
    }

    /** The run starts again where the finger turns: a pull up and a roll-off down is still a roll-off. */
    @Test
    fun theRunStartsAgainWhereTheFingerTurns() {
        val finger = SheetFinger()
        finger.down(0)
        repeat(20) { finger.move(16L * (it + 1), -30f) }
        finger.move(336, 8f)
        finger.move(352, 8f)
        finger.move(368, 8f)
        finger.lift(370)
        assertEquals(0f, finger.throwOf(reported = 500f, atLeast = 28f), "a roll-off after a pull up counted as a throw")
    }

    /** A flick that runs past the minimum is the throw it always was. */
    @Test
    fun aFlickThatRunsFarEnoughIsAThrow() {
        val finger = SheetFinger()
        finger.down(0)
        repeat(4) { finger.move(16L * (it + 1), 8f) }
        finger.lift(66)
        assertEquals(500f, finger.throwOf(reported = 500f, atLeast = 28f))
    }
}
