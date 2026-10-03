package io.kontour.ui.catalog.apps.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Note
import com.composables.icons.tabler.outline.Paperclip
import com.composables.icons.tabler.outline.Phone
import com.composables.icons.tabler.outline.Send
import com.composables.icons.tabler.outline.Train
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.display.BubbleSide
import io.kontour.ui.components.display.ChatBubble
import io.kontour.ui.components.display.Kbd
import io.kontour.ui.components.display.KbdIcons
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.list.SectionHeader
import io.kontour.ui.components.text.TextField
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.nav.TopBar
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.Popover
import io.kontour.ui.theme.Theme

/**
 * One conversation: the messages, newest at the foot, and a field to add to them.
 *
 * Beside the chat list when the window has room and over it when it has not;
 * the page is the same either way, and only [onBack] differs — null beside the
 * list, where there is nothing to go back to, and the stack's back over it.
 *
 * @param onCall Starts a call, which the app shows as a page over everything,
 *   navigation included.
 * @param contentPadding What the composer keeps clear of: a navigation bar
 *   over the foot of the window, on a phone.
 */
@Composable
internal fun ConversationPage(
    state: MessagesState,
    chatId: String,
    onBack: (() -> Unit)?,
    onCall: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val chat = state.chat(chatId) ?: return
    val thread = state.thread(chatId)
    val typing = state.typing[chatId]

    // Open is read: the badge goes as the page arrives, and a reply that lands
    // while it is showing never becomes unread at all.
    DisposableEffect(chatId) {
        state.openChatId = chatId
        state.markRead(chatId)
        onDispose { if (state.openChatId == chatId) state.openChatId = null }
    }

    Column(modifier.fillMaxSize()) {
        TopBar(
            onBack = onBack,
            actions = {
                IconButton(onClick = onCall, icon = Tabler.Outline.Phone, contentDescription = "Call ${chat.name}")
                ChatInfo(chat, thread)
            },
        ) {
            +chat.name
            supporting {
                +when {
                    typing != null -> "${typing.substringBefore(' ')} is typing…"
                    chat.kind == ChatKind.Group -> "${chat.members.size + 1} members"
                    else -> chat.about
                }
            }
        }
        MessageList(chat, thread, typing, Modifier.weight(1f))
        Composer(
            chatId = chatId,
            onSend = { state.send(chatId, it) },
            modifier = Modifier
                // The bar's height or the keyboard's, whichever is taller —
                // not both: the keyboard comes up over the bar.
                .windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets(bottom = contentPadding.calculateBottomPadding())),
                ),
        )
    }
}

/** One line of the thread as the list lays it out. */
@Immutable
private sealed interface ThreadRow {
    val key: Any

    data class Day(val heading: String) : ThreadRow {
        override val key: Any get() = "day-$heading"
    }

    /** Whose run of messages starts here, in a group, where the bubbles alone do not say. */
    data class Sender(val name: String, val before: Int) : ThreadRow {
        override val key: Any get() = "sender-$before"
    }

    data class Bubble(val message: Message, val position: GroupPosition) : ThreadRow {
        override val key: Any get() = message.id
    }
}

/**
 * The thread, flattened into headings, names and bubbles.
 *
 * Runs are worked out inside each day rather than across the whole thread: a
 * run that crossed midnight would draw its heading through the middle of a
 * group of bubbles that are meant to read as one.
 */
private fun rowsFor(chat: Chat, thread: List<Message>): List<ThreadRow> = buildList {
    for ((day, messages) in thread.groupBy { it.date }) {
        add(ThreadRow.Day(day.dayHeading()))
        messages.forEachIndexed { index, message ->
            val position = GroupPosition.of(messages, index) { it.sender }
            val startsRun = position == GroupPosition.First || position == GroupPosition.Only
            if (chat.kind == ChatKind.Group && !message.fromMe && startsRun) {
                add(ThreadRow.Sender(message.sender, message.id))
            }
            add(ThreadRow.Bubble(message, position))
        }
    }
}

/**
 * The messages, laid out from the foot up.
 *
 * Reversed, so a short conversation sits by the composer, where a reply is
 * typed, rather than at the top with a gap under it; and so the newest message
 * is where the list starts. A new one scrolls into view whoever sent it.
 */
@Composable
private fun MessageList(chat: Chat, thread: List<Message>, typing: String?, modifier: Modifier = Modifier) {
    val rows = remember(chat, thread) { rowsFor(chat, thread).asReversed() }
    val listState = rememberLazyListState()
    val reduceMotion = Theme.motion.reduceMotion
    val newest = thread.lastOrNull()?.id

    LaunchedEffect(newest, typing) {
        if (listState.firstVisibleItemIndex > 0) {
            if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm),
    ) {
        if (typing != null) {
            item(key = "typing") {
                Text(
                    "${typing.substringBefore(' ')} is typing…",
                    modifier = Modifier.padding(top = Theme.spacing.xs).animateItem(),
                    style = Theme.typography.bodySmall,
                    colour = Theme.colours.contentMuted,
                )
            }
        }
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ThreadRow.Day -> SectionHeader(Modifier.padding(bottom = Theme.spacing.xxs)) { +row.heading }
                is ThreadRow.Sender -> Text(
                    row.name.substringBefore(' '),
                    modifier = Modifier.padding(start = Theme.spacing.sm, top = Theme.spacing.xs, bottom = RunGap),
                    style = Theme.typography.labelSmall,
                    colour = Theme.colours.contentMuted,
                )
                is ThreadRow.Bubble -> MessageBubble(row.message, row.position, Modifier.animateItem())
            }
        }
    }
}

