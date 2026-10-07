package io.kontour.ui.catalog.apps.messages

import androidx.compose.foundation.lazy.rememberLazyListState
import io.kontour.ui.components.list.scrollbar
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Archive
import io.kontour.icons.tabler.outline.Checks
import io.kontour.icons.tabler.outline.Dots
import io.kontour.icons.tabler.outline.Download
import io.kontour.icons.tabler.outline.Filter
import io.kontour.icons.tabler.outline.Keyboard
import io.kontour.icons.tabler.outline.MessageCircle
import io.kontour.icons.tabler.outline.Pin
import io.kontour.icons.tabler.outline.PinnedOff
import io.kontour.icons.tabler.outline.Search
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.display.Avatar
import io.kontour.ui.components.display.Badge
import io.kontour.ui.components.display.BadgedBox
import io.kontour.ui.components.display.EmptyState
import io.kontour.ui.components.display.Kbd
import io.kontour.ui.components.display.KbdIcons
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.list.listSection
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.shape
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.nav.topBarCollapse
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.LoadingOverlay
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.Popover
import io.kontour.ui.overlay.ToastHostState
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The chat list: the app's first page.
 *
 * Most of what the gallery cannot show on a card is here because a list of
 * conversations is where it belongs — rows that swipe, a badge on a face, a
 * filter in a sheet, a menu whose items start something that takes a moment.
 *
 * @param selectedChatId The chat open beside the list, drawn selected. Null on
 *   a phone, where the conversation covers the list and nothing beside it is
 *   open.
 * @param navigation The top bar's leading slot: the way out of the app, on the
 *   app's first page.
 * @param contentPadding What the list keeps clear of — a navigation bar over
 *   its foot — taken as content padding, so the rows scroll under the bar and
 *   the last one can still be scrolled clear of it.
 */
@Composable
internal fun ChatsPage(
    state: MessagesState,
    toasts: ToastHostState,
    selectedChatId: String?,
    onOpenChat: (String) -> Unit,
    onSearch: () -> Unit,
    navigation: (@Composable () -> Unit)?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val collapse = rememberTopBarCollapseState()
    var filtersOpen by remember { mutableStateOf(false) }
    val filter = state.chatFilter
    val chats = state.chats
    val shown = chats.filter { filter.matches(it, state.last(it.id)) }
    val unread = state.unreadTotal

    Column(modifier.fillMaxSize().topBarCollapse(collapse)) {
        TopBar(
            style = TopBarStyle.Large,
            collapseState = collapse,
            navigation = navigation,
            actions = {
                IconButton(onClick = onSearch, icon = Tabler.Outline.Search, contentDescription = "Search")
                // A dot rather than a count: how many filters are on is not the
                // question, whether the list is the whole list is.
                if (filter.active) {
                    BadgedBox(badge = { Badge(contentDescription = "Filters on") }) {
                        FilterButton { filtersOpen = true }
                    }
                } else {
                    FilterButton { filtersOpen = true }
                }
                ChatsMenu(state, toasts)
            },
        ) {
            +"Chats"
            supporting { +if (unread == 0) "All caught up" else "$unread unread" }
        }

        val list = rememberLazyListState()
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().scrollbar(list, contentPadding = contentPadding),
            contentPadding = contentPadding.plusPage(),
        ) {
            when {
                shown.isEmpty() -> item(key = "empty") {
                    NoChats(filtered = chats.isNotEmpty(), onClear = { state.chatFilter = ChatFilter() })
                }
                else -> {
                    val (pinned, rest) = shown.partition { it.pinned }
                    chatSection("Pinned", pinned, state, toasts, selectedChatId, onOpenChat)
                    chatSection(if (pinned.isEmpty()) null else "All chats", rest, state, toasts, selectedChatId, onOpenChat)
                }
            }
        }
    }

    ChatFiltersSheet(visible = filtersOpen, onDismissRequest = { filtersOpen = false }, state = state)
}

/** The Filter action, drawn alone or under its dot. */
@Composable
private fun FilterButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, icon = Tabler.Outline.Filter, contentDescription = "Filter")
}

/**
 * A run of rows under a heading, grouped so their outer corners round and the
 * seams between them stay square. [title] null draws the rows with no heading.
 */
private fun LazyListScope.chatSection(
    title: String?,
    chats: List<Chat>,
    state: MessagesState,
    toasts: ToastHostState,
    selectedChatId: String?,
    onOpenChat: (String) -> Unit,
) {
    listSection(
        chats,
        key = { it.id },
        sectionKey = title ?: "chats",
        title = if (title != null) {
            { +title }
        } else {
            null
        },
    ) { chat, position ->
        ChatRow(
            chat = chat,
            last = state.last(chat.id),
            selected = chat.id == selectedChatId,
            position = position,
            onOpen = { onOpenChat(chat.id) },
            onPin = { state.togglePin(chat.id) },
            onArchive = {
                state.archive(chat.id)
                toasts.show(
                    "Archived ${chat.name}",
                    actionLabel = "Undo",
                    onAction = { state.unarchive(chat.id) },
                )
            },
        )
    }
}

/**
 * One conversation: who, the last thing said, and when.
 *
 * Pin from the leading edge and archive from the trailing one, as the mail and
 * messaging apps people already use have them — start and end, so in a
 * right-to-left layout both trade sides along with everything else. The swipe
 * takes the row's own grouped shape, so a row mid-swipe is still the row.
 */
