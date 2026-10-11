package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Three days at a time, and more than one week: asked for as *"option to show
 * more than one week at a time, as well as maybe a separate 3-day view"*.
 */
@OptIn(ExperimentalTestApi::class)
class DaysViewsTest {

    private val wednesday = LocalDate(2026, 10, 14)

    @Test
    fun threeDaysArePagedInThreesFromTheDayTheyWereShownOn() {
        val periods = CalendarPeriods(DayOfWeek.MONDAY, anchor = wednesday.toEpochDays())
        val here = periods.indexOf(wednesday, CalendarView.ThreeDays)
        assertEquals(wednesday..LocalDate(2026, 10, 16), periods.visibleDates(here, CalendarView.ThreeDays))
        assertEquals(LocalDate(2026, 10, 17)..LocalDate(2026, 10, 19), periods.visibleDates(here + 1, CalendarView.ThreeDays))
        assertEquals(LocalDate(2026, 10, 11), periods.startOf(here - 1, CalendarView.ThreeDays))
        // The day before is on the page before.
        assertEquals(here - 1, periods.indexOf(LocalDate(2026, 10, 13), CalendarView.ThreeDays))
    }

    @Test
    fun severalWeeksArePagedFromTheWeekTheyWereShownOn() {
        val periods = CalendarPeriods(DayOfWeek.MONDAY, weekCount = 2, anchor = wednesday.toEpochDays())
        val here = periods.indexOf(wednesday, CalendarView.Week)
        assertEquals(LocalDate(2026, 10, 12)..LocalDate(2026, 10, 25), periods.visibleDates(here, CalendarView.Week))
        assertEquals(LocalDate(2026, 10, 26), periods.startOf(here + 1, CalendarView.Week))
        assertEquals(here, periods.indexOf(LocalDate(2026, 10, 25), CalendarView.Week))
        assertEquals(14, periods.daysIn(CalendarView.Week))
        // A month's rows are still single weeks.
        assertEquals(LocalDate(2026, 10, 12), periods.weekStart(wednesday))
    }

    private class Probe {
        lateinit var state: EventCalendarState
        lateinit var strings: Strings
    }

    private fun androidx.compose.ui.test.ComposeUiTest.calendar(view: CalendarView): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.size(900.dp, 800.dp)) {
                    probe.strings = Theme.strings
                    probe.state = rememberEventCalendarState(wednesday, view, LocalTime(8, 0), DayOfWeek.MONDAY)
                    EventCalendar(state = probe.state, events = emptyList(), now = null, modifier = Modifier.size(900.dp, 800.dp))
                }
            }
        }
        waitForIdle()
        return probe
    }

    @Test
    fun theSwitcherOffersThreeDaysWhichShowsThreeAndStepsByThree() = runComposeUiTest {
        val probe = calendar(CalendarView.Day)
        onNode(hasText(probe.strings.threeDays) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(CalendarView.ThreeDays, probe.state.view)
        assertEquals(wednesday..LocalDate(2026, 10, 16), probe.state.visibleDates)
        onNode(hasContentDescription(probe.strings.nextDays) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(LocalDate(2026, 10, 17)..LocalDate(2026, 10, 19), probe.state.visibleDates)
    }

    @Test
    fun twoWeeksShowFourteenDaysAndStepByThem() = runComposeUiTest {
        val probe = calendar(CalendarView.Week)
        runOnIdle { probe.state.showWeeks(2) }
        waitForIdle()
        assertEquals(LocalDate(2026, 10, 12)..LocalDate(2026, 10, 25), probe.state.visibleDates)
        onNode(hasContentDescription(probe.strings.nextDays) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(LocalDate(2026, 10, 26)..LocalDate(2026, 11, 8), probe.state.visibleDates)
        assertEquals(LocalDate(2026, 10, 28), probe.state.date, "a step keeps the day's place in the fortnight")
    }

    /**
     * Too narrow for every view side by side, they are offered in a menu: six
     * segments on a phone stacked into a column. Wide enough, they are side by
     * side.
     */
    @Test
    fun aHeaderTooNarrowForEveryViewOffersThemInAMenu() = runComposeUiTest {
        lateinit var strings: Strings
        setContent {
            KontourTheme(reduceMotion = true) {
                io.kontour.ui.overlay.OverlayHost(Modifier.size(600.dp, 500.dp)) {
                    strings = Theme.strings
                    androidx.compose.foundation.layout.Column {
                        EventCalendarDefaults.Header(rememberEventCalendarState(wednesday, CalendarView.Month), Modifier.size(360.dp, 200.dp))
                        EventCalendarDefaults.Header(rememberEventCalendarState(wednesday, CalendarView.Month), Modifier.size(560.dp, 200.dp))
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(1, onAllNodes(hasText(strings.threeDays)).fetchSemanticsNodes().size, "the views should be side by side in the wide header only")
        assertEquals(1, onAllNodes(hasText(strings.schedule)).fetchSemanticsNodes().size)
        // The narrow one names the view on show, as its menu's button.
        assertEquals(2, onAllNodes(hasText(strings.month) and hasClickAction()).fetchSemanticsNodes().size)
    }

    /** Narrower than a phone, the menu's button goes under the title rather than squeezing it out. */
    @Test
    fun aNarrowHeaderKeepsItsTitle() = runComposeUiTest {
        var title = ""
        setContent {
            KontourTheme(reduceMotion = true) {
                io.kontour.ui.overlay.OverlayHost(Modifier.size(300.dp, 300.dp)) {
                    val state = rememberEventCalendarState(wednesday, CalendarView.Month)
                    title = Theme.formats.monthAndYear(state.date)
                    EventCalendarDefaults.Header(state, Modifier.size(280.dp, 200.dp))
                }
            }
        }
        waitForIdle()
        val bounds = onNode(hasText(title)).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width > 60f, "the title is ${bounds.width}px wide beside the arrows and the menu")
    }

    @Test
    fun aWeekCountOutsideOneToSixIsRefusedByName() {
        val state = EventCalendarState(wednesday, CalendarView.Week, LocalTime(8, 0), DayOfWeek.MONDAY)
        val error = assertFailsWith<IllegalArgumentException> { state.showWeeks(7) }
        assertEquals("A week view shows 1 to 6 weeks, not 7.", error.message)
    }

    @Test
    fun theWeekCountAndWhereItCountsFromAreKept() {
        val state = EventCalendarState(wednesday, CalendarView.Week, LocalTime(8, 0), DayOfWeek.MONDAY)
        state.showWeeks(3)
        val saved = with(EventCalendarState.Saver) { SaverScope { true }.save(state) }!!
        val restored = EventCalendarState.Saver.restore(saved)!!
        assertEquals(3, restored.weekCount)
        assertEquals(state.visibleDates, restored.visibleDates)
    }
}
