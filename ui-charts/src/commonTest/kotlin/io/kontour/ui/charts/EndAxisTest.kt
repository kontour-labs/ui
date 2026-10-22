package io.kontour.ui.charts

import io.kontour.ui.locale.LocaleFormats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A second axis up, down the end edge, for a series in other units: riders as
 * bars against the start axis, and the fares they paid as a line against the
 * end axis.
 *
 * Asked for as *"different labelled axis on the start and the end of the
 * chart ... useful for things with 2 series"*.
 */
class EndAxisTest {

    private val formats = LocaleFormats.english()
    private val strings = ChartStrings()

    private class Day(val day: Int, val riders: Int, val fares: Double)

    private val week = listOf(Day(1, 640, 2.8), Day(2, 720, 3.1), Day(3, 1_120, 4.6), Day(4, 810, 3.3))

    private val riders = ChartSeries("Riders")
    private val fares = ChartSeries("Fares")
    private val x = ChartAxis.number()
    private val y = ChartAxis.number(title = "Riders")
    private val end = ChartAxis.number(title = "Fares", label = { "$" + formats.decimal(it, 2) })

    private fun model(withRule: Boolean = false) = ChartRecorder(x, y, end).apply {
        bar(week, x = { it.day }, y = { it.riders }, series = { riders })
        onEndAxis {
            line(week, x = { it.day }, y = { it.fares }, series = { fares })
            if (withRule) rule(y = 4.0, label = "Peak fare")
        }
    }.build()

    private fun resolved(model: ChartModel = model()) = resolve(model, x, y, formats, end)

    @Test
    fun eachAxisIsMeasuredByItsOwnSeries() {
        val chart = resolved()
        val yEnd = assertNotNull(chart.yEnd, "a chart with content on its end axis should have one")
        // The fares, at a few dollars, do not pull the riders' axis down to
        // them, and the riders do not stretch the fares' axis to a thousand.
        assertTrue(chart.y.high >= 1_120.0 && chart.y.high < 2_000.0, "the start axis should be the riders': ${chart.y.low} to ${chart.y.high}")
        assertTrue(yEnd.high >= 4.6 && yEnd.high < 20.0, "the end axis should be the fares': ${yEnd.low} to ${yEnd.high}")
        assertEquals(chart.y.ticks.size, yEnd.ticks.size, "the end axis's labels should sit on the start axis's gridlines, one each")
        assertTrue(chart.layers.single { it.series == fares }.mark.onEnd)
        assertTrue(!chart.layers.single { it.series == riders }.mark.onEnd)
    }

    @Test
    fun hidingWhatIsOnTheEndAxisTakesTheAxisAway() {
        val chart = resolved(model().without(setOf(fares)))
        assertNull(chart.yEnd, "with nothing on it, the end axis should go, and the plot widen into its gutter")
    }

    @Test
    fun aRuleOnTheEndAxisIsReadOnIt() {
        val chart = resolved(model(withRule = true))
        val rule = chart.rules.single()
        assertTrue(rule.onEnd)
        // At $4.00, which is on the fares' axis and nowhere near the riders'.
        val summary = chartSummary(chart, strings, formats, { it.joinToString(". ") }, null)
        assertTrue("Peak fare, \$4.00" in summary, "the rule should be said in the end axis's units: $summary")
    }

    @Test
    fun theEndSeriesIsSaidInItsOwnUnits() {
        val summary = chartSummary(resolved(), strings, formats, { it.joinToString(". ") }, null)
        assertTrue("Fares, \$2.80 to \$4.60" in summary, "the fares should be said as the end axis writes them: $summary")
        assertTrue("Riders, 640 to 1,120" in summary, "the riders as the start axis does: $summary")
    }

    /** Without an end axis, `onEndAxis` is the start axis's, and nothing is lost. */
    @Test
    fun withNoEndAxisTheContentIsTheStartAxiss() {
        val model = ChartRecorder(x, y).apply {
            onEndAxis { line(week, x = { it.day }, y = { it.fares }, series = { fares }) }
        }.build()
        val chart = resolve(model, x, y, formats)
        assertNull(chart.yEnd)
        assertEquals(1, chart.layers.size)
        assertTrue(!chart.layers.single().mark.onEnd)
    }

    @Test
    fun alignedTicksAreRoundAndReachTheData() {
        val five = alignedTicks(0.0, 4.6, 5)
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0), five.values.toList())
        val four = alignedTicks(0.0, 1_234.0, 4)
        assertEquals(5, four.values.size)
        assertTrue(four.high >= 1_234.0, "the ticks should reach the data: ${four.values.toList()}")
        assertEquals(0.0, four.low)
    }
}
