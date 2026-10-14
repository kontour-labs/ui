package io.kontour.ui.components.text

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A right-click in a text box opens the library's menu, in the library's window.
 *
 * Reported as "replace the right-click menu in every text box", and it took a
 * measurement to find out what was actually there. Two different answers,
 * depending on where you look:
 *
 * * **On the JVM**, Compose opens a context menu of its own. It shows up here as
 *   a **second semantics root** — a separate popup window, with its own tree,
 *   its own surface, and none of the library's shape or type.
 * * **In a browser**, nothing happens at all: measured against the built site
 *   with `docs/measure-web.mjs --right-click`, the `contextmenu` event comes back
 *   `prevented` and no menu is drawn in its place, on a field and on prose alike.
 *
 * So this is not one defect with one cause, and `LocalContextMenuRepresentation`
 * — the seam that would replace the first — is declared in foundation's
 * **desktop** source set and does not exist on the platform the report came
 * from. The menu is the library's own instead, opened from a secondary press
 * caught on `PointerEventPass.Initial` so it is consumed before the field's own
 * detector can raise the platform one.
 *
 * ### Counted in roots, not in labels
 *
 * "A node saying Copy exists" is true either way — Compose's own menu says Copy
 * too, and the test API searches every root. What separates them is *where* it
 * is: the library's menu is an overlay inside the host, in the same tree as the
 * field, so the count of roots does not change. A second root is a second
 * window, and that is the thing being replaced.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("DEPRECATION")
class TextContextMenuTest {

    @Test
    fun aRightClickOpensTheLibrarysMenuRatherThanTheePlatformsWindow() {
        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        TextField(
                            state = rememberTextFieldState("Perth Station"),
                            modifier = Modifier.testTag(Tag),
                        )
                    }
                }
            }
            waitForIdle()

            onNodeWithTag(Tag).performMouseInput { rightClick() }
            waitForIdle()

            val roots = onAllNodes(isRoot()).fetchSemanticsNodes().size
            assertEquals(
                1,
                roots,
                "right-clicking a text box left $roots semantics roots on screen. " +
                    "Two means the platform opened its own context menu in its own " +
                    "window — a surface with none of this library's shape, type or " +
                    "colour, and nowhere to put an app's own actions.",
            )
            onNodeWithText(Paste).assertExists(
                "the right-click menu drew no \"$Paste\" — so either it did not " +
                    "open, or it opened without the verbs a context menu on a text " +
                    "box exists for",
            )
        }
    }

    /**
     * A paste goes through the field's filter, as typing the same characters
     * would.
     *
     * The menu edited the state directly, which is beneath the field's input
     * handling, so `12ab` pasted into a digits-only field went in whole — a
     * number field holding letters, which no keystroke could have produced.
     */
    @Test
    fun aPasteIntoANumberFieldIsFilteredLikeTyping() = runComposeUiTest {
        val clipboard = FakeClipboard("12ab")
        val state = TextFieldState("")
        setContent {
            Menued(clipboard) { NumberField(state = state, modifier = Modifier.testTag(Tag)) }
        }
        waitForIdle()

        onNodeWithTag(Tag).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithText(Paste).performClick()
        waitForIdle()
        assertEquals("", state.text.toString(), "a paste of \"12ab\" got past a digits-only field")

        // And what the filter does take still goes in.
        clipboard.held = "34"
        onNodeWithTag(Tag).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithText(Paste).performClick()
        waitForIdle()
        assertEquals("34", state.text.toString())
    }

    /**
     * A masked field offers neither Copy nor Cut, whatever is selected.
     *
     * The dots are there so the password is not seen, and Copy put it in plain
     * text on the clipboard, where any app can read it.
     */
    @Test
    fun aMaskedPasswordCannotBeCopiedOrCut() = runComposeUiTest {
        val clipboard = FakeClipboard("")
        val state = TextFieldState("hunter2")
        setContent {
            Menued(clipboard) { PasswordField(state = state, modifier = Modifier.testTag(Tag)) }
        }
        waitForIdle()
        state.edit { selection = TextRange(0, length) }
        waitForIdle()

        onNodeWithTag(Tag).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithText(Paste).assertExists("the menu did not open over the password field")
        onNodeWithText(Copy).assertDoesNotExist()
        onNodeWithText(Cut).assertDoesNotExist()
        assertEquals("", clipboard.held)
    }

    /**
     * The menu acts on the state the field holds now.
     *
     * A handler left holding the first `TextFieldState` would paste into a
     * text no longer on screen.
     */
    @Test
    fun theMenuActsOnTheStateTheFieldHoldsNow() = runComposeUiTest {
        val clipboard = FakeClipboard("Joondalup")
        val first = TextFieldState("")
        val second = TextFieldState("")
        var shown by mutableStateOf(first)
        setContent {
            Menued(clipboard) { TextField(state = shown, modifier = Modifier.testTag(Tag)) }
        }
        waitForIdle()
        shown = second
        waitForIdle()

        onNodeWithTag(Tag).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithText(Paste).performClick()
        waitForIdle()
        assertEquals("Joondalup", second.text.toString(), "the paste missed the field's current state")
        assertEquals("", first.text.toString(), "the paste went into the state the field held before")
    }

    @Composable
    private fun Menued(clipboard: ClipboardManager, field: @Composable () -> Unit) {
        KontourTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                OverlayHost(Modifier.fillMaxSize()) { field() }
            }
        }
    }

    /** A clipboard the test owns, so nothing reaches the machine's. */
    private class FakeClipboard(var held: String) : ClipboardManager {
        override fun setText(annotatedString: AnnotatedString) {
            held = annotatedString.text
        }

        override fun getText(): AnnotatedString = AnnotatedString(held)
    }

    private companion object {
        const val Tag = "field"

        /**
         * `Strings.paste`'s default.
         *
         * Paste rather than Copy, because it is the one verb that is offered
         * with **no selection** — a right-click that places the caret and offers
         * to paste is the whole gesture on a desktop, and asserting on Copy
         * would pass on a menu that only ever appears over a selection.
         */
        const val Paste = "Paste"

        /** `Strings.copy`'s and `Strings.cut`'s defaults. */
        const val Copy = "Copy"
        const val Cut = "Cut"
    }
}
