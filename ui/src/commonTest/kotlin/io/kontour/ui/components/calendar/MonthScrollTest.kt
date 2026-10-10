package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * A month that scrolls up and down by the week, as Apple's calendar does:
 * asked for as *"switch to up/down swiping between months, but snap to the
 * week rather than to the month"*, and *"line the days up vertically so weeks
 * sit under each other properly"* — a month's rows were its height shared by
 * its own four to six weeks, so the next month's were another height.
 */
@OptIn(ExperimentalTestApi::class)
class MonthScrollTest {

    private val periods = CalendarPeriods(DayOfWeek.MONDAY)

    // --- The rows and the months they show ---

    @Test
    fun aRowIsAWeekBothWaysRoundAcrossYearsAndBefore1970() {
        for (first in DayOfWeek.entries) {
            val p = CalendarPeriods(first)
            for (date in listOf(LocalDate(1969, 12, 31), LocalDate(1970, 1, 1), LocalDate(2026, 10, 14), LocalDate(1900, 3, 1), LocalDate(2400, 2, 29))) {
                val start = p.weekOfRow(p.rowOf(date))
                assertEquals(p.weekStart(date), start, "the row of $date starting on $first")
                assertEquals(first, start.dayOfWeek)
            }
        }
    }

    @Test
    fun aMonthIsScrolledToWithItsFirstWeekAtTheTop() {
        // October 2026 starts on a Thursday: its first row is Monday 28 September.
        assertEquals(periods.rowOf(LocalDate(2026, 9, 28)), periods.monthTarget(LocalDate(2026, 10, 14), rowsInView = 6))
        // Three rows in view, and the 30th: its week goes at the foot, not off it.
        assertEquals(periods.rowOf(LocalDate(2026, 10, 12)), periods.monthTarget(LocalDate(2026, 10, 30), rowsInView = 3))
    }

    @Test
    fun theMonthMostOnScreenIsTheCurrentOneOnceItIsClearlySo() {
        val october = periods.indexOf(LocalDate(2026, 10, 1), CalendarView.Month)
        val november = october + 1
        fun rows(firstWeek: LocalDate, offset: Int) = (0 until 7).map {
            VisibleRow(firstWeek.plus(it * 7, DateTimeUnit.DAY), offset + it * 100, offset + (it + 1) * 100)
        }
        // Scrolled to October's first week: October.
        assertEquals(october, periods.dominantMonth(rows(LocalDate(2026, 9, 28), 0), 600, november, hysteresis = 350f))
        // Two weeks on, November is most of it: November.
        assertEquals(november, periods.dominantMonth(rows(LocalDate(2026, 10, 19), 0), 600, october, hysteresis = 350f))
        // Near an even split — October's 20 days on screen to November's 22 —
        // whichever it was about, it stays.
        assertEquals(october, periods.dominantMonth(rows(LocalDate(2026, 10, 12), 0), 600, october, hysteresis = 350f))
        assertEquals(november, periods.dominantMonth(rows(LocalDate(2026, 10, 12), 0), 600, november, hysteresis = 350f))
    }

    // --- Scrolling it ---

    private class Probe {
        lateinit var state: EventCalendarState
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        var sunken = androidx.compose.ui.graphics.Color.Unspecified
        var moved = false
    }

