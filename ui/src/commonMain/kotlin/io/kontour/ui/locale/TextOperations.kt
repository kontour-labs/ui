package io.kontour.ui.locale

/**
 * Where one user-perceived character ends: a grapheme cluster, as UAX #29
 * describes it, closely enough for the uses the library has.
 *
 * Those uses are an avatar's initial and a navigation item's monogram — the
 * first character of a name. `String.take(1)` is a UTF-16 unit, which cuts a
 * flag in half, strips the accent off a decomposed `é`, splits a family emoji
 * into a lone man and a joiner, and turns the Hindi `क्ष` into `क्`.
 *
 * Common rather than per platform, so every target draws the same monogram for
 * the same name. It handles what names contain: combining marks and spacing
 * vowel signs, emoji modifiers and variation selectors, zero-width-joined emoji,
 * flags, Hangul jamo, and the conjuncts Unicode 15.1 keeps together in
 * Devanagari, Bengali, Gujarati, Oriya, Telugu and Malayalam.
 */
internal object Graphemes {

    /** The first grapheme of [text]; empty for empty text. */
    fun first(text: String): String = if (text.isEmpty()) "" else text.substring(0, end(text, 0))

    /** The index just after the grapheme that starts at [start]. */
    fun end(text: String, start: Int): Int {
        if (start >= text.length) return text.length
        val first = codePointAt(text, start)
        var at = start + charCount(first)
        if (first == 0x0D && at < text.length && text[at] == '\n') return at + 1
        if (first < 0x20 || first == 0x7F) return at

        // Flags: two regional indicators, and only two.
        if (isRegionalIndicator(first) && at < text.length) {
            val next = codePointAt(text, at)
            if (isRegionalIndicator(next)) at += charCount(next)
        }

        var previous = first
        var pictographicBase = isPictographic(first)
        var hangul = hangulType(first)
        var afterLinker = false
        val script = indicScriptOf(first)
        while (at < text.length) {
            val next = codePointAt(text, at)
            val joins = when {
                // Hangul: L × (L | V | LV | LVT), (LV | V) × (V | T), (LVT | T) × T.
                hangul != 0 && hangulType(next).let { following ->
                    following != 0 && when (hangul) {
                        HangulL -> following == HangulL || following == HangulV || following == HangulLV || following == HangulLVT
                        HangulLV, HangulV -> following == HangulV || following == HangulT
                        HangulLVT, HangulT -> following == HangulT
                        else -> false
                    }
                } -> true
                isExtend(next) -> true
                // An emoji joined to the one before it by a zero-width joiner.
                previous == ZeroWidthJoiner && pictographicBase && isPictographic(next) -> true
                // A consonant after a virama, in a script that joins them.
                afterLinker && script != 0 && indicScriptOf(next) == script && isIndicConsonant(next) -> true
                else -> false
            }
            if (!joins) break
            if (script != 0 && indicScriptOf(next) == script) {
                if (isIndicLinker(next)) afterLinker = true else if (isIndicConsonant(next)) afterLinker = false
            }
            if (isPictographic(next)) pictographicBase = true
            hangul = hangulType(next)
            previous = next
            at += charCount(next)
        }
        return at
    }

    /** [text] as its code points, each a string — what a password mask counts. */
    fun codePoints(text: String): List<String> = buildList {
        var at = 0
        while (at < text.length) {
            val size = charCount(codePointAt(text, at))
            add(text.substring(at, at + size))
            at += size
        }
    }

    private const val ZeroWidthJoiner = 0x200D
    private const val HangulL = 1
    private const val HangulV = 2
    private const val HangulT = 3
    private const val HangulLV = 4
    private const val HangulLVT = 5

    private fun codePointAt(text: String, index: Int): Int {
        val high = text[index]
        if (high.isHighSurrogate() && index + 1 < text.length) {
            val low = text[index + 1]
            if (low.isLowSurrogate()) return ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
        }
        return high.code
    }

    private fun charCount(codePoint: Int): Int = if (codePoint >= 0x10000) 2 else 1

    private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

    /**
     * Grapheme_Extend and SpacingMark together, which the extended rules treat
     * alike: the marks in the Basic Multilingual Plane by their category, and
     * the supplementary ranges names actually meet — emoji modifiers, variation
     * selectors and tags.
     */
    private fun isExtend(codePoint: Int): Boolean = when {
        codePoint == ZeroWidthJoiner || codePoint == 0x200C -> true
        codePoint in 0xFE00..0xFE0F -> true
        codePoint in 0x1F3FB..0x1F3FF -> true
        codePoint in 0xE0020..0xE007F -> true
        codePoint in 0xE0100..0xE01EF -> true
        codePoint < 0x10000 -> codePoint.toChar().category.let {
            it == CharCategory.NON_SPACING_MARK ||
                it == CharCategory.ENCLOSING_MARK ||
                it == CharCategory.COMBINING_SPACING_MARK
        }
        else -> false
    }

