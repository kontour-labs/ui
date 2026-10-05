package io.kontour.ui.locale

import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.DayOfWeek

/**
 * The parts of a BCP 47 tag the library reads for itself.
 *
 * Every platform parses a tag, and every platform honours a different subset of
 * its `-u-` extensions: the JDK ignores `-u-hc-` when it hands out a pattern,
 * iOS drops keywords it does not know from some `NSLocale` paths, and a browser
 * honours all three. So the three that change conventions — `hc` the hour cycle,
 * `fw` the first day of the week, `nu` the digits — are read here, once, and
 * applied on top of whatever the platform answered. `en-AU-u-hc-h23` then means
 * a 24-hour clock on every target.
 */
internal class LocaleTag private constructor(
    val language: String,
    val region: String,
    val script: String,
    private val keywords: Map<String, String>,
) {
    /** The value of a `-u-` keyword — `h23` for `hc` — or `null`. */
    fun keyword(key: String): String? = keywords[key]

    /**
     * Whether the language is written right to left: by its script when the tag
     * names one — `pa-Arab` is, `pa` is not — and otherwise by the script the
     * language is usually written in.
     */
    val isRightToLeft: Boolean
        get() = if (script.isNotEmpty()) script in RightToLeftScripts else language in RightToLeftLanguages

    /** The hour cycle the tag asks for, if it asks. */
    val hourCycle: HourCycle?
        get() = when (keywords["hc"]) {
            "h11" -> HourCycle.H11
            "h12" -> HourCycle.H12
            "h23" -> HourCycle.H23
            "h24" -> HourCycle.H24
            else -> null
        }

    /** The first day of the week the tag asks for, if it asks. */
    val firstDayOfWeek: DayOfWeek?
        get() = when (keywords["fw"]) {
            "mon" -> DayOfWeek.MONDAY
            "tue" -> DayOfWeek.TUESDAY
            "wed" -> DayOfWeek.WEDNESDAY
            "thu" -> DayOfWeek.THURSDAY
            "fri" -> DayOfWeek.FRIDAY
            "sat" -> DayOfWeek.SATURDAY
            "sun" -> DayOfWeek.SUNDAY
            else -> null
        }

    /**
     * The zero of the numbering system the tag asks for, if it asks for one
     * whose ten digits are consecutive in Unicode — which is every decimal
     * system in use except the Han ideographs, and those are never a default.
     */
    val zeroDigit: Char?
        get() = keywords["nu"]?.let { NumberingSystems[it] }

    companion object {
        fun of(locale: Locale): LocaleTag = parse(locale.toLanguageTag())

        /**
         * A tag a platform reported, made into one `Locale(tag)` can take, or
         * null where it names no language at all.
         *
         * Platforms report POSIX locales as well as BCP 47 tags. Headless
         * Chromium's `navigator.language` is `en-US@posix`, and Compose's
         * `Locale(tag)` on the web is `new Intl.Locale(tag)` with nothing round
         * it: the RangeError it throws on that took the whole page down —
         * *"An odd browser locale blanks the whole page"*. `en_US.UTF-8`, `C`
         * and an empty string throw the same. So the modifier after `@` and
         * the charset after `.` go, `_` becomes `-`, and the two POSIX names
         * for no locale at all are none.
         */
        fun cleanPlatformTag(raw: String): String? {
            val tag = raw.trim().substringBefore('@').substringBefore('.').replace('_', '-').trim('-')
            return tag.takeUnless { it.isEmpty() || it.equals("C", ignoreCase = true) || it.equals("POSIX", ignoreCase = true) }
        }

        /**
         * The first of [candidates] a platform takes, as it writes it: each
         * cleaned ([cleanPlatformTag]) and offered to [canonical], which
         * answers the platform's own form of a tag it accepts and `null` for
         * one it refuses. [fallback] when it takes none.
         *
         * A browser's languages go through this with `Intl.getCanonicalLocales`
         * as [canonical]. Here, rather than beside it, so the choice is tested
         * on every target and not only in a browser that reports a bad tag.
         */
        fun firstUsableTag(candidates: List<String>, fallback: String, canonical: (String) -> String?): String {
            for (raw in candidates) {
                val tag = cleanPlatformTag(raw) ?: continue
                canonical(tag)?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            return fallback
        }

        fun parse(tag: String): LocaleTag {
            val parts = tag.replace('_', '-').split('-').filter { it.isNotEmpty() }
            val language = parts.firstOrNull()?.lowercase() ?: ""
            var script = ""
            var region = ""
            val keywords = mutableMapOf<String, String>()
            var index = 1
            // Script and region come before any singleton; variants are skipped.
            while (index < parts.size && parts[index].length > 1) {
                val part = parts[index]
                when {
                    part.length == 4 && part.all { it.isLetter() } && script.isEmpty() -> script = part.lowercase().replaceFirstChar { it.uppercase() }
                    (part.length == 2 && part.all { it.isLetter() }) || (part.length == 3 && part.all { it.isDigit() }) ->
                        if (region.isEmpty()) region = part.uppercase()
                }
                index++
            }
            // Extensions: a singleton, then its subtags up to the next singleton.
            while (index < parts.size) {
                val singleton = parts[index].lowercase()
                index++
                if (singleton == "x") break
                if (singleton != "u") {
                    while (index < parts.size && parts[index].length > 1) index++
                    continue
                }
                // Attributes (3–8 characters before the first key) are skipped;
                // a key is two characters, its value every longer subtag after it.
                while (index < parts.size && parts[index].length > 2) index++
                while (index < parts.size && parts[index].length == 2) {
                    val key = parts[index].lowercase()
                    index++
                    val value = buildList {
                        while (index < parts.size && parts[index].length > 2) add(parts[index++].lowercase())
                    }.joinToString("-")
                    keywords.getOrPut(key) { value.ifEmpty { "true" } }
                }
            }
            return LocaleTag(language, region, script, keywords)
        }

        private val RightToLeftScripts = setOf(
            "Adlm", "Arab", "Hebr", "Mand", "Mend", "Nkoo", "Rohg", "Samr", "Syrc", "Thaa", "Yezi",
        )

        private val RightToLeftLanguages = setOf(
            "ar", "arc", "ckb", "dv", "fa", "he", "iw", "ji", "ks", "prs", "ps", "sd", "syr", "ug", "ur", "yi",
        )

        /** CLDR's decimal numbering systems, by their `-u-nu-` names. */
        private val NumberingSystems = mapOf(
            "latn" to '0',
            "arab" to '٠',
            "arabext" to '۰',
            "beng" to '০',
            "deva" to '०',
            "fullwide" to '０',
            "gujr" to '૦',
            "guru" to '੦',
            "khmr" to '០',
            "knda" to '೦',
            "laoo" to '໐',
            "mlym" to '൦',
            "mymr" to '၀',
            "orya" to '୦',
            "tamldec" to '௦',
            "telu" to '౦',
            "thai" to '๐',
            "tibt" to '༠',
        )
    }
}

/**
 * Applies what [tag] asks for on top of what a platform answered.
 *
 * A platform's own answer for a plain tag stands; the tag's keywords, where it
 * has them, win — they are the one way a reader or an app says "this, whatever
 * the region usually does".
 */
internal fun DateTimeConventions.honouring(tag: LocaleTag): DateTimeConventions = copy(
    hourCycle = tag.hourCycle ?: hourCycle,
    firstDayOfWeek = tag.firstDayOfWeek ?: firstDayOfWeek,
)

internal fun NumberSymbols.honouring(tag: LocaleTag): NumberSymbols =
    tag.zeroDigit?.let { copy(zeroDigit = it) } ?: this
