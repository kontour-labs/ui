package io.kontour.ui.catalog.apps.messages

import androidx.compose.runtime.derivedStateOf
import io.kontour.ui.catalog.pageScroll
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import io.kontour.ui.adaptive.sheetEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.GripVertical
import io.kontour.icons.tabler.outline.InfoCircle
import io.kontour.icons.tabler.outline.Notes
import io.kontour.icons.tabler.outline.Pencil
import io.kontour.icons.tabler.outline.Pin
import io.kontour.icons.tabler.outline.Plus
import io.kontour.icons.tabler.outline.Search
import io.kontour.ui.components.action.ExtendedFloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.action.IconToggleButton
import io.kontour.ui.components.display.EmptyState
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.display.Tag
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.list.TextFieldListItem
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.SectionHeader
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.ui.components.selection.ChipGroup
import io.kontour.ui.components.selection.InputChip
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.components.text.TextArea
import io.kontour.ui.components.text.TextField
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.SelectableText
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.markdownText
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.nav.topBarCollapse
import io.kontour.ui.sheet.ModalSideSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import kotlinx.coroutines.flow.drop

/**
 * The notes: the pinned ones first, in an order you drag them into, and the
 * rest after them with their tags.
 *
 * One scrolling column rather than one lazy list, because of the drag. A
 * reorderable row moves among whatever rows its list has, and in a single list
 * that would take in the headings and the unpinned notes too — so the pinned
 * notes are a list of their own, short enough to lay out whole, inside a page
 * that scrolls.
 *
 * @param navigation The top bar's leading slot: the way out of the app.
 * @param contentPadding What the page keeps clear of: a navigation bar over
 *   its foot. The button for a new note sits above it as well.
 */
@Composable
internal fun NotesPage(
    state: MessagesState,
    selectedNoteId: String?,
    onOpenNote: (String) -> Unit,
    onNewNote: () -> Unit,
    onSearch: () -> Unit,
    navigation: (@Composable () -> Unit)?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val collapse = rememberTopBarCollapseState()
    val scroll = rememberScrollState()
    val pinned = state.pinnedNotes
    val others = state.otherNotes

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().topBarCollapse(collapse)) {
            TopBar(
                style = TopBarStyle.Large,
                collapseState = collapse,
                navigation = navigation,
                actions = { IconButton(onClick = onSearch, icon = Tabler.Outline.Search, contentDescription = "Search") },
            ) {
                +"Notes"
                supporting { +"${state.notes.size} notes" }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .pageScroll(scroll)
                    .padding(contentPadding.plusPage(bottom = NotesFootRoom)),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing),
            ) {
                if (pinned.isNotEmpty()) {
                    SectionHeader(supporting = { +"Drag by the handle to reorder" }) { +"Pinned" }
                    PinnedNotes(state, pinned, selectedNoteId, onOpenNote)
                }
                if (others.isNotEmpty()) {
                    SectionHeader { +if (pinned.isEmpty()) "Notes" else "Other notes" }
                    others.forEachIndexed { index, note ->
                        NoteRow(note, note.id == selectedNoteId, GroupPosition.of(index, others.size)) { onOpenNote(note.id) }
                    }
                }
                if (state.notes.isEmpty()) {
                    EmptyState(Modifier.fillMaxWidth().padding(top = Theme.spacing.xl)) {
                        +"No notes"
                        message { +"Write one down before it goes." }
                        leading { +Tabler.Outline.Notes }
                    }
                }
            }
        }
        // Shrinks to its icon once the list has scrolled, so it stops covering
        // the rows it was sitting over at the top. Derived, so the page
        // recomposes when the answer changes rather than on every pixel of a
        // scroll: `scroll.value` read here was the whole page, every frame.
        val atTop by remember { derivedStateOf { scroll.value == 0 } }
        ExtendedFloatingActionButton(
            onClick = onNewNote,
            icon = Tabler.Outline.Plus,
            contentDescription = "New note",
            expanded = atTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Theme.spacing.md, bottom = contentPadding.calculateBottomPadding() + Theme.spacing.md),
        ) {
            +"New note"
        }
    }
}

/**
 * The pinned notes, each draggable by its handle.
 *
 * By the handle rather than by the whole row: the row opens the note on a tap,
 * and a row that is both pressed to open and pressed to pick up asks the hand
 * to know which press it meant. The order lands in [MessagesState] as the drag
 * crosses each row, so the list under the finger is always the real one.
 */