    /** Extended_Pictographic, by the blocks it covers. */
    private fun isPictographic(codePoint: Int): Boolean =
        codePoint == 0x00A9 || codePoint == 0x00AE || codePoint == 0x203C || codePoint == 0x2049 ||
            codePoint == 0x2122 || codePoint == 0x2139 || codePoint in 0x2194..0x21AA ||
            codePoint in 0x231A..0x23FF || codePoint in 0x25AA..0x25FE || codePoint in 0x2600..0x27BF ||
            codePoint in 0x2934..0x2935 || codePoint in 0x2B05..0x2B55 || codePoint == 0x3030 ||
            codePoint == 0x303D || codePoint == 0x3297 || codePoint == 0x3299 ||
            codePoint in 0x1F000..0x1FAFF || codePoint in 0x1FC00..0x1FFFD

    private fun hangulType(codePoint: Int): Int = when (codePoint) {
        in 0x1100..0x115F, in 0xA960..0xA97C -> HangulL
        in 0x1160..0x11A7, in 0xD7B0..0xD7C6 -> HangulV
        in 0x11A8..0x11FF, in 0xD7CB..0xD7FB -> HangulT
        in 0xAC00..0xD7A3 -> if ((codePoint - 0xAC00) % 28 == 0) HangulLV else HangulLVT
        else -> 0
    }

    /** The scripts with InCB=Linker in Unicode 15.1, by the start of their block; 0 for any other. */
    private fun indicScriptOf(codePoint: Int): Int = when (codePoint) {
        in 0x0900..0x097F -> 0x0900
        in 0x0980..0x09FF -> 0x0980
        in 0x0A80..0x0AFF -> 0x0A80
        in 0x0B00..0x0B7F -> 0x0B00
        in 0x0C00..0x0C7F -> 0x0C00
        in 0x0D00..0x0D7F -> 0x0D00
        else -> 0
    }

    private fun isIndicLinker(codePoint: Int): Boolean =
        codePoint == 0x094D || codePoint == 0x09CD || codePoint == 0x0ACD ||
            codePoint == 0x0B4D || codePoint == 0x0C4D || codePoint == 0x0D4D

    private fun isIndicConsonant(codePoint: Int): Boolean = when (indicScriptOf(codePoint)) {
        0x0900 -> codePoint in 0x0915..0x0939 || codePoint in 0x0958..0x095F || codePoint in 0x0978..0x097F
        0x0980 -> codePoint in 0x0995..0x09B9 || codePoint in 0x09DC..0x09DF || codePoint in 0x09F0..0x09F1
        0x0A80 -> codePoint in 0x0A95..0x0AB9 || codePoint == 0x0AF9
        0x0B00 -> codePoint in 0x0B15..0x0B39 || codePoint in 0x0B5C..0x0B5F || codePoint == 0x0B71
        0x0C00 -> codePoint in 0x0C15..0x0C39 || codePoint in 0x0C58..0x0C5A
        0x0D00 -> codePoint in 0x0D15..0x0D3A
        else -> false
    }
}

/**
 * [text] folded for matching: lower case in [formats]' language, accents off.
 *
 * Decomposed first, so `é` written as one code point and `é` written as `e` and
 * an accent fold to the same `e`; then the marks that are accents — Latin,
 * Greek and Cyrillic diacritics, Hebrew points, Arabic vowel marks — are
 * dropped. The marks that are letters in their own scripts, an Indic vowel sign
 * or a Thai tone mark, are kept: dropping them changes the word, not its
 * spelling. `ı` and `i` fold together, so a Turkish reader's `ISTANBUL` finds
 * `istanbul`, and `ß` is `ss`.
 */
internal fun foldText(text: String, formats: LocaleFormats): String {
    val decomposed = decomposeCanonical(formats.lowercase(text))
    return buildString(decomposed.length) {
        for (char in decomposed) {
            when {
                isFoldedMark(char.code) -> Unit
                char == 'ı' -> append('i')
                char == 'ß' -> append("ss")
                char == 'ς' -> append('σ')
                char == 'ſ' -> append('s')
                else -> append(char)
            }
        }
    }
}

private fun isFoldedMark(code: Int): Boolean =
    code in 0x0300..0x036F || code in 0x1AB0..0x1AFF || code in 0x1DC0..0x1DFF ||
        code in 0x20D0..0x20FF || code in 0xFE20..0xFE2F ||
        code in 0x0591..0x05BD || code == 0x05BF || code in 0x05C1..0x05C2 || code in 0x05C4..0x05C5 || code == 0x05C7 ||
        code in 0x064B..0x065F || code == 0x0670

/** Canonical decomposition, NFD, from the platform's own Unicode tables. */
internal expect fun decomposeCanonical(text: String): String

/**
 * [text] as a unit of its own direction inside whatever line it lands in.
 *
 * A count like `+3` or `99+` begins or ends with a neutral character, and a
 * neutral takes the direction of the text around it: in a right-to-left line
 * `+3` reads `3+`. First-strong isolation (U+2068 … U+2069) lays it out by its
 * own first strong character and leaves the line alone; the marks draw nothing.
 */
fun bidiIsolated(text: String): String = "⁨$text⁩"
