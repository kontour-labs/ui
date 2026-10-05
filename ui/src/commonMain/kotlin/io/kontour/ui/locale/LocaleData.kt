package io.kontour.ui.locale

/**
 * Everything [PatternLocaleFormats] needs to know about one language, as data.
 *
 * Each platform fills one in from its own CLDR — names, patterns, symbols — and
 * one engine writes with it everywhere, so a date is laid out by the same code
 * on a phone and in a browser, and the only difference between them is the
 * release of CLDR the data came from. The built-in English is one of these too
 * ([englishData]).
 *
 * Patterns are LDML's (UTS #35 part 4): `d MMM y`, `EEEE, d MMMM y`, `h:mm a`,
 * with literal text in single quotes.
 */
internal class LocaleData(
    /** The language whose plural rules apply: `en`, `pl`, `ar`. */
    val language: String,
    val symbols: NumberSymbols,
    /** What goes before and after a percentage's number: `""` and `"%"`, `""` and `" %"`, `"%"` and `""`. */
    val percentPrefix: String,
    val percentSuffix: String,
    val months: CalendarNames,
    /** Monday first, as `DayOfWeek.entries` is. */
    val weekdays: CalendarNames,
    val am: String,
    val pm: String,
    /** A pattern for each [DateStyle], by ordinal, in the locale's own order. */
    val datePatterns: List<String>,
    /**
     * Patterns for every [DateOrder], for a table that can write a date in any
     * of them — the built-in English. A platform's patterns come in the order its
     * locale writes, and a date order asked for through the conventions changes
     * how dates are picked but not how that locale writes them.
     */
    val datePatternsByOrder: Map<DateOrder, List<String>>? = null,
    /** A month with its year: `MMMM y`, `y年M月`. */
    val monthYearPattern: String,
    /** The locale's time on a twelve-hour clock: `h:mm a`, `a h:mm`, `aK:mm`. */
    val twelveHourPattern: String,
    /** The locale's time on a twenty-four-hour clock: `HH:mm`, `H.mm`. */
    val twentyFourHourPattern: String,
    /** How a date and a time are joined: `{1}, {0}` — `{0}` the time, `{1}` the date. */
    val dateTimePattern: String,
    val lists: Map<ListStyle, ListPattern>,
    val relative: RelativeWords,
    /**
     * How an amount of a currency is written, by ISO 4217 code, or `null` for a
     * code this has nothing on — which is then written as the code before the
     * number. The platform's reading of the locale, or the built-in English's
     * table.
     */
    val currency: (code: String) -> CurrencyFormat? = { null },
) {
    init {
        require(datePatterns.size == DateStyle.entries.size) { "a pattern for each DateStyle" }
    }

    /** The patterns a date is written in under [order]. */
    fun datePatternsFor(order: DateOrder): List<String> = datePatternsByOrder?.get(order) ?: datePatterns

    /** This data with other digits and separators. */
    fun copyWith(symbols: NumberSymbols): LocaleData = LocaleData(
        language = language,
        symbols = symbols,
        percentPrefix = percentPrefix,
        percentSuffix = percentSuffix,
        months = months,
        weekdays = weekdays,
        am = am,
        pm = pm,
        datePatterns = datePatterns,
        datePatternsByOrder = datePatternsByOrder,
        monthYearPattern = monthYearPattern,
        twelveHourPattern = twelveHourPattern,
        twentyFourHourPattern = twentyFourHourPattern,
        dateTimePattern = dateTimePattern,
        lists = lists,
        relative = relative,
        currency = currency,
    )

    /** This data with currencies read by [read] instead. */
    fun withCurrency(read: (code: String) -> CurrencyFormat?): LocaleData = LocaleData(
        language = language,
        symbols = symbols,
        percentPrefix = percentPrefix,
        percentSuffix = percentSuffix,
        months = months,
        weekdays = weekdays,
        am = am,
        pm = pm,
        datePatterns = datePatterns,
        datePatternsByOrder = datePatternsByOrder,
        monthYearPattern = monthYearPattern,
        twelveHourPattern = twelveHourPattern,
        twentyFourHourPattern = twentyFourHourPattern,
        dateTimePattern = dateTimePattern,
        lists = lists,
        relative = relative,
        currency = read,
    )
}

