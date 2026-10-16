package io.kontour.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
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
import io.kontour.ui.theme.toneColours
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * An event across days, swiped between them, has its title once: *"there's
 * instances where you can see the label on the event in both the day you're
 * swiping away from and the day you're swiping to. can we just make the label
 * stay in place as the calendar moves, so it only shows once?"*
 *
 * Each page drew its own piece of the bar with the title at that piece's
 * start, so halfway through a swipe there were two: one going off with the
 * page leaving, one coming in with the page arriving. The title now stays
 * where it is on the screen while the bar goes on under it, drawn by each
 * page's piece at the one place and cut to that piece, and goes with the bar
 * only once the bar's own end reaches it.
 */
@OptIn(ExperimentalTestApi::class)
class AllDayLabelSwipeTest {

    private class Probe {
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        var ink = Color.Unspecified
    }

    private fun ComposeUiTest.calendar(view: CalendarView, event: CalendarEvent, direction: LayoutDirection = LayoutDirection.Ltr): Probe {
        val probe = Probe()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalLayoutDirection provides direction) {
                KontourTheme(darkTheme = false, reduceMotion = false) {
                    OverlayHost(Modifier.size(700.dp, 500.dp).background(Color.White)) {
                        probe.formats = Theme.formats
                        probe.strings = Theme.strings
                        probe.ink = toneColours(Tone.Danger).onSolid
                        EventCalendar(
                            state = rememberEventCalendarState(LocalDate(2026, 10, 14), view, LocalTime(8, 0), DayOfWeek.MONDAY),
                            events = listOf(event),
                            now = null,
                            header = null,
                            modifier = Modifier.size(700.dp, 500.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        return probe
    }

    /** Each piece of the bar, where it is laid out: not cut to the screen, so a piece going off it is seen going. */
    private fun ComposeUiTest.bars(probe: Probe, event: CalendarEvent): List<Rect> =
        onAllNodes(hasContentDescription(eventDescription(event, probe.formats, probe.strings))).fetchSemanticsNodes().map { it.laidOut() }

    /** The title as each page has it laid out: where its text is. */
    private fun ComposeUiTest.titles(event: CalendarEvent): List<Rect> =
        onAllNodes(hasText(event.title), useUnmergedTree = true).fetchSemanticsNodes().map { it.laidOut() }

    private fun SemanticsNode.laidOut(): Rect = Rect(positionInRoot, Size(size.width.toFloat(), size.height.toFloat()))

    /** Held part of the way through a swipe by [by] pixels, on the hours below the bar. */
    private fun ComposeUiTest.holdSwipe(from: Offset, by: Float) {
        mainClock.autoAdvance = false
        onRoot().performTouchInput {
            down(from)
            val steps = 20
            repeat(steps) { moveBy(Offset(by / steps, 0f)) }
        }
        mainClock.advanceTimeBy(100)
    }

    /** Columns of the bar's band, between [from] and [to], with the title's ink in them. */
    private fun ComposeUiTest.inkColumns(probe: Probe, band: Rect, from: Float, to: Float): List<Int> {
        val image = onRoot().captureToImage().toPixelMap()
        val ink = probe.ink
        return (from.toInt() until to.toInt()).filter { x ->
            (band.top.toInt() + 2 until band.bottom.toInt() - 2).any { y ->
                val c = image[x, y]
                abs(c.red - ink.red) < 0.08f && abs(c.green - ink.green) < 0.08f && abs(c.blue - ink.blue) < 0.08f
            }
        }
    }

    /** Tuesday to Thursday. */
    private val conference = CalendarEvent(1, "Conference", startDate = LocalDate(2026, 10, 13), endDate = LocalDate(2026, 10, 15), tone = Tone.Danger)

    private fun ComposeUiTest.titleShowsOnceMidSwipe(direction: LayoutDirection) {
        val probe = calendar(CalendarView.Day, conference, direction)
        val bar = bars(probe, conference).single()
        // Two fifths of the way to the next day, as the reading goes.
        val towardNext = if (direction == LayoutDirection.Rtl) 1f else -1f
        holdSwipe(Offset(bar.center.x, bar.bottom + 200f), towardNext * bar.width * 0.4f)
        val pieces = bars(probe, conference)
        assertEquals(2, pieces.size, "mid-swipe, both days' pieces of the bar should be on the screen")
        val titles = titles(conference)
        assertEquals(2, titles.size, "each day's piece should still have the title, for its own day: $titles")
        val (a, b) = titles
        assertTrue(abs(a.left - b.left) <= 1f, "mid-swipe, the two days drew the title in two places, at ${a.left} and ${b.left}")
        // On the screen, and nowhere else along the bar: the title once.
        val band = Rect(bar.left, pieces.first().top, bar.right, pieces.first().bottom)
        val title = if (direction == LayoutDirection.Rtl) a.right else a.left
        assertTrue(title in bar.left - 1f..bar.right + 1f, "mid-swipe, the title is off the screen at $title, the bar from ${bar.left} to ${bar.right}")
        val elsewhere = if (direction == LayoutDirection.Rtl) {
            inkColumns(probe, band, bar.left + 4f, a.left - 8f)
        } else {
            inkColumns(probe, band, a.right + 8f, bar.right - 4f)
        }
        assertTrue(elsewhere.isEmpty(), "mid-swipe, the title's ink is along the bar away from the title as well, at $elsewhere")
    }

    @Test
    fun aDaysBarHasItsTitleOnceMidSwipe() = runComposeUiTest { titleShowsOnceMidSwipe(LayoutDirection.Ltr) }

    @Test
    fun aDaysBarHasItsTitleOnceMidSwipeRightToLeft() = runComposeUiTest { titleShowsOnceMidSwipe(LayoutDirection.Rtl) }

    @Test
    fun aWeeksBarHasItsTitleOnceMidSwipe() = runComposeUiTest {
        // Friday to the Tuesday after: the end of one week and the start of the next.
        val trip = CalendarEvent(2, "Trip", startDate = LocalDate(2026, 10, 16), endDate = LocalDate(2026, 10, 20), tone = Tone.Danger)
        val probe = calendar(CalendarView.Week, trip)
        val bar = bars(probe, trip).single()
        val cell = bar.width / 3f
        // On to the next week, until the line between the two is a day and a half in from the start.
        val days = cell * 7f
        val strip = Rect(bar.right - days, bar.top, bar.right, bar.bottom)
        holdSwipe(Offset(strip.center.x, bar.bottom + 200f), -(days - cell * 1.5f) - 20f)
        val titles = titles(trip)
        assertEquals(2, titles.size, "each week's piece should still have the title: $titles")
        val (a, b) = titles
        assertTrue(abs(a.left - b.left) <= 1f, "mid-swipe, the two weeks drew the title in two places, at ${a.left} and ${b.left}")
    }

    @Test
    fun theTitleStaysOnTheScreenUntilTheBarsEndTakesItAway() = runComposeUiTest {
        // Monday to Wednesday, on the Wednesday: it ends today.
        val visit = CalendarEvent(3, "Visit", startDate = LocalDate(2026, 10, 12), endDate = LocalDate(2026, 10, 14), tone = Tone.Danger)
        val probe = calendar(CalendarView.Day, visit)
        val bar = bars(probe, visit).single()
        holdSwipe(Offset(bar.center.x, bar.bottom + 200f), -bar.width * 0.5f)
        val half = titles(visit).single()
        assertTrue(half.left >= bar.left - 1f, "halfway to tomorrow, the title went off the screen with the day, to ${half.left}; the bar is from ${bar.left}")
        onRoot().performTouchInput { repeat(10) { moveBy(Offset(-bar.width * 0.04f, 0f)) } }
        mainClock.advanceTimeBy(100)
        val end = bars(probe, visit).single().right
        val late = titles(visit).single()
        assertTrue(late.right <= end + 1f, "nine tenths of the way to tomorrow, the title ran on past the end of the bar, to ${late.right}; the bar ends at $end")
    }
}
