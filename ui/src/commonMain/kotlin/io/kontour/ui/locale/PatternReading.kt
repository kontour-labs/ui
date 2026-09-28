package io.kontour.ui.locale

import kotlinx.datetime.DayOfWeek

// Reading what a platform hands over — LDML patterns, or the parts of a
// formatted example — into the facts and patterns `LocaleData` holds.
//
// Common, so the four platforms agree on what a pattern means, and so it is
// tested once, here, rather than on four devices.

/** The hour cycle an LDML time pattern is written on; 24-hour when it says nothing. */
internal fun hourCycleIn(pattern: String): HourCycle {
    var cycle: HourCycle? = null
    forEachField(pattern, literal = {}) { field, _ ->
        if (cycle == null) {
            cycle = when (field) {
                'h' -> HourCycle.H12
                'K' -> HourCycle.H11
                'H' -> HourCycle.H23
                'k' -> HourCycle.H24
                else -> null
            }
        }
    }
    return cycle ?: HourCycle.H23
}

/**
 * The order of a date pattern's day, month and year.
 *
 * Year first is year first whatever follows it; otherwise the day and the month
 * decide. A pattern missing any of them answers day first, the commonest order
 * in the world.
 */
internal fun dateOrderIn(pattern: String): DateOrder {
    var year = -1
    var month = -1
    var day = -1
    var index = 0
    forEachField(pattern, literal = { index++ }) { field, _ ->
        when (field) {
            'y', 'u' -> if (year < 0) year = index
            'M', 'L' -> if (month < 0) month = index
            'd' -> if (day < 0) day = index
        }
        index++
    }
    return when {
        year >= 0 && (month < 0 || year < month) && (day < 0 || year < day) -> DateOrder.YearMonthDay
        day >= 0 && month >= 0 && month < day -> DateOrder.MonthDayYear
        else -> DateOrder.DayMonthYear
    }
}

/**
 * A twelve-hour version of a time pattern, for a platform that will only give
 * the locale's own: the hour becomes `h`, and a day period goes after the time
 * if there was none.
 */
internal fun twelveHourFrom(pattern: String): String {
    var hasPeriod = false
    forEachField(pattern, literal = {}) { field, _ -> if (field == 'a' || field == 'b' || field == 'B') hasPeriod = true }
    val hours = mapFields(pattern) { field, _ -> if (field == 'H' || field == 'k' || field == 'K') "h" else null }
    return if (hasPeriod) hours else "$hours a"
}

/** A twenty-four-hour version of a time pattern: `HH`, and no day period. */
internal fun twentyFourHourFrom(pattern: String): String {
    val parts = partsOf(pattern).toMutableList()
    val period = parts.indexOfFirst { it.first == 'a' || it.first == 'b' || it.first == 'B' }
    if (period >= 0) {
        parts.removeAt(period)
        // The literal that joined it to the time goes with it: the one before
        // it when there is a field before that, otherwise the one after.
        when {
            period >= 2 && parts[period - 1].first == null -> parts.removeAt(period - 1)
            period < parts.size && parts[period].first == null -> parts.removeAt(period)
        }
    }
    return patternOf(parts.map { (field, text) -> if (field == 'h' || field == 'K' || field == 'k' || field == 'H') 'H' to "HH" else field to text })
}

/**
 * A month-and-year pattern from a long date pattern, for a platform with no
 * way to ask for one: the day and the weekday go, with whatever joined each to
 * the next field, and the month becomes its standalone form — Russian heads a
 * calendar `июнь 2026 г.`, not `июня`.
 */
internal fun monthYearFrom(longDate: String): String {
    val parts = partsOf(longDate)
    val kept = mutableListOf<Pair<Char?, String>>()
    var skipLiteral = false
    for (part in parts) {
        val (field, text) = part
        when {
            field == 'd' || field == 'E' || field == 'c' || field == 'e' -> {
                // The literal after a dropped field belongs to it (`d日`, `d. `,
                // `EEEE, `); one before it, when it ends the pattern, too.
                skipLiteral = true
            }
            field == null && skipLiteral -> skipLiteral = false
            field == 'M' -> kept += 'L' to "L".repeat(text.length)
            else -> {
                skipLiteral = false
                kept += part
            }
        }
    }
    while (kept.isNotEmpty() && kept.last().first == null && kept.last().second.isBlank()) kept.removeAt(kept.lastIndex)
    while (kept.isNotEmpty() && kept.first().first == null) kept.removeAt(0)
    return patternOf(kept).ifEmpty { "LLLL y" }
}

/**
 * The glue between a date and a time — `{1}, {0}`, `{1} {0}`, `{0} {1}` — found
 * by taking both out of a pattern that has both in.
 */
