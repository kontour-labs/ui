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
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
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
 * What a held finger, or a secondary click, offers on the calendar — asked for
 * as *"when you tap and hold, it should show a dropdown menu with an option to
 * create a new event, rather than just doing it"*, and an event's own menu to
 * adjust its time or delete it — the handles that "Adjust time" gives going
 * at a tap anywhere else, and a carry that pages on into the next week or month
 * rather than letting go.
 */
@OptIn(ExperimentalTestApi::class)
class EventCalendarMenuTest {

    private val wednesday = LocalDate(2026, 10, 14)
    private val review = CalendarEvent(1, "Design review", LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 11, 0))
    private val trip = CalendarEvent(3, "Perth trip", startDate = LocalDate(2026, 10, 20), endDate = LocalDate(2026, 10, 21))

    private class Probe {
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        var density = 1f
        val moves = mutableListOf<Triple<CalendarEvent, LocalDateTime, LocalDateTime>>()
        val resizes = mutableListOf<Triple<CalendarEvent, LocalDateTime, LocalDateTime>>()
        val creates = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        val deletes = mutableListOf<CalendarEvent>()
    }

    private fun ComposeUiTest.calendar(
        view: CalendarView = CalendarView.Week,
        modality: InputModality = InputModality.Touch,
        reduceMotion: Boolean = true,
    ): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = reduceMotion) {
                CompositionLocalProvider(LocalInputModality provides modality) {
                    OverlayHost(Modifier.size(900.dp, 900.dp)) {
                        probe.formats = Theme.formats
                        probe.strings = Theme.strings
                        probe.density = LocalDensity.current.density
                        EventCalendar(
                            state = rememberEventCalendarState(
                                initialDate = wednesday,
                                initialView = view,
                                initialScrollTime = LocalTime(8, 0),
                                firstDayOfWeek = DayOfWeek.MONDAY,
                            ),
                            events = listOf(review, trip),
                            now = null,
                            onEventMove = { event, start, end -> probe.moves += Triple(event, start, end) },
                            onEventResize = { event, start, end -> probe.resizes += Triple(event, start, end) },
                            onCreateRequest = { start, end -> probe.creates += start to end },
                            onEventDelete = { probe.deletes += it },
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

    private fun ComposeUiTest.dayWidth(probe: Probe): Float {
        fun heading(date: LocalDate) = onNode(hasContentDescription(probe.formats.date(date, DateStyle.Full)) and hasClickAction())
            .fetchSemanticsNode().boundsInRoot.center.x
        return heading(LocalDate(2026, 10, 15)) - heading(wednesday)
    }

    private fun hour(probe: Probe) = 48f * probe.density

    private fun ComposeUiTest.menuItem(label: String) = onNode(hasText(label) and hasClickAction())

    /** A finger held at [at] past a long press, and let go where it was. */
    private fun ComposeUiTest.hold(at: Offset) {
        onRoot().performTouchInput {
            down(at)
            advanceEventTime(800)
            up()
        }
        waitForIdle()
    }

    /** Monday at 11:00 in the hours, an hour under the review's start. */
    private fun ComposeUiTest.mondayEleven(probe: Probe): Offset {
        val review = boundsOf(review, probe)
        return Offset(review.center.x - dayWidth(probe) * 2f, review.top + hour(probe) + 2f)
    }

    @Test
    fun aFingerHeldOnEmptyTimeOffersANewEventAndMakesNothingUntilItIsChosen() = runComposeUiTest {
        val probe = calendar()
        hold(mondayEleven(probe))
        assertTrue(probe.creates.isEmpty(), "holding made an event without asking: ${probe.creates}")
        menuItem(probe.strings.newEvent).performClick()
        waitForIdle()
        assertEquals(listOf(LocalDateTime(2026, 10, 12, 11, 0) to LocalDateTime(2026, 10, 12, 12, 0)), probe.creates)
        menuItem(probe.strings.newEvent).assertDoesNotExist()
    }

    @Test
    fun aNewEventsMenuDismissedMakesNothing() = runComposeUiTest {
        val probe = calendar()
        hold(mondayEleven(probe))
        menuItem(probe.strings.newEvent).assertExists()
        // A tap away from it, on the header's title.
        onRoot().performTouchInput { down(Offset(8f, 8f)); up() }
        waitForIdle()
        menuItem(probe.strings.newEvent).assertDoesNotExist()
        assertTrue(probe.creates.isEmpty(), "a dismissed menu made its event: ${probe.creates}")
    }

    @Test
    fun aFingerHeldThenDraggedDrawsOutWithNoMenuLeftOpen() = runComposeUiTest {
        val probe = calendar()
        onRoot().performTouchInput {
            down(mondayEleven(probe))
            advanceEventTime(800)
            repeat(8) { moveBy(Offset(0f, hour(probe) / 4f)) }
            up()
        }
        waitForIdle()
        assertEquals(LocalDateTime(2026, 10, 12, 11, 0) to LocalDateTime(2026, 10, 12, 13, 0), probe.creates.single())
        menuItem(probe.strings.newEvent).assertDoesNotExist()
    }

    @Test
    fun aFingerHeldOnAnEventOffersItsTimeAndItsDeletion() = runComposeUiTest {
        val probe = calendar()
        hold(boundsOf(review, probe).center)
        menuItem(probe.strings.adjustTime).assertExists()
        menuItem(probe.strings.deleteEvent).performClick()
        waitForIdle()
        assertEquals(listOf(review), probe.deletes)
        assertTrue(probe.moves.isEmpty(), "a hold let go where it was moved the event")
    }

    @Test
    fun adjustTimeGivesTheEdgesHandlesUntilATapElsewhere() = runComposeUiTest {
        val probe = calendar()
        hold(boundsOf(review, probe).center)
        menuItem(probe.strings.adjustTime).performClick()
        waitForIdle()
        val bounds = boundsOf(review, probe)
        val edge = Offset(bounds.center.x, bounds.bottom - 3f * probe.density)
        // A handle: dragged at once, no hold.
        onRoot().performTouchInput {
            down(edge)
            repeat(6) { moveBy(Offset(0f, hour(probe) / 12f)) }
            up()
        }
        waitForIdle()
        assertEquals(LocalDateTime(2026, 10, 14, 11, 30), probe.resizes.single().third)
        // A tap on empty time: the handles go, and the same drag is a scroll of the hours.
        val tapAt = mondayEleven(probe)
        onRoot().performTouchInput { down(tapAt); up() }
        waitForIdle()
        onRoot().performTouchInput {
            down(edge)
            repeat(6) { moveBy(Offset(0f, hour(probe) / 12f)) }
            up()
        }
        waitForIdle()
        assertEquals(1, probe.resizes.size, "the handles were still there after a tap elsewhere")
    }

    @Test
    fun aHeldEventDraggedAwayClosesItsMenuAndMoves() = runComposeUiTest {
        val probe = calendar()
        val from = boundsOf(review, probe).center
        onRoot().performTouchInput {
            down(from)
            advanceEventTime(800)
            repeat(8) { moveBy(Offset(0f, hour(probe) / 8f)) }
            up()
        }
        waitForIdle()
        assertEquals(LocalDateTime(2026, 10, 14, 11, 0), probe.moves.single().second)
        menuItem(probe.strings.deleteEvent).assertDoesNotExist()
    }

    @Test
    fun aSecondaryClickOnAnEventOpensItsMenu() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Mouse)
        onRoot().performMouseInput {
            moveTo(boundsOf(review, probe).center)
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
        }
        waitForIdle()
        // A mouse has its edges to drag already: only the deletion.
        menuItem(probe.strings.adjustTime).assertDoesNotExist()
        menuItem(probe.strings.deleteEvent).performClick()
        waitForIdle()
        assertEquals(listOf(review), probe.deletes)
    }

    @Test
    fun deleteFromTheKeyboardAndAScreenReadersAction() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Keyboard)
        val node = onNode(hasContentDescription(eventDescription(review, probe.formats, probe.strings)))
        val actions = node.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        runOnIdle { actions.first { it.label == probe.strings.deleteEvent }.action() }
        node.requestFocus()
        node.performKeyInput { pressKey(Key.Delete) }
        waitForIdle()
        assertEquals(listOf(review, review), probe.deletes)
    }

    @Test
    fun aCarryHeldAtTheWeeksEdgePagesOnAndLandsInTheNextWeek() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Mouse)
        val from = boundsOf(review, probe).center
        val edge = 900f * probe.density - 6f
        val width = dayWidth(probe)
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (edge - from.x) * (it + 1) / 10f, from.y)) }
        }
        // Held there: the next week. The clock by hand, since a carry held at
        // the edge goes on paging for as long as it is held there.
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        val nextMonday = probe.formats.date(LocalDate(2026, 10, 19), DateStyle.Full)
        onNode(hasContentDescription(nextMonday) and hasClickAction()).assertExists("holding at the edge did not page on")
        // And on, to its Wednesday, and let go.
        onRoot().performMouseInput {
            val at = currentPosition
            repeat(10) { moveTo(Offset(at.x - width * 4.5f * (it + 1) / 10f, at.y)) }
            release()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        val (event, start, end) = probe.moves.singleOrNull() ?: error("the carry let go when the week paged")
        assertEquals(review.id, event.id)
        assertEquals(LocalDate(2026, 10, 21), start.date)
        // At whatever time the pointer was level with: the next week has an all-day
        // row this one has not, and the hours are lower by it.
        assertEquals(60L, end.epochMinute - start.epochMinute, "the carry changed the event's length")
    }

    /**
     * Paged twice, the carry still follows: the days it looked for its own in
     * were the first page's, kept from when the calendar last composed, and at
     * the second page none of them were on show — the event stopped following
     * the finger.
     */
    @Test
    fun aCarryHeldLongEnoughToPageTwiceGoesOnFollowing() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Mouse)
        val from = boundsOf(review, probe).center
        val edge = 900f * probe.density - 6f
        val width = dayWidth(probe)
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (edge - from.x) * (it + 1) / 10f, from.y)) }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_700)
        val twoOn = probe.formats.date(LocalDate(2026, 10, 26), DateStyle.Full)
        onNode(hasContentDescription(twoOn) and hasClickAction()).assertExists("holding at the edge did not page on twice")
        onRoot().performMouseInput {
            val at = currentPosition
            repeat(10) { moveTo(Offset(at.x - width * 4.5f * (it + 1) / 10f, at.y)) }
            release()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        val (_, start, _) = probe.moves.singleOrNull() ?: error("the carry let go when the week paged")
        assertEquals(LocalDate(2026, 10, 28), start.date, "the carry stopped following the pointer after the second page")
    }

    /**
     * With the pager's slide: a pointer held still at the edge carries the
     * event into the week paged in, without having to move to be followed.
     */
    @Test
    fun withMotionAStillPointerIsFollowedIntoTheWeekPagedIn() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Mouse, reduceMotion = false)
        val from = boundsOf(review, probe).center
        val edge = 900f * probe.density - 6f
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (edge - from.x) * (it + 1) / 10f, from.y)) }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_250)
        onRoot().performMouseInput { release() }
        mainClock.autoAdvance = true
        waitForIdle()
        val (_, start, _) = probe.moves.singleOrNull() ?: error("the carry let go when the week paged")
        assertTrue(
            start.date >= LocalDate(2026, 10, 19) && start.date <= LocalDate(2026, 10, 25),
            "a still pointer was left on the week before: $start",
        )
    }

    /** As [aMonthsBarCarriedToTheEdgeGoesOnIntoTheNextMonth], with the slide: two months show the same spare days while it runs. */
    @Test
    fun withMotionAMonthsBarGoesOnIntoTheNextMonth() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month, modality = InputModality.Mouse, reduceMotion = false)
        val bar = boundsOf(trip, probe)
        val cell = 900f * probe.density / DaysPerWeek
        val from = Offset(bar.left + cell / 2f, bar.center.y)
        val edge = 900f * probe.density - 6f
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (edge - from.x) * (it + 1) / 10f, from.y)) }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_250)
        onRoot().performMouseInput {
            val at = currentPosition
            repeat(10) { moveTo(Offset(at.x - cell * 3f * (it + 1) / 10f, at.y)) }
            release()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        val (_, start, _) = probe.moves.singleOrNull() ?: error("the carry let go when the month paged")
        assertEquals(kotlinx.datetime.Month.NOVEMBER, start.date.month, "the bar did not go on into November: $start")
    }

    @Test
    fun aMonthsBarCarriedToTheEdgeGoesOnIntoTheNextMonth() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month, modality = InputModality.Mouse)
        val bar = boundsOf(trip, probe)
        val cell = 900f * probe.density / DaysPerWeek
        val from = Offset(bar.left + cell / 2f, bar.center.y)
        val edge = 900f * probe.density - 6f
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (edge - from.x) * (it + 1) / 10f, from.y)) }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        onRoot().performMouseInput {
            val at = currentPosition
            repeat(10) { moveTo(Offset(at.x - cell * 3f * (it + 1) / 10f, at.y)) }
            release()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        val (_, start, _) = probe.moves.singleOrNull() ?: error("the carry let go when the month paged")
        assertEquals(kotlinx.datetime.Month.NOVEMBER, start.date.month, "the bar did not go on into November: $start")
    }

    @Test
    fun aFingerHeldJustBesideAMonthsLineTakesIt() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month)
        val bar = boundsOf(trip, probe)
        val cell = 900f * probe.density / DaysPerWeek
        // Under the bar, off it, as a fingertip lands on something under half its height.
        val beside = Offset(bar.left + cell / 2f, bar.bottom + 6f * probe.density)
        onRoot().performTouchInput {
            down(beside)
            advanceEventTime(800)
            repeat(8) { moveBy(Offset(cell / 8f, 0f)) }
            up()
        }
        waitForIdle()
        val (event, start, _) = probe.moves.singleOrNull() ?: error("a hold beside the bar did not take it")
        assertEquals(trip.id, event.id)
        assertEquals(trip.start.plusMinutes(MinutesPerDay.toLong()), start)
    }

    @Test
    fun aFingerHeldOnAnEmptyDayInTheMonthOffersANewDay() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month)
        // Tuesday the 27th, which has nothing on it: low in its box, away from its number.
        val day = onNode(hasContentDescription(probe.formats.date(LocalDate(2026, 10, 27), DateStyle.Full)))
            .fetchSemanticsNode().boundsInRoot
        hold(Offset(day.center.x, day.bottom - 8f * probe.density))
        menuItem(probe.strings.newEvent).performClick()
        waitForIdle()
        val (start, end) = probe.creates.single()
        assertEquals(LocalTime(0, 0), start.time)
        assertEquals(start.date.plusDaysForTest(1).atMidnightForTest(), end)
    }

    private fun LocalDate.plusDaysForTest(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)
    private fun LocalDate.atMidnightForTest(): LocalDateTime = LocalDateTime(year, month, day, 0, 0)
}
