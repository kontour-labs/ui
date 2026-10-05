@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalComposeUiApi::class)

package io.kontour.ui.dragdrop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.domDataTransferOrNull
import androidx.compose.ui.geometry.Offset
import io.kontour.ui.files.browserFile
import kotlin.js.ExperimentalWasmJsInterop
import org.w3c.dom.DataTransfer

@Composable
internal actual fun rememberDropReader(): DropReader = BrowserDropReader

/**
 * A page drag: files from the desktop, text from another tab. While it is
 * dragged the browser names the types and keeps the files to itself; dropped,
 * it hands them over.
 */
private object BrowserDropReader : DropReader {
    override fun info(event: DragAndDropEvent): ExternalDragInfo? {
        val transfer = event.transferData?.domDataTransferOrNull ?: return null
        val types = List(typeCount(transfer)) { typeAt(transfer, it) }
        val kinds = itemKinds(transfer)
        val files = "Files" in types
        val text = "text/plain" in types || "text/uri-list" in types
        if (!files && !text) return null
        return ExternalDragInfo(hasFiles = files, hasText = text, mimeTypes = kinds)
    }

    override fun read(event: DragAndDropEvent): DroppedData? {
        val transfer = event.transferData?.domDataTransferOrNull ?: return null
        val list = transfer.files
        val files = List(list.length) { list.item(it) }.filterNotNull().map(::browserFile)

        val text = transfer.getData("text/plain").ifEmpty { transfer.getData("text/uri-list") }
        return DroppedData(files = files, text = if (text.isEmpty()) null else ({ text }))
    }

    /** The browser's drag event says where, but Compose does not pass it on here. */
    override fun position(event: DragAndDropEvent): Offset? = null
}

/** The media types of the files being dragged, which a browser names before the drop. */
private fun itemKinds(transfer: DataTransfer): List<String> =
    List(itemCount(transfer)) { itemType(transfer, it) }.filter { it.isNotEmpty() }

private fun itemCount(transfer: DataTransfer): Int = js("(transfer.items ? transfer.items.length : 0)")

private fun itemType(transfer: DataTransfer, index: Int): String = js("(transfer.items[index].kind === 'file' ? transfer.items[index].type : '')")

private fun typeCount(transfer: DataTransfer): Int = js("(transfer.types ? transfer.types.length : 0)")

private fun typeAt(transfer: DataTransfer, index: Int): String = js("String(transfer.types[index])")
