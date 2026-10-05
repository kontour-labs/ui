package io.kontour.ui.dragdrop

import android.app.Activity
import android.content.ClipDescription
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import io.kontour.ui.files.DocumentFile

@Composable
internal actual fun rememberDropReader(): DropReader {
    val context = LocalContext.current
    return remember(context) { ClipDropReader(context.contentResolver, context.findActivity()) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * A drag from another app, in split screen or from the taskbar: its clip says
 * the types while it is dragged, and holds content URIs and text once dropped.
 * A URI from another app is readable only with the permission the drop grants,
 * which is asked for through the activity.
 */
private class ClipDropReader(
    private val resolver: ContentResolver,
    private val activity: Activity?,
) : DropReader {
    override fun info(event: DragAndDropEvent): ExternalDragInfo? {
        val description = event.toAndroidDragEvent().clipDescription ?: return null
        val types = List(description.mimeTypeCount) { description.getMimeType(it) }
        val text = types.any { it == ClipDescription.MIMETYPE_TEXT_PLAIN || it == ClipDescription.MIMETYPE_TEXT_HTML }
        val files = types.any { it !in TextTypes }
        if (!files && !text) return null
        return ExternalDragInfo(hasFiles = files, hasText = text, mimeTypes = types.filter { it !in TextTypes })
    }

    override fun read(event: DragAndDropEvent): DroppedData? {
        val drag = event.toAndroidDragEvent()
        val clip = drag.clipData ?: return null
        val items = List(clip.itemCount) { clip.getItemAt(it) }
        val uris = items.mapNotNull { it.uri }
        if (uris.isNotEmpty()) activity?.requestDragAndDropPermissions(drag)
        val text = items.firstNotNullOfOrNull { it.text?.toString() }
        return DroppedData(
            files = uris.map { DocumentFile(resolver, it) },
            text = if (text == null) null else ({ text }),
        )
    }

    override fun position(event: DragAndDropEvent): Offset {
        val drag = event.toAndroidDragEvent()
        return Offset(drag.x, drag.y)
    }
}

private val TextTypes = setOf(
    ClipDescription.MIMETYPE_TEXT_PLAIN,
    ClipDescription.MIMETYPE_TEXT_HTML,
    ClipDescription.MIMETYPE_TEXT_INTENT,
    ClipDescription.MIMETYPE_TEXT_URILIST,
)
