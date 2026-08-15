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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

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
 *     onQueryChange = viewModel::search,
 * )
 * ```
 *
 * [onQueryChange] fires on a [debounceMillis] delay rather than on every
 * keystroke, so a search-as-you-type field does not fire a request per
 * character. The debounce lives here rather than in every caller's view model
 * because getting it wrong is invisible until you look at the network log.
 *
 * The clear button appears only when there is something to clear, and pops in
 * with a small scale rather than fading — at 16dp a cross-fade is barely
 * perceptible.
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
    placeholder: String? = "Search",
    searchIcon: ImageVector? = null,
    clearIcon: ImageVector? = null,
    clearLabel: String = "Clear search",
    supportingText: String? = null,
    debounceMillis: Long = 250L,
    onQueryChange: ((String) -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    variant: TextFieldVariant = TextFieldVariant.Filled,
    shape: Shape = Theme.shapes.small,
    interactionSource: MutableInteractionSource? = null,
) {
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)

    if (onQueryChange != null) {
        LaunchedEffect(state, debounceMillis) {
            snapshotFlow { state.text.toString() }
                .debounce(debounceMillis)
                .distinctUntilChanged()
                .collect { currentOnQueryChange?.invoke(it) }
        }
    }

    val currentOnSearch by rememberUpdatedState(onSearch)

    TextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        placeholder = placeholder,
        supportingText = supportingText,
        leadingIcon = searchIcon,
        variant = variant,
        shape = shape,
        imeAction = ImeAction.Search,
        onKeyboardAction = { currentOnSearch?.invoke(state.text.toString()) },
        interactionSource = interactionSource,
        trailing = if (clearIcon != null) {
            {
                AnimatedVisibility(
                    visible = state.text.isNotEmpty(),
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
