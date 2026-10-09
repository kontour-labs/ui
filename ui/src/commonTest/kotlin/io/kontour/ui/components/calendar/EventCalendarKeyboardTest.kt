package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * The calendar from the keyboard — asked for as a cursor through the month's
 * days and the hours' empty time: one stop for the grid, arrows to move, the
 * period paging past its ends, Enter to open a day or a time, and Tab on to
 * the cursor day's events alone.
 */
@OptIn(ExperimentalTestApi::class)
class EventCalendarKeyboardTest {

    private val wednesday = LocalDate(2026, 10, 14)
    private val review = CalendarEvent(1, "Design review", LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 11, 0))
    private val trip = CalendarEvent(3, "Perth trip", startDate = LocalDate(2026, 10, 20), endDate = LocalDate(2026, 10, 21))

    private class Probe {
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        lateinit var state: EventCalendarState
        val dates = mutableListOf<LocalDate>()
        val times = mutableListOf<LocalDateTime>()
        val creates = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
    }

    private fun ComposeUiTest.calendar(
        view: CalendarView,
        dateClicks: Boolean = false,
        timeClicks: Boolean = false,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalInputModality provides InputModality.Keyboard, LocalLayoutDirection provides direction) {
                    OverlayHost(Modifier.size(900.dp, 900.dp)) {
                        probe.formats = Theme.formats
                        probe.strings = Theme.strings
                        probe.state = rememberEventCalendarState(
                            initialDate = wednesday,
                            initialView = view,
                            initialScrollTime = LocalTime(8, 0),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        )
                        EventCalendar(
                            state = probe.state,
                            events = listOf(review, trip),
                            now = null,
                            onDateClick = if (dateClicks) ({ probe.dates += it }) else null,
                            onTimeClick = if (timeClicks) ({ probe.times += it }) else null,
                            onCreateRequest = { start, end -> probe.creates += start to end },
                            onEventMove = { _, _, _ -> },
                            modifier = Modifier.size(900.dp, 900.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        return probe
    }

    /**
     * The grid's one stop: focusable, and neither a button nor an event with
     * its actions — a month's own actions are its zoom, not an event's.
     */
    private fun ComposeUiTest.grid() = onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused) and hasNoClickAction() and
            SemanticsMatcher("no event's actions") { node ->
                val zoom = io.kontour.ui.theme.Strings().let { setOf(it.zoomIn, it.zoomOut) }
                node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().all { it.label in zoom }
            },
    )

    private fun ComposeUiTest.keys(key: Key) = keys(listOf(key))

    private fun ComposeUiTest.keys(keys: List<Key>) {
        grid().performKeyInput { keys.forEach { pressKey(it) } }
        waitForIdle()
    }

    private fun ComposeUiTest.focusedDescription(): String? =
        onNode(isFocused()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()

    @Test
    fun theMonthsArrowsMoveTheCursorDayAndEnterReportsIt() = runComposeUiTest {
        val probe = calendar(CalendarView.Month, dateClicks = true)
        grid().requestFocus()
        keys(listOf(Key.DirectionRight, Key.DirectionDown, Key.DirectionDown, Key.DirectionLeft))
        assertEquals(LocalDate(2026, 10, 28), probe.state.date)
        keys(Key.Enter)
        assertEquals(listOf(LocalDate(2026, 10, 28)), probe.dates)
        // A day the grid's cursor reaches is not also a stop of its own.
        assertEquals(dayDescription(probe, LocalDate(2026, 10, 28)), focusedDescription())
    }

    @Test
    fun theMonthsHomeAndEndAreTheWeeksEnds() = runComposeUiTest {
        val probe = calendar(CalendarView.Month)
        grid().requestFocus()
        keys(Key.MoveHome)
        assertEquals(LocalDate(2026, 10, 12), probe.state.date)
        keys(Key.MoveEnd)
        assertEquals(LocalDate(2026, 10, 18), probe.state.date)
    }

    @Test
    fun theMonthPagesPastItsEndAndByPage() = runComposeUiTest {
        val probe = calendar(CalendarView.Month)
        grid().requestFocus()
        keys(Key.PageDown)
        assertEquals(LocalDate(2026, 11, 14), probe.state.date)
        repeat(3) { keys(Key.DirectionDown) }
        // Off the end of November, into December's first week: the month goes too.
        assertEquals(LocalDate(2026, 12, 5), probe.state.date)
        assertEquals(LocalDate(2026, 11, 30), probe.state.visibleDates.start)
        // Still the grid with focus: paging did not take it away with the page.
        keys(Key.PageUp)
        assertEquals(LocalDate(2026, 11, 5), probe.state.date)
    }

    @Test
    fun aMonthsEnterWithNoDateClickOpensTheDay() = runComposeUiTest {
        val probe = calendar(CalendarView.Month)
        grid().requestFocus()
        keys(listOf(Key.DirectionRight, Key.Enter))
        assertEquals(CalendarView.Day, probe.state.view)
        assertEquals(LocalDate(2026, 10, 15), probe.state.date)
    }

    @Test
    fun rightToLeftTheArrowsAcrossAreMirrored() = runComposeUiTest {
        val probe = calendar(CalendarView.Month, direction = LayoutDirection.Rtl)
        grid().requestFocus()
        keys(Key.DirectionLeft)
        assertEquals(LocalDate(2026, 10, 15), probe.state.date)
        keys(listOf(Key.DirectionRight, Key.DirectionRight))
        assertEquals(LocalDate(2026, 10, 13), probe.state.date)
    }

    @Test
    fun tabFromTheMonthGoesToTheCursorDaysEventsAlone() = runComposeUiTest {
        val probe = calendar(CalendarView.Month)
        grid().requestFocus()
        // On the 20th: the trip, though the review on the 14th comes first.
        repeat(6) { keys(Key.DirectionRight) }
        grid().performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        val focused = focusedDescription().orEmpty()
        assertTrue(focused.startsWith(trip.title), "Tab from the 20th went to \"$focused\", not the trip")
    }

    @Test
    fun theHoursCursorStartsInViewAndStepsBySnap() = runComposeUiTest {
        val probe = calendar(CalendarView.Week)
        grid().requestFocus()
        waitForIdle()
        assertEquals(8 * 60, probe.state.cursorMinute)
        keys(listOf(Key.DirectionDown, Key.DirectionDown))
        keys(Key.Enter)
        val start = LocalDateTime(2026, 10, 14, 8, 30)
        assertEquals(listOf(start to LocalDateTime(2026, 10, 14, 9, 30)), probe.creates)
    }

    @Test
    fun theHoursEnterIsATimePressedWhereThereIsOne() = runComposeUiTest {
        val probe = calendar(CalendarView.Week, timeClicks = true)
        grid().requestFocus()
        keys(listOf(Key.DirectionRight, Key.PageDown, Key.Enter))
        assertEquals(listOf(LocalDateTime(2026, 10, 15, 9, 0)), probe.times)
        assertTrue(probe.creates.isEmpty())
    }

    @Test
    fun theHoursScrollToFollowTheCursor() = runComposeUiTest {
        val probe = calendar(CalendarView.Day)
        grid().requestFocus()
        keys(Key.MoveEnd)
        assertEquals(24 * 60 - 15, probe.state.cursorMinute)
        val scroll = probe.state.timeScroll
        assertEquals(scroll.maxValue, scroll.value)
        keys(Key.MoveHome)
        assertEquals(0, scroll.value)
    }

    @Test
    fun aWeeksCursorPagesPastItsLastDay() = runComposeUiTest {
        val probe = calendar(CalendarView.Week)
        grid().requestFocus()
        repeat(5) { keys(Key.DirectionRight) }
        assertEquals(LocalDate(2026, 10, 19), probe.state.date)
        assertEquals(LocalDate(2026, 10, 19), probe.state.visibleDates.start)
        keys(Key.Enter)
        assertEquals(LocalDateTime(2026, 10, 19, 8, 0), probe.creates.single().first)
    }

    @Test
    fun theCursorsMoveIsReadOut() = runComposeUiTest {
        val probe = calendar(CalendarView.Day)
        grid().requestFocus()
        keys(Key.DirectionDown)
        val said = probe.formats.list(
            listOf(probe.formats.date(wednesday, DateStyle.Full), probe.formats.time(LocalTime(8, 15))),
            io.kontour.ui.locale.ListStyle.Unit,
        )
        onNode(hasContentDescription(said, substring = true) and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertExists()
    }

    private fun dayDescription(probe: Probe, date: LocalDate): String = probe.formats.list(
        listOfNotNull(
            probe.formats.date(date, DateStyle.Full),
            listOf(review, trip).count { it.coversDay(date) }.takeIf { it > 0 }?.let { probe.strings.eventCount(probe.formats.quantity(it)) },
        ),
        io.kontour.ui.locale.ListStyle.Unit,
    )
}
