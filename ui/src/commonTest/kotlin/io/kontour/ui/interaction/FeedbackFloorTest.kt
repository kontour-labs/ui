package io.kontour.ui.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * One rate floor for the whole composition, rather than one per component.
 *
 * The interval itself is argued for on `DetentTicker.MinimumTickInterval` and
 * tested next door. What is tested here is the *sharing*, which is a separate
 * claim and the one that was wrong: a floor per component is a floor per
 * component, and a hand does not feel components.
 *
 * It was already wrong before the light tier arrived — a `TimePicker` is three
 * `WheelPicker`s with three tickers, so a thumb sweeping across all three could
 * produce three independent streams of ticks 8ms apart and each one would be
 * inside its own limit. With a dozen components now acknowledging a press it
 * would be plainly wrong: a chip row answering three taps in quick succession is
 * one rattle to the person holding the phone, and three calls to three helpers to
 * the code.
 *
 * ### What it costs, said plainly
 *
 * A slider's tick can swallow an unrelated chip's tap forty milliseconds later.
 * That is a real loss and it is the same argument the eighty milliseconds came
 * from: two pulses that close together are not two pulses to a hand, so the one
 * that arrives second was never going to be felt as its own event anyway.
 *
 * The floor deliberately does **not** gate anything heavier than a tick. An
 * outcome has to arrive when it happens, and a threshold quietly dropped because
 * a slider ticked recently is a gesture whose meaning changed without saying so.
 */
class FeedbackFloorTest {

    private class Recorder : FeedbackDispatcher {
        val intents = mutableListOf<FeedbackIntent>()
        override fun perform(intent: FeedbackIntent) {
            intents += intent
        }
    }

    @Test
    fun twoComponentsSharingAFloorRattleOnce() {
        val clock = TestTimeSource()
        val floor = FeedbackFloor(clock)
        val recorder = Recorder()
        // A slider and a chip, as far as this is concerned: two tickers that have
        // never heard of each other, handed the same floor by the theme.
        val slider = DetentTicker(recorder, floor = floor)
        val chip = DetentTicker(recorder, FeedbackIntent.Tap, floor = floor)

        slider.at(0f)
        clock += 200.milliseconds
        slider.at(1f)
        chip.at(0f)
        clock += 40.milliseconds
        chip.at(1f)

        assertEquals(
            listOf(FeedbackIntent.Tick), recorder.intents,
            "two components 40ms apart fired ${recorder.intents}. Inside the floor " +
                "that is one rattle, and which of them produced it is the code's " +
                "business rather than the hand's.",
        )
    }

    @Test
    fun theSameTwoComponentsBothReportOnceTheFloorHasPassed() {
        // The other side of it, or the test above would pass against a floor that
        // simply never fired twice.
        val clock = TestTimeSource()
        val floor = FeedbackFloor(clock)
        val recorder = Recorder()
        val slider = DetentTicker(recorder, floor = floor)
        val chip = DetentTicker(recorder, FeedbackIntent.Tap, floor = floor)

        slider.at(0f)
        clock += 200.milliseconds
        slider.at(1f)
        chip.at(0f)
        clock += 100.milliseconds
        chip.at(1f)

        assertEquals(
            listOf(FeedbackIntent.Tick, FeedbackIntent.Tap), recorder.intents,
            "two components 100ms apart fired ${recorder.intents}, where the floor " +
                "is ${DetentTicker.MinimumTickInterval} and both should be through it.",
        )
    }

    @Test
    fun anOutcomeIsNeverHeldBackByALightHapticBeforeIt() {
        // The switch's midpoint goes through a ticker now, which is what makes
        // this more than theoretical: a `DragThreshold` that a slider's tick 10ms
        // earlier could swallow is a commit point the finger crosses in silence.
        val clock = TestTimeSource()
        val floor = FeedbackFloor(clock)
        val recorder = Recorder()
        val slider = DetentTicker(recorder, floor = floor)
        val switch = DetentTicker(recorder, FeedbackIntent.DragThreshold, floor = floor)

        slider.at(0f)
        clock += 200.milliseconds
        slider.at(1f)
        switch.at(0f)
        clock += 10.milliseconds
        switch.at(1f)

        assertEquals(
            listOf(FeedbackIntent.Tick, FeedbackIntent.DragThreshold), recorder.intents,
            "a threshold 10ms after a detent fired ${recorder.intents}. Outcomes " +
                "are not part of the stream the floor is thinning, and they do not " +
                "come in streams.",
        )
    }

    /**
     * The converse, reversed on purpose: an outcome holds back the stream that
     * follows it.
     *
     * It used to leave the clock alone, so a tick a frame after a wall's knock
     * fired — and a phone restarts its motor for each new vibration, so the
     * tick cut the knock short. The knob's last step behind its end stop, a
     * date range's day behind the month it had just paged: the outcome was the
     * report lost. Now the stream waits its usual gap after one.
     */
    @Test
    fun anOutcomeHoldsBackTheStreamBehindIt() {
        val clock = TestTimeSource()
        val floor = FeedbackFloor(clock)

        assertTrue(floor.claim(FeedbackIntent.Limit), "a wall was refused outright")
        clock += 10.milliseconds
        assertTrue(!floor.claim(FeedbackIntent.Tick), "a tick 10ms after a wall cut its knock short")
        assertTrue(floor.claim(FeedbackIntent.DragThreshold), "an outcome is never held back, even by another")
        clock += DetentTicker.MinimumTickInterval
        assertTrue(floor.claim(FeedbackIntent.Tick), "the stream resumes its gap after the last outcome")
    }

    /**
     * A toggle is a press answered and thinned like one; a resting place, a way
     * back and a wall are not in the stream, and never the report dropped.
     */
    @Test
    fun togglesAreThinnedAndSnapsBacksAndWallsAreNot() {
        val clock = TestTimeSource()
        val floor = FeedbackFloor(clock)
        assertTrue(floor.claim(FeedbackIntent.Tick))
        clock += 10.milliseconds
        assertTrue(!floor.claim(FeedbackIntent.ToggleOn), "a toggle 10ms after a tick is the same rattle")
        assertTrue(!floor.claim(FeedbackIntent.ToggleOff))
        assertTrue(floor.claim(FeedbackIntent.Snap), "a row's new slot was dropped")
        assertTrue(floor.claim(FeedbackIntent.DragThresholdBack), "a way back was dropped")
        assertTrue(floor.claim(FeedbackIntent.Limit), "an end stop was dropped")
        assertTrue(floor.claim(FeedbackIntent.Bump), "two thumbs meeting was dropped")
    }

    /**
     * A texture's grains may run closer than detents — that is how a fast drag
     * feels fast — and are still thinned, and still thin what comes after them.
     */
    @Test
    fun aGrainMayComeSoonerThanATickAndStillHoldsOneBack() {
        val clock = TestTimeSource()
        val floor = FeedbackFloor(clock)
        assertTrue(floor.claim(FeedbackIntent.Scrub))
        clock += 30.milliseconds
        assertTrue(!floor.claim(FeedbackIntent.Scrub), "grains 30ms apart are a buzz")
        clock += 25.milliseconds
        assertTrue(floor.claim(FeedbackIntent.Scrub), "grains 55ms apart were held to a detent's gap")
        clock += 55.milliseconds
        assertTrue(!floor.claim(FeedbackIntent.Tick), "a tick 55ms after a grain is the same rattle")
        assertTrue(floor.claim(FeedbackIntent.Bump))
    }
}
