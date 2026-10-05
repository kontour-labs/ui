package io.kontour.ui.locale

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.text.toUpperCase
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * [LocaleFormats] written from a [LocaleData]: one engine, every platform.
 *
 * A platform's job is to fill in the data — names, patterns, symbols — and this
 * does the writing: an LDML pattern walked field by field, digits mapped to the
 * locale's, groups placed by the locale's sizes. Doing the writing once, here,
 * is what lets [withConventions] change the hour cycle of a locale whose own
 * formatter would not, and what makes the same date come out the same way from
 * a phone and a browser with the same CLDR.
 */
@Immutable
internal class PatternLocaleFormats(
    override val locale: Locale,
    private val data: LocaleData,
    override val conventions: DateTimeConventions,
) : LocaleFormats {

    override val symbols: NumberSymbols get() = data.symbols

    private val timePattern: String = timePatternFor(conventions.hourCycle)

    override val timeLayout: TimeLayout = layoutOf(timePattern)

    // --- Numbers ---------------------------------------------------------

    override fun integer(value: Long): String {
        val size = if (value == Long.MIN_VALUE) "9223372036854775808" else abs(value).toString()
        val sign = if (value < 0) symbols.minusSign else ""
        return sign + localDigits(grouped(size))
    }

    override fun digits(value: Long): String {
        val size = if (value == Long.MIN_VALUE) "9223372036854775808" else abs(value).toString()
        return (if (value < 0) symbols.minusSign else "") + localDigits(size)
    }

    override fun decimal(value: Double, fractionDigits: Int): String {
        val digits = fractionDigits.coerceIn(0, 15)
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return (if (value < 0) symbols.minusSign else "") + "∞"
        val scale = 10.0.pow(digits)
        // Half away from zero, which is what a reader expects of 4.25 to one
        // place; `kotlin.math.round` rounds half to even.
        val scaled = floor(abs(value) * scale + 0.5)
        if (scaled >= Long.MAX_VALUE.toDouble()) return (if (value < 0) symbols.minusSign else "") + abs(value).toString()
        val whole = scaled.toLong()
        val text = whole.toString().padStart(digits + 1, '0')
        val integerPart = text.dropLast(digits)
        val fractionPart = text.takeLast(digits)
        val sign = if (value < 0 && whole != 0L) symbols.minusSign else ""
        val body = if (digits == 0) grouped(integerPart) else grouped(integerPart) + symbols.decimalSeparator + fractionPart
        return sign + localDigits(body)
    }

    override fun percent(fraction: Double, fractionDigits: Int): String {
        val number = decimal(abs(fraction) * 100.0, fractionDigits)
        val negative = fraction < 0 && number.any { it != symbols.zeroDigit && it.isLocalDigit() }
        return (if (negative) symbols.minusSign else "") + data.percentPrefix + number + data.percentSuffix
    }

    override fun currency(amount: Double, currency: String): String {
        val format = currencies[isoCurrencyCode(currency)]
        val number = decimal(abs(amount), format.fractionDigits)
        val negative = amount < 0 && number.any { it != symbols.zeroDigit && it.isLocalDigit() }
        return (if (negative) symbols.minusSign else "") + format.prefix + number + format.suffix
    }

    /**
     * Each currency's format, read once: the platform's reading of one is a
     * formatter built and taken apart, which is not something to do per row of a
     * list of fares.
     */
    private val currencies = CurrencyFormats(data.currency)

    override fun parseDecimal(text: String): Double? = parseNumber(text, symbols)

    // --- Plurals ---------------------------------------------------------

    override fun pluralCategory(count: Long): PluralCategory = PluralRules.categoryOf(data.language, count)

    // --- Calendar words --------------------------------------------------

    override fun monthName(month: Month, width: NameWidth, standalone: Boolean): String =
        data.months.name(month.ordinal, width, standalone)

    override fun weekdayName(day: DayOfWeek, width: NameWidth, standalone: Boolean): String =
        data.weekdays.name(day.ordinal, width, standalone)

    override fun dayPeriod(time: LocalTime): String = if (time.hour < 12) data.am else data.pm

    // --- Dates and times -------------------------------------------------

    override fun date(date: LocalDate, style: DateStyle): String =
        write(data.datePatternsFor(conventions.dateOrder)[style.ordinal], date = date, time = null)

    override fun monthAndYear(date: LocalDate): String = write(data.monthYearPattern, date = date, time = null)

    override fun time(time: LocalTime): String = write(timePattern, date = null, time = time)

    override fun dateTime(date: LocalDate, time: LocalTime): String =
        data.dateTimePattern.fill(time(time), date(date, DateStyle.Medium))

    // --- Relative time ---------------------------------------------------

    override fun relativeTime(value: Long, unit: RelativeUnit): String =
        data.relative.format(value, integer(if (value == Long.MIN_VALUE) Long.MAX_VALUE else abs(value)), unit)

    override fun relativeNow(): String = data.relative.now

    // --- Lists and text --------------------------------------------------

    override fun list(items: List<String>, style: ListStyle): String =
        (data.lists[style] ?: ListPattern.Commas).join(items)

    override fun foldForSearch(text: String): String = foldText(text, this)

    override fun firstGrapheme(text: String): String = Graphemes.first(text)

    override fun uppercase(text: String): String = text.toUpperCase(locale)

    override fun lowercase(text: String): String = text.toLowerCase(locale)

    override fun withConventions(conventions: DateTimeConventions): LocaleFormats =
        if (conventions == this.conventions) this else PatternLocaleFormats(locale, data, conventions)

    override fun equals(other: Any?): Boolean =
        other is PatternLocaleFormats && other.locale == locale && other.conventions == conventions && other.data === data

    override fun hashCode(): Int = (locale.hashCode() * 31 + conventions.hashCode()) * 31 + data.hashCode()

    override fun toString(): String = "LocaleFormats(${locale.toLanguageTag()}, $conventions)"

    // --- The engine ------------------------------------------------------

    private fun Char.isLocalDigit(): Boolean = this - symbols.zeroDigit in 0..9

    /** ASCII digits into this locale's, leaving everything else. */
    private fun localDigits(text: String): String {
        val zero = symbols.zeroDigit
        if (zero == '0') return text
        return buildString(text.length) {
            for (char in text) append(if (char in '0'..'9') zero + (char - '0') else char)
        }
    }

    /** [digits], a run of ASCII digits, with this locale's separators between its groups. */
    private fun grouped(digits: String): String {
        val primary = symbols.groupingSize
        if (primary <= 0 || digits.length < primary + symbols.minimumGroupingDigits) return digits
        val secondary = symbols.secondaryGroupingSize.takeIf { it > 0 } ?: primary
        val groups = ArrayDeque<String>()
        var end = digits.length
        groups.addFirst(digits.substring(end - primary, end))
        end -= primary
        while (end > 0) {
            val start = maxOf(0, end - secondary)
            groups.addFirst(digits.substring(start, end))
            end = start
        }
        return groups.joinToString(symbols.groupingSeparator)
    }

    private fun timePatternFor(cycle: HourCycle): String {
        val base = if (cycle.usesDayPeriod) data.twelveHourPattern else data.twentyFourHourPattern
        val letter = when (cycle) {
            HourCycle.H11 -> 'K'
            HourCycle.H12 -> 'h'
            HourCycle.H23 -> 'H'
            HourCycle.H24 -> 'k'
        }
        return mapFields(base) { field, count -> if (field in HourFields) letter.toString().repeat(count) else null }
    }

    /** Writes an LDML pattern for [date] and [time], either of which may be absent. */
    private fun write(pattern: String, date: LocalDate?, time: LocalTime?): String = buildString {
        forEachField(pattern, literal = { append(it) }) { field, count ->
            append(fieldText(field, count, date, time))
        }
    }.let(::tidy)

    private fun fieldText(field: Char, count: Int, date: LocalDate?, time: LocalTime?): String {
        fun number(value: Int, width: Int = count) = localDigits(value.toString().padStart(width, '0'))
        return when (field) {
            'y', 'u' -> {
                val year = date?.year ?: return ""
                if (count == 2) number(((year % 100) + 100) % 100, 2) else number(year, count)
            }
            'M', 'L' -> {
                val month = date?.month ?: return ""
                val standalone = field == 'L'
                when (count) {
                    1, 2 -> number(month.ordinal + 1)
                    3 -> monthName(month, NameWidth.Abbreviated, standalone)
                    4 -> monthName(month, NameWidth.Wide, standalone)
                    else -> monthName(month, NameWidth.Narrow, standalone)
                }
            }
            'd' -> date?.let { number(it.day) } ?: ""
            'E', 'c', 'e' -> {
                val day = date?.dayOfWeek ?: return ""
                val standalone = field == 'c'
                when {
                    count == 4 -> weekdayName(day, NameWidth.Wide, standalone)
                    count >= 5 -> weekdayName(day, NameWidth.Narrow, standalone)
                    else -> weekdayName(day, NameWidth.Abbreviated, standalone)
                }
            }
            'a', 'b', 'B' -> time?.let { dayPeriod(it) } ?: ""
            'h' -> time?.let { number((it.hour % 12).let { h -> if (h == 0) 12 else h }) } ?: ""
            'K' -> time?.let { number(it.hour % 12) } ?: ""
            'H' -> time?.let { number(it.hour) } ?: ""
            'k' -> time?.let { number(if (it.hour == 0) 24 else it.hour) } ?: ""
            'm' -> time?.let { number(it.minute) } ?: ""
            's' -> time?.let { number(it.second) } ?: ""
            // Eras, time zones and the rest: nothing this library writes has
            // them, and a Gregorian era beside a Gregorian year says nothing.
            else -> ""
        }
    }

    private fun layoutOf(pattern: String): TimeLayout {
        val parts = mutableListOf<TimePart>()
        var separator = ":"
        var hourPadded = true
        var betweenHourAndMinute: StringBuilder? = null
        forEachField(
            pattern,
            literal = { betweenHourAndMinute?.append(it) },
        ) { field, count ->
            when (field) {
                in HourFields -> {
                    parts += TimePart.Hour
                    hourPadded = count >= 2
                    betweenHourAndMinute = StringBuilder()
                }
                'm' -> {
                    parts += TimePart.Minute
                    betweenHourAndMinute?.let { separator = it.toString().ifEmpty { ":" } }
                    betweenHourAndMinute = null
                }
                'a', 'b', 'B' -> {
                    parts += TimePart.DayPeriod
                    betweenHourAndMinute = null
                }
                else -> betweenHourAndMinute = null
            }
        }
        return TimeLayout(parts = parts.distinct(), separator = separator, hourPadded = hourPadded)
    }

    private companion object {
        val HourFields = setOf('h', 'H', 'K', 'k')

        /**
         * Spaces a dropped field left behind, and ones at the ends. An era the
         * engine does not write should not leave `d MMMM  y`.
         */
        fun tidy(text: String): String {
            if ("  " !in text && text.firstOrNull()?.isWhitespace() != true && text.lastOrNull()?.isWhitespace() != true) return text
            return text.replace(Regex("[   ]{2,}"), " ").trim()
        }
    }
}

