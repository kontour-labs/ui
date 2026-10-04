package io.kontour.ui.components.selection

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A checkbox, a switch or a radio button on its own can be named.
 *
 * Reported from an app: *"Standalone `Checkbox`, `TriStateCheckbox` and
 * `Switch` can't be named … the 'everything in this area' tri-state checkbox in
 * the grant editor came out in the accessibility tree as an unnamed `button`."*
 * Inside a `SelectionRow` the row's label names them; on their own nothing did.
 */
@OptIn(ExperimentalTestApi::class)
class StandaloneControlNameTest {

    private fun SemanticsNodeInteraction.role(): Role? =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)

    @Test
    fun aLoneTriStateCheckboxIsNamed() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                TriStateCheckbox(
                    state = ToggleableState.Indeterminate,
                    onClick = {},
                    contentDescription = "Everything in this area",
                )
            }
        }
        waitForIdle()
        val node = onNodeWithContentDescription("Everything in this area")
        assertEquals(Role.Checkbox, node.role(), "named, and still a checkbox")
        assertEquals(
            ToggleableState.Indeterminate,
            node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState),
        )
    }

    @Test
    fun aLoneCheckboxIsNamed() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                Checkbox(checked = true, onCheckedChange = {}, contentDescription = "Notify on delay")
            }
        }
        waitForIdle()
        assertEquals(Role.Checkbox, onNodeWithContentDescription("Notify on delay").role())
    }

    @Test
    fun aLoneSwitchIsNamed() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                Switch(checked = false, onCheckedChange = {}, contentDescription = "Live vehicles")
            }
        }
        waitForIdle()
        assertEquals(Role.Switch, onNodeWithContentDescription("Live vehicles").role())
    }

    @Test
    fun aLoneRadioButtonIsNamed() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                RadioButton(selected = true, onClick = {}, contentDescription = "Fastest")
            }
        }
        waitForIdle()
        assertEquals(Role.RadioButton, onNodeWithContentDescription("Fastest").role())
    }

    /** In a row, the row's label is the name, and the control adds nothing to it. */
    @Test
    fun inARowTheLabelIsTheName() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                SelectionRow(selected = true, onSelectedChange = {}, role = Role.Checkbox) { +"Notify on delay" }
            }
        }
        waitForIdle()
        val node = onNodeWithText("Notify on delay", useUnmergedTree = false).fetchSemanticsNode()
        assertNull(
            node.config.getOrNull(SemanticsProperties.ContentDescription),
            "a row's control should carry no description of its own by default",
        )
    }
}
