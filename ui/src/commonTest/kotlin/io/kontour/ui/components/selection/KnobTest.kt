package io.kontour.ui.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A knob is operable without a pointer: from the keyboard and by assistive technology. */
@OptIn(ExperimentalTestApi::class)
class KnobTest {

    @Test
    fun theKeysStepItAndHomeAndEndGoToTheEnds() = runComposeUiTest {
        var value by mutableStateOf(0.5f)
        setContent {
            KontourTheme {
                Knob(value = value, onValueChange = { value = it }, steps = 9, contentDescription = "Volume")
            }
        }
        val knob = onNodeWithContentDescription("Volume")
        knob.requestFocus()
        knob.performKeyInput { pressKey(Key.DirectionRight) }
        assertTrue(abs(value - 0.6f) < 0.001f, "a step up from 0.5 of ten intervals should be 0.6, was $value")
        knob.performKeyInput { pressKey(Key.DirectionDown) }
        knob.performKeyInput { pressKey(Key.DirectionDown) }
        assertTrue(abs(value - 0.4f) < 0.001f, "two steps down from 0.6 should be 0.4, was $value")
        knob.performKeyInput { pressKey(Key.MoveEnd) }
        assertEquals(1f, value, "End should go to the top of the range")
        knob.performKeyInput { pressKey(Key.MoveHome) }
        assertEquals(0f, value, "Home should go to the bottom of the range")
    }

    @Test
    fun assistiveTechnologyCanSetItUnlessItIsDisabled() = runComposeUiTest {
        var value by mutableStateOf(0.2f)
        var enabled by mutableStateOf(true)
        setContent {
            KontourTheme {
                Knob(value = value, onValueChange = { value = it }, enabled = enabled, contentDescription = "Volume")
            }
        }
        onNodeWithContentDescription("Volume").performSemanticsAction(SemanticsActions.SetProgress) { it(0.7f) }
        assertEquals(0.7f, value)
        enabled = false
        waitForIdle()
        onNodeWithContentDescription("Volume")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
    }

    @Test
    fun upAndRightAreMoreDownAndLeftAreLess() {
        val travel = 400f
        assertTrue(knobDragTurn(Offset(0f, -100f), travel) > 0f, "a drag up should turn it up")
        assertTrue(knobDragTurn(Offset(100f, 0f), travel) > 0f, "a drag right should turn it up")
        assertTrue(knobDragTurn(Offset(0f, 100f), travel) < 0f, "a drag down should turn it down")
        assertTrue(knobDragTurn(Offset(-100f, 0f), travel) < 0f, "a drag left should turn it down")
        assertEquals(0.25f, knobDragTurn(Offset(0f, -100f), travel), "a quarter of the travel is a quarter of the range")
        assertEquals(0.5f, knobDragTurn(Offset(100f, -100f), travel), "up and to the right is both")
    }
}
