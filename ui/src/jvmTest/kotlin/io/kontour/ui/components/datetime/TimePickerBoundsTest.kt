package io.kontour.ui.components.datetime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.locale.HourCycle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A time picker's wheels stop at `earliest` and `latest` rather than being
 * turned past them and set back.
 *
 * Asked for by Anyways' planner for 0.5.6: *leave at* cannot be before now, and
 * *arrive by* cannot be sooner than now and the quickest way there. The app let
 * the wheel stop on 9:10 and then set 9:15, and the drum jumped there, so it
 * looked as though the wheel had turned only part of the way. Each wheel now
 * has only the rows the bounds allow, read here as a screen reader is handed
 * them: a range of rows and the one in the band.
 */
@OptIn(ExperimentalTestApi::class)
class TimePickerBoundsTest {

    private val english = LocaleFormats.english()
    private val twelve = english.withConventions(english.conventions.copy(hourCycle = HourCycle.H12))

    private fun ComposeUiTest.rows(wheel: String): Int {
        val info = onNodeWithContentDescription(wheel).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        return info.range.endInclusive.toInt() + 1
    }

    private fun ComposeUiTest.at(wheel: String): ProgressBarRangeInfo =
        onNodeWithContentDescription(wheel).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]

    @Test
    fun theBoundsAreOnTheMinuteSteps() {
        // A second into 9:07 is after it.
        assertEquals(LocalTime(9, 8), TimeBounds(LocalTime(9, 7, 33), null, 1).first)
        assertEquals(LocalTime(9, 10), TimeBounds(LocalTime(9, 7), null, 5).first)
        assertEquals(LocalTime(9, 5), TimeBounds(LocalTime(9, 5), null, 5).first)
        // Past the day's last step, the last step.
        assertEquals(LocalTime(23, 55), TimeBounds(LocalTime(23, 58), null, 5).first)
        assertEquals(LocalTime(17, 40), TimeBounds(null, LocalTime(17, 43), 5).last)
        // A latest before the earliest leaves only the earliest.
        assertEquals(LocalTime(10, 0), TimeBounds(LocalTime(10, 0), LocalTime(9, 0), 5).last)
    }

    @Test
    fun theWheelsStartAtTheEarliest() = runComposeUiTest {
        var value by mutableStateOf(LocalTime(8, 0))
        setContent {
            KontourTheme {
                TimePicker(
                    value = value,
                    onValueChange = { value = it },
                    minuteStep = 5,
                    earliest = LocalTime(9, 7),
                    now = null,
                    formats = english,
                )
            }
        }
        waitForIdle()
        assertEquals(LocalTime(9, 10), value, "a value before the earliest was not moved forward to it")
        assertEquals(15, rows("Hour"), "the hours should be 9 to 23")
        assertEquals(10, rows("Minute"), "on the earliest's hour the minutes should be :10 to :55")
        assertEquals(0f, at("Hour").current, "the hour wheel should be at its first row, 9")

        // Past the earliest's hour the minutes open up again.
        value = LocalTime(10, 20)
        waitForIdle()
        assertEquals(12, rows("Minute"), "past the earliest's hour the minutes should be whole again")
        assertEquals(4f, at("Minute").current, ":20 should be the fifth row of :00 to :55")
    }

    @Test
    fun aFlickTowardsEarlierStopsAtTheEarliest() = runComposeUiTest {
        var value by mutableStateOf(LocalTime(14, 30))
        val seen = mutableListOf<LocalTime>()
        setContent {
            KontourTheme {
                TimePicker(
                    value = value,
                    onValueChange = { value = it; seen += it },
                    minuteStep = 5,
                    earliest = LocalTime(9, 10),
                    now = null,
                    formats = english,
                )
            }
        }
        waitForIdle()
        // Down the wheel is back through the hours, hard.
        onNodeWithContentDescription("Hour").performTouchInput { swipeDown(durationMillis = 80) }
        mainClock.advanceTimeBy(3_000)
        waitForIdle()
        assertTrue(seen.isNotEmpty(), "the flick turned nothing, so this measured nothing")
        assertTrue(seen.none { it < LocalTime(9, 10) }, "a turned wheel passed the earliest: ${seen.filter { it < LocalTime(9, 10) }}")
        assertTrue(value.hour < 14, "the flick did not turn the hours back at all: $value")
    }

    @Test
    fun aTwelveHourClockOffersOnlyTheHalfOfTheDayTheBoundsReach() = runComposeUiTest {
        var value by mutableStateOf(LocalTime(15, 0))
        setContent {
            KontourTheme {
                TimePicker(
                    value = value,
                    onValueChange = { value = it },
                    earliest = LocalTime(14, 0),
                    now = null,
                    formats = twelve,
                )
            }
        }
        waitForIdle()
        assertEquals(1, rows("AM or PM"), "a picker that cannot be before 2 PM has no morning to offer")
        // 2 PM to 11 PM.
        assertEquals(10, rows("Hour"), "the afternoon's hours should be 2 to 11")
        assertEquals(1f, at("Hour").current, "3 PM should be the second row, after 2")
    }

    @Test
    fun theWheelsStopAtTheLatest() = runComposeUiTest {
        var value by mutableStateOf(LocalTime(17, 43))
        setContent {
            KontourTheme {
                TimePicker(
                    value = value,
                    onValueChange = { value = it },
                    minuteStep = 5,
                    latest = LocalTime(17, 43),
                    now = null,
                    formats = english,
                )
            }
        }
        waitForIdle()
        assertEquals(LocalTime(17, 40), value, "a value after the latest was not moved back to it")
        assertEquals(18, rows("Hour"), "the hours should be 0 to 17")
        assertEquals(9, rows("Minute"), "on the latest's hour the minutes should be :00 to :40")
    }

    @Test
    fun aDateTimeBeforeTheEarliestMovesToIt() = runComposeUiTest {
        var value by mutableStateOf(LocalDateTime(LocalDate(2026, 11, 20), LocalTime(8, 0)))
        val earliest = LocalDateTime(LocalDate(2026, 11, 21), LocalTime(9, 7))
        setContent {
            KontourTheme {
                OverlayHost {
                    DateTimePicker(
                        value = value,
                        onValueChange = { value = it },
                        minuteStep = 5,
                        earliest = earliest,
                        now = null,
                        formats = english,
                    )
                }
            }
        }
        waitForIdle()
        assertEquals(LocalDateTime(LocalDate(2026, 11, 21), LocalTime(9, 10)), value, "a date and time before the earliest was not moved to it")
    }
}
