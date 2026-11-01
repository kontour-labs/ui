package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * The year scrolls on through the years, and its months are there at once:
 * asked for as *"please make it infinite scroll (up and down, similar to the
 * new month scrolling)"* and *"some months tend to load in one at a time.
 * Maybe we need to audit and optimise the month component a bit"*.
 */
@OptIn(ExperimentalTestApi::class)
class YearViewTest {

    private val today = LocalDate(2026, 10, 14)

    private class Probe {
        lateinit var state: EventCalendarState
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        var density = 1f
        var ink = androidx.compose.ui.graphics.Color.Unspecified
    }

    private fun androidx.compose.ui.test.ComposeUiTest.year(
        showing: LocalDate = today,
        now: LocalDateTime? = null,
        reduceMotion: Boolean = false,
    ): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = reduceMotion) {
                OverlayHost(Modifier.size(420.dp, 800.dp)) {
                    probe.formats = Theme.formats
                    probe.strings = Theme.strings
                    probe.density = androidx.compose.ui.platform.LocalDensity.current.density
                    probe.ink = Theme.colours.content
                    probe.state = rememberEventCalendarState(showing, CalendarView.Year, firstDayOfWeek = DayOfWeek.MONDAY)
                    EventCalendar(state = probe.state, events = emptyList(), now = now, modifier = Modifier.size(420.dp, 800.dp))
                }
            }
        }
        return probe
    }

    private fun androidx.compose.ui.test.ComposeUiTest.month(probe: Probe, date: LocalDate) =
        onNode(hasContentDescription(probe.formats.monthAndYear(date)) and hasClickAction())

    @Test
    fun itOpensAtTheMonthItIsOnWithEveryMonthOnScreenDrawnInTheFirstFrame() = runComposeUiTest {
        mainClock.autoAdvance = false
        val probe = year()
        mainClock.advanceTimeByFrame()
        month(probe, today).assertExists()
        month(probe, LocalDate(2026, 1, 1)).assertDoesNotExist()
        // November's days, under its name, already in ink: not an outline waiting for its turn.
        val name = month(probe, LocalDate(2026, 11, 1)).fetchSemanticsNode().boundsInRoot
        val image = onRoot().captureToImage().toPixelMap()
        var inked = 0
        for (y in (name.bottom + 30f * probe.density).toInt() until minOf(image.height, (name.bottom + 200f * probe.density).toInt())) {
            for (x in 0 until image.width) if (image[x, y].red < 0.5f) inked++
        }
        assertTrue(inked > 200, "November's days were not drawn in the first frame ($inked dark pixels)")
    }

    @Test
    fun scrolledOnPastDecemberItIsAboutTheNextYear() = runComposeUiTest {
        val probe = year()
        waitForIdle()
        repeat(3) { onRoot().performTouchInput { swipeUp(startY = height * 0.9f, endY = height * 0.1f, durationMillis = 300) } }
        waitForIdle()
        assertTrue(probe.state.date.year >= 2027, "scrolled on past December, the calendar is still about ${probe.state.date}")
        assertEquals(today.month, probe.state.date.month, "the year's scroll changed the month")
    }

    @Test
    fun theHeadersNextGoesToTheNextYearsMonth() = runComposeUiTest {
        val probe = year()
        waitForIdle()
        onNode(hasContentDescription(probe.strings.nextYear) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(LocalDate(2027, 10, 14), probe.state.date)
        month(probe, LocalDate(2027, 10, 1)).assertExists()
    }

    @Test
    fun aMonthsNameOpensItAndADayItsDay() = runComposeUiTest {
        val probe = year()
        waitForIdle()
        val october = month(probe, today).fetchSemanticsNode().boundsInRoot
        // The 14th: a Wednesday, the third column, on October's third row
        // (the 1st is a Thursday). A month is at most 308dp wide, centred
        // under its name; a row a seventh of that, at most 40dp.
        val width = 308f * probe.density
        val left = october.center.x - width / 2f
        val cell = width / 7f
        val row = minOf(cell, 40f * probe.density)
        val initials = 24f * probe.density
        val at = Offset(left + cell * 2.5f, october.bottom + initials + row * 2.5f)
        onRoot().performTouchInput { click(at) }
        waitForIdle()
        assertEquals(CalendarView.Day, probe.state.view)
        assertEquals(today, probe.state.date)
    }

    @Test
    fun aMonthsNameOpensTheMonth() = runComposeUiTest {
        val probe = year()
        waitForIdle()
        month(probe, LocalDate(2026, 11, 1)).performClick()
        waitForIdle()
        assertEquals(CalendarView.Month, probe.state.view)
        assertEquals(LocalDate(2026, 11, 1), probe.state.date)
    }

    // --- Today, pointed at ---

    /**
     * Where inside today's ring to look for the flash, while October is whole
     * on screen; null while it is not. The 14th is a Wednesday, the third
     * column, on October's third row, as `aMonthsNameOpensItAndADayItsDay`
     * finds it; the point is down and towards the end from its centre, clear
     * of the number and inside the ring.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.insideToday(probe: Probe): Offset? {
        val node = onAllNodes(hasContentDescription(probe.formats.monthAndYear(today)) and hasClickAction())
            .fetchSemanticsNodes().firstOrNull() ?: return null
        val october = node.boundsInRoot
        val width = 308f * probe.density
        val left = october.center.x - width / 2f
        val cell = width / 7f
        val row = minOf(cell, 40f * probe.density)
        val initials = 24f * probe.density
        val centre = Offset(left + cell * 2.5f, october.bottom + initials + row * 2.5f)
        // October's name, and its last week, inside the list's viewport.
        val list = onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNode().boundsInRoot
        val lastRow = october.bottom + initials + row * 5f
        if (october.top < list.top || lastRow > list.bottom) return null
        return centre + Offset(row * 0.22f, row * 0.22f)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.shade(at: Offset): androidx.compose.ui.graphics.Color =
        onRoot().captureToImage().toPixelMap()[at.x.toInt(), at.y.toInt()]

    /** How far apart two shades are, their alpha too: the calendar has no ground of its own here, so the flash is drawn on nothing. */
    private fun away(from: androidx.compose.ui.graphics.Color, to: androidx.compose.ui.graphics.Color) =
        abs(from.red * from.alpha - to.red * to.alpha) + abs(from.green * from.alpha - to.green * to.alpha) +
            abs(from.blue * from.alpha - to.blue * to.alpha) + abs(from.alpha - to.alpha)

    /**
     * The header's today pressed, and today watched, a frame every 20ms for
     * [frames]: how far from its resting shade it is in each frame it is on
     * screen, and null in each it is not.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.pressTodayAndWatch(probe: Probe, frames: Int): List<Float?> {
        mainClock.autoAdvance = false
        onNode(hasContentDescription(probe.strings.returnToToday) and hasClickAction()).performClick()
        val seen = List(frames) {
            mainClock.advanceTimeBy(20L)
            insideToday(probe)?.let { shade(it) }
        }
        mainClock.autoAdvance = true
        waitForIdle()
        val resting = shade(insideToday(probe) ?: error("today is not on screen once it has all settled: the press never brought it back"))
        return seen.map { colour -> colour?.let { away(resting, it) } }
    }

    /**
     * Whether a flash seen from the frame today came on screen began there:
     * today dark in that frame, or only starting to light — brighter in the
     * next — rather than caught on the way out of a pulse that began off it.
     */
    private fun beganOnScreen(shown: List<Float>): Boolean = shown.first() < 0.05f || shown[1] > shown[0]

    /** How many times [strength] lights up, counting one already lit at the start. */
    private fun pulses(strength: List<Float>): Int {
        val lit = strength.map { it > 0.05f }
        return lit.zipWithNext().count { (was, now) -> !was && now } + (if (lit.firstOrNull() == true) 1 else 0)
    }

    /**
     * The header's today, pressed in the year with today on screen, flashes
     * today twice as every other view does: *"in the event calendar's year
     * view, clicking the 'today' button doesn't flash today like every other
     * calendar view does"*.
     */
    @Test
    fun todayPressedWithTodayOnScreenFlashesItTwice() = runComposeUiTest {
        val probe = year(now = LocalDateTime(today, LocalTime(12, 0)))
        waitForIdle()
        assertTrue(insideToday(probe) != null, "today is not on screen to begin with")
        val strength = pressTodayAndWatch(probe, frames = 50).map { it ?: error("today went off screen") }
        assertEquals(2, pulses(strength), "today should pulse twice; its distance from resting was $strength")
        assertTrue(strength.last() < 0.01f, "the flash did not fade back out: $strength")
    }

    /**
     * Scrolled away from today within the same year — so the state is still
     * about today — the press brings today's month back, and the flash plays
     * once it is there: not while it is still off screen, where it was missed.
     */
    @Test
    fun todayPressedFromElsewhereInItsYearComesBackAndThenFlashes() = runComposeUiTest {
        todayComesBackAndThenFlashes(reduceMotion = false)
    }

    /** The same under reduced motion: today jumps in rather than sliding, and still flashes, a fade being no movement. */
    @Test
    fun underReducedMotionTodayJumpsBackAndFlashes() = runComposeUiTest {
        todayComesBackAndThenFlashes(reduceMotion = true)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.todayComesBackAndThenFlashes(reduceMotion: Boolean) {
        val probe = year(now = LocalDateTime(today, LocalTime(12, 0)), reduceMotion = reduceMotion)
        waitForIdle()
        // Back towards the summer, slowly enough that nothing flings on into another year.
        onRoot().performTouchInput { swipeDown(startY = height * 0.2f, endY = height * 0.9f, durationMillis = 1500) }
        waitForIdle()
        assertEquals(today, probe.state.date, "scrolled within its year, the calendar should still be about today")
        assertEquals(null, insideToday(probe), "today is still on screen, so this is not the case it means to test")
        val seen = pressTodayAndWatch(probe, frames = 100)
        val first = seen.indexOfFirst { it != null }
        assertTrue(first >= 0, "today was never brought back on screen: ${probe.state.date}")
        val shown = seen.drop(first).map { it ?: 1f }
        assertTrue(beganOnScreen(shown), "today was already lit as it came on screen, so the flash began off it: $shown")
        assertEquals(2, pulses(shown), "today should pulse twice once on screen; its distance from resting was $shown")
    }

    /** From another year, the list scrolls a year to today's month, and the flash waits for it there. */
    @Test
    fun todayPressedFromAnotherYearFlashesOnceItIsThere() = runComposeUiTest {
        val probe = year(showing = LocalDate(2025, 6, 1), now = LocalDateTime(today, LocalTime(12, 0)))
        waitForIdle()
        assertEquals(null, insideToday(probe), "today is already on screen")
        val seen = pressTodayAndWatch(probe, frames = 100)
        val first = seen.indexOfFirst { it != null }
        assertTrue(first >= 0, "today was never brought on screen")
        val shown = seen.drop(first).map { it ?: 1f }
        assertTrue(beganOnScreen(shown), "today was already lit as it came on screen, so the flash began off it: $shown")
        assertEquals(2, pulses(shown), "today should pulse twice once on screen; its distance from resting was $shown")
        assertEquals(today, probe.state.date)
    }

    /**
     * Today years away slides in, as the month's far months do, rather than
     * cutting there as it did past two years: from just past the screen, so a
     * jump of five years is there no later than one of a year. Asked for
     * 0.5.2, for the month, as *"it just snaps back (rather than animating
     * like it does if you're only a few months down)"*.
     */
    @Test
    fun todayYearsAwaySlidesInNoLaterThanFromAYearAway() = runComposeUiTest {
        val probe = Probe()
        var now by mutableStateOf(LocalDateTime(2027, 10, 14, 12, 0))
        setContent {
            KontourTheme(reduceMotion = false) {
                OverlayHost(Modifier.size(420.dp, 800.dp)) {
                    probe.formats = Theme.formats
                    probe.strings = Theme.strings
                    probe.state = rememberEventCalendarState(today, CalendarView.Year, firstDayOfWeek = DayOfWeek.MONDAY)
                    EventCalendar(state = probe.state, events = emptyList(), now = now, modifier = Modifier.size(420.dp, 800.dp))
                }
            }
        }
        waitForIdle()
        // Today's month's name, unclipped, each frame from the press of today; null off screen.
        fun watch(): List<Float?> {
            val name = onAllNodes(hasContentDescription(probe.formats.monthAndYear(now.date)) and hasClickAction())
            mainClock.autoAdvance = false
            onNode(hasContentDescription(probe.strings.returnToToday) and hasClickAction()).performClick()
            val tops = List(50) {
                mainClock.advanceTimeByFrame()
                name.fetchSemanticsNodes().firstOrNull()?.positionInRoot?.y
            }
            mainClock.autoAdvance = true
            waitForIdle()
            return tops
        }
        fun settledBy(tops: List<Float?>): Int = tops.indexOfLast { it != tops.last() } + 2
        val year = watch()
        now = LocalDateTime(2032, 10, 14, 12, 0)
        waitForIdle()
        val on = watch()
        now = LocalDateTime(2027, 3, 14, 12, 0)
        waitForIdle()
        val back = watch()
        assertEquals(LocalDate(2027, 3, 14), probe.state.date)
        for ((way, tops) in listOf("on" to on, "back" to back)) {
            assertTrue(tops.filterNotNull().distinct().size >= 4, "today's month five years $way did not slide in: $tops")
            assertTrue(
                settledBy(tops) <= settledBy(year),
                "today's month five years $way took ${settledBy(tops)} frames to come to rest, where a year on took ${settledBy(year)}: $tops against $year",
            )
        }
    }
}
