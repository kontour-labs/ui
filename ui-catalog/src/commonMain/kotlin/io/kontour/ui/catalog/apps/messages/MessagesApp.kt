package io.kontour.ui.catalog.apps.messages

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Messages
import com.composables.icons.tabler.outline.Notes
import io.kontour.ui.adaptive.sheetEdges
import io.kontour.ui.adaptive.windowSizeClass
import io.kontour.ui.catalog.apps.LeaveAppButton
import io.kontour.ui.catalog.apps.LocalLeaveApp
import io.kontour.ui.components.display.Badge
import io.kontour.ui.motion.pageBack
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.navigationSuitePadding
import io.kontour.ui.nav.rememberNavSearchState
import io.kontour.ui.nav3.coversNavigation
import io.kontour.ui.nav3.detailPane
import io.kontour.ui.nav3.listPane
import io.kontour.ui.nav3.rememberListDetailSceneStrategy
import io.kontour.ui.nav3.rememberNavigationSuiteSceneStrategy
import io.kontour.ui.nav3.rememberPageTransitionStrategy
import io.kontour.ui.overlay.CommandPalette
import io.kontour.ui.overlay.ToastHost
import io.kontour.ui.overlay.rememberToastHostState

// The app's places. Plain objects and data classes: the stack is held in
// memory for as long as the app is open, and a gallery app is not restored
// after the process dies, so there is nothing to serialise them for.

private data object ChatsRoute
private data class ConversationRoute(val chatId: String)
private data class CallRoute(val chatId: String)
private data object NotesRoute
private data class NoteRoute(val noteId: String, val editing: Boolean = false)

/**
 * Messages & Notes: a messaging app, and a notebook beside it.
 *
 * A gallery page shows one component at a time on a card. This shows them
 * where an app puts them, together, and moving between pages — so the parts
 * that only mean anything in an app have somewhere to be seen: a list beside
 * its detail that becomes a stack on a phone, a bar that becomes a rail, a
 * page that covers the navigation, rows that swipe, a palette on ⌘K, sheets
 * that open from whichever side suits the window.
 *
 * One `NavDisplay` holds every page. The navigation suite is a scene strategy
 * around the others, so the call can leave it out; the list and its detail are
 * a second, which lays them side by side from a medium window up and stacks
 * them below that. Choosing a tab replaces the root of the stack, so back
 * from the root of either tab leaves the app.
 */
