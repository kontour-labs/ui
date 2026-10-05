package io.kontour.ui.dragdrop

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.geometry.Offset
import io.kontour.ui.files.FileTypes
import io.kontour.ui.files.PickedFile

/**
 * What a drag from outside the app carries, as far as a target can tell before
 * it is let go: whether there are files in it, whether there is text, and the
 * media types of what is offered where the platform says. A browser and Android
 * name the files' types while they are dragged; a desktop says only that there
 * are files.
 */
@Immutable
class ExternalDragInfo(
    val hasFiles: Boolean,
    val hasText: Boolean,
    val mimeTypes: List<String> = emptyList(),
) {
    override fun toString(): String = "ExternalDragInfo(files=$hasFiles, text=$hasText, $mimeTypes)"
}

/**
 * What a drag from outside the app put down: [files], read on demand as a
 * picker's are, and text where there was some.
 */
@Stable
class DroppedData(
    val files: List<PickedFile>,
    private val text: (suspend () -> String?)? = null,
) {
    /** Whether there is text to read. */
    val hasText: Boolean get() = text != null

    /**
     * The text that was dropped: a link, a selection, a snippet. Read when
     * asked, since on iOS it arrives after the drop.
     */
    suspend fun readText(): String? = text?.invoke()
}

/**
 * A target's terms for drags from outside the app — files from the desktop or
 * the Files app, text from another window — beside the in-app drags its
 * [DragType] already takes.
 *
 * ```kotlin
 * val column = rememberDropListState(
 *     …,
 *     external = ExternalDrops.files(FileTypes.of("image/png", "image/jpeg")) { files, index ->
 *         cards.addAll(index ?: cards.size, files.map(::Card))
 *     },
 * )
 * ```
 *
 * @param accepts Whether to take a drag, from what can be told before it is let
 *   go. Turned away, it is not offered here at all.
 * @param onDrop Called once, when it is let go: what was dropped, and for a list
 *   the position it was let go at where the platform says where — on a desktop
 *   and on Android — or `null` where it does not.
 */
@Stable
class ExternalDrops(
    val accepts: (ExternalDragInfo) -> Boolean = { true },
    val onDrop: (data: DroppedData, index: Int?) -> Unit,
) {
    companion object {
        /**
         * Takes files of [types] and nothing else. A drag whose types the
         * platform names is turned away before it is let go if none would do;
         * what is let go is checked again, and only matching files reach
         * [onDrop] — never an empty list.
         */
        fun files(
            types: FileTypes = FileTypes.Any,
            onDrop: (files: List<PickedFile>, index: Int?) -> Unit,
        ): ExternalDrops = ExternalDrops(
            accepts = { info -> info.hasFiles && mightTake(types, info) },
            onDrop = { data, index ->
                val taken = data.files.filter(types::accepts)
                if (taken.isNotEmpty()) onDrop(taken, index)
            },
        )
    }
}

/**
 * Whether any of what is offered could be [types]. A type nobody named, or an
 * extension that can only be checked against a name, gives it the benefit of
 * the doubt: the files are checked again when they land.
 */
internal fun mightTake(types: FileTypes, info: ExternalDragInfo): Boolean =
    types == FileTypes.Any ||
        info.mimeTypes.isEmpty() ||
        types.extensions.isNotEmpty() ||
        info.mimeTypes.any { types.accepts("", it) }

/**
 * Reads what a platform drag carries. Each platform's own: the drag event is a
 * different thing on each, and so is what it holds.
 */
internal interface DropReader {
    /** What [event] carries, as far as can be told while it is dragged; `null` if nothing this can read. */
    fun info(event: DragAndDropEvent): ExternalDragInfo?

    /** What [event] put down. */
    fun read(event: DragAndDropEvent): DroppedData?

    /** Where [event] is, in the root's coordinates, where the platform says. */
    fun position(event: DragAndDropEvent): Offset?
}

/** This platform's [DropReader]. */
@Composable
internal expect fun rememberDropReader(): DropReader

/**
 * A target's side of a platform drag: the callbacks foundation's
 * `dragAndDropTarget` calls, made once for the life of a target's state.
 *
 * Once, and not per composition, because a drop list reads its own layout as it
 * composes: a modifier that is new each time re-lays the list out, which
 * recomposes it, which makes the modifier anew — and the list never settles.
 */
internal class ExternalDropHandle(
    private val external: () -> ExternalDrops?,
    private val reader: () -> DropReader?,
    private val status: (DropStatus) -> Unit,
    private val over: (Offset?) -> Unit,
    private val index: () -> Int?,
) {
    /** Whether there is anything to take drags for. */
    val active: Boolean get() = external() != null && reader() != null

    val shouldStart: (DragAndDropEvent) -> Boolean = { event ->
        val terms = external()
        val read = reader()
        terms != null && read != null && read.info(event)?.let(terms.accepts) == true
    }

    val target: DragAndDropTarget = object : DragAndDropTarget {
        override fun onStarted(event: DragAndDropEvent) = status(DropStatus.Available)
        override fun onEntered(event: DragAndDropEvent) = status(DropStatus.Over)
        override fun onMoved(event: DragAndDropEvent) = over(reader()?.position(event))
        override fun onExited(event: DragAndDropEvent) {
            over(null)
            status(DropStatus.Available)
        }
        override fun onEnded(event: DragAndDropEvent) {
            over(null)
            status(DropStatus.Idle)
        }
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val read = reader() ?: return false
            val terms = external() ?: return false
            over(read.position(event))
            val data = read.read(event) ?: return false
            terms.onDrop(data, index())
            return true
        }
    }
}

/** Takes platform drags on this element through [handle], while it has terms to take them on. */
internal fun Modifier.externalDrops(handle: ExternalDropHandle, enabled: Boolean): Modifier =
    if (!enabled || !handle.active) this else this.dragAndDropTarget(handle.shouldStart, handle.target)
