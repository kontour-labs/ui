package io.kontour.ui.components.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * Wall-clock arithmetic for the event calendar, in whole minutes.
 *
 * The calendar draws wall time — a day is 24 rows whatever the clocks did that
 * night — so every position, step and snap is minutes since the epoch's first
 * day on that wall clock, and back. kotlinx-datetime has no `LocalDateTime`
 * plus a duration, deliberately, since without a zone the answer is wall time;
 * that is exactly the answer wanted here.
 */
internal const val MinutesPerDay: Int = 24 * 60

/** Minutes since midnight. */
internal val LocalTime.minuteOfDay: Int get() = hour * 60 + minute

/** Minutes since 1970-01-01T00:00 on the same wall clock. Long: centuries of minutes overflow an Int. */
internal val LocalDateTime.epochMinute: Long
    get() = date.toEpochDays() * MinutesPerDay + time.minuteOfDay

/** The wall-clock moment [minutes] since 1970-01-01T00:00. */
internal fun localDateTimeOfEpochMinute(minutes: Long): LocalDateTime {
    val day = minutes.floorDiv(MinutesPerDay.toLong())
    val minute = (minutes - day * MinutesPerDay).toInt()
    return LocalDateTime(LocalDate.fromEpochDays(day), LocalTime(minute / 60, minute % 60))
}

/** [this] moved by [minutes], either way, across days. Seconds are dropped. */
internal fun LocalDateTime.plusMinutes(minutes: Long): LocalDateTime =
    localDateTimeOfEpochMinute(epochMinute + minutes)

/** The start of [this] day. */
internal val LocalDate.atMidnight: LocalDateTime get() = LocalDateTime(this, LocalTime(0, 0))

/** [minutes] down to the [step] at or before it. */
internal fun floorTo(minutes: Long, step: Int): Long = minutes.floorDiv(step.toLong()) * step

/** [minutes] to the nearest [step], halves up. */
internal fun roundTo(minutes: Long, step: Int): Long = floorTo(minutes + step / 2, step)

/**
 * Where an event's end can be dragged to, given [proposed]: within the day its
 * last minute is on, from a [step] after its start — or after that day's
 * midnight, for one that began the day before — to the day's own midnight.
 * A resize changes how long an event is on its day; carrying it onto the next
 * is a move.
 */
internal fun resizedEnd(start: Long, end: Long, proposed: Long, step: Int): Long {
    val day = floorTo(if (end > start) end - 1 else start, MinutesPerDay)
    val lower = maxOf(start, day) + step
    val upper = maxOf(day + MinutesPerDay, lower)
    return proposed.coerceIn(lower, upper)
}

/** Where its start can be dragged to: within the day it starts on, a [step] before its end at the latest. */
internal fun resizedStart(start: Long, end: Long, proposed: Long, step: Int): Long {
    val day = floorTo(start, MinutesPerDay)
    val upper = minOf(end, day + MinutesPerDay) - step
    val lower = minOf(day, upper)
    return proposed.coerceIn(lower, upper)
}

/**
 * A bar's end moved [by] minutes — a whole day, from the keyboard or a screen
 * reader — and no shorter than a [step] after its start.
 */
internal fun barEnd(start: Long, end: Long, by: Long, step: Int): Long = maxOf(end + by, start + step)

/**
 * Where a carried event can start, given [proposed], on the day whose midnight
 * is [day]: one that fits in a day keeps all of it on that day, from its
 * midnight to the next; one that already runs past midnight, or is longer
 * than a day, keeps its start on it. Carried to the foot of the hours, an
 * event stopped at nothing and spilled into the next day.
 */
internal fun movedStart(proposed: Long, length: Long, day: Long, oneDay: Boolean, step: Int): Long {
    // At least a step from the next midnight: one with no length, at the foot
    // of the day, went on to the next day's first minute.
    val latest = if (oneDay && length <= MinutesPerDay) day + MinutesPerDay - maxOf(length, step.toLong()) else day + MinutesPerDay - step
    return proposed.coerceIn(day, maxOf(day, latest))
}

/**
 * The day an event from [start] to [end] has its last minute on: one ending at
 * midnight ends the day before, and one with no length ends where it starts.
 * Every view of a calendar asks it, and asked it in a line of its own in each.
 */
internal fun lastDayOf(start: LocalDateTime, end: LocalDateTime): LocalDate =
    if (end > start) end.plusMinutes(-1).date else start.date

/** Whether an event from [start] to [end] is all on one day. */
internal fun onOneDay(start: LocalDateTime, end: LocalDateTime): Boolean = lastDayOf(start, end) == start.date