/**
 * One message.
 *
 * A couple of dp between bubbles of a run and a line's worth between runs, as
 * `ChatBubble` asks; the time goes under the last of a run only, where the
 * tail says the turn ended.
 */
@Composable
private fun MessageBubble(message: Message, position: GroupPosition, modifier: Modifier = Modifier) {
    val startsRun = position == GroupPosition.First || position == GroupPosition.Only
    val endsRun = position == GroupPosition.Last || position == GroupPosition.Only
    ChatBubble(
        side = if (message.fromMe) BubbleSide.Outgoing else BubbleSide.Incoming,
        position = position,
        modifier = modifier.padding(top = if (startsRun) Theme.spacing.xs else RunGap),
        meta = if (endsRun) ({ +message.time.label() }) else null,
    ) {
        +message.text
    }
}

/**
 * The chat's details, in a popover off its button: a short list of facts that
 * is not worth a page of its own.
 */
@Composable
private fun ChatInfo(chat: Chat, thread: List<Message>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = !open }, icon = Tabler.Outline.InfoCircle, contentDescription = "About this chat")
        Popover(
            visible = open,
            onDismissRequest = { open = false },
            alignment = OverlayAlignment.End,
            title = { +chat.name },
        ) {
            KeyValueList {
                if (chat.kind == ChatKind.Group) {
                    item("Members", (chat.members.map { it.substringBefore(' ') } + "you").joinToString(", "))
                }
                item(if (chat.kind == ChatKind.Group) "About" else "Known for", chat.about)
                item("Messages", "${thread.size}")
                thread.firstOrNull()?.let { item("Since", it.date.shortDate()) }
                item("Pinned", if (chat.pinned) "Yes" else "No")
            }
        }
    }
}

/**
 * Something to attach: what the attach popover offers.
 *
 * Each sends a message saying what it was, since there is nothing behind them
 * to send — but each is a different kind of thing, which is the popover's
 * point: a list of choices anchored to the button that asked for them.
 */
private enum class Attachment(val label: String, val supporting: String, val message: String) {
    Location("My location", "Elizabeth Quay", "My location: Elizabeth Quay, by the bell tower"),
    Note("A note", "Weekend plan", "Shared a note: Weekend plan"),
    Train("Next train", "8:22 from Perth Station", "Next train: 8:22 from Perth Station, platform 7"),
}

/**
 * Where a message is written: attach, the field, send.
 *
 * Enter sends — the field is one line, and its action key is Send — and the
 * hint saying so is drawn only for a pointer or a keyboard, the inputs that
 * have an Enter key on them. A phone's keyboard already shows its send key.
 */
@Composable
private fun Composer(chatId: String, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    // One draft per chat: half a message to Ari stays Ari's when Kai's chat opens.
    val draft = remember(chatId) { TextFieldState() }
    var attachOpen by remember { mutableStateOf(false) }
    val canSend = draft.text.isNotBlank()
    val modality = LocalInputModality.current
    val send = {
        if (draft.text.isNotBlank()) {
            onSend(draft.text.toString())
            draft.clearText()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Theme.spacing.sm, vertical = Theme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                IconButton(onClick = { attachOpen = !attachOpen }, icon = Tabler.Outline.Paperclip, contentDescription = "Attach")
                Popover(
                    visible = attachOpen,
                    onDismissRequest = { attachOpen = false },
                    side = OverlaySide.Top,
                    alignment = OverlayAlignment.Start,
                    title = { +"Attach" },
                ) {
                    ListGroup {
                        for (attachment in Attachment.entries) {
                            item(
                                label = attachment.label,
                                supporting = attachment.supporting,
                                icon = when (attachment) {
                                    Attachment.Location -> Tabler.Outline.MapPin
                                    Attachment.Note -> Tabler.Outline.Note
                                    Attachment.Train -> Tabler.Outline.Train
                                },
                                onClick = {
                                    attachOpen = false
                                    onSend(attachment.message)
                                },
                            )
                        }
                    }
                }
            }
            TextField(
                state = draft,
                modifier = Modifier.weight(1f).testTag("composer"),
                placeholder = "Message",
                imeAction = ImeAction.Send,
                onKeyboardAction = { send() },
            )
            IconButton(
                onClick = send,
                icon = Tabler.Outline.Send,
                contentDescription = "Send",
                enabled = canSend,
                variant = ButtonVariant.Primary,
            )
        }
        if (modality.supportsHover || modality.showsFocusRing) {
            Row(
                // Under the field rather than under the attach button: the
                // hint is about what the field does with Enter.
                modifier = Modifier.padding(start = Theme.spacing.xxl + Theme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Kbd { +KbdIcons.Return }
                Text("to send", style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
            }
        }
    }
}

/** Between two bubbles of one run: close enough to read as one turn. */
private val RunGap = 2.dp
