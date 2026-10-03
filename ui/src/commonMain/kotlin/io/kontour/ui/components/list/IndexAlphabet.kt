package io.kontour.ui.components.list

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.intl.Locale
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.locale.LocaleTag
import io.kontour.ui.locale.likelyScript

/**
 * The labels an [IndexScroller] runs down, and which of them a piece of text
 * files under.
 *
 * ```kotlin
 * val alphabet = IndexAlphabet.forLocale(Theme.locale)       // the reader's script, then Latin, then "#"
 * val swedish = IndexAlphabet.Latin + IndexAlphabet(listOf("Å", "Ä", "Ö"))
 * ```
 *
 * One per writing system, because each files its words its own way. Japanese
 * files under the first sound's row — `か` for `が` and `カ` alike; Korean
 * under a syllable's first consonant; Hebrew and Arabic fold a letter's final
 * and hamza forms into the letter. Latin, Cyrillic and Greek lose their accents
 * and their case. Whatever none of the labels take — a digit, an emoji, a
 * Chinese character — goes under [other], after them.
 *
 * **An exact match comes first**, so an alphabet of your own wins over the
 * folding: a Swedish one with `Å`, `Ä` and `Ö` files `Ödla` under `Ö`, where the
 * fold alone would put it under `O`.
 */
@Immutable
class IndexAlphabet(
    /** In order, top to bottom. */
    val labels: List<String>,
    /** Where anything the labels do not take goes: the last label on the rail. */
    val other: String = "#",
) {
    private val set = labels.toSet()

    /**
     * The label [text] files under: one of [labels], or [other].
     *
     * By its first grapheme, as [formats] reads one — an accented letter, a
     * syllable, a flag — tried as it is, then capitalised, then by its own
     * script's rule, then folded.
     */
    fun labelFor(text: String, formats: LocaleFormats): String {
        val first = formats.firstGrapheme(text.trimStart())
        if (first.isEmpty()) return other
        if (first in set) return first
        val upper = formats.uppercase(first)
        if (upper in set) return upper
        scriptLabel(first)?.let { if (it in set) return it }
        val folded = formats.uppercase(formats.foldForSearch(first)).take(1)
        if (folded in set) return folded
        return other
    }

    /** These labels, then [more]'s that are not already here: Russian's, then Latin's. */
    operator fun plus(more: IndexAlphabet): IndexAlphabet =
        IndexAlphabet(labels + more.labels.filter { it !in set }, other)

    override fun equals(other: Any?): Boolean =
        other is IndexAlphabet && other.labels == labels && other.other == this.other

    override fun hashCode(): Int = labels.hashCode() * 31 + other.hashCode()

    override fun toString(): String = "IndexAlphabet(${labels.joinToString("")}, other=$other)"

    companion object {
        /** A to Z. */
        val Latin: IndexAlphabet = IndexAlphabet(('A'..'Z').map { it.toString() })

        /** Russian's: Ё files under Е, and the hard and soft signs begin no word. */
        val Cyrillic: IndexAlphabet = IndexAlphabet(
            "А Б В Г Д Е Ж З И Й К Л М Н О П Р С Т У Ф Х Ц Ч Ш Щ Э Ю Я".split(' '),
        )

        /** Ukrainian's, which has Ґ, Є, І and Ї of its own and no Ё, Ы or Э. */
        private val Ukrainian: IndexAlphabet = IndexAlphabet(
            "А Б В Г Ґ Д Е Є Ж З И І Ї Й К Л М Н О П Р С Т У Ф Х Ц Ч Ш Щ Ю Я".split(' '),
        )

        /** Α to Ω; an accented vowel files under its own. */
        val Greek: IndexAlphabet = IndexAlphabet(
            "Α Β Γ Δ Ε Ζ Η Θ Ι Κ Λ Μ Ν Ξ Ο Π Ρ Σ Τ Υ Φ Χ Ψ Ω".split(' '),
        )

        /** Aleph to tav; a final form files under its letter. */
        val Hebrew: IndexAlphabet = IndexAlphabet(
            "א ב ג ד ה ו ז ח ט י כ ל מ נ ס ע פ צ ק ר ש ת".split(' '),
        )

        /** In the order of the dictionary; an alif with hamza files under alif. */
        val Arabic: IndexAlphabet = IndexAlphabet(
            "ا ب ت ث ج ح خ د ذ ر ز س ش ص ض ط ظ ع غ ف ق ك ل م ن ه و ي".split(' '),
        )

        /** The heads of the kana rows, as a Japanese contacts list runs. */
        val Kana: IndexAlphabet = IndexAlphabet("あ か さ た な は ま や ら わ".split(' '))

        /** The fourteen initial consonants; a doubled one files under its single. */
        val Hangul: IndexAlphabet = IndexAlphabet(
            "ㄱ ㄴ ㄷ ㄹ ㅁ ㅂ ㅅ ㅇ ㅈ ㅊ ㅋ ㅌ ㅍ ㅎ".split(' '),
        )

        /**
         * The alphabet for [locale]'s script, then Latin's for the names a list in
         * any language has some of, then [other]. Latin alone for a script with
         * no alphabet here: Chinese is indexed by something other than its
         * characters, and is left to the app.
         */
        fun forLocale(locale: Locale): IndexAlphabet {
            val tag = LocaleTag.of(locale)
            val script = tag.script.ifEmpty { likelyScript(tag.language) }
            val own = when (script) {
                "Cyrl" -> if (tag.language == "uk") Ukrainian else Cyrillic
                "Grek" -> Greek
                "Hebr" -> Hebrew
                "Arab" -> Arabic
                "Jpan" -> Kana
                "Kore" -> Hangul
                else -> null
            }
            return if (own == null) Latin else own + Latin
        }
    }
}

