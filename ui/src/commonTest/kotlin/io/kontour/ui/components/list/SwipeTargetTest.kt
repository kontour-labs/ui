package io.kontour.ui.components.list

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a released swipe goes, rule by rule. See `SwipeActions`' "How it decides".
 *
 * The numbers are a row with two end actions of 88px each: the end reveal at -176,
 * a flick at 400px/s and 12px, and a slow release opening at 0.35 of the reveal.
 */
class SwipeTargetTest {

    private fun target(
        offset: Float,
        velocity: Float = 0f,
        startedAt: SwipeValue = SwipeValue.Resting,
        committing: Boolean = false,
    ) = swipeTarget(
        offset = offset,
        velocity = velocity,
        startedAt = startedAt,
        startReveal = Float.NaN,
        endReveal = -176f,
        committing = committing,
        flickVelocity = 400f,
        flickTravel = 12f,
        revealShare = 0.35f,
    )

    @Test
    fun pastThePointOfNoReturnItCommitsAtAnySpeed() {
        assertEquals(SwipeValue.EndCommitted, target(-300f, committing = true))
        assertEquals(SwipeValue.EndCommitted, target(-300f, velocity = 2_000f, committing = true))
    }

    @Test
    fun aHardFlickShortOfThePointOfNoReturnOnlyReveals() {
        assertEquals(SwipeValue.End, target(-150f, velocity = -3_000f))
        assertEquals(SwipeValue.End, target(-260f, velocity = -3_000f))
    }

    @Test
    fun aFlickOpensOrClosesByItsDirection() {
        assertEquals(SwipeValue.End, target(-30f, velocity = -600f))
        assertEquals(SwipeValue.Resting, target(-150f, velocity = 600f, startedAt = SwipeValue.End))
    }

    @Test
    fun aTwitchTooShortToMeanADirectionFallsBackToDistance() {
        // Fast, but only 6px from where the row was resting.
        assertEquals(SwipeValue.Resting, target(-6f, velocity = -900f))
    }

    @Test
    fun aSlowReleaseOpensAThirdOfTheWayInAndClosesAThirdOfTheWayOut() {
        assertEquals(SwipeValue.Resting, target(-50f))
        assertEquals(SwipeValue.End, target(-70f))
        assertEquals(SwipeValue.End, target(-130f, startedAt = SwipeValue.End))
        assertEquals(SwipeValue.Resting, target(-100f, startedAt = SwipeValue.End))
    }

    @Test
    fun carriedPastTheActionsButNotCommittingItOpens() {
        assertEquals(SwipeValue.End, target(-240f))
    }

    @Test
    fun aSideWithNothingOnItRests() {
        assertEquals(SwipeValue.Resting, target(40f, velocity = 900f))
    }
}
