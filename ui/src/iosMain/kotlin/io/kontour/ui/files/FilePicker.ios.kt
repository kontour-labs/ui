@file:OptIn(ExperimentalForeignApi::class)

package io.kontour.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLFileSizeKey
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * iOS's document picker, presented over whatever the Compose view controller
 * is already presenting. Files come back as copies the app owns, so they stay
 * readable after the picker has gone.
 */
@Composable
actual fun rememberPlatformFileChooser(): FileChooser {
    val host = LocalUIViewController.current
    return remember(host) { DocumentPickerChooser(host) }
}

private class DocumentPickerChooser(private val host: UIViewController) : FileChooser {
    /**
     * The picker holds its delegate weakly, so something has to hold it
     * strongly until the picker answers.
     */
    private var delegate: PickerDelegate? = null

    override fun choose(types: FileTypes, multiple: Boolean, onResult: (List<PickedFile>) -> Unit) {
        val contentTypes = (
            types.mimeTypes.mapNotNull { mime ->
                // A wildcard has no exact type; its family does.
                if (mime.endsWith("/*")) familyOf(mime.substringBefore('/')) else UTType.typeWithMIMEType(mime)
            } + types.extensions.mapNotNull { UTType.typeWithFilenameExtension(it) }
        ).ifEmpty { listOf(UTTypeItem) }
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = contentTypes, asCopy = true)
        picker.allowsMultipleSelection = multiple
        val answer = PickerDelegate { urls ->
            delegate = null
            onResult(urls.map(::CopiedFile))
        }
        delegate = answer
        picker.delegate = answer
        var top = host
        while (true) top = top.presentedViewController ?: break
        top.presentViewController(picker, animated = true, completion = null)
    }
}

private fun familyOf(family: String): UTType? = when (family) {
    "image" -> UTType.typeWithIdentifier("public.image")
    "video" -> UTType.typeWithIdentifier("public.movie")
    "audio" -> UTType.typeWithIdentifier("public.audio")
    "text" -> UTType.typeWithIdentifier("public.text")
    else -> null
}

private class PickerDelegate(private val onPicked: (List<NSURL>) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onPicked(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(emptyList())
    }
}

/** A file the picker copied into the app's own storage. */
private class CopiedFile(private val url: NSURL) : PickedFile() {
    override val name: String = url.lastPathComponent.orEmpty()
    override val size: Long? = runCatching {
        (url.resourceValuesForKeys(listOf(NSURLFileSizeKey), null)?.get(NSURLFileSizeKey) as? NSNumber)?.longLongValue
    }.getOrNull()
    override val mimeType: String? = url.pathExtension?.let { UTType.typeWithFilenameExtension(it)?.preferredMIMEType } ?: typeFromName(name)

    override suspend fun readBytes(): ByteArray = withContext(Dispatchers.IO) {
        val data = requireNotNull(NSData.dataWithContentsOfURL(url)) { "the file at $url could not be read" }
        data.toByteArray()
    }
}

private fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
