package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
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
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

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

    private fun androidx.compose.ui.test.ComposeUiTest.year(): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = false) {
                OverlayHost(Modifier.size(420.dp, 800.dp)) {
                    probe.formats = Theme.formats
                    probe.strings = Theme.strings
                    probe.density = androidx.compose.ui.platform.LocalDensity.current.density
                    probe.ink = Theme.colours.content
                    probe.state = rememberEventCalendarState(today, CalendarView.Year, firstDayOfWeek = DayOfWeek.MONDAY)
                    EventCalendar(state = probe.state, events = emptyList(), now = null, modifier = Modifier.size(420.dp, 800.dp))
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
}
