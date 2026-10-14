package io.kontour.ui.components.selection

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The values a slider hands back when no finger is dragging it: from a tap on a
 * range with no width, and from the keys and `setProgress`.
 *
 * Every assertion on a value here is exact. The bugs were a float sum that
 * drifted off the grid by a few units in the last place and a key that left it
 * by most of a step, and a tolerance wide enough to be comfortable would pass
 * both.
 */
@OptIn(ExperimentalTestApi::class)
class SliderTest {

    /**
     * `5f..5f` is a range `require` lets through, and a stepped one threw on
     * the first touch: the detent index divided by the width of the range, and
     * `roundToInt` throws on the `NaN`.
     */
    @Test
    fun aZeroWidthSteppedRangeTakesATapAndAKey() = runComposeUiTest {
        var value by mutableFloatStateOf(5f)
        setContent {
            KontourTheme(reduceMotion = true) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.testTag(Tag).width(Width.dp),
                    valueRange = 5f..5f,
                    steps = 3,
                )
            }
        }

        onNodeWithTag(Tag).performTouchInput { click(Offset(centerX, centerY)) }
        waitForIdle()
        assertEquals(5f, value, "a tap on a zero-width range moved it")

        onNodeWithTag(Tag).requestFocus()
        onNodeWithTag(Tag).performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(5f, value, "an arrow key on a zero-width range moved it")
    }

    /** An arrow lands on a step, from a value that was not on one. */
    @Test
    fun anArrowKeyLandsOnTheStepGrid() = runComposeUiTest {
        var value by mutableFloatStateOf(0.33f)
        setContent {
            KontourTheme(reduceMotion = true) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.testTag(Tag).width(Width.dp),
                    // Detents at 0, ¼, ½, ¾, 1.
                    steps = 3,
                )
            }
        }

        onNodeWithTag(Tag).requestFocus()
        onNodeWithTag(Tag).performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(0.5f, value, "Right from 0.33 on a quarter-step slider should land on the 0.5 detent")
    }

    /** Seven steps up a ten-notch slider is 0.7, not seven float additions of 0.1. */
    @Test
    fun repeatedArrowKeysDoNotDriftOffTheGrid() = runComposeUiTest {
        var value by mutableFloatStateOf(0f)
        setContent {
            KontourTheme(reduceMotion = true) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.testTag(Tag).width(Width.dp),
                    steps = 9,
                )
            }
        }

        onNodeWithTag(Tag).requestFocus()
        repeat(7) {
            onNodeWithTag(Tag).performKeyInput { pressKey(Key.DirectionRight) }
            waitForIdle()
        }
        assertEquals(0.7f, value, "seven steps of a tenth drifted off the grid")
    }

    /** Assistive technology's value is put on the grid too. */
    @Test
    fun setProgressLandsOnTheStepGrid() = runComposeUiTest {
        var value by mutableFloatStateOf(0f)
        setContent {
            KontourTheme(reduceMotion = true) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.testTag(Tag).width(Width.dp),
                    steps = 3,
                )
            }
        }

        onNodeWithTag(Tag).performSemanticsAction(SemanticsActions.SetProgress) { it(0.33f) }
        waitForIdle()
        assertEquals(0.25f, value, "setProgress(0.33) on a quarter-step slider should land on 0.25")
    }

    private companion object {
        const val Tag = "slider"
        const val Width = 400
    }
}
