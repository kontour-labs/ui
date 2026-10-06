package io.kontour.ui.locale

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * Everything about how this reader writes things that is not words: numbers,
 * dates, times, lists, plurals — and the few operations on text that depend on
 * which language it is in.
 *
 * ```kotlin
 * val formats = Theme.formats
 * Text(formats.date(departure, DateStyle.Medium))   // 5 Jun 2026, Jun 5, 2026, 2026年6月5日
 * Text(formats.integer(riders))                      // 12,345  12.345  ١٢٬٣٤٥
 * ```
 *
 * Read through `Theme.formats`, which `KontourTheme` builds for its `locale` —
 * the device's, followed live, unless an app sets its own. Every component in
 * the library that puts a number, a date or a list on screen goes through here,
 * so a German reader gets German dates and a reader in Cairo gets Arabic-Indic
 * digits without either being asked for.
 *
 * ### Changing one thing
 *
 * Three ways, cheapest first:
 *
 * - **A locale tag with extensions.** `Locale("en-AU-u-hc-h23")` is Australian
 *   English on a 24-hour clock; `-u-fw-mon` starts weeks on Monday;
 *   `-u-nu-latn` asks for Western digits in a locale that defaults to others.
 * - **[withConventions]**, for the three facts people change most — the hour
 *   cycle, the order of a date's parts and the first day of the week — without
 *   touching the rest: `Theme.formats.withConventions(conventions.copy(hourCycle = HourCycle.H23))`.
 * - **Delegation.** It is an interface, so an app that needs one member written
 *   its own way wraps the one it has and overrides that member.
 *
 * ### English is built in
 *
 * Every other language takes its names and patterns from the platform — ICU on
 * Android, Foundation on iOS, `Intl` in a browser, the JDK on a desktop — which
 * is CLDR's data in the release that platform shipped. English is the library's
 * own table, with the region's conventions (12- or 24-hour, day or month first,
 * where a week starts) still read from the platform. It is what every picture
 * in these docs was taken with, and it does not change between a phone that
 * shipped last year and one that shipped this year. [english] is that table on
 * its own, and what a test pins itself to.
 */
@Stable
interface LocaleFormats {

    /** The locale these formats are for. */
    val locale: Locale

    /** The hour cycle, the order of a date's parts and the first day of the week. */
    val conventions: DateTimeConventions

    /** The digits, separators and grouping [integer] and [decimal] write with. */
    val symbols: NumberSymbols

    // --- Numbers ---------------------------------------------------------

    /** A whole number, grouped: `12,345`, `12.345`, `12 345`, `1,23,456`, `١٢٬٣٤٥`. */
    fun integer(value: Long): String

    /** [integer] for an `Int`. */
    fun integer(value: Int): String = integer(value.toLong())

    /**
     * A number in this locale's digits and nothing else — no grouping: a year,
     * a day of the month, a code. `2026` stays `2026`, and becomes `٢٠٢٦`.
     */
    fun digits(value: Long): String

    /**
     * A number with exactly [fractionDigits] digits after the separator, rounded
     * half away from zero: `decimal(4.25, 1)` is `4.3`, `4,3` or `٤٫٣`.
     */
    fun decimal(value: Double, fractionDigits: Int = 1): String

    /** A share of a whole, written as a percentage: `0.42` is `42%`, `42 %` or `%42`. */
    fun percent(fraction: Double, fractionDigits: Int = 0): String

    /**
     * An amount of money in [currency], an ISO 4217 code, as this locale writes
     * it: `3.2` in `"EUR"` is `€3.20` to an American reader, `3,20 €` to a German
     * one, and `320` yen is `￥320` in Japan — the currency's own places after the
     * separator, the reader's digits and grouping, and a minus where the locale
     * puts one.
     *
     * ```kotlin
     * Text(Theme.formats.currency(fare.cents / 100.0, "AUD"))
     * ```
     *
     * **Keep money exact until it is shown.** Add it up in minor units as a
     * `Long`, or in a decimal type, and make a `Double` of it only here: a
     * `Double` cannot hold `0.10` exactly, and a total of them drifts. Written
     * once, rounded to the currency's places, it is right.
     *
     * A code the platform does not know is written as the code before the
     * number, `XYZ 3.20`, rather than thrown over. Only the code says which
     * currency: the locale does not, and a fare in euros is in euros wherever it
     * is read.
     */
    fun currency(amount: Double, currency: String): String = "$currency\u00A0" + decimal(amount, 2)

