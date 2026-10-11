package io.kontour.ui.components.calendar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
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
 * An event in a busy day's list, opened from its "+N", carried out of the list
 * and put down on another day of the month.
 *
 * The list is an overlay over the days, so the press that takes an event up in
 * it is the list's: the days never hear that pointer. The list follows it —
 * hidden, not closed, since closed it would take the pointer with it — and
 * closes once the event is put down.
 */
@OptIn(ExperimentalTestApi::class)
class DayListCarryTest {

    private val busy = (0 until 12).map {
        CalendarEvent(100 + it, "Meeting $it", LocalDateTime(2026, 10, 14, 8 + it / 2, (it % 2) * 30), LocalDateTime(2026, 10, 14, 8 + it / 2, (it % 2) * 30 + 15))
    }

    private class Probe {
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        val moves = mutableListOf<Pair<CalendarEvent, LocalDateTime>>()
    }

    private fun ComposeUiTest.calendar(modality: InputModality): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalInputModality provides modality) {
                    OverlayHost(Modifier.size(900.dp, 800.dp)) {
                        probe.formats = Theme.formats
                        probe.strings = Theme.strings
                        EventCalendar(
                            state = rememberEventCalendarState(LocalDate(2026, 10, 14), CalendarView.Month, LocalTime(8, 0), DayOfWeek.MONDAY),
                            events = busy,
                            now = null,
                            onEventMove = { event, start, _ -> probe.moves += event to start },
                            modifier = Modifier.size(900.dp, 800.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        return probe
    }

    private fun ComposeUiTest.openTheList(probe: Probe) {
        val hidden = (1..busy.size).map { probe.strings.moreEvents(probe.formats.quantity(it)) }
        onAllNodes(SemanticsMatcher("a \"+N\"") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it in hidden }
        } and hasClickAction())[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.dayBox(probe: Probe, date: LocalDate): Rect =
        onAllNodes(hasContentDescription(probe.formats.date(date, DateStyle.Full), substring = true) and hasClickAction())
            .fetchSemanticsNodes().map { it.boundsInRoot }.maxBy { it.height }

    private fun ComposeUiTest.listed(probe: Probe, event: CalendarEvent) =
        onAllNodes(hasContentDescription(eventDescription(event, probe.formats, probe.strings))).fetchSemanticsNodes()

    @Test
    fun aFingerCarriesAnEventOutOfTheListOntoAnotherDay() = runComposeUiTest {
        val probe = calendar(InputModality.Touch)
        openTheList(probe)
        val last = busy.last()
        val from = listed(probe, last).single().boundsInRoot.center
        val to = dayBox(probe, LocalDate(2026, 10, 21)).center
        onRoot().performTouchInput {
            down(from)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(20) { moveTo(from + (to - from) * ((it + 1) / 20f)) }
        }
        waitForIdle()
        onRoot().performTouchInput { up() }
        waitForIdle()
        assertEquals(listOf(last.id), probe.moves.map { it.first.id }, "carried out of the list and let go, the event was not moved")
        assertEquals(LocalDate(2026, 10, 21), probe.moves.single().second.date, "it was put down on ${probe.moves.single().second.date}")
        assertEquals(LocalTime(13, 30), probe.moves.single().second.time, "carried by days, it kept its time")
        assertTrue(listed(probe, last).isEmpty(), "the list stayed open once the event was put down")
    }

    @Test
    fun aMouseCarriesAnEventOutOfTheListOntoAnotherDay() = runComposeUiTest {
        val probe = calendar(InputModality.Mouse)
        openTheList(probe)
        val last = busy.last()
        val from = listed(probe, last).single().boundsInRoot.center
        val to = dayBox(probe, LocalDate(2026, 10, 7)).center
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(20) { moveTo(from + (to - from) * ((it + 1) / 20f)) }
            release()
        }
        waitForIdle()
        assertEquals(LocalDate(2026, 10, 7), probe.moves.singleOrNull()?.second?.date, "dragged out of the list with a mouse, the event landed: ${probe.moves}")
    }
}