/**
 * The label a script's own rule gives [grapheme], before any folding, or null:
 * a kana's row, a Hangul syllable's first consonant, a Hebrew letter's
 * non-final form, an Arabic letter without its hamza.
 */
internal fun scriptLabel(grapheme: String): String? {
    val c = grapheme.firstOrNull() ?: return null
    return when (c) {
        // The small ka and ke, which sort with their row.
        'ゕ', 'ゖ', 'ヵ', 'ヶ' -> "か"
        in 'ぁ'..'ゖ' -> kanaRow(c)
        // Katakana to hiragana: the same sounds, a fixed distance apart.
        in 'ァ'..'ヶ' -> kanaRow(c - 0x60)
        in '가'..'힣' -> HangulInitials[(c - '가') / 588].toString()
        in 'ㄱ'..'ㅎ' -> FoldedJamo[c]?.toString() ?: c.toString()
        else -> HebrewFinals[c]?.toString() ?: ArabicForms[c]?.toString()
    }
}

/** The head of the row a hiragana belongs to: か for が, あ for ぁ. */
private fun kanaRow(c: Char): String? {
    val index = KanaOrder.indexOf(c)
    if (index < 0) return null
    return KanaRows.last { it.second <= index }.first
}

/** The hiragana in order, the small and voiced ones beside their plain ones. */
private const val KanaOrder =
    "ぁあぃいぅうゔぇえぉお" +
        "かがきぎくぐけげこご" +
        "さざしじすずせぜそぞ" +
        "ただちぢっつづてでとど" +
        "なにぬねの" +
        "はばぱひびぴふぶぷへべぺほぼぽ" +
        "まみむめも" +
        "ゃやゅゆょよ" +
        "らりるれろ" +
        "ゎわゐゑをん"

/** Each row's head, and where its kana start in [KanaOrder]. */
private val KanaRows: List<Pair<String, Int>> = listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ")
    .map { head -> head to KanaOrder.indexOf(if (head == "あ") 'ぁ' else if (head == "や") 'ゃ' else if (head == "わ") 'ゎ' else head[0]) }

/** The nineteen initial consonants of a Hangul syllable, the doubled ones folded. */
private const val HangulInitials = "ㄱㄱㄴㄷㄷㄹㅁㅂㅂㅅㅅㅇㅈㅈㅊㅋㅌㅍㅎ"

/** A doubled consonant written on its own, filed under its single. */
private val FoldedJamo = mapOf('ㄲ' to 'ㄱ', 'ㄸ' to 'ㄷ', 'ㅃ' to 'ㅂ', 'ㅆ' to 'ㅅ', 'ㅉ' to 'ㅈ')

