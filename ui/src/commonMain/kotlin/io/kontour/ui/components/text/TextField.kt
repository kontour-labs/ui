package io.kontour.ui.components.text

import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.intl.LocaleList
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error as semanticsError
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.platform.opensKeyboard
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextRange
import io.kontour.ui.platform.BrowserFace
import io.kontour.ui.platform.BrowserText
import io.kontour.ui.platform.browserText
import io.kontour.ui.platform.textTouchesGoToTheBrowser
import io.kontour.ui.theme.jetBrainsMonoFontFamily
import io.kontour.ui.theme.outfitFontFamily
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.mono

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
 * @param code For text that is code, a key or a command: set in the theme's
 *   mono face, and typed with no autocorrect or capitalisation, which would
 *   change what was meant.
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
    code: Boolean = false,
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
    TextFieldImpl(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        supporting = supporting,
        errorMessage = errorMessage,
        leadingIcon = leadingIcon,
        leading = leading,
        trailingIcon = trailingIcon,
        trailing = trailing,
        variant = variant,
        shape = shape,
        keyboardType = keyboardType,
        imeAction = imeAction,
        onKeyboardAction = onKeyboardAction,
        imeChain = imeChain,
        inputTransformation = inputTransformation,
        outputTransformation = outputTransformation,
        lineLimits = lineLimits,
        colours = colours,
        metrics = metrics,
        interactionSource = interactionSource,
        accessibleName = label,
        code = code,
    )
}

/**
 * The field, named for assistive tech by [accessibleName]: [TextField]'s
 * label, or what a [SearchField] says it is for when it has no label to show.
 */
