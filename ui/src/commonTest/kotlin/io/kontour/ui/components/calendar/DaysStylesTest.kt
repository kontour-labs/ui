package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
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
 * The three-day and week views without their hours: asked for as *"option to
 * hide the hourly grid and only show all-day events (condensed list per
 * day)"*. A split layout, the all-day events in a pane of their own above the
 * hours, was tried and taken out again as *"too confusing"*.
 */
@OptIn(ExperimentalTestApi::class)
class DaysStylesTest {

    private val wednesday = LocalDate(2026, 10, 14)
    private val review = CalendarEvent(1, "Design review", LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 11, 0))

    private class Probe {
        lateinit var state: EventCalendarState
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        var density = 1f
        val dates = mutableListOf<LocalDate>()
        val moves = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.calendar(style: CalendarDaysStyle, events: List<CalendarEvent>): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalInputModality provides InputModality.Mouse) {
                    OverlayHost(Modifier.size(900.dp, 800.dp)) {
                        probe.formats = Theme.formats
                        probe.strings = Theme.strings
                        probe.density = LocalDensity.current.density
                        probe.state = rememberEventCalendarState(wednesday, CalendarView.Week, LocalTime(8, 0), DayOfWeek.MONDAY)
                        EventCalendar(
                            state = probe.state,
                            events = events,
                            now = null,
                            onDateClick = { probe.dates += it },
                            onEventMove = { _, start, end -> probe.moves += start to end },
                            daysStyle = style,
                            modifier = Modifier.size(900.dp, 800.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        return probe
    }

    /** The hours' grid: a row a quarter-hour, as a screen reader is told. */
    private val hours = SemanticsMatcher("the hours") { n -> n.config.getOrNull(SemanticsProperties.CollectionInfo)?.rowCount == MinutesPerDay / 15 }

    private val busy = (0 until 12).map {
        CalendarEvent(100L + it, "Meeting $it", LocalDateTime(2026, 10, 14, 8 + it / 2, (it % 2) * 30), LocalDateTime(2026, 10, 14, 8 + it / 2, (it % 2) * 30 + 15))
    }

    @Test
    fun condensedHasNoHoursAndEveryEventOfABusyDay() = runComposeUiTest {
        val probe = calendar(CalendarDaysStyle.Condensed, busy)
        onNode(hours).assertDoesNotExist()
        for (event in busy) onNode(hasContentDescription(eventDescription(event, probe.formats, probe.strings))).assertExists("${event.title} is not in the list")
        val more = (1..busy.size).map { probe.strings.moreEvents(probe.formats.quantity(it)) }
        assertTrue(
            onAllNodes(SemanticsMatcher("a \"+N\"") { n -> n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it in more } }).fetchSemanticsNodes().isEmpty(),
            "a condensed list has a \"+N\"",
        )
    }

    @Test
    fun condensedEmptySpaceInADayOpensIt() = runComposeUiTest {
        val probe = calendar(CalendarDaysStyle.Condensed, listOf(review))
        val at = onNode(hasContentDescription(eventDescription(review, probe.formats, probe.strings))).fetchSemanticsNode().boundsInRoot
        // Well below the day's one event.
        onRoot().performTouchInput { click(Offset(at.center.x, at.bottom + 300f * probe.density)) }
        waitForIdle()
        assertEquals(listOf(wednesday), probe.dates)
    }

    @Test
    fun condensedATimedEventCarriedADayOnKeepsItsTime() = runComposeUiTest {
        val probe = calendar(CalendarDaysStyle.Condensed, listOf(review))
        val at = onNode(hasContentDescription(eventDescription(review, probe.formats, probe.strings))).fetchSemanticsNode().boundsInRoot
        val cell = at.width
        onRoot().performMouseInput {
            moveTo(at.center)
            press()
            repeat(10) { moveTo(Offset(at.center.x + cell * (it + 1) / 10f, at.center.y)) }
            release()
        }
        waitForIdle()
        assertEquals(listOf(LocalDateTime(2026, 10, 15, 10, 0) to LocalDateTime(2026, 10, 15, 11, 0)), probe.moves)
    }

    @Test
    fun condensedComposesOnlyTheRowsOnScreen() = runComposeUiTest {
        val many = (0 until 1000).map { CalendarEvent(1000L + it, "Thing $it", LocalDateTime(2026, 10, 14, 9, 0), LocalDateTime(2026, 10, 14, 9, 30)) }
        val probe = calendar(CalendarDaysStyle.Condensed, many)
        val names = many.map { eventDescription(it, probe.formats, probe.strings) }.toSet()
        val composed = onAllNodes(SemanticsMatcher("one of the thousand") { n ->
            n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it in names }
        }).fetchSemanticsNodes().size
        assertTrue(composed in 1..60, "$composed of a thousand events composed at once")
    }

    private val trips = (0 until 5).map {
        // All five on the 12th to the 14th: five rows, two more than the strip above the hours holds.
        CalendarEvent(200L + it, "Trip $it", startDate = LocalDate(2026, 10, 12), endDate = LocalDate(2026, 10, 14 + it))
    }

    /** *"the day view shouldn't have a condensed option"*: a day on its own keeps its hours. */
    @Test
    fun aDayOnItsOwnKeepsItsHoursWhenCondensed() = runComposeUiTest {
        val probe = calendar(CalendarDaysStyle.Condensed, listOf(review))
        onNode(hours).assertDoesNotExist()
        probe.state.show(CalendarView.Day)
        waitForIdle()
        onNode(hours).assertExists("the day view, condensed, lost its hours")
    }
}
