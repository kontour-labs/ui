package io.kontour.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A condensed week swiped part-way to the last one has its line between the two:
 * *"when swiping between weeks, the lines at the side between this monday and
 * last sunday disappear (same on the other side)"*. Each page drew the lines
 * between its own days and none at its first day's start edge, so where two
 * pages met there was none.
 */
@OptIn(ExperimentalTestApi::class)
class CondensedEdgeLineTest {

    @Test
    fun twoWeeksSideBySideHaveALineBetweenThem() = runComposeUiTest {
        var line = Color.Unspecified
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                KontourTheme(darkTheme = false, reduceMotion = false) {
                    line = Theme.colours.outline
                    OverlayHost(Modifier.size(700.dp, 400.dp).background(Color.White)) {
                        val state = rememberEventCalendarState(LocalDate(2026, 10, 14), CalendarView.Week, LocalTime(8, 0), DayOfWeek.MONDAY)
                        EventCalendar(
                            state = state,
                            events = emptyList(),
                            now = null,
                            daysStyle = CalendarDaysStyle.Condensed,
                            header = null,
                            modifier = Modifier.size(700.dp, 400.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        // Two and a half days' worth towards last week, held.
        onRoot().performTouchInput {
            down(Offset(300f, 250f))
            repeat(10) { moveBy(Offset(25f, 0f)) }
        }
        mainClock.advanceTimeBy(100)
        val image = onRoot().captureToImage().toPixelMap()
        val y = 300
        fun isLine(x: Int): Boolean {
            val c = image[x, y]
            return abs(c.red - line.red) < 0.06f && abs(c.green - line.green) < 0.06f && abs(c.blue - line.blue) < 0.06f
        }
        // The days are a seventh of the width each, with no gutter. Where the
        // lines are says how far the pages have slid, the same for all of them.
        val cell = 700f / 7f
        val lines = (4 until 696).filter { isLine(it) && !isLine(it - 1) }
        assertTrue(lines.isNotEmpty(), "no lines between the days at all")
        val slid = lines.first() % cell
        // Two and a half days less the touch slop: the one of its places in that range.
        val boundary = generateSequence(slid) { it + cell }.first { it > 150f }.toInt()
        val found = (boundary - 3..boundary + 3).any { isLine(it) }
        assertTrue(found, "no line at x=$boundary between Sunday and Monday, mid-swipe; lines at $lines")
    }
}
