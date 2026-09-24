package io.kontour.ui.components.display

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A fall is held and announced; a rise is not.
 *
 * The counter cannot see the future, so `warnBefore` makes one: a decrease is
 * held for that long, wiggled at the end of it, and only then rolled. The two
 * halves worth pinning here are that the *drawn* number really does lag —
 * otherwise there is nothing to warn about — and that an increase is untouched,
 * because a number going up is good news and delaying it would be a bug wearing
 * a feature.
 */
@OptIn(ExperimentalTestApi::class)
class AnimatedCounterWarningTest {

    @Test
    fun aFallIsHeldForTheWarningAndARiseIsNot() = runComposeUiTest {
        var seats by mutableIntStateOf(12)

        setContent {
            KontourTheme(reduceMotion = false) {
                AnimatedCounter(value = seats, warnBefore = Warning)
            }
        }
        mainClock.autoAdvance = false
        onNodeWithContentDescription("12").assertIsDisplayed()

        // Down: still showing the old number part-way through the warning.
        seats = 9
        mainClock.advanceTimeBy(Warning.inWholeMilliseconds / 2)
        onNodeWithContentDescription("12").assertIsDisplayed()

        // And through it.
        mainClock.advanceTimeBy(Warning.inWholeMilliseconds)
        mainClock.advanceTimeBy(Settle)
        onNodeWithContentDescription("9").assertIsDisplayed()

        // Up: no hold at all.
        seats = 40
        mainClock.advanceTimeBy(Settle)
        onNodeWithContentDescription("40").assertIsDisplayed()
    }

    @Test
    fun withoutAWarningAFallIsImmediate() = runComposeUiTest {
        var seats by mutableIntStateOf(12)

        setContent { KontourTheme(reduceMotion = false) { AnimatedCounter(value = seats) } }
        mainClock.autoAdvance = false

        seats = 9
        mainClock.advanceTimeBy(Settle)
        onNodeWithContentDescription("9").assertIsDisplayed()
    }

    /**
     * Drops faster than the warning can finish still land, and the counter keeps
     * warning afterwards.
     *
     * Reported from the catalog: tapping "Tick down" repeatedly with the warning
     * on stopped the counter working altogether. A second drop cancelled the hold
     * part-way through its delay, which left it believing a warning was still
     * running, and every later drop returned early on that belief — so the drawn
     * number froze and never wiggled again.
     */
    @Test
    fun spammedDropsLandOnTheLastValueAndTheNextDropStillWarns() = runComposeUiTest {
        var seats by mutableIntStateOf(12)

        setContent {
            KontourTheme(reduceMotion = false) {
                AnimatedCounter(value = seats, warnBefore = Brief)
            }
        }
        mainClock.autoAdvance = false

        // Nine drops, 50ms apart: every one lands inside the previous warning.
        repeat(9) {
            seats -= 1
            mainClock.advanceTimeBy(50)
        }
        mainClock.advanceTimeBy(Brief.inWholeMilliseconds)
        mainClock.advanceTimeBy(Settle)
        onNodeWithContentDescription("3").assertIsDisplayed()

        // And it still works: the next drop is held, then lands.
        seats = 2
        mainClock.advanceTimeBy(Brief.inWholeMilliseconds / 2)
        onNodeWithContentDescription("3").assertIsDisplayed()
        mainClock.advanceTimeBy(Brief.inWholeMilliseconds)
        mainClock.advanceTimeBy(Settle)
        onNodeWithContentDescription("2").assertIsDisplayed()
    }

    /** A rise during a warning is good news, and does not wait for it to finish. */
    @Test
    fun aRiseDuringAWarningLandsAtOnce() = runComposeUiTest {
        var seats by mutableIntStateOf(12)

        setContent {
            KontourTheme(reduceMotion = false) {
                AnimatedCounter(value = seats, warnBefore = Warning)
            }
        }
        mainClock.autoAdvance = false

        seats = 9
        mainClock.advanceTimeBy(100)
        seats = 30
        mainClock.advanceTimeBy(Settle)
        onNodeWithContentDescription("30").assertIsDisplayed()
    }

    private companion object {
        /** The catalog's own warning: two there-and-backs. */
        val Brief = 450.milliseconds

        val Warning = 2.seconds

        /** Long enough for the digit roll to finish once the value lands. */
        val Settle = 2_000L
    }
}
