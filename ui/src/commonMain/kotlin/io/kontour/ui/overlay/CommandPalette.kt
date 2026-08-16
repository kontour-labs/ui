package io.kontour.ui.overlay

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.components.display.Kbd
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * One thing a [CommandPalette] can run.
 *
 * @param keywords Extra words that should match this command without being
 *   shown — "prefs" finding "Settings". Search over labels alone makes a palette
 *   useless the moment the user's word for something is not the one on screen.
 */
@Immutable
data class Command(
    val id: String,
    val label: String,
    val onRun: () -> Unit,
    val icon: ImageVector? = null,
    val shortcut: String? = null,
    val group: String? = null,
    val keywords: List<String> = emptyList(),
    val enabled: Boolean = true,
)

/**
 * Search over *actions* rather than values.
 *
 * ```kotlin
 * CommandPalette(
 *     visible = paletteOpen,
 *     onDismissRequest = { paletteOpen = false },
 *     commands = listOf(
 *         Command("plan", "Plan a trip", onRun = ::plan, shortcut = "⌘P"),
 *         Command("saved", "Saved trips", onRun = ::openSaved, keywords = listOf("favourites")),
 *     ),
 * )
 * ```
 *
 * **Not a [io.kontour.ui.components.text.Combobox].** A combobox picks a *value*
 * and leaves it in a field; this runs something and closes. `Combobox`'s own
 * documentation declines this case, and the difference matters at the call site:
 * a palette has no value, no field to leave behind, and no meaning for "the
 * previous selection".
 *
 * ### The keyboard is the point
 *
 * Up and down move the highlight and wrap at both ends; Enter runs what is
 * highlighted; Escape dismisses. A palette that can only be operated by pointer
 * is a menu with extra steps — nobody opens one of these with the mouse.
 *
 * The highlight resets to the top on every keystroke. Typing is what shrinks
 * the list, so a remembered index is stale the instant it matters: highlight the
 * fifth command, type one more letter, and Enter runs whatever that index now
 * points at — or nothing at all. Resetting rather than clamping, because after a
 * keystroke the user is looking at the top of the results, not at wherever they
 * had arrowed to before the list changed under them.
 *
 * @param filter Decides what a query matches. The default is case-insensitive
 *   over the label and [Command.keywords].
 */
@Composable
fun CommandPalette(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    commands: List<Command>,
    modifier: Modifier = Modifier,
    key: Any = remember { Any() },
    query: TextFieldState = rememberTextFieldState(),
    placeholder: String = Theme.strings.commandPalettePlaceholder,
    emptyLabel: String = Theme.strings.noMatchingCommands,
    width: Dp = CommandPaletteDefaults.Width,
    maxHeight: Dp = CommandPaletteDefaults.MaxHeight,
    /**
     * How far below the top of the window it sits.
     *
     * A companion to [width] and [maxHeight], and here for the same reason they
     * are: the defaults are sized for a real window, and a host that is not one
     * needs all three or none of them. Without it the catalog's 300dp panel
     * spent a third of its height on the inset and clipped the palette at the
     * bottom, which read as the component being broken.
     */
    topInset: Dp = CommandPaletteDefaults.TopInset,
    filter: (String, Command) -> Boolean = ::commandMatches,
) {
    val host = LocalOverlayHost.current
    val latestModifier by rememberUpdatedState(modifier)
    val latestCommands by rememberUpdatedState(commands)
    val latestFilter by rememberUpdatedState(filter)
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val latestTopInset by rememberUpdatedState(topInset)

    LaunchedEffect(visible, key) {
        if (visible) {
            host.show(
                OverlayEntry(
                    key = key,
                    layer = OverlayLayer.Dialog,
                    scrim = ScrimStyle.Dimmed,
                    dismissOnOutside = true,
                    dismissLabel = "Dismiss",
                    trapFocus = true,
                    onDismiss = { latestDismiss() },
                    content = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .overlayAppearance(
                                    LocalOverlayProgress.current,
                                    fromScale = 0.96f,
                                )
                                .windowInsetsPadding(WindowInsets.allEdges),
                            // Near the top rather than centred: the list grows
                            // downward as the user types, and a centred palette
                            // jumps up the screen on every keystroke.
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            PaletteBody(
                                modifier = latestModifier.padding(top = latestTopInset),
                                query = query,
                                commands = latestCommands,
                                filter = latestFilter,
                                placeholder = placeholder,
                                emptyLabel = emptyLabel,
                                width = width,
                                maxHeight = maxHeight,
                                onDismissRequest = { latestDismiss() },
                            )
                        }
                    },
                )
            )
        } else {
            host.hide(key)
        }
    }
}

