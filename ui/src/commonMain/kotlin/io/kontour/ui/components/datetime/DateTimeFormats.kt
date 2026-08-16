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
 * The formats in use.
 *
 * Defaults to 24-hour, day-first, week starting Monday — Australian conventions,
 * since that is where the app ships. Override at the root from the platform's
 * own settings:
 *
 * ```
 * CompositionLocalProvider(
 *     LocalDateTimeFormats provides DateTimeFormats(is24Hour = DateFormat.is24HourFormat(context))
 * ) { … }
 * ```
 */
val LocalDateTimeFormats = staticCompositionLocalOf { DateTimeFormats() }

private fun Int.padded(): String = if (this < 10) "0$this" else toString()

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
        else -> name
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
        else -> name
    }

internal val DayOfWeek.shortName: String get() = fullName.take(3)
