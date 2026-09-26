package io.kontour.ui.foundation

/**
 * The inline half of Markdown, read into a tree [RichTextScope.markdown] can
 * draw.
 *
 * **Inline only, on purpose.** What this is for is a string an app already has
 * — a notice, a hint, a translated sentence — where the emphasis has to travel
 * with the words. A sentence assembled from fragments around a bold span cannot
 * be translated, because another language puts the bold word somewhere else;
 * `"Services on the **Midland** line are delayed"` can be. Headings, lists and
 * block quotes are layout, and layout is what composables are for, so a `#` or a
 * `-` at the start of a line is left as written.
 *
 * What it reads:
 *
 * | Written | Drawn |
 * |---|---|
 * | `**bold**`, `__bold__` | bold |
 * | `*italic*`, `_italic_` | italic |
 * | `` `code` ``, ``` `` a ` inside `` ``` | code |
 * | `~~struck~~` | struck through |
 * | `[label](https://…)` | a link, with the label read the same way |
 * | `\*` and any other backslashed punctuation | that character, literally |
 *
 * It follows CommonMark where CommonMark is simple and is plainer where it is
 * not:
 *
 * - A delimiter opens only before a character that is not a space and closes
 *   only after one, so `2 * 3 * 4` is arithmetic.
 * - An underscore also has to sit at a word boundary, so `snake_case_name`
 *   keeps its underscores.
 * - Anything that does not close is text. Nothing is ever dropped, and nothing
 *   fails: a string with a stray asterisk shows the asterisk.
 * - A link whose address is not `http`, `https`, `mailto` or `tel` is drawn as
 *   its label, unlinked. A string can come from anywhere, and a `javascript:`
 *   address is not something a label should be able to smuggle in.
 *
 * One pass, left to right: each delimiter looks ahead for its closer once, so
 * the cost is linear in the length for anything a person would write.
 */
internal fun parseInlineMarkdown(source: String): List<InlineSpan> =
    InlineMarkdownReader(source).read(0, source.length)

/** One piece of a parsed string. */
internal sealed interface InlineSpan {
    data class Plain(val text: String) : InlineSpan
    data class Bold(val children: List<InlineSpan>) : InlineSpan
    data class Italic(val children: List<InlineSpan>) : InlineSpan
    data class Strikethrough(val children: List<InlineSpan>) : InlineSpan
    data class Code(val text: String) : InlineSpan
    data class Link(val children: List<InlineSpan>, val url: String) : InlineSpan
}

/** The schemes a link may point at; anything else is drawn as its label. */
internal fun isLinkableUrl(url: String): Boolean {
    val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase()
    return scheme in LinkableSchemes
}

private val LinkableSchemes = setOf("http", "https", "mailto", "tel")

/** The characters a backslash makes literal, which is CommonMark's list. */
private const val Escapable = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

private class InlineMarkdownReader(private val source: String) {

