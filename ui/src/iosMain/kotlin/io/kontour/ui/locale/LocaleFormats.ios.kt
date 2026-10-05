package io.kontour.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.DayOfWeek
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCurrentLocaleDidChangeNotification
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSListFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterPercentStyle
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSRelativeDateTimeFormatterStyleNamed
import platform.Foundation.NSRelativeDateTimeFormatterStyleNumeric
import platform.Foundation.NSRelativeDateTimeFormatterUnitsStyleShort
import platform.Foundation.NSString
import platform.Foundation.currentLocale
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.localeIdentifier

/**
 * The reader's locale, followed through `NSCurrentLocaleDidChangeNotification`,
 * which iOS posts when Language & Region changes under a running app.
 */
@Composable
actual fun platformLocale(): Locale = observeLocaleChange { Locale(currentTag()) }

/**
 * iOS's 24-Hour Time switch, where it differs from what the region would use.
 *
 * `NSLocale.currentLocale` carries the switch and a locale built from the same
 * identifier does not, so asking both for their clock and comparing is how the
 * choice is told apart from the region's default.
 */
@Composable
internal actual fun platformHourCycleSetting(): HourCycle? = observeLocaleChange {
    val chosen = hourCycleIn(template("jmm", NSLocale.currentLocale))
    val regional = hourCycleIn(template("jmm", NSLocale(localeIdentifier = NSLocale.currentLocale.localeIdentifier)))
    if (chosen.usesDayPeriod == regional.usesDayPeriod) null else chosen
}

@Composable
private fun <T> observeLocaleChange(read: () -> T): T {
    var value by remember { mutableStateOf(read()) }
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = NSCurrentLocaleDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> value = read() }
        value = read()
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    return value
}

private fun currentTag(): String =
    LocaleTag.cleanPlatformTag(NSLocale.currentLocale.localeIdentifier) ?: "en-AU"

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun decomposeCanonical(text: String): String =
    (text as NSString).decomposedStringWithCanonicalMapping

/**
 * Foundation's CLDR: `NSDateFormatter`'s patterns and names, `NSNumberFormatter`'s
 * symbols, `NSRelativeDateTimeFormatter` and `NSListFormatter`. iOS has no "or"
 * or unit list, so those two are joined with commas.
 */
internal actual fun readPlatformLocale(locale: Locale): PlatformReading {
    val ns = NSLocale(localeIdentifier = locale.toLanguageTag())
    val tag = LocaleTag.of(locale)
    val shortDate = styled(ns, NSDateFormatterShortStyle, NSDateFormatterNoStyle)
    val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    calendar.locale = ns
    val conventions = DateTimeConventions(
        hourCycle = hourCycleIn(template("jmm", ns)),
        dateOrder = dateOrderIn(shortDate),
        firstDayOfWeek = DayOfWeek.entries[(calendar.firstWeekday.toInt().coerceIn(1, 7) + 5) % 7],
    )
    val numbers = NSNumberFormatter()
    numbers.locale = ns
    numbers.numberStyle = NSNumberFormatterDecimalStyle
    val primary = numbers.groupingSize.toInt().takeIf { it > 0 } ?: 3
    val grouping = numbers.groupingSeparator
    val symbols = NumberSymbols(
        zeroDigit = numbers.stringFromNumber(NSNumber(int = 0))?.firstOrNull() ?: '0',
        decimalSeparator = numbers.decimalSeparator,
        groupingSeparator = grouping,
        minusSign = numbers.minusSign,
        percentSign = numbers.percentSymbol,
        groupingSize = primary,
        secondaryGroupingSize = numbers.secondaryGroupingSize.toInt().takeIf { it > 0 } ?: primary,
        minimumGroupingDigits = if (grouping !in (numbers.stringFromNumber(NSNumber(int = 1000)) ?: "")) 2 else 1,
    )
    return PlatformReading(conventions, symbols) { foundationData(ns, tag, symbols) }
}

/** Foundation's currency format for [code], with the currency's own places. */
internal actual fun readPlatformCurrency(locale: Locale, code: String): CurrencyFormat? {
    val money = NSNumberFormatter()
    money.locale = NSLocale(localeIdentifier = locale.toLanguageTag())
    money.numberStyle = NSNumberFormatterCurrencyStyle
    money.currencyCode = code
    return CurrencyFormat(
        prefix = money.positivePrefix,
        suffix = money.positiveSuffix,
        fractionDigits = money.maximumFractionDigits.toInt(),
    )
}

