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

    private companion object {
        val Warning = 2.seconds

        /** Long enough for the digit roll to finish once the value lands. */
        val Settle = 2_000L
    }
}
