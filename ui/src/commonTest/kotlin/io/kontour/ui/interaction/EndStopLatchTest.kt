package io.kontour.ui.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * A stop is met once, and met again only after it has been properly left.
 *
 * Reported from a phone: the slider's end stop "only triggers once" was meant to
 * be true, and "ive found it trigger a few times sometimes". A finger held
 * against the end is not still, and the latch re-armed on the first pixel back
 * inside.
 */
class EndStopLatchTest {

    private class Recorder : FeedbackDispatcher {
        val intents = mutableListOf<FeedbackIntent>()
        override fun perform(intent: FeedbackIntent) {
            intents += intent
        }
    }

    private fun latch(recorder: Recorder, intent: FeedbackIntent = FeedbackIntent.Limit): EndStopLatch {
        // A clock that never moves: a limit is not floored, and this proves it.
        val floor = FeedbackFloor(TestTimeSource())
        return EndStopLatch(DetentTicker(recorder, intent, floor = floor))
    }

    @Test
    fun aFingerTremblingAtTheEndIsOnePush() {
        val recorder = Recorder()
        val stop = latch(recorder)
        stop.arm()
        stop.at(0.5f)
        stop.at(0.99f)
        // Against the end, then a few pixels either side of it.
        for (position in listOf(1.002f, 0.998f, 1.004f, 0.999f, 1f, 1.003f, 0.985f, 1.001f)) stop.at(position)
        assertEquals(listOf(FeedbackIntent.Limit), recorder.intents)
    }

    @Test
    fun backingOffProperlyAndPushingAgainIsASecondPush() {
        val recorder = Recorder()
        val stop = latch(recorder)
        stop.arm()
        stop.at(1.01f)
        stop.at(1f - EndStopLatch.EndStopRelease - 0.01f)
        stop.at(1.01f)
        // And the other end is its own stop.
        stop.at(-0.01f)
        assertEquals(List(3) { FeedbackIntent.Limit }, recorder.intents)
    }

    @Test
    fun aDragThatNeverLeavesTheRangeMeetsNothing() {
        val recorder = Recorder()
        val stop = latch(recorder)
        stop.arm()
        for (i in 0..100) stop.at(i / 100f)
        assertEquals(emptyList(), recorder.intents, "1 is the end, not past it")
    }

    @Test
    fun aSpinRunningOutAtAnEndIsOneMeeting() {
        val recorder = Recorder()
        val stop = latch(recorder)
        stop.arm()
        stop.at(1.02f)
        stop.reached(1)
        assertEquals(1, recorder.intents.size, "the drag had already met it")
        stop.reached(-1)
        assertEquals(2, recorder.intents.size)
    }

    /** Two thumbs meeting: once, and again only once they have parted. */
    @Test
    fun aContactIsMetOnceUntilTheTwoPart() {
        val recorder = Recorder()
        val bump = latch(recorder, FeedbackIntent.Bump)
        bump.arm()
        for (clearance in listOf(0.2f, 0.05f, 0f, -0.001f, 0f, 0.01f, 0f)) bump.touching(clearance)
        assertEquals(listOf(FeedbackIntent.Bump), recorder.intents)
        bump.touching(EndStopLatch.EndStopRelease)
        bump.touching(0f)
        assertEquals(List(2) { FeedbackIntent.Bump }, recorder.intents)
    }

    @Test
    fun aGestureThatStartsInContactHasToPartFirst() {
        val recorder = Recorder()
        val bump = latch(recorder, FeedbackIntent.Bump)
        bump.arm(resting = 1)
        bump.touching(0f)
        bump.touching(0f)
        assertEquals(emptyList(), recorder.intents, "already together is not a meeting")
        bump.touching(0.3f)
        bump.touching(0f)
        assertEquals(listOf(FeedbackIntent.Bump), recorder.intents)
    }
}

