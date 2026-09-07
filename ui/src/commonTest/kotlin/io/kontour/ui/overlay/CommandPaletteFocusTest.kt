package io.kontour.ui.overlay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test

/**
 * A palette opens ready to be typed into.
 *
 * Nobody opens one of these to look at it. Every one of them — Spotlight, the
 * command bar in an editor, the browser's own — puts the caret in the field as
 * it appears, and one that does not is a dialog you have to click before you can
 * use, which on the keyboard path it exists for is the whole thing broken.
 *
 * ### Why it asserts on the editable node and not on "something has focus"
 *
 * The palette already requested focus, onto its **container** — the `Surface`
 * carrying `.focusable()` and the `onPreviewKeyEvent` that drives the arrows. So
 * a test asking whether anything was focused passed before this change and after
 * it, and would have gone on passing with the caret nowhere.
 *
 * `hasSetTextAction()` matches the node that can actually receive text, which is
 * the `BasicTextField` inside `SearchField` and not the frame around it. That
 * distinction is real rather than pedantic: `SearchField`'s `modifier` lands on
 * the frame (`TextField.kt:133`), and `TextField`'s own comment warns that "the
 * requester belongs on the input itself… focusing the frame would put the caret
 * nowhere". Compose does delegate a frame-level requester down to the descendant
 * focus target, which is what `NavSearch` relies on — but "does delegate" is a
 * claim worth a test rather than a comment.
 *
 * Moving focus off the container costs the arrows nothing: `onPreviewKeyEvent`
 * runs down the focus path from the root, so an ancestor still sees every key
 * aimed at its descendant. `CommandPaletteKeyboardTest` is the proof of that and
 * must keep passing unchanged.
 */
@OptIn(ExperimentalTestApi::class)
class CommandPaletteFocusTest {

    private class Fixture {
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
                            Command("plan", "Plan a trip", onRun = {}),
                            Command("saved", "Saved trips", onRun = {}),
                        )
                    },
                )
            }
        }
    }

    @Test
    fun theFieldHasTheCaretAsSoonAsThePaletteIsOpen() {
        runComposeUiTest {
            val fixture = Fixture()
            setContent { Harness(fixture) }
            waitForIdle()

            onNode(hasSetTextAction()).assertIsFocused()
        }
    }
}
