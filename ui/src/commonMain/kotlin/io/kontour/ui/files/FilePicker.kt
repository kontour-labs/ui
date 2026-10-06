package io.kontour.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Something that shows a picker and hands back what was chosen.
 *
 * The platform's own — Android's document picker, iOS's, a desktop file dialog,
 * a browser's — unless [LocalFileChooser] provides another: a test's, a
 * preview's, or an app's that picks from its own storage.
 */
fun interface FileChooser {
    /**
     * Shows the picker for [types], one file or [multiple], and calls
     * [onResult] once with what was chosen — an empty list if nothing was.
     */
    fun choose(types: FileTypes, multiple: Boolean, onResult: (List<PickedFile>) -> Unit)
}

/**
 * The chooser [rememberFilePicker] uses instead of the platform's. `null`, the
 * default, is the platform's own.
 */
val LocalFileChooser: ProvidableCompositionLocal<FileChooser?> = staticCompositionLocalOf { null }

/**
 * Opens a picker. Made by [rememberFilePicker].
 *
 * [launch] is not a suspending call, and that is not a convenience: a browser
 * opens a picker only from inside the handler of the press that asked for it,
 * and a coroutine would have resumed after the handler returned. Call it
 * straight from an `onClick`.
 */
@Stable
class FilePicker internal constructor(
    private val chooser: () -> FileChooser,
    private val onResult: () -> (List<PickedFile>) -> Unit,
) {
    /** Shows the picker for [types], for one file or [multiple]. */
    fun launch(types: FileTypes = FileTypes.Any, multiple: Boolean = false) {
        chooser().choose(types, multiple) { picked ->
            // Checked again: not every platform can be told what to show.
            onResult()(picked.filter(types::accepts))
        }
    }
}

/**
 * A picker for files, from the platform — or from [LocalFileChooser] — calling
 * [onResult] with what was chosen.
 *
 * ```kotlin
 * val picker = rememberFilePicker { files -> attach(files) }
 * Button(onClick = { picker.launch(FileTypes.of("image/jpeg", "image/png")) }) { +"Add photos" }
 * ```
 *
 * On Android it uses the document picker through the activity's result
 * registry, so it has to be composed under an activity; on iOS the document
 * picker, presented from the Compose view controller; on a desktop a file
 * dialog over the window; in a browser the page's own file input.
 */
@Composable
fun rememberFilePicker(onResult: (List<PickedFile>) -> Unit): FilePicker {
    val provided = LocalFileChooser.current
    val platform = rememberPlatformFileChooser()
    val chooser by rememberUpdatedState(provided ?: platform)
    val result by rememberUpdatedState(onResult)
    return remember { FilePicker(chooser = { chooser }, onResult = { result }) }
}

/**
 * The platform's own picker, as [rememberFilePicker] uses when [LocalFileChooser]
 * provides none: for a chooser of your own that wraps it — one that records
 * what was asked for, or offers an app's own files first and the platform's
 * after.
 *
 * ```kotlin
 * val platform = rememberPlatformFileChooser()
 * val logged = remember(platform) {
 *     FileChooser { types, multiple, onResult ->
 *         platform.choose(types, multiple) { picked ->
 *             log("picked ${picked.size}")
 *             onResult(picked)
 *         }
 *     }
 * }
 * CompositionLocalProvider(LocalFileChooser provides logged) { Form() }
 * ```
 */
@Composable
expect fun rememberPlatformFileChooser(): FileChooser
