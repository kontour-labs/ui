package io.kontour.ui.components.datetime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A time between two of the minute wheel's steps opens on the step before it,
 * in the same hour.
 */
@OptIn(ExperimentalTestApi::class)
class TimePickerStepTest {

    @Test
    fun aMinutePastTheLastStepOpensOnTheLastStepNotTheTopOfTheHour() = runComposeUiTest {
        var value by mutableStateOf(LocalTime(9, 58))
        val seen = mutableListOf<LocalTime>()
        setContent {
            KontourTheme(reduceMotion = true) {
                TimePicker(
                    value = value,
                    onValueChange = { value = it; seen += it },
                    minuteStep = 5,
                    now = null,
                )
            }
        }
        waitForIdle()
        // The step after 9:58 is the next hour's 10:00, which the minute wheel
        // cannot reach; it opened on this hour's :00 instead, and reported it.
        assertEquals(LocalTime(9, 55), value, "opening the picker moved the time: $seen")
    }
}