/**
 * Walks an LDML pattern: runs of one letter are fields, text in single quotes
 * and anything that is not a letter is literal, and `''` is a quote.
 */
internal inline fun forEachField(pattern: String, literal: (String) -> Unit, field: (Char, Int) -> Unit) {
    var index = 0
    while (index < pattern.length) {
        val char = pattern[index]
        when {
            char == '\'' -> {
                if (index + 1 < pattern.length && pattern[index + 1] == '\'') {
                    literal("'")
                    index += 2
                    continue
                }
                val close = pattern.indexOf('\'', index + 1).let { if (it < 0) pattern.length else it }
                // A quoted run may itself contain `''`, which is a quote.
                var text = pattern.substring(index + 1, close)
                var end = close
                while (end + 1 < pattern.length && pattern[end + 1] == '\'') {
                    val next = pattern.indexOf('\'', end + 2).let { if (it < 0) pattern.length else it }
                    text += "'" + pattern.substring(end + 2, next)
                    end = next
                }
                literal(text)
                index = end + 1
            }
            char in 'a'..'z' || char in 'A'..'Z' -> {
                var end = index
                while (end < pattern.length && pattern[end] == char) end++
                field(char, end - index)
                index = end
            }
            else -> {
                literal(char.toString())
                index++
            }
        }
    }
}

