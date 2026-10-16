package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A menu's row of actions across the top: buttons, each closing the menu as it runs, walked with the arrows. */
@OptIn(ExperimentalTestApi::class)
class MenuActionRowTest {

    @Test
    fun anActionRunsAndClosesTheMenu() = runComposeUiTest {
        var ran: String? = null
        var open by mutableStateOf(true)
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        DropdownMenu(visible = open, onDismissRequest = { open = false }) {
                            actionRow {
                                action("Copy", SystemIcons.Default.close) { ran = "Copy" }
                                action("Share", SystemIcons.Default.close) { ran = "Share" }
                                action("Save", SystemIcons.Default.close) { ran = "Save" }
                            }
                            item("Report a problem") {}
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Share").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        onNodeWithText("Share").performTouchInput { click() }
        waitForIdle()
        assertEquals("Share", ran)
        assertTrue(!open, "an action closes the menu")
    }

    @Test
    fun theArrowsWalkAlongIt() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        DropdownMenu(visible = true, onDismissRequest = {}) {
                            actionRow {
                                action("Copy", SystemIcons.Default.close) {}
                                action("Share", SystemIcons.Default.close) {}
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Copy").requestFocus()
        waitForIdle()
        onNodeWithText("Copy").assertIsFocused()
        onNodeWithText("Copy").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        onNodeWithText("Share").assertIsFocused()
        onNodeWithText("Share").performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        onNodeWithText("Copy").assertIsFocused()
    }
}