/**
 * The texture of a control without steps: grains a set distance apart, as often
 * as the floor allows, firmer the faster the drag.
 */
class DragTextureTest {

    private class Recorder : FeedbackDispatcher {
        val grains = mutableListOf<Float>()
        override fun perform(intent: FeedbackIntent) = error("a grain is performed with a strength")
        override fun perform(intent: FeedbackIntent, strength: Float) {
            assertEquals(FeedbackIntent.Scrub, intent)
            grains += strength
        }
    }

    /** Drags from 0 to [to] at [speed] travel a second, one event every [frameMillis]. */
    private fun drag(texture: DragTexture, clock: TestTimeSource, speed: Float, to: Float = 1f, frameMillis: Int = 8) {
        var at = 0f
        texture.at(at)
        while (at < to) {
            clock += frameMillis.milliseconds
            at = (at + speed * frameMillis / 1000f).coerceAtMost(to)
            texture.at(at)
        }
    }

    @Test
    fun aSlowDragIsFaintGrainsAtEverySpacing() {
        val clock = TestTimeSource()
        val recorder = Recorder()
        val texture = DragTexture(recorder, FeedbackFloor(clock), clock)
        // A tenth of the track a second: a grain every quarter-second, far
        // under the floor.
        drag(texture, clock, speed = 0.1f)
        // A grain is felt on the first event at least a spacing on from the last,
        // so each gap is a spacing or a hair more: 39 or 40 over the whole track.
        val spacings = (1f / DragTexture.GrainSpacing).toInt()
        assertTrue(recorder.grains.size in (spacings - 1)..spacings, "one grain per spacing, not ${recorder.grains.size}")
        for (strength in recorder.grains) {
            assertEquals(true, strength < 0.35f, "a crawl is meant to be faint: ${recorder.grains}")
        }
    }

    @Test
    fun aFastDragIsFirmerAndAsCloseAsTheFloorAllows() {
        val clock = TestTimeSource()
        val recorder = Recorder()
        val texture = DragTexture(recorder, FeedbackFloor(clock), clock)
        // The whole track in half a second: forty spacings in 500ms, which the
        // floor thins to one every 50ms.
        drag(texture, clock, speed = 2f)
        val most = (500 / DragTexture.MinimumGrainInterval.inWholeMilliseconds).toInt()
        assertEquals(true, recorder.grains.size in (most - 2)..most, "${recorder.grains.size} grains, expected about $most")
        assertEquals(true, recorder.grains.drop(2).all { it > 0.8f }, "a fast drag is meant to be firm: ${recorder.grains}")
    }

    @Test
    fun fasterIsFirmer() {
        fun strengths(speed: Float): Float {
            val clock = TestTimeSource()
            val recorder = Recorder()
            drag(DragTexture(recorder, FeedbackFloor(clock), clock), clock, speed)
            return recorder.grains.drop(1).average().toFloat()
        }
        val speeds = listOf(0.1f, 0.4f, 1f, 2f)
        val felt = speeds.map(::strengths)
        assertEquals(felt.sorted(), felt, "strength follows speed: $felt")
    }

    @Test
    fun holdingStillIsSilent() {
        val clock = TestTimeSource()
        val recorder = Recorder()
        val texture = DragTexture(recorder, FeedbackFloor(clock), clock)
        texture.at(0.5f)
        repeat(100) {
            clock += 8.milliseconds
            texture.at(0.5f + if (it % 2 == 0) 0.002f else -0.002f)
        }
        assertEquals(emptyList(), recorder.grains, "a finger resting on a slider is not moving it")
    }

    @Test
    fun theStrengthRunsFromFaintToFull() {
        assertEquals(DragTexture.SlowestStrength, DragTexture.strengthAt(0f))
        assertEquals(1f, DragTexture.strengthAt(DragTexture.FullSpeed))
        assertEquals(1f, DragTexture.strengthAt(DragTexture.FullSpeed * 10f))
    }
}
