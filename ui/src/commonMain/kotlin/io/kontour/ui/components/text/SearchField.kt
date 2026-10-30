package io.kontour.ui.components.text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.ui.text.input.KeyboardType
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A search field with a clear button and built-in debounce.
 *
 * ```kotlin
 * val query = rememberTextFieldState()
 *
 * SearchField(
 *     state = query,
 *     placeholder = "Search stops and routes",
 *     searchIcon = FontAwesome.Solid.Search,
 *     clearIcon = FontAwesome.Solid.Times,
 *     onQuery = viewModel::search,
 * )
 * ```
 *
 * [onQuery] fires on a [debounce] delay rather than on every
 * keystroke, so a search-as-you-type field does not fire a request per
 * character. The debounce lives here rather than in every caller's view model
 * because getting it wrong is invisible until you look at the network log.
 *
 * The clear button appears only when there is something to clear, and pops in
 * with a small scale rather than fading — at 16dp a cross-fade is barely
 * perceptible.
 *
 * The field is named by its [placeholder], which says what it searches, and by
 * `Strings.search` without one: a search field shows no label, and with nothing
 * to name it a screen reader announced an unnamed edit box.
 *
 * @param onSearch Called when the user submits from the keyboard. Fires
 *   immediately, bypassing the debounce, because an explicit submit should not
 *   wait.
 */
@OptIn(FlowPreview::class)
@Composable
fun SearchField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = Theme.strings.search,
    searchIcon: ImageVector? = Theme.icons.search,
    clearIcon: ImageVector? = Theme.icons.close,
    clearLabel: String = Theme.strings.clearSearch,
    supporting: String? = null,
    debounce: Duration = 250.milliseconds,
    onQuery: ((String) -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    variant: TextFieldVariant = TextFieldVariant.Filled,
    shape: Shape = Theme.shapes.field,
    colours: TextFieldColours = TextFieldDefaults.colours(variant),
    metrics: TextFieldMetrics = TextFieldDefaults.metrics(),
    interactionSource: MutableInteractionSource? = null,
) {
    val currentOnQuery by rememberUpdatedState(onQuery)

    if (onQuery != null) {
        LaunchedEffect(state, debounce) {
            snapshotFlow { state.text.toString() }
                .debounce(debounce)
                .distinctUntilChanged()
                .collect { currentOnQuery?.invoke(it) }
        }
    }

    val currentOnSearch by rememberUpdatedState(onSearch)
    // Read by the clear button's `AnimatedVisibility`, which only needs to know
    // whether there is text. As `state.text.isNotEmpty()` it read the text
    // itself, so every keystroke composed the button's slot again — the
    // visibility, its transition and the button inside it — to be told the
    // same thing. See the same note in `TextFieldImpl`.
    val hasText by remember(state) { derivedStateOf { state.text.isNotEmpty() } }

    TextFieldImpl(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = false,
        label = null,
        placeholder = placeholder,
        supporting = supporting,
        errorMessage = null,
        leadingIcon = searchIcon,
        leading = null,
        trailingIcon = null,
        variant = variant,
        shape = shape,
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Search,
        onKeyboardAction = { currentOnSearch?.invoke(state.text.toString()) },
        imeChain = null,
        inputTransformation = null,
        outputTransformation = null,
        lineLimits = TextFieldLineLimits.SingleLine,
        colours = colours,
        metrics = metrics,
        interactionSource = interactionSource,
        // A search field shows no label: what it is for is its placeholder,
        // and with none, that it searches. It was an unnamed edit box.
        accessibleName = placeholder ?: Theme.strings.search,
        trailing = if (clearIcon != null) {
            {
                AnimatedVisibility(
                    visible = hasText,
                    enter = fadeIn(Theme.motion.tweenFast()) +
                        scaleIn(
                            Theme.motion.springOrTween(Theme.motion.springBouncy),
                            initialScale = 0.5f,
                        ),
                    exit = fadeOut(Theme.motion.tweenFast()) +
                        scaleOut(Theme.motion.tweenFast(), targetScale = 0.5f),
                ) {
                    IconButton(
                        icon = clearIcon,
                        contentDescription = clearLabel,
                        onClick = { state.clearText() },
                        enabled = enabled,
                        size = ButtonSize.XSmall,
                    )
                }
            }
        } else {
            null
        },
    )
}

/** Clears the field's text. Shared with [Combobox], which resets its query on close. */
internal fun TextFieldState.clearText() {
    edit { replace(0, length, "") }
}
