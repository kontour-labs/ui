package io.kontour.ui.components.datetime

import kotlinx.datetime.DayOfWeek
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/**
 * Foundation answers all three, and answers them the way the user set them.
 *
 * `NSDateFormatter`'s `dateFormat` after a style has been set is the localised
 * *pattern* — `dd/MM/y` in Australia, `M/d/yy` in the United States — in the
 * same LDML vocabulary the other targets produce, which is why the reading of it
 * is shared.
 *
 * Unlike Android, the 12/24-hour answer here is the user's own: iOS folds its
 * "24-Hour Time" switch into the locale that `NSDateFormatter` resolves, so a
 * user who has turned it on gets `HH:mm` even in a region that would not.
 */
internal actual fun platformDateTimeFormats(): DateTimeFormats {
    val locale = NSLocale.currentLocale
    return DateTimeFormats(
        is24Hour = is24HourIn(pattern(date = false, locale = locale)),
        dayFirst = dayBeforeMonthIn(pattern(date = true, locale = locale)),
        firstDayOfWeek = NSCalendar.currentCalendar.firstWeekday.toDayOfWeek(),
    )
}

private fun pattern(date: Boolean, locale: NSLocale): String {
    val formatter = NSDateFormatter()
    formatter.locale = locale
    formatter.dateStyle = if (date) NSDateFormatterShortStyle else NSDateFormatterNoStyle
    formatter.timeStyle = if (date) NSDateFormatterNoStyle else NSDateFormatterShortStyle
    return formatter.dateFormat
}

/**
 * `NSCalendar.firstWeekday` counts from 1 for Sunday; `DayOfWeek` counts from 1
 * for Monday. Two one-based weeks that start on different days, which is exactly
 * the kind of pair that gets added instead of converted.
 */
private fun ULong.toDayOfWeek(): DayOfWeek {
    val sundayBased = toInt().coerceIn(1, 7)
    // 1 (Sunday) → SUNDAY, 2 (Monday) → MONDAY, …
    return DayOfWeek.entries[(sundayBased + 5) % 7]
}
