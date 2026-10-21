package io.kontour.ui.charts

import io.kontour.ui.locale.LocaleFormats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A chart moving between ways of showing the same data — stacked, side by
 * side, as shares, as bars or a line — moves each mark from where it was to
 * where it goes, with nothing standing in the air, jumping in width, or
 * heaped at the foot of the plot on the way.
 *
 * Reported as *"in things like the stacked bar chart, it looks a bit weird
 * with each of the sections animating open individually"*, with the ask to go
 * through every switch of presentation and make sure none jumps around.
 */
class PresentationMotionTest {

    private val formats = LocaleFormats.english()

    private class Spend(val month: String, val what: String, val amount: Double)

    private val quarter = listOf(
        Spend("Jul", "Rent", 1450.0), Spend("Jul", "Food", 520.0), Spend("Jul", "Travel", 180.0),
        Spend("Aug", "Rent", 1450.0), Spend("Aug", "Food", 610.0), Spend("Aug", "Travel", 340.0),
        Spend("Sep", "Rent", 1450.0), Spend("Sep", "Food", 480.0), Spend("Sep", "Travel", 95.0),
    )

    private fun bars(stacking: ChartStacking, data: List<Spend> = quarter): ResolvedChart {
        val x = ChartAxis.category<String>()
        val y = ChartAxis.number()
        return resolve(
            ChartRecorder(x, y).apply {
                bar(data, x = { it.month }, y = { it.amount }, series = { ChartSeries(it.what) }, stacking = stacking)
            }.build(),
            x, y, formats,
        )
    }

    private fun lines(): ResolvedChart {
        val x = ChartAxis.category<String>()
        val y = ChartAxis.number()
        return resolve(
            ChartRecorder(x, y).apply {
                line(quarter, x = { it.month }, y = { it.amount }, series = { ChartSeries(it.what) })
            }.build(),
            x, y, formats,
        )
    }

    /** Each part of a stack stands on the one under it in every frame: no gaps, no overlaps. */
    private fun assertStandsTogether(chart: ResolvedChart, at: String) {
        val parts = chart.layers.filter { it.opacity > 0f }
        for (i in 1 until parts.size) {
            for (place in parts[i].up.indices) {
                assertEquals(
                    parts[i - 1].up[place], parts[i].base!![place], 1e-9,
                    "$at: ${parts[i].series.name} should stand on ${parts[i - 1].series.name} at place $place",
                )
            }
        }
    }

    @Test
    fun aStackRisesAsOne() {
        val stack = bars(ChartStacking.Stacked)
        val start = collapsed(stack)
        start.layers.forEach { layer ->
            layer.up.forEach { assertEquals(0.0, it, "every part should start at the stack's foot") }
            layer.base!!.forEach { assertEquals(0.0, it, "every part's foot should start at the stack's foot") }
        }
        for (fraction in listOf(0.1f, 0.5f, 0.9f)) assertStandsTogether(interpolate(start, stack, fraction), "rising at $fraction")
    }

    @Test
    fun aNewPlacesStackRisesAsOne() {
        val two = bars(ChartStacking.Stacked, quarter.filter { it.month != "Sep" })
        val three = bars(ChartStacking.Stacked)
        val halfway = interpolate(two, three, 0.5f)
        assertStandsTogether(halfway, "September arriving")
        val rent = halfway.layers.first { it.series.name == "Rent" }
        assertEquals(725.0, rent.up[2], 1e-9, "September's foot should rise from zero with the rest of its stack")
    }

    @Test
    fun barsSideBySideNarrowAsTheyLeaveTheStack() {
        val stacked = bars(ChartStacking.Stacked)
        val grouped = bars(ChartStacking.Grouped)
        val halfway = interpolate(stacked, grouped, 0.5f)
        val food = halfway.layers.first { it.series.name == "Food" }
        assertEquals((1f + 1f / 3) / 2, food.groupWidth, 1e-6f, "a bar should be between a stack's width and a group's")
        assertEquals(0f, food.groupOffset, 1e-6f, "the middle bar of three stays in the middle")
        val travel = halfway.layers.first { it.series.name == "Travel" }
        assertEquals(1f / 6, travel.groupOffset, 1e-6f, "the last bar should be half way to its place in the group")
        // And its foot comes down from the stack to zero as it goes.
        assertEquals(2060.0 / 2, travel.base!![1], 1e-9)
    }

