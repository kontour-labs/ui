package io.kontour.ui.charts

import io.kontour.ui.locale.LocaleFormats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A streamgraph: areas stacked about the middle of the plot, where only how
 * thick each series is means anything, so its axis has no ticks and is not
 * said, and its values are said as themselves.
 */
class CentredStackTest {

    private val formats = LocaleFormats.english()
    private val strings = ChartStrings()

    private class Riders(val route: String, val month: String, val count: Int)

    private val riders = listOf(
        Riders("950", "Jan", 30), Riders("951", "Jan", 10),
        Riders("950", "Feb", 60), Riders("951", "Feb", 40),
    )

    private fun <X : Any, Y : Any> chart(
        x: ChartAxis<X>,
        y: ChartAxis<Y>,
        content: ChartScope<X, Y>.() -> Unit,
    ): ResolvedChart = resolve(ChartRecorder(x, y).apply(content).build(), x, y, formats)

    private fun stream(items: List<Riders> = riders) = chart(ChartAxis.category<String>(), ChartAxis.number(title = "Riders")) {
        area(items, x = { it.month }, y = { it.count }, series = { ChartSeries(it.route) }, stacking = ChartStacking.Centred)
    }

    @Test
    fun eachPlaceIsStackedAboutTheMiddle() {
        val resolved = stream()
        val (first, second) = resolved.layers
        val firstBase = assertNotNull(first.base)
        val secondBase = assertNotNull(second.base)
        // January's whole is 40, so it runs from -20 to 20; February's 100.
        assertEquals(-20.0, firstBase[0])
        assertEquals(10.0, first.up[0])
        assertEquals(10.0, secondBase[0])
        assertEquals(20.0, second.up[0])
        assertEquals(-50.0, firstBase[1])
        assertEquals(50.0, second.up[1])
        // Values stay the riders each carried.
        assertEquals(30.0, first.values[0])
    }

    @Test
    fun theAxisIsSymmetricWithNothingOnIt() {
        val resolved = stream()
        assertTrue(resolved.y.centred)
        assertEquals(-resolved.y.high, resolved.y.low)
        assertTrue(resolved.y.high >= 50.0)
        assertEquals(0, resolved.y.ticks.size)
        assertFalse(resolved.y.showGridlines)
        assertFalse(resolved.y.showLabels)
    }

    @Test
    fun theAxisIsNotSaidButEachSeriesIs() {
        val summary = chartSummary(stream(), strings, formats, { it.joinToString(". ") }, null)
        assertFalse("Riders, " in summary, "a stream's axis was said: $summary")
        assertTrue("950, 30 to 60" in summary, summary)
        assertEquals("Jan: 950, 30 and 951, 10", placeDescriptions(stream(), strings, formats).first().text)
    }

    @Test
    fun aNegativeValueHasNoThicknessAndIsStillSaid() {
        val resolved = stream(riders + Riders("952", "Jan", -5))
        val third = resolved.layers.last()
        assertEquals(third.base!![0], third.up[0], "a negative value took up room in the stream")
        assertEquals(-5.0, third.values[0])
    }

    @Test
    fun barsTakeCentredAsStacked() {
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(riders, x = { it.month }, y = { it.count }, series = { ChartSeries(it.route) }, stacking = ChartStacking.Centred)
        }
        assertEquals(0.0, resolved.layers[0].base!![0])
        assertEquals(40.0, resolved.layers[1].up[0])
        assertFalse(resolved.y.centred)
    }
}
