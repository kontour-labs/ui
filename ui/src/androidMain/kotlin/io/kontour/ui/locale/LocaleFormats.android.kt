package io.kontour.ui.locale

import android.database.ContentObserver
import android.icu.text.DateFormat
import android.icu.text.DateFormatSymbols
import android.icu.text.DateTimePatternGenerator
import android.icu.text.DecimalFormat
import android.icu.text.DecimalFormatSymbols
import android.icu.text.DisplayContext
import android.icu.text.ListFormatter
import android.icu.text.NumberFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.ULocale
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.intl.Locale
import java.text.Normalizer
import kotlinx.datetime.DayOfWeek

/**
 * The configuration's first locale, which is the app's own where the reader has
 * set a per-app language and the device's otherwise — and a change to either
 * recreates the configuration, so reading it here is following it.
 */
@Composable
actual fun platformLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) { Locale(configuration.locales[0].toLanguageTag()) }
}

/**
 * Android's "Use 24-hour format" switch, followed live.
 *
 * `Settings.System.TIME_12_24` is `"24"` or `"12"` once the reader has chosen,
 * and absent while the clock follows the locale — which is the case this
 * answers `null` for, so the locale's own clock stands.
 */
@Composable
internal actual fun platformHourCycleSetting(): HourCycle? {
    val resolver = LocalContext.current.contentResolver
    fun read(): HourCycle? = when (Settings.System.getString(resolver, Settings.System.TIME_12_24)) {
        "24" -> HourCycle.H23
        "12" -> HourCycle.H12
        else -> null
    }
    var cycle by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                cycle = read()
            }
        }
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, observer)
        cycle = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return cycle
}