private val HebrewFinals = mapOf('ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ')

private val ArabicForms = mapOf(
    'أ' to 'ا', 'إ' to 'ا', 'آ' to 'ا', 'ٱ' to 'ا',
    'ة' to 'ه', 'ى' to 'ي', 'ؤ' to 'و', 'ئ' to 'ي',
)

/** Where a section starts in a list: the label it files under, and the item it starts at. */
@Immutable
class IndexSection(val label: String, val itemIndex: Int) {
    override fun equals(other: Any?): Boolean =
        other is IndexSection && other.label == label && other.itemIndex == itemIndex

    override fun hashCode(): Int = label.hashCode() * 31 + itemIndex

    override fun toString(): String = "IndexSection($label at $itemIndex)"
}

/** The items under one label, in the order they came. */
@Immutable
class IndexedGroup<T>(val label: String, val items: List<T>)

/**
 * A list grouped under an [IndexAlphabet]'s labels, and where each group starts
 * in a `LazyColumn` laid out from it — what an [IndexScroller] jumps to.
 */
@Immutable
class IndexedItems<T>(val groups: List<IndexedGroup<T>>, val sections: List<IndexSection>)

/**
 * These items grouped under [alphabet]'s labels, in the alphabet's order and
 * each group in the order its items came, with [IndexedItems.sections] saying
 * where each group starts in a list that shows them.
 *
 * ```kotlin
 * val indexed = remember(stops, formats) { stops.indexedBy(alphabet, formats) { it.name } }
 * LazyColumn(state = list) {
 *     indexed.groups.forEach { group ->
 *         listSection(group.items, title = { +group.label }) { stop, position -> StopRow(stop, position) }
 *     }
 * }
 * ```
 *
 * @param leadingItems Items the list shows before the first group — a search
 *   field, a pinned row — which every index is counted past.
 * @param headers Whether each group starts with a header item of its own, as
 *   above. Its index is the section's.
 * @param text What an item files under: its name, usually.
 */
fun <T> List<T>.indexedBy(
    alphabet: IndexAlphabet,
    formats: LocaleFormats,
    leadingItems: Int = 0,
    headers: Boolean = true,
    text: (T) -> String,
): IndexedItems<T> {
    val byLabel = LinkedHashMap<String, MutableList<T>>()
    for (item in this) byLabel.getOrPut(alphabet.labelFor(text(item), formats)) { mutableListOf() } += item
    val order = alphabet.labels + alphabet.other
    val groups = order.mapNotNull { label -> byLabel[label]?.let { IndexedGroup(label, it.toList()) } }
    var at = leadingItems
    val sections = groups.map { group ->
        IndexSection(group.label, at).also { at += group.items.size + if (headers) 1 else 0 }
    }
    return IndexedItems(groups, sections)
}

/**
 * What a rail of [count] labels shows in [fits] places: every label when they
 * fit, and otherwise labels with a dot standing for each run left out between
 * them, the first and the last always shown.
 *
 * Pure, and the rail's whole answer to a short window: the finger still runs
 * over the full alphabet, a dot at a time.
 */
internal fun indexSlots(count: Int, fits: Int): List<IndexSlot> {
    if (count <= 0) return emptyList()
    if (fits >= count) return List(count) { IndexSlot.Label(it) }
    // Odd, so it starts and ends on a label and alternates: label, dot, label.
    val slots = (if (fits % 2 == 0) fits - 1 else fits).coerceAtLeast(3)
    val labels = (slots + 1) / 2
    if (labels >= count) return List(count) { IndexSlot.Label(it) }
    val shown = List(labels) { i -> (i.toLong() * (count - 1) / (labels - 1)).toInt() }
    return buildList {
        shown.forEachIndexed { i, label ->
            // Two shown labels side by side leave nothing for a dot to stand for.
            if (i > 0 && label - shown[i - 1] > 1) add(IndexSlot.Dot(shown[i - 1] + 1, label - 1))
            add(IndexSlot.Label(label))
        }
    }
}

/** One place on the rail: a label, or a dot standing for the labels from [first] to [last]. */
internal sealed interface IndexSlot {
    data class Label(val index: Int) : IndexSlot
    data class Dot(val first: Int, val last: Int) : IndexSlot
}
