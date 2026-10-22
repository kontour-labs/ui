package io.kontour.ui.charts

import io.kontour.ui.locale.LocaleFormats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A normalised chart's value axis is shares of a whole, whatever else is on it.
 *
 * Reported from the gallery: normalised bars vanished, because the chart's
 * "Capacity" rule at 700 riders stretched a share axis to 70,000%, and lying
 * bars of one series were all the same length, because one series is the
 * whole of every place it stands at.
 */
class NormalisedStackTest {

    private val formats = LocaleFormats.english()

    private class Riders(val route: String, val month: String, val count: Int)

    private val august = listOf(
        Riders("950", "Aug", 40), Riders("951", "Aug", 30), Riders("952", "Aug", 20), Riders("953", "Aug", 10),
    )

    private val months = listOf(
        Riders("950", "Jul", 300), Riders("951", "Jul", 100),
        Riders("950", "Aug", 200), Riders("951", "Aug", 200),
    )

    private fun <X : Any, Y : Any> chart(
        x: ChartAxis<X>,
        y: ChartAxis<Y>,
        content: ChartScope<X, Y>.() -> Unit,
    ): ResolvedChart = resolve(ChartRecorder(x, y).apply(content).build(), x, y, formats)

    @Test
    fun aRuleInTheDataSUnitsDoesNotStretchTheShares() {
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(months, x = { it.month }, y = { it.count }, series = { ChartSeries(it.route) }, stacking = ChartStacking.Normalised)
            rule(y = 700, label = "Capacity")
            rule(y = 0.5, label = "Half")
            rule(x = "Aug", label = "Launch")
            region(yStart = 600, yEnd = 800, label = "Busy")
        }
        assertEquals(0.0, resolved.y.low)
        assertEquals(1.0, resolved.y.high, "the axis ran to ${resolved.y.high}, past every share")
        assertEquals(listOf("Half", "Launch"), resolved.rules.map { it.label }, "a rule off the end of the axis was kept")
        assertTrue(resolved.regions.isEmpty(), "a region wholly off the axis was kept")
    }

    @Test
    fun sharesThatAddToAHairOverOneTopTheAxisAtOneHundredPercent() {
        val tenths = listOf(Riders("950", "Aug", 1), Riders("951", "Aug", 2), Riders("952", "Aug", 7))
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(tenths, x = { it.month }, y = { it.count }, series = { ChartSeries(it.route) }, stacking = ChartStacking.Normalised)
        }
        assertEquals(1.0, resolved.layers.last().up[0], "0.1 + 0.2 + 0.7 was left a hair over one")
        assertEquals(1.0, resolved.y.high, "the axis took a step past 100%")
        assertEquals("100%", resolved.y.tickLabels.last())
    }

    @Test
    fun oneSeriesIsSharedOutAcrossItsPlaces() {
        val resolved = chart(ChartAxis.number(), ChartAxis.category<String>()) {
            bar(august, x = { it.count }, y = { it.route }, stacking = ChartStacking.Normalised)
        }
        val layer = resolved.layers.single()
        listOf(0.4, 0.3, 0.2, 0.1).forEachIndexed { i, share ->
            assertEquals(share, layer.values[i], 1e-9, "each bar is its share of the series")
        }
        assertEquals(1.0, resolved.x.high)
        assertEquals("40%", resolved.valueAxis.write(layer.values[0]))
    }

    @Test
    fun severalSeriesStillFillEachPlace() {
        val resolved = chart(ChartAxis.number(), ChartAxis.category<String>()) {
            bar(months, x = { it.count }, y = { it.month }, series = { ChartSeries(it.route) }, stacking = ChartStacking.Normalised)
            rule(x = 700, label = "Capacity")
        }
        resolved.layers.last().across.forEach { assertEquals(1.0, it, "a place's stack fell short of the whole") }
        assertEquals(0.75, resolved.layers.first().values[0], 1e-9)
        assertEquals(1.0, resolved.x.high)
        assertTrue(resolved.rules.isEmpty(), "a lying chart's rule off the end of its shares was kept")
    }

    @Test
    fun negativesReachDownToMinusOneHundredPercent() {
        val owed = listOf(Riders("950", "Aug", -30), Riders("951", "Aug", -10))
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(owed, x = { it.month }, y = { it.count }, series = { ChartSeries(it.route) }, stacking = ChartStacking.Normalised)
        }
        assertEquals(-1.0, resolved.layers.last().up[0])
        assertEquals(-1.0, resolved.y.low)
    }
}
