package io.kontour.ui.components.text

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.autofill.ContentDataType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A secret that is not a password is not handed to the password manager.
 *
 * Reported: *"`PasswordField` marks everything as a password, so a browser may
 * offer to save an API key."* `PasswordField` says `ContentType.Password` to
 * the platform's autofill, which is right for a password and is how a key came
 * to be filed as the reader's login. `SecretField` masks the same way and says
 * nothing of the kind.
 *
 * Read from the node the text field's own semantics are on, where an autofill
 * service reads them.
 */
@OptIn(ExperimentalTestApi::class)
class SecretFieldTest {

    @Test
    fun aSecretFieldIsNotAPasswordToAutofill() = runComposeUiTest {
        setContent {
            KontourTheme {
                SecretField(state = TextFieldState("kt_live_51Hx"), label = "API key", revealIcon = SystemIcons.ChevronDown)
            }
        }
        val field = onNode(hasSetTextAction()).fetchSemanticsNode().config
        assertNull(field.getOrNull(SemanticsProperties.ContentType), "a key should carry no autofill content type")
        assertEquals(
            ContentDataType.None,
            field.getOrNull(SemanticsProperties.ContentDataType),
            "and should say it holds nothing to fill, which keeps an autofill service off it",
        )
    }

    /** Masked as a password is, until the toggle says otherwise. */
    @Test
    fun itIsMaskedAndRevealsOnTheToggle() = runComposeUiTest {
        setContent {
            KontourTheme {
                SecretField(
                    state = TextFieldState("kt_live_51Hx"),
                    label = "API key",
                    revealIcon = SystemIcons.ChevronDown,
                    revealLastTyped = false,
                )
            }
        }
        fun shown() = onNode(hasSetTextAction()).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        val hidden = shown()
        assertTrue(hidden.none { it.isLetterOrDigit() }, "the key should be masked; it showed '$hidden'")
        onNodeWithContentDescription("Show").performClick()
        waitForIdle()
        assertEquals("kt_live_51Hx", shown())
    }

    /** A password field is still one: the change is a new field, not a weaker old one. */
    @Test
    fun aPasswordFieldIsStillAPassword() = runComposeUiTest {
        setContent { KontourTheme { PasswordField(state = TextFieldState("hunter2"), label = "Password") } }
        val field = onNode(hasSetTextAction()).fetchSemanticsNode().config
        assertEquals(ContentType.Password, field.getOrNull(SemanticsProperties.ContentType))
    }
}
