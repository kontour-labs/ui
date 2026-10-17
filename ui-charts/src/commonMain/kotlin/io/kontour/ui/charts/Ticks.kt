package io.kontour.ui.charts

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Instant

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
    /** For an instant axis ticked within a day: the clock's unit its ticks are in. */
    val clock: ClockUnit? = null,
)

internal enum class DateUnit { Day, Week, Month, Year }

internal enum class ClockUnit { Second, Minute, Hour }

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
    // Rounded with a little give, so an end that is a tick but for floating
    // point, 1.0000000000000002 for 1, does not reach out a whole step past it.
    val first = floor(lo / step + StepSlack) * step
    val last = ceil(hi / step - StepSlack) * step
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

/** How far past a tick, in steps, an end may sit and still end on it: rounding, not data. */
private const val StepSlack = 1e-9

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

/**
 * Ticks for a span of moments, [low]..[high] in epoch milliseconds, read off
 * the clock of [zone]: every 1, 5, 15 or 30 seconds or minutes, or every 1, 3,
 * 6 or 12 hours, counted from the zone's midnight, whichever gives near
 * [count] of them; and for steps of a day or more, [dateTicks]' at the start of
 * each of those days in [zone].
 *
 * On the clock, not on a stopwatch: a tick every three hours on the day the
 * clocks go forward is at midnight, three and six, two hours apart and then
 * three, because that is when the clocks said three and six. A time the
 * clocks skip is moved to the moment they show it; a time they show twice is
 * its first.
 */
internal fun instantTicks(low: Double, high: Double, count: Int, zone: TimeZone, firstDayOfWeek: DayOfWeek): Ticks {
    var lo = low
    var hi = high
    if (lo.isNaN() || hi.isNaN()) {
        lo = 0.0
        hi = 1000.0
    }
    if (hi - lo < 1000.0) {
        lo -= 1000.0
        hi += 1000.0
    }
    val wanted = (hi - lo) / 1000.0 / max(count - 1, 1)
    val step = ClockSteps.firstOrNull { it.seconds >= wanted }
    if (step == null) {
        val from = Instant.fromEpochMilliseconds(lo.roundToLong()).toLocalDateTime(zone).date
        val to = Instant.fromEpochMilliseconds(hi.roundToLong()).toLocalDateTime(zone).date
        val days = dateTicks(from.toEpochDays().toDouble(), to.toEpochDays().toDouble() + 1, count, firstDayOfWeek)
        val values = days.values.map { day ->
            LocalDate.fromEpochDays(day.roundToLong()).atStartOfDayIn(zone).toEpochMilliseconds().toDouble()
        }.toDoubleArray()
        return Ticks(values, values.first(), values.last(), unit = days.unit)
    }
    val first = Instant.fromEpochMilliseconds(lo.roundToLong()).toLocalDateTime(zone)
    var date = first.date
    var second = (first.hour * 3600 + first.minute * 60 + first.second) / step.seconds * step.seconds
    val ticks = mutableListOf<Double>()
    while (ticks.size < 400) {
        val moment = LocalDateTime(date, LocalTime.fromSecondOfDay(second)).toInstant(zone)
        val at = moment.toEpochMilliseconds().toDouble()
        if (at > hi && ticks.size >= 2) break
        // A skipped hour can move two ticks onto one moment; keep the first.
        if (ticks.isEmpty() || at > ticks.last()) ticks += at
        second += step.seconds
        if (second >= SecondsPerDay) {
            second -= SecondsPerDay
            date = date.plus(1, DateTimeUnit.DAY)
        }
    }
    return Ticks(ticks.toDoubleArray(), ticks.first(), ticks.last(), clock = step.unit)
}

private class ClockStep(val unit: ClockUnit, val seconds: Int)

/** Every step divides a day, so counting from midnight lands on midnight again. */
private val ClockSteps = listOf(
    ClockStep(ClockUnit.Second, 1),
    ClockStep(ClockUnit.Second, 5),
    ClockStep(ClockUnit.Second, 15),
    ClockStep(ClockUnit.Second, 30),
    ClockStep(ClockUnit.Minute, 60),
    ClockStep(ClockUnit.Minute, 300),
    ClockStep(ClockUnit.Minute, 900),
    ClockStep(ClockUnit.Minute, 1800),
    ClockStep(ClockUnit.Hour, 3600),
    ClockStep(ClockUnit.Hour, 3 * 3600),
    ClockStep(ClockUnit.Hour, 6 * 3600),
    ClockStep(ClockUnit.Hour, 12 * 3600),
)

private const val SecondsPerDay = 86_400
