package io.kontour.ui.overlay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The keyboard path, which is the whole reason a palette exists.
 *
 * Nobody opens one of these with a mouse. A palette whose arrows or Enter are
 * wrong is a menu with extra steps, and every one of the failures below looks
 * completely correct on screen — the highlight moves, the list filters, and the
 * wrong thing runs.
 */
@OptIn(ExperimentalTestApi::class)
class CommandPaletteKeyboardTest {

    private class Fixture {
        val ran = mutableListOf<String>()
        var visible by mutableStateOf(true)
        val query = TextFieldState()
    }

    @Composable
    private fun Harness(fixture: Fixture) {
        KontourTheme {
            OverlayHost(Modifier.fillMaxSize()) {
                CommandPalette(
                    visible = fixture.visible,
                    onDismissRequest = { fixture.visible = false },
                    query = fixture.query,
                    commands = remember {
                        listOf(
                            Command("plan", "Plan a trip", onRun = { fixture.ran += "plan" }),
                            Command("saved", "Saved trips", onRun = { fixture.ran += "saved" }),
                            Command("settings", "Settings", onRun = { fixture.ran += "settings" }),
                        )
                    },
                )
            }
        }
    }

    /** Enter runs whatever is highlighted, which starts at the top. */
    @Test
    fun enterRunsTheHighlightedCommand() = runComposeUiTest {
        val fixture = Fixture()
        setContent { Harness(fixture) }
        waitForIdle()

        onNodeWithText("Plan a trip").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(listOf("plan"), fixture.ran)
        assertTrue(!fixture.visible, "running a command should close the palette")
    }

    /** Down moves the highlight, so Enter runs the second one. */
    @Test
    fun downThenEnterRunsTheSecondCommand() = runComposeUiTest {
        val fixture = Fixture()
        setContent { Harness(fixture) }
        waitForIdle()

        onNodeWithText("Plan a trip").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        waitForIdle()

        assertEquals(listOf("saved"), fixture.ran)
    }

    /**
     * Up from the first row wraps to the last.
     *
     * Kotlin's `%` keeps the sign of its left operand, so `(0 - 1) % 3` is `-1`,
     * not `2`. Without the `+ size` the highlight goes out of bounds on the
     * first keystroke anyone tries, and Enter then runs nothing at all — a
     * palette that looks alive and does nothing. Reverting that term fails here.
     */
    @Test
    fun upFromTheFirstRowWrapsToTheLast() = runComposeUiTest {
        val fixture = Fixture()
        setContent { Harness(fixture) }
        waitForIdle()

        onNodeWithText("Plan a trip").performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.Enter)
        }
        waitForIdle()

        assertEquals(listOf("settings"), fixture.ran)
    }

    /**
     * A keystroke that shrinks the list resets the highlight to the top.
     *
     * Arrow down to the third command, then type — the list is now one row long
     * and index 2 points at nothing. Enter either runs the wrong command or runs
     * none, and the user pressed it because the row they wanted was right there
     * under the highlight.
     *
     * Reverting the `LaunchedEffect(text) { highlighted = 0 }` fails this.
     */
    @Test
    fun typingResetsTheHighlightToTheTop() = runComposeUiTest {
        val fixture = Fixture()
        setContent { Harness(fixture) }
        waitForIdle()

        onNodeWithText("Plan a trip").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
        }
        waitForIdle()

        // "trip" matches only "Plan a trip", so the stale index 2 is off the end.
        fixture.query.edit { replace(0, length, "trip") }
        waitForIdle()

        onNodeWithText("Plan a trip").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(
            listOf("plan"),
            fixture.ran,
            "after filtering, Enter should run the first match rather than a stale index",
        )
    }

    /** Escape closes it without running anything. */
    @Test
    fun escapeDismissesWithoutRunning() = runComposeUiTest {
        val fixture = Fixture()
        setContent { Harness(fixture) }
        waitForIdle()

        onNodeWithText("Plan a trip").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertTrue(fixture.ran.isEmpty(), "escape ran ${fixture.ran}")
        assertTrue(!fixture.visible, "escape should close the palette")
    }
}
