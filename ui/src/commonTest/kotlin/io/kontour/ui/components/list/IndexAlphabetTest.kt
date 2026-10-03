package io.kontour.ui.components.list

import androidx.compose.ui.text.intl.Locale
import io.kontour.ui.locale.localeFormatsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which label a name files under, in each script the rail knows, and the rail's
 * arithmetic: where a list's sections start, what a short rail shows, and which
 * letter a finger is over.
 */
class IndexAlphabetTest {

    private val formats = localeFormatsFor(Locale("en"))

    private fun IndexAlphabet.of(vararg names: String) = names.map { labelFor(it, formats) }

    @Test
    fun latinLosesItsAccentsAndItsCase() {
        assertEquals(
            listOf("A", "E", "Z", "S", "B"),
            IndexAlphabet.Latin.of("apple", "Émile", "zoë", "ßtraße", "  bob"),
        )
    }

    @Test
    fun digitsEmojiAndNothingGoUnderOther() {
        assertEquals(listOf("#", "#", "#", "#"), IndexAlphabet.Latin.of("42nd Street", "😀 Smile", "", "東京"))
    }

    @Test
    fun cyrillicKeepsItsOwnLettersAndFilesYoUnderYe() {
        val alphabet = IndexAlphabet.Cyrillic + IndexAlphabet.Latin
        assertEquals(listOf("Е", "Й", "Я", "M"), alphabet.of("Ёлка", "йошкар", "яблоко", "Moscow"))
    }

    @Test
    fun greekAccentedVowelsFileUnderTheirOwn() {
        assertEquals(listOf("Α", "Ω", "Σ"), IndexAlphabet.Greek.of("Άρτα", "ώρα", "σπίτι"))
    }

    @Test
    fun hebrewAndArabicFoldTheirForms() {
        assertEquals(listOf("ש", "מ"), IndexAlphabet.Hebrew.of("שלום", "םם"))
        assertEquals(listOf("ا", "ا", "ا", "ب"), IndexAlphabet.Arabic.of("أحمد", "إبراهيم", "آمنة", "بيروت"))
    }

    @Test
    fun kanaFilesUnderItsRowHiraganaAndKatakanaAlike() {
        assertEquals(
            listOf("か", "か", "あ", "あ", "や", "わ", "わ", "か", "は", "#"),
            IndexAlphabet.Kana.of("がっこう", "カメラ", "ぁ", "ヴィオラ", "ゃ", "を", "ん", "ヶ月", "パン", "東京"),
        )
    }

    @Test
    fun hangulFilesUnderItsFirstConsonantDoublesFolded() {
        assertEquals(
            listOf("ㄱ", "ㄱ", "ㄷ", "ㅎ", "ㅅ", "ㅇ"),
            IndexAlphabet.Hangul.of("김치", "꽃", "떡", "한국", "ㅆ", "아이"),
        )
    }

    @Test
    fun anAlphabetOfYourOwnWinsOverTheFold() {
        val swedish = IndexAlphabet.Latin + IndexAlphabet(listOf("Å", "Ä", "Ö"))
        assertEquals(listOf("Ö", "Å", "O"), swedish.of("Ödla", "Åre", "Oslo"))
        assertEquals("Ö", swedish.labels.last())
        assertEquals(29, swedish.labels.size)
    }

    @Test
    fun eachLocaleGetsItsScriptThenLatin() {
        val japanese = IndexAlphabet.forLocale(Locale("ja"))
        assertEquals("あ", japanese.labels.first())
        assertTrue("A" in japanese.labels)
        val russian = IndexAlphabet.forLocale(Locale("ru"))
        assertEquals("А", russian.labels.first())
        assertTrue("Ы" !in russian.labels && "Э" in russian.labels)
        val ukrainian = IndexAlphabet.forLocale(Locale("uk"))
        assertTrue("Ґ" in ukrainian.labels && "Ї" in ukrainian.labels)
        assertFalse("Ё" in ukrainian.labels)
        assertEquals(IndexAlphabet.Latin, IndexAlphabet.forLocale(Locale("en-GB")))
        assertEquals(IndexAlphabet.Latin, IndexAlphabet.forLocale(Locale("zh")))
    }

