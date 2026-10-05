package io.kontour.ui.components.text

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The autofill hint a specialised field gives is on the input, where an autofill
 * service reads it — not on the frame round it.
 *
 * Each field put its hint on the caller's modifier, and the caller's modifier is
 * the frame's: a node with no text in it. The input said `Password`, a phone
 * number and an address anyway, because foundation derives those from the
 * keyboard asked for; nothing derives `NewPassword`, so `PasswordField(newPassword
 * = true)` told the platform it was a password to fill rather than one to make.
 * Phone and email are here as guards: they now say it themselves.
 */
@OptIn(ExperimentalTestApi::class)
class AutofillHintTest {

    private fun ComposeUiTestHint(content: @androidx.compose.runtime.Composable () -> Unit, expected: ContentType) = runComposeUiTest {
        setContent { KontourTheme { content() } }
        val input = onNode(hasSetTextAction()).fetchSemanticsNode().config
        assertEquals(expected, input.getOrNull(SemanticsProperties.ContentType))
    }

    @Test
    fun aNewPasswordIsOneToTheInput() =
        ComposeUiTestHint({ PasswordField(TextFieldState(), label = "New password", newPassword = true) }, ContentType.NewPassword)

    @Test
    fun aPhoneNumberIsOneToTheInput() =
        ComposeUiTestHint({ PhoneField(TextFieldState(), label = "Mobile") }, ContentType.PhoneNumber)

    @Test
    fun anEmailAddressIsOneToTheInput() =
        ComposeUiTestHint({ EmailField(TextFieldState(), label = "Email") }, ContentType.EmailAddress)
}
