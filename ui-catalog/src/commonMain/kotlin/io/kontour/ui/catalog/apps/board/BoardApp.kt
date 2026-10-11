package io.kontour.ui.catalog.apps.board

import io.kontour.ui.components.list.ScrollbarDefaults
import io.kontour.ui.components.list.scrollbar
import io.kontour.ui.catalog.pageScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.text.input.rememberTextFieldState
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.text.TextArea
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Archive
import io.kontour.icons.tabler.outline.Paperclip
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.catalog.apps.LeaveAppButton
import io.kontour.ui.catalog.apps.rememberAppToast
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.TextButton
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.DropZone
import io.kontour.ui.components.display.Tag
import io.kontour.ui.components.text.FileField
import io.kontour.ui.dragdrop.ExternalDrops
import io.kontour.ui.dragdrop.dragAutoScroll
import io.kontour.ui.dragdrop.dragItems
import io.kontour.ui.dragdrop.dropList
import io.kontour.ui.dragdrop.rememberDropListState
import io.kontour.ui.dragdrop.rememberDropTargetState
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.TopBar
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone

/**
 * Board: a team's work as cards in columns, moved by hand.
 *
 * The app the drag and drop API was built for. A card held — a finger's hold, a
 * mouse's press — is carried over everything else on the screen, opens a gap
 * where it would land, and scrolls the board sideways near its edges to reach a
 * column off the screen. Doing holds three at once and turns a fourth away. The
 * archive at the foot takes a card dropped on it. Files dragged in from outside
 * the app, where the platform lets them, become a card in the column they are
 * dropped on.
 *
 * None of it needs a pointer: every card offers *Move to Doing* and the rest to
 * a screen reader, and Alt and an arrow carries one by keyboard.
 */
@Composable
internal fun BoardApp() {
    val board = remember { BoardState.sample() }
    var opened by remember { mutableStateOf<Int?>(null) }
    // The card the sheet was opened on, kept while it closes.
    var shown by remember { mutableStateOf<Int?>(null) }
    val toast = rememberAppToast()

    Scaffold(
        topBar = {
            TopBar(navigation = { LeaveAppButton() }) { +"Board" }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Lanes(
                board = board,
                onOpen = {
                    opened = it.id
                    shown = it.id
                },
                modifier = Modifier.weight(1f),
            )
            Archive(board, onArchived = { toast("Archived ${it.title}") })
        }
    }

    TaskSheet(
        board = board,
        task = shown?.let(board::task),
        visible = opened != null,
        onDismiss = { opened = null },
        onArchive = { task ->
            board.archive(task)
            opened = null
            toast("Archived ${task.title}")
        },
    )
}

/**
 * The columns, side by side, in a row that scrolls sideways — and scrolls by
 * itself while a card is carried near either side of it.
 *
 * A scrolling `Row` rather than a `LazyRow`, and on purpose: a board has a few
 * columns, and a lazy row would leave the ones off a phone's screen
 * uncomposed, so nobody using a screen reader could move a card to them.
 */
@Composable
private fun Lanes(board: BoardState, onOpen: (Task) -> Unit, modifier: Modifier) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dragAutoScroll(scroll, Orientation.Horizontal)
            .scrollbar(scroll, orientation = Orientation.Horizontal)
            .horizontalScroll(scroll)
            .padding(Theme.spacing.md)
            .testTag("board-lanes"),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        for (lane in board.lanes) {
            key(lane.id) { LaneColumn(board, lane, onOpen, Modifier.width(LaneWidth).fillMaxHeight()) }
        }
    }
}

/** One column: its name and how full it is, and its cards. */
@Composable
private fun LaneColumn(
    board: BoardState,
    lane: Lane,
    onOpen: (Task) -> Unit,
    modifier: Modifier,
) {
    val scroll = rememberLazyListState()
    val cards = rememberDropListState(
        listState = scroll,
        type = TaskCard,
        items = lane.tasks,
        key = { it.id },
        label = lane.title,
        itemLabel = { it.title },
        onDrop = { drop -> board.moveTask(drop, lane) },
        accepts = lane::takes,
        // A card's own shape, so the shadow it lifts with has its corners.
        previewShape = Theme.shapes.container,
        // Files from the desktop, another window or the Files app: a card of
        // their own, where they are let go — or at the end, where the platform
        // does not say where. A full column is not offered them.
        external = ExternalDrops(
            accepts = { info -> info.hasFiles && !lane.full },
            onDrop = { data, index -> board.addFromFiles(lane, data.files, index) },
        ),
    )
    Surface(
        modifier = modifier.testTag("lane-${lane.id}"),
        shape = Theme.shapes.container,
        containerColour = Theme.colours.surfaceSunken,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = Theme.spacing.md, end = Theme.spacing.sm, top = Theme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            ) {
                Text(lane.title, style = Theme.typography.titleSmall, modifier = Modifier.weight(1f))
                val count = lane.limit?.let { "${lane.tasks.size} of $it" } ?: "${lane.tasks.size}"
                Tag(tone = if (lane.full) Tone.Warning else Tone.Neutral) { +count }
            }
            Box(Modifier.weight(1f)) {
                if (lane.tasks.isEmpty()) {
                    Text(
                        "Nothing here yet",
                        style = Theme.typography.bodySmall,
                        colour = Theme.colours.contentMuted,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = Theme.spacing.lg),
                    )
                }
                LazyColumn(
                    state = scroll,
                    modifier = Modifier
                        .fillMaxSize()
                        // Inside the lane's rounded card, clear of its corners.
                        .scrollbar(scroll, cornerInset = ScrollbarDefaults.cornerInset(Theme.shapes.container))
                        .dropList(cards),
                    contentPadding = PaddingValues(Theme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                ) {
                    dragItems(cards) { _, task -> TaskCard(task, onClick = { onOpen(task) }) }
                }
            }
        }
    }
}