    @Test
    fun indexedByGroupsInTheAlphabetsOrderAndCountsHeaders() {
        val names = listOf("banana", "apple", "42nd", "avocado")
        val indexed = names.indexedBy(IndexAlphabet.Latin, formats) { it }
        assertEquals(listOf("A", "B", "#"), indexed.groups.map { it.label })
        assertEquals(listOf("apple", "avocado"), indexed.groups[0].items)
        assertEquals(listOf(IndexSection("A", 0), IndexSection("B", 3), IndexSection("#", 5)), indexed.sections)

        val bare = names.indexedBy(IndexAlphabet.Latin, formats, leadingItems = 1, headers = false) { it }
        assertEquals(listOf(1, 3, 4), bare.sections.map { it.itemIndex })
    }

    @Test
    fun aRailWithRoomShowsEveryLabel() {
        assertEquals(List(27) { IndexSlot.Label(it) }, indexSlots(27, 27))
        assertEquals(List(27) { IndexSlot.Label(it) }, indexSlots(27, 40))
        assertEquals(emptyList(), indexSlots(0, 10))
    }

    @Test
    fun aShortRailShowsTheEndsAndADotForEachRunBetween() {
        for (count in 4..40) for (fits in 1 until count) {
            val slots = indexSlots(count, fits)
            assertEquals(IndexSlot.Label(0), slots.first(), "$count in $fits")
            assertEquals(IndexSlot.Label(count - 1), slots.last(), "$count in $fits")
            assertTrue(slots.size <= maxOf(fits, 3), "$count labels took ${slots.size} slots of $fits")
            // Every label is on the rail exactly once, as itself or under a dot.
            val covered = slots.flatMap {
                when (it) {
                    is IndexSlot.Label -> listOf(it.index)
                    is IndexSlot.Dot -> (it.first..it.last).toList()
                }
            }
            assertEquals((0 until count).toList(), covered, "$count in $fits: $slots")
            assertTrue(slots.zipWithNext().none { (a, b) -> a is IndexSlot.Dot && b is IndexSlot.Dot }, "$count in $fits")
        }
    }

    @Test
    fun aFingerOnADotPassesThroughEachLetterItStandsFor() {
        val slots = listOf(IndexSlot.Label(0), IndexSlot.Dot(1, 3), IndexSlot.Label(4))
        val at = { y: Float -> labelAt(y, slots, top = 0f, slotPx = 10f) }
        assertEquals(listOf(0, 0, 1, 2, 3, 4, 4), listOf(-5f, 5f, 10f, 15f, 19.9f, 25f, 100f).map(at))
        assertEquals(-1, labelAt(5f, emptyList(), 0f, 10f))
    }

    @Test
    fun anEmptyLabelLandsOnTheNextSectionAndPastTheLastOnTheLast() {
        val labels = listOf("A", "B", "C", "D", "#")
        val sections = listOf(IndexSection("A", 0), IndexSection("C", 3), IndexSection("#", 7))
        assertEquals(listOf(0, 1, 1, 2, 2), landingSections(labels, sections).toList())
        assertEquals(listOf(0, 1, 1, 1, 1), landingSections(labels, sections.dropLast(1)).toList())
        assertEquals(listOf(-1, -1), landingSections(listOf("A", "B"), emptyList()).toList())
    }

    @Test
    fun theCurrentSectionIsTheOneTheFirstVisibleItemIsIn() {
        val sections = listOf(IndexSection("A", 1), IndexSection("C", 4))
        assertEquals(listOf(-1, 0, 0, 1, 1), listOf(0, 1, 3, 4, 9).map { sectionAt(sections, it) })
    }
}
