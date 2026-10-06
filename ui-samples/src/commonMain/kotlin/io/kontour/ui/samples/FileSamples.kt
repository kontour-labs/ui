package io.kontour.ui.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.text.FileField
import io.kontour.ui.files.FileTypes
import io.kontour.ui.files.PickedFile
import io.kontour.ui.files.rememberFilePicker
import kotlinx.coroutines.launch

@Composable
fun FileFieldBasics() {
    var files by remember { mutableStateOf(emptyList<PickedFile>()) }

    // One control: pressed, it opens the platform's picker; filled, it says
    // what it holds, and clears with its own button.
    FileField(
        files = files,
        onFilesChange = { files = it },
        label = "Timetable",
        types = FileTypes.of(".pdf", ".csv"),
        supporting = "A PDF or a spreadsheet export",
    )
}

@Composable
fun FileFieldMultiple() {
    var photos by remember { mutableStateOf(emptyList<PickedFile>()) }

    // Several add up, a chip each; a file already there is not added twice.
    FileField(
        files = photos,
        onFilesChange = { photos = it },
        label = "Photos",
        types = FileTypes.of("image/*"),
        multiple = true,
    )
}

@Composable
fun FilePickerButton(upload: suspend (name: String, bytes: ByteArray) -> Unit) {
    val scope = rememberCoroutineScope()
    val picker = rememberFilePicker { files ->
        // Read when you are ready, not when it is picked.
        for (file in files) scope.launch { upload(file.name, file.readBytes()) }
    }

    Column {
        // Launched straight from the press: a browser opens a picker only
        // while the press that asked for it is being handled.
        Button(onClick = { picker.launch(FileTypes.of("image/*"), multiple = true) }) { +"Add photos" }
    }
}
