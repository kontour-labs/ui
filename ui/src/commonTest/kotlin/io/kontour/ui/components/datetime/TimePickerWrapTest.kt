package io.kontour.ui.components.datetime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.locale.DateTimeConventions
import io.kontour.ui.locale.HourCycle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A time picker's hour and minute wheels go round by default, as a clock does,
 * and on a 12-hour clock the day period turns with the hour.
 *
 * Asked for for 0.5.1 as *"it wraps infinitely by default"*. The wheels used
 * to stop at 23 and :59, so 23:30 to half past midnight was twenty-three hours
 * back up the wheel.
 */
@OptIn(ExperimentalTestApi::class)
class TimePickerWrapTest {

    private val twentyFour = LocaleFormats.english(DateTimeConventions(hourCycle = HourCycle.H23))
    private val twelve = LocaleFormats.english(DateTimeConventions(hourCycle = HourCycle.H12))

    private fun turn(start: LocalTime, formats: LocaleFormats, wheel: String, key: Key, infinite: Boolean = true): LocalTime {
        var time by mutableStateOf(start)
        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    if (infinite) {
                        TimePicker(value = time, onValueChange = { time = it }, now = null, formats = formats)
                    } else {
                        TimePicker(value = time, onValueChange = { time = it }, infinite = false, now = null, formats = formats)
                    }
                }
            }
            waitForIdle()
            drum(wheel).requestFocus().performKeyInput { pressKey(key) }
            waitForIdle()
        }
        return time
    }

    private fun ComposeUiTest.drum(name: String) =
        onNode(hasContentDescription(name) and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))

    @Test
    fun theHoursGoRoundMidnight() {
        assertEquals(LocalTime(0, 30), turn(LocalTime(23, 30), twentyFour, "Hour", Key.DirectionDown))
        assertEquals(LocalTime(23, 30), turn(LocalTime(0, 30), twentyFour, "Hour", Key.DirectionUp))
    }

    @Test
    fun theMinutesGoRoundTheHourAndLeaveItAsItIs() {
        assertEquals(LocalTime(10, 0), turn(LocalTime(10, 59), twentyFour, "Minute", Key.DirectionDown))
        assertEquals(LocalTime(10, 59), turn(LocalTime(10, 0), twentyFour, "Minute", Key.DirectionUp))
    }

    /** 11 PM on is 12 AM, not noon; and back again. */
    @Test
    fun onATwelveHourClockTheDayPeriodTurnsWithTheHour() {
        assertEquals(LocalTime(0, 15), turn(LocalTime(23, 15), twelve, "Hour", Key.DirectionDown), "11 PM on")
        assertEquals(LocalTime(23, 15), turn(LocalTime(0, 15), twelve, "Hour", Key.DirectionUp), "12 AM back")
        assertEquals(LocalTime(12, 15), turn(LocalTime(11, 15), twelve, "Hour", Key.DirectionDown), "11 AM on")
        // 12 to 1 is not where the period changes.
        assertEquals(LocalTime(13, 15), turn(LocalTime(12, 15), twelve, "Hour", Key.DirectionDown), "12 PM on")
    }

    /** Off, the wheels stop at their ends, as before 0.5.1. */
    @Test
    fun turnedOffTheWheelsStopAtTheirEnds() {
        assertEquals(LocalTime(23, 30), turn(LocalTime(23, 30), twentyFour, "Hour", Key.DirectionDown, infinite = false))
        assertEquals(LocalTime(10, 59), turn(LocalTime(10, 59), twentyFour, "Minute", Key.DirectionDown, infinite = false))
    }

    @Test
    fun theDayPeriodChangesBetweenElevenAndTwelveAndNowhereElse() {
        val h12 = (1..12).toList()
        fun row(hour: Int) = h12.indexOf(hour)
        assertTrue(passesPeriodChange(h12, row(11), row(12)))
        assertTrue(passesPeriodChange(h12, row(12), row(11)))
        assertFalse(passesPeriodChange(h12, row(12), row(1)), "12 to 1 is within the period")
        assertFalse(passesPeriodChange(h12, row(1), row(12)))
        // A flick that carries the wheel past it in one report.
        assertTrue(passesPeriodChange(h12, row(10), row(2)))
        assertTrue(passesPeriodChange(h12, row(2), row(9)))
        assertFalse(passesPeriodChange(h12, row(3), row(5)))
        // Japan's 0–11: the change is between 11 and 0, where the wheel wraps.
        val h11 = (0..11).toList()
        assertTrue(passesPeriodChange(h11, h11.indexOf(11), h11.indexOf(0)))
        assertFalse(passesPeriodChange(h11, h11.indexOf(0), h11.indexOf(1)))
    }
}
