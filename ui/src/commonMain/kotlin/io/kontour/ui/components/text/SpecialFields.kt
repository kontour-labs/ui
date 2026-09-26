package io.kontour.ui.components.text

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.then
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.RevealToggleButton
import io.kontour.ui.theme.Theme
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * A password field, with a reveal toggle.
 *
 * ```kotlin
 * PasswordField(
 *     state = password,
 *     label = "Password",
 *     revealIcon = FontAwesome.Solid.Eye,
 * )
 * ```
 *
 * The reveal toggle is not a nicety — password fields with no way to check what
 * was typed are a major cause of failed sign-ins on phone keyboards. It
 * announces which state it will move *to*, so a screen-reader user knows what
 * pressing it does rather than what it currently is — and it is a toggle rather
 * than a button, so assistive tech also reports which state that currently is.
 *
 * **Pass [revealIcon] alone and the slash draws itself across it** while the
 * password is hidden, and leaves as it is revealed: the eye says what is true
 * now. A second, already-slashed glyph is still accepted as [hideIcon], and the
 * two cross-fade; but the line moving is the thing that reads as the password
 * being covered and uncovered, and it is one icon to supply rather than two.
 *
 * Sets the autofill content type so the platform offers a saved password, and
 * so a password manager can save a new one.
 *
 * @param revealIcon The eye: shown plain while the password can be read, and
 *   struck through while it is hidden. No toggle at all without one.
 * @param hideIcon An already-slashed eye to show while the password is hidden,
 *   instead of drawing the slash across [revealIcon].
 * @param newPassword Set for a sign-up or change-password field. Changes the
 *   autofill hint from "fill an existing password" to "generate and save a new
 *   one", which is what makes password managers offer to create a strong one.
 * @param revealLastTyped Shows the character just typed for a moment before it
 *   is masked, the way a phone keyboard does. On by default; turn it off where
 *   the screen is likely to be watched.
 */
