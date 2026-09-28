package io.kontour.ui.components.selection

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A knob's value can be typed: tap the readout, type, done — asked for from a
 * phone as "tap value to edit".
 */
@OptIn(ExperimentalTestApi::class)
class KnobEditTest {

    @Test
    fun aTapOnTheFaceOpensTheValueAndDoneSetsIt() = runComposeUiTest {
        var value by mutableStateOf(40f)
        setContent {
            KontourTheme(locale = Locale("en-AU")) {
                Knob(value = value, onValueChange = { value = it }, valueRange = 0f..100f, contentDescription = "Volume") {
                    +"${value.toInt()}"
                }
            }
        }
        onNodeWithContentDescription("Volume").performTouchInput { click(center) }
        val field = onNode(hasSetTextAction())
        assertEquals("40", field.text(), "the field should hold the value")
        field.performTextReplacement("72")
        field.performImeAction()
        waitForIdle()
        assertEquals(72f, value)
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun aTypedValueIsKeptInRangeAndPutOnAStep() = runComposeUiTest {
        var value by mutableStateOf(40f)
        setContent {
            KontourTheme(locale = Locale("en-AU")) {
                Knob(value = value, onValueChange = { value = it }, valueRange = 0f..100f, steps = 9, contentDescription = "Volume") {
                    +"${value.toInt()}"
                }
            }
        }
        typeInto("Volume", "47")
        assertTrue(abs(value - 50f) < 0.01f, "47 on a knob that stops every ten should land on 50, was $value")
        typeInto("Volume", "150")
        assertEquals(100f, value, "150 on a knob that ends at 100 should be 100")
    }

    @Test
    fun somethingThatIsNotANumberAndEscapeBothLeaveTheValue() = runComposeUiTest {
        var value by mutableStateOf(40f)
        var finished = 0
        setContent {
            KontourTheme(locale = Locale("en-AU")) {
                Knob(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..100f,
                    onValueChangeFinished = { finished++ },
                    contentDescription = "Volume",
                ) { +"${value.toInt()}" }
            }
        }
        typeInto("Volume", "loud")
        assertEquals(40f, value, "a word is not a value")

        onNodeWithContentDescription("Volume").performTouchInput { click(center) }
        val field = onNode(hasSetTextAction())
        field.performTextReplacement("90")
        field.performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(40f, value, "Escape should put the value back")
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
        assertEquals(0, finished, "nothing was set, so nothing finished")
    }

    @Test
    fun leavingTheFieldSetsIt() = runComposeUiTest {
        var value by mutableStateOf(40f)
        lateinit var elsewhere: FocusRequester
        setContent {
            elsewhere = remember { FocusRequester() }
            KontourTheme(locale = Locale("en-AU")) {
                Column {
                    Knob(value = value, onValueChange = { value = it }, valueRange = 0f..100f, contentDescription = "Volume") {
                        +"${value.toInt()}"
                    }
                    Box(Modifier.size(40.dp).focusRequester(elsewhere).focusable())
                }
            }
        }
        onNodeWithContentDescription("Volume").performTouchInput { click(center) }
        onNode(hasSetTextAction()).performTextReplacement("15")
        runOnIdle { elsewhere.requestFocus() }
        waitForIdle()
        assertEquals(15f, value)
    }

    @Test
    fun enterOnTheFocusedKnobOpensIt() = runComposeUiTest {
        var value by mutableStateOf(40f)
        setContent {
            KontourTheme(locale = Locale("en-AU")) {
                Knob(value = value, onValueChange = { value = it }, valueRange = 0f..100f, contentDescription = "Volume") {
                    +"${value.toInt()}"
                }
            }
        }
        val knob = onNodeWithContentDescription("Volume")
        knob.requestFocus()
        knob.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        val field = onNode(hasSetTextAction())
        field.performTextReplacement("64")
        field.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(64f, value)
    }

    @Test
    fun theReadersSeparatorIsTheOneTypedAndShown() = runComposeUiTest {
        var value by mutableStateOf(4f)
        setContent {
            KontourTheme(locale = Locale("de-DE")) {
                Knob(value = value, onValueChange = { value = it }, valueRange = 0f..10f, contentDescription = "Lautstärke") {
                    +"$value"
                }
            }
        }
        onNodeWithContentDescription("Lautstärke").performTouchInput { click(center) }
        val field = onNode(hasSetTextAction())
        assertEquals("4,0", field.text(), "a German reader's field should use a decimal comma")
        field.performTextReplacement("7,5")
        field.performImeAction()
        waitForIdle()
        assertTrue(abs(value - 7.5f) < 0.001f, "7,5 typed by a German reader is seven and a half, was $value")
    }

    @Test
    fun aKnobShowingNoFigureHasNothingToOpen() = runComposeUiTest {
        var value by mutableStateOf(40f)
        setContent {
            KontourTheme(locale = Locale("en-AU")) {
                Knob(value = value, onValueChange = { value = it }, valueRange = 0f..100f, contentDescription = "Volume")
            }
        }
        val knob = onNodeWithContentDescription("Volume")
        knob.performTouchInput { click(center) }
        waitForIdle()
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
        knob.requestFocus()
        knob.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
        assertEquals(40f, value)
    }

    @Test
    fun theFieldHoldsAsManyPlacesAsAStepNeeds() {
        assertEquals(0, knobEditDigits(100f, 0), "a smooth 0 to 100 types whole numbers")
        assertEquals(2, knobEditDigits(1f, 0), "a smooth 0 to 1 types hundredths")
        assertEquals(1, knobEditDigits(1f, 9), "ten steps of a tenth type one place")
        assertEquals(0, knobEditDigits(100f, 9), "steps of ten type whole numbers")
        assertEquals(3, knobEditDigits(1f, 10), "an eleventh is written to three places, and no more")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.typeInto(knob: String, text: String) {
        onNodeWithContentDescription(knob).performTouchInput { click(center) }
        val field = onNode(hasSetTextAction())
        field.performTextReplacement(text)
        field.performImeAction()
        waitForIdle()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.text(): String =
        fetchSemanticsNode().config[SemanticsProperties.EditableText].text
}