@Composable
internal fun MessagesApp() {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { MessagesState(scope) }
    // The app's own toasts rather than the host's: archiving offers Undo, and
    // a toast with an action is the library's to give, not the host's.
    val toasts = rememberToastHostState()
    val search = rememberNavSearchState()
    val backStack = remember { mutableStateListOf<Any>(ChatsRoute) }
    var paletteOpen by remember { mutableStateOf(false) }
    val leave = LocalLeaveApp.current?.leave
    val twoPane = windowSizeClass.width.hasRoomBeside

    // A tab's root replaces the old one in place, and whatever was above it
    // goes: the stack is never empty, not even for a moment between the two.
    val showRoot: (Any) -> Unit = { root ->
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack[0] = root
    }
    // Opening a chat or a note replaces the detail rather than piling onto
    // it, as picking from a list does — and from the other tab, goes there.
    val openChat: (String) -> Unit = { id ->
        showRoot(ChatsRoute)
        backStack += ConversationRoute(id)
    }
    val openNote: (String) -> Unit = { id ->
        showRoot(NotesRoute)
        backStack += NoteRoute(id)
    }
    val newNote: () -> Unit = {
        showRoot(NotesRoute)
        backStack += NoteRoute(state.addNote(), editing = true)
    }
    val archive: (String) -> Unit = { id ->
        val name = state.chat(id)?.name.orEmpty()
        state.archive(id)
        showRoot(ChatsRoute)
        toasts.show("Archived $name", actionLabel = "Undo", onAction = { state.unarchive(id) })
    }

    // The list and its detail are pages *inside* the navigation, so the
    // list-detail strategy is the suite's own: listed beside the suite instead,
    // it never got a turn — the suite takes every entry first — and a wide
    // window showed the conversation in place of the list.
    val suite = rememberNavigationSuiteSceneStrategy<Any>(
        sceneStrategies = listOf(rememberListDetailSceneStrategy<Any>(twoPane = twoPane)),
    ) { pages ->
        // Read here, inside the strategy's scaffold, so the marker and the
        // badge follow the stack and the count as they change.
        val tab = backStack.first()
        val unread = state.unreadTotal
        NavigationSuiteScaffold(
            destinations = {
                item(
                    selected = tab == ChatsRoute,
                    onClick = { showRoot(ChatsRoute) },
                    key = "chats",
                    icon = Tabler.Outline.Messages,
                    modifier = Modifier.testTag("tab-chats"),
                    badge = if (unread > 0) {
                        { Badge(count = unread, contentDescription = "$unread unread") }
                    } else {
                        null
                    },
                ) { +"Chats" }
                item(
                    selected = tab == NotesRoute,
                    onClick = { showRoot(NotesRoute) },
                    key = "notes",
                    icon = Tabler.Outline.Notes,
                    modifier = Modifier.testTag("tab-notes"),
                ) { +"Notes" }
            },
            // In the bar on a phone; a rail's search is its header's to hold.
            search = { MessagesSearch(state, search, openChat, openNote) },
            header = { MessagesSearch(state, search, openChat, openNote) },
        ) { padding ->
            pages()
            // Above the bar on a phone, not under it: a toast's insets know
            // the gesture bar and the keyboard, and the navigation bar is a
            // component, so it is added here.
            ToastHost(
                toasts,
                windowInsets = WindowInsets.sheetEdges.union(WindowInsets(bottom = padding.calculateBottomPadding())),
            )
        }
    }

    ShortcutRoot(onPalette = { paletteOpen = true }) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(suite),
            sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
            entryProvider = entryProvider {
                entry<ChatsRoute>(
                    metadata = listPane {
                        PanePlaceholder(Tabler.Outline.Messages, "No chat open", "Pick a conversation from the list.")
                    },
                ) {
                    ChatsPage(
                        state = state,
                        toasts = toasts,
                        selectedChatId = (backStack.lastOrNull { it is ConversationRoute } as? ConversationRoute)?.chatId,
                        onOpenChat = openChat,
                        onSearch = { paletteOpen = true },
                        navigation = { LeaveAppButton() },
                        contentPadding = navigationSuitePadding(),
                    )
                }
                entry<ConversationRoute>(metadata = detailPane()) { route ->
                    ConversationPage(
                        state = state,
                        chatId = route.chatId,
                        onBack = pageBack(),
                        onCall = { backStack += CallRoute(route.chatId) },
                        contentPadding = navigationSuitePadding(),
                    )
                }
                entry<CallRoute>(metadata = coversNavigation()) { route ->
                    state.chat(route.chatId)?.let { chat ->
                        CallPage(chat, onEnd = { backStack.removeLastOrNull() })
                    }
                }
                entry<NotesRoute>(
                    metadata = listPane {
                        PanePlaceholder(Tabler.Outline.Notes, "No note open", "Pick one from the list, or start a new one.")
                    },
                ) {
                    NotesPage(
                        state = state,
                        selectedNoteId = (backStack.lastOrNull { it is NoteRoute } as? NoteRoute)?.noteId,
                        onOpenNote = openNote,
                        onNewNote = newNote,
                        onSearch = { paletteOpen = true },
                        navigation = { LeaveAppButton() },
                        contentPadding = navigationSuitePadding(),
                    )
                }
                entry<NoteRoute>(metadata = detailPane()) { route ->
                    NoteDetailPage(
                        state = state,
                        noteId = route.noteId,
                        startEditing = route.editing,
                        onBack = pageBack(),
                        contentPadding = navigationSuitePadding(),
                    )
                }
            },
        )
    }

    CommandPalette(
        visible = paletteOpen,
        onDismissRequest = { paletteOpen = false },
        commands = paletteCommands(
            state = state,
            openChatId = (backStack.lastOrNull() as? ConversationRoute)?.chatId,
            onNewNote = newNote,
            onOpenChat = openChat,
            onOpenNotes = { showRoot(NotesRoute) },
            onArchive = archive,
            onLeave = leave,
        ),
    )
}
