package io.kontour.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The platform's own file dialog, over the window that has the focus.
 *
 * Opened on the next turn of the event queue rather than inside the press, so the
 * press finishes first: AWT's dialog is modal and runs its own loop until it
 * closes. Headless — a test, a server — there is no dialog, and the result is
 * that nothing was chosen.
 */
@Composable
actual fun rememberPlatformFileChooser(): FileChooser = remember { DesktopFileChooser }

internal object DesktopFileChooser : FileChooser {
    override fun choose(types: FileTypes, multiple: Boolean, onResult: (List<PickedFile>) -> Unit) {
        if (GraphicsEnvironment.isHeadless()) {
            onResult(emptyList())
            return
        }
        EventQueue.invokeLater {
            val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
            val dialog = FileDialog(owner, null, FileDialog.LOAD)
            dialog.isMultipleMode = multiple
            // Honoured on Linux; macOS and Windows show every file, and what is
            // chosen is checked again by the picker either way.
            dialog.setFilenameFilter { _, name -> types.accepts(name, null) }
            dialog.isVisible = true
            onResult(dialog.files.orEmpty().map(::DesktopFile))
            dialog.dispose()
        }
    }
}

/** A file on disk, read when asked. */
internal class DesktopFile(private val file: File) : PickedFile() {
    override val name: String = file.name
    override val size: Long? = file.length().takeIf { file.exists() }
    override val mimeType: String? = runCatching { Files.probeContentType(file.toPath()) }.getOrNull() ?: typeFromName(file.name)
    override suspend fun readBytes(): ByteArray = withContext(Dispatchers.IO) { file.readBytes() }
}
