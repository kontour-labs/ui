package io.kontour.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.adaptive.sheetEdges
import io.kontour.ui.adaptive.topEdges
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.theme.Theme

/** Where an expanded [NavSearch] goes. */
enum class NavSearchPlacement {
    /**
     * Over the keyboard, where the finger already is.
     *
     * The field stays near the thumb that opened it and the results fill the
     * screen above it, which is the arrangement of every map app's search. Its
     * cost is that the results run *up* from the field, so the first one is
     * furthest from the eye's resting point.
     */
    AboveKeyboard,

    /**
     * At the top of the screen, keyboard below it.
     *
     * Results read downwards from the field in the order a list is normally
     * read, and the field ends up where a browser or a settings screen puts one.
     * Its cost is the reach: the field travels the height of the screen away
     * from the thumb that tapped it.
     */
    Top,
}

/** Metrics for [NavSearch]. */
object NavSearchDefaults {
    /** How far the expanded field sits in from the window's sides. */
    val Margin = 16.dp
}

/**
 * The collapsed pill's state, and whether it is currently expanded.
 *
 * Hoisted rather than internal so a screen can open the search from somewhere
 * else — a keyboard shortcut, a "search this area" button on a map — and so the
 * query survives being collapsed and reopened.
 */
@Stable
class NavSearchState internal constructor(
    /** What the user has typed. Shared by the pill and the expanded field. */
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
 * A search field that lives in a [NavBar] and expands to fill the screen.
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
 * ### Expanded, it is an overlay rather than a taller bar
 *
 * A navigation bar clears `WindowInsets.bottomEdges`, whose own documentation
 * says why: *"A navigation bar holds no text field and should stay where it is
 * while the user types."* That is right, and it is also the reason the expanded
 * field cannot be a state of the bar — a bar riding up on the keyboard would
 * contradict the inset it asks for. So the pill stays where it is and the
 * expanded field is pushed into the [io.kontour.ui.overlay.OverlayHost], the
 * same way [io.kontour.ui.overlay.CommandPalette] does, where it is free to take
 * the keyboard inset ([NavSearchPlacement.AboveKeyboard]) or the status bar's
 * ([NavSearchPlacement.Top]) without the bar knowing anything about it.
 *
 * That also gets the rest for nothing: a scrim over the content, back and
 * escape closing it, focus trapped inside it, and the overlay's own entrance.
 *
 * @param placement Where the expanded field goes. Both are built because which
 *   one an app wants is a question about that app, not about this component.
 * @param results Shown under the expanded field. Given a `ColumnScope`, so a
 *   caller can weight a list to fill what is left.
 */
@Composable
fun NavSearch(
    state: NavSearchState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placement: NavSearchPlacement = NavSearchPlacement.AboveKeyboard,
    placeholder: String = Theme.strings.search,
    /**
     * Beside the placeholder. `null` for none, matching
     * [io.kontour.ui.components.text.SearchField] — the library owns no
     * magnifier glyph, because an icon set is the app's choice.
     */
    searchIcon: ImageVector? = null,
    onQuery: ((String) -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    results: (@Composable ColumnScope.() -> Unit)? = null,
) {
    CollapsedSearch(
        state = state,
        modifier = modifier,
        enabled = enabled,
        placeholder = placeholder,
        searchIcon = searchIcon,
    )

    ExpandedSearch(
        state = state,
        placement = placement,
        placeholder = placeholder,
        onQuery = onQuery,
        onSearch = onSearch,
        results = results,
    )
}

/**
 * The pill in the bar.
 *
 * A button that looks like a field, not a field. A real one here would take
 * focus and raise the keyboard *inside the navigation bar*, which is the thing
 * the expansion exists to avoid — and it would need its own handling for every
 * way a field can be reached, of which tapping is only the most obvious.
 * `Role.Button` is what it is: pressing it opens something.
 */
@Composable
private fun CollapsedSearch(
    state: NavSearchState,
    modifier: Modifier,
    enabled: Boolean,
    placeholder: String,
    searchIcon: ImageVector?,
) {
    val colors = Theme.colors
    val feedback = LocalFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val shape = Theme.shapes.pill
    val typed = state.text.text.toString()

    Surface(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape, enabled = enabled)
            .clickable(
                interactionSource = interactions,
                indication = kontourIndication(shape, pressScale = 1f),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    state.expand()
                },
            ),
        shape = shape,
        color = colors.surfaceSunken,
        contentColor = if (enabled) colors.content else colors.contentDisabled,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = Theme.sizing.controlHeightMedium)
                .padding(horizontal = Theme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchIcon != null) {
                Icon(
                    imageVector = searchIcon,
                    contentDescription = null,
                    size = Theme.sizing.iconMedium,
                    tint = if (enabled) colors.contentMuted else colors.contentDisabled,
                )
            }
            Text(
                // What the user last searched for, once they have searched for
                // something. A pill that goes back to saying "Search" after a
                // query has been run is a pill that looks like it forgot.
                text = typed.ifEmpty { placeholder },
                style = Theme.typography.bodyMedium,
                color = when {
                    !enabled -> colors.contentDisabled
                    typed.isEmpty() -> colors.contentMuted
                    else -> colors.content
                },
                maxLines = 1,
            )
        }
    }
}

