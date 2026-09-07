package io.kontour.ui.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * The rate limit on the shared detent ticker.
 *
 * The thing it is defending against is not obvious from the code, so it is worth
 * stating here: a haptic tick is not free-running. On the web
 * [FeedbackIntent.Tick] is twenty milliseconds of motor time, and the platform
 * *restarts* the motor on each call rather than queueing. Fire one every 8ms —
 * which is what a flung wheel picker did, once per row — and the motor never
 * stops, so twenty-four detents arrive at the hand as one continuous buzz.
 *
 * The old KDoc called "once per index change" a rate limit. It is not one: an
 * index can change every frame, and sixty a second is a buzz.
 */
class DetentTickerTest {

    private class Recorder : FeedbackDispatcher {
        val intents = mutableListOf<FeedbackIntent>()
        override fun perform(intent: FeedbackIntent) {
            intents += intent
        }
    }

    @Test
    fun aDeliberateDragTicksForEveryDetentItCrosses() {
        // The case that must not regress. A stepped slider dragged by hand
        // crosses its detents a few hundred milliseconds apart — measured at
        // about 160ms on the built site — so the limit never applies and every
        // crossing is reported.
        val clock = TestTimeSource()
        val recorder = Recorder()
        val ticker = DetentTicker(recorder, clock)

        ticker.at(0f)
        repeat(9) { step ->
            clock += 160.milliseconds
            ticker.at((step + 1).toFloat())
        }

        assertEquals(
            9,
            recorder.intents.size,
            "nine detents crossed 160ms apart should be nine ticks, not " +
                "${recorder.intents.size} — the limit is for a fling, and a " +
                "deliberate drag must never reach it",
        )
    }

    @Test
    fun aFlingIsThinnedRatherThanFiringPerRow() {
        // The wheel picker's report. A fling crosses a row about every 8ms; at
        // 20ms of motor time each that is a motor that never stops.
        val clock = TestTimeSource()
        val recorder = Recorder()
        val ticker = DetentTicker(recorder, clock)

        ticker.at(0f)
        repeat(60) { row ->
            clock += 8.milliseconds
            ticker.at((row + 1).toFloat())
        }

        // 60 rows over 480ms. At the 80ms floor that is six ticks, not sixty.
        assertEquals(
            6,
            recorder.intents.size,
            "sixty rows flung past in 480ms should thin to six ticks at the " +
                "${DetentTicker.MinimumTickInterval} floor, but fired " +
                "${recorder.intents.size} — sixty of them is the continuous " +
                "buzz that was reported",
        )
    }

    @Test
    fun aDroppedCrossingDoesNotLeaveTheNextOneArmed() {
        // The subtle half. If a suppressed crossing left `last` where it was,
        // the *next* crossing would compare against a stale index, fire
        // immediately, and the limit would do nothing on exactly the fast drag
        // it exists for.
        val clock = TestTimeSource()
        val recorder = Recorder()
        val ticker = DetentTicker(recorder, clock)

        ticker.at(0f)
        clock += 100.milliseconds
        ticker.at(1f) // fires
        clock += 10.milliseconds
        ticker.at(2f) // dropped, inside the floor
        clock += 10.milliseconds
        ticker.at(3f) // dropped

        assertEquals(
            1,
            recorder.intents.size,
            "two crossings inside the floor should both be dropped, but " +
                "${recorder.intents.size} ticks fired",
        )
    }

    @Test
    fun theFirstCrossingOfAGestureArmsWithoutFiring() {
        // Unchanged behaviour, pinned because the rate limit sits next to it: a
        // drag that starts on a detent has not crossed one.
        val clock = TestTimeSource()
        val recorder = Recorder()
        val ticker = DetentTicker(recorder, clock)

        ticker.at(4f)

        assertEquals(
            emptyList(),
            recorder.intents,
            "landing on a detent is not crossing one",
        )
    }

    @Test
    fun aNewGestureIsStillHeldToTheFloor() {
        // `reset()` re-arms the *index* and deliberately not the clock. Two
        // gestures a few milliseconds apart are one continuous rattle to the
        // hand, whatever they are to the code.
        val clock = TestTimeSource()
        val recorder = Recorder()
        val ticker = DetentTicker(recorder, clock)

        ticker.at(0f)
        clock += 100.milliseconds
        ticker.at(1f) // fires
        ticker.reset()
        clock += 10.milliseconds
        ticker.at(0f) // arms
        clock += 10.milliseconds
        ticker.at(1f) // 20ms after the last tick — inside the floor

        assertEquals(
            1,
            recorder.intents.size,
            "a second gesture starting 20ms after the first one ticked should " +
                "still be inside the floor, but ${recorder.intents.size} ticks fired",
        )
    }
}
