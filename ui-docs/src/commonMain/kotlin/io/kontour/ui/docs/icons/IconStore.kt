package io.kontour.ui.docs.icons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.ui.docs.fetchText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Where the icon browser's data comes from: a file of `docs/icondata.py`'s
 * layout, by its path under `icons/`, or null when it cannot be had.
 */
internal fun interface IconSource {
    suspend fun fetch(path: String): String?
}

/** The site's own data, beside the page, as `docs/lay-out-site.py` lays it out. */
internal val SiteIconSource = IconSource { path -> fetchText("icons/$path") }

/**
 * The index, once, and each chunk of paths when an icon in it is first asked
 * for — never the whole set. A chunk asked for twice while it is on its way is
 * fetched once, and one that failed is fetched again the next time it is asked
 * for, rather than remembered as missing.
 *
 * Everything runs on the composition's own thread, so the maps need no lock.
 */
internal class IconStore(private val source: IconSource, private val scope: CoroutineScope) {

    var index: IconIndex? by mutableStateOf(null)
        private set

    /** Whether the index could not be had: no data beside the site, or no network. */
    var failed: Boolean by mutableStateOf(false)
        private set

    private var dir: String? = null
    private val chunks = HashMap<Pair<IconStyle, Int>, Deferred<Map<String, List<String>>?>>()
    private val vectors = HashMap<Pair<IconStyle, String>, ImageVector>()

    /** Loads the index, if it is not loaded; again after a failure. */
    suspend fun load() {
        if (index != null) return
        failed = false
        val name = source.fetch("manifest.txt")?.trim()?.takeIf { it.isNotEmpty() && '/' !in it && '<' !in it }
        val parsed = name?.let { source.fetch("$it/index.txt") }?.let(IconIndex::parse)
        if (parsed == null) {
            failed = true
            return
        }
        dir = name
        index = parsed
    }

    /** [entry] in [style], if it has already been drawn once. */
    fun cached(style: IconStyle, entry: IconEntry): ImageVector? = vectors[style to entry.name]

    /** [entry] in [style], fetching its chunk if it has to; null if it cannot be had. */
    suspend fun vector(style: IconStyle, entry: IconEntry): ImageVector? {
        cached(style, entry)?.let { return it }
        val index = index ?: return null
        val dir = dir ?: return null
        val at = if (style == IconStyle.Filled) entry.filledAt else entry.outlineAt
        if (at < 0) return null
        val key = style to at / index.chunkSize
        val chunk = chunks.getOrPut(key) {
            scope.async {
                val number = (at / index.chunkSize).toString().padStart(3, '0')
                source.fetch("$dir/${style.dir}/$number.txt")?.let(::parseChunk)
            }
        }
        val paths = chunk.await()
        if (paths == null) {
            // Asked again the next time, rather than missing for good.
            if (chunks[key] === chunk) chunks.remove(key)
            return null
        }
        val vector = paths[entry.name]?.let { tablerVector(entry.name, it) } ?: return null
        vectors[style to entry.name] = vector
        return vector
    }
}
