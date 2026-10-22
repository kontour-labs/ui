package io.kontour.ui.charts

import io.kontour.ui.locale.LocaleFormats
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a chart says beside its data: a band's range, a region's span, a rule's
 * place, and the pins that keep a run of events' labels apart.
 */
class ChartReferencesTest {

    private val formats = LocaleFormats.english()
    private val strings = ChartStrings()

    private class Week(val week: Int, val low: Int, val high: Int)

    private val ahead = listOf(Week(10, 690, 690), Week(20, 700, 730), Week(30, 710, 770))

    private fun <X : Any, Y : Any> chart(x: ChartAxis<X>, y: ChartAxis<Y>, content: ChartScope<X, Y>.() -> Unit): ResolvedChart =
        resolve(ChartRecorder(x, y).apply(content).build(), x, y, formats)

    private fun summary(chart: ResolvedChart) = chartSummary(chart, strings, formats, { it.joinToString(". ") }, null)

    @Test
    fun aBandIsAnAreaFromItsFootToItsTop() {
        val resolved = chart(ChartAxis.number(), ChartAxis.number()) {
            band(ahead, x = { it.week }, yStart = { it.high }, yEnd = { it.low })
        }
        val band = resolved.layers.single()
        // Given high first, it is the same band.
        assertContentEquals(doubleArrayOf(690.0, 700.0, 710.0), band.base)
        assertContentEquals(doubleArrayOf(690.0, 730.0, 770.0), band.up)
        assertTrue(resolved.y.high >= 770.0, "the axis takes in the top of the band: ${resolved.y.high}")
        assertTrue(resolved.y.low > 0.0, "a band is not measured from zero, as a bar is: ${resolved.y.low}")
    }

    @Test
    fun aBandIsSaidAndTabledAsItsRange() {
        val content: ChartScope<Number, Number>.() -> Unit = {
            band(ahead, x = { it.week }, yStart = { it.low }, yEnd = { it.high }, series = { ChartSeries("Likely") })
        }
        val resolved = chart(ChartAxis.number(), ChartAxis.number(), content)
        val places = placeDescriptions(resolved, strings, formats)
        assertEquals("20: Likely, 700 to 730", places[1].text)
        val table = chartTable(resolved, strings, formats)
        assertEquals(listOf("710 to 770"), table.rows[2].cells)
    }

    @Test
    fun aRegionWidensTheAxisAndIsSaidWithItsSpan() {
        val resolved = chart(ChartAxis.number(), ChartAxis.number()) {
            line(ahead, x = { it.week }, y = { it.low })
            region(xStart = 13, xEnd = 40, label = "Forecast")
            rule(y = 700, label = "Target")
            rule(x = 12, label = "Launch")
            region(xStart = 12, xEnd = 13)
        }
        assertTrue(resolved.x.high >= 40.0, "the axis runs on to the region's end: ${resolved.x.high}")
        val said = summary(resolved)
        assertTrue("Forecast, 13 to 40" in said, said)
        assertTrue("Target, 700" in said, said)
        assertTrue("Launch, 12" in said, said)
    }

    /**
     * Two events close together: the second's pin would touch the first's, so
     * it goes up a row; the third is clear of both and back on the plot.
     */
    @Test
    fun pinsThatWouldTouchStandOneOnAnother() {
        val rows = stackPins(listOf(0f to 40f, null, 30f to 70f, 100f to 140f, 35f to 60f), pad = 4f)
        assertContentEquals(intArrayOf(0, -1, 1, 0, 2), rows)
    }

    @Test
    fun aPinIsKeptInsideTheChart() {
        assertEquals(0f, pinStart(centre = 5f, pinWidth = 40f, plotLeft = 0f, width = 300f))
        assertEquals(260f, pinStart(centre = 295f, pinWidth = 40f, plotLeft = 0f, width = 300f))
        assertEquals(30f, pinStart(centre = 50f, pinWidth = 40f, plotLeft = 0f, width = 300f))
    }
}