private fun foundationData(ns: NSLocale, tag: LocaleTag, symbols: NumberSymbols): LocaleData {
    val names = NSDateFormatter()
    names.locale = ns
    fun strings(list: List<*>?): List<String> = list.orEmpty().map { it as? String ?: "" }
    // Foundation's weekdays run Sunday first.
    fun days(list: List<*>?): List<String> = strings(list).let { all -> if (all.size == 7) List(7) { all[(it + 1) % 7] } else all }
    val percent = NSNumberFormatter()
    percent.locale = ns
    percent.numberStyle = NSNumberFormatterPercentStyle
    val dates = listOf(NSDateFormatterShortStyle, NSDateFormatterMediumStyle, NSDateFormatterLongStyle, NSDateFormatterFullStyle)
        .map { styled(ns, it, NSDateFormatterNoStyle) }
    val shortTime = styled(ns, NSDateFormatterNoStyle, NSDateFormatterShortStyle)
    return LocaleData(
        language = tag.language,
        symbols = symbols,
        percentPrefix = percent.positivePrefix,
        percentSuffix = percent.positiveSuffix,
        months = CalendarNames(
            wide = strings(names.monthSymbols),
            abbreviated = strings(names.shortMonthSymbols),
            narrow = strings(names.veryShortMonthSymbols),
            wideStandalone = strings(names.standaloneMonthSymbols),
            abbreviatedStandalone = strings(names.shortStandaloneMonthSymbols),
            narrowStandalone = strings(names.veryShortStandaloneMonthSymbols),
        ),
        weekdays = CalendarNames(
            wide = days(names.weekdaySymbols),
            abbreviated = days(names.shortWeekdaySymbols),
            narrow = days(names.veryShortWeekdaySymbols),
            wideStandalone = days(names.standaloneWeekdaySymbols),
            abbreviatedStandalone = days(names.shortStandaloneWeekdaySymbols),
            narrowStandalone = days(names.veryShortStandaloneWeekdaySymbols),
        ),
        am = names.AMSymbol,
        pm = names.PMSymbol,
        datePatterns = dates,
        monthYearPattern = template("yMMMM", ns),
        twelveHourPattern = template("hmm", ns),
        twentyFourHourPattern = template("Hmm", ns),
        dateTimePattern = dateTimeGlue(
            combined = styled(ns, NSDateFormatterMediumStyle, NSDateFormatterShortStyle),
            date = dates[DateStyle.Medium.ordinal],
            time = shortTime,
        ),
        lists = foundationLists(ns),
        relative = FoundationRelativeWords(ns),
    )
}

private fun styled(ns: NSLocale, date: ULong, time: ULong): String {
    val formatter = NSDateFormatter()
    formatter.locale = ns
    formatter.dateStyle = date
    formatter.timeStyle = time
    return formatter.dateFormat
}

private fun template(skeleton: String, ns: NSLocale): String =
    NSDateFormatter.dateFormatFromTemplate(skeleton, 0uL, ns) ?: ""

private fun foundationLists(ns: NSLocale): Map<ListStyle, ListPattern> {
    val formatter = NSListFormatter()
    formatter.locale = ns
    val and = listPatternFrom { items -> formatter.stringFromItems(items) } ?: return emptyMap()
    return mapOf(ListStyle.And to and)
}

private class FoundationRelativeWords(ns: NSLocale) : RelativeWords {
    private val numeric = NSRelativeDateTimeFormatter().also {
        it.locale = ns
        it.unitsStyle = NSRelativeDateTimeFormatterUnitsStyleShort
        it.dateTimeStyle = NSRelativeDateTimeFormatterStyleNumeric
    }

    override fun format(value: Long, formatted: String, unit: RelativeUnit): String {
        val components = NSDateComponents()
        val amount = value
        when (unit) {
            RelativeUnit.Second -> components.second = amount
            RelativeUnit.Minute -> components.minute = amount
            RelativeUnit.Hour -> components.hour = amount
            RelativeUnit.Day -> components.day = amount
            RelativeUnit.Week -> components.weekOfMonth = amount
            RelativeUnit.Month -> components.month = amount
            RelativeUnit.Year -> components.year = amount
        }
        return numeric.localizedStringFromDateComponents(components)
    }

    override val now: String = NSRelativeDateTimeFormatter().let {
        it.locale = ns
        it.dateTimeStyle = NSRelativeDateTimeFormatterStyleNamed
        it.localizedStringFromTimeInterval(0.0)
    }
}
