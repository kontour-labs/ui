package io.kontour.ui.components.text

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.testTag
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reveal toggle changes what is on the screen.
 *
 * It used to spend `revealed` on the keyboard type, the icon and the announced
 * label — and on nothing that draws. `KeyboardType.Password` is a hint to the
 * soft keyboard and substitutes no glyphs, so the field rendered in plaintext in
 * both states and pressing the toggle appeared to do nothing.
 *
 * Asserted through the semantics text rather than a golden, because the golden
 * could not have caught it either: `text-light.png` showed `hunter2` in the clear
 * for as long as this was broken, and nobody reading it knew that was wrong.
 */
@OptIn(ExperimentalTestApi::class)
class PasswordFieldTest {

    @Test
    fun theFieldMasksUntilItIsRevealed() {
        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    PasswordField(
                        state = rememberTextFieldState(Secret),
                        modifier = Modifier.testTag(Tag),
                        label = "Password",
                        revealIcon = Tabler.Outline.Eye,
                        hideIcon = Tabler.Outline.EyeOff,
                    )
                }
            }
            waitForIdle()

            val masked = shownText()
            assertTrue(
                Secret !in masked,
                "the password is on screen before anything was revealed: \"$masked\"",
            )
            assertEquals(
                Secret.length,
                masked.length,
                "the mask is not one character per character, so every cursor " +
                    "offset in the field means something different from what it says",
            )

            onNodeWithContentDescription("Show password").performClick()
            waitForIdle()

            assertTrue(
                Secret in shownText(),
                "pressing reveal did not show the password — `revealed` is wired " +
                    "to something that does not draw",
            )
        }
    }

    /**
     * Backspace takes one character, not the whole password.
     *
     * Reported from the catalog. The mask replaced the whole text in one edit, and a
     * replaced range maps every offset *inside* it back to the whole of the source —
     * so the one-character deletion a hardware backspace makes on the displayed text
     * became a deletion of everything.
     */
    @Test
    fun backspaceDeletesOneCharacter() {
        runComposeUiTest {
            val state = TextFieldState(Secret)
            setContent {
                KontourTheme(reduceMotion = true) {
                    PasswordField(state = state, label = "Password", revealLastTyped = false)
                }
            }
            onNode(hasSetTextAction()).requestFocus()
            onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Backspace) }
            waitForIdle()
            assertEquals(Secret.dropLast(1), state.text.toString())
        }
    }

    /**
     * The character just typed is shown for a moment, and then masked like the rest.
     *
     * Asked for: "I'd like to be able to see the most recently-typed character for a
     * short period of time in that field" — the phone keyboard's own habit, and the
     * only feedback a reader has that the key they meant is the key they hit.
     */
    @Test
    fun theLastTypedCharacterShowsBrieflyThenMasks() {
        runComposeUiTest {
            val state = TextFieldState(Secret)
            setContent {
                KontourTheme(reduceMotion = true) {
                    PasswordField(
                        state = state,
                        modifier = Modifier.testTag(Tag),
                        label = "Password",
                    )
                }
            }
            onNode(hasSetTextAction()).requestFocus()
            mainClock.autoAdvance = false
            onNode(hasSetTextAction()).performTextInput("x")
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()

            val typing = shownText()
            assertEquals(Secret.length + 1, typing.length)
            assertTrue(
                typing.endsWith("x") && Secret.none { it in typing },
                "just after typing, the field should show the new character and mask " +
                    "the rest; it showed \"$typing\"",
            )

            mainClock.advanceTimeBy(3_000)
            val later = shownText()
            assertTrue(
                'x' !in later,
                "the typed character was still showing three seconds later: \"$later\"",
            )
        }
    }

    /**
     * Typing does not restart the keyboard.
     *
     * Reported from Android: *"whenever you type something into the password field,
     * the keyboard jumps away then reappears"*. The last-typed reveal handed the field
     * a new output transformation on every keystroke, and a new transformation is a
     * new input session — so the keyboard was asked for again, each time. Counted
     * here as the platform input requests the field makes after it has focus.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun typingDoesNotAskForTheKeyboardAgain() {
        runComposeUiTest {
            val state = TextFieldState(Secret)
            var requests = 0
            setContent {
                KontourTheme(reduceMotion = true) {
                    InterceptPlatformTextInput(
                        interceptor = { request, next ->
                            requests++
                            next.startInputMethod(request)
                        },
                    ) {
                        PasswordField(state = state, modifier = Modifier.testTag(Tag), label = "Password")
                    }
                }
            }
            onNode(hasSetTextAction()).requestFocus()
            waitForIdle()
            val onFocus = requests
            repeat(4) { onNode(hasSetTextAction()).performTextInput("x") }
            waitForIdle()
            assertEquals(
                onFocus, requests,
                "typing four characters asked for the keyboard ${requests - onFocus} more " +
                    "times after focus ($onFocus) — each one is the keyboard hiding and coming back",
            )
        }
    }

    /**
     * The field's own text, from wherever in the subtree it lives.
     *
     * `testTag` lands on the scaffold — the label, the frame and the helper line
     * are all under it — and `EditableText` is set by the `BasicTextField` node
     * several levels down, so the tagged node itself carries nothing.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.shownText(): String {
        fun find(node: SemanticsNode): String? =
            node.config.getOrNull(SemanticsProperties.EditableText)?.text
                ?: node.children.firstNotNullOfOrNull { find(it) }
        return find(onNodeWithTag(Tag, useUnmergedTree = true).fetchSemanticsNode())
            ?: error("no editable text anywhere under the field")
    }

    private companion object {
        const val Tag = "password"
        const val Secret = "hunter2"
    }
}
