package io.kontour.ui.docs.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Tabler's two sets, as the library names them: `Tabler.Outline`, `Tabler.Filled`. */
internal enum class IconStyle(
    /** The directory its paths are laid out in, and the package's last part. */
    val dir: String,
    /** The object in `Tabler` that holds it. */
    val objectName: String,
) {
    Outline("outline", "Outline"),
    Filled("filled", "Filled"),
}

/**
 * One icon, as the index lists it: Tabler's name, its category and tags, and
 * where its paths are.
 */
internal class IconEntry(
    /** Tabler's own name, `arrow-back-up`. */
    val name: String,
    val category: String,
    /** Tabler's tags, lower case, without the name's own words. */
    val tags: List<String>,
    val hasFilled: Boolean,
    /** Its place in the index, which is its place among the outline paths. */
    val outlineAt: Int,
    /** Its place among the filled paths, or -1 for an icon with no filled form. */
    val filledAt: Int,
) {
    /** The name's words: `arrow`, `back`, `up`. */
    val parts: List<String> = name.split('-')

    /**
     * The Kotlin property: each part capitalised, so `a-b-2` is `AB2` and
     * `auth-2fa` is `Auth2fa` — the rule `kontour-labs/tabler` names them by.
     */
    val property: String = parts.joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }

    /** The tags' words, for a word of a query to start: `ash-bin` is `ash` and `bin`. */
    val tagWords: List<String> = tags.flatMap { tag -> tag.split(' ', '-', '/', '_') }.filter { it.isNotEmpty() }

    val categoryWords: List<String> = category.lowercase().split(' ').filter { it.isNotEmpty() }

    /** How the library is asked for it. */
    fun reference(style: IconStyle): String = "Tabler.${style.objectName}.$property"

    /** The import that brings [reference] into scope. */
    fun import(style: IconStyle): String = "import io.kontour.icons.tabler.${style.dir}.$property"

    override fun toString(): String = name
}

/**
 * Every icon the library has, read from the `index.txt` that
 * `docs/icondata.py` lays out: a header line, then an icon a line.
 */
internal class IconIndex(
    val version: String,
    val entries: List<IconEntry>,
    /** How many icons each chunk file holds. */
    val chunkSize: Int,
) {
    val filledCount: Int = entries.count { it.hasFilled }

    /** How many icons there are in [style]: every one in outline, and those with a filled form. */
    fun count(style: IconStyle): Int = if (style == IconStyle.Filled) filledCount else entries.size

    companion object {
        /**
         * Reads an index, or null for one that is not an index — a host's 404
         * page, say, served with a 200.
         */
        fun parse(text: String): IconIndex? {
            val lines = text.lineSequence().filter { it.isNotEmpty() }.toList()
            val header = lines.firstOrNull()?.split('\t') ?: return null
            if (header.size != 5 || header[0] != "tabler") return null
            val chunk = header[4].toIntOrNull()?.takeIf { it > 0 } ?: return null
            var filled = 0
            val entries = lines.drop(1).mapIndexedNotNull { at, line ->
                val fields = line.split('\t')
                if (fields.size != 4) return@mapIndexedNotNull null
                val hasFilled = fields[3] == "1"
                IconEntry(
                    name = fields[0],
                    category = fields[1],
                    tags = fields[2].split(',').filter { it.isNotEmpty() },
                    hasFilled = hasFilled,
                    outlineAt = at,
                    filledAt = if (hasFilled) filled++ else -1,
                )
            }
            return IconIndex(version = header[1], entries = entries, chunkSize = chunk)
        }
    }
}

/**
 * One chunk file's icons, by name, each as its encoded paths: a line is
 * `name <TAB> path <TAB> path…`.
 */
internal fun parseChunk(text: String): Map<String, List<String>> =
    text.lineSequence().filter { it.isNotEmpty() }.associate { line ->
        val fields = line.split('\t')
        fields[0] to fields.drop(1)
    }

/**
 * An icon drawn from its encoded paths exactly as `kontour-labs/tabler` builds
 * `Tabler.Outline.*`: a 24-unit viewport, black where tinted, stroked at 2 with
 * round caps and joins, and the same first path that draws nothing.
 *
 * Each path is `<fill><stroke>;<alpha>;<evenOdd>;<d>` — `docs/icondata.py`
 * applies each style's defaults, so these are what the path does, not what it
 * says.
 *
 * **The empty first path.** Compose's Skia renderer — the web's and the
 * desktop's — records the paint's join as round without passing it to Skia, and
 * then passes on only a join that differs from the one it recorded, so the first
 * round join never arrives and an icon's corners come out mitred. A miter path
 * that draws nothing makes every round join after it a change. The library's
 * generated icons start with the same one, which is why they are drawn right.
 */
internal fun tablerVector(name: String, paths: List<String>): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    val encoded = paths.map { it.split(';', limit = 4) }.filter { it.size == 4 && it[0].length == 2 }
    if (encoded.any { it[0][1] == '1' }) {
        builder.addPath(
            pathData = listOf(PathNode.MoveTo(0f, 0f)),
            stroke = SolidColor(Color.Transparent),
            strokeLineWidth = 1f,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }
    for ((flags, alpha, evenOdd, d) in encoded) {
        val fill = flags[0] == '1'
        val stroke = flags[1] == '1'
        val opacity = alpha.toFloatOrNull() ?: 1f
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            pathFillType = if (evenOdd == "1") PathFillType.EvenOdd else PathFillType.NonZero,
            fill = if (fill) SolidColor(Color.Black) else null,
            fillAlpha = if (fill) opacity else 1f,
            stroke = if (stroke) SolidColor(Color.Black) else null,
            strokeAlpha = if (stroke) opacity else 1f,
            strokeLineWidth = if (stroke) 2f else 0f,
            strokeLineCap = if (stroke) StrokeCap.Round else StrokeCap.Butt,
            strokeLineJoin = if (stroke) StrokeJoin.Round else StrokeJoin.Miter,
        )
    }
    return builder.build()
}
