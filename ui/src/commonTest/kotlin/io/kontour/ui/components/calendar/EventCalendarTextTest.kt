package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * An event's title is readable in a phone's month: asked for as *"smaller
 * font / better truncation for event titles so more than 3 letters show in a
 * day cell"*, and *"fix the layout on cluttered days… events overflow and look
 * messy"*. A line gave its start time all the room it wanted and its title
 * what was left, two letters and an ellipsis; a busy day's "+N" hung under its
 * events as loose text.
 */
@OptIn(ExperimentalTestApi::class)
class EventCalendarTextTest {

    private class Probe {
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        var fourLetters = 0f
    }

    private fun androidx.compose.ui.test.ComposeUiTest.phoneMonth(events: List<CalendarEvent>): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.size(390.dp, 720.dp)) {
                    probe.formats = Theme.formats
                    probe.strings = Theme.strings
                    val measurer = rememberTextMeasurer()
                    probe.fourLetters = measurer.measure("Drag", calendarItemStyle()).size.width.toFloat()
                    EventCalendar(
                        state = rememberEventCalendarState(LocalDate(2026, 10, 14), CalendarView.Month, firstDayOfWeek = DayOfWeek.MONDAY),
                        events = events,
                        now = null,
                        header = null,
                        modifier = Modifier.size(390.dp, 720.dp),
                    )
                }
            }
        }
        waitForIdle()
        return probe
    }

    @Test
    fun aPhoneMonthsLineShowsMoreThanThreeLettersOfItsTitle() = runComposeUiTest {
        val event = CalendarEvent(1, "Dragonfruit review", LocalDateTime(2026, 10, 14, 9, 0), LocalDateTime(2026, 10, 14, 10, 0))
        val probe = phoneMonth(listOf(event))
        val title = onNode(hasText(event.title), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            title.width >= probe.fourLetters,
            "the title has ${title.width}px of a day ${390 / 7}dp wide; four letters need ${probe.fourLetters}px",
        )
    }

    @Test
    fun aBusyDaysMoreIsARowLikeTheEventsAboveIt() = runComposeUiTest {
        val busy = (0 until 12).map {
            CalendarEvent(100L + it, "Meeting $it", LocalDateTime(2026, 10, 14, 8 + it / 2, (it % 2) * 30), LocalDateTime(2026, 10, 14, 8 + it / 2, (it % 2) * 30 + 15))
        }
        val probe = phoneMonth(busy)
        val hidden = (1..busy.size).map { probe.strings.moreEvents(probe.formats.quantity(it)) }
        val more = onNode(SemanticsMatcher("a \"+N\"") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it in hidden }
        } and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val shown = busy.map { eventDescription(it, probe.formats, probe.strings) }
        val above = onAllNodes(SemanticsMatcher("an event shown") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it in shown }
        }).fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue(above.isNotEmpty(), "none of the busy day's events is shown")
        val event = above.maxBy { it.top }
        assertTrue(abs(more.height - event.height) < 1f, "the \"+N\" is ${more.height}px tall, its events ${event.height}px")
        assertTrue(abs(more.left - event.left) <= 1.5f && abs(more.width - event.width) <= 1.5f, "the \"+N\" is not in line with its events: $more, $event")
        assertTrue(more.top >= event.bottom - 1f, "the \"+N\" is not below the events: $more, $event")
    }

    @Test
    fun aLineGivesUpItsTimeThenItsDotForItsTitle() {
        assertEquals(LineParts(dot = true, time = true), lineParts(width = 200, dot = 8, time = 40, gap = 4, minTitle = 26))
        // 100 - 8 - 4 - 40 - 4 = 44 left for the title.
        assertEquals(LineParts(dot = true, time = true), lineParts(width = 100, dot = 8, time = 40, gap = 4, minTitle = 44))
        assertEquals(LineParts(dot = true, time = false), lineParts(width = 100, dot = 8, time = 40, gap = 4, minTitle = 45))
        // 40 - 8 - 4 = 28 left.
        assertEquals(LineParts(dot = true, time = false), lineParts(width = 40, dot = 8, time = 40, gap = 4, minTitle = 28))
        assertEquals(LineParts(dot = false, time = false), lineParts(width = 40, dot = 8, time = 40, gap = 4, minTitle = 29))
    }
}
