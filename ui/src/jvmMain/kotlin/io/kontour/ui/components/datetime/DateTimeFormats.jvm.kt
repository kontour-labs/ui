package io.kontour.ui.components.datetime

import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.datetime.DayOfWeek

/**
 * The JDK already knows all three, from CLDR.
 *
 * `Locale.Category.FORMAT` rather than the plain default: a JVM can be told to
 * present its interface in one language and format its numbers and dates in
 * another, and dates are the second of those.
 *
 */
internal actual fun platformDateTimeFormats(): DateTimeFormats {
    val locale = Locale.getDefault(Locale.Category.FORMAT)
    val date = localizedPattern(dateStyle = FormatStyle.SHORT, timeStyle = null, locale = locale)
    val time = localizedPattern(dateStyle = null, timeStyle = FormatStyle.SHORT, locale = locale)
    return DateTimeFormats(
        is24Hour = is24HourIn(time),
        dayFirst = dayBeforeMonthIn(date),
        firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek.toKotlin(),
    )
}

private fun localizedPattern(
    dateStyle: FormatStyle?,
    timeStyle: FormatStyle?,
    locale: Locale,
): String = runCatching {
    DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        dateStyle,
        timeStyle,
        IsoChronology.INSTANCE,
        locale,
    )
}.getOrDefault("")

/**
 * `java.time.DayOfWeek` and `kotlinx.datetime.DayOfWeek` are two enums with the
 * same seven entries in the same order, and no relationship the compiler knows
 * about. `value` is 1 for Monday in the first and `entries` is Monday-first in
 * the second, so the index is the whole of the conversion.
 */
private fun java.time.DayOfWeek.toKotlin(): DayOfWeek = DayOfWeek.entries[value - 1]