/**
 * How a locale writes an amount of one currency: what goes before the number
 * and after it — `$` and nothing, nothing and ` €`, `CHF ` and nothing — and how
 * many digits follow the separator.
 */
internal class CurrencyFormat(val prefix: String, val suffix: String, val fractionDigits: Int)

/**
 * A currency no reading of the locale knows: its code before the number, held
 * to it by a no-break space, at the places ISO 4217 gives it.
 */
internal fun fallbackCurrency(code: String): CurrencyFormat = CurrencyFormat("$code\u00A0", "", isoFractionDigits(code))

/**
 * The places after the separator ISO 4217 gives [code]: none for the yen and
 * the won, three for the dinars of the Gulf, two for nearly everything else.
 */
internal fun isoFractionDigits(code: String): Int = when (code) {
    in NoMinorUnits -> 0
    in ThreeMinorUnits -> 3
    else -> 2
}

private val NoMinorUnits = setOf(
    "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "UYI", "VND", "VUV", "XAF", "XOF", "XPF",
)

private val ThreeMinorUnits = setOf("BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND")

/**
 * Month or weekday names in each width, in the form they take inside a date and
 * the form they take on their own.
 *
 * Most languages write the two forms the same, and the standalone ones default
 * to the in-date ones. Russian is the reason they are separate: `июнь` heading a
 * calendar, `5 июня` inside a date.
 */
internal class CalendarNames(
    val wide: List<String>,
    val abbreviated: List<String>,
    val narrow: List<String>,
    val wideStandalone: List<String> = wide,
    val abbreviatedStandalone: List<String> = abbreviated,
    val narrowStandalone: List<String> = narrow,
) {
    fun name(index: Int, width: NameWidth, standalone: Boolean): String = when (width) {
        NameWidth.Wide -> if (standalone) wideStandalone else wide
        NameWidth.Abbreviated -> if (standalone) abbreviatedStandalone else abbreviated
        NameWidth.Narrow -> if (standalone) narrowStandalone else narrow
    }[index]
}

/**
 * CLDR's four list patterns: two items, and the start, middle and end of three
 * or more. `{0}` and `{1}` are the two sides.
 */
internal class ListPattern(val two: String, val start: String, val middle: String, val end: String) {
    fun join(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> two.fill(items[0], items[1])
        else -> {
            // Built from the end, as UTS #35 describes: the last two with `end`,
            // then each earlier one with `middle`, and the first with `start`.
            var text = end.fill(items[items.size - 2], items.last())
            for (index in items.size - 3 downTo 1) text = middle.fill(items[index], text)
            start.fill(items[0], text)
        }
    }

    companion object {
        /** Items with commas and no conjunction: what a list falls back to when a platform cannot say. */
        val Commas = ListPattern("{0}, {1}", "{0}, {1}", "{0}, {1}", "{0}, {1}")
    }
}

/** Fills `{0}` and `{1}` in one pass, so an item containing `{1}` is left alone. */
internal fun String.fill(first: String, second: String): String = buildString {
    var index = 0
    val pattern = this@fill
    while (index < pattern.length) {
        if (pattern[index] == '{' && index + 2 < pattern.length && pattern[index + 2] == '}') {
            when (pattern[index + 1]) {
                '0' -> { append(first); index += 3; continue }
                '1' -> { append(second); index += 3; continue }
            }
        }
        append(pattern[index])
        index++
    }
}

/** A language's words for an amount of time from now. */
internal interface RelativeWords {
    /**
     * [value] is negative for the past; [formatted] is its size, already
     * written in the reader's digits.
     */
    fun format(value: Long, formatted: String, unit: RelativeUnit): String

    /** A moment ago, or a moment from now. */
    val now: String
}
