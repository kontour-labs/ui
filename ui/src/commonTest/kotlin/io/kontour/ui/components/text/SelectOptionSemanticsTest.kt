package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A select's options are announced as what they are: one of a set for a
 * `Select` or a `Combobox`, each on or off for a `MultiSelect`.
 *
 * Reported from an app: *"In the accessibility tree … Select options [show up
 * as plain buttons] … I had to work around [it] in the tests."* Only the chosen
 * option was a radio button. The rest were buttons, so a screen reader heard one
 * radio button among buttons, and nothing said the others were choices at all.
 */
@OptIn(ExperimentalTestApi::class)
class SelectOptionSemanticsTest {

    @Test
    fun aSelectsOptionsAreOneOfASet() = runComposeUiTest {
        show {
            Select(
                value = "Train",
                options = Options,
                onValueChange = {},
                label = "Mode",
                state = rememberSelectState(initialExpanded = true),
            )
        }
        assertOneOf("a select", chosen = "Train")
    }

    @Test
    fun aComboboxsOptionsAreOneOfASet() = runComposeUiTest {
        show {
            Combobox(
                value = "Ferry",
                options = Options,
                onValueChange = {},
                label = "Mode",
                state = rememberSelectState(initialExpanded = true),
            )
        }
        assertOneOf("a combobox", chosen = "Ferry")
    }

    @Test
    fun aMultiSelectsOptionsAreEachOnOrOff() = runComposeUiTest {
        show {
            MultiSelect(
                value = setOf("Bus", "Ferry"),
                options = Options,
                onValueChange = {},
                label = "Modes",
                state = rememberSelectState(initialExpanded = true),
            )
        }
        for (option in Options) {
            val node = option(option)
            assertEquals(Role.Checkbox, node.config.getOrNull(SemanticsProperties.Role), "a multi-select's $option")
            assertEquals(
                if (option == "Train") ToggleableState.Off else ToggleableState.On,
                node.config.getOrNull(SemanticsProperties.ToggleableState),
                "a multi-select's $option should say whether it is ticked",
            )
        }
    }

    /** A menu of actions is a menu of buttons: nothing in it is a choice. */
    @Test
    fun aMenusActionsStayButtons() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.size(40.dp)) {
                        DropdownMenu(visible = true, onDismissRequest = {}) {
                            item("Share") {}
                            item("Copy") {}
                        }
                    }
                }
            }
        }
        waitForIdle()
        for (action in listOf("Share", "Copy")) {
            val node = option(action)
            assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role), action)
            assertNull(node.config.getOrNull(SemanticsProperties.Selected), "$action is not a choice")
            assertNull(node.config.getOrNull(SemanticsProperties.ToggleableState), "$action is not a choice")
        }
    }

    private fun ComposeUiTest.show(field: @Composable () -> Unit) {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.width(280.dp)) { field() }
                }
            }
        }
        waitForIdle()
    }

    /** The row in the menu with this text, rather than the field showing it as its value. */
    private fun ComposeUiTest.option(text: String): SemanticsNode {
        val rows = onAllNodesWithText(text).fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsProperties.Role) != Role.DropdownList }
        assertEquals(1, rows.size, "one row should say \"$text\", and ${rows.size} did")
        return rows.single()
    }

    private fun ComposeUiTest.assertOneOf(what: String, chosen: String) {
        for (option in Options) {
            val node = option(option)
            assertEquals(Role.RadioButton, node.config.getOrNull(SemanticsProperties.Role), "$what's $option")
            assertEquals(
                option == chosen,
                node.config.getOrNull(SemanticsProperties.Selected),
                "$what's $option should say whether it is the one chosen, either way",
            )
            assertTrue(
                generateSequence(node.parent) { it.parent }.any { SemanticsProperties.SelectableGroup in it.config },
                "$what's options should be one group, so a reader says how many there are",
            )
        }
    }

    private companion object {
        val Options = listOf("Bus", "Train", "Ferry")
    }
}
