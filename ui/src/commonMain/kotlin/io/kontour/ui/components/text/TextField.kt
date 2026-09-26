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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error as semanticsError
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.pointerCursor
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
 * Pass [errorMessage] to mark the field invalid; it sets `error` semantics as
 * well as colouring the border, because colour alone would fail WCAG 1.4.1.
 * Error outranks focus — see
 * `ui-docs/content/components/text-editing.md`.
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
    shape: Shape = Theme.shapes.field,
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
    colours: TextFieldColours = TextFieldDefaults.colours(variant),
    metrics: TextFieldMetrics = TextFieldDefaults.metrics(),
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()

    // The requester the frame hands a press to.
    //
    // A chain already owns one — `rememberImeChain` needs it to move focus down
    // a form — and taking it when it is there keeps one requester on one node.
    // Without a chain there was none at all, which is why the frame had nothing
    // to focus and the field's padding did nothing.
    val ownRequester = remember { FocusRequester() }
    val requester = imeChain?.requester ?: ownRequester

    val selectionColours = remember(colours) {
        TextSelectionColors(
            handleColor = colours.cursor,
            backgroundColor = colours.selectionBackground,
        )
    }

    FieldScaffold(
        modifier = modifier,
        enabled = enabled,
        focused = focused,
        readOnly = readOnly,
        colours = colours,
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
                    tint = if (enabled) colours.label else colours.contentDisabled,
                    size = Theme.sizing.iconMedium,
                )
            }
        },
        // The one control in the library where the exactly right cursor is
        // available: **editable** text is what `PointerIcon.Text` is for.
        //
        // On the frame rather than on the input, because the whole field takes
        // the caret when it is clicked — a beam over the middle of a field and
        // an arrow over its padding would say the two halves do different
        // things. The trailing slot's own controls set a hand over themselves,
        // which is right: a reveal toggle is a button sitting in a text field.
        //
        // Not on a read-only one. The text there can still be selected, so the
        // beam is not *entirely* a lie, but what a caret promises is that typing
        // will go in at the point you click — and it will not. Same reasoning as
        // the focus tint this field also stopped showing.
        frameModifier = Modifier
            .pointerCursor(
                Cursor.Text,
                enabled = enabled && !readOnly,
            )
            // On the frame, not the input, and that placement is the mechanism:
            // the Initial pointer pass runs parent to child, so a secondary
            // press is consumed here before the text input's own detector can
            // raise the platform's context menu. See `textContextMenu`.
            .textContextMenu(
                state = state,
                editable = enabled && !readOnly,
                enabled = enabled,
            ),
        // A press anywhere on the frame puts the caret in the field.
        //
        // Not gated on `readOnly`: a read-only field is still focusable and its
        // text is still selectable, which is the whole reason foundation has
        // that flag rather than `enabled`. It is gated on `enabled`, because a
        // disabled field should not take focus from wherever it currently is.
        onFrameTap = if (enabled) {
            { requester.requestFocus() }
        } else {
            null
        },
    ) {
        val contentColour = if (enabled) colours.content else colours.contentDisabled

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            CompositionLocalProvider(
                LocalTextSelectionColors provides selectionColours,
                LocalContentColour provides contentColour,
            ) {
                BasicTextField(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        // The requester belongs on the input itself, not on the
                        // frame — focusing the frame would put the caret
                        // nowhere. Always present now rather than only with a
                        // chain, because the frame needs something to aim a
                        // press at either way.
                        .focusRequester(requester)
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
                    textStyle = Theme.typography.bodyMedium.merge(color = contentColour),
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
                    cursorBrush = SolidColor(colours.cursor),
                    interactionSource = interactions,
                )
            }

            if (placeholder != null && state.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = Theme.typography.bodyMedium,
                    colour = colours.placeholder,
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
 * TextArea(
 *     state = feedback,
 *     label = "What went wrong?",
 *     lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8),
 * )
 * ```
 *
 * Growing between the [lineLimits] and then scrolling internally beats a fixed
 * height in both directions: a short answer does not sit in a mostly empty box,
 * and a long one does not push the submit button off screen.
 *
 * @param lineLimits The same `TextFieldLineLimits` a [TextField] takes, held to
 *   the multi-line kind: three lines to start with, growing to eight.
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
    lineLimits: TextFieldLineLimits.MultiLine = TextAreaDefaults.LineLimits,
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
        lineLimits = lineLimits,
        interactionSource = interactionSource,
    )
}

/** What a [TextArea] takes by default. */
object TextAreaDefaults {
    /** Three lines to start with, growing to eight before it scrolls. */
    val LineLimits: TextFieldLineLimits.MultiLine =
        TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8)
}
