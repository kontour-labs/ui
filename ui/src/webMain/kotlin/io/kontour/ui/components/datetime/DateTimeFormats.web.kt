package io.kontour.ui.components.datetime

import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.datetime.DayOfWeek

/**
 * `Intl` knows all three, and it is the browser's own answer rather than a
 * guess from `navigator.language`.
 *
 * Asked in JavaScript rather than through a typed binding because these are
 * three primitive answers and the shapes involved — `formatToParts`, the
 * `resolvedOptions` bag, the `Intl.Locale` week info — have no bindings in
 * `kotlinx.browser` and would be `dynamic`, which the wasm target does not have.
 * Primitives cross that boundary from both js and wasmJs; anything else does
 * not.
 *
 * Every one of them is wrapped, because `Intl` is where browsers differ most:
 * `getWeekInfo` is recent enough that a supported browser and an unsupported one
 * are both in the field today.
 */
internal actual fun platformDateTimeFormats(): DateTimeFormats = DateTimeFormats(
    is24Hour = !localeUsesTwelveHourClock(),
    dayFirst = localeWritesDayFirst(),
    firstDayOfWeek = localeFirstDayOfWeek().toDayOfWeek(),
)

/**
 * `hour12` from the resolved options of a formatter that was asked for an hour.
 *
 * A formatter with no time fields in it does not resolve `hour12` at all, so the
 * `hour` option is not decoration — without it this is `undefined` everywhere
 * and every locale comes out as 24-hour.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun localeUsesTwelveHourClock(): Boolean = js(
    """(function(){try{
        return new Intl.DateTimeFormat(undefined,{hour:'numeric'}).resolvedOptions().hour12===true;
    }catch(e){return false;}})()"""
)

/**
 * The order of the parts of a formatted date, which is the question itself.
 *
 * Reading the *parts* rather than the pattern: the web has no way to ask a
 * locale for its LDML pattern, and formatting a known date and looking at what
 * came out is both simpler and harder to get wrong than parsing a format string
 * that does not exist. Any date does; this one is unambiguous in every order.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun localeWritesDayFirst(): Boolean = js(
    """(function(){try{
        var parts=new Intl.DateTimeFormat(undefined).formatToParts(new Date(2026,5,9));
        var day=-1,month=-1;
        for(var i=0;i<parts.length;i++){
            if(parts[i].type==='day'&&day<0)day=i;
            if(parts[i].type==='month'&&month<0)month=i;
        }
        if(day<0||month<0)return true;
        return day<month;
    }catch(e){return true;}})()"""
)

/** 1 for Monday through 7 for Sunday, or 0 when the browser will not say. */
@OptIn(ExperimentalWasmJsInterop::class)
private fun localeFirstDayOfWeek(): Int = js(
    """(function(){try{
        var tag=new Intl.DateTimeFormat().resolvedOptions().locale;
        var loc=new Intl.Locale(tag);
        var info=typeof loc.getWeekInfo==='function'?loc.getWeekInfo():loc.weekInfo;
        return (info&&info.firstDay)?info.firstDay:0;
    }catch(e){return 0;}})()"""
)

/**
 * `Intl`'s week is already Monday-first and one-based, which is `DayOfWeek`'s
 * own numbering — so this is a bounds check rather than a conversion, and a
 * browser that would not answer falls back to Monday.
 */
private fun Int.toDayOfWeek(): DayOfWeek =
    if (this in 1..7) DayOfWeek.entries[this - 1] else DayOfWeek.MONDAY
