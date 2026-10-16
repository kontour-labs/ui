package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import io.kontour.ui.locale.DateStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
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
     * A timed event that runs into a later day reads as flights and shifts
     * write one: its own times on the day it starts, the end raised by how many
     * days on it is — "09:00 – 17:00⁺²" — all day between, and "Until 17:00"
     * on the last. Asked for as *"with the label, could we incorporate the
     * fairly standard superscript '+1' notation?"*: its first day read
     * "09:00 – 00:00", and a night flight "22:30 – 06:15" on both its days.
     */
    @Test
    fun aTimedEventIntoALaterDayHasItsEndRaisedByTheDaysOn() {
        val formats = LocaleFormats.english()
        val strings = Strings()
        fun time(hour: Int, minute: Int = 0) = formats.time(LocalTime(hour, minute))
        fun on(event: CalendarEvent, month: Int, day: Int) = scheduleTime(event, LocalDate(2026, month, day), formats, strings)

        val conference = CalendarEvent(6, "Conference", at(10, 12, 9), at(10, 14, 17))
        val first = on(conference, 10, 12)
        assertEquals(strings.timeRange(time(9), time(17)) + "+2", first.text)
        assertRaised(first, "+2")
        assertEquals(strings.allDay, on(conference, 10, 13).text)
        assertEquals("Until ${time(17)}", on(conference, 10, 14).text)

        // Shorter than a day, but into the next: the "+1" is what says so.
        val flight = CalendarEvent(7, "Flight", LocalDateTime(2026, 10, 12, 22, 30), LocalDateTime(2026, 10, 13, 6, 15))
        val night = on(flight, 10, 12)
        assertEquals(strings.timeRange(time(22, 30), time(6, 15)) + "+1", night.text)
        assertRaised(night, "+1")
        assertEquals("Until ${time(6, 15)}", on(flight, 10, 13).text)

        // Those inside a day, and all-day ones, are as they were, with nothing raised.
        assertEquals(strings.allDay, on(trip, 10, 21).text)
        assertEquals(strings.timeRange(time(10), time(11)), on(review, 10, 16).text)
        assertTrue(on(review, 10, 16).spanStyles.isEmpty())
    }

    /**
     * An end at midnight is the end of the day before it: an evening until
     * midnight is "18:00 – 00:00", on its one day, with no "+1" pointing at a
     * day it has no part of. A later midnight is its own day's, so the number
     * is still the end's date.
     */
    @Test
    fun anEndAtMidnightClosesTheDayBefore() {
        val formats = LocaleFormats.english()
        val strings = Strings()
        fun time(hour: Int) = formats.time(LocalTime(hour, 0))
        val evening = CalendarEvent(8, "Party", at(10, 12, 18), at(10, 13, 0))
        val party = scheduleTime(evening, LocalDate(2026, 10, 12), formats, strings)
        assertEquals(strings.timeRange(time(18), time(0)), party.text)
        assertTrue(party.spanStyles.isEmpty(), "an evening until midnight has something raised: ${party.spanStyles}")
        val days = scheduleItems(listOf(evening), LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), null).filterIsInstance<ScheduleDay>()
        assertEquals(listOf(LocalDate(2026, 10, 12)), days.map { it.date })

        val hackathon = CalendarEvent(9, "Hackathon", at(10, 12, 9), at(10, 14, 0))
        assertEquals(strings.timeRange(time(9), time(0)) + "+2", scheduleTime(hackathon, LocalDate(2026, 10, 12), formats, strings).text)
        assertEquals(strings.allDay, scheduleTime(hackathon, LocalDate(2026, 10, 13), formats, strings).text)
    }

    /**
     * The "+2" is the reader's: its number in their digits and its sign in
     * their words, through `Strings`, as every other count is.
     */
    @Test
    fun theRaisedDaysAreTheReadersOwnWords() {
        val formats = LocaleFormats.english()
        val strings = Strings(endDayOffset = { days -> "(+${days.formatted} d)" })
        val conference = CalendarEvent(6, "Conference", at(10, 12, 9), at(10, 14, 17))
        val first = scheduleTime(conference, LocalDate(2026, 10, 12), formats, strings)
        assertTrue(first.text.endsWith("(+2 d)"), first.text)
        assertRaised(first, "(+2 d)")
    }

    /**
     * A screen reader is not told "plus two": the event's times say the later
     * day in words — "09:00 – 17:00, 2 days later", "06:15 the next day" —
     * where they said "09:00 – 17:00" as though it were nine to five each day.
     */
    @Test
    fun aScreenReaderIsToldTheLaterDayInWords() {
        val formats = LocaleFormats.english()
        val strings = Strings()
        fun time(hour: Int, minute: Int = 0) = formats.time(LocalTime(hour, minute))
        val conference = CalendarEvent(6, "Conference", at(10, 12, 9), at(10, 14, 17))
        val described = eventDescription(conference, formats, strings)
        assertTrue("${time(9)} – ${time(17)}, 2 days later" in described, described)
        assertTrue("+" !in described, described)
        val flight = CalendarEvent(7, "Flight", LocalDateTime(2026, 10, 12, 22, 30), LocalDateTime(2026, 10, 13, 6, 15))
        val flown = eventDescription(flight, formats, strings)
        assertTrue("${time(22, 30)} – ${time(6, 15)} the next day" in flown, flown)
        val evening = CalendarEvent(8, "Party", at(10, 12, 18), at(10, 13, 0))
        val party = eventDescription(evening, formats, strings)
        assertTrue("${time(18)} – ${time(0)}" in party && "later" !in party && "next day" !in party, party)
    }

    /** [text] ends in [raised], set smaller and above the line. */
    private fun assertRaised(text: androidx.compose.ui.text.AnnotatedString, raised: String) {
        val from = text.text.lastIndexOf(raised)
        assertTrue(from >= 0, "\"$raised\" is not in \"${text.text}\"")
        val span = text.spanStyles.singleOrNull { it.start == from && it.end == from + raised.length }
        assertTrue(span != null, "\"$raised\" is not set apart in \"${text.text}\": ${text.spanStyles}")
        val shift = span.item.baselineShift
        assertTrue(shift != null && shift.multiplier > 0f, "\"$raised\" is not raised: $shift")
        val size = span.item.fontSize
        assertTrue(size.isEm && size.value < 1f, "\"$raised\" is not smaller than its line: $size")
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

    private fun ComposeUiTest.schedule(list: List<CalendarEvent> = events, fontScale: Float = 1f): Probe {
        val probe = Probe()
        setContent {
            val density = LocalDensity.current
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
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
        }
        waitForIdle()
        return probe
    }

    /**
     * A day is a button named by its date, whose action is showing it. The date
     * was the action's label, so the button had no name and said a date as
     * what pressing it does.
     */
    @Test
    fun aDayIsNamedByItsDateAndShowsTheDay() = runComposeUiTest {
        val probe = schedule()
        val day = onNode(hasContentDescription(probe.formats.date(today, DateStyle.Full)))
        day.assertExists()
        assertEquals(probe.strings.showDay, day.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
    }

    /** At 200% type a day's number has the room its line needs, where its 32dp ring cut it. */
    @Test
    fun aDaysNumberFitsAtLargeType() = runComposeUiTest {
        schedule(fontScale = 2f)
        val number = onNode(hasText("14"), useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        number.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertTrue(layouts.isNotEmpty() && !layouts[0].didOverflowHeight, "today's number is cut at 200% type")
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
