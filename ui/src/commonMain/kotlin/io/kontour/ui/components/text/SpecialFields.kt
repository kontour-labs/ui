package io.kontour.ui.components.text

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.input.then
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton

/**
 * A password field, with a reveal toggle.
 *
 * ```kotlin
 * PasswordField(
 *     state = password,
 *     label = "Password",
 *     revealIcon = FontAwesome.Solid.Eye,
 *     hideIcon = FontAwesome.Solid.EyeSlash,
 * )
 * ```
 *
 * The reveal toggle is not a nicety — password fields with no way to check what
 * was typed are a major cause of failed sign-ins on phone keyboards. It
 * announces which state it will move *to*, so a screen-reader user knows what
 * pressing it does rather than what it currently is.
 *
 * Sets the autofill content type so the platform offers a saved password, and
 * so a password manager can save a new one.
 *
 * @param isNewPassword Set for a sign-up or change-password field. Changes the
 *   autofill hint from "fill an existing password" to "generate and save a new
 *   one", which is what makes password managers offer to create a strong one.
 */
@Composable
fun PasswordField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    errorMessage: String? = null,
    revealIcon: ImageVector? = null,
    hideIcon: ImageVector? = null,
    revealLabel: String = "Show password",
    hideLabel: String = "Hide password",
    isNewPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    imeChain: ImeChainStep? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    var revealed by remember { mutableStateOf(false) }

    TextField(
        state = state,
        modifier = modifier.semantics {
            contentType = if (isNewPassword) ContentType.NewPassword else ContentType.Password
        },
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        errorMessage = errorMessage,
        variant = variant,
        keyboardType = if (revealed) KeyboardType.Text else KeyboardType.Password,
        imeAction = imeAction,
        imeChain = imeChain,
        interactionSource = interactionSource,
        trailing = if (revealIcon != null && hideIcon != null) {
            {
                IconButton(
                    icon = if (revealed) hideIcon else revealIcon,
                    // Describes the action, not the state: pressing it hides.
                    contentDescription = if (revealed) hideLabel else revealLabel,
                    onClick = { revealed = !revealed },
                    enabled = enabled,
                    size = ButtonSize.XSmall,
                )
            }
        } else {
            null
        },
    )
}

/**
 * A numeric field.
 *
 * Rejects non-numeric keystrokes as they arrive rather than validating
 * afterwards, so the field never shows an invalid value and never has to show
 * an error for one.
 *
 * @param allowDecimal Permits a single decimal point. Intermediate states like
 *   `1.` are allowed through — a user typing `1.5` passes through one.
 * @param allowNegative Permits a leading minus.
 */
@Composable
fun NumberField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    errorMessage: String? = null,
    allowDecimal: Boolean = false,
    allowNegative: Boolean = false,
    maxLength: Int? = null,
    prefix: ImageVector? = null,
    imeAction: ImeAction = ImeAction.Done,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    imeChain: ImeChainStep? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val transformation = remember(allowDecimal, allowNegative, maxLength) {
        val base = if (allowDecimal) {
            InputTransformation.decimal(allowNegative)
        } else {
            InputTransformation.digitsOnly()
        }
        if (maxLength != null) base.then(InputTransformation.limit(maxLength)) else base
    }

    TextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        errorMessage = errorMessage,
        leadingIcon = prefix,
        variant = variant,
        keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
        imeAction = imeAction,
        inputTransformation = transformation,
        imeChain = imeChain,
        interactionSource = interactionSource,
    )
}

/**
 * A phone-number field, masked as it is typed.
 *
 * The mask is display-only: the caller reads clean digits out of [state] and
 * never has to strip formatting back out.
 */
@Composable
fun PhoneField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    errorMessage: String? = null,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    imeChain: ImeChainStep? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    TextField(
        state = state,
        modifier = modifier.semantics { contentType = ContentType.PhoneNumber },
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        errorMessage = errorMessage,
        variant = variant,
        keyboardType = KeyboardType.Phone,
        inputTransformation = remember {
            InputTransformation.digitsOnly().then(InputTransformation.limit(10))
        },
        outputTransformation = remember { phoneMask() },
        imeChain = imeChain,
        interactionSource = interactionSource,
    )
}

/**
 * An email field.
 *
 * Exists mostly to get the three platform details right in one place: the email
 * keyboard layout, the autofill content type, and *no* capitalisation — which
 * is the single most common cause of a rejected sign-in on a phone.
 */
@Composable
fun EmailField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    errorMessage: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    imeChain: ImeChainStep? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    TextField(
        state = state,
        modifier = modifier.semantics { contentType = ContentType.EmailAddress },
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        errorMessage = errorMessage,
        variant = variant,
        keyboardType = KeyboardType.Email,
        imeAction = imeAction,
        imeChain = imeChain,
        interactionSource = interactionSource,
    )
}