    /** The spans in `[from, to)`. */
    fun read(from: Int, to: Int): List<InlineSpan> {
        val out = mutableListOf<InlineSpan>()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) {
                out += InlineSpan.Plain(plain.toString())
                plain.clear()
            }
        }

        var i = from
        while (i < to) {
            val c = source[i]
            when {
                c == '\\' && i + 1 < to && source[i + 1] in Escapable -> {
                    plain.append(source[i + 1])
                    i += 2
                }
                c == '`' -> {
                    val run = runLength(i, to, '`')
                    val close = findBacktickCloser(i + run, to, run)
                    if (close < 0) {
                        plain.append(source, i, i + run)
                        i += run
                    } else {
                        flush()
                        out += InlineSpan.Code(trimCode(source.substring(i + run, close)))
                        i = close + run
                    }
                }
                c == '[' -> {
                    val link = readLink(i, to)
                    if (link == null) {
                        plain.append(c)
                        i++
                    } else {
                        val (labelEnd, url, end) = link
                        val label = read(i + 1, labelEnd)
                        flush()
                        if (isLinkableUrl(url)) out += InlineSpan.Link(label, url) else out += label
                        i = end
                    }
                }
                c == '*' || c == '_' || c == '~' -> {
                    val run = runLength(i, to, c)
                    val found = readEmphasis(i, to, c, run)
                    if (found == null) {
                        plain.append(source, i, i + run)
                        i += run
                    } else {
                        val (literal, span, next) = found
                        plain.append(source, i, i + literal)
                        flush()
                        out += span
                        i = next
                    }
                }
                else -> {
                    plain.append(c)
                    i++
                }
            }
        }
        flush()
        return merge(out)
    }

    /**
     * A span opened by the delimiter run at [at], or null when nothing closes it.
     *
     * Returns how many of the run's characters are left over as text, the span,
     * and the index just past its closer. The widest reading is tried first —
     * three stars are bold and italic at once — and a narrower one when that
     * does not close, with the spare stars kept as text: `***a**` is a star and
     * then bold.
     */
    private fun readEmphasis(at: Int, to: Int, c: Char, run: Int): Triple<Int, InlineSpan, Int>? {
        // Tildes only mean something in pairs; one is a character.
        if (c == '~' && run != 2) return null
        if (!canOpen(at, run, c)) return null
        val widths = if (c == '~') listOf(2) else listOf(3, 2, 1).filter { it <= run }
        for (width in widths) {
            val close = findCloser(at + run, to, c, width)
            if (close < 0) continue
            val children = read(at + run, close)
            val span = when {
                c == '~' -> InlineSpan.Strikethrough(children)
                width == 3 -> InlineSpan.Bold(listOf(InlineSpan.Italic(children)))
                width == 2 -> InlineSpan.Bold(children)
                else -> InlineSpan.Italic(children)
            }
            return Triple(run - width, span, close + width)
        }
        return null
    }

    /** The first run of exactly [width] [c]s after [from] that can close, or -1. */
    private fun findCloser(from: Int, to: Int, c: Char, width: Int): Int {
        var i = from
        while (i < to) {
            val ch = source[i]
            when {
                ch == '\\' && i + 1 < to && source[i + 1] in Escapable -> i += 2
                ch == '`' -> {
                    // A code span is opaque: a star inside one is a star.
                    val run = runLength(i, to, '`')
                    val close = findBacktickCloser(i + run, to, run)
                    i = if (close < 0) i + run else close + run
                }
                ch == c -> {
                    val run = runLength(i, to, c)
                    // A run of the same width closes; so does a run of three,
                    // from its end, so `*a **b***` closes the bold and then the
                    // italic. A pair met while looking for one star is a bold
                    // span of its own and is stepped over, not split.
                    if ((run == width || run == 3) && canClose(i, run, c)) {
                        return i + run - width
                    }
                    i += run
                }
                else -> i++
            }
        }
        return -1
    }

    private fun canOpen(at: Int, run: Int, c: Char): Boolean {
        val next = source.getOrNull(at + run) ?: return false
        if (next.isWhitespace()) return false
        if (c == '_') {
            val before = source.getOrNull(at - 1)
            if (before != null && before.isLetterOrDigit()) return false
        }
        return true
    }

    private fun canClose(at: Int, run: Int, c: Char): Boolean {
        val before = source.getOrNull(at - 1) ?: return false
        if (before.isWhitespace()) return false
        if (c == '_') {
            val after = source.getOrNull(at + run)
            if (after != null && after.isLetterOrDigit()) return false
        }
        return true
    }

    /**
     * `[label](url)` starting at [at]: the index of the closing `]`, the URL,
     * and the index just past the `)`. Null when it is not a link.
     */
    private fun readLink(at: Int, to: Int): Triple<Int, String, Int>? {
        var depth = 0
        var i = at
        var labelEnd = -1
        while (i < to) {
            val ch = source[i]
            when {
                ch == '\\' && i + 1 < to && source[i + 1] in Escapable -> i++
                ch == '`' -> {
                    val run = runLength(i, to, '`')
                    val close = findBacktickCloser(i + run, to, run)
                    if (close >= 0) i = close + run - 1 else i += run - 1
                }
                ch == '[' -> depth++
                ch == ']' -> {
                    depth--
                    if (depth == 0) {
                        labelEnd = i
                        break
                    }
                }
            }
            i++
        }
        if (labelEnd < 0 || labelEnd + 1 >= to || source[labelEnd + 1] != '(') return null
        var parens = 0
        var j = labelEnd + 1
        while (j < to) {
            when (source[j]) {
                '(' -> parens++
                ')' -> {
                    parens--
                    if (parens == 0) {
                        val url = source.substring(labelEnd + 2, j).trim()
                        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
                        return Triple(labelEnd, url, j + 1)
                    }
                }
            }
            j++
        }
        return null
    }

    private fun findBacktickCloser(from: Int, to: Int, width: Int): Int {
        var i = from
        while (i < to) {
            if (source[i] == '`') {
                val run = runLength(i, to, '`')
                if (run == width) return i
                i += run
            } else {
                i++
            }
        }
        return -1
    }

    private fun runLength(at: Int, to: Int, c: Char): Int {
        var i = at
        while (i < to && source[i] == c) i++
        return i - at
    }

    /** CommonMark's one trim: a single space each side, so `` ` `` `` can hold a backtick. */
    private fun trimCode(text: String): String =
        if (text.length >= 2 && text.first() == ' ' && text.last() == ' ' && text.isNotBlank()) {
            text.substring(1, text.length - 1)
        } else {
            text
        }
}

/** Adjacent plain pieces as one, so a tree compares equal however it was reached. */
private fun merge(spans: List<InlineSpan>): List<InlineSpan> {
    val out = mutableListOf<InlineSpan>()
    for (span in spans) {
        val last = out.lastOrNull()
        when {
            span is InlineSpan.Plain && span.text.isEmpty() -> Unit
            span is InlineSpan.Plain && last is InlineSpan.Plain ->
                out[out.lastIndex] = InlineSpan.Plain(last.text + span.text)
            else -> out += span
        }
    }
    return out
}
