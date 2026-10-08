package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * The event calendar from outside: what it shows, what a press on each part
 * of it does, and where its header and its days take it.
 */
@OptIn(ExperimentalTestApi::class)
class EventCalendarTest {

    private val today = LocalDate(2026, 10, 14)
    private val standUp = CalendarEvent(1, "Stand-up", LocalDateTime(2026, 10, 14, 9, 0), LocalDateTime(2026, 10, 14, 9, 15))
    private val trip = CalendarEvent(2, "Perth trip", startDate = LocalDate(2026, 10, 13), endDate = LocalDate(2026, 10, 16), tone = Tone.Warning)

    private class Probe {
        lateinit var state: EventCalendarState
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
    }

    private fun androidx.compose.ui.test.ComposeUiTest.calendar(
        view: CalendarView = CalendarView.Month,
        events: List<CalendarEvent> = listOf(standUp, trip),
        onEventClick: ((CalendarEvent) -> Unit)? = null,
        onDateClick: ((LocalDate) -> Unit)? = null,
        onTimeClick: ((LocalDateTime) -> Unit)? = null,
    ): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.size(900.dp, 800.dp)) {
                    probe.formats = Theme.formats
                    probe.strings = Theme.strings
                    probe.state = rememberEventCalendarState(initialDate = today, initialView = view, firstDayOfWeek = DayOfWeek.MONDAY)
                    Box(Modifier.size(900.dp, 800.dp)) {
                        EventCalendar(
                            state = probe.state,
                            events = events,
                            now = LocalDateTime(2026, 10, 14, 11, 20),
                            onEventClick = onEventClick,
                            onDateClick = onDateClick,
                            onTimeClick = onTimeClick,
                            modifier = Modifier.size(900.dp, 800.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        return probe
    }

    @Test
    fun anEventPressedIsReported() = runComposeUiTest {
        val pressed = mutableListOf<CalendarEvent>()
        val probe = calendar(onEventClick = { pressed += it })
        val described = eventDescription(standUp, probe.formats, probe.strings)
        onNode(hasContentDescription(described) and hasClickAction()).performClick()
        assertEquals(listOf(standUp), pressed)
    }

    @Test
    fun anEventIsReadAsItsTitleDayAndTime() = runComposeUiTest {
        val probe = calendar()
        val described = eventDescription(standUp, probe.formats, probe.strings)
        assertTrue("Stand-up" in described)
        assertTrue(probe.formats.time(standUp.start.time) in described, "the time is not in \"$described\"")
        assertTrue(probe.formats.date(today, DateStyle.Full) in described, "the day is not in \"$described\"")
        val allDay = eventDescription(trip, probe.formats, probe.strings)
        assertTrue(probe.strings.allDay in allDay, "an all-day event does not say so: \"$allDay\"")
        // Without a click callback the event is still a node, and not a button.
        onNode(hasContentDescription(described)).assertExists()
        assertEquals(0, onAllNodes(hasContentDescription(described) and hasClickAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun aDaysNumberOpensThatDay() = runComposeUiTest {
        val probe = calendar()
        val date = LocalDate(2026, 10, 20)
        onNode(hasText(probe.formats.integer(20)) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(CalendarView.Day, probe.state.view)
        assertEquals(date, probe.state.date)
    }

    @Test
    fun aDaysBoxPressedReportsTheDay() = runComposeUiTest {
        val pressed = mutableListOf<LocalDate>()
        val probe = calendar(onDateClick = { pressed += it })
        val date = LocalDate(2026, 10, 22)
        onNode(hasContentDescription(probe.formats.date(date, DateStyle.Full), substring = true) and hasClickAction()).performClick()
        assertEquals(listOf(date), pressed)
        assertEquals(CalendarView.Month, probe.state.view, "pressing a day's box is not opening it")
    }

    /**
     * One rule for a day, wherever it is pressed: asked for as *"sometimes
     * clicking a date in the month view will take you to the day view, but
     * other times it won't? please make it consistent"*. Its number opened the
     * day and the rest of its box called `onDateClick`.
     */
    @Test
    fun aDaysBoxWithNoCallbackOpensThatDayAsItsNumberDoes() = runComposeUiTest {
        val probe = calendar()
        val date = LocalDate(2026, 10, 22)
        onNode(hasContentDescription(probe.formats.date(date, DateStyle.Full), substring = true) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(CalendarView.Day, probe.state.view, "a day's box pressed did not open it")
        assertEquals(date, probe.state.date)
    }

    @Test
    fun aDaysNumberWithACallbackReportsTheDayAsItsBoxDoes() = runComposeUiTest {
        val pressed = mutableListOf<LocalDate>()
        val probe = calendar(onDateClick = { pressed += it })
        onNode(hasText(probe.formats.integer(20)) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(listOf(LocalDate(2026, 10, 20)), pressed)
        assertEquals(CalendarView.Month, probe.state.view, "the number opened the day over the callback")
    }

    @Test
    fun theHeaderStepsByTheViewsPeriodAndComesBackToToday() = runComposeUiTest {
        val probe = calendar()
        onNode(hasContentDescription(probe.strings.nextMonth)).performClick()
        waitForIdle()
        assertEquals(LocalDate(2026, 11, 14), probe.state.date)
        runOnIdle { probe.state.show(CalendarView.Week) }
        onNode(hasContentDescription(probe.strings.previousWeek)).performClick()
        waitForIdle()
        assertEquals(LocalDate(2026, 11, 7), probe.state.date)
        onNode(hasContentDescription(probe.strings.returnToToday)).performClick()
        waitForIdle()
        assertEquals(today, probe.state.date)
    }

    @Test
    fun theSwitcherChangesTheView() = runComposeUiTest {
        val probe = calendar()
        onNode(hasText(probe.strings.year) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(CalendarView.Year, probe.state.view)
        assertEquals(today, probe.state.date, "a change of view lost the day")
    }

    @Test
    fun theYearOpensAtTheMonthItIsOnWithEveryMonthInViewFilledIn() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Year)
        fun day(date: LocalDate) = onNode(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                probe.formats.date(date, DateStyle.Full),
            ),
        )
        // October's row first: January is scrolled past, not shown above it.
        onNode(hasText(probe.formats.monthName(kotlinx.datetime.Month.OCTOBER, io.kontour.ui.locale.NameWidth.Wide))).assertExists()
        onNode(hasText(probe.formats.monthName(kotlinx.datetime.Month.JANUARY, io.kontour.ui.locale.NameWidth.Wide))).assertDoesNotExist()
        // And its days there, and those of the month beside it, filled in a frame apart.
        day(today).assertExists()
        day(LocalDate(2026, 9, 1)).assertExists()
    }

    @Test
    fun anEmptyTimePressedIsTheQuarterHourItIsIn() = runComposeUiTest {
        val pressed = mutableListOf<LocalDateTime>()
        val probe = calendar(view = CalendarView.Day, events = emptyList(), onTimeClick = { pressed += it })
        // The day's column, a little way down from where the hours open.
        onNode(hasContentDescription(probe.formats.date(today, DateStyle.Full)) and hasClickAction()).assertExists()
        onRoot().performTouchInput { click(Offset(centerX + 100f, centerY)) }
        waitForIdle()
        assertEquals(1, pressed.size, "nothing was reported for an empty time")
        val time = pressed.single()
        assertEquals(today, time.date)
        assertEquals(0, time.minute % 15, "$time is not on a quarter-hour")
    }

    @Test
    fun aMonthStepKeepsTheDayWhereTheMonthHasIt() = runComposeUiTest {
        val probe = calendar()
        runOnIdle {
            probe.state.show(LocalDate(2026, 1, 31), CalendarView.Month)
            probe.state.step(1)
        }
        assertEquals(LocalDate(2026, 2, 28), probe.state.date)
    }

    @Test
    fun theVisibleDatesOfAMonthAreItsWholeWeeks() = runComposeUiTest {
        val probe = calendar()
        val dates = probe.state.visibleDates
        assertEquals(LocalDate(2026, 9, 28), dates.start)
        assertEquals(LocalDate(2026, 11, 1), dates.endInclusive)
    }

    @Test
    fun aBusyDaysMoreOpensItsEvents() = runComposeUiTest {
        val busy = (0 until 12).map { CalendarEvent(100 + it, "Meeting $it", LocalDateTime(2026, 10, 14, 8 + it / 2, (it % 2) * 30), LocalDateTime(2026, 10, 14, 8 + it / 2, (it % 2) * 30 + 15)) }
        val probe = calendar(events = busy)
        // However many are hidden, the "+N" is read as that many "more events".
        val hidden = (1..busy.size).map { probe.strings.moreEvents(probe.formats.quantity(it)) }
        val more = onAllNodes(androidx.compose.ui.test.SemanticsMatcher("a \"+N\"") { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription).orEmpty().any { it in hidden }
        } and hasClickAction())
        assertTrue(more.fetchSemanticsNodes().isNotEmpty(), "twelve events in one day showed no \"+N\"")
        more[0].performClick()
        waitForIdle()
        // Every one of the day's events is in the list now, the hidden ones included.
        val last = eventDescription(busy.last(), probe.formats, probe.strings)
        onNode(hasContentDescription(last)).assertExists()
    }
}
