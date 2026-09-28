package io.kontour.ui.locale

import io.kontour.ui.theme.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/** Every word a pseudo-locale is given comes out changed, and nothing else does. */
class PseudoTest {

    @Test
    fun everyStringIsBracketedAndLonger() {
        val english = Strings()
        val accented = english.pseudoLocalised(PseudoLocale.Accented)
        val fields = Strings::class.java.declaredFields.filter { it.type == String::class.java }
        val untouched = fields.mapNotNull { field ->
            field.isAccessible = true
            val before = field.get(english) as String
            val after = field.get(accented) as String
            if (after.startsWith("[") && after.endsWith("]") && after.length > before.length) null else field.name
        }
        assertTrue(untouched.isEmpty(), "these fields were not pseudo-localised: $untouched")
        val phrase = accented.pageOfCount(LocaleFormats.english().quantity(2), LocaleFormats.english().quantity(10))
        assertTrue(phrase.startsWith("[Þàĝé 2 öƒ 10"), phrase)
    }

    @Test
    fun eachModeDoesWhatItSays() {
        assertEquals("[Ĥéĺĺö ··]", pseudoLocalise("Hello", PseudoLocale.Accented))
        assertEquals("‮Hello‬", pseudoLocalise("Hello", PseudoLocale.Bidi))
        assertEquals("ậ́ 1", pseudoLocalise("a 1", PseudoLocale.Tall))
        assertEquals("", pseudoLocalise("", PseudoLocale.Accented))
    }

    @Test
    fun mirroredFormatsUseOtherDigits() {
        val bidi = LocaleFormats.english().pseudoLocalised(PseudoLocale.Bidi)
        assertEquals("١٬٢٣٤", bidi.integer(1234).replace(',', '٬'))
        assertEquals('٠', bidi.symbols.zeroDigit)
        assertTrue(bidi.date(LocalDate(2026, 6, 5), DateStyle.Medium).contains('٢'))
        assertEquals("١٬٢٣٤", bidi.quantity(1234).formatted.replace(',', '٬'))
        val accented = LocaleFormats.english().pseudoLocalised(PseudoLocale.Accented)
        assertTrue(accented.monthName(Month.JUNE).startsWith("[Ĵûñé"), accented.monthName(Month.JUNE))
    }
}
