package io.kontour.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.LayoutDirection

/**
 * The locale this device is set to, followed live.
 *
 * What `KontourTheme` uses when it is given no locale. A reader who changes
 * their language in Settings and comes back to the app sees it in the new
 * language's formats without a restart: Android reports the change through the
 * configuration, iOS through `NSCurrentLocaleDidChangeNotification`, a browser
 * through `languagechange`. A desktop JVM reads its locale once, at start.
 */
@Composable
expect fun platformLocale(): Locale

/**
 * The formats for [locale], built once per locale and remembered.
 *
 * The platform's names and patterns, the platform's conventions, and on
 * Android the reader's own 24-hour switch, which overrides the locale's clock
 * and is followed live. A tag that sets the hour cycle itself — `-u-hc-h23` —
 * wins over the switch: an app that asked for a clock gets that clock.
 */
@Composable
fun rememberLocaleFormats(locale: Locale): LocaleFormats {
    val base = remember(locale) { LocaleFormats.of(locale) }
    val switch = platformHourCycleSetting()
    return remember(base, switch) {
        if (switch == null || LocaleTag.of(locale).hourCycle != null || base.conventions.hourCycle.usesDayPeriod == switch.usesDayPeriod) {
            base
        } else {
            base.withConventions(base.conventions.copy(hourCycle = switch))
        }
    }
}

/**
 * An hour cycle the reader chose in the platform's settings, over their
 * locale's, or `null` where there is no such setting or it is left to follow the
 * locale. Only Android has one the library can read.
 */
@Composable
internal expect fun platformHourCycleSetting(): HourCycle?

/** Whether [locale] is written right to left. */
internal val Locale.layoutDirection: LayoutDirection
    get() = if (LocaleTag.of(this).isRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr

/**
 * What a platform can say about a locale, before the library's own reading of
 * its tag is applied.
 *
 * [data] is only asked for a language other than English, whose words the
 * library keeps itself.
 */
internal class PlatformReading(
    val conventions: DateTimeConventions,
    val symbols: NumberSymbols,
    val data: () -> LocaleData,
)

/** The platform's reading of [locale]. May be slow — it is called once per locale. */
internal expect fun readPlatformLocale(locale: Locale): PlatformReading

/** [LocaleFormats.of], built from the platform and cached by tag. */
internal fun localeFormatsFor(locale: Locale): LocaleFormats = LocaleFormatsCache.get(locale)

private object LocaleFormatsCache {
    @kotlin.concurrent.Volatile
    private var built: Map<String, LocaleFormats> = emptyMap()

    fun get(locale: Locale): LocaleFormats {
        val key = locale.toLanguageTag()
        built[key]?.let { return it }
        val made = build(locale)
        built = built + (key to made)
        return made
    }

    private fun build(locale: Locale): LocaleFormats {
        val tag = LocaleTag.of(locale)
        val reading = runCatching { readPlatformLocale(locale) }.getOrNull()
        val conventions = (reading?.conventions ?: DateTimeConventions()).honouring(tag)
        val symbols = (reading?.symbols ?: NumberSymbols()).honouring(tag)
        val data = when {
            tag.language == "en" || reading == null -> englishData(writesAmericanEnglish(tag.region), symbols)
            else -> runCatching { reading.data() }.getOrNull()
                ?.let { if (it.symbols == symbols) it else it.copyWith(symbols) }
                ?: englishData(writesAmericanEnglish(tag.region), symbols)
        }
        return PatternLocaleFormats(locale, data, conventions)
    }
}

/**
 * `Theme.formats.integer`, remembered, as a counter's default `format`: the same
 * function instance until the formats change, so a component handed it can skip.
 */
@Composable
internal fun rememberIntegerFormat(): (Int) -> String {
    val formats = io.kontour.ui.theme.Theme.formats
    return remember(formats) { { value: Int -> formats.integer(value) } }
}
