package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * *"i'd like to have the option to use a dropdown for selecting the view instead
 * of a segmented control, and i want to be able to disable certain views"*.
 */
@OptIn(ExperimentalTestApi::class)
class CalendarViewsOfferedTest {

    private val wednesday = LocalDate(2026, 10, 14)

    private class Probe {
        lateinit var state: EventCalendarState
        lateinit var strings: Strings
    }

    private fun ComposeUiTest.calendar(
        start: CalendarView,
        views: List<CalendarView>,
        switcher: CalendarSwitcher = CalendarSwitcher.Segmented,
    ): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.size(900.dp, 800.dp)) {
                    probe.strings = Theme.strings
                    probe.state = rememberEventCalendarState(wednesday, start, LocalTime(8, 0), DayOfWeek.MONDAY)
                    EventCalendar(
                        state = probe.state,
                        events = emptyList(),
                        now = null,
                        views = views,
                        header = { EventCalendarDefaults.Header(it, switcher = switcher) },
                        modifier = Modifier.size(900.dp, 800.dp),
                    )
                }
            }
        }
        waitForIdle()
        return probe
    }

    @Test
    fun aDayOpenedWithoutADayViewOpensTheNearestViewThereIs() = runComposeUiTest {
        val probe = calendar(CalendarView.Month, listOf(CalendarView.Week, CalendarView.Month))
        probe.state.show(LocalDate(2026, 10, 20), CalendarView.Day)
        waitForIdle()
        assertEquals(CalendarView.Week, probe.state.view, "asked for a day it does not have, the calendar went to ${probe.state.view}")
        assertEquals(LocalDate(2026, 10, 20), probe.state.date)
    }

    @Test
    fun aStateInAViewNotOfferedMovesToTheNearestThatIs() = runComposeUiTest {
        val probe = calendar(CalendarView.Day, listOf(CalendarView.ThreeDays, CalendarView.Month))
        assertEquals(CalendarView.ThreeDays, probe.state.view)
    }

    @Test
    fun theSwitcherOffersOnlyTheViewsTheCalendarHas() = runComposeUiTest {
        val probe = calendar(CalendarView.Month, listOf(CalendarView.Week, CalendarView.Month))
        onNode(hasText(probe.strings.week) and hasClickAction()).assertExists()
        onNode(hasText(probe.strings.day) and hasClickAction()).assertDoesNotExist()
        onNode(hasText(probe.strings.year) and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun aDropdownSwitcherNamesTheViewAndChangesIt() = runComposeUiTest {
        val probe = calendar(CalendarView.Month, EventCalendarDefaults.Views, CalendarSwitcher.Dropdown)
        // One button, named for the view on show; the others are in its menu.
        onNode(hasText(probe.strings.week) and hasClickAction()).assertDoesNotExist()
        onNode(hasText(probe.strings.month) and hasClickAction()).performClick()
        waitForIdle()
        onNode(hasText(probe.strings.week) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(CalendarView.Week, probe.state.view)
    }
}
