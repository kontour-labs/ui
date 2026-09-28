package io.kontour.ui.locale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The interpreter agrees with CLDR about every number CLDR gives as an example.
 *
 * Each rule in the table carries the samples CLDR published with it — `@integer
 * 2~4, 22~24, …` and `@decimal 0.0, 1.5, …` — and each sample must come out in
 * that rule's category. That is every language and every category in the table,
 * checked against the source's own word, which is a far better test of a small
 * expression interpreter than any list written here.
 */
class CldrPluralsTest {

    @Test
    fun everyCldrSampleLandsInItsCategory() {
        val wrong = mutableListOf<String>()
        var checked = 0
        for ((language, rules) in CldrPluralRules) {
            for ((name, rule) in rules) {
                val expected = PluralCategory.entries.first { it.name.lowercase() == name }
                for (sample in samplesOf(rule)) {
                    val actual = if ('.' in sample) {
                        PluralRules.categoryOf(language, sample)
                    } else {
                        PluralRules.categoryOf(language, sample.toLong())
                    }
                    checked++
                    if (actual != expected) wrong += "$language $sample: $actual, CLDR says $expected"
                }
            }
        }
        assertTrue(checked > 2000, "only $checked samples were checked, so the table was not read")
        assertTrue(wrong.isEmpty(), "${wrong.size} of $checked samples disagree with CLDR:\n" + wrong.take(30).joinToString("\n"))
    }

    @Test
    fun theLanguagesPeopleAskAbout() {
        assertEquals(PluralCategory.One, PluralRules.categoryOf("en", 1))
        assertEquals(PluralCategory.Other, PluralRules.categoryOf("en", "1.0"))
        assertEquals(PluralCategory.Few, PluralRules.categoryOf("pl", 2))
        assertEquals(PluralCategory.Many, PluralRules.categoryOf("pl", 5))
        assertEquals(PluralCategory.Few, PluralRules.categoryOf("pl", 22))
        assertEquals(PluralCategory.Many, PluralRules.categoryOf("pl", 12))
        assertEquals(PluralCategory.Zero, PluralRules.categoryOf("ar", 0))
        assertEquals(PluralCategory.Two, PluralRules.categoryOf("ar", 2))
        assertEquals(PluralCategory.Few, PluralRules.categoryOf("ar", 3))
        assertEquals(PluralCategory.Many, PluralRules.categoryOf("ar", 11))
        assertEquals(PluralCategory.Other, PluralRules.categoryOf("ja", 1))
        assertEquals(PluralCategory.One, PluralRules.categoryOf("pt-BR", 0), "a region falls back to its language")
        assertEquals(PluralCategory.Other, PluralRules.categoryOf("xx", 1), "an unknown language has only other")
    }

    /** The whole numbers and decimals a rule's samples list, with ranges' ends and `…` left out. */
    private fun samplesOf(rule: String): List<String> {
        val samples = mutableListOf<String>()
        for (section in listOf("@integer", "@decimal")) {
            val text = rule.substringAfter(section, "").substringBefore('@').trim()
            if (text.isEmpty()) continue
            for (piece in text.split(',').map { it.trim() }) {
                if (piece.isEmpty() || piece == "…") continue
                // Compact forms, `1c3`, are not numbers this library writes.
                if ('c' in piece || 'e' in piece) continue
                for (end in piece.split('~')) samples += end.trim()
            }
        }
        return samples
    }
}
