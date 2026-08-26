package io.kontour.ui.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
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
}
