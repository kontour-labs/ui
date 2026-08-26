package io.kontour.ui.components.text

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
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
