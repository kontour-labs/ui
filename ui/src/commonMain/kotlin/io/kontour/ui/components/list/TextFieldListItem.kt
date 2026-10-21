package io.kontour.ui.components.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.error as semanticsError
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.optionalBorder
import io.kontour.ui.components.text.ImeChainStep
import io.kontour.ui.components.text.TextFieldDefaults
import io.kontour.ui.components.text.TextFieldImpl
import io.kontour.ui.components.text.TextFieldMetrics
import io.kontour.ui.components.text.TextFieldVariant
import io.kontour.ui.components.text.frameTap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import io.kontour.ui.components.text.FrameSpot
import io.kontour.ui.components.text.CaretAim
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.shape
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.platform.opensKeyboard
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.clipProxy

/**
 * A list row you type into: a [ListItem]'s ground, corners and place in a group,
 * holding a one-line text field where its label's words would go.
 *
 * ```kotlin
 * val name = rememberTextFieldState("Perth Underground")
 * val note = rememberTextFieldState()
 *
 * ListGroup {
 *     field(name, label = "Name")
 *     field(note, placeholder = "Add a note")
 * }
 * ```
 *
 * **With a [label], the label is at the start and the value at the end**, as a
 * settings row's value is: "Name" on the left, "Perth Underground" on the right.
 * **Without one, the field starts where a row's words start**, and its
 * [placeholder] says what goes in it — the row is the field.
 *
 * For a form that lives in a list — a contact's details, a stop's name and
 * note, a profile — where a [TextField][io.kontour.ui.components.text.TextField]
 * would stand out of the rows round it as a box of its own. A field on its own
 * on a page is still a `TextField`: a row's ground does not say "type here" the
 * way a field's frame does, and inside a group of rows is where it does not
 * need to.
 *
 * **A press anywhere on the row puts the caret in the field**, the label and the
 * padding included, as a press on a field's frame does. Focused, the row takes
 * the field's focused edge round it; in error, the error edge, and the message
 * under it in the error colour in place of [supporting].
 *
 * **Its place in a group** is [position], as a [ListItem]'s is: [ListGroup]'s
 * `field` passes it for you, and so does `listGroup`. In [SwipeActions] and a
 * [ReorderableItem] it is a row like any other.
 *
 * @param state What is typed.
 * @param readOnly Selectable, focusable, and not editable.
 * @param label What the value is, at the start of the row. It names the field
 *   for a screen reader.
 * @param placeholder Shown while the field is empty. Without a [label] it names
 *   the field too.
 * @param supporting A line under the row: what the value is for, or a limit.
 * @param errorMessage In place of [supporting] while set, in the error colour,
 *   and said as the field's error.
 * @param leadingIcon A glyph at the row's start, as a [ListItem]'s leading icon.
 * @param position Where the row sits in a group: see [GroupPosition].
 * @param imeChain A step in a [rememberImeChain][io.kontour.ui.components.text.rememberImeChain],
 *   so the keyboard's action key walks down the rows.
 * @param trailing After the field: a unit ("kg"), a clear button, a reveal toggle.
 */
