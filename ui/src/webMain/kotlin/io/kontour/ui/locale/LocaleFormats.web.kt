@file:OptIn(ExperimentalWasmJsInterop::class)

package io.kontour.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.intl.Locale
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.window
import kotlinx.datetime.DayOfWeek
import org.w3c.dom.events.Event

/** The browser's first language, followed through `languagechange`. */
@Composable
actual fun platformLocale(): Locale {
    var tag by remember { mutableStateOf(browserLanguage()) }
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { tag = browserLanguage() }
        window.addEventListener("languagechange", listener)
        tag = browserLanguage()
        onDispose { window.removeEventListener("languagechange", listener) }
    }
    return remember(tag) { Locale(tag) }
}

@Composable
internal actual fun platformHourCycleSetting(): HourCycle? = null

internal actual fun decomposeCanonical(text: String): String = normalizeNfd(text)

/**
 * `Intl`, which formats in every locale the browser knows but never says how.
 *
 * So the patterns are read back from examples: a date formatted at each of the
 * four lengths, broken into its parts, is the pattern for that length. Every
 * call is primitive in and primitive out — a string of parts joined with control
 * characters — because that is what crosses into both the js and the wasm
 * target, and each is wrapped, because `Intl` is where browsers differ most.
 */
internal actual fun readPlatformLocale(locale: Locale): PlatformReading {
    val tag = locale.toLanguageTag()
    val parsed = LocaleTag.of(locale)
    val shortDate = parts(intlDateParts(tag, """{"dateStyle":"short"}"""))
    val conventions = DateTimeConventions(
        hourCycle = when (intlHourCycle(tag)) {
            "h11" -> HourCycle.H11
            "h12" -> HourCycle.H12
            "h24" -> HourCycle.H24
            else -> HourCycle.H23
        },
        dateOrder = orderOf(shortDate),
        firstDayOfWeek = intlFirstDay(tag).let { if (it in 1..7) DayOfWeek.entries[it - 1] else DayOfWeek.MONDAY },
    )
    val number = parts(intlNumberParts(tag, """{"minimumFractionDigits":1,"maximumFractionDigits":1}""", -1234567.5))
    val integers = number.filter { it.first == "integer" }.map { it.second }
    val group = number.firstOrNull { it.first == "group" }?.second ?: ","
    val thousand = parts(intlNumberParts(tag, "{}", 1000.0))
    val symbols = NumberSymbols(
        zeroDigit = parts(intlNumberParts(tag, "{}", 0.0)).firstOrNull { it.first == "integer" }?.second?.firstOrNull() ?: '0',
        decimalSeparator = number.firstOrNull { it.first == "decimal" }?.second ?: ".",
        groupingSeparator = group,
        minusSign = number.firstOrNull { it.first == "minusSign" }?.second ?: "-",
        percentSign = parts(intlNumberParts(tag, """{"style":"percent"}""", 0.5)).firstOrNull { it.first == "percentSign" }?.second ?: "%",
        groupingSize = integers.lastOrNull()?.length?.takeIf { integers.size > 1 } ?: 3,
        secondaryGroupingSize = integers.getOrNull(integers.size - 2)?.length?.takeIf { integers.size > 2 }
            ?: integers.lastOrNull()?.length?.takeIf { integers.size > 1 } ?: 3,
        minimumGroupingDigits = if (thousand.any { it.first == "group" }) 1 else 2,
    )
    return PlatformReading(conventions, symbols) { intlData(tag, parsed, symbols) }
}

