package io.kontour.ui.components.datetime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A wheel turns from the keyboard and from a screen reader, as well as under a
 * finger.
 *
 * It did not. A drum was a box that announced its value and nothing more: it
 * could not take the focus, so Tab went past every wheel in a `TimePicker`, and
 * a date-time picker's popover opened from the keyboard put the focus on Now,
 * the one thing in it that could hold it. A screen reader could hear the value
 * and not change it.
 */
@OptIn(ExperimentalTestApi::class)
class WheelKeysTest {

    private class Wheel {
        var index by mutableIntStateOf(5)
        var finished = 0
    }

    private fun ComposeUiTest.wheel(infinite: Boolean = false, items: List<String> = Hours): Wheel {
        val wheel = Wheel()
        setContent {
            KontourTheme(reduceMotion = true) {
                WheelPicker(
                    items = items,
                    selectedIndex = wheel.index,
                    onSelectedIndexChange = { wheel.index = it },
                    itemLabel = { it },
                    modifier = Modifier.testTag("wheel"),
                    contentDescription = "Hour",
                    infinite = infinite,
                    onSelectedIndexChangeFinished = { wheel.finished++ },
                )
            }
        }
        waitForIdle()
        return wheel
    }

    private fun ComposeUiTest.drum(): SemanticsNodeInteraction =
        onNode(hasContentDescription("Hour") and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))

    private fun ComposeUiTest.press(keys: List<Key>) {
        for (key in keys) {
            drum().performKeyInput { pressKey(key) }
            waitForIdle()
        }
    }

    @Test
    fun theArrowsTurnTheDrumARowAtATime() = runComposeUiTest {
        val wheel = wheel()
        drum().requestFocus()
        press(listOf(Key.DirectionDown))
        assertEquals(6, wheel.index, "Down turned the drum to ${wheel.index}")
        press(listOf(Key.DirectionUp, Key.DirectionUp))
        assertEquals(4, wheel.index, "Up twice from 6 turned the drum to ${wheel.index}")
        assertEquals("04", drum().fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals(3, wheel.finished, "each key's turn should come to rest once, not ${wheel.finished} times in all")
    }

    @Test
    fun homeEndAndThePageKeysGoToTheEndsAndAFaceful() = runComposeUiTest {
        val wheel = wheel()
        drum().requestFocus()
        press(listOf(Key.MoveEnd))
        assertEquals(23, wheel.index, "End turned the drum to ${wheel.index}")
        press(listOf(Key.MoveHome))
        assertEquals(0, wheel.index, "Home turned the drum to ${wheel.index}")
        press(listOf(Key.PageDown))
        assertEquals(WheelPickerDefaults.VisibleItems, wheel.index, "Page Down from the first row turned the drum to ${wheel.index}")
        press(listOf(Key.DirectionUp, Key.DirectionUp, Key.DirectionUp, Key.DirectionUp, Key.DirectionUp, Key.DirectionUp))
        assertEquals(0, wheel.index, "a drum with ends stops at its first row, not ${wheel.index}")
    }

    @Test
    fun typingTurnsToTheRowItSays() = runComposeUiTest {
        val wheel = wheel()
        drum().requestFocus()
        press(listOf(Key.One, Key.Seven))
        assertEquals(17, wheel.index, "typing 1 and 7 turned the drum to ${wheel.index}")
        // A pause, and what is typed next is a new word.
        mainClock.advanceTimeBy(1_000)
        press(listOf(Key.Zero, Key.Nine))
        assertEquals(9, wheel.index, "typing 0 and 9 turned the drum to ${wheel.index}")
    }

    @Test
    fun anEndlessDrumGoesRoundFromItsFirstRowToItsLast() = runComposeUiTest {
        val wheel = wheel(infinite = true)
        drum().requestFocus()
        press(listOf(Key.MoveHome))
        assertEquals(0, wheel.index)
        press(listOf(Key.DirectionUp))
        assertEquals(23, wheel.index, "Up from the first row of an endless drum turned it to ${wheel.index}")
        press(listOf(Key.DirectionDown, Key.DirectionDown))
        assertEquals(1, wheel.index, "Down twice from the last row turned it to ${wheel.index}")
    }

    @Test
    fun aScreenReaderCanTurnTheDrum() = runComposeUiTest {
        val wheel = wheel()
        drum().performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        waitForIdle()
        assertEquals(12, wheel.index, "a screen reader's setProgress(12) turned the drum to ${wheel.index}")
        val range = drum().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(12f, range.current)
        assertEquals(0f..23f, range.range)
    }

    @Test
    fun aRowTappedUnderReducedMotionComesToRestOnce() = runComposeUiTest {
        val wheel = wheel()
        // Two rows below the middle: the drum's rows are 40dp.
        onNodeWithTag("wheel").performTouchInput { click(center.copy(y = center.y + 80.dp.toPx())) }
        waitForIdle()
        assertEquals(7, wheel.index, "a tap two rows down turned the drum to ${wheel.index}")
        assertEquals(1, wheel.finished, "the turn came to rest ${wheel.finished} times")
    }

    @Test
    fun aClickedDrumTakesTheArrows() = runComposeUiTest {
        val wheel = wheel()
        // On the middle row, which chooses nothing: what it does is take the focus.
        onNodeWithTag("wheel").performMouseInput { click(center) }
        waitForIdle()
        drum().assertIsFocused()
        press(listOf(Key.DirectionDown))
        assertEquals(6, wheel.index, "Down after clicking the drum turned it to ${wheel.index}")
    }

    @Test
    fun aTimePickersWheelsAreEachAStopForTheKeyboard() = runComposeUiTest {
        var time by mutableStateOf(LocalTime(9, 30))
        setContent {
            KontourTheme(reduceMotion = true) {
                TimePicker(value = time, onValueChange = { time = it }, now = null)
            }
        }
        waitForIdle()
        onNode(hasContentDescription("Hour") and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals(LocalTime(10, 30), time, "Down on the hour wheel made it $time")
        onNode(hasContentDescription("Minute") and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals(LocalTime(10, 31), time, "Down on the minute wheel made it $time")
    }

    @Test
    fun aDateTimePickersPopoverOpenedFromTheKeyboardFocusesTheHour() = runComposeUiTest {
        var value by mutableStateOf(kotlinx.datetime.LocalDateTime(2026, 6, 18, 20, 15))
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost {
                    DateTimePicker(
                        value = value,
                        onValueChange = { value = it },
                        // A phone's width, where the wheels are behind the time row.
                        modifier = Modifier.testTag("picker").width(360.dp),
                        now = null,
                    )
                }
            }
        }
        waitForIdle()
        onNode(hasContentDescription("Time") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
            .requestFocus()
            .performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        val hour = onNode(hasContentDescription("Hour") and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
        hour.assertIsFocused()
        hour.performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals(21, value.hour, "Down on the hour wheel in the popover made it ${value.hour}")
    }

    @Test
    fun aWheelSelectsFieldTakesTheArrowsAndItsDrumIsNoStop() = runComposeUiTest {
        var value by mutableStateOf("Wed")
        var finished = 0
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost {
                    io.kontour.ui.components.text.WheelSelect(
                        value = value,
                        options = Days,
                        onValueChange = { value = it },
                        label = "Day",
                        onValueChangeFinished = { finished++ },
                    )
                }
            }
        }
        waitForIdle()
        val field = onNode(hasContentDescription("Day") and SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.DropdownList))
        field.requestFocus().performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals("Thu", value, "Down on a wheel select's field made it $value")
        field.performKeyInput { pressKey(Key.MoveHome) }
        waitForIdle()
        assertEquals("Mon", value, "Home on a wheel select's field made it $value")
        assertEquals(2, finished, "each key's change should finish once")
    }

    private companion object {
        val Days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val Hours = (0..23).map { it.toString().padStart(2, '0') }
    }
}
