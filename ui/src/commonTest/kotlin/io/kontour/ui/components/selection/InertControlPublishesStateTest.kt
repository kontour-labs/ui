package io.kontour.ui.components.selection

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import io.kontour.ui.components.list.SettingRow
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test

/**
 * A control handed a null callback still says what it is showing.
 *
 * `Checkbox`, `RadioButton` and `Switch` all take a nullable callback, and
 * passing `null` means "the row owns the click, you just show the state". That
 * is the shape `SelectionRow` uses and the shape every settings screen ends up
 * in.
 *
 * The trap is that a *row* only publishes state when it is `toggleable` or
 * `selectable`. `SettingRow` is `clickable` — it is a row you tap to open
 * something, which happens to carry a switch — so nothing above the control
 * says on or off. If the control also says nothing, the whole row announces as
 * a button with a name and no state, and a screen-reader user cannot tell a
 * setting that is on from one that is off.
 *
 * `Switch` had this branch from the start. `Checkbox` and `RadioButton` did
 * not, and their own `@param` text promised it — "non-interactive while still
 * showing state". Reverting either `semantics {}` block fails this test.
 */
@OptIn(ExperimentalTestApi::class)
class InertControlPublishesStateTest {

    @Test
    fun checkboxInAClickableRowAnnouncesItsTick() = runComposeUiTest {
        setContent {
            KontourTheme {
                SettingRow(modifier = Modifier.testTag("row"), onClick = {}) {
                    +"Notify me about delays"
                    trailing { Checkbox(checked = true, onCheckedChange = null) }
                }
            }
        }

        onNodeWithTag("row").assert(hasToggleState(ToggleableState.On))
    }

    @Test
    fun switchInAClickableRowAnnouncesItsPosition() = runComposeUiTest {
        setContent {
            KontourTheme {
                SettingRow(modifier = Modifier.testTag("row"), onClick = {}) {
                    +"Live alerts"
                    trailing { Switch(checked = false, onCheckedChange = null) }
                }
            }
        }

        onNodeWithTag("row").assert(hasToggleState(ToggleableState.Off))
    }

    @Test
    fun radioButtonInAClickableRowAnnouncesBeingChosen() = runComposeUiTest {
        setContent {
            KontourTheme {
                SettingRow(modifier = Modifier.testTag("row"), onClick = {}) {
                    +"Fastest route"
                    trailing { RadioButton(selected = true, onClick = null) }
                }
            }
        }

        onNodeWithTag("row").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        )
    }

    private fun hasToggleState(expected: ToggleableState) =
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, expected)
}
