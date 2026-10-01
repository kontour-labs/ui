package io.kontour.ui.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import io.kontour.ui.foundation.GroupPosition
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The row reports what it should now be, not what it already was.
 *
 * `SelectionRow` used to take `onClick: () -> Unit` for all three roles, which
 * meant `Modifier.toggleable` handed it the new value and it threw it away. Every
 * toggle call site then wrote `{ x = !x }` — the shape that reads the state a
 * second time, from a lambda that may have captured a stale copy of it.
 *
 * A screenshot cannot see any of this, and neither can `ComponentContractTest`:
 * it only asks whether the callback fires at all.
 */
@OptIn(ExperimentalTestApi::class)
class SelectionRowTest {

    @Test
    fun aToggleRowReportsTheValueItIsMovingTo() {
        runComposeUiTest {
            var checked by mutableStateOf(false)
            setContent {
                KontourTheme(reduceMotion = true) {
                    SelectionRow(
                        selected = checked,
                        onSelectedChange = { checked = it },
                        role = Role.Switch,
                    ) {
                        +"Show live vehicles"
                        trailing { Switch(checked = checked, onCheckedChange = null) }
                    }
                }
            }

            onNodeWithText("Show live vehicles").performClick()
            waitForIdle()
            assertEquals(
                true,
                checked,
                "the row reported its old value — a caller assigning the value " +
                    "straight through can never turn the setting on",
            )

            onNodeWithText("Show live vehicles").performClick()
            waitForIdle()
            assertEquals(
                false,
                checked,
                "the row reported a constant rather than the value it moved to",
            )
        }
    }

    /**
     * A radio is turned on by pressing it and off by pressing a sibling, so
     * pressing one that is already selected is not a request to deselect it —
     * `Modifier.selectable` fires either way, and the group owns the pair.
     */
    @Test
    fun pressingASelectedRadioRowStillReportsSelected() {
        runComposeUiTest {
            val reported = mutableListOf<Boolean>()
            setContent {
                KontourTheme(reduceMotion = true) {
                    SelectionRow(
                        selected = true,
                        onSelectedChange = { reported += it },
                        role = Role.RadioButton,
                    ) {
                        +"Leave now"
                        trailing { RadioButton(selected = true, onClick = null) }
                    }
                }
            }

            onNodeWithText("Leave now").performClick()
            waitForIdle()
            assertEquals(
                listOf(true),
                reported,
                "a radio row reported false for a press on the already-selected " +
                    "option, which would clear a selection the group requires",
            )
        }
    }

    /**
     * In a group the row is drawn by the list row, and it is still one control
     * named by its label, with its role and its state, that a press toggles.
     */
    @Test
    fun aGroupedToggleRowIsOneNamedControlWithItsState() {
        runComposeUiTest {
            var on by mutableStateOf(false)
            setContent {
                KontourTheme(reduceMotion = true) {
                    SelectionRow(
                        selected = on,
                        onSelectedChange = { on = it },
                        role = Role.Switch,
                        position = GroupPosition.First,
                    ) {
                        +"Show live vehicles"
                        trailing { Switch(checked = on, onCheckedChange = null) }
                    }
                }
            }
            onNode(hasText("Show live vehicles") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
                .assertIsOff()
                .performClick()
            waitForIdle()
            assertEquals(true, on, "the grouped row did not toggle")
            onNode(hasText("Show live vehicles") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
                .assertIsOn()
        }
    }

    /** A row that opens a choice is a button named by its label, and runs its `onClick`. */
    @Test
    fun aRowThatOpensAChoiceIsANamedButton() {
        runComposeUiTest {
            var opened = 0
            setContent {
                KontourTheme(reduceMotion = true) {
                    SelectionRow(onClick = { opened++ }, position = GroupPosition.Last) {
                        +"Distances"
                        trailing { +"Kilometres" }
                    }
                }
            }
            onNode(hasText("Distances") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertHasClickAction()
                .performClick()
            waitForIdle()
            assertEquals(1, opened)
        }
    }
}
