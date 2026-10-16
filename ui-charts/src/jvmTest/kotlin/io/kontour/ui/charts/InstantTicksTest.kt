package io.kontour.ui.charts

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * An instant axis is ticked on a clock: from the zone's midnight, at the times
 * its clocks show, and by the calendar past a day.
 */
class InstantTicksTest {

    private fun ms(at: LocalDateTime, zone: TimeZone): Double = at.toInstant(zone).toEpochMilliseconds().toDouble()

    private fun local(ms: Double, zone: TimeZone): LocalDateTime =
        Instant.fromEpochMilliseconds(ms.toLong()).toLocalDateTime(zone)

    @Test
    fun anHourIsTickedEveryFifteenMinutesFromTheHour() {
        val zone = TimeZone.UTC
        val ticks = instantTicks(
            ms(LocalDateTime(2026, 10, 16, 9, 7), zone),
            ms(LocalDateTime(2026, 10, 16, 10, 2), zone),
            5,
            zone,
            DayOfWeek.MONDAY,
        )
        assertEquals(ClockUnit.Minute, ticks.clock)
        val first = local(ticks.values.first(), zone)
        assertEquals(9, first.hour)
        assertEquals(0, first.minute)
        assertEquals(listOf(0, 15, 30, 45, 0), ticks.values.map { local(it, zone).minute })
    }

    /**
     * Sydney's clocks went from two to three on 4 October 2026, so the ticks
     * every three hours that day are at midnight and three, two hours apart,
     * and then three hours apart again: where the clocks said them.
     */
    @Test
    fun theDayTheClocksGoForwardIsTickedOnTheClock() {
        val zone = TimeZone.of("Australia/Sydney")
        val ticks = instantTicks(
            ms(LocalDateTime(2026, 10, 4, 0, 0), zone),
            ms(LocalDateTime(2026, 10, 4, 23, 0), zone),
            9,
            zone,
            DayOfWeek.MONDAY,
        )
        val hours = ticks.values.map { local(it, zone).hour }
        assertEquals(listOf(0, 3, 6, 9, 12, 15, 18, 21), hours.take(8))
        assertEquals(2 * 3_600_000.0, ticks.values[1] - ticks.values[0])
        assertEquals(3 * 3_600_000.0, ticks.values[2] - ticks.values[1])
    }

    @Test
    fun aMonthIsTickedByTheCalendarAtEachDaysStart() {
        val zone = TimeZone.of("Australia/Perth")
        val ticks = instantTicks(
            ms(LocalDateTime(2026, 9, 1, 0, 0), zone),
            ms(LocalDateTime(2026, 10, 1, 0, 0), zone),
            5,
            zone,
            DayOfWeek.MONDAY,
        )
        assertEquals(null, ticks.clock)
        assertTrue(ticks.unit != null)
        ticks.values.forEach { tick ->
            val at = local(tick, zone)
            assertEquals(0, at.hour, "a day's tick is at its midnight in Perth, not in UTC: $at")
        }
    }
}
