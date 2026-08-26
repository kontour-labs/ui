package io.kontour.ui.components.text

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColor
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.overlay.AnchoredDropdownMenu
import io.kontour.ui.overlay.MenuItem
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.anchorBounds
import io.kontour.ui.theme.Theme

/**
 * Whether a [Select], [MultiSelect] or [Combobox] has its menu open.
 *
 * Hoisted so the app can drive it — opening a select from a "change" link
 * elsewhere on the screen, or closing every select on a form when the sheet
 * holding it collapses. Left alone, the control manages this itself and the
 * parameter can be ignored.
 */
@Stable
class SelectState internal constructor(initiallyExpanded: Boolean) {
    var expanded: Boolean by mutableStateOf(initiallyExpanded)

    fun expand() { expanded = true }
    fun collapse() { expanded = false }
}

@Composable
fun rememberSelectState(initiallyExpanded: Boolean = false): SelectState =
    remember { SelectState(initiallyExpanded) }

/**
 * Picks one of a fixed set of options.
 *
 * ```kotlin
 * Select(
 *     value = departureType,
 *     options = DepartureType.entries,
 *     onValueChange = { departureType = it },
 *     label = "When",
 *     optionLabel = { it.label },
 * )
 * ```
 *
 * Looks like a [TextField] — same frame, same label, same helper and error slot,
 * because in a form it *is* one of the fields, and a select styled as a button
 * in a column of text inputs reads as a different kind of thing. They share
 * `FieldScaffold` rather than resembling each other by hand.
 *
 * Reach for a [io.kontour.ui.components.selection.RadioGroup] instead when there
 * are three or four options and room to show them: a select hides its options
 * behind a tap, a cost worth paying only when showing them would crowd the
 * screen. Above roughly a dozen, use [Combobox] so the user can type rather than
 * scroll.
 *
 * The menu matches the field's width and ticks the current value, reporting
 * `Role.RadioButton` per item to assistive tech — "one of these is on" is what
 * is actually being conveyed.
 *
 * @param optionLabel How to render each option. Needed in practice: the default
 *   `toString()` is right only for a `List<String>`.
 * @param optionIcon Draws an icon beside each option in the menu, and beside the
 *   value in the closed field.
 */
@Composable
fun <T> Select(
    value: T?,
    options: List<T>,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String = Theme.strings.select,
    optionLabel: (T) -> String = { it.toString() },
    optionIcon: ((T) -> ImageVector?)? = null,
    optionEnabled: (T) -> Boolean = { true },
    supporting: String? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    colors: TextFieldColors = TextFieldDefaults.colors(variant),
    metrics: TextFieldMetrics = TextFieldDefaults.metrics(),
    state: SelectState = rememberSelectState(),
    interactionSource: MutableInteractionSource? = null,
) {
    SelectFrame(
        valueLabel = value?.let(optionLabel),
        placeholder = placeholder,
        modifier = modifier,
        label = label,
        enabled = enabled,
        supporting = supporting,
        errorMessage = errorMessage,
        leadingIcon = leadingIcon ?: value?.let { optionIcon?.invoke(it) },
        colors = colors,
        metrics = metrics,
        state = state,
        interactionSource = interactionSource,
    ) { dismiss ->
        for (option in options) {
            MenuItem(
                onClick = {
                    onValueChange(option)
                    dismiss()
                },
                enabled = optionEnabled(option),
                selected = option == value,
            ) {
                +optionLabel(option)
                optionIcon?.invoke(option)?.let { icon -> leading { +icon } }
            }
        }
    }
}

/**
 * Picks any number of a fixed set of options.
 *
 * ```kotlin
 * MultiSelect(
 *     value = includedModes,
 *     options = TransportMode.entries,
 *     onValueChange = { includedModes = it },
 *     label = "Include",
 *     optionLabel = { it.label },
 * )
 * ```
 *
 * The menu stays open as options are toggled. Closing after each would mean
 * three trips back to the trigger to pick three modes.
 *
 * Prefer a [io.kontour.ui.components.selection.ChipGroup] of filter chips where
 * the options fit on screen — chips show what is on without a tap, which is the
 * thing that matters for a filter. A multi-select earns its place when the list
 * is long, or when a summary line reads better than a row of chips.
 *
 * @param summary How to describe the selection in the closed field. The default
 *   lists up to three labels and then collapses to a count, because "Bus, Train,
 *   Ferry, Tram, Coa…" truncated mid-word says less than "5 selected".
 */
