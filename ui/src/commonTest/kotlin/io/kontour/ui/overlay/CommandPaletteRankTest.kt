package io.kontour.ui.overlay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A command palette puts the commands closest to what was typed first, and
 * with nothing typed shows them in the order it was given.
 *
 * Asked for as *"order results by similarity to the prompt, not just by their
 * default order (obviously if there is no prompt, go back to the default)"*. It
 * filtered and kept the caller's order, so "set" left *Reset zoom* at the top,
 * where the highlight is and what Enter runs, and *Set theme* third.
 */
@OptIn(ExperimentalTestApi::class)
class CommandPaletteRankTest {

    private val ran = mutableListOf<String>()
    private val query = TextFieldState()

    private fun ComposeUiTest.palette() {
        setContent {
            KontourTheme {
                OverlayHost(Modifier.fillMaxSize()) {
                    CommandPalette(
                        visible = true,
                        onDismissRequest = {},
                        query = query,
                        commands = listOf(
                            Command("reset", "Reset zoom", onAction = { ran += "reset" }),
                            Command("prefs", "Preferences", onAction = { ran += "prefs" }, keywords = listOf("settings")),
                            Command("theme", "Set theme", onAction = { ran += "theme" }),
                            Command("assets", "Asset library", onAction = { ran += "assets" }),
                            Command("sync", "Sync settings", onAction = { ran += "sync" }),
                        ),
                    )
                }
            }
        }
        waitForIdle()
    }

    /** The labels on screen, top to bottom. */
    private fun ComposeUiTest.order(vararg labels: String): List<String> =
        labels.sortedBy { onAllNodesWithText(it).fetchSemanticsNodes().single().boundsInRoot.top }

    @Test
    fun theClosestComeFirst() = runComposeUiTest {
        palette()
        onNode(hasSetTextAction()).performTextInput("set")
        waitForIdle()
        val shown = listOf("Set theme", "Sync settings", "Preferences", "Reset zoom", "Asset library")
        assertEquals(shown, order(*shown.toTypedArray()), "the label itself, then a word in one, then a keyword, then inside a word")
    }

    @Test
    fun enterRunsTheClosest() = runComposeUiTest {
        palette()
        onNode(hasSetTextAction()).performTextInput("set")
        waitForIdle()
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(listOf("theme"), ran)
    }

    @Test
    fun withNothingTypedTheOrderIsTheCallers() = runComposeUiTest {
        palette()
        onNode(hasSetTextAction()).performTextInput("set")
        waitForIdle()
        runOnUiThread { query.clearText() }
        waitForIdle()
        val given = listOf("Reset zoom", "Preferences", "Set theme", "Asset library", "Sync settings")
        assertEquals(given, order(*given.toTypedArray()))
    }
}
