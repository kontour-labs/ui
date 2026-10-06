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