@Composable
fun <T> MultiSelect(
    value: Set<T>,
    options: List<T>,
    onValueChange: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String = Theme.strings.select,
    optionLabel: (T) -> String = { it.toString() },
    optionIcon: ((T) -> ImageVector?)? = null,
    optionEnabled: (T) -> Boolean = { true },
    summary: (Set<T>) -> String = { selected ->
        val labels = options.filter { it in selected }.map(optionLabel)
        when {
            labels.isEmpty() -> ""
            labels.size <= 3 -> labels.joinToString(", ")
            else -> "${labels.size} selected"
        }
    },
    supporting: String? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    colors: TextFieldColors = TextFieldDefaults.colors(variant),
    metrics: TextFieldMetrics = TextFieldDefaults.metrics(),
    state: SelectState = rememberSelectState(),
    interactionSource: MutableInteractionSource? = null,
) {
    SelectFrame(
        valueLabel = summary(value).takeIf { it.isNotEmpty() },
        placeholder = placeholder,
        modifier = modifier,
        label = label,
        enabled = enabled,
        supporting = supporting,
        errorMessage = errorMessage,
        leadingIcon = leadingIcon,
        colors = colors,
        metrics = metrics,
        state = state,
        interactionSource = interactionSource,
    ) {
        for (option in options) {
            MenuItem(
                onClick = {
                    onValueChange(
                        if (option in value) value - option else value + option
                    )
                },
                enabled = optionEnabled(option),
                selected = option in value,
                // Any number of these can be on, so they are checkboxes.
                // Announced as radio buttons — which is what they were — a
                // screen reader told the user that choosing a second mode of
                // transport would clear the first.
                multiple = true,
            ) {
                +optionLabel(option)
                optionIcon?.invoke(option)?.let { icon -> leading { +icon } }
            }
        }
    }
}

/**
 * A [Select] the user can type into to narrow a long list.
 *
 * ```kotlin
 * Combobox(
 *     value = operator,
 *     options = allOperators,
 *     onValueChange = { operator = it },
 *     label = "Operator",
 *     optionLabel = { it.name },
 * )
 * ```
 *
 * The filter is a case-insensitive `contains` over [optionLabel]. Pass [matches]
 * for anything smarter — finding a route by its number *or* its destination.
 *
 * This is a select with search, not an autocomplete: the value is always one of
 * [options], and typing something unmatched leaves the previous value alone. For
 * free text with suggestions, use a [SearchField] and render the results
 * yourself. Conflating the two gives you a control where it is unclear whether
 * what you typed counts as an answer.
 */
@Composable
fun <T> Combobox(
    value: T?,
    options: List<T>,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String = Theme.strings.select,
    optionLabel: (T) -> String = { it.toString() },
    optionIcon: ((T) -> ImageVector?)? = null,
    matches: (T, String) -> Boolean = { option, query ->
        optionLabel(option).contains(query, ignoreCase = true)
    },
    emptyLabel: String = Theme.strings.noMatches,
    supporting: String? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    colors: TextFieldColors = TextFieldDefaults.colors(variant),
    metrics: TextFieldMetrics = TextFieldDefaults.metrics(),
    state: SelectState = rememberSelectState(),
    interactionSource: MutableInteractionSource? = null,
) {
    val query = rememberTextFieldState()
    var text by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.collect { text = it }
    }

    val filtered = remember(options, text) {
        if (text.isEmpty()) options else options.filter { matches(it, text) }
    }

    SelectFrame(
        valueLabel = value?.let(optionLabel),
        placeholder = placeholder,
        modifier = modifier,
        label = label,
        enabled = enabled,
        supporting = supporting,
        errorMessage = errorMessage,
        leadingIcon = leadingIcon,
        colors = colors,
        metrics = metrics,
        state = state,
        interactionSource = interactionSource,
        // The query belongs to one opening of the menu. Carrying it over would
        // reopen the list already filtered by something the user typed a screen
        // ago and has no reason to remember.
        onOpenChange = { open -> if (!open) query.clearText() },
        editor = { slot, requester ->
            CompositionLocalProvider(LocalContentColor provides colors.content) {
                BasicTextField(
                    state = query,
                    modifier = slot.fillMaxWidth().focusRequester(requester),
                    textStyle = Theme.typography.bodyMedium.merge(color = colors.content),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    cursorBrush = SolidColor(colors.cursor),
                )
                if (query.text.isEmpty()) {
                    Text(
                        text = value?.let(optionLabel) ?: placeholder,
                        style = Theme.typography.bodyMedium,
                        color = colors.placeholder,
                        maxLines = 1,
                    )
                }
            }
        },
    ) { dismiss ->
        if (filtered.isEmpty()) {
            Text(
                text = emptyLabel,
                modifier = Modifier.padding(
                    horizontal = Theme.spacing.sm,
                    vertical = Theme.spacing.xs,
                ),
                style = Theme.typography.bodyMedium,
                color = Theme.colors.contentMuted,
            )
        }
        for (option in filtered) {
            MenuItem(
                onClick = {
                    onValueChange(option)
                    dismiss()
                },
                selected = option == value,
            ) {
                +optionLabel(option)
                optionIcon?.invoke(option)?.let { icon -> leading { +icon } }
            }
        }
    }
}

