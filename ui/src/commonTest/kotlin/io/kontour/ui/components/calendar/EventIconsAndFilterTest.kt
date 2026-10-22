package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Icons in events — *"i'd like the ability to show icons in events"* — and a
 * calendar of only all-day and multi-day events: *"i'd like to have the option
 * to show only full-day or multi-day events"*.
 */
@OptIn(ExperimentalTestApi::class)
class EventIconsAndFilterTest {

    private val wednesday = LocalDate(2026, 10, 14)

    private class Probe {
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        lateinit var state: EventCalendarState
    }

    private fun ComposeUiTest.calendar(view: CalendarView, events: List<CalendarEvent>, timed: Boolean = true): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.size(900.dp, 800.dp)) {
                    probe.formats = Theme.formats
                    probe.strings = Theme.strings
                    probe.state = rememberEventCalendarState(wednesday, view, LocalTime(8, 0), DayOfWeek.MONDAY)
                    EventCalendar(
                        state = probe.state,
                        events = events,
                        now = null,
                        showTimedEvents = timed,
                        modifier = Modifier.size(900.dp, 800.dp),
                    )
                }
            }
        }
        waitForIdle()
        return probe
    }

    private val meeting = CalendarEvent(1, "Design review", LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 12, 0))
    private val trip = CalendarEvent(2, "Perth trip", startDate = LocalDate(2026, 10, 13), endDate = LocalDate(2026, 10, 15))

    /** Where an event's title starts, against its own box's start. */
    private fun ComposeUiTest.titleInset(probe: Probe, event: CalendarEvent): Float {
        val box = onNode(hasContentDescription(eventDescription(event, probe.formats, probe.strings))).fetchSemanticsNode().boundsInRoot
        val title = onNode(hasText(event.title), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return title.left - box.left
    }

    private fun inset(view: CalendarView, event: CalendarEvent): Float {
        var result = 0f
        runComposeUiTest {
            val probe = calendar(view, listOf(event))
            result = titleInset(probe, event)
        }
        return result
    }

    @Test
    fun anIconLeadsAnEventsTitleInEachOfItsShapes() {
        val icon = SystemIcons.Default.close
        // A block in the hours, a bar across days, a line in a month's day.
        for ((view, event) in listOf(CalendarView.Week to meeting, CalendarView.Week to trip, CalendarView.Month to meeting)) {
            val plain = inset(view, event)
            val led = inset(view, event.copy(icon = icon))
            assertTrue(led > plain + 4f, "in the $view view, ${event.title}'s title starts $plain in without an icon and $led with one")
        }
    }

    @Test
    fun anIconIsPartOfWhatAnEventIs() {
        val plane = meeting.copy(icon = SystemIcons.Default.close)
        assertNotEquals(meeting, plane)
        assertEquals(SystemIcons.Default.close, plane.icon)
        assertEquals(plane, plane.copy())
        assertEquals(plane.hashCode(), plane.copy().hashCode())
    }

    /** The hours' grid: a row a quarter-hour, as a screen reader is told. */
    private val hours = SemanticsMatcher("the hours") { n -> n.config.getOrNull(SemanticsProperties.CollectionInfo)?.rowCount == MinutesPerDay / 15 }

    @Test
    fun withoutTimedEventsAMonthShowsOnlyTheLongerOnes() = runComposeUiTest {
        calendar(CalendarView.Month, listOf(meeting, trip), timed = false)
        onNode(hasText(meeting.title), useUnmergedTree = true).assertDoesNotExist()
        onNode(hasText(trip.title), useUnmergedTree = true).assertExists()
    }

    @Test
    fun withoutTimedEventsTheDaysHaveNoHours() {
        for (view in listOf(CalendarView.Day, CalendarView.Week)) runComposeUiTest {
            calendar(view, listOf(meeting, trip), timed = false)
            onNode(hours).assertDoesNotExist()
            onNode(hasText(meeting.title), useUnmergedTree = true).assertDoesNotExist()
            onNode(hasText(trip.title), useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun aTimedEventOvernightIsATimedOneAndADayLongIsNot() {
        val overnight = CalendarEvent(3, "Flight", LocalDateTime(2026, 10, 14, 22, 0), LocalDateTime(2026, 10, 15, 6, 0))
        val dayLong = CalendarEvent(4, "Conference", LocalDateTime(2026, 10, 14, 9, 0), LocalDateTime(2026, 10, 16, 17, 0))
        assertTrue(overnight.isTimed())
        assertTrue(!dayLong.isTimed())
    }
}
