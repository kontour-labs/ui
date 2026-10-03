package io.kontour.ui.nav

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.components.text.FieldParts
import io.kontour.ui.components.text.LocalFieldParts
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.components.text.TextFieldDefaults
import io.kontour.ui.components.text.TextFieldVariant
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
 * NavBar {
 *     item(here == Home, { go(Home) }, key = Home, icon = Tabler.Outline.Home) { +"Home" }
 *     item(here == Map, { go(Map) }, key = Map, icon = Tabler.Outline.Map) { +"Map" }
 *     slot(weight = 1f) { NavSearch(search, onSearch = viewModel::search) }
 *     item(here == Saved, { go(Saved) }, key = Saved, icon = Tabler.Outline.Star) { +"Saved" }
 *     item(here == Me, { go(Me) }, key = Me, icon = Tabler.Outline.User) { +"Me" }
 * }
 * ```
 *
 * In the middle of the bar the destinations sit two either side of it, which is
 * the arrangement a map app wants: the thing people came to do in the middle,
 * and the places they might go on the flanks. [NavigationSuiteScaffold]'s
 * `search` and `searchIndex` put it there by counting destinations.
 *
 * ### Two shapes, and the surface picks
 *
 * Somewhere narrow — a bar, or a rail at 88dp — this is a pill that opens a
 * panel over the screen, because there is no typing to be done in a hundred dp.
 * Somewhere wide — an expanded or a modal rail — it is simply the field, because
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
 * @param windowInsets What the open panel keeps clear of; see [NavExpandingSlot].
 * @param interactionSource Hoisted, to observe the pill's press and hover.
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
    containerColour: Color = NavExpandingSlotDefaults.containerColour(),
    contentColour: Color = Theme.colours.content,
    onQuery: ((String) -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    windowInsets: WindowInsets = WindowInsets.allEdges,
    interactionSource: MutableInteractionSource? = null,
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
                shape = Theme.shapes.field,
            )
            results?.invoke(this)
        }
        return
    }

    val focus = remember { FocusRequester() }
    val typed = state.text.text.toString()
    // The field the pill grows into: its ground once it is focused, which it is
    // by the time the capsule arrives.
    val fieldColour = TextFieldDefaults.colours(TextFieldVariant.Filled).containerFocused

    NavExpandingSlot(
        expanded = state.expanded,
        onExpandedChange = { if (it) state.expand() else state.collapse() },
        modifier = modifier,
        enabled = enabled,
        placement = placement,
        containerColour = containerColour,
        contentColour = contentColour,
        interactionSource = interactionSource,
        windowInsets = windowInsets,
        expandedContent = {
            // The pill becomes the field: the capsule it leaves as ends where the
            // field is, in the field's colour, and the results come after.
            val morph = LocalNavMorph.current
            val fieldShape = Theme.shapes.field
            SideEffect {
                morph?.targetColour = fieldColour
                morph?.targetShape = fieldShape
            }

            if (placement == NavExpandPlacement.AboveKeyboard) ResultsPanel(results, morph)

            // Where the field draws its icon and starts its text: where the
            // capsule puts down the pill's.
            val parts = remember(morph) {
                morph?.let { FieldParts(icon = { at -> it.iconTo = at }, text = { at -> it.wordTo = at }) }
            }
            CompositionLocalProvider(LocalFieldParts provides parts) {
                SearchField(
                    state = state.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onPlaced { morph?.target = it },
                    placeholder = placeholder,
                    searchIcon = searchIcon,
                    onQuery = onQuery,
                    onSearch = onSearch,
                    shape = Theme.shapes.field,
                )
            }

            if (placement == NavExpandPlacement.Top) ResultsPanel(results, morph)

            // Inside the panel, so the field it focuses exists. Nothing else in
            // the overlay wants focus, but `trapFocus` would otherwise leave it
            // on the scrim with the keyboard shut — an expanded search you have
            // to tap a second time to type in. And let go of as the panel goes
            // back into the pill, so the keyboard goes with it rather than after.
            val focusManager = LocalFocusManager.current
            LaunchedEffect(state.expanded) {
                if (state.expanded) focus.requestFocus() else focusManager.clearFocus(force = true)
            }
        },
    ) {
        // The parts the capsule carries into the field: this icon and this word,
        // from where they are drawn here to where the field draws its own, and
        // from this pill's colours to the field's.
        val morph = LocalNavMorph.current
        val iconTint = if (enabled) Theme.colours.contentMuted else Theme.colours.contentDisabled
        if (searchIcon != null) {
            Icon(
                imageVector = searchIcon,
                contentDescription = null,
                size = Theme.sizing.iconMedium,
                tint = iconTint,
                modifier = Modifier.onPlaced { morph?.iconFrom = it },
            )
        }
        // The last query once there has been one. A pill that goes back to
        // saying "Search" after a search has run is a pill that looks like it
        // forgot. Skipped entirely where there is no room for a word — a rail at
        // 88dp gets the icon and nothing else. Derived, so a rail resizing
        // recomposes this once, at the point the word fits, not every frame.
        val room = LocalNavExpansion.current
        val worded by remember(room) { derivedStateOf { room.progress > NavSearchDefaults.LabelAt } }
        val word = typed.ifEmpty { placeholder }
        val wordStyle = Theme.typography.bodyMedium
        val wordColour = when {
            !enabled -> Theme.colours.contentDisabled
            typed.isEmpty() -> Theme.colours.contentMuted
            else -> contentColour
        }
        if (worded) {
            Text(
                text = word,
                style = wordStyle,
                colour = wordColour,
                maxLines = 1,
                modifier = Modifier.onPlaced { morph?.wordFrom = it },
            )
        }
        val fieldColours = TextFieldDefaults.colours(TextFieldVariant.Filled)
        val carried = CarriedParts(
            icon = searchIcon,
            iconColourFrom = iconTint,
            iconColourTo = fieldColours.label,
            // Nothing to carry from a pill too narrow for a word: the field's own
            // fades in where it is.
            word = if (worded) word else null,
            style = wordStyle,
            wordColourFrom = wordColour,
            wordColourTo = if (typed.isEmpty()) fieldColours.placeholder else fieldColours.content,
        )
        SideEffect { morph?.carried = carried }
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
private fun ColumnScope.ResultsPanel(results: (@Composable ColumnScope.() -> Unit)?, morph: NavMorph?) {
    if (results == null) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            // After the field: the last thing to arrive, and the first to go.
            .graphicsLayer { alpha = morph?.results ?: 1f },
        shape = Theme.shapes.panel,
        containerColour = Theme.colours.surface,
        border = contrastEdge(),
    ) {
        Column(
            modifier = Modifier.padding(Theme.spacing.md),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement
                .spacedBy(Theme.spacing.xs),
            content = results,
        )
    }
}