@Composable
private fun PaletteBody(
    modifier: Modifier,
    query: TextFieldState,
    commands: List<Command>,
    filter: (String, Command) -> Boolean,
    placeholder: String,
    emptyLabel: String,
    width: Dp,
    maxHeight: Dp,
    onDismissRequest: () -> Unit,
) {
    val text = query.text.toString()
    val matches = remember(text, commands) {
        if (text.isBlank()) commands else commands.filter { filter(text, it) }
    }

    var highlighted by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }

    // Typing is what shrinks the list, and a remembered index outlives the row
    // it pointed at. Reset rather than clamp: after a keystroke the user's
    // attention is on the top of the results, not on wherever they had arrowed
    // to before the list changed under them.
    LaunchedEffect(text) { highlighted = 0 }

    LaunchedEffect(Unit) { focus.requestFocus() }

    // Keep the highlighted row on screen when the arrows walk past the fold.
    LaunchedEffect(highlighted) {
        if (matches.isNotEmpty()) listState.animateScrollToItem(highlighted.coerceIn(matches.indices))
    }

    fun run(index: Int) {
        val command = matches.getOrNull(index) ?: return
        if (!command.enabled) return
        command.onRun()
        onDismissRequest()
    }

    Surface(
        modifier = modifier
            .widthIn(max = width)
            .fillMaxWidth()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        if (matches.isNotEmpty()) {
                            highlighted = (highlighted + 1) % matches.size
                        }
                        true
                    }

                    Key.DirectionUp -> {
                        if (matches.isNotEmpty()) {
                            // `+ size` before the modulo: Kotlin's `%` keeps the
                            // sign of the left operand, so `-1 % 5` is `-1` and
                            // arrowing up from the first row lands out of bounds
                            // rather than at the last.
                            highlighted = (highlighted - 1 + matches.size) % matches.size
                        }
                        true
                    }

                    Key.Enter, Key.NumPadEnter -> {
                        run(highlighted)
                        true
                    }

                    Key.Escape -> {
                        onDismissRequest()
                        true
                    }

                    else -> false
                }
            },
        shape = Theme.shapes.medium,
        color = Theme.colors.surface,
        shadow = Theme.elevation.overlay,
    ) {
        Column {
            SearchField(
                state = query,
                placeholder = placeholder,
                modifier = Modifier.fillMaxWidth().padding(Theme.spacing.xs),
                // No debounce. A palette filters a list already in memory, and
                // a quarter-second lag between the key and the result is the
                // whole difference between this feeling instant and feeling
                // broken. The debounce exists for fields that hit the network.
                debounceMillis = 0L,
            )

            if (matches.isEmpty()) {
                Text(
                    text = emptyLabel,
                    style = Theme.typography.bodyMedium,
                    color = Theme.colors.contentMuted,
                    modifier = Modifier.padding(Theme.spacing.md),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = maxHeight),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Theme.spacing.xs,
                        vertical = Theme.spacing.xxs,
                    ),
                ) {
                    items(matches, key = { it.id }) { command ->
                        val index = matches.indexOf(command)
                        ListItem(
                            onClick = { run(index) },
                            enabled = command.enabled,
                            selected = index == highlighted,
                            position = ListItemPosition.Only,
                        ) {
                            command.icon?.let { icon -> leading { +icon } }
                            +command.label
                            command.shortcut?.let { shortcut -> trailing { Kbd { +shortcut } } }
                        }
                    }
                }
            }
        }
    }
}

/** Case-insensitive over the label and [Command.keywords]. */
fun commandMatches(query: String, command: Command): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    if (command.label.lowercase().contains(needle)) return true
    return command.keywords.any { it.lowercase().contains(needle) }
}

object CommandPaletteDefaults {
    /** Wide enough for a command and its shortcut, narrow enough to read. */
    val Width: Dp = 560.dp

    /**
     * How tall the results get before they scroll.
     *
     * A palette that grows to fill the screen stops being a palette. Ten rows is
     * more than anyone reads before typing another letter.
     */
    val MaxHeight: Dp = 360.dp

    /**
     * How far down the screen it sits.
     *
     * Not centred: the list grows downward as the user types, and a centred
     * palette jumps up the screen on every keystroke.
     */
    val TopInset: Dp = 96.dp
}