    /**
     * Reads a number a reader typed, in this locale's digits and separators or in
     * Western ones — a German reader typing `4,5` and one pasting `4.5` both mean
     * four and a half. `null` if it is not a number.
     */
    fun parseDecimal(text: String): Double?

    // --- Plurals ---------------------------------------------------------

    /** Which plural form [count] takes in this language. See [PluralCategory]. */
    fun pluralCategory(count: Long): PluralCategory

    /** [count], formatted and categorised, ready for a phrase in `Strings`. */
    fun quantity(count: Long): Quantity = Quantity(count, integer(count), pluralCategory(count))

    /** [quantity] for an `Int`. */
    fun quantity(count: Int): Quantity = quantity(count.toLong())

    // --- Calendar words --------------------------------------------------

    /**
     * A month's name. [standalone] is the form a month takes on its own, as a
     * heading; `false` the form it takes inside a date. Most languages do not
     * distinguish; Russian, Polish, Czech and Catalan do.
     */
    fun monthName(month: Month, width: NameWidth = NameWidth.Wide, standalone: Boolean = true): String

    /** A day's name, on its own ([standalone]) or inside a date. */
    fun weekdayName(day: DayOfWeek, width: NameWidth = NameWidth.Wide, standalone: Boolean = true): String

    /** Morning or afternoon, in this language's words: `am`, `AM`, `午前`, `ص`. */
    fun dayPeriod(time: LocalTime): String

    // --- Dates and times -------------------------------------------------

    /** A date, in one of the four lengths every locale has a pattern for. */
    fun date(date: LocalDate, style: DateStyle = DateStyle.Medium): String

    /** A month with its year, as a calendar heads its page: `June 2026`, `2026年6月`. */
    fun monthAndYear(date: LocalDate): String

    /** A time of day, on this reader's clock: `14:05`, `2:05 pm`, `午後2:05`. */
    fun time(time: LocalTime): String

    /** A date and a time together. */
    fun dateTime(date: LocalDate, time: LocalTime): String

    /**
     * How a time is laid out: which parts, in which order, with what between
     * them. A time picker builds its fields from this rather than assuming
     * hour, colon, minute, then AM or PM.
     */
    val timeLayout: TimeLayout

    // --- Relative time ---------------------------------------------------

    /**
     * An amount of time from now: negative [value] is past. `in 5 min`,
     * `5 min ago`, `vor 5 Min.`, `5分後`.
     */
    fun relativeTime(value: Long, unit: RelativeUnit): String

    /** The word for a moment ago or a moment from now: `now`, `jetzt`, `今`. */
    fun relativeNow(): String

    // --- Lists -----------------------------------------------------------

    /** Items joined the way this language joins them: `a, b and c`, `a, b und c`, `a、b、c`. */
    fun list(items: List<String>, style: ListStyle = ListStyle.And): String

    // --- Text ------------------------------------------------------------

    /**
     * [text] folded for matching a search against: case removed as this language
     * removes it, and accents stripped — so `zurich` finds `Zürich` and `ISTANBUL`
     * finds `istanbul`, in Turkish too.
     */
    fun foldForSearch(text: String): String

    /**
     * The first character a reader would count in [text] — one grapheme, which may
     * be several code points: an accented letter, a flag, a family emoji, a
     * Hindi syllable. Empty for empty text.
     */
    fun firstGrapheme(text: String): String

    /** [text] in capitals, the way this language capitalises: `i` is `İ` in Turkish. */
    fun uppercase(text: String): String

    /** [text] in lower case, the way this language does it. */
    fun lowercase(text: String): String

    /** These formats with [conventions] in place of their own. */
    fun withConventions(conventions: DateTimeConventions): LocaleFormats

    companion object {
        /**
         * The library's built-in English, independent of any platform.
         *
         * What every test and picture in the library is taken with, so a golden
         * recorded on one machine matches one taken on another. [conventions]
         * defaults to Australian — 24-hour, day first, weeks from Monday — which
         * is where the pictures were first taken.
         */
        fun english(conventions: DateTimeConventions = DateTimeConventions()): LocaleFormats =
            PatternLocaleFormats(Locale("en-AU"), englishData(), conventions)

        /** The platform's formats for [locale]. What `KontourTheme` uses when given no formats. */
        fun of(locale: Locale): LocaleFormats = localeFormatsFor(locale)
    }
}