    private fun androidx.compose.ui.test.ComposeUiTest.month(): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = false) {
                OverlayHost(Modifier.size(700.dp, 800.dp)) {
                    probe.formats = Theme.formats
                    probe.strings = Theme.strings
                    probe.sunken = Theme.colours.surfaceSunken
                    probe.state = rememberEventCalendarState(LocalDate(2026, 10, 14), CalendarView.Month, firstDayOfWeek = DayOfWeek.MONDAY)
                    EventCalendar(state = probe.state, events = emptyList(), now = null, modifier = Modifier.size(700.dp, 800.dp))
                }
            }
        }
        waitForIdle()
        return probe
    }

    /** Every day box on screen, by its date. */
    private fun androidx.compose.ui.test.ComposeUiTest.days(probe: Probe): Map<LocalDate, Rect> {
        val dates = (0 until 140).map { LocalDate(2026, 8, 31).plus(it, DateTimeUnit.DAY) }
        val names = dates.associateBy { probe.formats.date(it, DateStyle.Full) }
        return onAllNodes(SemanticsMatcher("a day's box") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { d -> names.keys.any { d.startsWith(it) } }
        } and hasClickAction()).fetchSemanticsNodes().mapNotNull { node ->
            val description = node.config[SemanticsProperties.ContentDescription].first()
            val date = names.entries.firstOrNull { description.startsWith(it.key) }?.value ?: return@mapNotNull null
            date to node.boundsInRoot
        }.toMap()
    }

    @Test
    fun itOpensWithTheMonthsFirstWeekAtTheTopAndEveryRowAsTall() = runComposeUiTest {
        val probe = month()
        val days = days(probe)
        val top = days.values.minOf { it.top }
        assertEquals(top, days.getValue(LocalDate(2026, 9, 28)).top, "the first row is not the week October starts in")
        val heights = days.values.filter { it.top >= top }.map { it.height }
        assertTrue(heights.max() - heights.min() < 1f, "the rows are not all as tall: ${heights.distinct()}")
    }

    @Test
    fun swipedUpTheNextMonthIsTheOneItIsAboutAndItSettlesOnAWeek() = runComposeUiTest {
        val probe = month()
        val top = days(probe).values.minOf { it.top }
        onRoot().performTouchInput { swipeUp(startY = height * 0.85f, endY = height * 0.4f, durationMillis = 400) }
        waitForIdle()
        assertEquals(Month.NOVEMBER, probe.state.date.month, "scrolled up into November, the calendar is still about ${probe.state.date}")
        onNode(hasText(probe.formats.monthAndYear(LocalDate(2026, 11, 1)))).assertExists("the title did not follow")
        // A row's top on the box's: settled on a week, not between two.
        val after = days(probe).values.map { it.top }
        assertTrue(after.any { abs(it - top) < 1f }, "after the swipe no row starts at the top: $after, the top at $top")
    }

    @Test
    fun theHeadersNextScrollsToTheNextMonthsFirstWeek() = runComposeUiTest {
        val probe = month()
        val top = days(probe).values.minOf { it.top }
        onNode(hasContentDescription(probe.strings.nextMonth) and hasClickAction()).performClick()
        waitForIdle()
        // 1 November 2026 is a Sunday: its week starts on 26 October.
        val first = days(probe)[LocalDate(2026, 10, 26)] ?: error("November's first week is not on screen")
        assertTrue(abs(first.top - top) < 1f, "November's first week is at ${first.top}, not the top $top")
        assertEquals(LocalDate(2026, 11, 14), probe.state.date)
    }

    @Test
    fun theDaysQuietedAreTheOnesOutsideTheMonthOnScreen() = runComposeUiTest {
        val probe = month()
        // Inside a box, clear of its number: its ground.
        fun ground(date: LocalDate): androidx.compose.ui.graphics.Color {
            val box = days(probe).getValue(date)
            return onRoot().captureToImage().toPixelMap()[(box.right - 6f).toInt(), (box.bottom - 6f).toInt()]
        }
        assertTrue(ground(LocalDate(2026, 10, 26)) != probe.sunken, "October's 26th is quiet in October")
        onNode(hasContentDescription(probe.strings.nextMonth) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(probe.sunken, ground(LocalDate(2026, 10, 26)), "October's 26th is not quiet in November")
        assertTrue(ground(LocalDate(2026, 11, 3)) != probe.sunken, "November's 3rd is quiet in November")
    }

    // --- Zoomed: asked for as *"pinch to zoom: change the row height so you
    // can show more/less weeks on screen (e.g. zoom in -> ~3 week view, zoom
    // out -> full month)"*, and, taller, *"show time and location"*. ---

    @Test
    fun zoomedTheRowUnderTheFingersStaysThere() {
        // Rows 100px tall, the list's top 2.5 rows down; the focus 250px down.
        val top = 2.5
        val after = zoomAnchor(top, fromY = 250f, toY = 250f, oldRow = 100f, newRow = 200f)
        // What was under the focus — row 5 — is under it still.
        assertEquals(top + 250.0 / 100.0, after + 250.0 / 200.0, 1e-9)
    }

    /**
     * *"when pinching to zoom in the month view, it's a bit jittery"*: the rows
     * are numbered from a million, where a float keeps a sixteenth of a row — a
     * hundred-pixel row's week under the fingers moved in six-pixel steps.
     */
    @Test
    fun aZoomAMillionRowsInIsAsExactAsOneAtTheStart() {
        val top = WeekOrigin + 0.37
        var at = top
        var row = 100f
        // Twenty small steps of a spread, each about the same point.
        repeat(20) {
            val next = row * 1.03f
            at = zoomAnchor(at, fromY = 250f, toY = 250f, oldRow = row, newRow = next)
            row = next
        }
        val under = at + 250.0 / row
        assertEquals(top + 2.5, under, 1e-6, "the week under the fingers drifted ${(under - top - 2.5) * row}px")
    }

    /**
     * A month given next to no height still has rows a date and an event tall.
     * Rows of nothing made a lazy list compose every one of its two million
     * weeks to fill the space, and run out of memory doing it: a calendar with
     * no overlay host, whose six views stacked into a header taller than it.
     */
    @Test
    fun aMonthWithNoRoomStillHasRowsWorthLayingOut() {
        assertEquals(40f, monthRowPx(viewportPx = 0f, rows = 6f, least = 40f))
        assertEquals(40f, monthRowPx(viewportPx = 90f, rows = 3f, least = 40f))
        assertEquals(100f, monthRowPx(viewportPx = 300f, rows = 3f, least = 40f))
    }

    @Test
    fun aCalendarSqueezedUnderItsHeaderStillDraws() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(reduceMotion = true) {
                // No overlay host, so the views stay side by side, stacked.
                EventCalendar(
                    state = rememberEventCalendarState(LocalDate(2026, 10, 14), CalendarView.Month, firstDayOfWeek = DayOfWeek.MONDAY),
                    events = emptyList(),
                    now = null,
                    modifier = Modifier.size(360.dp, 200.dp),
                )
            }
        }
        repeat(3) { mainClock.advanceTimeByFrame() }
        val weeks = onAllNodes(hasText("14"), useUnmergedTree = true).fetchSemanticsNodes().size
        assertTrue(weeks <= 2, "the squeezed month composed the 14th $weeks times")
    }

    /** Fingers that move together as well as apart take the weeks with them. */
    @Test
    fun fingersMovingTogetherPanTheWeeks() {
        val after = zoomAnchor(10.0, fromY = 250f, toY = 300f, oldRow = 100f, newRow = 100f)
        assertEquals(9.5, after, 1e-9, "50px down at 100px a row is half a row")
    }

    @Test
    fun tallerRowsGiveEachEventMoreLines() {
        // A date 33px, a line 14px, 4px above and below: an event of one line is 22px.
        assertEquals(1, itemLines(rowPx = 98f, dateLabelPx = 33f, linePx = 14f, padPx = 4f))
        assertEquals(2, itemLines(rowPx = 147f, dateLabelPx = 33f, linePx = 14f, padPx = 4f))
        assertEquals(3, itemLines(rowPx = 196f, dateLabelPx = 33f, linePx = 14f, padPx = 4f))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.zoomable(events: List<CalendarEvent> = emptyList()): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = false) {
                OverlayHost(Modifier.size(700.dp, 800.dp)) {
                    probe.formats = Theme.formats
                    probe.strings = Theme.strings
                    probe.sunken = Theme.colours.surfaceSunken
                    probe.state = rememberEventCalendarState(LocalDate(2026, 10, 14), CalendarView.Month, firstDayOfWeek = DayOfWeek.MONDAY)
                    EventCalendar(
                        state = probe.state,
                        events = events,
                        now = null,
                        onEventMove = { _, _, _ -> probe.moved = true },
                        onCreateRequest = { _, _ -> probe.moved = true },
                        modifier = Modifier.size(700.dp, 800.dp),
                    )
                }
            }
        }
        waitForIdle()
        return probe
    }

    @Test
    fun twoFingersSpreadShowFewerWeeksTheDayBetweenThemStayingPut() = runComposeUiTest {
        val probe = zoomable()
        val before = days(probe)
        val day = LocalDate(2026, 10, 14)
        val at = before.getValue(day).center
        onRoot().performTouchInput {
            down(0, androidx.compose.ui.geometry.Offset(at.x, at.y - 20f))
            down(1, androidx.compose.ui.geometry.Offset(at.x, at.y + 20f))
            repeat(20) {
                moveBy(0, androidx.compose.ui.geometry.Offset(0f, -8f))
                moveBy(1, androidx.compose.ui.geometry.Offset(0f, 8f))
            }
            up(0)
            up(1)
        }
        waitForIdle()
        assertEquals(3f, probe.state.monthRows, "spread apart, the month shows ${probe.state.monthRows} weeks")
        val after = days(probe).getValue(day)
        assertTrue(at.y in after.top..after.bottom, "the 14th, between the fingers, is now at $after, not under $at")
        assertTrue(!probe.moved, "the pinch moved or made an event")
    }

    /**
     * Step by step through a spread, what is between the fingers stays there —
     * to within a pixel and a half every step, not only at the end — and the
     * events keep their lines until the fingers let go, so no week jumps.
     */
    @Test
    fun aPinchHoldsTheWeekUnderTheFingersEveryStepAndTheEventsStill() = runComposeUiTest {
        val review = CalendarEvent(1, "Design review", kotlinx.datetime.LocalDateTime(2026, 10, 14, 9, 0), kotlinx.datetime.LocalDateTime(2026, 10, 14, 10, 0), location = "Room 4")
        val probe = zoomable(listOf(review))
        val day = LocalDate(2026, 10, 14)
        val start = days(probe).getValue(day)
        val focus = start.top + start.height * 0.6f
        val fraction = (focus - start.top) / start.height
        fun chip() = onNode(hasContentDescription(eventDescription(review, probe.formats, probe.strings))).fetchSemanticsNode().boundsInRoot
        val chipBefore = chip().height
        onRoot().performTouchInput {
            down(0, androidx.compose.ui.geometry.Offset(start.center.x, focus - 20f))
            down(1, androidx.compose.ui.geometry.Offset(start.center.x, focus + 20f))
        }
        var worst = 0f
        repeat(20) {
            onRoot().performTouchInput {
                moveBy(0, androidx.compose.ui.geometry.Offset(0f, -6f))
                moveBy(1, androidx.compose.ui.geometry.Offset(0f, 6f))
            }
            waitForIdle()
            val now = days(probe).getValue(day)
            val drift = abs(now.top + now.height * fraction - focus)
            if (it > 2) worst = maxOf(worst, drift)
        }
        val chipDuring = chip().height
        onRoot().performTouchInput { up(0); up(1) }
        waitForIdle()
        assertTrue(worst < 1.5f, "the point between the fingers drifted ${worst}px from them during the pinch")
        assertEquals(chipBefore, chipDuring, 0.5f, "an event changed height mid-pinch")
        assertTrue(chip().height > chipBefore + 1f, "let go zoomed in to ${probe.state.monthRows} weeks, the event is ${chip().height} tall, from $chipBefore")
    }

    @Test
    fun ctrlAndTheWheelZoomAWeekAtATime() = runComposeUiTest {
        val probe = zoomable()
        val at = days(probe).getValue(LocalDate(2026, 10, 14)).center
        onRoot().performMultiModalInput {
            key { keyDown(androidx.compose.ui.input.key.Key.CtrlLeft) }
            mouse {
                moveTo(at)
                scroll(-1f)
            }
            key { keyUp(androidx.compose.ui.input.key.Key.CtrlLeft) }
        }
        waitForIdle()
        assertEquals(5f, probe.state.monthRows)
    }

    @Test
    fun aScreenReaderZoomsByItsActions() = runComposeUiTest {
        val probe = zoomable()
        val grid = onNode(SemanticsMatcher("the month's grid") { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.CustomActions).orEmpty().any { it.label == probe.strings.zoomIn }
        })
        repeat(3) { grid.performCustomAccessibilityActionWithLabel(probe.strings.zoomIn) }
        waitForIdle()
        assertEquals(3f, probe.state.monthRows, "three zoom-ins from six weeks")
        grid.performCustomAccessibilityActionWithLabel(probe.strings.zoomOut)
        waitForIdle()
        assertEquals(4f, probe.state.monthRows)
    }

    @Test
    fun zoomedInAnEventShowsItsTimeAndPlaceOnLinesOfTheirOwn() = runComposeUiTest {
        val review = CalendarEvent(1, "Design review", kotlinx.datetime.LocalDateTime(2026, 10, 14, 9, 0), kotlinx.datetime.LocalDateTime(2026, 10, 14, 10, 0), location = "Room 4")
        val probe = zoomable(listOf(review))
        // A whole month to the screen: one line, its time beside its title.
        onNode(hasText("Room 4"), useUnmergedTree = true).assertDoesNotExist()
        val grid = onNode(SemanticsMatcher("the month's grid") { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.CustomActions).orEmpty().any { it.label == probe.strings.zoomIn }
        })
        repeat(3) { grid.performCustomAccessibilityActionWithLabel(probe.strings.zoomIn) }
        waitForIdle()
        val title = onNode(hasText(review.title), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = onNode(hasText(probe.formats.time(review.start.time)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val place = onNode(hasText("Room 4"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(time.top >= title.bottom - 1f && place.top >= time.bottom - 1f, "not a line each: $title, $time, $place")
    }
}
