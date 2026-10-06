package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
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
 * Editing on the event calendar — asked for as drag to create, to move and to
 * resize — by mouse, by finger, by keyboard and by a screen reader's actions,
 * each reported to the app as what it would do, the app's events untouched.
 */
@OptIn(ExperimentalTestApi::class)
class EventCalendarEditTest {

    private val wednesday = LocalDate(2026, 10, 14)
    private val thursday = LocalDate(2026, 10, 15)
    private val review = CalendarEvent(1, "Design review", LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 11, 0))
    private val holiday = CalendarEvent(2, "Public holiday", startDate = LocalDate(2026, 10, 16), tone = Tone.Warning, editable = false)
    private val trip = CalendarEvent(3, "Perth trip", startDate = LocalDate(2026, 10, 20), endDate = LocalDate(2026, 10, 21))

    private class Probe {
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        var density = 1f
        val moves = mutableListOf<Triple<CalendarEvent, LocalDateTime, LocalDateTime>>()
        val resizes = mutableListOf<Triple<CalendarEvent, LocalDateTime, LocalDateTime>>()
        val creates = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
    }

    private fun ComposeUiTest.calendar(
        view: CalendarView = CalendarView.Week,
        modality: InputModality = InputModality.Mouse,
        events: List<CalendarEvent> = listOf(review, holiday, trip),
    ): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalInputModality provides modality) {
                    OverlayHost(Modifier.size(900.dp, 900.dp)) {
                        probe.formats = Theme.formats
                        probe.strings = Theme.strings
                        probe.density = LocalDensity.current.density
                        EventCalendar(
                            state = rememberEventCalendarState(
                                initialDate = wednesday,
                                initialView = view,
                                initialScrollTime = kotlinx.datetime.LocalTime(8, 0),
                                firstDayOfWeek = DayOfWeek.MONDAY,
                            ),
                            events = events,
                            now = null,
                            onEventMove = { event, start, end -> probe.moves += Triple(event, start, end) },
                            onEventResize = { event, start, end -> probe.resizes += Triple(event, start, end) },
                            onCreateRequest = { start, end -> probe.creates += start to end },
                            modifier = Modifier.size(900.dp, 900.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        return probe
    }

    private fun ComposeUiTest.boundsOf(event: CalendarEvent, probe: Probe): Rect =
        onNode(hasContentDescription(eventDescription(event, probe.formats, probe.strings))).fetchSemanticsNode().boundsInRoot

    /** The distance from one day's column to the next, from the days' headings. */
    private fun ComposeUiTest.dayWidth(probe: Probe): Float {
        fun heading(date: LocalDate) = onNode(hasContentDescription(probe.formats.date(date, DateStyle.Full)) and hasClickAction())
            .fetchSemanticsNode().boundsInRoot.center.x
        return heading(thursday) - heading(wednesday)
    }

    private fun hour(probe: Probe) = 48f * probe.density

    @Test
    fun aMouseCarriesAnEventToAnotherDayAndTimeKeepingItsLength() = runComposeUiTest {
        val probe = calendar()
        val from = boundsOf(review, probe).center
        val by = Offset(dayWidth(probe), hour(probe))
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveBy(by / 10f) }
            release()
        }
        waitForIdle()
        val (event, start, end) = probe.moves.single()
        assertEquals(review, event)
        assertEquals(LocalDateTime(2026, 10, 15, 11, 0), start)
        assertEquals(LocalDateTime(2026, 10, 15, 12, 0), end, "the move changed the event's length")
        assertTrue(probe.resizes.isEmpty() && probe.creates.isEmpty())
    }

    @Test
    fun aCarryLetGoWhereItStartedReportsNothing() = runComposeUiTest {
        val probe = calendar()
        val from = boundsOf(review, probe).center
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(5) { moveBy(Offset(0f, hour(probe) / 5f)) }
            repeat(5) { moveBy(Offset(0f, -hour(probe) / 5f)) }
            release()
        }
        waitForIdle()
        assertTrue(probe.moves.isEmpty(), "an event put back where it was was reported moved: ${probe.moves}")
    }

    @Test
    fun theBottomEdgeDraggedMakesItLongerByWholeSteps() = runComposeUiTest {
        val probe = calendar()
        val bounds = boundsOf(review, probe)
        onRoot().performMouseInput {
            moveTo(Offset(bounds.center.x, bounds.bottom - 3f * probe.density))
            press()
            repeat(6) { moveBy(Offset(0f, hour(probe) / 12f)) }
            release()
        }
        waitForIdle()
        val (event, start, end) = probe.resizes.single()
        assertEquals(review, event)
        assertEquals(review.start, start)
        assertEquals(LocalDateTime(2026, 10, 14, 11, 30), end)
        assertTrue(probe.moves.isEmpty(), "dragging the edge moved the event")
    }

    @Test
    fun anEdgeCannotBeDraggedPastTheOtherOne() = runComposeUiTest {
        val probe = calendar()
        val bounds = boundsOf(review, probe)
        onRoot().performMouseInput {
            moveTo(Offset(bounds.center.x, bounds.bottom - 3f * probe.density))
            press()
            repeat(10) { moveBy(Offset(0f, -hour(probe) * 2f / 10f)) }
            release()
        }
        waitForIdle()
        assertEquals(LocalDateTime(2026, 10, 14, 10, 15), probe.resizes.single().third, "an event went shorter than one step")
    }

    @Test
    fun aMouseDragOnEmptyTimeDrawsOutANewEvent() = runComposeUiTest {
        val probe = calendar()
        val review = boundsOf(review, probe)
        // An hour under the review's start, on the Monday: 11:00, and an hour down from there.
        val monday = Offset(review.center.x - dayWidth(probe) * 2f, review.top + hour(probe) + 2f)
        onRoot().performMouseInput {
            moveTo(monday)
            press()
            repeat(8) { moveBy(Offset(0f, hour(probe) / 8f)) }
            release()
        }
        waitForIdle()
        assertEquals(LocalDateTime(2026, 10, 12, 11, 0) to LocalDateTime(2026, 10, 12, 12, 0), probe.creates.single())
    }

    @Test
    fun aFingerSweptOverEmptyTimeScrollsAndDrawsNothing() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Touch)
        val review = boundsOf(review, probe)
        val monday = Offset(review.center.x - dayWidth(probe) * 2f, review.top + hour(probe) * 2f)
        onRoot().performTouchInput {
            down(monday)
            repeat(10) { moveBy(Offset(0f, -hour(probe) / 5f)) }
            up()
        }
        waitForIdle()
        assertTrue(probe.creates.isEmpty(), "a swipe on empty time drew out an event: ${probe.creates}")
        // The canary: the swipe really did reach the hours, and scrolled them.
        assertTrue(boundsOf(this@EventCalendarEditTest.review, probe).top < review.top - hour(probe), "the hours did not scroll, so this measured nothing")
    }

    @Test
    fun aFingerHeldThenDraggedDrawsOutANewEvent() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Touch)
        val review = boundsOf(review, probe)
        val monday = Offset(review.center.x - dayWidth(probe) * 2f, review.top + hour(probe) + 2f)
        onRoot().performTouchInput {
            down(monday)
            advanceEventTime(800)
            repeat(8) { moveBy(Offset(0f, hour(probe) / 8f)) }
            up()
        }
        waitForIdle()
        assertEquals(LocalDateTime(2026, 10, 12, 11, 0) to LocalDateTime(2026, 10, 12, 12, 0), probe.creates.single())
    }

    @Test
    fun theKeyboardPicksUpMovesAndPutsDown() = runComposeUiTest {
        val probe = calendar()
        onNode(hasContentDescription(eventDescription(review, probe.formats, probe.strings))).requestFocus()
        onRoot().performKeyInput {
            pressKey(Key.Spacebar)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionRight)
            pressKey(Key.Enter)
        }
        waitForIdle()
        val (_, start, end) = probe.moves.single()
        assertEquals(LocalDateTime(2026, 10, 15, 10, 30), start)
        assertEquals(LocalDateTime(2026, 10, 15, 11, 30), end)
    }

    @Test
    fun shiftAndAnArrowMakeItLongerFromTheKeyboard() = runComposeUiTest {
        val probe = calendar()
        onNode(hasContentDescription(eventDescription(review, probe.formats, probe.strings))).requestFocus()
        onRoot().performKeyInput {
            pressKey(Key.Spacebar)
            withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) }
            pressKey(Key.Enter)
        }
        waitForIdle()
        assertEquals(LocalDateTime(2026, 10, 14, 11, 15), probe.resizes.single().third)
        assertTrue(probe.moves.isEmpty(), "a change of length from the keyboard was reported as a move")
    }

    @Test
    fun escapePutsItBack() = runComposeUiTest {
        val probe = calendar()
        onNode(hasContentDescription(eventDescription(review, probe.formats, probe.strings))).requestFocus()
        onRoot().performKeyInput {
            pressKey(Key.Spacebar)
            pressKey(Key.DirectionDown)
            pressKey(Key.Escape)
        }
        waitForIdle()
        assertTrue(probe.moves.isEmpty() && probe.resizes.isEmpty())
    }

    @Test
    fun aScreenReaderMovesAndLengthensByActions() = runComposeUiTest {
        val probe = calendar()
        val node = onNode(hasContentDescription(eventDescription(review, probe.formats, probe.strings))).fetchSemanticsNode()
        val actions = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(
            listOf(probe.strings.moveEarlier, probe.strings.moveLater, probe.strings.moveToPreviousDay, probe.strings.moveToNextDay, probe.strings.makeLonger, probe.strings.makeShorter),
            actions.map { it.label },
        )
        runOnIdle { actions.first { it.label == probe.strings.moveLater }.action() }
        runOnIdle { actions.first { it.label == probe.strings.makeLonger }.action() }
        assertEquals(LocalDateTime(2026, 10, 14, 10, 15), probe.moves.single().second)
        assertEquals(LocalDateTime(2026, 10, 14, 11, 15), probe.resizes.single().third)
    }

    @Test
    fun anEventTheAppOnlyShowsCannotBeMoved() = runComposeUiTest {
        val probe = calendar()
        val node = onNode(hasContentDescription(eventDescription(holiday, probe.formats, probe.strings))).fetchSemanticsNode()
        assertTrue(node.config.getOrNull(SemanticsActions.CustomActions).isNullOrEmpty(), "a non-editable event offers moves")
        val from = node.boundsInRoot.center
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(5) { moveBy(Offset(-dayWidth(probe) / 5f, 0f)) }
            release()
        }
        waitForIdle()
        assertTrue(probe.moves.isEmpty())
    }

    @Test
    fun aMonthsBarCarriedOverADayMovesByThatDay() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month)
        val from = boundsOf(trip, probe).center
        val width = 900f * probe.density / DaysPerWeek
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(8) { moveBy(Offset(width / 8f, 0f)) }
            release()
        }
        waitForIdle()
        val (_, start, end) = probe.moves.single()
        assertEquals(trip.start.plusMinutes(MinutesPerDay.toLong()), start)
        assertEquals(trip.end.plusMinutes(MinutesPerDay.toLong()), end)
    }
}
