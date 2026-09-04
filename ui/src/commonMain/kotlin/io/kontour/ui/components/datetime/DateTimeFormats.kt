package io.kontour.ui.components.datetime

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * How dates and times are written for this user.
 *
 * Two preferences the platform exposes and users genuinely notice: whether time
 * is 12- or 24-hour, and whether dates lead with the day or the month. Getting
 * either wrong is not cosmetic — "05/06" is two different days depending on the
 * answer.
 *
 * Provided once at the root through [LocalDateTimeFormats] rather than threaded
 * through every component, and mirrors the shape of `makeDateFormatters` in the
 * Android app so the port is a move rather than a rewrite.
 *
 * @param firstDayOfWeek Where a calendar's week starts. Monday across most of
 *   Europe and Australia, Sunday in North America.
 */
@Immutable
data class DateTimeFormats(
    val is24Hour: Boolean = true,
    val dayFirst: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {

    /** `14:05` or `2:05 pm`. */
    fun time(time: LocalTime): String = if (is24Hour) {
        "${time.hour.padded()}:${time.minute.padded()}"
    } else {
        val hour = when (time.hour % 12) {
            0 -> 12
            else -> time.hour % 12
        }
        val suffix = if (time.hour < 12) "am" else "pm"
        "$hour:${time.minute.padded()} $suffix"
    }

    /** `5 Jun 2026` or `Jun 5, 2026`. */
    fun dateShort(date: LocalDate): String = if (dayFirst) {
        "${date.day} ${date.month.shortName} ${date.year}"
    } else {
        "${date.month.shortName} ${date.day}, ${date.year}"
    }

    /** `Friday, 5 June 2026` or `Friday June 5, 2026`. */
    fun dateFull(date: LocalDate): String = if (dayFirst) {
        "${date.dayOfWeek.fullName}, ${date.day} ${date.month.fullName} ${date.year}"
    } else {
        "${date.dayOfWeek.fullName} ${date.month.fullName} ${date.day}, ${date.year}"
    }

    /** `June 2026` — a calendar header. */
    fun monthAndYear(date: LocalDate): String = "${date.month.fullName} ${date.year}"

    /** `14:05 5 Jun 2026`. */
    fun timeAndDate(time: LocalTime, date: LocalDate): String = "${time(time)} ${dateShort(date)}"

    /** One-letter column headings, starting at [firstDayOfWeek]. */
    fun weekdayInitials(): List<String> = weekdays().map { it.shortName.take(1) }

    /** The days of the week in display order, starting at [firstDayOfWeek]. */
    fun weekdays(): List<DayOfWeek> {
        val all = DayOfWeek.entries
        val start = all.indexOf(firstDayOfWeek)
        return List(7) { all[(start + it) % 7] }
    }

    /** How many columns from [firstDayOfWeek] this day sits at. */
    internal fun columnOf(day: DayOfWeek): Int {
        val all = DayOfWeek.entries
        return (all.indexOf(day) - all.indexOf(firstDayOfWeek) + 7) % 7
    }
}

/**
 * The formats in use, defaulting to the ones the platform says this user wants.
 *
 * The constructor's own defaults are Australian — 24-hour, day-first, weeks
 * from Monday — because a data class needs *some* answer and that is where the
 * app ships. Those defaults are not what a component gets, and that distinction
 * is the whole of this: an app in the United States was being told 9 June 2026
 * as "9 Jun", and "05/06" means two different days depending on who is reading
 * it. So the local resolves from the platform's locale instead.
 *
 * Read once, outside composition, and cached for the life of the process — a
 * `staticCompositionLocalOf`'s default is computed lazily on first read. A user
 * who changes their region while the app is running keeps the old formats until
 * it restarts, which is the same behaviour every platform's own date formatter
 * has and is not worth an observer per format.
 *
 * ### Android's 24-hour switch
 *
 * Android has a *setting* for 24-hour time that overrides the locale, and
 * reading it needs a `Context`, which nothing here has. So
 * [DateTimeFormats.is24Hour] on Android follows the locale rather than the
 * switch. An app that wants the switch
 * honoured provides the local itself, which is a one-liner and the reason this
 * is a composition local at all:
 *
 * ```kotlin
 * CompositionLocalProvider(
 *     LocalDateTimeFormats provides LocalDateTimeFormats.current.copy(
 *         is24Hour = DateFormat.is24HourFormat(context),
 *     )
 * ) { … }
 * ```
 */
val LocalDateTimeFormats = staticCompositionLocalOf { platformDateTimeFormats() }

/**
 * What the platform says about this user's date and time conventions.
 *
 * Each target answers three questions from its own locale machinery — is time
 * written on a 24-hour clock, does a short date lead with the day or the month,
 * and which day starts a week — and they are the same three [DateTimeFormats]
 * holds.
 */
internal expect fun platformDateTimeFormats(): DateTimeFormats

/**
 * Reads a locale's short-date pattern for whether the day comes first.
 *
 * Every platform here can produce a pattern in the same LDML-ish vocabulary —
 * `d/M/y` against `M/d/y` — so the answer is the same string comparison
 * everywhere and belongs in one place rather than in four.
 *
 * Quoted literals are skipped, because a pattern is allowed to contain them and
 * a `'d'` inside one is a letter in somebody's word rather than a field. Danish
 * writes `d. MMM y`; the full stop is fine but the quoting rule is the general
 * case and cheap to honour.
 */
internal fun dayBeforeMonthIn(datePattern: String): Boolean {
    var quoted = false
    var day = -1
    var month = -1
    for ((index, char) in datePattern.withIndex()) {
        if (char == '\'') {
            quoted = !quoted
            continue
        }
        if (quoted) continue
        if (day < 0 && (char == 'd' || char == 'D')) day = index
        if (month < 0 && (char == 'M' || char == 'L')) month = index
    }
    // A pattern with no month in it says nothing either way; keep the
    // constructor's answer rather than inventing one.
    if (day < 0 || month < 0) return DateTimeFormats().dayFirst
    return day < month
}

/**
 * Reads a locale's short-time pattern for whether the clock runs to 24.
 *
 * `h` and `K` are the two twelve-hour hour fields in LDML; `H` and `k` are the
 * twenty-four-hour ones. Looking for the twelve-hour ones rather than the others
 * means a pattern this does not understand comes out as 24-hour, which is the
 * safer way to be wrong: a 24-hour clock read by somebody expecting twelve is
 * unambiguous, and "1:00" where "13:00" was meant is not.
 */
internal fun is24HourIn(timePattern: String): Boolean {
    var quoted = false
    for (char in timePattern) {
        if (char == '\'') {
            quoted = !quoted
            continue
        }
        if (quoted) continue
        if (char == 'h' || char == 'K') return false
    }
    return true
}

private fun Int.padded(): String = if (this < 10) "0$this" else toString()

/**
 * No `else`, deliberately.
 *
 * Both this and [DayOfWeek.fullName] used to fall through to the enum's own
 * `name` — `JANUARY` where the reader wanted `January` — for a case that cannot
 * happen. Without the branch the compiler proves the map is complete, and an
 * entry appearing in a future kotlinx-datetime is a compile error here rather
 * than a shouted month in somebody's date picker.
 */
internal val Month.fullName: String
    get() = when (this) {
        Month.JANUARY -> "January"
        Month.FEBRUARY -> "February"
        Month.MARCH -> "March"
        Month.APRIL -> "April"
        Month.MAY -> "May"
        Month.JUNE -> "June"
        Month.JULY -> "July"
        Month.AUGUST -> "August"
        Month.SEPTEMBER -> "September"
        Month.OCTOBER -> "October"
        Month.NOVEMBER -> "November"
        Month.DECEMBER -> "December"
    }

internal val Month.shortName: String get() = fullName.take(3)

internal val DayOfWeek.fullName: String
    get() = when (this) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
    }

internal val DayOfWeek.shortName: String get() = fullName.take(3)