/** The expanded field, in the overlay host. */
@Composable
private fun ExpandedSearch(
    state: NavSearchState,
    placement: NavSearchPlacement,
    placeholder: String,
    onQuery: ((String) -> Unit)?,
    onSearch: ((String) -> Unit)?,
    results: (@Composable ColumnScope.() -> Unit)?,
) {
    val host = LocalOverlayHost.current
    val focus = remember { FocusRequester() }
    val latestPlacement by rememberUpdatedState(placement)
    val latestPlaceholder by rememberUpdatedState(placeholder)
    val latestQuery by rememberUpdatedState(onQuery)
    val latestSearch by rememberUpdatedState(onSearch)
    val latestResults by rememberUpdatedState(results)
    val dismissLabel = Theme.strings.close

    LaunchedEffect(state.expanded, state) {
        if (!state.expanded) {
            host.hide(state)
            return@LaunchedEffect
        }

        host.show(
            OverlayEntry(
                // The state object itself, so two searches on one screen cannot
                // collide and the same one reopening replaces rather than
                // stacks.
                key = state,
                layer = OverlayLayer.Dialog,
                scrim = ScrimStyle.Dimmed,
                dismissOnOutside = true,
                dismissOnBack = true,
                trapFocus = true,
                dismissLabel = dismissLabel,
                onDismiss = { state.collapse() },
                content = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                // The whole point of the two placements. A field
                                // over the keyboard has to clear the keyboard,
                                // which is `sheetEdges`; one at the top has to
                                // clear the status bar and the cutout, which is
                                // `topEdges`. Both tokens already exist and
                                // already mean this.
                                when (latestPlacement) {
                                    NavSearchPlacement.AboveKeyboard ->
                                        WindowInsets.sheetEdges
                                    NavSearchPlacement.Top -> WindowInsets.topEdges
                                }
                            )
                            .padding(NavSearchDefaults.Margin),
                        contentAlignment = when (latestPlacement) {
                            NavSearchPlacement.AboveKeyboard -> Alignment.BottomCenter
                            NavSearchPlacement.Top -> Alignment.TopCenter
                        },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                        ) {
                            // Results above the field when the field is at the
                            // bottom, below it when it is at the top. Either way
                            // they are on the side of it with the room.
                            if (latestPlacement == NavSearchPlacement.AboveKeyboard) {
                                ResultsPanel(latestResults)
                            }

                            SearchField(
                                state = state.text,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focus),
                                placeholder = latestPlaceholder,
                                onQuery = latestQuery,
                                onSearch = latestSearch,
                                shape = Theme.shapes.pill,
                            )

                            if (latestPlacement == NavSearchPlacement.Top) {
                                ResultsPanel(latestResults)
                            }
                        }
                    }
                },
            )
        )

        // After `show`, so the field exists to be focused. Nothing else in the
        // overlay wants focus, but `trapFocus` would otherwise leave it on the
        // scrim and the keyboard closed — an expanded search you have to tap a
        // second time to type in.
        focus.requestFocus()
    }
}

/**
 * The results, on a surface of their own.
 *
 * The caller supplies the contents; the panel under them belongs to the
 * component, because the alternative is every caller discovering for themselves
 * that plain text on a dimmed page is barely legible. A `Column` so the slot can
 * weight a list to fill what is left, which is the usual shape of a result set.
 */
@Composable
private fun ColumnScope.ResultsPanel(results: (@Composable ColumnScope.() -> Unit)?) {
    if (results == null) return
    Surface(
        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        shape = Theme.shapes.large,
        color = Theme.colors.surface,
    ) {
        Column(
            modifier = Modifier.padding(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            content = results,
        )
    }
}
