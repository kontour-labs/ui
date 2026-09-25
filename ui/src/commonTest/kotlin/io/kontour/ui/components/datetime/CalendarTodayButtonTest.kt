package io.kontour.ui.components.datetime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every date picker has a way back to today's month, with a glyph of its own and
 * nothing to supply — shown while it is somewhere else, and gone when asked.
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

    @Test
    fun onTodaysOwnMonthThereIsNone() = runComposeUiTest {
        datePicker(showing = LocalDate(2026, 6, 1))
        onNodeWithContentDescription("Return to today").assertDoesNotExist()
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