/**
 * The shell all three share: a field-shaped trigger, a chevron that turns over,
 * and a menu the width of the field.
 *
 * The menu is anchored with [Modifier.anchorBounds] on the frame rather than by
 * declaring it inside one, because "the parent layout" is the wrong node here —
 * the menu has to line up with the whole field, not with whichever slot of the
 * row it happened to be written in.
 *
 * @param editor Replaces the static value line with an input, for [Combobox].
 *   Receives the slot modifier and a [FocusRequester] that fires when the menu
 *   opens — a search box the user has to tap a second time to type in is a
 *   search box nobody uses.
 * @param content The menu body, given a `dismiss`. [Select] calls it; [MultiSelect]
 *   deliberately does not.
 */
@Composable
private fun SelectFrame(
    valueLabel: String?,
    placeholder: String,
    modifier: Modifier,
    label: String?,
    enabled: Boolean,
    supporting: String?,
    errorMessage: String?,
    leadingIcon: ImageVector?,
    colors: TextFieldColors,
    metrics: TextFieldMetrics,
    state: SelectState,
    interactionSource: MutableInteractionSource?,
    onOpenChange: (Boolean) -> Unit = {},
    editor: (@Composable RowScope.(Modifier, FocusRequester) -> Unit)? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val feedback = LocalFeedback.current
    val motion = Theme.motion
    val editorFocus = remember { FocusRequester() }

    var anchor by remember { mutableStateOf<Rect?>(null) }
    val expanded = state.expanded

    fun setExpanded(open: Boolean) {
        if (state.expanded == open) return
        state.expanded = open
        onOpenChange(open)
    }

    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "selectChevron",
    )

    if (editor != null) {
        LaunchedEffect(expanded) {
            if (expanded) runCatching { editorFocus.requestFocus() }
        }
    }

    FieldScaffold(
        modifier = modifier,
        enabled = enabled,
        // Drawn focused while its menu is open: that is where the user's
        // attention is, whatever the focus system thinks.
        focused = focused || expanded,
        colors = colors,
        metrics = metrics,
        shape = Theme.shapes.field,
        label = label,
        supporting = supporting,
        errorMessage = errorMessage,
        leadingIcon = leadingIcon,
        trailing = {
            Icon(
                imageVector = SystemIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = chevron },
                tint = if (enabled) colors.label else colors.contentDisabled,
                size = Theme.sizing.iconMedium,
            )
        },
        frameModifier = Modifier
            .anchorBounds { anchor = it }
            .semantics {
                role = Role.DropdownList
                // The frame merges its value text, so this names the control
                // rather than replacing what it says: "When, Tomorrow, dropdown
                // list". Without it the announcement is just the value, and a
                // form of three selects reads as three unattributed values.
                if (label != null) contentDescription = label
                if (!enabled) disabled()
            }
            .clickable(
                interactionSource = interactions,
                // A whole field flinching is too much movement for a control
                // this large — see `kontourIndication`.
                indication = kontourIndication(Theme.shapes.field, pressScale = 1f),
                enabled = enabled,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    setExpanded(!expanded)
                },
            ),
    ) {
        if (editor != null && expanded) {
            editor(Modifier.weight(1f), editorFocus)
        } else {
            Text(
                text = valueLabel ?: placeholder,
                modifier = Modifier.weight(1f),
                style = Theme.typography.bodyMedium,
                color = when {
                    !enabled -> colors.contentDisabled
                    valueLabel == null -> colors.placeholder
                    else -> colors.content
                },
                maxLines = 1,
            )
        }
    }

    AnchoredDropdownMenu(
        visible = expanded,
        anchor = anchor,
        onDismissRequest = { setExpanded(false) },
        alignment = OverlayAlignment.Start,
        matchAnchorWidth = true,
    ) {
        content { setExpanded(false) }
    }
}
