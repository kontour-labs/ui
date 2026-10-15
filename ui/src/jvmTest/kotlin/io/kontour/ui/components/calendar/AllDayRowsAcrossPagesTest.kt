package io.kontour.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * A bar running on into the next page is on the same line there, so mid-swipe
 * its two pieces meet and its title shows once, whatever else each page has:
 * *"when swiping between days/weeks/3days in the event calendar, if there's a
 * multi-day event, then there's instances where you can see the label on the
 * event in both the day you're swiping away from and the day you're swiping
 * to."*
 *
 * Each page packed its own bars, so a bar under one that had started before it
 * was on the second line of one page and the first of the next: its pieces
 * were a line apart, and each drew its own title.
 */
@OptIn(ExperimentalTestApi::class)
class AllDayRowsAcrossPagesTest {

    private class Probe {
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
    }

    private fun ComposeUiTest.calendar(
        view: CalendarView,
        events: List<CalendarEvent>,
        date: LocalDate = LocalDate(2026, 10, 14),
        direction: LayoutDirection = LayoutDirection.Ltr,
        style: CalendarDaysStyle = CalendarDaysStyle.Hours,
        height: Int = 500,
    ): Probe {
        val probe = Probe()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalLayoutDirection provides direction) {
                KontourTheme(darkTheme = false, reduceMotion = false) {
                    OverlayHost(Modifier.size(700.dp, height.dp).background(Color.White)) {
                        probe.formats = Theme.formats
                        probe.strings = Theme.strings
                        EventCalendar(
                            state = rememberEventCalendarState(date, view, LocalTime(8, 0), DayOfWeek.MONDAY),
                            events = events,
                            now = null,
                            header = null,
                            daysStyle = style,
                            modifier = Modifier.size(700.dp, height.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        return probe
    }

    /** Each piece of [event], where it is laid out: not cut to the screen, so a piece going off it is seen going. */
    private fun ComposeUiTest.pieces(probe: Probe, event: CalendarEvent): List<Rect> =
        onAllNodes(hasContentDescription(eventDescription(event, probe.formats, probe.strings))).fetchSemanticsNodes().map { it.laidOut() }

    /** The title as each page has it laid out. */
    private fun ComposeUiTest.titles(event: CalendarEvent): List<Rect> =
        onAllNodes(hasText(event.title), useUnmergedTree = true).fetchSemanticsNodes().map { it.laidOut() }

    private fun SemanticsNode.laidOut(): Rect = Rect(positionInRoot, Size(size.width.toFloat(), size.height.toFloat()))

    /** Held [share] of a page's width of the way on to the next page, as the reading goes, on the hours at [y]. */
    private fun ComposeUiTest.holdSwipe(share: Float, y: Float, direction: LayoutDirection = LayoutDirection.Ltr) {
        mainClock.autoAdvance = false
        // The days are the calendar's width less the hours' labels beside them: near enough.
        val by = 640f * share * if (direction == LayoutDirection.Rtl) 1f else -1f
        onRoot().performTouchInput {
            down(Offset(400f, y))
            val steps = 20
            repeat(steps) { moveBy(Offset(by / steps, 0f)) }
        }
        mainClock.advanceTimeBy(100)
    }

    /** Mid-swipe, [event]'s two pieces are on the same line, and its title is at one place. */
    private fun ComposeUiTest.runsOnLevel(probe: Probe, event: CalendarEvent, what: String) {
        val pieces = pieces(probe, event)
        assertEquals(2, pieces.size, "mid-swipe between $what, both pages' pieces of \"${event.title}\" should be on the screen: $pieces")
        val (a, b) = pieces
        assertTrue(
            abs(a.top - b.top) <= 1f,
            "mid-swipe between $what, \"${event.title}\" was on one line of one page and another of the next, at ${a.top} and ${b.top}",
        )
        val titles = titles(event)
        assertTrue(
            titles.size == 2 && abs(titles[0].left - titles[1].left) <= 1f && abs(titles[0].top - titles[1].top) <= 1f,
            "mid-swipe between $what, \"${event.title}\" had its title in two places: $titles",
        )
    }

    /** Wednesday the 14th to the Sunday after. */
    private val trip = CalendarEvent(1, "Trip to Perth and back", startDate = LocalDate(2026, 10, 14), endDate = LocalDate(2026, 10, 18), tone = Tone.Info)

    /** At nine on [day] of October. */
    private fun standUp(id: Int, day: Int) = CalendarEvent(id, "Stand-up", LocalDateTime(2026, 10, day, 9, 0), LocalDateTime(2026, 10, day, 9, 15))

    @Test
    fun aBarRunningOnIntoTomorrowIsOnTheSameLine() = runComposeUiTest {
        // Monday to today: on today's first line, over the trip.
        val conference = CalendarEvent(2, "Conference", startDate = LocalDate(2026, 10, 12), endDate = LocalDate(2026, 10, 14), tone = Tone.Danger)
        val probe = calendar(CalendarView.Day, listOf(conference, trip))
        holdSwipe(0.4f, 400f)
        runsOnLevel(probe, trip, "today and tomorrow")
    }

    private fun ComposeUiTest.threeDays(direction: LayoutDirection) {
        // The trip from the 13th, as the calendar's own demo has it: the same
        // first day on the page, as long on it, and first in the list.
        val perth = CalendarEvent(2, "Perth trip", startDate = LocalDate(2026, 10, 13), endDate = LocalDate(2026, 10, 16), tone = Tone.Warning)
        val probe = calendar(CalendarView.ThreeDays, listOf(perth, trip, standUp(3, 14)), direction = direction)
        holdSwipe(0.4f, 400f, direction)
        runsOnLevel(probe, trip, "the 14th to the 16th and the 17th to the 19th")
    }

    @Test
    fun aBarRunningOnIntoTheNextThreeDaysIsOnTheSameLine() = runComposeUiTest { threeDays(LayoutDirection.Ltr) }

    @Test
    fun aBarRunningOnIntoTheNextThreeDaysIsOnTheSameLineRightToLeft() = runComposeUiTest { threeDays(LayoutDirection.Rtl) }

    /** Friday and Saturday: the week's first line, where the trip below starts. */
    private val holidays = CalendarEvent(2, "School holidays", startDate = LocalDate(2026, 10, 16), endDate = LocalDate(2026, 10, 17), tone = Tone.Neutral)

    /** Saturday to the Tuesday after: under the holidays on its first day, and alone in the week after. */
    private val visit = CalendarEvent(3, "Visit", startDate = LocalDate(2026, 10, 17), endDate = LocalDate(2026, 10, 20), tone = Tone.Info)

    @Test
    fun aBarRunningOnIntoNextWeekIsOnTheSameLine() = runComposeUiTest {
        val probe = calendar(CalendarView.Week, listOf(holidays, visit))
        holdSwipe(0.4f, 400f)
        runsOnLevel(probe, visit, "this week and next")
    }

    @Test
    fun aBarRunningOnIntoNextWeeksRowOfTheMonthIsOnTheSameLine() = runComposeUiTest {
        // Monday, the 19th: on the next week's first line, under nothing.
        val release = CalendarEvent(4, "Release", startDate = LocalDate(2026, 10, 19), tone = Tone.Accent)
        val probe = calendar(CalendarView.Month, listOf(holidays, visit, release), height = 760)
        val (first, second) = pieces(probe, visit).sortedBy { it.top }
        val above = pieces(probe, holidays).single()
        val beside = pieces(probe, release).single()
        assertTrue(
            abs((first.top - above.top) - (second.top - beside.top)) <= 1f,
            "the visit is a line under the week's first in its first week, at ${first.top - above.top}, " +
                "and should be in the next week too, not at ${second.top - beside.top}",
        )
    }

    @Test
    fun aCondensedListsBarRunningOnIsOnTheSameLine() = runComposeUiTest {
        val perth = CalendarEvent(2, "Perth trip", startDate = LocalDate(2026, 10, 13), endDate = LocalDate(2026, 10, 16), tone = Tone.Warning)
        // Enough on the 15th for the list to scroll.
        val busy = (0 until 16).map { CalendarEvent(10 + it, "Call $it", LocalDateTime(2026, 10, 15, 8 + it / 2, 30 * (it % 2)), LocalDateTime(2026, 10, 15, 9 + it / 2, 0)) }
        val probe = calendar(CalendarView.ThreeDays, listOf(perth, trip) + busy, style = CalendarDaysStyle.Condensed)
        val atRest = pieces(probe, trip).single()
        // Scrolled down a little, slowly, so it does not fling.
        onRoot().performTouchInput {
            down(Offset(400f, 300f))
            repeat(10) { moveBy(Offset(0f, -3f), delayMillis = 100) }
            up()
        }
        waitForIdle()
        val scrolled = pieces(probe, trip).single()
        assertTrue(scrolled.top < atRest.top - 10f, "the list should have scrolled, the trip from ${atRest.top} to ${scrolled.top}")
        holdSwipe(0.4f, 300f)
        runsOnLevel(probe, trip, "the condensed 14th to the 16th and 17th to the 19th")
    }

    @Test
    fun theStripGoesFromOnePagesHeightToTheOthersWithTheSwipe() = runComposeUiTest {
        val conference = CalendarEvent(2, "Conference", startDate = LocalDate(2026, 10, 12), endDate = LocalDate(2026, 10, 14), tone = Tone.Danger)
        val dinner = CalendarEvent(3, "Anniversary", startDate = LocalDate(2026, 10, 14), tone = Tone.Success)
        val standUp = standUp(4, 14)
        val probe = calendar(CalendarView.Day, listOf(conference, dinner, standUp))
        val bars = pieces(probe, conference) + pieces(probe, dinner)
        val strip = bars.maxOf { it.bottom } - bars.minOf { it.top }
        val atRest = pieces(probe, standUp).single().top
        // Two fifths of the way to tomorrow, which has nothing all day.
        holdSwipe(0.4f, 400f)
        val moved = atRest - pieces(probe, standUp).single().top
        assertTrue(
            moved in strip * 0.25f..strip * 0.55f,
            "two fifths of the way to a day with nothing all day, the hours should have come up about two fifths of the strip's ${strip}px, not $moved",
        )
    }
}
