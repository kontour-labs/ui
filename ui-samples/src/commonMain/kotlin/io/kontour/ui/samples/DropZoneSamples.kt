package io.kontour.ui.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Archive
import io.kontour.icons.tabler.outline.FileUpload
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.DropZone
import io.kontour.ui.dragdrop.ExternalDrops
import io.kontour.ui.dragdrop.dragSource
import io.kontour.ui.dragdrop.rememberDropTargetState
import io.kontour.ui.files.FileTypes
import io.kontour.ui.files.PickedFile
import io.kontour.ui.files.rememberFilePicker
import io.kontour.ui.foundation.Text

@Composable
fun DropZoneArchive() {
    val tasks = remember { mutableStateListOf(Task(1, "Timetable"), Task(2, "Fares")) }
    val archived = remember { mutableStateListOf<Task>() }

    // A drop target, drawn: dashed at rest, the accent while a task is
    // carried, its ground once one is over it.
    val archive = rememberDropTargetState(TaskType, label = "Archive", onDrop = { task ->
        tasks.remove(task)
        archived.add(task)
    })
    Column {
        for (task in tasks) {
            Card(Modifier.dragSource(TaskType, task, label = task.title)) { Text(task.title) }
        }
        DropZone(archive) {
            leading { +Tabler.Outline.Archive }
            +"Archive"
            message { +"Drop a task here to put it away." }
        }
    }
}

@Composable
fun DropZoneUpload(attach: (List<PickedFile>) -> Unit) {
    val types = FileTypes.of(".pdf", ".csv")

    // Files from outside the app only: no in-app type, so nothing in the app
    // can be carried here.
    val upload = rememberDropTargetState(
        label = "Timetables",
        external = ExternalDrops.files(types) { files, _ -> attach(files) },
    )
    // Nobody drags with a screen reader or a keyboard, so the zone has a
    // button that does the same thing.
    val picker = rememberFilePicker(attach)
    DropZone(upload) {
        leading { +Tabler.Outline.FileUpload }
        +"Drop timetables here"
        message { +"PDF or CSV" }
        action { Button(onClick = { picker.launch(types, multiple = true) }) { +"Choose files" } }
    }
}