internal actual fun decomposeCanonical(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

/**
 * ICU, as Android ships it: CLDR's names and patterns, grouping sizes, and a
 * relative-time and a list formatter. Android 13 adds the "or" and unit lists;
 * before it, those two are joined with commas.
 */
internal actual fun readPlatformLocale(locale: Locale): PlatformReading {
    val uLocale = ULocale.forLanguageTag(locale.toLanguageTag())
    val tag = LocaleTag.of(locale)
    val generator = DateTimePatternGenerator.getInstance(uLocale)
    val shortDate = patternOf(DateFormat.getDateInstance(DateFormat.SHORT, uLocale))
    val preferredTime = generator.getBestPattern("jmm")
    val conventions = DateTimeConventions(
        hourCycle = hourCycleIn(preferredTime),
        dateOrder = dateOrderIn(shortDate),
        firstDayOfWeek = sundayBased(Calendar.getInstance(uLocale).firstDayOfWeek),
    )
    val decimal = DecimalFormatSymbols.getInstance(uLocale)
    val numbers = NumberFormat.getInstance(uLocale) as? DecimalFormat
    val primary = numbers?.groupingSize?.takeIf { it > 0 } ?: 3
    val symbols = NumberSymbols(
        zeroDigit = decimal.zeroDigit,
        decimalSeparator = decimal.decimalSeparatorString,
        groupingSeparator = decimal.groupingSeparatorString,
        minusSign = decimal.minusSignString,
        percentSign = decimal.percentString,
        groupingSize = primary,
        secondaryGroupingSize = numbers?.secondaryGroupingSize?.takeIf { it > 0 } ?: primary,
        minimumGroupingDigits = if (numbers != null && decimal.groupingSeparatorString !in numbers.format(1000)) 2 else 1,
    )
    return PlatformReading(conventions, symbols) { icuData(uLocale, tag, symbols, generator) }
}

/** ICU's currency format for [code], with the currency's own places; `null` for a code it does not know. */
internal actual fun readPlatformCurrency(locale: Locale, code: String): CurrencyFormat? {
    val uLocale = ULocale.forLanguageTag(locale.toLanguageTag())
    val currency = runCatching { android.icu.util.Currency.getInstance(code) }.getOrNull() ?: return null
    val format = NumberFormat.getCurrencyInstance(uLocale) as? DecimalFormat ?: return null
    format.currency = currency
    return CurrencyFormat(
        prefix = format.positivePrefix,
        suffix = format.positiveSuffix,
        fractionDigits = currency.defaultFractionDigits.takeIf { it >= 0 } ?: isoFractionDigits(code),
    )
}

private fun icuData(uLocale: ULocale, tag: LocaleTag, symbols: NumberSymbols, generator: DateTimePatternGenerator): LocaleData {
    val names = DateFormatSymbols.getInstance(uLocale)
    fun months(context: Int, width: Int) = names.getMonths(context, width).take(12)
    // ICU's weekdays run Sunday first, from index 1.
    fun days(context: Int, width: Int) = names.getWeekdays(context, width).let { all -> List(7) { all[(it + 1) % 7 + 1] } }
    val percent = NumberFormat.getPercentInstance(uLocale) as? DecimalFormat
    val dates = listOf(DateFormat.SHORT, DateFormat.MEDIUM, DateFormat.LONG, DateFormat.FULL)
        .map { patternOf(DateFormat.getDateInstance(it, uLocale)) }
    return LocaleData(
        language = tag.language,
        symbols = symbols,
        percentPrefix = percent?.positivePrefix ?: "",
        percentSuffix = percent?.positiveSuffix ?: "%",
        months = CalendarNames(
            wide = months(DateFormatSymbols.FORMAT, DateFormatSymbols.WIDE),
            abbreviated = months(DateFormatSymbols.FORMAT, DateFormatSymbols.ABBREVIATED),
            narrow = months(DateFormatSymbols.FORMAT, DateFormatSymbols.NARROW),
            wideStandalone = months(DateFormatSymbols.STANDALONE, DateFormatSymbols.WIDE),
            abbreviatedStandalone = months(DateFormatSymbols.STANDALONE, DateFormatSymbols.ABBREVIATED),
            narrowStandalone = months(DateFormatSymbols.STANDALONE, DateFormatSymbols.NARROW),
        ),
        weekdays = CalendarNames(
            wide = days(DateFormatSymbols.FORMAT, DateFormatSymbols.WIDE),
            abbreviated = days(DateFormatSymbols.FORMAT, DateFormatSymbols.ABBREVIATED),
            narrow = days(DateFormatSymbols.FORMAT, DateFormatSymbols.NARROW),
            wideStandalone = days(DateFormatSymbols.STANDALONE, DateFormatSymbols.WIDE),
            abbreviatedStandalone = days(DateFormatSymbols.STANDALONE, DateFormatSymbols.ABBREVIATED),
            narrowStandalone = days(DateFormatSymbols.STANDALONE, DateFormatSymbols.NARROW),
        ),
        am = names.amPmStrings.getOrElse(0) { "AM" },
        pm = names.amPmStrings.getOrElse(1) { "PM" },
        datePatterns = dates,
        monthYearPattern = generator.getBestPattern("yMMMM"),
        twelveHourPattern = generator.getBestPattern("hmm"),
        twentyFourHourPattern = generator.getBestPattern("Hmm"),
        dateTimePattern = generator.dateTimeFormat,
        lists = icuLists(uLocale),
        relative = IcuRelativeWords(uLocale),
    )
}

private fun patternOf(format: DateFormat): String = (format as? SimpleDateFormat)?.toPattern() ?: ""

private fun sundayBased(day: Int): DayOfWeek = DayOfWeek.entries[(day.coerceIn(1, 7) + 5) % 7]

private fun icuLists(uLocale: ULocale): Map<ListStyle, ListPattern> = buildMap {
    fun read(formatter: ListFormatter): ListPattern? = listPatternFrom { items -> synchronized(formatter) { formatter.format(items) } }
    read(ListFormatter.getInstance(uLocale))?.let { put(ListStyle.And, it) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        read(ListFormatter.getInstance(uLocale, ListFormatter.Type.OR, ListFormatter.Width.WIDE))?.let { put(ListStyle.Or, it) }
        read(ListFormatter.getInstance(uLocale, ListFormatter.Type.UNITS, ListFormatter.Width.SHORT))?.let { put(ListStyle.Unit, it) }
    }
}

/** ICU's short relative phrases: `in 5 min.`, `vor 5 Min.`. Not thread-safe, so held under a lock. */
private class IcuRelativeWords(uLocale: ULocale) : RelativeWords {
    private val formatter = RelativeDateTimeFormatter.getInstance(
        uLocale,
        null,
        RelativeDateTimeFormatter.Style.SHORT,
        DisplayContext.CAPITALIZATION_NONE,
    )

    override fun format(value: Long, formatted: String, unit: RelativeUnit): String = synchronized(formatter) {
        formatter.format(
            kotlin.math.abs(value).toDouble(),
            if (value < 0) RelativeDateTimeFormatter.Direction.LAST else RelativeDateTimeFormatter.Direction.NEXT,
            when (unit) {
                RelativeUnit.Second -> RelativeDateTimeFormatter.RelativeUnit.SECONDS
                RelativeUnit.Minute -> RelativeDateTimeFormatter.RelativeUnit.MINUTES
                RelativeUnit.Hour -> RelativeDateTimeFormatter.RelativeUnit.HOURS
                RelativeUnit.Day -> RelativeDateTimeFormatter.RelativeUnit.DAYS
                RelativeUnit.Week -> RelativeDateTimeFormatter.RelativeUnit.WEEKS
                RelativeUnit.Month -> RelativeDateTimeFormatter.RelativeUnit.MONTHS
                RelativeUnit.Year -> RelativeDateTimeFormatter.RelativeUnit.YEARS
            },
        )
    }

    override val now: String = synchronized(formatter) {
        formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
    }
}
