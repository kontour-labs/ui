package io.kontour.ui.interaction

import androidx.compose.animation.core.spring
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A boundary hit at speed gives, and comes home — including one hit exactly.
 *
 * Reported from a phone on the wheel picker, once the bounce was made to take up
 * in the frame the drum reached its end: "sometimes it snaps into place". The
 * bounce ended itself as soon as it was home, and its first frame is at the place
 * it starts from — which is home exactly when the drum ran out of room exactly at
 * its end. So that bounce was over before it began, and the drum stopped dead.
 */
class RubberBandBounceTest {

    @Test
    fun aBoundaryHitExactlyStillGives() = runTest {
        val band = RubberBand()
        val seen = mutableListOf<Float>()
        withContext(Frames { seen += band.offset }) {
            band.carryOn(past = 0f, velocity = 2000f, limit = 180f, spec = Critical, sinceNanos = 0L)
        }
        assertTrue(seen.max() > 10f, "a drum that met its end exactly at 2000px/s gave ${seen.max()}px")
        assertEquals(0f, band.offset, "and it should have come home")
    }

    @Test
    fun aBoundaryPassedCarriesOnFromWhereItIs() = runTest {
        val band = RubberBand()
        val seen = mutableListOf<Float>()
        withContext(Frames { seen += band.offset }) {
            band.carryOn(past = 12f, velocity = 2000f, limit = 180f, spec = Critical, sinceNanos = 0L)
        }
        assertTrue(seen.first() > 12f * 0.9f, "a band taken up 12px past its end started at ${seen.first()}")
        // Out once, and home without passing it: never the other side of nothing.
        val peak = seen.indexOf(seen.max())
        assertTrue(seen.take(peak + 1).zipWithNext().all { (a, b) -> b >= a }, "on its way out it came back: $seen")
        assertTrue(seen.drop(peak).zipWithNext().all { (a, b) -> b <= a }, "on its way home it went out again: $seen")
        assertTrue(seen.all { it >= 0f }, "it crossed to the other side: $seen")
    }

    /** Frames 8ms apart, as a 120Hz phone draws them, reporting after each. */
    private class Frames(private val drawn: () -> Unit) : MonotonicFrameClock {
        private var time = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            time += 8_000_000L
            return onFrame(time).also { drawn() }
        }
    }

    private companion object {
        val Critical = spring(dampingRatio = 1f, stiffness = 1400f, visibilityThreshold = 0.5f)
    }
}
