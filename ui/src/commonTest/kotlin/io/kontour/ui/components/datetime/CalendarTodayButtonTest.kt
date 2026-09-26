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
import kotlin.test.assertNotEquals

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

    /** On today's own month it is still there, and pressing it flashes the day. */
    @Test
    fun onTodaysOwnMonthItFlashesTheDay() = runComposeUiTest {
        datePicker(showing = LocalDate(2026, 6, 1))
        val day = onNodeWithText("12").fetchSemanticsNode().boundsInRoot
        // Inside the day's circle, below its number.
        val probe = Offset(day.center.x, day.center.y + day.height * 0.3f)
        fun shade(): Color = onRoot().captureToImage().toPixelMap()[probe.x.toInt(), probe.y.toInt()]
        val before = shade()
        mainClock.autoAdvance = false
        onNodeWithContentDescription("Return to today").performClick()
        mainClock.advanceTimeBy(200L)
        val flashing = shade()
        mainClock.advanceTimeBy(1_500L)
        val after = shade()
        assertNotEquals(before, flashing, "pressing the today button on today's month did not flash the day")
        assertEquals(before, after, "the flash did not fade back out")
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
