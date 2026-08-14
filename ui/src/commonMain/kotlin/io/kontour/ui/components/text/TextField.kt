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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
 * @param supportingText Guidance shown below the field. Replaced by
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
    supportingText: String? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    colors: TextFieldColors = TextFieldDefaults.colors(variant),
    metrics: TextFieldMetrics = TextFieldDefaults.metrics(),
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val motion = Theme.motion
    val shape = Theme.shapes.small
    val isError = errorMessage != null

    val borderColor by animateColorAsState(
        targetValue = colors.border(enabled, focused, isError),
        animationSpec = motion.tweenFast(),
        label = "textFieldBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused || isError) Theme.sizing.borderWidthStrong else Theme.sizing.borderWidth,
        animationSpec = motion.tweenFast(),
        label = "textFieldBorderWidth",
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.contentDisabled
            isError -> colors.error
            focused -> colors.labelFocused
            else -> colors.label
        },
        animationSpec = motion.tweenFast(),
        label = "textFieldLabel",
    )

    val selectionColors = remember(colors) {
        TextSelectionColors(
            handleColor = colors.cursor,
            backgroundColor = colors.selectionBackground,
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = Theme.typography.labelMedium,
                color = labelColor,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = metrics.minHeight)
                .clip(shape)
                .background(colors.container(enabled, focused), shape)
                .border(borderWidth, borderColor, shape)
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val contentColor = if (enabled) colors.content else colors.contentDisabled

            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (enabled) colors.label else colors.contentDisabled,
                    size = Theme.sizing.iconMedium,
                )
            }

            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                CompositionLocalProvider(
                    LocalTextSelectionColors provides selectionColors,
                    LocalContentColor provides contentColor,
                ) {
                    BasicTextField(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                if (errorMessage != null) semanticsError(errorMessage)
                            },
                        enabled = enabled,
                        readOnly = readOnly,
                        textStyle = Theme.typography.bodyMedium.merge(color = contentColor),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = keyboardType,
                            imeAction = imeAction,
                        ),
                        onKeyboardAction = onKeyboardAction,
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

            if (trailing != null) {
                trailing()
            } else if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = if (enabled) colors.label else colors.contentDisabled,
                    size = Theme.sizing.iconMedium,
                )
            }
        }

        // Helper and error occupy the same slot and animate in place, so the
        // form does not jump by a line height every time validation flips.
        AnimatedVisibility(
            visible = errorMessage != null || supportingText != null,
            enter = fadeIn(motion.tweenFast()) + expandVertically(motion.tweenFast()),
            exit = fadeOut(motion.tweenFast()) + shrinkVertically(motion.tweenFast()),
        ) {
            Text(
                text = errorMessage ?: supportingText.orEmpty(),
                style = Theme.typography.bodySmall,
                color = if (isError) colors.error else colors.helper,
            )
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
    supportingText: String? = null,
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
        supportingText = supportingText,
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
