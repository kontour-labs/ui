package io.kontour.ui.components.datetime

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The date/time components' behaviour, tested without rendering.
 *
 * Everything here is logic a user would notice being wrong — a range that
 * selects backwards, a countdown that rounds a bus into the past, a calendar
 * whose first column is the wrong day.
 */
class RangeSelectionTest {

    private fun date(day: Int) = LocalDate(2026, 6, day)

    @Test
    fun noRangeWithoutAStart() {
        assertEquals(RangePosition.None, rangePosition(date(5), start = null, end = null))
    }

    @Test
    fun aLoneStartIsBothEnds() {
        // It has to render as a complete pill, not as a half-open cap pointing
        // at nothing.
        assertEquals(RangePosition.StartAndEnd, rangePosition(date(5), date(5), null))
        assertEquals(RangePosition.None, rangePosition(date(6), date(5), null))
    }

    @Test
    fun endpointsAndInteriorAreDistinguished() {
        val start = date(5)
        val end = date(9)
        assertEquals(RangePosition.Start, rangePosition(date(5), start, end))
        assertEquals(RangePosition.Middle, rangePosition(date(6), start, end))
        assertEquals(RangePosition.Middle, rangePosition(date(8), start, end))
        assertEquals(RangePosition.End, rangePosition(date(9), start, end))
    }

    @Test
    fun daysOutsideTheRangeAreUnmarked() {
        assertEquals(RangePosition.None, rangePosition(date(4), date(5), date(9)))
        assertEquals(RangePosition.None, rangePosition(date(10), date(5), date(9)))
    }

    @Test
    fun aSingleDayRangeIsBothEnds() {
        assertEquals(RangePosition.StartAndEnd, rangePosition(date(5), date(5), date(5)))
    }
}

class RelativeTimeFormatTest {

    private val threshold = 30.seconds

    @Test
    fun anythingInsideTheThresholdReadsAsNow() {
        assertEquals("now", formatRelative(0.seconds, threshold))
        assertEquals("now", formatRelative(29.seconds, threshold))
        assertEquals("now", formatRelative((-29).seconds, threshold))
    }

    @Test
    fun minutesRoundDownNotToNearest() {
        // Rounding 90s up to "2 min" is what makes someone miss their bus.
        assertEquals("in 1 min", formatRelative(90.seconds, threshold))
        assertEquals("in 1 min", formatRelative(119.seconds, threshold))
        assertEquals("in 2 min", formatRelative(2.minutes, threshold))
    }

    @Test
    fun subMinuteButPastTheThresholdStillReadsAsAMinute() {
        // Never "in 0 min" — that is not a thing a departure board says.
        assertEquals("in 1 min", formatRelative(45.seconds, threshold))
    }

    @Test
    fun escalatesToHoursThenDays() {
        assertEquals("in 59 min", formatRelative(59.minutes, threshold))
        assertEquals("in 1 hr", formatRelative(60.minutes, threshold))
        assertEquals("in 23 hr", formatRelative(23.hours, threshold))
        assertEquals("in 1 d", formatRelative(24.hours, threshold))
        assertEquals("in 3 d", formatRelative(3.days, threshold))
    }

    @Test
    fun pastTimesReadAsAgo() {
        assertEquals("2 min ago", formatRelative((-2).minutes, threshold))
        assertEquals("1 hr ago", formatRelative((-1).hours, threshold))
    }
}

class DateTimeFormatsTest {

    @Test
    fun twentyFourHourTimePadsTheHour() {
        val formats = DateTimeFormats(is24Hour = true)
        assertEquals("09:05", formats.time(LocalTime(9, 5)))
        assertEquals("00:00", formats.time(LocalTime(0, 0)))
        assertEquals("23:59", formats.time(LocalTime(23, 59)))
    }

    @Test
    fun twelveHourTimeHandlesBothMidnightAndNoon() {
        // The two values every 12-hour implementation gets wrong.
        val formats = DateTimeFormats(is24Hour = false)
        assertEquals("12:00 am", formats.time(LocalTime(0, 0)))
        assertEquals("12:00 pm", formats.time(LocalTime(12, 0)))
        assertEquals("1:05 pm", formats.time(LocalTime(13, 5)))
        assertEquals("11:59 pm", formats.time(LocalTime(23, 59)))
    }

    @Test
    fun dateOrderFollowsThePreference() {
        val date = LocalDate(2026, 6, 5)
        assertEquals("5 Jun 2026", DateTimeFormats(dayFirst = true).dateShort(date))
        assertEquals("Jun 5, 2026", DateTimeFormats(dayFirst = false).dateShort(date))
    }

    @Test
    fun weekStartsWhereThePreferenceSaysItDoes() {
        val monday = DateTimeFormats(firstDayOfWeek = DayOfWeek.MONDAY)
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), monday.weekdayInitials())
        assertEquals(0, monday.columnOf(DayOfWeek.MONDAY))
        assertEquals(6, monday.columnOf(DayOfWeek.SUNDAY))

        val sunday = DateTimeFormats(firstDayOfWeek = DayOfWeek.SUNDAY)
        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), sunday.weekdayInitials())
        assertEquals(0, sunday.columnOf(DayOfWeek.SUNDAY))
        assertEquals(1, sunday.columnOf(DayOfWeek.MONDAY))
    }
}