@Composable
private fun PinnedNotes(state: MessagesState, pinned: List<Note>, selectedNoteId: String?, onOpenNote: (String) -> Unit) {
    val listState = rememberLazyListState()
    val reorder = rememberReorderableState(listState) { from, to -> state.movePinned(from, to) }
    LazyColumn(
        // Bounded, as a list inside a scrolling page has to be, and far taller
        // than a handful of pinned notes will ever need: the list is laid out
        // whole and the page does the scrolling.
        modifier = Modifier.fillMaxWidth().heightIn(max = PinnedMaxHeight),
        state = listState,
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing),
    ) {
        itemsIndexed(pinned, key = { _, note -> note.id }) { index, note ->
            ReorderableItem(
                state = reorder,
                index = index,
                itemCount = pinned.size,
                handleIcon = Tabler.Outline.GripVertical,
            ) {
                ListItem(
                    modifier = Modifier.testTag("note-${note.id}"),
                    onClick = { onOpenNote(note.id) },
                    selected = note.id == selectedNoteId,
                    position = GroupPosition.of(index, pinned.size),
                ) {
                    leading { +Tabler.Outline.Pin }
                    +note.title
                    supporting { +note.edited }
                }
            }
        }
    }
}

/** An unpinned note: its title, how it starts, and its tags. */
@Composable
private fun NoteRow(note: Note, selected: Boolean, position: GroupPosition, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.testTag("note-${note.id}"),
        onClick = onClick,
        selected = selected,
        position = position,
    ) {
        +note.title
        supporting {
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
                val excerpt = note.body.plain().lineSequence().firstOrNull { it.isNotBlank() }
                if (excerpt != null) Text(excerpt, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (note.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                    ) {
                        for (tag in note.tags) Tag(tone = toneFor(tag)) { +tag }
                    }
                }
            }
        }
        trailing { +note.edited }
    }
}

/**
 * One note, read or written.
 *
 * Read, it is markdown drawn — bold, italics, code — in text that can be
 * selected and copied, as a note is something you take things out of. Written,
 * it is the same markdown as typed, in a field that grows with it. Changes are
 * kept as they are typed: there is no Save to forget, and leaving the page
 * keeps what was written.
 *
 * @param startEditing Open straight into writing, for a note that is new and
 *   has nothing to read.
 */
@Composable
internal fun NoteDetailPage(
    state: MessagesState,
    noteId: String,
    startEditing: Boolean,
    onBack: (() -> Unit)?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val note = state.note(noteId) ?: return
    var editing by remember(noteId) { mutableStateOf(startEditing) }
    var infoOpen by remember(noteId) { mutableStateOf(false) }
    val title = remember(noteId) { TextFieldState(note.title) }
    val body = remember(noteId) { TextFieldState(note.body) }

    LaunchedEffect(noteId) {
        snapshotFlow { title.text.toString() to body.text.toString() }
            .drop(1)
            .collect { (newTitle, newBody) ->
                state.note(noteId)?.let {
                    state.updateNote(it.copy(title = newTitle.ifBlank { "Untitled note" }, body = newBody, edited = "Just now"))
                }
            }
    }

    Column(modifier.fillMaxSize()) {
        TopBar(
            onBack = onBack,
            actions = {
                IconToggleButton(
                    checked = editing,
                    onCheckedChange = { editing = it },
                    icon = Tabler.Outline.Pencil,
                    contentDescription = "Edit",
                )
                IconButton(onClick = { infoOpen = true }, icon = Tabler.Outline.InfoCircle, contentDescription = "About this note")
            },
        ) {
            +note.title
            supporting { +"Edited ${note.edited.replaceFirstChar { it.lowercase() }}" }
        }
        val direction = LocalLayoutDirection.current
        Column(
            Modifier
                .fillMaxSize()
                // The foot as the conversation's composer has it: the gesture
                // bar, the keyboard while the note is being written, or the
                // navigation bar the page sits in — the tallest, never added.
                // On a phone the page covers the navigation, so it had only
                // a fixed margin, and nothing at all for the keyboard.
                .windowInsetsPadding(WindowInsets.sheetEdges.union(WindowInsets(bottom = contentPadding.calculateBottomPadding())))
                .pageScroll(barPadding = PaddingValues())
                .padding(
                    PaddingValues(
                        start = contentPadding.calculateStartPadding(direction),
                        top = contentPadding.calculateTopPadding(),
                        end = contentPadding.calculateEndPadding(direction),
                    ).plusPage(top = Theme.spacing.sm, bottom = Theme.spacing.md),
                ),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            if (editing) {
                TextField(state = title, label = "Title", modifier = Modifier.fillMaxWidth())
                TextArea(
                    state = body,
                    label = "Note",
                    supporting = "**Bold**, *italic* and `code` are drawn when you stop editing.",
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 8, maxHeightInLines = 24),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (note.body.isBlank()) {
                Text(
                    "Nothing here yet. The pencil above is for writing.",
                    style = Theme.typography.bodyMedium,
                    colour = Theme.colours.contentMuted,
                )
            } else {
                SelectableText {
                    Text(markdownText(note.body), style = Theme.typography.bodyLarge)
                }
                if (note.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
                        for (tag in note.tags) Tag(tone = toneFor(tag)) { +tag }
                    }
                }
            }
        }
    }

    ModalSideSheet(
        visible = infoOpen,
        onDismissRequest = { infoOpen = false },
        paneTitle = "About this note",
    ) {
        NoteInfo(state, note, onClose = { infoOpen = false })
    }
}

