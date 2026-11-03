package io.kontour.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * A hand-drawn option made pressable says whether it is the one chosen, and
 * still presses. An app kept a pressable of its own for this.
 */
@OptIn(ExperimentalTestApi::class)
class PressableSelectedTest {

    @Test
    fun anOptionSaysWhetherItIsChosen() = runComposeUiTest {
        var chosen by mutableStateOf(false)
        setContent {
            KontourTheme(darkTheme = false) {
                Box(
                    Modifier
                        .pressable(onClick = { chosen = !chosen }, role = Role.Tab, onLongClick = {}, selected = chosen)
                        .size(40.dp)
                        .testTag("tab"),
                )
            }
        }
        onNodeWithTag("tab").assertIsNotSelected().assertHasClickAction()
        onNodeWithTag("tab").performClick()
        onNodeWithTag("tab").assertIsSelected()
    }

    @Test
    fun anActionSaysNothingOfBeingChosen() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false) {
                Box(Modifier.pressable(onClick = {}).size(40.dp).testTag("button"))
            }
        }
        val node = onNodeWithTag("button").fetchSemanticsNode()
        assertNull(node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Selected) { null })
    }
}
