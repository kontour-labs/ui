package io.kontour.ui.components.datetime

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.LocalTextStyle
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A countdown reads the time left off a clock: it says "in 1 min" the moment
 * there is less than two, and a phone that slept comes back to the time
 * that passed while it did, not to the ticks it slept through.
 */
@OptIn(ExperimentalTestApi::class)
class RelativeTimeTextTest {

    /** A wall clock the test moves, by hand, beside the frame clock. */
    private class FakeClock {
        var at = Instant.fromEpochSeconds(1_800_000_000)
    }

    private fun ComposeUiTest.countdown(until: Duration): FakeClock {
        val clock = FakeClock()
        mainClock.autoAdvance = false
        setContent {
            KontourTheme {
                CountingDown(
                    until = until,
                    modifier = Modifier,
                    style = LocalTextStyle.current,
                    colour = Color.Unspecified,
                    imminentThreshold = 30.seconds,
                    announce = true,
                    animateValue = false,
                    clock = { clock.at },
                )
            }
        }
        mainClock.advanceTimeByFrame()
        return clock
    }

    /** Both clocks on by [by], as they go together on a device that stays awake. */
    private fun ComposeUiTest.pass(clock: FakeClock, by: Duration) {
        clock.at += by
        mainClock.advanceTimeBy(by.inWholeMilliseconds)
    }

    @Test
    fun theLabelChangesAtTheMinuteNotAtTheNextTick() = runComposeUiTest {
        val clock = countdown(2.minutes + 5.seconds)
        onNodeWithText("in 2 min").assertExists()
        pass(clock, 4_900.milliseconds)
        onNodeWithText("in 2 min").assertExists()
        // 1:59.9 left is 2:00 to the second, which a countdown shows.
        pass(clock, 200.milliseconds)
        onNodeWithText("in 2 min").assertExists()
        // 1:58.9 left: "in 2 min" here is the rounding up the countdown
        // promises never to do. It ticked every twenty seconds, so it said so
        // for fifteen of them.
        pass(clock, 1.seconds)
        onNodeWithText("in 1 min").assertExists()
    }

    @Test
    fun aCountdownOfWholeMinutesReadsThemAsItAppears() = runComposeUiTest {
        val clock = countdown(4.minutes)
        // The time it takes to get on screen is not a minute gone.
        pass(clock, 300.milliseconds)
        onNodeWithText("in 4 min").assertExists()
        pass(clock, 1.seconds)
        onNodeWithText("in 3 min").assertExists()
    }

    @Test
    fun timeThePhoneSleptThroughIsCounted() = runComposeUiTest {
        val clock = countdown(15.minutes + 10.seconds)
        onNodeWithText("in 15 min").assertExists()
        // Ten minutes the wall clock saw and the delays did not: asleep, or
        // in the background.
        clock.at += 10.minutes
        pass(clock, 20.seconds)
        onNodeWithText("in 4 min").assertExists()
    }
}
