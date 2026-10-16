package io.kontour.ui.charts

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * An axis's ticks: where each one goes, in the chart's own units, and the span
 * they cover, which is the axis's domain.
 *
 * @property fractionDigits How many digits after the point a tick of a number
 *   axis needs, which is also how precisely a value on it is written.
 * @property unit For a date axis, the calendar unit the ticks are in.
 */
internal class Ticks(
    val values: DoubleArray,
    val low: Double,
    val high: Double,
    val fractionDigits: Int = 0,
    val unit: DateUnit? = null,
)

internal enum class DateUnit { Day, Week, Month, Year }

/**
 * Round ticks over [low]..[high]: a step of 1, 2, 2.5 or 5 times a power of ten,
 * as near [count] ticks as that allows, with the domain widened out to the
 * first and last of them.
 *
 * Heckbert's "nice numbers", from *Graphics Gems* (1990), which is what most
 * charting libraries still use, because a reader reads a value off a chart by
 * counting in round steps from the nearest labelled one.
 */
internal fun niceTicks(low: Double, high: Double, count: Int): Ticks {
    var lo = low
    var hi = high
    if (lo.isNaN() || hi.isNaN() || lo.isInfinite() || hi.isInfinite()) {
        lo = 0.0
        hi = 1.0
    }
    if (lo > hi) lo = hi.also { hi = lo }
    if (hi - lo < 1e-12) {
        // One value, or none: a span either side of it, so it sits mid-axis.
        val pad = if (abs(lo) < 1e-12) 1.0 else abs(lo) * 0.5
        lo -= pad
        hi += pad
    }
    val target = max(count, 2)
    val step = niceNumber((hi - lo) / (target - 1), round = true)
    val first = floor(lo / step) * step
    val last = ceil(hi / step) * step
    val n = ((last - first) / step).roundToInt() + 1
    val values = DoubleArray(n) { index ->
        val value = first + index * step
        // `-0.0` and `1e-17` are both zero to a reader.
        if (abs(value) < step * 1e-9) 0.0 else value
    }
    val digits = max(0, -floor(log10(step) + 1e-9).toInt())
        .let { if (abs(step * 10.0.pow(it) - (step * 10.0.pow(it)).roundToLong()) > 1e-6) it + 1 else it }
    return Ticks(values, first, last, fractionDigits = digits)
}

/** The round number nearest [value]: 1, 2, 2.5, 5 or 10, times a power of ten. */
internal fun niceNumber(value: Double, round: Boolean): Double {
    if (value <= 0.0) return 1.0
    val exponent = floor(log10(value))
    val fraction = value / 10.0.pow(exponent)
    val nice = if (round) {
        when {
            fraction < 1.5 -> 1.0
            fraction < 2.25 -> 2.0
            fraction < 3.5 -> 2.5
            fraction < 7.5 -> 5.0
            else -> 10.0
        }
    } else {
        when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 2.5 -> 2.5
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
    }
    return nice * 10.0.pow(exponent)
}

/**
 * Ticks for a span of dates, [low]..[high] in epoch days, in the one calendar
 * unit that gives near [count] of them: every day or second day, every week
 * from [firstDayOfWeek], every one, two, three or six months from the first of
 * the month, or every one, two, five, ten or more years from the first of
 * January.
 *
 * A date axis with numeric ticks — every 37 days — is a date axis nobody can
 * read, which is why this is not [niceTicks] over epoch days.
 */
internal fun dateTicks(low: Double, high: Double, count: Int, firstDayOfWeek: DayOfWeek): Ticks {
    var lo = floor(low)
    var hi = ceil(high)
    if (lo.isNaN() || hi.isNaN()) {
        lo = 0.0
        hi = 1.0
    }
    if (hi - lo < 1.0) {
        lo -= 1.0
        hi += 1.0
    }
    val span = hi - lo
    val wanted = span / max(count - 1, 1)
    val (unit, step) = DateSteps.firstOrNull { it.days >= wanted }?.let { it.unit to it.step }
        ?: (DateUnit.Year to yearStep(wanted / 365.25))

    val start = LocalDate.fromEpochDays(lo.roundToLong())
    val end = hi.roundToLong()
    val ticks = mutableListOf<Double>()
    var date = when (unit) {
        DateUnit.Day -> start
        DateUnit.Week -> {
            val back = (start.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7
            start.plus(-back, DateTimeUnit.DAY)
        }
        DateUnit.Month -> {
            val first = LocalDate(start.year, start.month.number, 1)
            val index = first.year * 12 + first.month.number - 1
            first.plus(-(index % step), DateTimeUnit.MONTH)
        }
        DateUnit.Year -> LocalDate(start.year - (start.year % step + step) % step, 1, 1)
    }
    while (date.toEpochDays() <= end || ticks.size < 2) {
        ticks += date.toEpochDays().toDouble()
        date = when (unit) {
            DateUnit.Day -> date.plus(step, DateTimeUnit.DAY)
            DateUnit.Week -> date.plus(step * 7, DateTimeUnit.DAY)
            DateUnit.Month -> date.plus(step, DateTimeUnit.MONTH)
            DateUnit.Year -> date.plus(step, DateTimeUnit.YEAR)
        }
        if (ticks.size > 400) break
    }
    return Ticks(ticks.toDoubleArray(), ticks.first(), ticks.last(), unit = unit)
}

private class DateStep(val unit: DateUnit, val step: Int, val days: Double)

private val DateSteps = listOf(
    DateStep(DateUnit.Day, 1, 1.0),
    DateStep(DateUnit.Day, 2, 2.0),
    DateStep(DateUnit.Week, 1, 7.0),
    DateStep(DateUnit.Week, 2, 14.0),
    DateStep(DateUnit.Month, 1, 30.44),
    DateStep(DateUnit.Month, 2, 60.88),
    DateStep(DateUnit.Month, 3, 91.31),
    DateStep(DateUnit.Month, 6, 182.62),
    DateStep(DateUnit.Year, 1, 365.25),
)

/** Every this many years, for a span of [years] between ticks: 2, 5, 10, 20, 25, 50 … */
private fun yearStep(years: Double): Int {
    val nice = niceNumber(years, round = false)
    return max(2, nice.roundToInt())
}
