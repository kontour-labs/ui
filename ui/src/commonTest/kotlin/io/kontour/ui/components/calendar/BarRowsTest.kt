package io.kontour.ui.components.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/**
 * Each bar's row is the same on every page and week it is in, and no deeper
 * than the bars going on on its first day make it: see [barRows].
 */
class BarRowsTest {

    private fun day(n: Int) = LocalDate(2026, 10, n)

    private fun bar(id: Int, from: Int, to: Int = from) = CalendarEvent(id, "Bar $id", startDate = day(from), endDate = day(to))

    /** [event]'s row on the page of [days] from the [from]th, as the page packs it. */
    private fun rowOn(event: CalendarEvent, events: List<CalendarEvent>, from: Int, days: Int, maxRows: Int = 3): Int? {
        val start = day(from)
        val end = start.plus(days - 1, DateTimeUnit.DAY)
        val shown = events.filter { it.overlaps(start, end) }
        val pack = packWeek(weekItems(start, shown, lines = false, days = days), maxRows, days, barRows(events, lines = false))
        return pack.placed.firstOrNull { it.item.event == event }?.row
    }

    @Test
    fun aBarRunningOnIsOnItsRowOnEveryPage() {
        val conference = bar(1, 12, 14)
        val perth = bar(2, 13, 16)
        val trip = bar(3, 14, 18)
        val events = listOf(conference, perth, trip)
        val rows = (14..18).map { rowOn(trip, events, it, days = 1) }
        assertEquals(List(5) { 2 }, rows, "a day at a time, the trip should be on its third row every day")
        assertEquals(listOf(2, 2), listOf(rowOn(trip, events, 14, days = 3), rowOn(trip, events, 17, days = 3)))
    }

    @Test
    fun aDaysBarFillsARowALongerOneHasLeft() {
        val conference = bar(1, 12, 13)
        val trip = bar(2, 13, 18)
        val birthday = bar(3, 15)
        val rows = barRows(listOf(conference, trip, birthday), lines = false)
        assertEquals(0, rows[conference])
        assertEquals(1, rows[trip])
        assertEquals(0, rows[birthday], "the birthday is on a day the conference has left, under nothing")
    }

    @Test
    fun theRowsStartAgainAfterADayWithNoBar() {
        val a = bar(1, 12, 13)
        val b = bar(2, 13)
        val c = bar(3, 15)
        val rows = barRows(listOf(a, b, c), lines = false)
        assertEquals(1, rows[b])
        assertEquals(0, rows[c])
    }

    @Test
    fun theStripHasOnlyWhatIsNotInTheHoursAndTheMonthAlsoANightAcrossMidnight() {
        val flight = CalendarEvent(1, "Flight", LocalDateTime(2026, 10, 14, 22, 0), LocalDateTime(2026, 10, 15, 6, 0))
        val trip = bar(2, 15, 16)
        assertEquals(null, barRows(listOf(flight, trip), lines = false)[flight])
        assertEquals(0, barRows(listOf(flight, trip), lines = false)[trip])
        assertEquals(0, barRows(listOf(flight, trip), lines = true)[flight])
        assertEquals(1, barRows(listOf(flight, trip), lines = true)[trip])
    }

    @Test
    fun aBarPastWhatADayShowsGoesUpRatherThanUnderAPlus() {
        val events = listOf(bar(1, 12, 14), bar(2, 12, 14), bar(3, 13, 16))
        // Third on the 13th, and alone from the 15th: on its own row there it
        // would be cut by a box of two.
        assertEquals(2, barRows(events, lines = false)[events[2]])
        assertEquals(0, rowOn(events[2], events, 15, days = 1, maxRows = 2))
        assertEquals(2, rowOn(events[2], events, 15, days = 1, maxRows = 3))
    }

    @Test
    fun copiesOfOneEventEachHaveARow() {
        val trip = bar(1, 13, 15)
        val copy = bar(1, 13, 15)
        val pack = packWeek(weekItems(day(13), listOf(trip, copy), lines = false, days = 3), 3, 3, barRows(listOf(trip, copy), lines = false))
        assertEquals(listOf(0, 1), pack.placed.map { it.row }.sorted())
    }
}
