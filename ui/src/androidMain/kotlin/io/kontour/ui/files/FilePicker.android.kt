package io.kontour.ui.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android's document picker, through the activity's result registry: one
 * document or several, of the types asked for where the types are media types.
 *
 * Composed with no registry — a preview, a host that is not an activity — it
 * picks nothing rather than throwing.
 */
@Composable
actual fun rememberPlatformFileChooser(): FileChooser {
    val resolver = LocalContext.current.contentResolver
    if (LocalActivityResultRegistryOwner.current == null) return remember { FileChooser { _, _, onResult -> onResult(emptyList()) } }
    val waiting = remember { arrayOfNulls<(List<PickedFile>) -> Unit>(1) }
    fun deliver(uris: List<Uri>) {
        val callback = waiting[0] ?: return
        waiting[0] = null
        callback(uris.map { DocumentFile(resolver, it) })
    }
    val one = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> deliver(listOfNotNull(uri)) }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> deliver(uris) }
    return remember(one, many) {
        FileChooser { types, multiple, onResult ->
            waiting[0] = onResult
            val mimes = (types.mimeTypes + types.extensions.mapNotNull { typeFromName("file.$it") })
                .distinct()
                .ifEmpty { listOf("*/*") }
                .toTypedArray()
            if (multiple) many.launch(mimes) else one.launch(mimes)
        }
    }
}

/** A document the picker handed back, read through the content resolver. */
internal class DocumentFile(private val resolver: ContentResolver, private val uri: Uri) : PickedFile() {
    private val columns: Pair<String?, Long?> = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null to null
            val nameAt = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeAt = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameAt >= 0) cursor.getString(nameAt) else null
            val size = if (sizeAt >= 0 && !cursor.isNull(sizeAt)) cursor.getLong(sizeAt) else null
            name to size
        }
    }.getOrNull() ?: (null to null)

    override val name: String = columns.first ?: uri.lastPathSegment.orEmpty()
    override val size: Long? = columns.second
    override val mimeType: String? = resolver.getType(uri) ?: typeFromName(name)

    override suspend fun readBytes(): ByteArray = withContext(Dispatchers.IO) {
        requireNotNull(resolver.openInputStream(uri)) { "the document at $uri could not be opened" }.use { it.readBytes() }
    }
}