private fun intlData(tag: String, parsed: LocaleTag, symbols: NumberSymbols): LocaleData {
    fun names(month: Boolean, style: String, standalone: Boolean): List<String> =
        intlNames(tag, month, style, standalone).split(Separator)
    fun calendar(month: Boolean) = CalendarNames(
        wide = names(month, "long", standalone = false),
        abbreviated = names(month, "short", standalone = false),
        narrow = names(month, "narrow", standalone = false),
        wideStandalone = names(month, "long", standalone = true),
        abbreviatedStandalone = names(month, "short", standalone = true),
        narrowStandalone = names(month, "narrow", standalone = true),
    )
    val months = calendar(month = true)
    val weekdays = calendar(month = false)
    fun pattern(options: String) = patternFromParts(parts(intlDateParts(tag, options)), months, weekdays)
    val periods = intlDayPeriods(tag).split(Separator)
    val percent = parts(intlNumberParts(tag, """{"style":"percent"}""", 0.5))
    val firstNumber = percent.indexOfFirst { it.first == "integer" }
    val lastNumber = percent.indexOfLast { it.first == "integer" }
    val medium = parts(intlDateParts(tag, """{"dateStyle":"medium","timeStyle":"short"}"""))
    return LocaleData(
        language = parsed.language,
        symbols = symbols,
        percentPrefix = if (firstNumber < 0) "" else percent.take(firstNumber).joinToString("") { it.second },
        percentSuffix = if (lastNumber < 0) "%" else percent.drop(lastNumber + 1).joinToString("") { it.second },
        months = months,
        weekdays = weekdays,
        am = periods.getOrElse(0) { "AM" },
        pm = periods.getOrElse(1) { "PM" },
        datePatterns = listOf("short", "medium", "long", "full").map { pattern("""{"dateStyle":"$it"}""") },
        monthYearPattern = pattern("""{"year":"numeric","month":"long"}"""),
        twelveHourPattern = mapFields(pattern("""{"hour":"numeric","minute":"2-digit","hourCycle":"h12"}""")) { field, count ->
            if (field == 'H') "h".repeat(count) else null
        },
        twentyFourHourPattern = pattern("""{"hour":"2-digit","minute":"2-digit","hourCycle":"h23"}"""),
        dateTimePattern = glueOf(medium),
        lists = buildMap {
            for ((style, type) in listOf(ListStyle.And to "conjunction", ListStyle.Or to "disjunction", ListStyle.Unit to "unit")) {
                listPatternFrom { items -> intlList(tag, type, items.joinToString(Separator)).ifEmpty { null } }?.let { put(style, it) }
            }
        },
        relative = IntlRelativeWords(tag),
    )
}

private class IntlRelativeWords(private val tag: String) : RelativeWords {
    override fun format(value: Long, formatted: String, unit: RelativeUnit): String =
        intlRelative(tag, value.toDouble(), unit.name.lowercase()).ifEmpty { englishRelativeWords.format(value, formatted, unit) }

    override val now: String = intlRelativeNow(tag).ifEmpty { englishRelativeWords.now }
}

private const val Separator = "\u0002"

/** `type\u0001text\u0002type\u0001text…` back into pairs. */
private fun parts(joined: String): List<Pair<String, String>> =
    if (joined.isEmpty()) emptyList() else joined.split(Separator).map { it.substringBefore('\u0001') to it.substringAfter('\u0001', "") }

private fun orderOf(parts: List<Pair<String, String>>): DateOrder {
    val year = parts.indexOfFirst { it.first == "year" || it.first == "relatedYear" }
    val month = parts.indexOfFirst { it.first == "month" }
    val day = parts.indexOfFirst { it.first == "day" }
    return when {
        year >= 0 && (month < 0 || year < month) -> DateOrder.YearMonthDay
        day >= 0 && month >= 0 && month < day -> DateOrder.MonthDayYear
        else -> DateOrder.DayMonthYear
    }
}

private fun glueOf(parts: List<Pair<String, String>>): String {
    val dateTypes = setOf("year", "month", "day", "weekday", "relatedYear", "era")
    val timeTypes = setOf("hour", "minute", "second", "dayPeriod")
    val lastDate = parts.indexOfLast { it.first in dateTypes }
    val firstTime = parts.indexOfFirst { it.first in timeTypes }
    val lastTime = parts.indexOfLast { it.first in timeTypes }
    val firstDate = parts.indexOfFirst { it.first in dateTypes }
    return when {
        lastDate < 0 || firstTime < 0 -> "{1} {0}"
        lastDate < firstTime -> "{1}" + parts.subList(lastDate + 1, firstTime).joinToString("") { it.second } + "{0}"
        lastTime < firstDate -> "{0}" + parts.subList(lastTime + 1, firstDate).joinToString("") { it.second } + "{1}"
        else -> "{1} {0}"
    }
}

/**
 * The first of the browser's languages that `Intl` takes, as it writes it.
 *
 * Read with care because Compose's `Locale(tag)` is `new Intl.Locale(tag)` with
 * nothing round it, and a tag it rejects throws through the first composition:
 * headless Chromium reports `en-US@posix`, and the page came up blank. Each of
 * `navigator.languages`, then `navigator.language`, is cleaned
 * ([LocaleTag.cleanPlatformTag]) and offered to `Intl.getCanonicalLocales`, and
 * the first it accepts is the one; with none, the library's own default.
 */
private fun browserLanguage(): String {
    for (raw in browserLanguageList().split(Separator)) {
        val tag = LocaleTag.cleanPlatformTag(raw) ?: continue
        val canonical = intlCanonical(tag)
        if (canonical.isNotEmpty()) return canonical
    }
    return "en-AU"
}

