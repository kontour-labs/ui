package io.kontour.ui.catalog.apps.messages

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Archive
import io.kontour.icons.tabler.outline.Checks
import io.kontour.icons.tabler.outline.Logout
import io.kontour.icons.tabler.outline.MessageCircle
import io.kontour.icons.tabler.outline.Note
import io.kontour.icons.tabler.outline.Notes
import io.kontour.icons.tabler.outline.Plus
import io.kontour.icons.tabler.outline.Search
import io.kontour.ui.components.display.EmptyState
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.NavSearch
import io.kontour.ui.nav.NavSearchState
import io.kontour.ui.overlay.Command
import io.kontour.ui.theme.Theme

// The parts of the app that are not a page: the search in the navigation, the
// command palette's commands and the shortcut that opens it, and what an empty
// pane says.
//
// Kept apart from the wiring in `MessagesApp`, which only puts them where they
// go, so each can be read — and run — without the stack around it.

/**
 * Search over everything, in the navigation suite: in the bar on a phone, at
 * the head of the rail on anything wider.
 *
 * Results as they are typed, chats then notes, a few of each; Enter opens the
 * first. Chats match on a name or anything said in them, notes on a title or
 * anything written in them — the words people remember are rarely the name.
 */
@Composable
internal fun MessagesSearch(
    state: MessagesState,
    search: NavSearchState,
    onOpenChat: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query = search.text.text.toString().trim()
    val chats = if (query.isEmpty()) emptyList() else state.chats.filter { chat ->
        chat.name.contains(query, ignoreCase = true) ||
            state.thread(chat.id).any { it.text.contains(query, ignoreCase = true) }
    }
    val notes = if (query.isEmpty()) emptyList() else state.notes.filter {
        it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
    }
    val openChat = { id: String ->
        search.collapse(clear = true)
        onOpenChat(id)
    }
    val openNote = { id: String ->
        search.collapse(clear = true)
        onOpenNote(id)
    }

    NavSearch(
        state = search,
        modifier = modifier,
        placeholder = "Search chats and notes",
        searchIcon = Tabler.Outline.Search,
        onSearch = {
            chats.firstOrNull()?.let { openChat(it.id) } ?: notes.firstOrNull()?.let { openNote(it.id) }
        },
        // Nothing under the field until something is typed: in a sidebar the
        // field sits in place all the time, and a standing hint under it is a
        // line of the rail spent saying nothing.
        results = if (query.isEmpty()) {
            null
        } else {
            { SearchResults(state, query, chats, notes, openChat, openNote) }
        },
    )
}

/** What a search found, chats first, or a line saying it found nothing. */
@Composable
private fun SearchResults(
    state: MessagesState,
    query: String,
    chats: List<Chat>,
    notes: List<Note>,
    onOpenChat: (String) -> Unit,
    onOpenNote: (String) -> Unit,
) {
    if (chats.isEmpty() && notes.isEmpty()) {
        Hint("Nothing matches “$query”.")
        return
    }
    ListGroup {
        for (chat in chats.take(MaxResults)) {
            // The line that matched, when it was something said rather than
            // the name: it is why the chat is in the results.
            val said = state.thread(chat.id).lastOrNull { it.text.contains(query, ignoreCase = true) }
            item(
                label = chat.name,
                supporting = said?.text ?: "Chat",
                icon = Tabler.Outline.MessageCircle,
                onClick = { onOpenChat(chat.id) },
            )
        }
        for (note in notes.take(MaxResults)) {
            item(label = note.title, supporting = "Note", icon = Tabler.Outline.Note, onClick = { onOpenNote(note.id) })
        }
    }
}

/** A line of muted text where the search has no results to show. */
@Composable
private fun Hint(text: String) {
    Text(text, style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
}

/** How many chats, and how many notes, a search lists before it stops. */
private const val MaxResults = 4

/**
 * What the command palette can do from anywhere in the app.
 *
 * Every chat is a command of its own — "Go to Ari Ngata" — so the palette is
 * also the fastest way between conversations: ⌘K, three letters, Enter. The
 * keywords are the people in a group, so "Kai" finds the crew as well as Kai.
 *
 * @param openChatId The conversation on screen, which Archive acts on; null
 *   leaves Archive in the list but unavailable, so it is found and explains
 *   itself rather than appearing and vanishing.
 * @param onLeave Leaves the app; null where nothing is hosting it to leave to.
 */
internal fun paletteCommands(
    state: MessagesState,
    openChatId: String?,
    onNewNote: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenNotes: () -> Unit,
    onArchive: (String) -> Unit,
    onLeave: (() -> Unit)?,
): List<Command> = buildList {
    val open = openChatId?.let(state::chat)
    add(Command("new-note", "New note", onAction = onNewNote, icon = Tabler.Outline.Plus, group = "Actions"))
    add(
        Command(
            "archive",
            if (open != null) "Archive ${open.name}" else "Archive this chat",
            onAction = { if (open != null) onArchive(open.id) },
            icon = Tabler.Outline.Archive,
            group = "Actions",
            enabled = open != null,
        ),
    )
    add(
        Command(
            "mark-read", "Mark all read",
            onAction = state::markAllRead,
            icon = Tabler.Outline.Checks,
            group = "Actions",
            enabled = state.unreadTotal > 0,
        ),
    )
    for (chat in state.chats) {
        add(
            Command(
                "chat-${chat.id}", "Go to ${chat.name}",
                onAction = { onOpenChat(chat.id) },
                icon = Tabler.Outline.MessageCircle,
                group = "Go to chat",
                keywords = chat.members,
            ),
        )
    }
    add(Command("notes", "Go to Notes", onAction = onOpenNotes, icon = Tabler.Outline.Notes, group = "Go to"))
    if (onLeave != null) {
        add(
            Command(
                "leave", "Leave app",
                onAction = onLeave,
                icon = Tabler.Outline.Logout,
                group = "Go to",
                keywords = listOf("quit", "exit", "close", "gallery"),
            ),
        )
    }
}

/**
 * Holds [content] and opens the palette on ⌘K, or Ctrl+K.
 *
 * Both chords, on every platform, rather than the one each platform would
 * pick: the gallery runs on a Mac and a PC alike, and a hand that knows one
 * should not have to find out which this is.
 *
 * A preview handler on a focusable root, so it hears the chord before
 * whatever has focus — the composer's field, a row — and the root takes focus
 * when it arrives, so the chord works before anything has been clicked.
 */
@Composable
internal fun ShortcutRoot(onPalette: () -> Unit, content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val chord = event.type == KeyEventType.KeyDown &&
                    event.key == Key.K &&
                    (event.isMetaPressed || event.isCtrlPressed)
                if (chord) onPalette()
                chord
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        content()
    }
    LaunchedEffect(focus) { focus.requestFocus() }
}

/**
 * What the detail pane says before anything is picked, beside a list on a
 * wide window. A phone never draws it.
 */
@Composable
internal fun PanePlaceholder(icon: ImageVector, title: String, message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(Modifier.padding(Theme.spacing.lg)) {
            +title
            message { +message }
            leading { +icon }
        }
    }
}
