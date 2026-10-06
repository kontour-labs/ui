package io.kontour.ui.samples

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.GripVertical
import io.kontour.icons.tabler.outline.Trash
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.dragdrop.DragType
import io.kontour.ui.dragdrop.DropListState
import io.kontour.ui.dragdrop.DropStatus
import io.kontour.ui.dragdrop.ExternalDrops
import io.kontour.ui.dragdrop.dragItems
import io.kontour.ui.dragdrop.dragSource
import io.kontour.ui.dragdrop.dropList
import io.kontour.ui.dragdrop.dropTarget
import io.kontour.ui.dragdrop.rememberDropListState
import io.kontour.ui.dragdrop.rememberDropTargetState
import io.kontour.ui.files.FileTypes
import io.kontour.ui.files.PickedFile
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/** What a board's cards are carried as. Declared once, beside what it carries. */
internal val TaskType = DragType<Task>("task")

internal data class Task(val id: Int, val title: String)

@Composable
fun KanbanColumns() {
    val todo = remember { mutableStateListOf(Task(1, "Timetable"), Task(2, "Fares")) }
    val done = remember { mutableStateListOf(Task(3, "Maps")) }
    val lists = remember { mutableMapOf<DropListState<*>, MutableList<Task>>() }

    @Composable
    fun TaskLane(title: String, tasks: MutableList<Task>, modifier: Modifier) {
        val scroll = rememberLazyListState()
        // One call per drop: what, where, and where it came from. The lists
        // are yours, and nothing in them moves until this moves it.
        val lane = rememberDropListState(
            listState = scroll,
            type = TaskType,
            items = tasks,
            key = { it.id },
            label = title,
            itemLabel = { it.title },
            onDrop = { drop ->
                lists.getValue(drop.fromList!!).removeAt(drop.fromIndex!!)
                tasks.add(drop.index, drop.payload)
            },
        )
        lists[lane] = tasks
        LazyColumn(state = scroll, modifier = modifier.fillMaxHeight().dropList(lane)) {
            dragItems(lane) { _, task ->
                ListItem {
                    +task.title
                    trailing { Icon(Tabler.Outline.GripVertical, null, Modifier.dragHandle()) }
                }
            }
        }
    }

    Row {
        TaskLane("To do", todo, Modifier.weight(1f))
        TaskLane("Done", done, Modifier.weight(1f))
    }
}

@Composable
fun DropTargetTrash() {
    val tasks = remember { mutableStateListOf(Task(1, "Timetable"), Task(2, "Fares")) }

    // Anything can be carried, and anything can take it: a card here, a bin
    // there, and the type between them.
    val bin = rememberDropTargetState(TaskType, label = "Bin", onDrop = { tasks.remove(it) })
    Row {
        for (task in tasks) {
            Card(Modifier.dragSource(TaskType, task, label = task.title)) { Text(task.title) }
        }
        Box(Modifier.dropTarget(bin)) {
            // Its status says when something it would take is over it.
            val over = bin.status == DropStatus.Over
            Icon(Tabler.Outline.Trash, bin.label, tint = if (over) Theme.colours.danger.solid else LocalContentColour.current)
        }
    }
}

/** What an attachment list's rows are carried as. */
internal val AttachmentType = DragType<PickedFile>("attachment")

@Composable
fun ExternalFileDrop() {
    val attachments = remember { mutableStateListOf<PickedFile>() }
    val scroll = rememberLazyListState()

    // Its own rows move within it; files from outside the app land in it too.
    val list = rememberDropListState(
        listState = scroll,
        type = AttachmentType,
        items = attachments,
        key = { it.name },
        label = "Attachments",
        itemLabel = { it.name },
        onDrop = { drop ->
            attachments.removeAt(drop.fromIndex!!)
            attachments.add(drop.index, drop.payload)
        },
        external = ExternalDrops.files(FileTypes.of("image/*", ".pdf")) { files, index ->
            // Where it was let go, where the platform says; otherwise the end.
            attachments.addAll(index ?: attachments.size, files)
        },
    )
    LazyColumn(state = scroll, modifier = Modifier.dropList(list)) {
        dragItems(list) { _, file -> ListItem { +file.name } }
    }
}