@Composable
internal fun TextFieldImpl(
    state: TextFieldState,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    label: String?,
    placeholder: String?,
    supporting: String?,
    errorMessage: String?,
    leadingIcon: ImageVector?,
    leading: (@Composable () -> Unit)?,
    trailingIcon: ImageVector?,
    trailing: (@Composable () -> Unit)?,
    variant: TextFieldVariant,
    shape: Shape,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onKeyboardAction: KeyboardActionHandler?,
    imeChain: ImeChainStep?,
    inputTransformation: InputTransformation?,
    outputTransformation: OutputTransformation?,
    lineLimits: TextFieldLineLimits,
    colours: TextFieldColours,
    metrics: TextFieldMetrics,
    interactionSource: MutableInteractionSource?,
    accessibleName: String?,
    code: Boolean = false,
    /**
     * What the input itself tells the platform beyond its name: an autofill
     * content type, or that it holds nothing to fill. On the input rather than
     * on the caller's modifier, which lands on the frame — a node an autofill
     * service never reads.
     */
    inputSemantics: (SemanticsPropertyReceiver.() -> Unit)? = null,
    /** A requester for the input, for a field its owner focuses — a select's box for "Other". */
    focusRequester: FocusRequester? = null,
    /**
     * Whether [outputTransformation] is a password's dots and nothing else, which
     * a browser can draw for itself: see [textTouchesGoToTheBrowser].
     */
    masked: Boolean = false,
    /**
     * Whether the text, and the placeholder standing in for it, sit at the end
     * of the field rather than its start: the value of a
     * [io.kontour.ui.components.list.TextFieldListItem] beside its label, where
     * a settings row's value goes.
     */
    alignEnd: Boolean = false,
    /**
     * Where a press off the text puts the caret, shared with a frame round this
     * one: a [io.kontour.ui.components.list.TextFieldListItem]'s row. See [CaretAim].
     */
    caretAim: CaretAim? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    // Code in the theme's mono face, at the field's own size: the family and
    // tabular figures from `mono`, the metrics from the scale.
    val textStyle = (if (code) Theme.typography.bodyMedium.mono() else Theme.typography.bodyMedium)
        .let { if (alignEnd) it.copy(textAlign = TextAlign.End) else it }
    val focused by interactions.collectIsFocusedAsState()

    // The requester the frame hands a press to.
    //
    // A chain already owns one — `rememberImeChain` needs it to move focus down
    // a form — and taking it when it is there keeps one requester on one node.
    // Without a chain there was none at all, which is why the frame had nothing
    // to focus and the field's padding did nothing.
    val ownRequester = remember { FocusRequester() }
    val requester = imeChain?.requester ?: focusRequester ?: ownRequester

    // **In a browser on a phone, the focused field's caret, selection and menu
    // are the browser's own**: see `textTouchesGoToTheBrowser`. Not for a field
    // whose text is drawn as something other than itself — a phone number
    // grouped as it is typed — whose letters the browser cannot put where
    // Compose's are.
    val routed = textTouchesGoToTheBrowser && enabled && !readOnly && (outputTransformation == null || masked)
    val scroll = rememberScrollState()
    val browser = remember { BrowserText() }
    val aim = caretAim ?: remember { CaretAim() }
    aim.scroll = scroll
    aim.singleLine = lineLimits == TextFieldLineLimits.SingleLine
    if (routed) {
        // Asked for only here, where they are compared against: seven font
        // resources resolved on every composition of every field, on platforms
        // that never route a field to the browser, for an answer only this
        // branch reads.
        val outfit = outfitFontFamily()
        val jetBrainsMono = jetBrainsMonoFontFamily()
        browser.state = state
        browser.inputTransformation = inputTransformation
        browser.focused = focused
        browser.singleLine = lineLimits == TextFieldLineLimits.SingleLine
        browser.masked = masked
        browser.style = textStyle
        browser.face = when (textStyle.fontFamily) {
            outfit -> BrowserFace.Outfit
            jetBrainsMono -> BrowserFace.Mono
            else -> null
        }
        browser.caret = colours.cursor
        browser.selection = colours.selectionBackground
        browser.scroll = scroll
    } else {
        browser.focused = false
    }
    // The browser draws them over a focused field, so Compose's would be a second
    // caret and a second selection a hair away from the first.
    val drawnByBrowser = routed && focused
    val selectionColours = remember(colours, drawnByBrowser) {
        TextSelectionColors(
            handleColor = if (drawnByBrowser) Color.Transparent else colours.cursor,
            backgroundColor = if (drawnByBrowser) Color.Transparent else colours.selectionBackground,
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
            // A tap here puts a caret in the field, and in a browser on an
            // iPhone that has to open the keyboard during the tap: see
            // `opensKeyboard`.
            .opensKeyboard(enabled = enabled && !readOnly)
            // On the frame, not the input, and that placement is the mechanism:
            // the Initial pointer pass runs parent to child, so a secondary
            // press is consumed here before the text input's own detector can
            // raise the platform's context menu. See `textContextMenu`.
            .textContextMenu(
                state = state,
                editable = enabled && !readOnly,
                enabled = enabled,
                // The menu's paste and cut go through the field's filter, as a
                // keystroke does, and a masked field offers neither Cut nor Copy.
                filter = inputTransformation,
                copyable = !masked,
            ),
        // A press anywhere on the frame puts the caret in the field, at the
        // end of the text nearer the press: see [CaretAim].
        //
        // Not gated on `readOnly`: a read-only field is still focusable and its
        // text is still selectable, which is the whole reason foundation has
        // that flag rather than `enabled`. It is gated on `enabled`, because a
        // disabled field should not take focus from wherever it currently is.
        // Remembered, because `frameTap` is keyed on it.
        onFrameTap = if (enabled) {
            remember(requester, state, aim) {
                { frame, at ->
                    aim.place(state, frame, at)
                    requester.requestFocus()
                }
            }
        } else {
            null
        },
    ) {
        val contentColour = if (enabled) colours.content else colours.contentDisabled
        val parts = LocalFieldParts.current

        // The input and its placeholder at the top start of the box they
        // share — the top left, or the top right right to left — which is
        // where the text starts. Centred, a text area's hint floated a line or
        // more below where the first character goes: *"in large text fields,
        // the placeholder text is centred. it should be top-left aligned …
        // like the text itself."* A single-line field's box is one line tall
        // either way, but an empty input measures a pixel shorter than one
        // with a character in it, which the hint does not: centred, the empty
        // input and its caret sat a pixel below the hint, and jumped up a
        // pixel at the first character.
        Box(
            Modifier.weight(1f).then(if (parts != null) Modifier.onPlaced(parts.text) else Modifier),
            contentAlignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
        ) {
            CompositionLocalProvider(
                LocalTextSelectionColors provides selectionColours,
                LocalContentColour provides contentColour,
                // Nor its toolbar: the browser's menu is the field's.
                LocalTextToolbar provides if (routed) NoTextToolbar else LocalTextToolbar.current,
            ) {
                BasicTextField(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPlaced { aim.input = it }
                        .then(if (routed) Modifier.tapPlacesCaret(state, browser, requester) else Modifier)
                        .browserText(browser)
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
                            // it is for. A search field with no label is named
                            // by its placeholder instead — see `SearchField`.
                            if (accessibleName != null) contentDescription = accessibleName
                            if (errorMessage != null) semanticsError(errorMessage)
                            // Foundation greys the field out and stops accepting
                            // input, but does not mark the node disabled — so a
                            // screen reader still offers "double tap to edit" on
                            // a field that will not take a character.
                            if (!enabled) disabled()
                            inputSemantics?.invoke(this)
                        },
                    enabled = enabled,
                    readOnly = readOnly,
                    textStyle = textStyle.merge(color = contentColour),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = keyboardType,
                        // A chain decides the action key outright, overriding
                        // both this parameter and a specialised field's own
                        // default — `PasswordField` defaults to Done, which is
                        // wrong for a password halfway down a form. One rule
                        // beats a precedence table nobody can predict.
                        imeAction = imeChain?.imeAction ?: imeAction,
                        // The reader's language, so a keyboard that can switch
                        // offers theirs and its dictionary.
                        hintLocales = LocaleList(Theme.locale),
                        // Code is typed as it is meant: a keyboard that
                        // corrected `val` or capitalised a key would change it.
                        autoCorrectEnabled = if (code) false else null,
                        capitalization = if (code) KeyboardCapitalization.None else KeyboardCapitalization.Unspecified,
                    ),
                    onKeyboardAction = onKeyboardAction ?: imeChain?.handler,
                    lineLimits = lineLimits,
                    inputTransformation = inputTransformation,
                    outputTransformation = outputTransformation,
                    cursorBrush = SolidColor(if (drawnByBrowser) Color.Transparent else colours.cursor),
                    interactionSource = interactions,
                    scrollState = scroll,
                    onTextLayout = { result ->
                        aim.layout = result
                        if (routed) browser.layout = result()
                    },
                )
            }

            if (placeholder != null && state.text.isEmpty()) {
                Text(
                    text = placeholder,
                    // Said once, as the field's name, when it is that: read
                    // again as a line of its own it is the same words twice.
                    modifier = if (placeholder == accessibleName) Modifier.clearAndSetSemantics {} else Modifier,
                    style = textStyle,
                    // The disabled grey when disabled, as the text it stands in
                    // for is. It kept its own colour, darker than that grey, so
                    // an empty disabled field said more than a filled one.
                    colour = if (enabled) colours.placeholder else colours.contentDisabled,
                    textAlign = if (alignEnd) TextAlign.End else null,
                    // Over the lines an empty area already has, and no more: a
                    // hint taller than that would grow the empty field, which
                    // would then shrink a line at the first keystroke. One line
                    // in a single-line field, as before.
                    maxLines = (lineLimits as? TextFieldLineLimits.MultiLine)?.minHeightInLines ?: 1,
                    overflow = TextOverflow.Ellipsis,
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
 * @param code For a snippet, a config file or a key pasted in: set in the
 *   theme's mono face, so columns line up and a 0 is not an O, and typed with
 *   no autocorrect or capitalisation.
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
    code: Boolean = false,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    shape: Shape = Theme.shapes.field,
    inputTransformation: InputTransformation? = null,
    colours: TextFieldColours = TextFieldDefaults.colours(variant),
    metrics: TextFieldMetrics = TextFieldDefaults.metrics(),
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
        code = code,
        variant = variant,
        shape = shape,
        imeAction = ImeAction.Default,
        inputTransformation = inputTransformation,
        lineLimits = lineLimits,
        colours = colours,
        metrics = metrics,
        interactionSource = interactionSource,
    )
}

/** What a [TextArea] takes by default. */
object TextAreaDefaults {
    /** Three lines to start with, growing to eight before it scrolls. */
    val LineLimits: TextFieldLineLimits.MultiLine =
        TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8)
}

/**
 * A tap on a field the browser's input will take over, before it has: the caret
 * goes where the finger was, and the field takes the focus, as foundation's own
 * tap does — but without foundation's caret handle, which would be left under a
 * caret the browser draws, a teardrop nobody can see that still takes touches.
 *
 * Only the lift, and only a tap's: a press that moves is left to the list the
 * field is in, which scrolls.
 */
private fun Modifier.tapPlacesCaret(state: TextFieldState, browser: BrowserText, requester: FocusRequester): Modifier =
    pointerInput(state, browser, requester) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Touch) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (change.isConsumed || (change.position - down.position).getDistance() > slop) return@awaitEachGesture
                if (!change.pressed) {
                    change.consume()
                    val layout = browser.layout
                    val scrolled = browser.scroll?.value?.toFloat() ?: 0f
                    val at = if (browser.singleLine) change.position + Offset(scrolled, 0f) else change.position + Offset(0f, scrolled)
                    if (layout != null) {
                        val offset = layout.getOffsetForPosition(at).coerceIn(0, state.text.length)
                        state.edit { selection = TextRange(offset) }
                    }
                    requester.requestFocus()
                    return@awaitEachGesture
                }
            }
        }
    }

/** A text toolbar that shows nothing: a field whose menu is the browser's. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden

    override fun hide() = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}
