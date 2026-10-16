package io.kontour.ui.charts

import io.kontour.ui.locale.LocaleFormats
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A chart's numbers as a table: a row for each place, a column for each series,
 * every value written as the chart writes it.
 */
class ChartDataTableTest {

    private val formats = LocaleFormats.english()
    private val strings = ChartStrings()

    private class Trip(val day: String, val route: String, val riders: Int)

    private val trips = listOf(
        Trip("Mon", "950", 1200), Trip("Mon", "951", 300),
        Trip("Tue", "950", 1450),
    )

    private fun <X : Any, Y : Any> table(x: ChartAxis<X>, y: ChartAxis<Y>, content: ChartScope<X, Y>.() -> Unit): ChartTable =
        chartTable(resolve(ChartRecorder(x, y).apply(content).build(), x, y, formats), strings, formats)

    @Test
    fun aRowForEachPlaceAndAColumnForEachSeries() {
        val table = table(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(trips, x = { it.day }, y = { it.riders }, series = { ChartSeries("Route ${it.route}") })
        }
        assertEquals("Category", table.placeTitle)
        assertEquals(listOf("Route 950", "Route 951"), table.columns)
        assertEquals(listOf("Mon", "Tue"), table.rows.map { it.label })
        assertEquals(listOf("1,200", "300"), table.rows[0].cells)
        // Nothing for 951 on Tuesday is an empty cell, not a zero.
        assertEquals(listOf("1,450", ""), table.rows[1].cells)
    }

    @Test
    fun anUnnamedSeriesOnDatesTakesTheAxesWords() {
        val days = listOf(LocalDate(2026, 10, 12) to 4, LocalDate(2026, 10, 13) to 7)
        val table = table(ChartAxis.date(), ChartAxis.number(title = "Riders")) {
            line(days, x = { it.first }, y = { it.second })
        }
        assertEquals("Date", table.placeTitle)
        assertEquals(listOf("Riders"), table.columns)
        assertEquals(listOf("4"), table.rows[0].cells)
        assertEquals(formats.date(LocalDate(2026, 10, 13), io.kontour.ui.locale.DateStyle.Medium), table.rows[1].label)
    }

    @Test
    fun aNormalisedStackIsItsShares() {
        val table = table(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(trips.take(2), x = { it.day }, y = { it.riders }, series = { ChartSeries(it.route) }, stacking = ChartStacking.Normalised)
        }
        assertEquals(listOf("80%", "20%"), table.rows[0].cells)
    }
}