/** How much of a name to write: `Wednesday`, `Wed`, `W`. */
enum class NameWidth { Wide, Abbreviated, Narrow }

/**
 * The four lengths a date comes in: `5/6/26`, `5 Jun 2026`, `5 June 2026`,
 * `Friday, 5 June 2026`. What each looks like is the locale's.
 */
enum class DateStyle { Short, Medium, Long, Full }

/** `a, b and c`, `a, b or c`, and `a, b, c` for units — `3 ft, 7 in`. */
enum class ListStyle { And, Or, Unit }

/** The unit [LocaleFormats.relativeTime] counts in. */
enum class RelativeUnit { Second, Minute, Hour, Day, Week, Month, Year }

/**
 * Which numbers a clock shows.
 *
 * [H12] is 1 to 12 with a day period — `12:05 pm` — as English writes it. [H11] is
 * 0 to 11, as Japanese writes it: `午後0:05`. [H23] is the 24-hour clock from 0,
 * which most of the world uses; [H24] runs 1 to 24 and is rare.
 */
enum class HourCycle {
    H11, H12, H23, H24;

    /** Whether the clock needs a morning or afternoon beside it. */
    val usesDayPeriod: Boolean get() = this == H11 || this == H12
}

/** The order a date's day, month and year are written in. */
enum class DateOrder { DayMonthYear, MonthDayYear, YearMonthDay }

/**
 * The three conventions people most often change about dates and times.
 *
 * Read from the locale, and changeable on their own with
 * [LocaleFormats.withConventions] — a phone set to English (Australia) with a
 * 24-hour clock is the commonest reason. [dateOrder] orders the fields a date is
 * *picked* in — a date picker's wheels — and, for the built-in English, how a date
 * is written; other languages write dates in their own patterns, which already
 * encode it.
 */
@Immutable
data class DateTimeConventions(
    val hourCycle: HourCycle = HourCycle.H23,
    val dateOrder: DateOrder = DateOrder.DayMonthYear,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {
    /** Whether the clock runs to 24 rather than twice to 12. */
    val is24Hour: Boolean get() = !hourCycle.usesDayPeriod

    /** The days of the week in display order, starting at [firstDayOfWeek]. */
    fun weekdays(): List<DayOfWeek> {
        val all = DayOfWeek.entries
        val start = all.indexOf(firstDayOfWeek)
        return List(7) { all[(start + it) % 7] }
    }

    /** How many columns from [firstDayOfWeek] this day sits at. */
    fun columnOf(day: DayOfWeek): Int {
        val all = DayOfWeek.entries
        return (all.indexOf(day) - all.indexOf(firstDayOfWeek) + 7) % 7
    }
}

/**
 * The characters numbers are written with.
 *
 * @property zeroDigit The locale's zero; the other nine follow it in Unicode, so
 *   `'٠'` gives Arabic-Indic digits and `'०'` Devanagari.
 * @property groupingSize Digits in the group next to the separator, and
 *   [secondaryGroupingSize] in every group after it — 3 and 2 in India, so
 *   `1,23,45,678`. 0 writes no separators at all.
 * @property minimumGroupingDigits How many digits must come before the first
 *   separator for there to be one: 1 in most languages, 2 in Spanish and
 *   Polish, which write `1000` but `10.000` and `10 000`.
 */
@Immutable
data class NumberSymbols(
    val zeroDigit: Char = '0',
    val decimalSeparator: String = ".",
    val groupingSeparator: String = ",",
    val minusSign: String = "-",
    val percentSign: String = "%",
    val groupingSize: Int = 3,
    val secondaryGroupingSize: Int = 3,
    val minimumGroupingDigits: Int = 1,
)

/** One of the parts a time is written in. */
enum class TimePart { Hour, Minute, DayPeriod }

/**
 * How [LocaleFormats.time] lays a time out, for a component that builds one from
 * fields.
 *
 * @property parts The parts, in reading order: `[Hour, Minute, DayPeriod]` in
 *   English, `[DayPeriod, Hour, Minute]` in Korean and Japanese.
 * @property separator What goes between hour and minute: `:` almost everywhere,
 *   `.` in Finnish and Danish.
 * @property hourPadded Whether the hour is two digits: `09:05` or `9:05`.
 */
@Immutable
data class TimeLayout(
    val parts: List<TimePart>,
    val separator: String,
    val hourPadded: Boolean,
)
