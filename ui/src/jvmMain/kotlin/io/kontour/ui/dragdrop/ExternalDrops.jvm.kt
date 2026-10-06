package io.kontour.ui.dragdrop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.geometry.Offset
import io.kontour.ui.files.DesktopFile
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File

@Composable
internal actual fun rememberDropReader(): DropReader = AwtDropReader

/** A desktop drag: files from the file manager, text from another window. */
@OptIn(ExperimentalComposeUiApi::class)
internal object AwtDropReader : DropReader {
    override fun info(event: DragAndDropEvent): ExternalDragInfo? = infoOf(event.awtTransferable)

    override fun read(event: DragAndDropEvent): DroppedData? = readFrom(event.awtTransferable)

    override fun position(event: DragAndDropEvent): Offset? {
        val (point, component) = when (val native = event.nativeEvent) {
            is DropTargetDragEvent -> native.location to native.dropTargetContext.component
            is DropTargetDropEvent -> native.location to native.dropTargetContext.component
            else -> return null
        }
        val scale = component?.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
        return Offset((point.x * scale).toFloat(), (point.y * scale).toFloat())
    }

    /** What a transferable offers: AWT says which flavours, not which files. */
    fun infoOf(transferable: Transferable): ExternalDragInfo? {
        val files = transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        val text = transferable.isDataFlavorSupported(DataFlavor.stringFlavor)
        return if (files || text) ExternalDragInfo(hasFiles = files, hasText = text) else null
    }

    /** What a transferable put down: the file manager's files, or another window's text. */
    fun readFrom(transferable: Transferable): DroppedData? {
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            val files = runCatching { transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> }
                .getOrNull().orEmpty()
                .filterIsInstance<File>()
                .filter { it.isFile }
                .map(::DesktopFile)
            return DroppedData(files = files)
        }
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            val text = runCatching { transferable.getTransferData(DataFlavor.stringFlavor) as? String }.getOrNull()
                ?: return null
            return DroppedData(files = emptyList(), text = { text })
        }
        return null
    }
}
