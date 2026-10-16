package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * A time picker's Now: asked for as a button that *scrolls the picker to now*,
 * where *i don't just want the picker to snap, i want to see it scroll*.
 */
@OptIn(ExperimentalTestApi::class)
class TimePickerNowTest {

    private class Probe {
        val seen = mutableListOf<LocalTime>()
        var finished = 0
        var now = ""
    }

    private fun ComposeUiTest.picker(reduceMotion: Boolean = false, now: (() -> LocalTime)? = { LocalTime(14, 32) }): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = reduceMotion) {
                probe.now = Theme.strings.now
                var value by remember { mutableStateOf(LocalTime(8, 15)) }
                TimePicker(
                    value = value,
                    onValueChange = { value = it; probe.seen += it },
                    minuteStep = 5,
                    now = now,
                    onValueChangeFinished = { probe.finished++ },
                )
            }
        }
        waitForIdle()
        return probe
    }

    @Test
    fun nowScrollsTheWheelsThroughToTheTimeRounded() = runComposeUiTest {
        val probe = picker()
        onNode(hasText(probe.now) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(LocalTime(14, 30), probe.seen.lastOrNull(), "Now did not land on the time now, to the nearest five minutes")
        // Scrolled there, row by row, not set at once.
        val passed = probe.seen.dropLast(1).distinct().size
        assertTrue(passed >= 3, "the wheels jumped to Now rather than scrolling there: ${probe.seen}")
        assertEquals(1, probe.finished, "the wheels reaching Now should finish once")
    }

    @Test
    fun underReducedMotionNowLandsAtOnce() = runComposeUiTest {
        val probe = picker(reduceMotion = true)
        onNode(hasText(probe.now) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(LocalTime(14, 30), probe.seen.lastOrNull())
    }

    @Test
    fun withNoNowThereIsNoButton() = runComposeUiTest {
        val probe = picker(now = null)
        onNode(hasText(probe.now) and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun aDateAndTimePickersNowTakesTodayToo() = runComposeUiTest {
        var value by mutableStateOf(LocalDateTime(2026, 6, 10, 18, 0))
        var now = ""
        setContent {
            KontourTheme(reduceMotion = true) {
                now = Theme.strings.now
                DateTimePicker(
                    value = value,
                    onValueChange = { value = it },
                    minuteStep = 5,
                    now = { LocalDateTime(2026, 6, 13, 9, 2) },
                    modifier = Modifier.width(900.dp),
                )
            }
        }
        waitForIdle()
        onNode(hasText(now) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(LocalDateTime(2026, 6, 13, 9, 0), value)
    }

    /** Pressed again on its way, Now says it has arrived once, not once a press. */
    @Test
    fun nowPressedTwiceFinishesOnce() = runComposeUiTest {
        val probe = picker()
        mainClock.autoAdvance = false
        onNode(hasText(probe.now) and hasClickAction()).performClick()
        repeat(3) { mainClock.advanceTimeByFrame() }
        onNode(hasText(probe.now) and hasClickAction()).performClick()
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(LocalTime(14, 30), probe.seen.lastOrNull())
        assertEquals(1, probe.finished, "a press taken over by another still said it had finished")
    }

    /** Today, where the calendar would refuse it, is not where Now takes the day. */
    @Test
    fun aDateAndTimePickersNowKeepsADayItCannotTake() = runComposeUiTest {
        var value by mutableStateOf(LocalDateTime(2026, 6, 10, 18, 0))
        var now = ""
        setContent {
            KontourTheme(reduceMotion = true) {
                now = Theme.strings.now
                DateTimePicker(
                    value = value,
                    onValueChange = { value = it },
                    minuteStep = 5,
                    now = { LocalDateTime(2026, 6, 13, 9, 2) },
                    isDateSelectable = { it != LocalDate(2026, 6, 13) },
                    modifier = Modifier.width(900.dp),
                )
            }
        }
        waitForIdle()
        onNode(hasText(now) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(LocalDate(2026, 6, 10), value.date, "Now took the day to one the calendar refuses")
        assertEquals(LocalTime(9, 0), value.time)
    }

    /**
     * Now at 23:58 by fives rounds to midnight, tomorrow's; where tomorrow is
     * refused it is the last step of today, not midnight on the day before.
     */
    @Test
    fun aDateAndTimePickersNowThatCannotTakeTomorrowStopsAtTheEndOfToday() = runComposeUiTest {
        var value by mutableStateOf(LocalDateTime(2026, 6, 10, 18, 0))
        var now = ""
        setContent {
            KontourTheme(reduceMotion = true) {
                now = Theme.strings.now
                DateTimePicker(
                    value = value,
                    onValueChange = { value = it },
                    minuteStep = 5,
                    now = { LocalDateTime(2026, 6, 13, 23, 58) },
                    isDateSelectable = { it != LocalDate(2026, 6, 14) },
                    modifier = Modifier.width(900.dp),
                )
            }
        }
        waitForIdle()
        onNode(hasText(now) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(LocalDateTime(2026, 6, 13, 23, 55), value, "Now went a day back from the time now")
    }

    @Test
    fun roundingPastMidnightIsTomorrow() {
        assertEquals(LocalTime(0, 0) to true, nearestStep(LocalTime(23, 58), 5))
        assertEquals(LocalTime(10, 0) to false, nearestStep(LocalTime(9, 58), 5))
        assertEquals(LocalTime(9, 55) to false, nearestStep(LocalTime(9, 57), 5))
        assertEquals(LocalTime(9, 2) to false, nearestStep(LocalTime(9, 2), 1))
        assertEquals(LocalDate(2026, 1, 1), LocalDate(2026, 1, 1))
    }
}
