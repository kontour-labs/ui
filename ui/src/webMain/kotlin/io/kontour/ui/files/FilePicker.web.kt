@file:OptIn(ExperimentalWasmJsInterop::class)

package io.kontour.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader

/**
 * The page's own file input, made for each pick and clicked at once.
 *
 * A browser opens a picker only while the press that asked for it is still
 * being handled, which is why [FilePicker.launch] does not suspend. Closed
 * without a choice, browsers that report it — every current one — hand back
 * nothing; an older one says nothing at all, and [FileChooser.choose]'s callback
 * does not come.
 */
@Composable
actual fun rememberPlatformFileChooser(): FileChooser = remember { WebFileChooser }

private object WebFileChooser : FileChooser {
    override fun choose(types: FileTypes, multiple: Boolean, onResult: (List<PickedFile>) -> Unit) {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.multiple = multiple
        input.accept = (types.mimeTypes + types.extensions.map { ".$it" }).joinToString(",")
        input.style.display = "none"
        var settled = false
        fun settle(files: List<PickedFile>) {
            if (settled) return
            settled = true
            input.remove()
            onResult(files)
        }
        input.addEventListener("change", {
            val list = input.files
            settle(if (list == null) emptyList() else List(list.length) { list.item(it) }.filterNotNull().map(::browserFile))
        })
        input.addEventListener("cancel", { settle(emptyList()) })
        document.body?.appendChild(input)
        input.click()
    }
}

/** A file the page was given — picked, or dropped on it. */
internal fun browserFile(file: File): PickedFile = BrowserFile(file)

/** A file the page was given, read through a `FileReader` when asked. */
private class BrowserFile(private val file: File) : PickedFile() {
    override val name: String = file.name
    override val size: Long? = fileSize(file).toLong()
    override val mimeType: String? = file.type.ifEmpty { null } ?: typeFromName(name)

    override suspend fun readBytes(): ByteArray = suspendCancellableCoroutine { continuation ->
        val reader = FileReader()
        reader.addEventListener("load", {
            val view = Int8Array(readerBuffer(reader))
            continuation.resume(ByteArray(view.length) { view[it] })
        })
        reader.addEventListener("error", {
            continuation.resumeWithException(IllegalStateException("${file.name} could not be read"))
        })
        continuation.invokeOnCancellation { reader.abort() }
        reader.readAsArrayBuffer(file)
    }
}

private fun fileSize(file: File): Double = js("file.size")

private fun readerBuffer(reader: FileReader): ArrayBuffer = js("reader.result")
