package io.kontour.ui.contract

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kontour.ui.components.action.SplitButton
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two halves do two different things, and neither does the other's.
 *
 * That division is the entire component — a `SplitButton` whose main half opens
 * the menu is a wide chevron, and one whose chevron runs the default action is a
 * `Button` with a decoration. Both mistakes are invisible in a screenshot and
 * both are one line away in the source, which is what makes this worth asserting
 * rather than reading.
 *
 * The seven contract rules already cover the rest — role, disabled, touch
 * target, name — through the registry.
 */
@OptIn(ExperimentalTestApi::class)
class SplitButtonTest {

    @Test
    fun theMainHalfActsAndDoesNotOpenTheMenu() = runComposeUiTest {
        var actions = 0
        val open = mutableStateOf(false)

        setContent { Harness(open) { actions++ } }
        onNodeWithText("Save").performClick()

        assertEquals(1, actions, "the main half did not run the default action")
        assertFalse(
            open.value,
            "the main half opened the menu — the whole point of a split button " +
                "is that the common case costs one tap and never shows a list",
        )
    }

    @Test
    fun theChevronOpensTheMenuAndDoesNotAct() = runComposeUiTest {
        var actions = 0
        val open = mutableStateOf(false)

        setContent { Harness(open) { actions++ } }
        onNodeWithContentDescription(MenuLabel).performClick()

        assertTrue(open.value, "the chevron did not open the menu")
        assertEquals(
            0,
            actions,
            "the chevron ran the default action as well as opening the menu — a " +
                "user reaching for the alternatives has just done the thing they " +
                "were looking for an alternative to",
        )
    }

    @Test
    fun theMenuHoldsTheAlternatives() = runComposeUiTest {
        val open = mutableStateOf(false)
        setContent { Harness(open) {} }

        onNodeWithContentDescription(MenuLabel).performClick()
        waitForIdle()

        onNodeWithText("Save and close").assertIsDisplayed()
        onNodeWithText("Save a copy").assertIsDisplayed()
    }

    @Composable
    private fun Harness(open: androidx.compose.runtime.MutableState<Boolean>, onClick: () -> Unit) {
        KontourTheme(reduceMotion = true) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    SplitButton(
                        onClick = onClick,
                        expanded = open.value,
                        onExpandedChange = { open.value = it },
                        menuContentDescription = MenuLabel,
                        menu = {
                            item("Save and close", onClick = {})
                            item("Save a copy", onClick = {})
                        },
                    ) {
                        +"Save"
                    }
                }
            }
        }
    }

    private companion object {
        const val MenuLabel = "Other save options"
    }
}
