package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * *"the day view shouldn't have a condensed option, instead we should have more
 * of a 'schedule' view which you can scroll through to see events you have
 * upcoming on days"* — the busy days only, today always, and "Nothing planned"
 * under it when it has nothing on.
 */
@OptIn(ExperimentalTestApi::class)
class ScheduleViewTest {

    private val today = LocalDate(2026, 10, 14)

    private fun at(month: Int, day: Int, hour: Int) = LocalDateTime(2026, month, day, hour, 0)

    private val review = CalendarEvent(1, "Design review", at(10, 16, 10), at(10, 16, 11))
    private val standUp = CalendarEvent(2, "Stand-up", at(10, 16, 9), at(10, 16, 9))
    private val trip = CalendarEvent(3, "Perth trip", startDate = LocalDate(2026, 10, 20), endDate = LocalDate(2026, 10, 22))
    private val dentist = CalendarEvent(4, "Dentist", at(11, 3, 15), at(11, 3, 16))
    private val events = listOf(review, standUp, trip, dentist)

    // --- Its rows ---

    @Test
    fun onlyTheBusyDaysAndTodayAreListedUnderTheirMonths() {
        val items = scheduleItems(events, LocalDate(2026, 10, 1), LocalDate(2026, 11, 30), today)
        val days = items.filterIsInstance<ScheduleDay>().map { it.date.day to it.date.month.ordinal + 1 }
        assertEquals(listOf(14 to 10, 16 to 10, 20 to 10, 21 to 10, 22 to 10, 3 to 11), days)
        assertEquals(listOf(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1)), items.filterIsInstance<ScheduleMonth>().map { it.first })
        assertTrue(items.first() is ScheduleMonth && items[items.indexOfFirst { it is ScheduleDay && it.date.month.ordinal == 10 } - 1] is ScheduleMonth)
        assertTrue((items[1] as ScheduleDay).events.isEmpty(), "today, with nothing on, has no events")
    }

    @Test
    fun aDaysEventsAreItsLongerOnesFirstThenByWhenTheyStart() {
        val allDay = CalendarEvent(5, "Holiday", startDate = LocalDate(2026, 10, 16))
        val day = scheduleItems(events + allDay, LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), null)
            .filterIsInstance<ScheduleDay>().first { it.date == LocalDate(2026, 10, 16) }
        assertEquals(listOf("Holiday", "Stand-up", "Design review"), day.events.map { it.title })
    }

    /**
     * A timed event of more than a day is its times on each day it is on, not
     * "All day" on every one: from its start on the first, all day between,
     * and until its end on the last.
     */
    @Test
    fun aTimedEventOverSeveralDaysIsItsPartOfEachDay() {
        val formats = LocaleFormats.english()
        val strings = Strings()
        val conference = CalendarEvent(6, "Conference", at(10, 12, 9), at(10, 14, 17))
        fun range(from: Int, to: Int) = strings.timeRange(formats.time(LocalTime(from, 0)), formats.time(LocalTime(to, 0)))
        assertEquals(range(9, 0), scheduleTime(conference, LocalDate(2026, 10, 12), formats, strings))
        assertEquals(strings.allDay, scheduleTime(conference, LocalDate(2026, 10, 13), formats, strings))
        assertEquals(range(0, 17), scheduleTime(conference, LocalDate(2026, 10, 14), formats, strings))
        // Those that were right stay so: an all-day event, and one inside a day.
        assertEquals(strings.allDay, scheduleTime(trip, LocalDate(2026, 10, 21), formats, strings))
        assertEquals(range(10, 11), scheduleTime(review, LocalDate(2026, 10, 16), formats, strings))
    }

    @Test
    fun aDayIsFoundAsItsRowOrTheNextBusyOne() {
        val items = scheduleItems(events, LocalDate(2026, 10, 1), LocalDate(2026, 11, 30), today)
        assertEquals(LocalDate(2026, 10, 20), (items[scheduleRowFor(items, LocalDate(2026, 10, 18))] as ScheduleDay).date)
        // The first of a month is its heading.
        assertTrue(items[scheduleRowFor(items, LocalDate(2026, 11, 1))] is ScheduleMonth)
    }

    // --- In the calendar ---

    private class Probe {
        lateinit var state: EventCalendarState
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        val clicked = mutableListOf<CalendarEvent>()
    }

    private fun ComposeUiTest.schedule(list: List<CalendarEvent> = events): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.size(400.dp, 700.dp)) {
                    probe.formats = Theme.formats
                    probe.strings = Theme.strings
                    probe.state = rememberEventCalendarState(today, CalendarView.Schedule, LocalTime(8, 0), DayOfWeek.MONDAY)
                    EventCalendar(
                        state = probe.state,
                        events = list,
                        now = LocalDateTime(today, LocalTime(11, 0)),
                        onEventClick = { probe.clicked += it },
                        modifier = Modifier.size(400.dp, 700.dp),
                    )
                }
            }
        }
        waitForIdle()
        return probe
    }

    @Test
    fun itOpensAtTodaySayingNothingIsPlanned() = runComposeUiTest {
        val probe = schedule()
        onNode(hasText(probe.strings.nothingPlanned), useUnmergedTree = true).assertExists()
        // The title, and the month's heading over its days.
        assertEquals(2, onAllNodes(hasText(probe.formats.monthAndYear(today))).fetchSemanticsNodes().size)
    }

    @Test
    fun anEventPressedIsReported() = runComposeUiTest {
        val probe = schedule()
        onNode(hasContentDescription(eventDescription(review, probe.formats, probe.strings))).performClick()
        waitForIdle()
        assertEquals(listOf(review), probe.clicked)
    }

    @Test
    fun theHeadersNextGoesToTheNextMonthsFirstBusyDay() = runComposeUiTest {
        val probe = schedule()
        onNode(hasContentDescription(probe.strings.nextMonth)).performClick()
        waitForIdle()
        assertEquals(LocalDate(2026, 11, 1), probe.state.date)
        onNode(hasContentDescription(eventDescription(dentist, probe.formats, probe.strings))).assertExists()
    }

    @Test
    fun scrolledOnTheTitleFollows() = runComposeUiTest {
        // A month of busy days, so there is a month to scroll through.
        val many = (1..30).map { CalendarEvent(100L + it, "Thing $it", at(10, it, 9), at(10, it, 10)) } +
            (1..30).map { CalendarEvent(200L + it, "Other $it", at(11, it, 9), at(11, it, 10)) }
        val probe = schedule(many)
        repeat(6) { onRoot().performTouchInput { swipeUp() }; waitForIdle() }
        assertTrue(probe.state.date > LocalDate(2026, 10, 20), "scrolled on, the state is still about ${probe.state.date}")
    }

    @Test
    fun theScheduleIsInTheSwitcher() = runComposeUiTest {
        val probe = schedule()
        assertTrue(CalendarView.Schedule in EventCalendarDefaults.Views)
        assertEquals(CalendarView.Schedule, probe.state.view)
        assertEquals(probe.strings.schedule, "Schedule")
    }
}