/** A pattern with some fields rewritten: [transform] returns the new field, or `null` to keep it. */
internal fun mapFields(pattern: String, transform: (Char, Int) -> String?): String = buildString {
    forEachField(
        pattern,
        literal = { text ->
            if (text.any { it in 'a'..'z' || it in 'A'..'Z' || it == '\'' }) {
                append('\'').append(text.replace("'", "''")).append('\'')
            } else {
                append(text)
            }
        },
    ) { field, count -> append(transform(field, count) ?: field.toString().repeat(count)) }
}

/**
 * Reads a number a reader typed, in this locale's digits and separators or in
 * Western ones.
 *
 * The locale's decimal separator, if the text has one, is the decimal point
 * and its grouping separators are dropped. Without one, a grouping separator
 * counts as grouping only where it is followed by whole groups — `1.234` is a
 * thousand and more in German, but `4.5` is four and a half, because nobody
 * groups a single digit. Anything else is read as Western: `.` a point, `,` a
 * group.
 */
internal fun parseNumber(text: String, symbols: NumberSymbols): Double? {
    val zero = symbols.zeroDigit
    var normal = buildString {
        for (char in text.trim()) {
            when {
                char - zero in 0..9 -> append('0' + (char - zero))
                char - '٠' in 0..9 -> append('0' + (char - '٠'))
                char - '۰' in 0..9 -> append('0' + (char - '۰'))
                char == '−' || char == '‒' || char == '–' -> append('-')
                char == '‎' || char == '‏' || char == '؜' -> Unit
                else -> append(char)
            }
        }
    }
    if (symbols.minusSign.isNotEmpty() && symbols.minusSign != "-") normal = normal.replace(symbols.minusSign, "-")
    val point = symbols.decimalSeparator
    val group = symbols.groupingSeparator
    val spaces = setOf(' ', ' ', ' ', ' ')
    fun withoutGroups(value: String, separator: String): String =
        if (separator.isNotEmpty() && separator.all { it in spaces }) value.filterNot { it in spaces } else value.replace(separator, "")
    fun groupsValid(value: String, separator: String): Boolean {
        if (separator.isEmpty() || separator !in value) return false
        val integer = value.removePrefix("-")
        val pieces = integer.split(separator)
        return pieces.first().isNotEmpty() && pieces.drop(1).all { it.length == 3 || it.length == symbols.secondaryGroupingSize }
    }
    val candidate = when {
        point.isNotEmpty() && point != "." && point in normal -> {
            val at = normal.lastIndexOf(point)
            withoutGroups(normal.substring(0, at), group) + "." + normal.substring(at + point.length)
        }
        point == "." && '.' in normal -> withoutGroups(normal, group)
        groupsValid(normal, group) -> withoutGroups(normal, group)
        // Western: a comma groups when it is followed by whole groups.
        groupsValid(normal, ",") && '.' !in normal.substringBefore(',') -> normal.replace(",", "")
        else -> normal.replace(',', '.').takeIf { it.count { c -> c == '.' } <= 1 } ?: return null
    }
    if (!Regex("""-?(\d+\.?\d*|\.\d+)""").matches(candidate)) return null
    return candidate.toDoubleOrNull()
}

/** [read]'s answer for each code, kept, and [fallbackCurrency] where it has none. */
private class CurrencyFormats(private val read: (String) -> CurrencyFormat?) {
    @kotlin.concurrent.Volatile
    private var known: Map<String, CurrencyFormat> = emptyMap()

    operator fun get(code: String): CurrencyFormat {
        known[code]?.let { return it }
        val made = runCatching { read(code) }.getOrNull() ?: fallbackCurrency(code)
        known = known + (code to made)
        return made
    }
}