@Composable
fun PasswordField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String? = null,
    supporting: String? = null,
    errorMessage: String? = null,
    revealIcon: ImageVector? = null,
    hideIcon: ImageVector? = null,
    revealLabel: String = Theme.strings.showPassword,
    hideLabel: String = Theme.strings.hidePassword,
    newPassword: Boolean = false,
    revealLastTyped: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    imeChain: ImeChainStep? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    var revealed by remember { mutableStateOf(false) }

    // **The character just typed, shown for a moment.** Asked for as "I'd like to
    // be able to see the most recently-typed character for a short period of time"
    // — the phone keyboard's own habit, and the only confirmation a reader has
    // that the key they meant is the key they hit.
    //
    // Found by comparing the text with what it was, not by an input filter: a
    // single character inserted anywhere is typing, and anything else — a paste,
    // a deletion, the caller setting the text — shows nothing. Cleared after
    // [RevealLastTypedFor], on the next edit, and whenever the toggle is used.
    val lastTypedState = remember { mutableIntStateOf(NothingTyped) }
    var lastTyped by lastTypedState
    if (revealLastTyped) {
        LaunchedEffect(state) {
            var before = state.text.toString()
            snapshotFlow { state.text.toString() }.collect { now ->
                lastTyped = insertedAt(before, now)
                before = now
            }
        }
        LaunchedEffect(lastTyped) {
            if (lastTyped == NothingTyped) return@LaunchedEffect
            delay(RevealLastTypedFor)
            lastTyped = NothingTyped
        }
    }
    LaunchedEffect(revealed) { lastTyped = NothingTyped }
    // **One mask for the life of the field**, reading the revealed index from
    // state as it runs. It used to be remembered *keyed* on that index, so every
    // keystroke handed the field a new `OutputTransformation` — and a new one is a
    // new transformed state inside `BasicTextField`, which restarts the platform's
    // input session. On Android that is the keyboard going away and coming back on
    // every key. The field already re-runs its output transformation when state
    // read inside it changes, so the key was never needed.
    val mask = remember { PasswordMask(unmasked = { lastTypedState.intValue }) }

    TextField(
        state = state,
        modifier = modifier.semantics {
            contentType = if (newPassword) ContentType.NewPassword else ContentType.Password
        },
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        supporting = supporting,
        errorMessage = errorMessage,
        variant = variant,
        keyboardType = if (revealed) KeyboardType.Text else KeyboardType.Password,
        // The thing that actually hides the password.
        //
        // `KeyboardType.Password` is an IME hint and substitutes no glyphs, so
        // for as long as this was the only thing `revealed` touched, the field
        // rendered in plaintext in both states and the toggle changed the icon
        // and nothing else.
        outputTransformation = if (revealed) null else mask,
        imeAction = imeAction,
        imeChain = imeChain,
        interactionSource = interactionSource,
        trailing = if (revealIcon != null) {
            {
                // **The eye says what is true now**: struck through while the
                // password is hidden, plain while it can be read. It was the other
                // way round — the slash appeared on reveal, describing what a press
                // would do — and was reported as reversed: "when the strikethrough
                // is visible, password should be hidden."
                //
                // The label still names the action, which is what a screen reader
                // user needs from a button; the checkbox state carries the rest.
                RevealToggleButton(
                    revealed = revealed,
                    onRevealedChange = { revealed = it },
                    icon = revealIcon,
                    hiddenIcon = hideIcon,
                    contentDescription = if (revealed) hideLabel else revealLabel,
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
 * Where [now] has one more character than [before] and is otherwise the same,
 * the index of that character; [NothingTyped] for any other change.
 */
internal fun insertedAt(before: String, now: String): Int {
    if (now.length != before.length + 1) return NothingTyped
    var index = 0
    while (index < before.length && before[index] == now[index]) index++
    // Everything after the new character must be what followed it before.
    for (rest in index until before.length) {
        if (before[rest] != now[rest + 1]) return NothingTyped
    }
    return index
}

/**
 * Replaces every character with a bullet, for display only — except [unmasked],
 * the character just typed, while it is being shown.
 *
 * An `OutputTransformation` rather than `BasicSecureTextField` because this field
 * is a [TextField] — label, frame, supporting line and all — and a secure field
 * is a different basic field with a scaffold of its own to rebuild.
 *
 * **One character at a time.** It used to be one replacement of the whole text,
 * and a replaced range maps every offset *inside* it back to the whole of what it
 * replaced — so the one-character deletion a hardware backspace makes on the
 * displayed text deleted the whole password, reported from the catalog. Replaced
 * one by one, every range is a single character with no inside, and every cursor
 * offset and selection maps to itself.
 */
private class PasswordMask(
    /** Read as the mask runs, so a change re-renders the text without a new mask. */
    private val unmasked: () -> Int = { NothingTyped },
    private val bullet: String = "\u2022",
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val shown = unmasked()
        for (index in 0 until length) {
            if (index != shown) replace(index, index + 1, bullet)
        }
    }
}

/** How long the character just typed stays readable. The platforms' own figure. */
private val RevealLastTypedFor = 1500.milliseconds

/** No character is being shown. */
private const val NothingTyped = -1

/**
 * A numeric field.
 *
 * Rejects non-numeric keystrokes as they arrive rather than validating
 * afterwards, so the field never shows an invalid value and never has to show
 * an error for one.
 *
 * @param allowDecimal Permits a single decimal point. Intermediate states like
 *   `1.` are allowed through — a user typing `1.5` passes through one.
 * @param allowNegative Permits a leading minus, with or without [allowDecimal].
 *
 * Turning either off — or lowering [maxLength] — while the field holds something
 * the new rules would not let in clears it, rather than leaving a text no edit
 * can get out of.
 */
@Composable
fun NumberField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String? = null,
    supporting: String? = null,
    errorMessage: String? = null,
    allowDecimal: Boolean = false,
    allowNegative: Boolean = false,
    maxLength: Int? = null,
    leadingIcon: ImageVector? = null,
    imeAction: ImeAction = ImeAction.Done,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    imeChain: ImeChainStep? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val transformation = remember(allowDecimal, allowNegative, maxLength) {
        val base = when {
            allowDecimal -> InputTransformation.decimal(allowNegative)
            // It used to be `digitsOnly()` whenever decimals were off, so
            // `allowNegative` on its own did nothing at all.
            allowNegative -> InputTransformation.integer(allowNegative = true)
            else -> InputTransformation.digitsOnly()
        }
        if (maxLength != null) base.then(InputTransformation.limit(maxLength)) else base
    }

    // **Rules that narrow under a text they no longer admit clear it.**
    //
    // The filters judge the whole text an edit would leave, never the edit — so a
    // field holding `-1.5` when decimals and negatives are turned off rejected
    // every edit there was, a backspace included, since each still left a minus or
    // a point behind. Reported from the catalog as the field refusing to be
    // edited at all. A text the rules no longer admit is not a value, and an
    // empty field is one the reader can start again in.
    LaunchedEffect(allowDecimal, allowNegative, maxLength) {
        if (!admitsNumber(state.text, allowDecimal, allowNegative, maxLength)) state.clearText()
    }

    TextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        supporting = supporting,
        errorMessage = errorMessage,
        leadingIcon = leadingIcon,
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
    supporting: String? = null,
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
        supporting = supporting,
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
    supporting: String? = null,
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
        supporting = supporting,
        errorMessage = errorMessage,
        variant = variant,
        keyboardType = KeyboardType.Email,
        imeAction = imeAction,
        imeChain = imeChain,
        interactionSource = interactionSource,
    )
}
