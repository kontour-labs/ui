package io.kontour.ui.charts

import androidx.compose.ui.graphics.Color
import io.kontour.ui.locale.LocaleFormats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A heat map: cells at two categories, shaded along a ramp from the least of
 * them to the most, said a column at a time and tabled with a row for each
 * column.
 */
class HeatMapTest {

    private val formats = LocaleFormats.english()
    private val strings = ChartStrings()

    private class Riders(val day: String, val hour: String, val count: Int?)

    private val riders = listOf(
        Riders("Mon", "6am", 12), Riders("Mon", "7am", 30),
        Riders("Tue", "6am", 18), Riders("Tue", "7am", null),
    )

    private fun heatMap(items: List<Riders> = riders): ResolvedChart {
        val x = ChartAxis.category<String>()
        val y = ChartAxis.category<String>()
        return resolve(ChartRecorder(x, y).apply { cell(items, x = { it.day }, y = { it.hour }, value = { it.count }) }.build(), x, y, formats)
    }

    @Test
    fun cellsStandAtTheirPlacesAndTheScaleRunsFromTheLeastToTheMost() {
        val resolved = heatMap()
        val layer = resolved.layers.single()
        assertEquals(MarkKind.Cell, layer.mark.kind)
        assertEquals(listOf(0.0, 0.0, 1.0, 1.0), layer.across.toList())
        assertEquals(listOf(0.0, 1.0, 0.0, 1.0), layer.up.toList())
        val scale = assertNotNull(resolved.cells)
        assertEquals(12.0, scale.low)
        assertEquals(30.0, scale.high)
        assertEquals(0f, scale.fraction(12.0))
        assertEquals(0.5f, scale.fraction(21.0))
        assertEquals(1f, scale.fraction(99.0), "a value past the most is clamped to the deep end")
        assertFalse(resolved.horizontal)
    }

    @Test
    fun aChartWithNoCellsHasNoScale() {
        val x = ChartAxis.category<String>()
        val y = ChartAxis.number()
        val resolved = resolve(ChartRecorder(x, y).apply { bar(listOf(1), x = { "Mon" }, y = { it }) }.build(), x, y, formats)
        assertNull(resolved.cells)
    }

    @Test
    fun itIsSaidAColumnAtATimeAndTabledWithARowForEach() {
        val resolved = heatMap()
        val places = placeDescriptions(resolved, strings, formats)
        assertEquals("Mon: 6am, 12 and 7am, 30", places[0].text)
        assertEquals("Tue: 6am, 18", places[1].text, "an empty cell was said")
        val summary = chartSummary(resolved, strings, formats, { it.joinToString(". ") }, null)
        assertTrue(summary.startsWith("Heat map."), summary)
        val table = chartTable(resolved, strings, formats)
        assertEquals(listOf("6am", "7am"), table.columns)
        assertEquals(listOf("12", "30"), table.rows[0].cells)
        assertEquals(listOf("18", ""), table.rows[1].cells)
    }

    @Test
    fun newShadesInTheSameCellsAreANewChart() {
        val more = riders.map { Riders(it.day, it.hour, it.count?.plus(1)) }
        assertTrue(heatMap().sameAs(heatMap()))
        assertFalse(heatMap().sameAs(heatMap(more)), "cells whose numbers changed were not animated to")
    }

    @Test
    fun theRampIsReadContinuously() {
        // Between stops, a blend: paler than the deep end and deeper than the pale.
        val ramp = listOf(Color.Black, Color.White)
        val middle = rampAt(ramp, 0.5f)
        assertTrue(middle.red > 0.1f && middle.red < 0.9f, "the middle of the ramp was a stop: $middle")
        assertTrue(rampAt(ramp, 0.25f).red < middle.red && middle.red < rampAt(ramp, 0.75f).red, "the ramp went back on itself")
        // At a stop, the stop.
        val three = listOf(Color.Black, Color.Red, Color.White)
        val red = rampAt(three, 0.5f)
        assertEquals(1f, red.red, 0.01f)
        assertEquals(0f, red.green, 0.01f)
        assertEquals(1f, rampAt(three, 2f).green, 0.01f, "past the end is the end")
    }
}