    @Test
    fun sharesTakeTheirAxisAtOnceAndKeepTheirPlace() {
        val stacked = bars(ChartStacking.Stacked)
        val shares = bars(ChartStacking.Normalised)
        val first = interpolate(stacked, shares, 0f)
        assertEquals(shares.y.high, first.y.high, "the axis should be in shares from the first frame, not heap its labels at the foot")
        // Rent's top at August, still where it was on the plot.
        val rent = first.layers.first { it.series.name == "Rent" }
        assertEquals(1450.0 / stacked.y.high, (rent.up[1] - first.y.low) / first.y.span, 1e-9)
        assertStandsTogether(interpolate(stacked, shares, 0.5f), "turning to shares")
        assertStandsTogether(interpolate(shares, stacked, 0.5f), "turning back")
    }

    @Test
    fun barsTurnedToALineMoveIntoItRatherThanSinkingUnderIt() {
        val grouped = bars(ChartStacking.Grouped)
        val line = lines()
        val first = interpolate(grouped, line, 0f)
        val bar = first.layers.first { it.series.name == "Food" && it.mark.kind == MarkKind.Bar }
        val drawn = first.layers.first { it.series.name == "Food" && it.mark.kind == MarkKind.Line }
        assertEquals(1f, bar.opacity, "the bars should start whole")
        assertEquals(0f, drawn.opacity, "the line should fade in")
        assertEquals(610.0, drawn.up[1], 1e-9, "the line should start at the bars' tops")
        val halfway = interpolate(grouped, line, 0.5f)
        val leaving = halfway.layers.first { it.series.name == "Food" && it.mark.kind == MarkKind.Bar }
        assertEquals(610.0, leaving.up[1], 1e-9, "a bar going to a line of its own values should stay its height")
        assertTrue(leaving.opacity in 0.4f..0.6f, "and fade as the line comes: ${leaving.opacity}")
    }

    @Test
    fun aLineTurnedToAStackRisesToItsTopsAsTheStackFadesIn() {
        val line = lines()
        val stack = bars(ChartStacking.Stacked)
        val halfway = interpolate(line, stack, 0.5f)
        // The stack where it will be, every part on the one under it.
        assertStandsTogether(halfway.copyOnly(MarkKind.Bar), "a line turning into a stack")
        // Still on the plot as the axis moves under it, rather than growing
        // down into it from above.
        val food = halfway.layers.first { it.series.name == "Food" && it.mark.kind == MarkKind.Bar }
        assertEquals((2060.0 - stack.y.low) / stack.y.span, (food.up[1] - halfway.y.low) / halfway.y.span, 1e-9)
        // And the line half way from its 610 to the stack's top.
        val drawn = halfway.layers.first { it.series.name == "Food" && it.mark.kind == MarkKind.Line }
        assertEquals((610.0 + 2060.0) / 2, drawn.up[1], 1e-9)
        // Back again, the same way round.
        val back = interpolate(stack, line, 0.5f)
        assertStandsTogether(back.copyOnly(MarkKind.Bar), "a stack turning into a line")
        assertEquals((2060.0 + 610.0) / 2, back.layers.first { it.series.name == "Food" && it.mark.kind == MarkKind.Line }.up[1], 1e-9)
    }

    /**
     * Along an axis that slides, a place that has gone sinks to its foot as
     * the axis closes in, and a category's, which does not slide, goes at once.
     */
    @Test
    fun barsAtPlacesThatHaveGoneSinkAsTheAxisClosesIn() {
        val x = ChartAxis.number()
        val y = ChartAxis.number()
        fun days(count: Int) = resolve(
            ChartRecorder(x, y).apply {
                bar((1..count).toList(), x = { it }, y = { 10.0 }, series = { ChartSeries("Use") })
            }.build(),
            x, y, formats,
        )
        val halfway = interpolate(days(6), days(3), 0.5f).layers.single()
        assertEquals(6, halfway.size, "the three days going should still be drawn on the way")
        val going = halfway.across.indices.filter { halfway.across[it] > 3.0 }
        going.forEach { assertEquals(5.0, halfway.up[it], 1e-9, "a day going should be half way down to its foot") }
        val categories = interpolate(bars(ChartStacking.Grouped), bars(ChartStacking.Grouped, quarter.filter { it.month != "Sep" }), 0.5f)
        assertEquals(2, categories.layers.first().size, "a category that has gone should go at once")
    }

    private fun ResolvedChart.copyOnly(kind: MarkKind) = with(x, y, layers.filter { it.mark.kind == kind })
}
