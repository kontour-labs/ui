package io.kontour.ui.components.datetime

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which day each cell of an activity calendar is, where its months go, and its shades. */
class ActivityGridTest {

    // Friday 5 June 2026.
    private val end = LocalDate(2026, 6, 5)

    @Test
    fun theLastColumnHoldsTheEndAndStopsThere() {
        val monday = ActivityGrid(end, weeks = 53, start = null, firstDayOfWeek = DayOfWeek.MONDAY)
        assertEquals(52 to 4, monday.cellOf(end), "a Friday is the fifth row of a week from Monday")
        assertNull(monday.dateAt(52, 5), "Saturday has not happened yet")
        assertEquals(LocalDate(2026, 6, 1), monday.dateAt(52, 0))

        val sunday = ActivityGrid(end, weeks = 53, start = null, firstDayOfWeek = DayOfWeek.SUNDAY)
        assertEquals(52 to 5, sunday.cellOf(end), "and the sixth of a week from Sunday")
        assertEquals(LocalDate(2026, 5, 31), sunday.dateAt(52, 0))
        assertEquals(DayOfWeek.SUNDAY, sunday.firstDay.dayOfWeek)
        assertEquals(1, sunday.rowOf(DayOfWeek.MONDAY), "Monday is the second row from Sunday")
    }

    @Test
    fun everyShownDayRoundTrips() {
        val grid = ActivityGrid(end, weeks = 20, start = null, firstDayOfWeek = DayOfWeek.MONDAY)
        var shown = 0
        for (column in 0 until 20) for (row in 0..6) {
            val date = grid.dateAt(column, row) ?: continue
            shown++
            assertEquals(column to row, grid.cellOf(date))
        }
        assertEquals(19 * 7 + 5, shown, "nineteen whole weeks and the five days of this one")
    }

    @Test
    fun nothingBeforeTheStartIsShown() {
        val grid = ActivityGrid(end, weeks = 10, start = LocalDate(2026, 4, 15), firstDayOfWeek = DayOfWeek.MONDAY)
        assertNull(grid.cellOf(LocalDate(2026, 4, 14)))
        assertTrue(grid.cellOf(LocalDate(2026, 4, 15)) != null)
        assertEquals(LocalDate(2026, 4, 15), grid.firstShown)
    }

    /**
     * Twelve labels for a year, each over the first week of its month — and the
     * month the calendar only shows the tail of loses its label rather than
     * crowding the next one.
     *
     * A year to Friday 26 June 2026 from Monday starts on 23 June 2025: a week
     * of June, then July from the third column. June's label would sit two
     * columns before July's, so it goes.
     */
    @Test
    fun monthLabelsGoOverTheFirstWeekOfEachMonth() {
        val grid = ActivityGrid(LocalDate(2026, 6, 26), weeks = 53, start = null, firstDayOfWeek = DayOfWeek.MONDAY)
        val labels = grid.monthLabels { 3 }
        assertEquals(2 to Month.JULY, labels.first(), "the tail of June gives way to July: $labels")
        assertEquals(49 to Month.JUNE, labels.last(), "June 2026 starts over its first Monday's column")
        assertTrue(labels.zipWithNext().all { (a, b) -> b.first - a.first >= 3 }, "no two labels crowd: $labels")
        assertEquals(12, labels.size, "a year of labels: $labels")
        assertEquals(12, labels.map { it.second }.toSet().size, "each month once")
        // With room for it, the partial month keeps its label.
        assertEquals(0 to Month.JUNE, grid.monthLabels { 2 }.first())
    }

    /** A month that starts in the last column has no room for its name, and goes without. */
    @Test
    fun aMonthBarelyBegunAtTheEndIsNotCutOff() {
        // A year to Friday 5 June 2026 from Monday: June's first Monday is the
        // last column.
        val grid = ActivityGrid(end, weeks = 53, start = null, firstDayOfWeek = DayOfWeek.MONDAY)
        assertEquals(Month.MAY, grid.monthLabels { 2 }.last().second)
        assertEquals(52 to Month.JUNE, grid.monthLabels { 1 }.last(), "unless its name fits the one column")
    }

    @Test
    fun quantilesUseEveryShadeWhateverTheScale() {
        assertContentEquals(intArrayOf(1, 26, 51, 76), quantileBounds((1..100).toList(), 4))
        assertContentEquals(intArrayOf(1, 2, 3, 4), quantileBounds(emptyList(), 4))
        val ones = quantileBounds(List(30) { 1 }, 4)
        assertEquals(1, levelOf(1, ones), "all ones is all level one")
        assertEquals(0, levelOf(0, ones))
        assertEquals(4, levelOf(100, quantileBounds((1..100).toList(), 4)))
        assertEquals(2, levelOf(30, quantileBounds((1..100).toList(), 4)))
    }

    @Test
    fun thresholdsAreWhereEachLevelStarts() {
        val bounds = intArrayOf(1, 5, 10, 20)
        assertEquals(listOf(0, 1, 1, 2, 3, 4), listOf(0, 1, 4, 5, 19, 20).map { levelOf(it, bounds) })
        assertEquals(0, levelOf(2, intArrayOf(3, 6)), "below the first threshold is no level")
    }
}
