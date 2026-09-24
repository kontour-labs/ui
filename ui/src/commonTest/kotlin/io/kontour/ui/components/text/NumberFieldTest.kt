package io.kontour.ui.components.text

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A number field whose rules narrow under it clears, rather than freezing.
 *
 * Reported from the catalog: with decimals and negatives on, type `-1.5`, turn them
 * off, and the field could not be edited at all. The filters judge the whole text an
 * edit would leave, and every edit to `-1.5` — a backspace included — still leaves a
 * minus or a point in it, so every one was reverted. The field now clears when its
 * rules no longer admit what it holds.
 */
@OptIn(ExperimentalTestApi::class)
class NumberFieldTest {

    @Test
    fun narrowingTheRulesClearsATextTheyNoLongerAdmit() = runComposeUiTest {
        val state = TextFieldState("-1.5")
        var decimals by mutableStateOf(true)
        var negatives by mutableStateOf(true)
        setContent {
            KontourTheme(reduceMotion = true) {
                NumberField(state = state, allowDecimal = decimals, allowNegative = negatives)
            }
        }
        waitForIdle()
        assertEquals("-1.5", state.text.toString(), "the field should start with what it was given")

        decimals = false
        negatives = false
        waitForIdle()
        assertEquals("", state.text.toString(), "narrowed rules left an inadmissible text in place")

        onNode(hasSetTextAction()).requestFocus()
        onNode(hasSetTextAction()).performTextInput("3")
        waitForIdle()
        assertEquals("3", state.text.toString(), "the cleared field would not take a digit")
    }

    @Test
    fun aTextTheNarrowerRulesStillAdmitIsKept() = runComposeUiTest {
        val state = TextFieldState("42")
        var decimals by mutableStateOf(true)
        setContent {
            KontourTheme(reduceMotion = true) {
                NumberField(state = state, allowDecimal = decimals, allowNegative = true)
            }
        }
        waitForIdle()
        decimals = false
        waitForIdle()
        assertEquals("42", state.text.toString(), "a whole number was cleared by turning decimals off")
    }

    /** Negatives without decimals: the knob did nothing on its own. */
    @Test
    fun aNegativeWholeNumberCanBeTyped() = runComposeUiTest {
        val state = TextFieldState("")
        setContent {
            KontourTheme(reduceMotion = true) {
                NumberField(state = state, allowDecimal = false, allowNegative = true)
            }
        }
        onNode(hasSetTextAction()).requestFocus()
        onNode(hasSetTextAction()).performTextInput("-")
        onNode(hasSetTextAction()).performTextInput("4")
        waitForIdle()
        assertEquals("-4", state.text.toString())
    }
}