/** A card: what it is, and how many files it holds. Picked up anywhere on it. */
@Composable
private fun TaskCard(task: Task, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(task.title, style = Theme.typography.bodyMedium)
        if (task.attachments.isNotEmpty()) {
            Row(
                Modifier.padding(top = Theme.spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
            ) {
                Icon(Tabler.Outline.Paperclip, contentDescription = null, tint = Theme.colours.contentMuted, size = Theme.sizing.iconSmall)
                Text(
                    Theme.strings.fileCount(Theme.formats.quantity(task.attachments.size)),
                    style = Theme.typography.bodySmall,
                    colour = Theme.colours.contentMuted,
                )
            }
        }
    }
}

/** Where finished cards go: dropped on, and back again all at once. */
@Composable
private fun Archive(board: BoardState, onArchived: (Task) -> Unit) {
    val archive = rememberDropTargetState(
        TaskCard,
        label = "Archive",
        onDrop = { task ->
            board.archive(task)
            onArchived(task)
        },
    )
    DropZone(
        archive,
        Modifier.padding(start = Theme.spacing.md, end = Theme.spacing.md, bottom = Theme.spacing.md).testTag("board-archive"),
    ) {
        leading { +Tabler.Outline.Archive }
        +"Archive"
        message {
            +if (board.archived.isEmpty()) "Drop a card here to put it away." else "${board.archived.size} put away"
        }
        if (board.archived.isNotEmpty()) {
            action { TextButton(onClick = board::restoreAll) { +"Restore all" } }
        }
    }
}

/**
 * A card opened: where it is on the board, its notes, its files, and the way to
 * archive it without carrying it — what a task sheet in a real board app holds.
 */
@Composable
private fun TaskSheet(
    board: BoardState,
    task: Task?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onArchive: (Task) -> Unit,
) {
    ModalBottomSheet(visible = visible && task != null, onDismissRequest = onDismiss, paneTitle = task?.title) { padding ->
        if (task == null) return@ModalBottomSheet
        val lane = board.laneOf(task.id)
        Column(
            Modifier.pageScroll(barPadding = PaddingValues()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        ) {
            SheetHeader {
                +task.title
                lane?.let { supporting { +it.title } }
            }
            Column(
                Modifier.padding(start = Theme.spacing.md, end = Theme.spacing.md, bottom = Theme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                if (lane != null) {
                    // The columns as a choice, so a card moves without being carried:
                    // a full one turns it away, as a drop on it does.
                    SegmentedControl(
                        options = board.lanes.map { it.title },
                        selectedIndex = board.lanes.indexOf(lane),
                        onSelectedIndexChange = { board.moveTo(task, board.lanes[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    board.lanes.firstOrNull { it !== lane && !it.takes(task) }?.let { full ->
                        Text(
                            "${full.title} is full: ${full.tasks.size} of ${full.limit}.",
                            style = Theme.typography.bodySmall,
                            colour = Theme.colours.contentMuted,
                        )
                    }
                }
                val notes = key(task.id) { rememberTextFieldState(task.notes) }
                LaunchedEffect(task.id) {
                    snapshotFlow { notes.text.toString() }.collect { text ->
                        board.task(task.id)?.takeIf { it.notes != text }?.let { board.update(it.copy(notes = text)) }
                    }
                }
                TextArea(state = notes, label = "Notes", placeholder = "What needs doing", modifier = Modifier.fillMaxWidth())
                FileField(
                    files = task.attachments,
                    onFilesChange = { files -> board.task(task.id)?.let { board.update(it.copy(attachments = files)) } },
                    label = "Attachments",
                    supporting = "Or drop files on a column to make a card of them",
                    multiple = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onArchive(task) },
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                ) { +"Archive" }
            }
        }
    }
}

/** A column's width: a phone shows one and the edge of the next, which says the board goes on. */
private val LaneWidth = 280.dp
