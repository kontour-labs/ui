package io.kontour.ui.components.text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.error as semanticsError
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColor
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * A single-line text field.
 *
 * Built on foundation's state-based `BasicTextField`, so the caller owns a
 * [TextFieldState] rather than a `String` plus a callback:
 *
 * ```kotlin
 * val query = rememberTextFieldState()
 *
 * TextField(
 *     state = query,
 *     label = "Where to?",
 *     placeholder = "Station, stop or address",
 * )
 * ```
 *
 * `TextFieldState` is the right default because it makes the two classic bugs
 * unrepresentable: the caret jumping to the end when text is edited
 * programmatically, and characters dropping under fast typing because state
 * hoisting round-tripped through a recomposition.
 *
 * ### Validation
 *
 * Pass [errorMessage] to mark the field invalid. It sets `error` semantics —
 * so a screen reader announces the problem rather than leaving the user to
 * discover it — and colours the border, which is *not* sufficient on its own:
 * colour alone would fail WCAG 1.4.1.
 *
 * Error state outranks focus. A focused invalid field keeps its error border,
 * because an accent ring would hide the thing the user needs to fix.
 *
 * @param inputTransformation Filters keystrokes as they arrive — max length,
 *   digits only. Rejected input never reaches the state, so the field cannot
 *   flicker through an invalid value.
 * @param outputTransformation Formats what is *displayed* without changing what
 *   is stored — a phone mask, a card-number grouping. The caller still reads
 *   clean digits out of [state].
 * @param supporting Guidance shown below the field. Replaced by
 *   [errorMessage] when the field is invalid, so the two never stack.
 */
@Composable
fun TextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    supporting: String? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    shape: Shape = Theme.shapes.small,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    /**
     * This field's place in a [rememberImeChain], which sets the keyboard's
     * action key to Next or Done and wires it to move focus or submit. Overrides
     * [imeAction]; [onKeyboardAction], if given, still wins over the chain's
     * handler.
     */
    imeChain: ImeChainStep? = null,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    colors: TextFieldColors = TextFieldDefaults.colors(variant),
    metrics: TextFieldMetrics = TextFieldDefaults.metrics(),
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()

    val selectionColors = remember(colors) {
        TextSelectionColors(
            handleColor = colors.cursor,
            backgroundColor = colors.selectionBackground,
        )
    }

    FieldScaffold(
        modifier = modifier,
        enabled = enabled,
        focused = focused,
        colors = colors,
        metrics = metrics,
        shape = shape,
        label = label,
        supporting = supporting,
        errorMessage = errorMessage,
        leadingIcon = leadingIcon,
        leading = leading,
        trailing = trailing ?: trailingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (enabled) colors.label else colors.contentDisabled,
                    size = Theme.sizing.iconMedium,
                )
            }
        },
    ) {
        val contentColor = if (enabled) colors.content else colors.contentDisabled

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            CompositionLocalProvider(
                LocalTextSelectionColors provides selectionColors,
                LocalContentColor provides contentColor,
            ) {
                BasicTextField(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            // The requester belongs on the input itself, not on
                            // the frame — focusing the frame would put the
                            // caret nowhere.
                            if (imeChain != null) {
                                Modifier.focusRequester(imeChain.requester)
                            } else {
                                Modifier
                            }
                        )
                        .semantics {
                            // The visible [label] is a sibling `Text` in the
                            // scaffold, one node up and to the side, so nothing
                            // associates the two — Compose has no `labelledBy`.
                            // Without this the field announces as an unnamed edit
                            // box: the user hears "Origin", moves to the next
                            // node, and is then in a text field with no idea what
                            // it is for.
                            if (label != null) contentDescription = label
                            if (errorMessage != null) semanticsError(errorMessage)
                            // Foundation greys the field out and stops accepting
                            // input, but does not mark the node disabled — so a
                            // screen reader still offers "double tap to edit" on
                            // a field that will not take a character.
                            if (!enabled) disabled()
                        },
                    enabled = enabled,
                    readOnly = readOnly,
                    textStyle = Theme.typography.bodyMedium.merge(color = contentColor),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = keyboardType,
                        // A chain decides the action key outright, overriding
                        // both this parameter and a specialised field's own
                        // default — `PasswordField` defaults to Done, which is
                        // wrong for a password halfway down a form. One rule
                        // beats a precedence table nobody can predict.
                        imeAction = imeChain?.imeAction ?: imeAction,
                    ),
                    onKeyboardAction = onKeyboardAction ?: imeChain?.handler,
                    lineLimits = lineLimits,
                    inputTransformation = inputTransformation,
                    outputTransformation = outputTransformation,
                    cursorBrush = SolidColor(colors.cursor),
                    interactionSource = interactions,
                )
            }

            if (placeholder != null && state.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = Theme.typography.bodyMedium,
                    color = colors.placeholder,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A multi-line text field that grows with its content.
 *
 * ```kotlin
 * TextArea(state = feedback, label = "What went wrong?", minLines = 3, maxLines = 8)
 * ```
 *
 * Growing between [minLines] and [maxLines] and then scrolling internally beats
 * a fixed height in both directions: a short answer does not sit in a mostly
 * empty box, and a long one does not push the submit button off screen.
 */
@Composable
fun TextArea(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    supporting: String? = null,
    errorMessage: String? = null,
    minLines: Int = 3,
    maxLines: Int = 8,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    inputTransformation: InputTransformation? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    TextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        supporting = supporting,
        errorMessage = errorMessage,
        variant = variant,
        imeAction = ImeAction.Default,
        inputTransformation = inputTransformation,
        lineLimits = TextFieldLineLimits.MultiLine(
            minHeightInLines = minLines,
            maxHeightInLines = maxLines,
        ),
        interactionSource = interactionSource,
    )
}