internal fun dateTimeGlue(combined: String, date: String, time: String): String {
    val at = combined.indexOf(date)
    val timeAt = combined.indexOf(time)
    if (at < 0 || timeAt < 0 || date.isEmpty() || time.isEmpty()) return "{1} {0}"
    return if (at < timeAt) {
        "{1}" + combined.substring(at + date.length, timeAt).unquoted() + "{0}"
    } else {
        "{0}" + combined.substring(timeAt + time.length, at).unquoted() + "{1}"
    }
}

private fun String.unquoted(): String = buildString { forEachField(this@unquoted, literal = { append(it) }) { _, _ -> } }

/**
 * A language's list pattern, read by joining placeholders with the platform's
 * own list formatter: two items give `two`, three give `start` and `end`, four
 * give `middle`.
 */
internal fun listPatternFrom(join: (List<String>) -> String?): ListPattern? {
    val a = ""
    val b = ""
    val c = ""
    val d = ""
    fun between(text: String, first: String, second: String): String? {
        val from = text.indexOf(first)
        val to = text.indexOf(second)
        return if (from < 0 || to < from) null else text.substring(from + first.length, to)
    }
    val two = join(listOf(a, b)) ?: return null
    val three = join(listOf(a, b, c)) ?: return null
    val four = join(listOf(a, b, c, d)) ?: return null
    val twoGlue = between(two, a, b) ?: return null
    val startGlue = between(three, a, b) ?: return null
    val endGlue = between(three, b, c) ?: return null
    val middleGlue = between(four, b, c) ?: return null
    return ListPattern(
        two = "{0}$twoGlue{1}",
        start = "{0}$startGlue{1}",
        middle = "{0}$middleGlue{1}",
        end = "{0}$endGlue{1}",
    )
}

/**
 * An LDML pattern from the parts of a formatted example, for a platform — a
 * browser — that formats but will not say how.
 *
 * [parts] are `(type, text)` as `Intl`'s `formatToParts` names them. The
 * example is the 9th of June 2026, a Tuesday, at 09:05: a day and an hour with
 * one digit each, so padding shows, and a month whose name is not a number.
 */
internal fun patternFromParts(
    parts: List<Pair<String, String>>,
    months: CalendarNames,
    weekdays: CalendarNames,
): String = buildString {
    for ((type, text) in parts) {
        val numeric = text.isNotEmpty() && text.all { it.isDigit() }
        append(
            when (type) {
                "year", "relatedYear" -> if (text.length == 2) "yy" else "y"
                "month" -> when {
                    numeric -> if (text.length == 2) "MM" else "M"
                    else -> monthField(text, months)
                }
                "day" -> if (text.length == 2) "dd" else "d"
                "weekday" -> when (text) {
                    weekdays.name(ExampleWeekday, NameWidth.Wide, standalone = false),
                    weekdays.name(ExampleWeekday, NameWidth.Wide, standalone = true) -> "EEEE"
                    weekdays.name(ExampleWeekday, NameWidth.Narrow, standalone = false) -> "EEEEE"
                    else -> "EEE"
                }
                "hour" -> if (text.length == 2) "HH" else "H"
                "minute" -> "mm"
                "second" -> "ss"
                "dayPeriod" -> "a"
                "era" -> ""
                else -> quoted(text)
            }
        )
    }
}

private fun monthField(text: String, months: CalendarNames): String {
    val index = ExampleMonth
    return when (text) {
        months.name(index, NameWidth.Wide, standalone = false) -> "MMMM"
        months.name(index, NameWidth.Wide, standalone = true) -> "LLLL"
        months.name(index, NameWidth.Abbreviated, standalone = false) -> "MMM"
        months.name(index, NameWidth.Abbreviated, standalone = true) -> "LLL"
        months.name(index, NameWidth.Narrow, standalone = false) -> "MMMMM"
        else -> "MMM"
    }
}

/** June, as an index into a month table. */
internal const val ExampleMonth = 5

/** Tuesday, as an index into a Monday-first weekday table. */
internal val ExampleWeekday = DayOfWeek.TUESDAY.ordinal

/** Literal text, quoted if it contains anything a pattern would read as a field. */
private fun quoted(text: String): String =
    if (text.any { it in 'a'..'z' || it in 'A'..'Z' || it == '\'' }) "'" + text.replace("'", "''") + "'" else text

/**
 * A pattern as its fields and literals, in order: a field's letter, or `null`
 * for literal text, with neighbouring literals joined — `, ` is one literal,
 * not two.
 */
private fun partsOf(pattern: String): List<Pair<Char?, String>> {
    val parts = mutableListOf<Pair<Char?, String>>()
    forEachField(
        pattern,
        literal = { text ->
            val last = parts.lastOrNull()
            if (last != null && last.first == null) parts[parts.lastIndex] = null to last.second + text else parts += null to text
        },
    ) { field, count -> parts += field to field.toString().repeat(count) }
    return parts
}

private fun patternOf(parts: List<Pair<Char?, String>>): String = buildString {
    for ((field, text) in parts) append(if (field == null) quoted(text) else text)
}
