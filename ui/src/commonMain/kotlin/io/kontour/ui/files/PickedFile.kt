package io.kontour.ui.files

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

/**
 * A file the reader chose: from a picker, or dropped onto a target.
 *
 * Its name, size and type as the platform reported them, and its contents read
 * on demand — a picked video is not read into memory until something asks.
 * Every platform's picker hands back these, so the code that uploads or parses
 * one is written once.
 */
@Stable
abstract class PickedFile {
    /** The file's name, with its extension: `timetable.pdf`. */
    abstract val name: String

    /** Its size in bytes, where the platform says; `null` where it does not. */
    abstract val size: Long?

    /** Its media type — `application/pdf` — where the platform says. */
    abstract val mimeType: String?

    /** Reads the whole file. */
    abstract suspend fun readBytes(): ByteArray

    /** Reads the whole file as UTF-8 text. */
    open suspend fun readText(): String = readBytes().decodeToString()

    override fun toString(): String = "PickedFile($name, $size bytes, $mimeType)"
}

/**
 * A [PickedFile] made of its parts: for tests, previews, and files that come
 * from somewhere a platform picker does not reach.
 */
fun PickedFile(
    name: String,
    size: Long?,
    mimeType: String?,
    read: suspend () -> ByteArray,
): PickedFile = object : PickedFile() {
    override val name = name
    override val size = size
    override val mimeType = mimeType
    override suspend fun readBytes(): ByteArray = read()
}

/**
 * Which files a picker offers, and a drop takes: media types — `image/png`,
 * or a family, written as `image/` followed by a star for any image — and
 * extensions, `.pdf`.
 *
 * ```kotlin
 * FileTypes.of("image/png", "image/jpeg", ".pdf")
 * ```
 *
 * A file matches if any of them does. A platform picker is asked to show only
 * these where it can, and what comes back is checked again, since not every
 * picker can be asked: a desktop's shows every file, and a phone's can be
 * persuaded to let anything through.
 */
@Immutable
class FileTypes private constructor(
    internal val patterns: List<String>,
) {
    /** The media types among them, for a picker that filters by type. */
    internal val mimeTypes: List<String> get() = patterns.filter { '/' in it }

    /** The extensions among them, without the dot, for a picker that filters by name. */
    internal val extensions: List<String> get() = patterns.filter { it.startsWith(".") }.map { it.drop(1) }

    /** Whether a file with this [name] and [mimeType] is one of these. */
    fun accepts(name: String, mimeType: String?): Boolean {
        if (patterns.isEmpty()) return true
        val type = (mimeType ?: typeFromName(name))?.lowercase()
        val lower = name.lowercase()
        return patterns.any { pattern ->
            when {
                pattern.startsWith(".") -> lower.endsWith(pattern)
                type == null -> false
                pattern.endsWith("/*") -> type.startsWith(pattern.dropLast(1))
                else -> type == pattern
            }
        }
    }

    /** Whether [file] is one of these. */
    fun accepts(file: PickedFile): Boolean = accepts(file.name, file.mimeType)

    override fun equals(other: Any?): Boolean = other is FileTypes && other.patterns == patterns

    override fun hashCode(): Int = patterns.hashCode()

    override fun toString(): String = "FileTypes(${patterns.joinToString()})"

    companion object {
        /** Any file at all. */
        val Any: FileTypes = FileTypes(emptyList())

        /**
         * Files of these media types and extensions. An extension without its
         * dot is taken as one; a type is anything with a slash in it.
         */
        fun of(vararg mimeOrExtension: String): FileTypes = FileTypes(
            mimeOrExtension.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.map { pattern ->
                if ('/' in pattern || pattern.startsWith(".")) pattern else ".$pattern"
            },
        )
    }
}

/**
 * The media type a platform did not report, from the extension, for the common
 * few a filter is likely to name. Anything else stays unknown and matches by
 * extension alone.
 */
internal fun typeFromName(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "svg" -> "image/svg+xml"
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "csv" -> "text/csv"
    "json" -> "application/json"
    "zip" -> "application/zip"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "mp3" -> "audio/mpeg"
    else -> null
}
