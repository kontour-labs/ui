@file:OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)

package io.kontour.ui.dragdrop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.geometry.Offset
import io.kontour.ui.files.PickedFile
import io.kontour.ui.files.typeFromName
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UniformTypeIdentifiers.UTTypeURL
import platform.UniformTypeIdentifiers.conformsToType
import platform.posix.memcpy

@Composable
internal actual fun rememberDropReader(): DropReader = ItemProviderDropReader

/**
 * An iPad drag from another app: each item an `NSItemProvider` that names its
 * types at once and hands over its contents later, asynchronously — so a
 * dropped file is read when asked, and so is dropped text.
 */
private object ItemProviderDropReader : DropReader {
    override fun info(event: DragAndDropEvent): ExternalDragInfo? {
        val providers = event.items.map { it.itemProvider }
        if (providers.isEmpty()) return null
        val text = providers.any { it.hasItemConformingToTypeIdentifier(UTTypePlainText.identifier) || it.hasItemConformingToTypeIdentifier(UTTypeURL.identifier) }
        val fileTypes = providers.mapNotNull { fileTypeOf(it) }
        if (fileTypes.isEmpty() && !text) return null
        return ExternalDragInfo(
            hasFiles = fileTypes.isNotEmpty(),
            hasText = text,
            mimeTypes = fileTypes.mapNotNull { UTType.typeWithIdentifier(it)?.preferredMIMEType },
        )
    }

    override fun read(event: DragAndDropEvent): DroppedData? {
        val providers = event.items.map { it.itemProvider }
        if (providers.isEmpty()) return null
        val files = providers.mapNotNull { provider -> fileTypeOf(provider)?.let { ProvidedFile(provider, it) } }
        val textProvider = providers.firstOrNull { it.hasItemConformingToTypeIdentifier(UTTypePlainText.identifier) }
        return DroppedData(
            files = files,
            text = textProvider?.let { provider -> { provider.loadBytes(UTTypePlainText.identifier).decodeToString() } },
        )
    }

    /** Compose does not say where on iOS, from what it passes on. */
    override fun position(event: DragAndDropEvent): Offset? = null
}

/** The type a provider's contents are a file of, if they are: anything that is data and not only text. */
private fun fileTypeOf(provider: NSItemProvider): String? =
    provider.registeredTypeIdentifiers.filterIsInstance<String>().firstOrNull { id ->
        val type = UTType.typeWithIdentifier(id) ?: return@firstOrNull false
        type.conformsToType(UTTypeData) && !type.conformsToType(UTTypePlainText)
    }

private class ProvidedFile(private val provider: NSItemProvider, private val typeId: String) : PickedFile() {
    override val name: String = provider.suggestedName?.let { suggested ->
        val ext = UTType.typeWithIdentifier(typeId)?.preferredFilenameExtension
        if (ext != null && !suggested.contains('.')) "$suggested.$ext" else suggested
    } ?: "file"
    override val size: Long? = null
    override val mimeType: String? = UTType.typeWithIdentifier(typeId)?.preferredMIMEType ?: typeFromName(name)
    override suspend fun readBytes(): ByteArray = provider.loadBytes(typeId)
}

private suspend fun NSItemProvider.loadBytes(typeId: String): ByteArray = suspendCancellableCoroutine { continuation ->
    val progress = loadDataRepresentationForTypeIdentifier(typeId) { data, error ->
        if (data != null) continuation.resume(data.toByteArray())
        else continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "the dropped item could not be read"))
    }
    continuation.invokeOnCancellation { progress.cancel() }
}

private fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
