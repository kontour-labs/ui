package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
        inScrollingPage: Boolean = false,
    ): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalInputModality provides modality) {
                    // As the demo has it: a calendar of its own height in a page that scrolls.
                    val page = if (inScrollingPage) Modifier.verticalScroll(rememberScrollState()) else Modifier
                    OverlayHost(Modifier.size(900.dp, 900.dp).then(page)) {
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

    /**
     * Its end dragged to the foot of the hours and on: it stops at midnight.
     * It followed the pointer into the next day's column, and the event ran
     * on over days.
     */
    @Test
    fun anEndDraggedPastMidnightStopsThere() = runComposeUiTest {
        val late = CalendarEvent(4, "Late call", LocalDateTime(2026, 10, 14, 17, 0), LocalDateTime(2026, 10, 14, 18, 0))
        val probe = calendar(events = listOf(late))
        val bounds = boundsOf(late, probe)
        onRoot().performMouseInput {
            moveTo(Offset(bounds.center.x, bounds.bottom - 3f * probe.density))
            press()
            // Down to the foot of the calendar, and across into Thursday,
            // held there while the hours scroll on to their end.
            val to = Offset(bounds.center.x + dayWidth(probe), 900f * probe.density - 2f)
            val from = currentPosition
            repeat(20) { moveTo(from + (to - from) * ((it + 1) / 20f)) }
            repeat(30) {
                advanceEventTime(100)
                moveTo(to + Offset(0f, if (it % 2 == 0) -1f else 0f))
            }
            release()
        }
        waitForIdle()
        val (_, start, end) = probe.resizes.singleOrNull() ?: error("no resize: moves ${probe.moves}, bounds $bounds")
        assertEquals(late.start, start)
        assertEquals(LocalDateTime(2026, 10, 15, 0, 0), end, "the end went past its day's midnight")
    }

    @Test
    fun shiftAndAnArrowStopAtMidnight() = runComposeUiTest {
        val late = CalendarEvent(4, "Late call", LocalDateTime(2026, 10, 14, 23, 0), LocalDateTime(2026, 10, 14, 23, 30))
        val probe = calendar(events = listOf(late))
        onNode(hasContentDescription(eventDescription(late, probe.formats, probe.strings))).requestFocus()
        onRoot().performKeyInput {
            pressKey(Key.Spacebar)
            repeat(6) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
            pressKey(Key.Enter)
        }
        waitForIdle()
        assertEquals(LocalDateTime(2026, 10, 15, 0, 0), probe.resizes.single().third, "the keyboard ran the end past midnight")
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
    fun aFingerHeldOnAnEventCarriesItToAnotherDayAndTime() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Touch, inScrollingPage = true)
        val from = boundsOf(review, probe).center
        val by = Offset(dayWidth(probe), hour(probe))
        onRoot().performTouchInput {
            down(from)
            // A real finger is never still: a few pixels of wander while it holds.
            repeat(8) { i ->
                advanceEventTime(60)
                moveTo(from + Offset(if (i % 2 == 0) 3f else -2f, if (i % 3 == 0) 2f else -1f))
            }
            advanceEventTime(200)
            repeat(12) { moveBy(by / 12f) }
            up()
        }
        waitForIdle()
        val (event, start, end) = probe.moves.singleOrNull() ?: error("the held event was not carried: no move reported")
        assertEquals(review.id, event.id)
        assertEquals(LocalDateTime(2026, 10, 15, 11, 0) to LocalDateTime(2026, 10, 15, 12, 0), start to end)
    }

    @Test
    fun aQuickSwipeOverEmptyTimeStillPages() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Touch)
        val review = boundsOf(review, probe)
        val tuesday = Offset(review.center.x - dayWidth(probe), review.top + hour(probe) * 2f)
        onRoot().performTouchInput {
            down(tuesday)
            repeat(6) {
                advanceEventTime(16)
                moveBy(Offset(-dayWidth(probe) * 0.6f, 0f))
            }
            up()
        }
        waitForIdle()
        val nextMonday = probe.formats.date(LocalDate(2026, 10, 19), DateStyle.Full)
        onNode(hasContentDescription(nextMonday) and hasClickAction()).assertExists("a sideways swipe did not page to the next week")
        assertTrue(probe.creates.isEmpty() && probe.moves.isEmpty())
    }

    @Test
    fun aFingerThatScrollsTheHoursThenStopsDrawsNothing() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Touch)
        val review = boundsOf(review, probe)
        val monday = Offset(review.center.x - dayWidth(probe) * 2f, review.top + hour(probe) * 2f)
        onRoot().performTouchInput {
            down(monday)
            // Slowly, as a reader reading: the hours move with the finger.
            repeat(20) {
                advanceEventTime(40)
                moveBy(Offset(0f, -hour(probe) / 10f))
            }
            // And stops, still down, for longer than a long press.
            advanceEventTime(900)
            moveBy(Offset(0f, 1f))
            advanceEventTime(100)
            up()
        }
        waitForIdle()
        assertTrue(probe.creates.isEmpty(), "stopping a scroll drew out an event: ${probe.creates}")
        // The canary: the hours really did scroll under the finger.
        assertTrue(boundsOf(this@EventCalendarEditTest.review, probe).top < review.top - hour(probe), "the hours did not scroll, so this measured nothing")
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

    /**
     * The middle of the trip bar's first day: the bar is two days long, so its
     * own centre is on the line between them, where a carry has no day yet.
     */
    private fun ComposeUiTest.tripFirstDay(probe: Probe): Offset {
        val bar = boundsOf(trip, probe)
        return Offset(bar.left + 900f * probe.density / DaysPerWeek / 2f, bar.center.y)
    }

    @Test
    fun aMonthsBarCarriedOverADayMovesByThatDay() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month)
        val from = tripFirstDay(probe)
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

    @Test
    fun aFingerHeldOnAMonthsBarCarriesItByADay() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month, modality = InputModality.Touch, inScrollingPage = true)
        val from = tripFirstDay(probe)
        val width = 900f * probe.density / DaysPerWeek
        onRoot().performTouchInput {
            down(from)
            repeat(8) { i ->
                advanceEventTime(60)
                moveTo(from + Offset(if (i % 2 == 0) 3f else -2f, 1f))
            }
            advanceEventTime(200)
            repeat(8) { moveBy(Offset(width / 8f, 0f)) }
            up()
        }
        waitForIdle()
        val (_, start, _) = probe.moves.singleOrNull() ?: error("the held bar was not carried")
        assertEquals(trip.start.plusMinutes(MinutesPerDay.toLong()), start)
    }

    /** Carries the trip's bar from the middle of its first day to [share] of the way into the next, and lets go. */
    private fun ComposeUiTest.carryTrip(probe: Probe, share: Float) {
        val from = tripFirstDay(probe)
        val width = 900f * probe.density / DaysPerWeek
        // From the middle of its day: half a day to the line, then [share] past it.
        val to = from.x + width * (0.5f + share)
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (to - from.x) * (it + 1) / 10f, from.y)) }
            release()
        }
        waitForIdle()
    }

    @Test
    fun aBarJustOverTheLineIntoTheNextDayStaysWhereItWas() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month)
        carryTrip(probe, share = 0.05f)
        assertTrue(probe.moves.isEmpty(), "5% into the next day moved it: ${probe.moves}")
    }

    @Test
    fun aBarWellIntoTheNextDayMovesThere() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month)
        carryTrip(probe, share = 0.3f)
        val (_, start, _) = probe.moves.singleOrNull() ?: error("30% into the next day did not move it")
        assertEquals(trip.start.plusMinutes(MinutesPerDay.toLong()), start)
    }
}
