package io.kontour.ui.locale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * The built-in English writes what the library wrote before it had a locale:
 * every picture in the docs was taken with it, and a golden moves if a single
 * character of this does.
 */
class EnglishFormatsTest {

    private val english = LocaleFormats.english()
    private val june = LocalDate(2026, 6, 5)

    @Test
    fun datesInEachLengthAndOrder() {
        assertEquals("5/6/26", english.date(june, DateStyle.Short))
        assertEquals("5 Jun 2026", english.date(june, DateStyle.Medium))
        assertEquals("5 June 2026", english.date(june, DateStyle.Long))
        assertEquals("Friday, 5 June 2026", english.date(june, DateStyle.Full))
        assertEquals("June 2026", english.monthAndYear(june))

        val american = english.withConventions(DateTimeConventions(dateOrder = DateOrder.MonthDayYear))
        assertEquals("Jun 5, 2026", american.date(june, DateStyle.Medium))
        assertEquals("Friday, June 5, 2026", american.date(june, DateStyle.Full))

        val yearFirst = english.withConventions(DateTimeConventions(dateOrder = DateOrder.YearMonthDay))
        assertEquals("2026-06-05", yearFirst.date(june, DateStyle.Short))
    }

    @Test
    fun timesOnEachClock() {
        assertEquals("09:05", english.time(LocalTime(9, 5)))
        assertEquals("00:00", english.time(LocalTime(0, 0)))
        val twelve = english.withConventions(english.conventions.copy(hourCycle = HourCycle.H12))
        assertEquals("12:00 am", twelve.time(LocalTime(0, 0)))
        assertEquals("12:00 pm", twelve.time(LocalTime(12, 0)))
        assertEquals("2:05 pm", twelve.time(LocalTime(14, 5)))
        val eleven = english.withConventions(english.conventions.copy(hourCycle = HourCycle.H11))
        assertEquals("0:05 pm", eleven.time(LocalTime(12, 5)))
        val twentyFour = english.withConventions(english.conventions.copy(hourCycle = HourCycle.H24))
        assertEquals("24:05", twentyFour.time(LocalTime(0, 5)))
        assertEquals("5 Jun 2026, 09:05", english.dateTime(june, LocalTime(9, 5)))
    }

    @Test
    fun theTimeLayoutFollowsTheClock() {
        assertEquals(TimeLayout(listOf(TimePart.Hour, TimePart.Minute), ":", hourPadded = true), english.timeLayout)
        val twelve = english.withConventions(english.conventions.copy(hourCycle = HourCycle.H12))
        assertEquals(
            TimeLayout(listOf(TimePart.Hour, TimePart.Minute, TimePart.DayPeriod), ":", hourPadded = false),
            twelve.timeLayout,
        )
    }

    @Test
    fun namesAreTheLibrarysOwn() {
        assertEquals("September", english.monthName(Month.SEPTEMBER))
        assertEquals("Sep", english.monthName(Month.SEPTEMBER, NameWidth.Abbreviated))
        assertEquals("S", english.monthName(Month.SEPTEMBER, NameWidth.Narrow))
        assertEquals("Wed", english.weekdayName(DayOfWeek.WEDNESDAY, NameWidth.Abbreviated))
        assertEquals("am", english.dayPeriod(LocalTime(9, 0)))
        assertEquals("pm", english.dayPeriod(LocalTime(12, 0)))
    }

    @Test
    fun numbersAreGroupedAndRoundedHalfAwayFromZero() {
        assertEquals("0", english.integer(0))
        assertEquals("999", english.integer(999))
        assertEquals("1,000", english.integer(1000))
        assertEquals("-1,234,567", english.integer(-1234567))
        assertEquals("-9,223,372,036,854,775,808", english.integer(Long.MIN_VALUE))
        assertEquals("4.3", english.decimal(4.25, 1))
        assertEquals("-4.3", english.decimal(-4.25, 1))
        assertEquals("0.0", english.decimal(-0.01, 1))
        assertEquals("1,234.50", english.decimal(1234.5, 2))
        assertEquals("42%", english.percent(0.42))
        assertEquals("12.5%", english.percent(0.125, 1))
    }

    /**
     * Money, in the currency's own places. Reported from an app: *"Theme.formats
     * has no currency formatting, so I wrote a small money() helper by hand."*
     */
    @Test
    fun moneyInItsCurrencysPlaces() {
        assertEquals("$3.20", english.currency(3.2, "AUD"))
        assertEquals("$1,234.50", english.currency(1234.5, "aud"), "grouped, and the code read whatever its case")
        assertEquals("-$3.20", english.currency(-3.2, "AUD"), "the minus before the sign, as a reader writes it")
        assertEquals("$0.00", english.currency(-0.001, "AUD"), "nothing is not negative")
        assertEquals("EUR\u00A03.20", english.currency(3.2, "EUR"), "another currency is its code, as Australian English writes it")
        assertEquals("JPY\u00A0320", english.currency(320.0, "JPY"), "the yen has no places after the point")
        assertEquals("KWD\u00A01.250", english.currency(1.25, "KWD"), "and a Kuwaiti dinar three")
        assertEquals("XYZ\u00A03.20", english.currency(3.2, "XYZ"), "a code nothing knows is still written, not thrown over")
    }

