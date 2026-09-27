package io.kontour.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import java.text.DateFormatSymbols
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.Normalizer
import java.time.chrono.Chronology
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlinx.datetime.DayOfWeek

/**
 * A desktop reads its locale once, at start — the JDK has no notification for a
 * change, and a desktop app is a development host more often than a product.
 */
@Composable
actual fun platformLocale(): Locale = Locale(java.util.Locale.getDefault(java.util.Locale.Category.FORMAT).toLanguageTag())

@Composable
internal actual fun platformHourCycleSetting(): HourCycle? = null

internal actual fun decomposeCanonical(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

/**
 * The JDK's CLDR, through `java.text` and `java.time`.
 *
 * What the JDK cannot say, this fills in: it has no relative-time formatter, so
 * a language other than English gets the built-in English phrases for "in 5
 * min"; no list formatter before JDK 22, so lists are joined with commas; and
 * its `DecimalFormat` has one grouping size, so India's `1,23,456` is restored
 * here by region.
 */
internal actual fun readPlatformLocale(locale: Locale): PlatformReading {
    val jdk = java.util.Locale.forLanguageTag(locale.toLanguageTag())
    val tag = LocaleTag.of(locale)
    val shortDate = localizedPattern(FormatStyle.SHORT, null, jdk)
    val shortTime = localizedPattern(null, FormatStyle.SHORT, jdk)
    val conventions = DateTimeConventions(
        hourCycle = hourCycleIn(shortTime),
        dateOrder = dateOrderIn(shortDate),
        firstDayOfWeek = DayOfWeek.entries[WeekFields.of(jdk).firstDayOfWeek.value - 1],
    )
    val decimal = DecimalFormatSymbols.getInstance(jdk)
    val symbols = NumberSymbols(
        zeroDigit = decimal.zeroDigit,
        decimalSeparator = decimal.decimalSeparator.toString(),
        groupingSeparator = decimal.groupingSeparator.toString(),
        minusSign = decimal.minusSign.toString(),
        percentSign = decimal.percent.toString(),
        groupingSize = 3,
        secondaryGroupingSize = if (groupsInLakhs(tag)) 2 else 3,
        minimumGroupingDigits = if (tag.language in GroupsFromTenThousand) 2 else 1,
    )
    return PlatformReading(conventions, symbols) { jdkData(jdk, tag, symbols, shortTime) }
}

private fun jdkData(jdk: java.util.Locale, tag: LocaleTag, symbols: NumberSymbols, shortTime: String): LocaleData {
    val names = DateFormatSymbols.getInstance(jdk)
    val percent = NumberFormat.getPercentInstance(jdk) as? DecimalFormat
    fun months(style: TextStyle) = java.time.Month.entries.map { it.getDisplayName(style, jdk) }
    fun days(style: TextStyle) = java.time.DayOfWeek.entries.map { it.getDisplayName(style, jdk) }
    val dates = listOf(FormatStyle.SHORT, FormatStyle.MEDIUM, FormatStyle.LONG, FormatStyle.FULL)
        .map { localizedPattern(it, null, jdk) }
    return LocaleData(
        language = tag.language,
        symbols = symbols,
        percentPrefix = percent?.positivePrefix ?: "",
        percentSuffix = percent?.positiveSuffix ?: "%",
        months = CalendarNames(
            wide = months(TextStyle.FULL),
            abbreviated = months(TextStyle.SHORT),
            narrow = months(TextStyle.NARROW),
            wideStandalone = months(TextStyle.FULL_STANDALONE),
            abbreviatedStandalone = months(TextStyle.SHORT_STANDALONE),
            narrowStandalone = months(TextStyle.NARROW_STANDALONE),
        ),
        weekdays = CalendarNames(
            wide = days(TextStyle.FULL),
            abbreviated = days(TextStyle.SHORT),
            narrow = days(TextStyle.NARROW),
            wideStandalone = days(TextStyle.FULL_STANDALONE),
            abbreviatedStandalone = days(TextStyle.SHORT_STANDALONE),
            narrowStandalone = days(TextStyle.NARROW_STANDALONE),
        ),
        am = names.amPmStrings.getOrElse(0) { "AM" },
        pm = names.amPmStrings.getOrElse(1) { "PM" },
        datePatterns = dates,
        monthYearPattern = templatePattern("yMMMM", jdk) ?: monthYearFrom(dates[DateStyle.Long.ordinal]),
        twelveHourPattern = templatePattern("hmm", jdk) ?: twelveHourFrom(shortTime),
        twentyFourHourPattern = templatePattern("Hmm", jdk) ?: twentyFourHourFrom(shortTime),
        dateTimePattern = dateTimeGlue(
            combined = localizedPattern(FormatStyle.MEDIUM, FormatStyle.SHORT, jdk),
            date = dates[DateStyle.Medium.ordinal],
            time = shortTime,
        ),
        lists = emptyMap(),
        relative = englishRelativeWords,
    )
}

private fun localizedPattern(date: FormatStyle?, time: FormatStyle?, locale: java.util.Locale): String = runCatching {
    DateTimeFormatterBuilder.getLocalizedDateTimePattern(date, time, IsoChronology.INSTANCE, locale)
}.getOrDefault("")

/**
 * A pattern for a skeleton — `yMMMM`, `hmm` — which the JDK can give from 19
 * on. Found by reflection so the library still runs on the older JDKs it
 * targets, where this answers `null` and the pattern is derived instead.
 */
private fun templatePattern(skeleton: String, locale: java.util.Locale): String? = runCatching {
    TemplateMethod?.invoke(null, skeleton, IsoChronology.INSTANCE, locale) as? String
}.getOrNull()

private val TemplateMethod = runCatching {
    DateTimeFormatterBuilder::class.java.getMethod(
        "getLocalizedDateTimePattern",
        String::class.java,
        Chronology::class.java,
        java.util.Locale::class.java,
    )
}.getOrNull()

/** Where numbers are grouped in lakhs and crores: 1,23,45,678. */
private fun groupsInLakhs(tag: LocaleTag): Boolean =
    tag.region == "IN" || tag.language in setOf("hi", "bn", "gu", "kn", "ml", "mr", "ne", "or", "pa", "ta", "te", "as")

/** Languages that leave a four-digit number ungrouped: `1000`, but `10 000`. */
private val GroupsFromTenThousand = setOf("es", "pl")
