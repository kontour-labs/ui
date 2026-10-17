package io.kontour.ui.charts

import io.kontour.ui.locale.LocaleFormats
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Marks that run from one value to another rather than from zero: floating
 * bars, stems and boxes. Each is drawn from its foot, said as its range, and
 * written in the table as one; a stem that only joins points already said is
 * not said again.
 */
class RangeMarksTest {

    private val formats = LocaleFormats.english()
    private val strings = ChartStrings()

    private class Day(val day: String, val low: Int, val high: Int)

    private val days = listOf(Day("Mon", 12, 25), Day("Tue", 30, 18), Day("Wed", 40, 55))

    private fun <X : Any, Y : Any> chart(
        x: ChartAxis<X>,
        y: ChartAxis<Y>,
        content: ChartScope<X, Y>.() -> Unit,
    ): ResolvedChart = resolve(ChartRecorder(x, y).apply(content).build(), x, y, formats)

    @Test
    fun aFloatingBarRunsFromItsStartToItsEndAndZeroIsNotForced() {
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(days, x = { it.day }, yStart = { it.low }, yEnd = { it.high })
        }
        val layer = resolved.layers.single()
        assertTrue(layer.mark.floating)
        assertContentEquals(doubleArrayOf(12.0, 18.0, 40.0), layer.base)
        // Ends the wrong way round are the same bar.
        assertContentEquals(doubleArrayOf(25.0, 30.0, 55.0), layer.up)
        assertEquals(25.0, layer.endAt(0))
        assertTrue(resolved.y.low > 0, "a range's axis was forced to zero: ${resolved.y.low}")
    }

    @Test
    fun aFloatingBarIsSaidAndTabledAsItsRange() {
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(days, x = { it.day }, yStart = { it.low }, yEnd = { it.high })
        }
        assertEquals("Mon: 12 to 25", placeDescriptions(resolved, strings, formats).first().text)
        assertEquals(listOf("12 to 25"), chartTable(resolved, strings, formats).rows.first().cells)
    }

    @Test
    fun floatingBarsOfSeveralSeriesStandSideBySide() {
        val routes = days.map { "950" to it } + days.map { "951" to it }
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(routes, x = { it.second.day }, yStart = { it.second.low }, yEnd = { it.second.high }, series = { ChartSeries(it.first) })
        }
        assertEquals(listOf(0, 1), resolved.layers.map { it.groupIndex })
        assertEquals(listOf(2, 2), resolved.layers.map { it.groupCount })
    }

    @Test
    fun aDumbbellsStemIsNotSaidTwice() {
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            stem(days, x = { it.day }, yStart = { it.low }, yEnd = { it.high })
            point(days, x = { it.day }, y = { minOf(it.low, it.high) }, series = { ChartSeries("Low") })
            point(days, x = { it.day }, y = { maxOf(it.low, it.high) }, series = { ChartSeries("High") })
        }
        assertEquals("Mon: Low, 12 and High, 25", placeDescriptions(resolved, strings, formats).first().text)
        assertEquals(listOf("Low", "High"), chartTable(resolved, strings, formats).columns)
    }

    @Test
    fun anErrorBarOverABarIsSaid() {
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(days, x = { it.day }, y = { (it.low + it.high) / 2 }, series = { ChartSeries("Mean") })
            stem(days, x = { it.day }, yStart = { it.low }, yEnd = { it.high }, series = { ChartSeries("Spread") })
        }
        val stem = resolved.layers.first { it.mark.kind == MarkKind.Stem }
        assertFalse(stemSilentAt(resolved, stem, 0))
        assertEquals(listOf("Mean", "Spread"), chartTable(resolved, strings, formats).columns)
    }

    @Test
    fun aBoxIsDrawnFromItsQuartilesAndSaidByItsNumbers() {
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            box(
                listOf("950"),
                x = { it },
                low = { 10 }, lowerQuartile = { 15 }, median = { 18 }, upperQuartile = { 21 }, high = { 30 },
                outliers = { listOf(3, 41) },
            )
        }
        val layer = resolved.layers.single()
        assertContentEquals(doubleArrayOf(15.0), layer.base)
        assertContentEquals(doubleArrayOf(21.0), layer.up)
        assertContentEquals(doubleArrayOf(18.0), layer.values)
        assertNull(layer.endAt(0), "a box is read by its median, not as a range")
        // The axis reaches the outliers.
        assertTrue(resolved.y.low <= 3.0 && resolved.y.high >= 41.0, "the axis left an outlier off: ${resolved.y.low} to ${resolved.y.high}")
        assertEquals(
            "950: median 18, quartiles 15 to 21, range 10 to 30, outliers 3 and 41",
            placeDescriptions(resolved, strings, formats).single().text,
        )
        val table = chartTable(resolved, strings, formats)
        assertEquals(listOf("Low", "Lower quartile", "Median", "Upper quartile", "High", "Outliers"), table.columns)
        assertEquals(listOf("10", "15", "18", "21", "30", "3, 41"), table.rows.single().cells)
    }

    @Test
    fun boxesOfNamedSeriesStandSideBySideAndNameTheirColumns() {
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            box(
                listOf("Mon" to "950", "Mon" to "951"),
                x = { it.first },
                samples = { if (it.second == "950") listOf(1, 2, 3, 4, 5) else listOf(4, 5, 6, 7, 8) },
                series = { ChartSeries(it.second) },
            )
        }
        assertEquals(listOf(0, 1), resolved.layers.map { it.groupIndex })
        assertEquals(listOf(3.0, 6.0), resolved.layers.map { it.values.single() })
        assertEquals("950, Median", chartTable(resolved, strings, formats).columns[2])
    }

    @Test
    fun aBoxThatMovesIsANewChart() {
        fun withHigh(high: Int) = chart(ChartAxis.category<String>(), ChartAxis.number(domain = 0.0..100.0)) {
            box(listOf("950"), x = { it }, low = { 10 }, lowerQuartile = { 15 }, median = { 18 }, upperQuartile = { 21 }, high = { high })
        }
        assertTrue(withHigh(30).sameAs(withHigh(30)))
        assertFalse(withHigh(30).sameAs(withHigh(35)), "a whisker that grew was not animated to")
    }
}
