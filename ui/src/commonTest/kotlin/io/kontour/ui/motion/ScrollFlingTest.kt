package io.kontour.ui.motion

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * The library's fling, where one is wanted: iOS, where flings "stop too soon".
 *
 * It slows at UIKit's rate, and a flick while a list is still moving adds to the
 * speed it had, as a UIKit scroll view's does — so a run of flicks goes further
 * each time instead of starting again from rest.
 */
class ScrollFlingTest {

    /** At UIKit's rate a throw travels its speed over `-ln(0.998) × 1000`: about half a second's worth. */
    @Test
    fun aFlingTravelsAsFarAsUIKitsWould() = runTest {
        val fling = AccumulatingFling(ScrollFeel(), density = 1f, clock = TestTimeSource())
        val travel = flung(fling, 2000f)
        val uikit = 2000f / (-ln(0.998f) * 1000f)
        assertTrue(abs(travel - uikit) <= uikit * 0.02f, "a 2000px/s fling went $travel px, UIKit's would go $uikit")
    }

    @Test
    fun theVelocityScaleScalesTheThrow() = runTest {
        val plain = flung(AccumulatingFling(ScrollFeel(), 1f, TestTimeSource()), 2000f)
        val scaled = flung(AccumulatingFling(ScrollFeel(velocityScale = 1.5f), 1f, TestTimeSource()), 2000f)
        assertTrue(abs(scaled / plain - 1.5f) < 0.03f, "a 1.5 scale took $plain px to $scaled px")
    }

    /** A second flick that lands on a moving list throws it faster than the flick alone would. */
    @Test
    fun aFlickOnAMovingListAddsToIt() = runTest {
        val clock = TestTimeSource()
        val fling = AccumulatingFling(ScrollFeel(), 1f, clock)
        val alone = flung(AccumulatingFling(ScrollFeel(), 1f, TestTimeSource()), 3000f)

        flung(fling, 3000f, fingerDownAfter = 10)
        clock += 150.milliseconds
        val second = flung(fling, 3000f)
        assertTrue(second > alone * 1.5f, "a second flick on a list still going went $second px, the same flick from rest $alone px")
    }

    @Test
    fun aFlickTheOtherWayDoesNotAdd() = runTest {
        val clock = TestTimeSource()
        val fling = AccumulatingFling(ScrollFeel(), 1f, clock)
        val alone = flung(AccumulatingFling(ScrollFeel(), 1f, TestTimeSource()), -3000f)
        flung(fling, 3000f, fingerDownAfter = 10)
        clock += 150.milliseconds
        assertEquals(alone, flung(fling, -3000f), 1f, "throwing it back the other way should start from rest")
    }

    /** A finger that held the list still for a moment has stopped it: the next flick starts afresh. */
    @Test
    fun aStaleFlingDoesNotCarry() = runTest {
        val clock = TestTimeSource()
        val fling = AccumulatingFling(ScrollFeel(), 1f, clock)
        val alone = flung(AccumulatingFling(ScrollFeel(), 1f, TestTimeSource()), 3000f)
        flung(fling, 3000f, fingerDownAfter = 10)
        clock += 800.milliseconds
        assertEquals(alone, flung(fling, 3000f), 1f, "a flick most of a second after the list was stopped should not add")
    }

    /** A slow release is the finger stopping the list, not another flick. */
    @Test
    fun aSlowReleaseDoesNotCarry() = runTest {
        val clock = TestTimeSource()
        val fling = AccumulatingFling(ScrollFeel(), 1f, clock)
        val alone = flung(AccumulatingFling(ScrollFeel(), 1f, TestTimeSource()), 100f)
        flung(fling, 3000f, fingerDownAfter = 10)
        clock += 100.milliseconds
        assertEquals(alone, flung(fling, 100f), 1f)
    }

    @Test
    fun withoutAccumulationEachFlickStartsFromRest() = runTest {
        val clock = TestTimeSource()
        val feel = ScrollFeel(accumulates = false)
        val fling = AccumulatingFling(feel, 1f, clock)
        val alone = flung(AccumulatingFling(feel, 1f, TestTimeSource()), 3000f)
        flung(fling, 3000f, fingerDownAfter = 10)
        clock += 100.milliseconds
        assertEquals(alone, flung(fling, 3000f), 1f)
    }

    /** At the end of the list what is left over is handed on, as the platform's fling does. */
    @Test
    fun atAnEndTheRestIsHandedOn() = runTest {
        val fling = AccumulatingFling(ScrollFeel(), 1f, TestTimeSource())
        var travelled = 0f
        val scope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                val room = (100f - travelled).coerceAtLeast(0f)
                val taken = pixels.coerceAtMost(room)
                travelled += taken
                return taken
            }
        }
        val left = withContext(Frames()) { with(fling) { scope.performFling(2000f) } }
        assertEquals(100f, travelled, 0.5f)
        assertTrue(left > 1000f, "a fling that hit the end 100px in should still have most of its speed, had $left")
    }

    /** Frames 16ms apart; after [fingerDownAfter] of them a finger lands and the fling is cancelled. */
    private class Frames(private val fingerDownAfter: Int = Int.MAX_VALUE) : MonotonicFrameClock {
        private var count = 0
        private var time = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            if (count++ >= fingerDownAfter) throw CancellationException("a finger landed")
            time += 16_000_000L
            return onFrame(time)
        }
    }

    /** How far [fling] carries a list thrown at [velocity], until it stops or a finger lands. */
    private suspend fun flung(fling: AccumulatingFling, velocity: Float, fingerDownAfter: Int = Int.MAX_VALUE): Float {
        var travelled = 0f
        val scope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                travelled += pixels
                return pixels
            }
        }
        try {
            withContext(Frames(fingerDownAfter)) { with(fling) { scope.performFling(velocity) } }
        } catch (_: CancellationException) {
        }
        return abs(travelled)
    }
}
