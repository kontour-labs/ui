package io.kontour.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * The query, and whether the search is currently open.
 *
 * Hoisted rather than internal so a screen can open the search from somewhere
 * else — a keyboard shortcut, a "search this area" button on a map — and so the
 * query survives being collapsed and reopened.
 */
@Stable
class NavSearchState internal constructor(
    /** What the user has typed. Shared by the collapsed pill and the field. */
    val text: TextFieldState,
) {
    var expanded: Boolean by mutableStateOf(false)
        private set

    fun expand() {
        expanded = true
    }

    /** @param clear Whether to throw the query away as well as closing. */
    fun collapse(clear: Boolean = false) {
        expanded = false
        if (clear) text.clearText()
    }
}

/** Remembers a [NavSearchState] across recomposition. */
@Composable
fun rememberNavSearchState(): NavSearchState {
    val text = rememberTextFieldState()
    return remember(text) { NavSearchState(text) }
}

/**
 * A search field sized to the navigation surface it is in.
 *
 * ```kotlin
 * val search = rememberNavSearchState()
 *
 * NavBar(
 *     items = destinations,
 *     selectedIndex = current,
 *     search = { NavSearch(search, onSearch = viewModel::search) },
 *     searchIndex = destinations.size / 2,
 * )
 * ```
 *
 * With `searchIndex = items.size / 2` the destinations sit two either side of
 * it, which is the arrangement a map app wants: the thing people came to do in
 * the middle, and the places they might go on the flanks.
 *
 * ### Two shapes, and the surface picks
 *
 * Somewhere narrow — a bar, or a rail at 88dp — this is a pill that opens a
 * panel over the screen, because there is no typing to be done in a hundred dp.
 * Somewhere wide — an expanded rail, a drawer — it is simply the field, because
 * there is, and an overlay would be ceremony.
 *
 * It reads [LocalNavExpansion] to tell which, so a rail growing into a drawer
 * turns its search pill into a search field as part of the same movement that
 * reveals the labels beside the icons. [editInPlace] overrides that where a
 * caller knows better.
 *
 * This is a wrapper. [NavExpandingSlot] is the pill-and-panel underneath it and
 * knows nothing about searching — a filter, an account switcher or a "where
 * to?" prompt is the same shape.
 *
 * @param editInPlace Whether this is a live field rather than a pill that opens
 *   one. Follows the surrounding surface's width by default.
 * @param placement Where the panel goes when there is not room to edit in place.
 * @param results Shown with the field: above it over a keyboard, below it at the
 *   top of the screen, and under an in-place field. Given a `ColumnScope`, so a
 *   caller can weight a list to fill what is left.
 */
@Composable
fun NavSearch(
    state: NavSearchState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    editInPlace: Boolean = LocalNavExpansion.current.expanded,
    placement: NavExpandPlacement = NavExpandPlacement.AboveKeyboard,
    placeholder: String = Theme.strings.search,
    /**
     * Beside the placeholder. `null` for none, matching
     * [io.kontour.ui.components.text.SearchField] — the library owns no
     * magnifier glyph, because an icon set is the app's choice.
     */
    searchIcon: ImageVector? = null,
    containerColor: Color = navSlotContainerColor(),
    contentColor: Color = Theme.colors.content,
    onQuery: ((String) -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    results: (@Composable ColumnScope.() -> Unit)? = null,
) {
    if (editInPlace) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement
                .spacedBy(Theme.spacing.sm),
        ) {
            SearchField(
                state = state.text,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                placeholder = placeholder,
                searchIcon = searchIcon,
                onQuery = onQuery,
                onSearch = onSearch,
                shape = Theme.shapes.pill,
            )
            results?.invoke(this)
        }
        return
    }

    val focus = remember { FocusRequester() }
    val typed = state.text.text.toString()

    NavExpandingSlot(
        expanded = state.expanded,
        onExpandedChange = { if (it) state.expand() else state.collapse() },
        modifier = modifier,
        enabled = enabled,
        placement = placement,
        containerColor = containerColor,
        contentColor = contentColor,
        expandedContent = {
            if (placement == NavExpandPlacement.AboveKeyboard) ResultsPanel(results)

            SearchField(
                state = state.text,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = placeholder,
                searchIcon = searchIcon,
                onQuery = onQuery,
                onSearch = onSearch,
                shape = Theme.shapes.pill,
            )

            if (placement == NavExpandPlacement.Top) ResultsPanel(results)

            // Inside the panel, so the field it focuses exists. Nothing else in
            // the overlay wants focus, but `trapFocus` would otherwise leave it
            // on the scrim with the keyboard shut — an expanded search you have
            // to tap a second time to type in.
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
    ) {
        if (searchIcon != null) {
            Icon(
                imageVector = searchIcon,
                contentDescription = null,
                size = Theme.sizing.iconMedium,
                tint = if (enabled) Theme.colors.contentMuted else Theme.colors.contentDisabled,
            )
        }
        // The last query once there has been one. A pill that goes back to
        // saying "Search" after a search has run is a pill that looks like it
        // forgot. Skipped entirely where there is no room for a word — a rail at
        // 88dp gets the icon and nothing else.
        if (LocalNavExpansion.current.progress > NavSearchDefaults.LabelAt) {
            Text(
                text = typed.ifEmpty { placeholder },
                style = Theme.typography.bodyMedium,
                color = when {
                    !enabled -> Theme.colors.contentDisabled
                    typed.isEmpty() -> Theme.colors.contentMuted
                    else -> contentColor
                },
                maxLines = 1,
            )
        }
    }
}

/** Metrics for [NavSearch]. */
object NavSearchDefaults {
    /**
     * How far into a surface's expansion the collapsed pill gains its word.
     *
     * Zero for a bar, which does not resize and reports `progress = 1` — the
     * pill there is always a word wide. It is a rail at its narrowest, reporting
     * 0, that gets the icon alone.
     */
    const val LabelAt: Float = 0.01f
}

/**
 * The results, on a surface of their own.
 *
 * The caller supplies the contents; the panel under them belongs to the
 * component, because the alternative is every caller discovering for themselves
 * that plain text on a dimmed page is barely legible.
 */
@Composable
private fun ColumnScope.ResultsPanel(results: (@Composable ColumnScope.() -> Unit)?) {
    if (results == null) return
    Surface(
        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        shape = Theme.shapes.panel,
        color = Theme.colors.surface,
    ) {
        Column(
            modifier = Modifier.padding(Theme.spacing.md),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement
                .spacedBy(Theme.spacing.xs),
            content = results,
        )
    }
}
