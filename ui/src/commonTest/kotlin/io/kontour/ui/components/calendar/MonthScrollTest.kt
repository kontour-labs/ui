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
}
