package io.kontour.ui.charts

import io.kontour.ui.locale.LocaleFormats
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the content of a chart turns into before anything is drawn: stacks,
 * domains, orientation, interpolation, and the words a screen reader hears.
 */
class ResolveTest {

    private val formats = LocaleFormats.english()

    private class Trip(val day: String, val route: String, val riders: Int)

    private val trips = listOf(
        Trip("Mon", "950", 40), Trip("Mon", "951", 10),
        Trip("Tue", "950", 55), Trip("Tue", "951", -5),
        Trip("Wed", "950", 30), Trip("Wed", "951", 20),
    )

    private fun <X : Any, Y : Any> chart(
        x: ChartAxis<X>,
        y: ChartAxis<Y>,
        content: ChartScope<X, Y>.() -> Unit,
    ): ResolvedChart = resolve(ChartRecorder(x, y).apply(content).build(), x, y, formats)

    @Test
    fun stackedBarsStandOnEachOtherAndNegativesHangBelowZero() {
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(trips, x = { it.day }, y = { it.riders }, series = { ChartSeries(it.route) })
        }
        val second = resolved.layers.first { it.series.name == "951" }
        // Monday: 951 sits on 950's 40.
        assertEquals(40.0, second.base!![0])
        assertEquals(50.0, second.up[0])
        // Tuesday: a negative stacks down from zero, not from 55.
        assertEquals(0.0, second.base!![1])
        assertEquals(-5.0, second.up[1])
        // And its own value, which is what a reader is told, is unchanged.
        assertEquals(listOf(10.0, -5.0, 20.0), second.values.toList())
        assertTrue(resolved.y.low <= -5.0 && resolved.y.high >= 55.0)
    }

    @Test
    fun aNormalisedStackFillsTheWholeAtEveryPlace() {
        val positive = trips.map { Trip(it.day, it.route, abs(it.riders)) }
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            area(positive, x = { it.day }, y = { it.riders }, series = { ChartSeries(it.route) }, stacking = ChartStacking.Normalised)
        }
        val top = resolved.layers.last()
        top.up.forEach { assertEquals(1.0, it, 1e-9) }
        assertTrue(resolved.y.tickLabels.any { it.endsWith("%") }, "a share axis is in percent: ${resolved.y.tickLabels}")
    }

    /**
     * A normalised stack's values are shares, as its axis is. They were the
     * raw values, written by the axis's percent writer: Monday's 40 riders of
     * 50 was said as "4,000%".
     */
    @Test
    fun aNormalisedStackIsWrittenAsTheShareOfEachPlace() {
        val positive = trips.map { Trip(it.day, it.route, abs(it.riders)) }
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(positive, x = { it.day }, y = { it.riders }, series = { ChartSeries(it.route) }, stacking = ChartStacking.Normalised)
        }
        val first = resolved.layers.first { it.series.name == "950" }
        assertEquals(0.8, first.values[0], 1e-9)
        assertEquals("80%", resolved.valueAxis.write(first.values[0]))
    }

    @Test
    fun aCategoryAxisUpLaysTheBarsDown() {
        val resolved = chart(ChartAxis.number(), ChartAxis.category<String>()) {
            bar(trips.filter { it.route == "950" }, x = { it.riders }, y = { it.day })
        }
        assertTrue(resolved.horizontal)
        assertEquals(AxisKind.Category, resolved.placeAxis.kind)
        assertTrue(resolved.x.low <= 0.0, "a lying bar is measured from zero")
    }

    @Test
    fun barsAndAreasIncludeZeroAndLinesNeedNot() {
        val readings = listOf(1 to 101.0, 2 to 104.0, 3 to 103.0)
        val line = chart(ChartAxis.number(), ChartAxis.number()) {
            line(readings, x = { it.first }, y = { it.second })
        }
        assertTrue(line.y.low > 0.0, "a line of values near 100 starts its axis at ${line.y.low}")
        val bars = chart(ChartAxis.number(), ChartAxis.number()) {
            bar(readings, x = { it.first }, y = { it.second })
        }
        assertEquals(0.0, bars.y.low)
    }

    @Test
    fun theOldLineIsReadAtTheNewPlaces() {
        // A line of three points becoming one of five moves from where the
        // three were, read at the five, rather than jumping.
        assertEquals(15.0, sampleAt(doubleArrayOf(0.0, 2.0, 4.0), doubleArrayOf(10.0, 20.0, 0.0), 1.0))
        assertEquals(10.0, sampleAt(doubleArrayOf(0.0, 2.0, 4.0), doubleArrayOf(10.0, 20.0, 0.0), -3.0))
        assertEquals(0.0, sampleAt(doubleArrayOf(0.0, 2.0, 4.0), doubleArrayOf(10.0, 20.0, 0.0), 9.0))
    }

    @Test
    fun aChartAppearingRisesFromItsBaseline() {
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(trips, x = { it.day }, y = { it.riders }, series = { ChartSeries(it.route) }, stacking = ChartStacking.Grouped)
        }
        val start = collapsed(resolved)
        start.layers.forEach { layer -> layer.up.forEach { assertEquals(0.0, it) } }
        val halfway = interpolate(start, resolved, 0.5f)
        assertEquals(20.0, halfway.layers.first().up[0], 1e-9)
        assertEquals(resolved, interpolate(start, resolved, 1f))
    }

    @Test
    fun theSummarySaysTheNumbersAGlanceWouldTake() {
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number(title = "Riders")) {
            line(trips, x = { it.day }, y = { it.riders }, series = { ChartSeries(it.route) })
        }
        val summary = chartSummary(resolved, ChartStrings(), formats, { it.joinToString(". ") }, "Riders this week")
        assertTrue(summary.startsWith("Riders this week. Line chart. 2 series."), summary)
        assertTrue("Riders, " in summary, "the axis up is not named by its title: $summary")
        assertTrue("950, 30 to 55, falling" in summary, summary)
        assertTrue("951, -5 to 20, rising" in summary, summary)
    }

    @Test
    fun eachPlaceSaysEverySeriesThere() {
        val resolved = chart(ChartAxis.category<String>(), ChartAxis.number()) {
            bar(trips, x = { it.day }, y = { it.riders }, series = { ChartSeries(it.route) })
        }
        val places = placeDescriptions(resolved, ChartStrings(), formats)
        assertEquals(3, places.size)
        assertEquals("Mon: 950, 40 and 951, 10", places[0].text)
    }

    @Test
    fun aSeriesKeepsItsSlotByItsKey() {
        val first = ChartRecorder(ChartAxis.category<String>(), ChartAxis.number()).apply {
            bar(trips, x = { it.day }, y = { it.riders }, series = { ChartSeries(it.route) })
        }.build()
        assertEquals(listOf("950", "951"), first.series.map { it.name })
        assertEquals(1, first.slotOf(ChartSeries("951")))
        assertFalse(first.isEmpty)
    }

    @Test
    fun withPatternsEachSlotDiffersOnEveryChannel() {
        val palette = SeriesPalettes.Light
        val looks = (0 until 5).map { lookOf(it, palette, patterns = true) }
        assertEquals(5, looks.map { it.line }.distinct().size)
        assertEquals(5, looks.map { it.symbol }.distinct().size)
        assertEquals(5, looks.map { it.fill }.distinct().size)
        assertEquals(LinePattern.Solid, looks[0].line)
        assertEquals(FillPattern.Solid, looks[0].fill)
        // Past the palette, colours repeat — and patterns come on to tell the
        // second round from the first.
        val seventh = lookOf(palette.size, palette, patterns = false)
        assertEquals(palette[0], seventh.colour)
        assertTrue(seventh.fill != FillPattern.Solid || seventh.line != LinePattern.Solid)
    }
}
