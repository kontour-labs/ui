package io.kontour.ui.locale

import androidx.compose.ui.text.intl.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The desktop's formats, against the JDK's own CLDR.
 *
 * Structural rather than exact: the JDK's CLDR moves between releases, and
 * what matters is that German uses a comma, Egyptian Arabic its own digits and
 * Japanese puts the year first — not which space a JDK puts before `%`.
 */
class JvmLocaleFormatsTest {

    private val june = LocalDate(2026, 6, 9)

    @Test
    fun germanWritesACommaAndA24HourClock() {
        val german = LocaleFormats.of(Locale("de-DE"))
        assertEquals("4,5", german.decimal(4.5, 1))
        assertEquals("1.234", german.integer(1234))
        assertEquals(HourCycle.H23, german.conventions.hourCycle)
        assertEquals("14:05", german.time(LocalTime(14, 5)))
        assertTrue("Juni" in german.date(june, DateStyle.Long), german.date(june, DateStyle.Long))
        assertEquals(4.5, german.parseDecimal("4,5"))
    }

    @Test
    fun egyptianArabicUsesItsOwnDigitsUnlessAskedNotTo() {
        val arabic = LocaleFormats.of(Locale("ar-EG"))
        assertEquals('٠', arabic.symbols.zeroDigit)
        assertTrue(arabic.integer(12).all { it in '٠'..'٩' }, arabic.integer(12))
        val latin = LocaleFormats.of(Locale("ar-EG-u-nu-latn"))
        assertEquals("12", latin.integer(12))
        assertEquals(PluralCategory.Two, arabic.pluralCategory(2))
    }

    @Test
    fun japanesePutsTheYearFirst() {
        val japanese = LocaleFormats.of(Locale("ja-JP"))
        assertEquals(DateOrder.YearMonthDay, japanese.conventions.dateOrder)
        assertTrue(japanese.date(june, DateStyle.Long).startsWith("2026"), japanese.date(june, DateStyle.Long))
        assertTrue(japanese.monthAndYear(june).startsWith("2026"), japanese.monthAndYear(june))
    }

    @Test
    fun theClockFollowsTheRegionAndTheTag() {
        assertTrue(LocaleFormats.of(Locale("en-US")).conventions.hourCycle.usesDayPeriod)
        assertFalse(LocaleFormats.of(Locale("de")).conventions.hourCycle.usesDayPeriod)
        val forced = LocaleFormats.of(Locale("en-US-u-hc-h23"))
        assertEquals(HourCycle.H23, forced.conventions.hourCycle)
        assertEquals("14:05", forced.time(LocalTime(14, 5)))
    }

    @Test
    fun englishIsTheLibrarysOwnWithTheRegionsConventions() {
        val australian = LocaleFormats.of(Locale("en-AU"))
        assertEquals("5 Jun 2026", australian.date(LocalDate(2026, 6, 5), DateStyle.Medium))
        val american = LocaleFormats.of(Locale("en-US"))
        assertEquals(DateOrder.MonthDayYear, american.conventions.dateOrder)
        assertEquals("Jun 5, 2026", american.date(LocalDate(2026, 6, 5), DateStyle.Medium))
        assertEquals("2:05 PM", american.time(LocalTime(14, 5)))
    }

    @Test
    fun indianGroupingIsRestored() {
        assertEquals("1,23,45,678", LocaleFormats.of(Locale("en-IN")).integer(12345678))
    }

    @Test
    fun theSameLocaleIsBuiltOnce() {
        assertTrue(LocaleFormats.of(Locale("fr-FR")) === LocaleFormats.of(Locale("fr-FR")))
    }
}
