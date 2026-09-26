package io.kontour.ui.components.datetime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

/**
 * Every date picker has a way back to today's month, with a glyph of its own and
 * nothing to supply. Always there, unless asked not to be; pressing it brings the
 * calendar to today's month and flashes the day.
 */
@OptIn(ExperimentalTestApi::class)
class CalendarTodayButtonTest {

    @Test
    fun aDatePickerAwayFromTodayOffersTheWayBack() = runComposeUiTest {
        val navigation = datePicker()
        onNodeWithContentDescription("Return to today").performClick()
        waitForIdle()
        assertEquals(LocalDate(2026, 6, 1), navigation.visibleMonth)
    }

    @Test
    fun aDateRangePickerAwayFromTodayOffersTheWayBack() = runComposeUiTest {
        lateinit var navigation: CalendarNavigationState
        setContent {
            KontourTheme {
                navigation = rememberCalendarNavigationState(LocalDate(2026, 9, 1))
                var start by remember { mutableStateOf<LocalDate?>(null) }
                var end by remember { mutableStateOf<LocalDate?>(null) }
                DateRangePicker(
                    start = start,
                    end = end,
                    onRangeSelected = { s, e -> start = s; end = e },
                    today = Today,
                    navigation = navigation,
                )
            }
        }
        onNodeWithContentDescription("Return to today").performClick()
        waitForIdle()
        assertEquals(LocalDate(2026, 6, 1), navigation.visibleMonth)
    }

    @Test
    fun toldNotToThereIsNone() = runComposeUiTest {
        datePicker(todayIcon = null)
        onNodeWithContentDescription("Return to today").assertDoesNotExist()
    }

    /** On today's own month it is still there, and pressing it flashes the day — twice. */
    @Test
    fun onTodaysOwnMonthItFlashesTheDayTwice() = runComposeUiTest {
        datePicker(showing = LocalDate(2026, 6, 1))
        val day = onNodeWithText("12").fetchSemanticsNode().boundsInRoot
        // Inside the day's circle, below its number.
        val probe = Offset(day.center.x, day.center.y + day.height * 0.3f)
        fun shade(): Color = onRoot().captureToImage().toPixelMap()[probe.x.toInt(), probe.y.toInt()]
        fun away(from: Color, to: Color) =
            abs(from.red - to.red) + abs(from.green - to.green) + abs(from.blue - to.blue)
        val before = shade()
        mainClock.autoAdvance = false
        onNodeWithContentDescription("Return to today").performClick()
        // How far from the resting shade the day is, every 20ms for a second.
        val strength = List(50) {
            mainClock.advanceTimeBy(20L)
            away(before, shade())
        }
        val lit = strength.map { it > 0.05f }
        val pulses = lit.zipWithNext().count { (was, now) -> !was && now } + (if (lit.first()) 1 else 0)
        assertEquals(2, pulses, "the day should pulse twice; its distance from resting was $strength")
        assertEquals(before, shade(), "the flash did not fade back out")
    }

    /**
     * Today inside a range: the flash lights the day's circle and stops there. The
     * corner of the day's cell, band behind it and outside the circle, stays the
     * band's colour throughout.
     *
     * Reported as the flash spilling into the whole box when today is part of the
     * selection: it was drawn in the cell's shape, which in a range is a square.
     */
    @Test
    fun inARangeTheFlashStaysInsideTodaysCircle() = runComposeUiTest {
        setContent {
            KontourTheme {
                DateRangePicker(
                    start = LocalDate(2026, 6, 8),
                    end = LocalDate(2026, 6, 17),
                    onRangeSelected = { _, _ -> },
                    today = Today,
                )
            }
        }
        waitForIdle()
        val day = onNodeWithText("12").fetchSemanticsNode().boundsInRoot
        val inside = Offset(day.center.x, day.center.y + day.height * 0.3f)
        val corner = Offset(day.left + day.width * 0.08f, day.top + day.height * 0.12f)
        fun shade(at: Offset): Color = onRoot().captureToImage().toPixelMap()[at.x.toInt(), at.y.toInt()]
        fun away(from: Color, to: Color) =
            abs(from.red - to.red) + abs(from.green - to.green) + abs(from.blue - to.blue)
        val insideBefore = shade(inside)
        val cornerBefore = shade(corner)
        mainClock.autoAdvance = false
        onNodeWithContentDescription("Return to today").performClick()
        var insideMost = 0f
        var cornerMost = 0f
        repeat(40) {
            mainClock.advanceTimeBy(20L)
            insideMost = maxOf(insideMost, away(insideBefore, shade(inside)))
            cornerMost = maxOf(cornerMost, away(cornerBefore, shade(corner)))
        }
        assertTrue(insideMost > 0.05f, "the day did not flash at all, so the corner shows nothing")
        assertTrue(cornerMost < 0.01f, "the flash reached the corner of the day's cell, $cornerMost from the band")
    }

    private fun ComposeUiTest.datePicker(
        todayIcon: ImageVector? = DatePickerDefaults.TodayIcon,
        showing: LocalDate = LocalDate(2026, 9, 1),
    ): CalendarNavigationState {
        lateinit var navigation: CalendarNavigationState
        setContent {
            KontourTheme {
                navigation = rememberCalendarNavigationState(showing)
                DatePicker(
                    selected = null,
                    onSelectedChange = {},
                    today = Today,
                    todayIcon = todayIcon,
                    navigation = navigation,
                )
            }
        }
        waitForIdle()
        return navigation
    }

    private companion object {
        val Today = LocalDate(2026, 6, 12)
    }
}