private fun browserLanguageList(): String = js(
    """(function(){try{
        if(typeof navigator==='undefined')return '';
        var all=(navigator.languages&&navigator.languages.length)?Array.prototype.slice.call(navigator.languages):[];
        if(navigator.language)all.push(navigator.language);
        return all.join('\u0002');
    }catch(e){return '';}})()"""
)

/** `tag` as `Intl` writes it, or empty where `Intl` rejects it. */
private fun intlCanonical(tag: String): String = js(
    """(function(){try{
        var c=Intl.getCanonicalLocales(tag);
        if(!c.length)return '';
        new Intl.Locale(c[0]);
        return c[0];
    }catch(e){return '';}})()"""
)

private fun normalizeNfd(text: String): String = js("text.normalize('NFD')")

private fun intlHourCycle(tag: String): String = js(
    """(function(){try{
        var o=new Intl.DateTimeFormat(tag,{hour:'numeric'}).resolvedOptions();
        return o.hourCycle||(o.hour12?'h12':'h23');
    }catch(e){return 'h23';}})()"""
)

/** 1 for Monday through 7 for Sunday, or 0 when the browser will not say. */
private fun intlFirstDay(tag: String): Int = js(
    """(function(){try{
        var loc=new Intl.Locale(tag);
        var info=typeof loc.getWeekInfo==='function'?loc.getWeekInfo():loc.weekInfo;
        return (info&&info.firstDay)?info.firstDay:0;
    }catch(e){return 0;}})()"""
)

/** The parts of the example date, 9 June 2026 at 09:05, under [options] (JSON). */
private fun intlDateParts(tag: String, options: String): String = js(
    """(function(){try{
        var p=new Intl.DateTimeFormat(tag,JSON.parse(options)).formatToParts(new Date(2026,5,9,9,5));
        return p.map(function(x){return x.type+'\u0001'+x.value;}).join('\u0002');
    }catch(e){return '';}})()"""
)

private fun intlNumberParts(tag: String, options: String, value: Double): String = js(
    """(function(){try{
        var p=new Intl.NumberFormat(tag,JSON.parse(options)).formatToParts(value);
        return p.map(function(x){return x.type+'\u0001'+x.value;}).join('\u0002');
    }catch(e){return '';}})()"""
)

/**
 * Twelve month names, or seven weekday names from Monday, in [style]. On their
 * own when [standalone]; otherwise lifted out of a date, which is where
 * languages that decline them use the other form.
 */
private fun intlNames(tag: String, month: Boolean, style: String, standalone: Boolean): String = js(
    """(function(){try{
        var out=[];
        var opts=month?{month:style}:{weekday:style};
        if(!standalone){ if(month){opts.day='numeric';} else {opts.day='numeric';opts.month='long';} }
        var f=new Intl.DateTimeFormat(tag,opts);
        var n=month?12:7;
        for(var i=0;i<n;i++){
            var d=month?new Date(2026,i,15):new Date(2026,5,8+i);
            var parts=f.formatToParts(d);
            var want=month?'month':'weekday';
            var v='';
            for(var j=0;j<parts.length;j++){ if(parts[j].type===want){v=parts[j].value;break;} }
            out.push(v);
        }
        return out.join('\u0002');
    }catch(e){return '';}})()"""
)

private fun intlDayPeriods(tag: String): String = js(
    """(function(){try{
        var f=new Intl.DateTimeFormat(tag,{hour:'numeric',hourCycle:'h12'});
        function period(h){var p=f.formatToParts(new Date(2026,5,9,h,0));for(var i=0;i<p.length;i++){if(p[i].type==='dayPeriod')return p[i].value;}return '';}
        return period(9)+'\u0002'+period(15);
    }catch(e){return 'AM\u0002PM';}})()"""
)

private fun intlList(tag: String, type: String, items: String): String = js(
    """(function(){try{
        if(typeof Intl.ListFormat!=='function')return '';
        return new Intl.ListFormat(tag,{type:type,style:type==='unit'?'short':'long'}).format(items.split('\u0002'));
    }catch(e){return '';}})()"""
)

private fun intlRelative(tag: String, value: Double, unit: String): String = js(
    """(function(){try{
        return new Intl.RelativeTimeFormat(tag,{style:'short',numeric:'always'}).format(value,unit);
    }catch(e){return '';}})()"""
)

private fun intlRelativeNow(tag: String): String = js(
    """(function(){try{
        return new Intl.RelativeTimeFormat(tag,{numeric:'auto'}).format(0,'second');
    }catch(e){return '';}})()"""
)