/**
 * The note's side sheet: how long it is, its tags, and whether it is pinned.
 *
 * Tags are chips that come off with their cross and go on from the field under
 * them — the one place in the app a chip is a thing you remove rather than a
 * thing you choose.
 */
@Composable
private fun ColumnScope.NoteInfo(state: MessagesState, note: Note, onClose: () -> Unit) {
    val newTag = remember(note.id) { TextFieldState() }
    val words = wordCount(note.body)
    val add = {
        val tag = newTag.text.toString().trim().replaceFirstChar { it.uppercase() }
        if (tag.isNotEmpty() && tag !in note.tags) state.updateNote(note.copy(tags = note.tags + tag))
        newTag.clearText()
    }

    SheetHeader(onClose = onClose) { +"About this note" }
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .pageScroll(barPadding = PaddingValues())
            .padding(start = Theme.spacing.md, end = Theme.spacing.md, bottom = Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xl)) {
            Stat {
                value("$words")
                +if (words == 1) "Word" else "Words"
                supporting("Edited ${note.edited.replaceFirstChar { it.lowercase() }}")
            }
            Stat {
                // A reading speed of about two hundred words a minute, rounded
                // up: nothing takes no time to read.
                value("${(words + ReadingSpeed - 1) / ReadingSpeed} min")
                +"To read"
            }
        }
        Text("Tags", style = Theme.typography.labelMedium, colour = Theme.colours.contentMuted)
        if (note.tags.isEmpty()) {
            Text("No tags yet.", style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
        } else {
            ChipGroup {
                for (tag in note.tags) {
                    InputChip(
                        onRemove = { state.updateNote(note.copy(tags = note.tags - tag)) },
                        removeLabel = "Remove $tag",
                    ) { +tag }
                }
            }
        }
        // A tag typed into a row, and the pin under it: one group, its rows'
        // corners saying where each is in it.
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing)) {
            TextFieldListItem(
                state = newTag,
                placeholder = "Add a tag",
                imeAction = ImeAction.Done,
                onKeyboardAction = { add() },
                position = GroupPosition.First,
            )
            SelectionRow(
                selected = note.pinned,
                onSelectedChange = { state.updateNote(note.copy(pinned = it)) },
                role = Role.Switch,
                position = GroupPosition.Last,
            ) {
                +"Pinned"
                supporting { +"At the top of Notes, in the order you drag it to" }
                trailing { Switch(checked = note.pinned, onCheckedChange = null) }
            }
        }
    }
}

/**
 * A tag's tone, so a tag reads the same colour wherever it is.
 *
 * By name, from a short list, and neutral for anything new — a colour is
 * a promise that two things are alike, and a tag typed a minute ago has not
 * earned one.
 */
internal fun toneFor(tag: String): Tone = when (tag) {
    "Plans", "Travel" -> Tone.Accent
    "Transit" -> Tone.Info
    "Recipes" -> Tone.Success
    "Home", "Birthdays" -> Tone.Warning
    else -> Tone.Neutral
}

/** [this] with its markdown marks taken out, for a one-line excerpt. */
private fun String.plain(): String = replace(Regex("[*`_~]"), "")

/** Words a minute, for the reading time. */
private const val ReadingSpeed = 200

/** The tallest the pinned list may lay itself out; it never comes close. */
private val PinnedMaxHeight = 2_000.dp

/** Room past the last note for the floating button not to sit on it. */
private val NotesFootRoom = 96.dp
