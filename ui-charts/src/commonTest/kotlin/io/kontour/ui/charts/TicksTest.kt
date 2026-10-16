package io.kontour.ui.charts

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where an axis's ticks fall: on round numbers, and on calendar boundaries.
 *
 * A reader reads a value off a chart by counting in steps from the nearest
 * label, which only works when the steps are ones they can count in.
 */
class TicksTest {

    @Test
    fun aRangeOfNumbersIsTickedInRoundSteps() {
        // 94 over four gaps is 23.5 a step, and the round number nearest that
        // is 25: five ticks, as many as were asked for.
        val ticks = niceTicks(3.0, 97.0, 5)
        assertEquals(listOf(0.0, 25.0, 50.0, 75.0, 100.0), ticks.values.toList())
        assertEquals(0.0, ticks.low)
        assertEquals(100.0, ticks.high)
        assertEquals(0, ticks.fractionDigits)
    }

    @Test
    fun everyStepIsOneTwoTwoAndAHalfOrFiveOfAPowerOfTen() {
        listOf(0.0 to 1.0, 0.0 to 7.0, 13.0 to 17.5, -40.0 to 260.0, 0.001 to 0.0042, 1e6 to 9.2e6).forEach { (low, high) ->
            val ticks = niceTicks(low, high, 5)
            val step = ticks.values[1] - ticks.values[0]
            val exponent = floor(log10(step))
            val mantissa = step / 10.0.pow(exponent)
            assertTrue(listOf(1.0, 2.0, 2.5, 5.0, 10.0).any { abs(it - mantissa) < 1e-6 }, "a step of $step over $low..$high")
            assertTrue(ticks.low <= low && ticks.high >= high, "the ticks over $low..$high do not cover it")
        }
    }

    @Test
    fun aStepOfAQuarterIsWrittenToTwoPlaces() {
        assertEquals(2, niceTicks(0.0, 1.0, 5).fractionDigits)
        assertEquals(1, niceTicks(0.0, 0.8, 5).fractionDigits)
    }

    @Test
    fun zeroIsZeroAndNotATinyNegative() {
        val ticks = niceTicks(-0.3, 0.3, 7)
        assertTrue(ticks.values.any { it == 0.0 && 1.0 / it > 0 }, "zero is written as -0 or a rounding error: ${ticks.values.toList()}")
    }

    @Test
    fun oneValueSitsMidAxis() {
        val ticks = niceTicks(40.0, 40.0, 5)
        assertTrue(ticks.low < 40.0 && ticks.high > 40.0)
    }

    @Test
    fun aMonthOfDaysIsTickedInWeeksFromTheReadersFirstDay() {
        val start = LocalDate(2026, 3, 1).toEpochDays().toDouble()
        val end = LocalDate(2026, 3, 31).toEpochDays().toDouble()
        val ticks = dateTicks(start, end, 5, DayOfWeek.MONDAY)
        assertEquals(DateUnit.Week, ticks.unit)
        ticks.values.forEach { assertEquals(DayOfWeek.MONDAY, dateOf(it).dayOfWeek) }
        val sunday = dateTicks(start, end, 5, DayOfWeek.SUNDAY)
        sunday.values.forEach { assertEquals(DayOfWeek.SUNDAY, dateOf(it).dayOfWeek) }
    }

    @Test
    fun aYearIsTickedOnTheFirstOfTheMonth() {
        val ticks = dateTicks(
            LocalDate(2026, 1, 9).toEpochDays().toDouble(),
            LocalDate(2026, 12, 20).toEpochDays().toDouble(),
            5,
            DayOfWeek.MONDAY,
        )
        assertEquals(DateUnit.Month, ticks.unit)
        ticks.values.forEach { assertEquals(1, dateOf(it).day, "a month tick on ${dateOf(it)}") }
    }

    @Test
    fun decadesAreTickedOnTheFirstOfJanuaryOfRoundYears() {
        val ticks = dateTicks(
            LocalDate(1983, 6, 1).toEpochDays().toDouble(),
            LocalDate(2026, 6, 1).toEpochDays().toDouble(),
            5,
            DayOfWeek.MONDAY,
        )
        assertEquals(DateUnit.Year, ticks.unit)
        ticks.values.forEach {
            val date = dateOf(it)
            assertEquals(1, date.day)
            assertEquals(1, date.month.ordinal + 1)
            assertEquals(0, date.year % 10, "a year tick on ${date.year}")
        }
    }

    @Test
    fun placesBeyondTheLimitAreSaidInRuns() {
        val places = DoubleArray(365) { it.toDouble() }
        val groups = groupPlaces(places, ChartDefaults.MaxAccessiblePlaces)
        assertTrue(groups.size <= ChartDefaults.MaxAccessiblePlaces)
        assertEquals(365, groups.sumOf { it.size })
        assertEquals(places.toList(), groups.flatMap { it.toList() })
    }

    @Test
    fun aLineOfTenThousandPointsKeepsItsPeaks() {
        val points = (0 until 10_000).map { i ->
            val y = if (i == 4_321) -500f else (i % 7).toFloat()
            Offset(i * 0.03f, y)
        }
        val kept = decimate(points, 300f)
        assertTrue(kept.size <= 300 * 4 + 8, "${kept.size} points for 300 pixels")
        assertTrue(kept.any { it != null && it.y == -500f }, "the one spike was decimated away")
    }

    private fun dateOf(epochDay: Double): LocalDate = LocalDate.fromEpochDays(epochDay.roundToLong())
}
