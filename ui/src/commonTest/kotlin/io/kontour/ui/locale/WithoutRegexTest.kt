package io.kontour.ui.locale

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The patterns the theme stopped using, held to what they did.
 *
 * On the web, five small `Regex` calls brought a regular-expression engine into
 * every app with a theme, so each became a loop. The patterns are kept here,
 * where a test costs nothing to ship, and every loop has to agree with its own
 * pattern exactly: on every CLDR plural rule, and on strings picked for the
 * edges of each one.
 */
class WithoutRegexTest {

    @Test
    fun aPluralRuleSplitsAsThePatternSplitIt() {
        val pattern = Regex("""\.\.|!=|[=%,]|[a-z]+|\d+(?:\.\d+)?""")
        val rules = CldrPluralRules.values.flatten().map { (_, rule) -> rule.substringBefore('@').trim() }
        val edges = listOf("", "n = 1", "n=1", "i = 2..4", "n % 10 = 1.5", "1.", "1..", ".5", "v != 0 or f = 1,3", "@integer 1", "N = 1")
        for (text in rules + edges) {
            assertEquals(pattern.findAll(text).map { it.value }.toList(), pluralRuleTokens(text), "'$text'")
        }
    }

    @Test
    fun runsOfSpacesCollapseAsThePatternCollapsedThem() {
        val pattern = Regex("[   ]{2,}")
        val texts = listOf(
            "", " ", "  ", "d MMMM  y", "d  MMMM", "8:31  am", "a   b", "a b", "  lead", "trail  ",
            " ", "a b", "x\t\ty",
        )
        for (text in texts) {
            assertEquals(text.replace(pattern, " "), collapseSpaces(text), "'$text'")
        }
    }

    @Test
    fun aPlainDecimalIsWhatThePatternMatched() {
        val pattern = Regex("""-?(\d+\.?\d*|\.\d+)""")
        val texts = listOf(
            "", "-", ".", "-.", "5", "-5", "5.", "5.2", ".5", "-.5", "1.2.3", "--5", "5-", "1e5", " 5", "5 ", "12345.6789", "0.", "a",
        )
        for (text in texts) {
            assertEquals(pattern.matches(text), isPlainDecimal(text), "'$text'")
        }
    }
}