@Composable
fun TextFieldListItem(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    supporting: String? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    position: GroupPosition = GroupPosition.Only,
    shape: Shape = position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical),
    containerColour: Color = Theme.colours.surfaceSunken,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    imeChain: ImeChainStep? = null,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    interactionSource: MutableInteractionSource? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colours = Theme.colours
    // The field family's own edges and error colour, so a row in error or in
    // focus says so in the same colours a text field does.
    val field = TextFieldDefaults.colours(TextFieldVariant.Outlined)
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    // One requester for the row and its field: a chain's when there is one,
    // which already sits on the input.
    val ownRequester = remember { FocusRequester() }
    val requester = imeChain?.requester ?: ownRequester
    // Remembered, because `frameTap` is keyed on it: a new lambda every
    // composition restarted the row's gesture under a finger. A press on the
    // row off the field — its label, its icon — puts the caret at the end of
    // the value nearer it, as one on the field's own frame does: see
    // `CaretAim`, shared with the field so both read the one text.
    val aim = remember { CaretAim() }
    val spot = remember { FrameSpot() }
    val focusOnTap: (LayoutCoordinates?, Offset) -> Unit = remember(requester, state, aim) {
        { frame, at ->
            aim.moveCaret(state, frame, at)
            requester.requestFocus()
        }
    }
    val editable = enabled && !readOnly
    val isError = errorMessage != null
    val message = errorMessage ?: supporting

    // A disabled row's ground is a list item's.
    val ground = if (enabled) containerColour else colours.surfaceSunken.copy(alpha = 0.5f)
    val content = if (enabled) colours.content else colours.contentDisabled
    val muted = if (enabled) colours.contentMuted else colours.contentDisabled
    val edge = when {
        // Error outranks focus, as it does on a field: a focused invalid value
        // is still invalid.
        enabled && isError -> BorderStroke(Theme.sizing.borderWidthStrong, field.borderError)
        editable && focused -> BorderStroke(Theme.sizing.borderWidthStrong, field.borderFocused)
        // At rest, a row's own edge: none, or the high contrast tier's.
        else -> contrastEdge()
    }

    Column(
        // Outside the caller's modifier, so a section still finds the row as a
        // row: see `ListRowEdges`.
        modifier = ListRowEdges
            .then(modifier)
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (message != null) ListItemDefaults.TwoLineMinHeight else ListItemDefaults.MinHeight)
            .clip(shape.clipProxy())
            .background(ground, shape)
            .optionalBorder(edge, shape)
            // The whole row is the field's frame: a caret over all of it, and a
            // press anywhere on it puts the caret in. See `TextFieldImpl`.
            .pointerCursor(Cursor.Text, enabled = editable)
            .opensKeyboard(enabled = editable)
            .then(if (enabled) Modifier.frameTap(spot, focusOnTap) else Modifier)
            .padding(start = ListItemDefaults.ContentInset, end = Theme.spacing.md)
            .padding(vertical = Theme.spacing.xs),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(imageVector = leadingIcon, contentDescription = null, tint = muted, size = Theme.sizing.iconLarge)
            }
            // The label as wide as its words, up to a share of the row, and the
            // field everything after it, its value at the row's end. Weights
            // left the label's unused share to nobody, and the value stopped
            // short of the row's end.
            LabelAndValue(
                modifier = Modifier.weight(1f),
                gap = Theme.spacing.md,
                label = label?.let { words ->
                    @Composable {
                        Text(
                            text = words,
                            // The field carries the name, as a text field's does:
                            // said here as well, it was the label twice.
                            modifier = Modifier.clearAndSetSemantics {},
                            style = Theme.typography.bodyMedium,
                            colour = if (enabled && isError) field.error else content,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            ) {
                TextFieldImpl(
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    readOnly = readOnly,
                    label = null,
                    placeholder = placeholder,
                    supporting = null,
                    errorMessage = null,
                    leadingIcon = null,
                    leading = null,
                    trailingIcon = null,
                    trailing = null,
                    variant = TextFieldVariant.Plain,
                    shape = RectangleShape,
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                    onKeyboardAction = onKeyboardAction,
                    imeChain = imeChain,
                    inputTransformation = inputTransformation,
                    outputTransformation = outputTransformation,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    // Nothing of a field's own drawn round it: the row is its frame.
                    colours = TextFieldDefaults.colours(
                        variant = TextFieldVariant.Plain,
                        container = Color.Transparent,
                        containerFocused = Color.Transparent,
                        containerDisabled = Color.Transparent,
                        border = Color.Transparent,
                        borderFocused = Color.Transparent,
                        borderError = Color.Transparent,
                        borderDisabled = Color.Transparent,
                    ),
                    metrics = TextFieldMetrics(
                        minHeight = 0.dp,
                        horizontalPadding = 0.dp,
                        iconPadding = 0.dp,
                        verticalPadding = 0.dp,
                        gap = 0.dp,
                    ),
                    interactionSource = interactions,
                    accessibleName = label ?: placeholder,
                    // The message is drawn by the row, under all of it; the error is
                    // still the field's to say.
                    inputSemantics = errorMessage?.let(::saysError),
                    focusRequester = requester,
                    alignEnd = label != null,
                    caretAim = aim,
                )
            }
            if (trailing != null) {
                Box(contentAlignment = Alignment.Center) {
                    ProvideContentColour(muted) {
                        ProvideTextStyle(Theme.typography.bodyMedium) { trailing() }
                    }
                }
            }
        }
        if (message != null) {
            Text(
                text = message,
                // Under the words, not under the icon.
                modifier = Modifier.padding(
                    start = if (leadingIcon != null) Theme.sizing.iconLarge + Theme.spacing.md else 0.dp,
                ),
                style = Theme.typography.bodySmall,
                colour = if (enabled && isError) field.error else muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The field's error, said on the input. A function with its type written out,
 * rather than a lambda left to inference: inside one whose receiver went
 * unseen, `error` is Kotlin's own, which throws.
 */
private fun saysError(message: String): SemanticsPropertyReceiver.() -> Unit = { semanticsError(message) }

/** The most of a row a [TextFieldListItem]'s label takes, as a share of the room beside its icon and trailing slot. */
private const val LabelShare = 0.4f

/**
 * A label up to [LabelShare] of the width, and [value] the rest of it after
 * [gap], both centred on the taller. Without a label, [value] is all of it.
 */
@Composable
private fun LabelAndValue(
    modifier: Modifier,
    gap: Dp,
    label: (@Composable () -> Unit)?,
    value: @Composable () -> Unit,
) {
    if (label == null) {
        Box(modifier) { value() }
        return
    }
    Layout(
        content = {
            Box { label() }
            Box { value() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val labelPlaceable = measurables[0].measure(loose.copy(maxWidth = (width * LabelShare).toInt()))
        val rest = (width - labelPlaceable.width - gapPx).coerceAtLeast(0)
        val valuePlaceable = measurables[1].measure(loose.copy(minWidth = rest, maxWidth = rest))
        val height = maxOf(labelPlaceable.height, valuePlaceable.height).coerceAtLeast(constraints.minHeight)
        layout(width, height) {
            labelPlaceable.placeRelative(0, (height - labelPlaceable.height) / 2)
            valuePlaceable.placeRelative(labelPlaceable.width + gapPx, (height - valuePlaceable.height) / 2)
        }
    }
}
