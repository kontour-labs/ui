package io.kontour.ui.locale

/**
 * The library's own English: every word and pattern it writes in English, with
 * no platform involved.
 *
 * What [LocaleFormats.english] is, and what every English locale gets, so the
 * pictures in the docs and the goldens in the tests do not move when a phone's
 * CLDR does. The region still decides what regions decide — [DateTimeConventions]
 * picks the clock and the order of a date, and [american] the few words that
 * differ across the Atlantic — and the platform still supplies the region's
 * digits and separators, which [symbols] carries in.
 *
 * Abbreviations are the first three letters, `Sep` included; CLDR's Australian
 * `Sept` is one of the things this table exists to hold still.
 */
internal fun englishData(
    american: Boolean = false,
    symbols: NumberSymbols = NumberSymbols(),
): LocaleData = if (american) AmericanEnglish.withSymbols(symbols) else BritishEnglish.withSymbols(symbols)

/** Whether [region] writes English the American way: `AM`, and the serial comma. */
internal fun writesAmericanEnglish(region: String): Boolean = region in AmericanRegions

private val AmericanRegions = setOf("US", "CA", "PH", "PR", "AS", "GU", "MP", "UM", "VI")

private val Months = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val Weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private val EnglishMonths = CalendarNames(
    wide = Months,
    abbreviated = Months.map { it.take(3) },
    narrow = Months.map { it.take(1) },
)

private val EnglishWeekdays = CalendarNames(
    wide = Weekdays,
    abbreviated = Weekdays.map { it.take(3) },
    narrow = Weekdays.map { it.take(1) },
)

/** Patterns for each date order, by [DateStyle] ordinal. */
private val EnglishDatePatterns = mapOf(
    DateOrder.DayMonthYear to listOf("d/M/yy", "d MMM y", "d MMMM y", "EEEE, d MMMM y"),
    DateOrder.MonthDayYear to listOf("M/d/yy", "MMM d, y", "MMMM d, y", "EEEE, MMMM d, y"),
    DateOrder.YearMonthDay to listOf("y-MM-dd", "y MMM d", "y MMMM d", "EEEE, y MMMM d"),
)

/**
 * `now`, `in 5 min`, `5 min ago` — a departure board's English, short enough to
 * sit beside a stop's name. Also what a platform with no relative-time
 * formatter of its own falls back to.
 */
internal val englishRelativeWords: RelativeWords get() = EnglishRelative

private object EnglishRelative : RelativeWords {
    override fun format(value: Long, formatted: String, unit: RelativeUnit): String {
        val word = when (unit) {
            RelativeUnit.Second -> "sec"
            RelativeUnit.Minute -> "min"
            RelativeUnit.Hour -> "hr"
            RelativeUnit.Day -> "d"
            RelativeUnit.Week -> "wk"
            RelativeUnit.Month -> "mo"
            RelativeUnit.Year -> "yr"
        }
        return if (value < 0) "$formatted $word ago" else "in $formatted $word"
    }

    override val now: String = "now"
}

private fun englishLists(serialComma: Boolean) = mapOf(
    ListStyle.And to ListPattern("{0} and {1}", "{0}, {1}", "{0}, {1}", if (serialComma) "{0}, and {1}" else "{0} and {1}"),
    ListStyle.Or to ListPattern("{0} or {1}", "{0}, {1}", "{0}, {1}", if (serialComma) "{0}, or {1}" else "{0} or {1}"),
    ListStyle.Unit to ListPattern.Commas,
)

private fun english(american: Boolean) = LocaleData(
    language = "en",
    symbols = NumberSymbols(),
    percentPrefix = "",
    percentSuffix = "%",
    months = EnglishMonths,
    weekdays = EnglishWeekdays,
    am = if (american) "AM" else "am",
    pm = if (american) "PM" else "pm",
    datePatterns = EnglishDatePatterns.getValue(if (american) DateOrder.MonthDayYear else DateOrder.DayMonthYear),
    datePatternsByOrder = EnglishDatePatterns,
    monthYearPattern = "MMMM y",
    twelveHourPattern = "h:mm a",
    twentyFourHourPattern = "HH:mm",
    dateTimePattern = "{1}, {0}",
    lists = englishLists(serialComma = american),
    relative = EnglishRelative,
    currency = englishCurrency(american),
)

/**
 * The built-in English's money: the dollar of where it is written as `$` — the
 * American one, or the Australian one the library's pictures were taken in —
 * and every other currency as its code, `EUR 3.20`, as Australian English
 * writes them. A platform's English replaces this with its own reading.
 */
private fun englishCurrency(american: Boolean): (String) -> CurrencyFormat? {
    val home = if (american) "USD" else "AUD"
    return { code -> if (code == home) CurrencyFormat("$", "", isoFractionDigits(code)) else null }
}

private val BritishEnglish = english(american = false)
private val AmericanEnglish = english(american = true)

/**
 * The same English with a region's digits and separators — one instance per
 * distinct set, so formats built for the same region compare equal.
 */
private fun LocaleData.withSymbols(symbols: NumberSymbols): LocaleData =
    if (symbols == this.symbols) this else EnglishBySymbols.getOrPut(this, symbols) { copyWith(symbols) }

private object EnglishBySymbols {
    @kotlin.concurrent.Volatile
    private var cache: Map<Pair<LocaleData, NumberSymbols>, LocaleData> = emptyMap()

    fun getOrPut(base: LocaleData, symbols: NumberSymbols, make: () -> LocaleData): LocaleData {
        val key = base to symbols
        cache[key]?.let { return it }
        val made = make()
        cache = cache + (key to made)
        return made
    }
}