@Composable
private fun ChatRow(
    chat: Chat,
    last: Message?,
    selected: Boolean,
    position: GroupPosition,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeActions(
        modifier = modifier,
        start = listOf(
            SwipeAction(
                label = if (chat.pinned) "Unpin" else "Pin",
                icon = if (chat.pinned) Tabler.Outline.PinnedOff else Tabler.Outline.Pin,
                onAction = onPin,
                background = Theme.colours.accent.solid,
            ),
        ),
        end = listOf(
            SwipeAction(
                label = "Archive",
                icon = Tabler.Outline.Archive,
                onAction = onArchive,
                background = Theme.colours.warning.solid,
            ),
        ),
        shape = position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical),
    ) {
        ListItem(
            modifier = Modifier.testTag("chat-${chat.id}"),
            onClick = onOpen,
            selected = selected,
            position = position,
        ) {
            leading {
                if (chat.unread > 0) {
                    BadgedBox(badge = { Badge(count = chat.unread, contentDescription = "${chat.unread} unread") }) {
                        Avatar(name = chat.name)
                    }
                } else {
                    Avatar(name = chat.name)
                }
            }
            +chat.name
            if (last != null) {
                supporting { Text(last.preview(chat), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                trailing { +last.whenLabel() }
            }
        }
    }
}

/** The last message as the row shows it: whose, in a group or from you, then what. */
private fun Message.preview(chat: Chat): String = when {
    fromMe -> "You: $text"
    chat.kind == ChatKind.Group -> "${sender.substringBefore(' ')}: $text"
    else -> text
}

/**
 * The list with nothing in it, and the way back to something.
 *
 * Two different empties: filters that match nothing, which one button undoes,
 * and every chat archived, which nothing in this app undoes — so that one says
 * so rather than offering a button that would not help.
 */
@Composable
private fun NoChats(filtered: Boolean, onClear: () -> Unit) {
    EmptyState(Modifier.fillMaxWidth().padding(top = Theme.spacing.xl)) {
        if (filtered) {
            +"No chats match"
            message { +"Nothing fits every filter at once. Widen the dates, or clear the filters." }
            leading { +Tabler.Outline.Filter }
            action {
                Button(onClick = onClear, variant = ButtonVariant.Secondary) { +"Clear filters" }
            }
        } else {
            +"No chats"
            message { +"Everything is archived. New messages will bring a chat back." }
            leading { +Tabler.Outline.MessageCircle }
        }
    }
}

/**
 * The overflow menu, and the two things it starts that outlive it: an export
 * behind a loading overlay, and a popover of keyboard shortcuts.
 *
 * Both hang off the menu's own button, so the popover points at what opened
 * it once the menu has gone.
 */
@Composable
private fun ChatsMenu(state: MessagesState, toasts: ToastHostState) {
    var open by remember { mutableStateOf(false) }
    var shortcutsOpen by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val count = state.chats.size

    // Nothing is written anywhere: the export is the wait and the word that it
    // is done, which is the part the overlay is for.
    LaunchedEffect(exporting) {
        if (exporting) {
            delay(ExportTime)
            exporting = false
            toasts.show("Exported $count chats to Downloads", tone = Tone.Success)
        }
    }

    Box {
        IconButton(onClick = { open = !open }, icon = Tabler.Outline.Dots, contentDescription = "More")
        DropdownMenu(visible = open, onDismissRequest = { open = false }, alignment = OverlayAlignment.End) {
            item(
                "Mark all read",
                leadingIcon = Tabler.Outline.Checks,
                enabled = state.unreadTotal > 0,
                onClick = {
                    state.markAllRead()
                    toasts.show("All caught up")
                },
            )
            item("Export chats", leadingIcon = Tabler.Outline.Download, onClick = { exporting = true })
            divider()
            item("Keyboard shortcuts", leadingIcon = Tabler.Outline.Keyboard, onClick = { shortcutsOpen = true })
        }
        Popover(
            visible = shortcutsOpen,
            onDismissRequest = { shortcutsOpen = false },
            alignment = OverlayAlignment.End,
            title = { +"Keyboard shortcuts" },
        ) {
            Shortcut("Command palette") {
                Kbd { +KbdIcons.Command }
                Kbd { +"K" }
                Text("or", style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
                Kbd { +"Ctrl" }
                Kbd { +"K" }
            }
            Shortcut("Send a message") { Kbd { +KbdIcons.Return } }
            Shortcut("Close a sheet or the palette") { Kbd { +"Esc" } }
            Shortcut("Move through a list") {
                Kbd { +"↑" }
                Kbd { +"↓" }
            }
        }
    }
    LoadingOverlay(visible = exporting, label = "Exporting $count chats")
}

/** One line of the shortcuts popover: the keys, then what they do. */
@Composable
private fun Shortcut(label: String, keys: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Theme.spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys()
        Text(label, modifier = Modifier.padding(start = Theme.spacing.xxs), style = Theme.typography.bodySmall)
    }
}

/** How long the pretend export takes: long enough to see the overlay, short enough not to resent it. */
private val ExportTime = 1_500.milliseconds

/**
 * This padding, with a page's margins added: the sides, a little at the top
 * and a little more past the last row.
 *
 * Start and end read in the layout direction rather than as left and right, so
 * a rail on the leading edge of a right-to-left window still pads the right
 * side.
 */
@Composable
internal fun PaddingValues.plusPage(top: Dp = Theme.spacing.xs, bottom: Dp = Theme.spacing.xl): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + Theme.spacing.md,
        end = calculateEndPadding(direction) + Theme.spacing.md,
        top = calculateTopPadding() + top,
        bottom = calculateBottomPadding() + bottom,
    )
}