    @Test
    fun aTypedNumberIsReadEitherWay() {
        assertEquals(4.5, english.parseDecimal("4.5"))
        assertEquals(1234.5, english.parseDecimal("1,234.5"))
        assertEquals(-3.0, english.parseDecimal("−3"))
        assertNull(english.parseDecimal("four"))
        assertNull(english.parseDecimal("1e5"))

        val german = PatternLocaleFormats(
            androidx.compose.ui.text.intl.Locale("de"),
            englishData(symbols = NumberSymbols(decimalSeparator = ",", groupingSeparator = ".")),
            DateTimeConventions(),
        )
        assertEquals(4.5, german.parseDecimal("4,5"))
        assertEquals(4.5, german.parseDecimal("4.5"), "a pasted Western number means what it says")
        assertEquals(1234.0, german.parseDecimal("1.234"))
        assertEquals(1234.5, german.parseDecimal("1.234,5"))
    }

    @Test
    fun otherDigitsAndGroupings() {
        val arabic = PatternLocaleFormats(
            androidx.compose.ui.text.intl.Locale("ar-EG"),
            englishData(symbols = NumberSymbols(zeroDigit = '٠', decimalSeparator = "٫", groupingSeparator = "٬")),
            DateTimeConventions(),
        )
        assertEquals("١٢٬٣٤٥", arabic.integer(12345))
        assertEquals(12345.0, arabic.parseDecimal("١٢٬٣٤٥"))
        assertEquals("٥ Jun ٢٠٢٦", arabic.date(june, DateStyle.Medium))

        val indian = PatternLocaleFormats(
            androidx.compose.ui.text.intl.Locale("en-IN"),
            englishData(symbols = NumberSymbols(secondaryGroupingSize = 2)),
            DateTimeConventions(),
        )
        assertEquals("1,23,45,678", indian.integer(12345678))

        val spanish = PatternLocaleFormats(
            androidx.compose.ui.text.intl.Locale("es"),
            englishData(symbols = NumberSymbols(groupingSeparator = ".", minimumGroupingDigits = 2)),
            DateTimeConventions(),
        )
        assertEquals("1000", spanish.integer(1000))
        assertEquals("10.000", spanish.integer(10000))
    }

    @Test
    fun listsRelativeTimeAndPlurals() {
        assertEquals("", english.list(emptyList()))
        assertEquals("Perth", english.list(listOf("Perth")))
        assertEquals("Perth and Fremantle", english.list(listOf("Perth", "Fremantle")))
        assertEquals("Perth, Fremantle and Joondalup", english.list(listOf("Perth", "Fremantle", "Joondalup")))
        assertEquals("a, b, c or d", english.list(listOf("a", "b", "c", "d"), ListStyle.Or))
        assertEquals("in 5 min", english.relativeTime(5, RelativeUnit.Minute))
        assertEquals("2 hr ago", english.relativeTime(-2, RelativeUnit.Hour))
        assertEquals("now", english.relativeNow())
        assertEquals(PluralCategory.One, english.pluralCategory(1))
        assertEquals(PluralCategory.Other, english.pluralCategory(0))
        assertEquals(Quantity(1234, "1,234", PluralCategory.Other), english.quantity(1234))
    }

    @Test
    fun americanEnglishSaysAmAndUsesTheSerialComma() {
        val american = PatternLocaleFormats(
            androidx.compose.ui.text.intl.Locale("en-US"),
            englishData(american = true),
            DateTimeConventions(hourCycle = HourCycle.H12, dateOrder = DateOrder.MonthDayYear),
        )
        assertEquals("2:05 PM", american.time(LocalTime(14, 5)))
        assertEquals("a, b, and c", american.list(listOf("a", "b", "c")))
    }

    @Test
    fun searchFoldsCaseAndAccents() {
        assertEquals("zurich", english.foldForSearch("Zürich"))
        assertEquals("strasse", english.foldForSearch("Straße"))
        assertEquals(english.foldForSearch("Café"), english.foldForSearch("Café"))
        val turkish = PatternLocaleFormats(androidx.compose.ui.text.intl.Locale("tr"), englishData(), DateTimeConventions())
        assertEquals(turkish.foldForSearch("istanbul"), turkish.foldForSearch("ISTANBUL"))
        assertEquals("İSTANBUL", turkish.uppercase("istanbul"))
    }
}
