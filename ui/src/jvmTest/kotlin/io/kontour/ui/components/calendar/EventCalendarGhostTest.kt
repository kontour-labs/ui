package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
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
import io.kontour.ui.theme.Tone
import io.kontour.ui.theme.toneColours
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * The copy a carry draws, watched frame by frame at full motion — reported as
 * *"they don't follow the finger, and they just feel floaty. they feel like
 * they're going backwards at some points too"*: within a step it leans with
 * the pointer in the same frame, it never goes the other way from the pointer,
 * and a step crossed is a short hop that has landed within a few frames.
 *
 * Found by its edge, the event's tone at full strength and opaque: the event
 * left behind is the same colour, faded.
 */
@OptIn(ExperimentalTestApi::class)
class EventCalendarGhostTest {

    private val review = CalendarEvent(1, "Design review", LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 11, 0), tone = Tone.Danger)

    private class Probe {
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        var edge = Color.Unspecified
    }

    private val trip = CalendarEvent(3, "Perth trip", startDate = LocalDate(2026, 10, 6), endDate = LocalDate(2026, 10, 6), tone = Tone.Danger)

    private fun ComposeUiTest.calendar(view: CalendarView = CalendarView.Week, still: Boolean = false): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = still) {
                CompositionLocalProvider(LocalInputModality provides InputModality.Mouse) {
                    OverlayHost(Modifier.size(700.dp, 700.dp)) {
                        probe.formats = Theme.formats
                        probe.strings = Theme.strings
                        probe.edge = toneColours(Tone.Danger).solid
                        EventCalendar(
                            state = rememberEventCalendarState(LocalDate(2026, 10, 14), view, LocalTime(8, 0), DayOfWeek.MONDAY),
                            events = if (view == CalendarView.Month) listOf(trip) else listOf(review),
                            now = null,
                            onEventMove = { _, _, _ -> },
                            modifier = Modifier.size(700.dp, 700.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        return probe
    }

    /** The ghost's top edge, from the topmost row of its edge colour; null with none drawn. */
    private fun ComposeUiTest.ghostTop(probe: Probe): Float? {
        val image = onRoot().captureToImage().toPixelMap()
        val target = probe.edge
        for (y in 0 until image.height) {
            for (x in 0 until image.width step 2) {
                val c = image[x, y]
                if (c.alpha > 0.9f && abs(c.red - target.red) < 0.06f && abs(c.green - target.green) < 0.06f && abs(c.blue - target.blue) < 0.06f) return y.toFloat()
            }
        }
        return null
    }

    @Test
    fun aMonthsCopyGoesDownTheWeeksWithThePointerAndNeverBackUp() = runComposeUiTest {
        val probe = calendar(CalendarView.Month)
        val bar = onNode(hasContentDescription(eventDescription(trip, probe.formats, probe.strings))).fetchSemanticsNode().boundsInRoot
        val from = bar.center
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(4) { moveBy(Offset(0f, 5f)) }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        repeat(6) { mainClock.advanceTimeByFrame() }
        val tops = mutableListOf(ghostTop(probe) ?: error("no copy drawn once the carry began"))
        // Down two weeks, a little each frame.
        // The weeks share the grid's height: five of them in October 2026.
        val week = 620f / 5f
        repeat(40) {
            onRoot().performMouseInput { moveBy(Offset(0f, week * 2f / 40)) }
            mainClock.advanceTimeByFrame()
            tops += ghostTop(probe)!!
        }
        val back = tops.zipWithNext().filter { (a, b) -> b < a - 1f }
        assertTrue(back.isEmpty(), "the copy went back up against the pointer: $back in $tops")
        assertTrue(tops.last() > tops.first() + week, "the copy did not go down the weeks: $tops")
        onRoot().performMouseInput { release() }
        mainClock.autoAdvance = true
    }

    /**
     * The month scrolled under a carry, the copy keeps the line of its week the
     * event was taken from, under the finger.
     *
     * It kept the bar's top as a place on the screen and worked out its line
     * from where the weeks were now, so once the weeks moved — the month
     * scrolling at its edges, or into the next — the copy kept to a line off by
     * however far they had gone, away from the finger holding it: reported as
     * a carry across weeks that "stops following the finger and becomes offset".
     */
    @Test
    fun aMonthScrolledUnderACarryKeepsTheCopyOnItsLine() = runComposeUiTest {
        val probe = calendar(CalendarView.Month, still = true)
        fun bar() = onNode(hasContentDescription(eventDescription(trip, probe.formats, probe.strings))).fetchSemanticsNode().boundsInRoot
        val days = (0 until 120).map { LocalDate(2026, 9, 1).plus(it, DateTimeUnit.DAY) }
        /** The top of the day's box the point is in. */
        fun dayTopAt(point: Offset): Float = days.firstNotNullOf { date ->
            onAllNodes(hasContentDescription(probe.formats.date(date, DateStyle.Full), substring = true) and hasClickAction())
                .fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.height > 40f }
                .firstOrNull { it.contains(point) }?.top
        }
        fun copyInItsDay(): Float {
            val top = ghostTop(probe) ?: error("no copy drawn")
            val left = ghostLeft(probe, top.toInt()..top.toInt() + 4) ?: error("no copy drawn")
            return top - dayTopAt(Offset(left + 3f, top + 3f))
        }
        val from = bar().center
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(4) { moveBy(Offset(2f, 0f)) }
        }
        waitForIdle()
        val before = copyInItsDay()
        // Held at the foot of the month, which scrolls on under the carry.
        mainClock.autoAdvance = false
        val steps = 20
        repeat(steps) {
            onRoot().performMouseInput { moveTo(Offset(from.x, from.y + (696f - from.y) * (it + 1) / steps)) }
            mainClock.advanceTimeByFrame()
        }
        repeat(15) { mainClock.advanceTimeByFrame() }
        val after = copyInItsDay()
        onRoot().performMouseInput { release() }
        mainClock.autoAdvance = true
        assertTrue(abs(after - before) < 1.5f, "the month scrolled under the carry and the copy went from ${before}px down its day to ${after}px")
    }

    /** The ghost's left edge, from the leftmost column of its edge colour; null with none drawn. */
    private fun ComposeUiTest.ghostLeft(probe: Probe, rows: IntRange): Float? {
        val image = onRoot().captureToImage().toPixelMap()
        val target = probe.edge
        for (x in 0 until image.width) {
            for (y in rows step 2) {
                if (y !in 0 until image.height) continue
                val c = image[x, y]
                if (c.alpha > 0.9f && abs(c.red - target.red) < 0.06f && abs(c.green - target.green) < 0.06f && abs(c.blue - target.blue) < 0.06f) return x.toFloat()
            }
        }
        return null
    }

    @Test
    fun theCopyKeepsUpWithThePointerAndNeverGoesBack() = runComposeUiTest {
        val probe = calendar()
        val event = onNode(hasContentDescription(eventDescription(review, probe.formats, probe.strings))).fetchSemanticsNode().boundsInRoot
        fun heading(date: LocalDate) = onNode(hasContentDescription(probe.formats.date(date, DateStyle.Full)) and hasClickAction())
            .fetchSemanticsNode().boundsInRoot.center.x
        val day = heading(LocalDate(2026, 10, 15)) - heading(LocalDate(2026, 10, 14))
        val rows = (event.top.toInt() - 4)..(event.bottom.toInt() + 4)
        val from = Offset(event.left + 6f, event.center.y)
        // Past the slop, and the copy is up; then the clock by hand.
        onRoot().performMouseInput {
            moveTo(from)
            press()
            // Past the touch slop a free carry waits for, still well inside the day.
            repeat(4) { moveBy(Offset(5f, 0f)) }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        repeat(6) { mainClock.advanceTimeByFrame() }
        val start = ghostLeft(probe, rows) ?: error("no copy drawn once the carry began")

        // Within the day: the lean follows the pointer in the same frame.
        val lefts = mutableListOf(start)
        onRoot().performMouseInput { moveBy(Offset(day * 0.2f, 0f)) }
        mainClock.advanceTimeByFrame()
        val leaned = ghostLeft(probe, rows)!!
        assertTrue(leaned > start + 1f, "the copy did not lean with the pointer in the frame it moved: $start → $leaned")

        // Across two days, a little each frame: never back the way it came.
        val steps = 40
        repeat(steps) {
            onRoot().performMouseInput { moveBy(Offset(day * 2f / steps, 0f)) }
            mainClock.advanceTimeByFrame()
            lefts += ghostLeft(probe, rows)!!
        }
        val back = lefts.zipWithNext().filter { (a, b) -> b < a - 1f }
        assertTrue(back.isEmpty(), "the copy went back against the pointer: $back in $lefts")

        // And landed on its day within a few frames of the last crossing.
        repeat(8) { mainClock.advanceTimeByFrame() }
        val settled = ghostLeft(probe, rows)!!
        repeat(8) { mainClock.advanceTimeByFrame() }
        assertTrue(abs(ghostLeft(probe, rows)!! - settled) <= 1f, "the copy was still moving 8 frames after the pointer stopped")
        assertTrue(settled > start + day * 1.5f, "the copy did not travel the two days: $start → $settled")
        onRoot().performMouseInput { release() }
        mainClock.autoAdvance = true
    }
}
