package io.kontour.ui.charts

import io.kontour.ui.locale.LocaleFormats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Points and rectangles between one chart and the next: a rectangle moves at
 * both its corners, and a series of points or rectangles with nothing to come
 * from fades in rather than being there at once.
 */
class MarkMotionTest {

    private val formats = LocaleFormats.english()
    private val x = ChartAxis.number(domain = 0.0..20.0)
    private val y = ChartAxis.number(domain = 0.0..20.0)

    private class Span(val from: Double, val to: Double, val low: Double, val high: Double)

    private fun rectangles(vararg spans: Span): ResolvedChart = resolve(
        ChartRecorder(x, y).apply {
            rectangle(spans.toList(), xStart = { it.from }, xEnd = { it.to }, yStart = { it.low }, yEnd = { it.high })
        }.build(),
        x, y, formats,
    )

    private fun points(vararg at: Pair<Double, Double>): ResolvedChart = resolve(
        ChartRecorder(x, y).apply { point(at.toList(), x = { it.first }, y = { it.second }, series = { ChartSeries("Readings") }) }.build(),
        x, y, formats,
    )

    /**
     * A rectangle moves its far corner as well as its near one. Only its
     * start moved: its end was the new one from the first frame, so a
     * rectangle moving right stretched out to where it was going and then
     * closed up behind.
     */
    @Test
    fun aRectangleMovesAtBothItsCorners() {
        val before = rectangles(Span(2.0, 4.0, 2.0, 4.0))
        val after = rectangles(Span(10.0, 12.0, 10.0, 12.0))
        val halfway = interpolate(before, after, 0.5f).layers.single()
        assertEquals(6.0, halfway.across[0], 1e-9)
        assertEquals(8.0, assertNotNull(halfway.acrossEnd)[0], 1e-9, "its end across should be halfway too")
        assertEquals(8.0, assertNotNull(halfway.upEnd)[0], 1e-9, "and its end up")
    }

    /** One whose far corner alone has moved is a new chart, and is moved to. */
    @Test
    fun aRectangleWhoseEndMovedIsANewChart() {
        assertFalse(rectangles(Span(2.0, 4.0, 2.0, 4.0)).sameAs(rectangles(Span(2.0, 6.0, 2.0, 7.0))))
    }

    /** A chart of points appearing: its points fade in, halfway half there. */
    @Test
    fun pointsAppearingFadeIn() {
        val chart = points(1.0 to 2.0, 3.0 to 4.0)
        val halfway = interpolate(collapsed(chart), chart, 0.5f).layers.single()
        assertEquals(0.5f, halfway.opacity, 0.01f, "points with nothing to come from should be half faded in halfway")
        assertEquals(1f, interpolate(collapsed(chart), chart, 1f).layers.single().opacity)
    }

    /** A series of points new to a chart fades in beside the ones that move. */
    @Test
    fun aNewSeriesOfPointsFadesIn() {
        val before = points(1.0 to 2.0)
        val more = ChartRecorder(x, y).apply {
            point(listOf(1.0 to 2.0), x = { it.first }, y = { it.second }, series = { ChartSeries("Readings") })
            point(listOf(5.0 to 5.0), x = { it.first }, y = { it.second }, series = { ChartSeries("Forecast") })
        }.build()
        val after = resolve(more, x, y, formats)
        val halfway = interpolate(before, after, 0.5f)
        assertEquals(1f, halfway.layers.single { it.series.name == "Readings" }.opacity, "the series that was there stays")
        assertEquals(0.5f, halfway.layers.single { it.series.name == "Forecast" }.opacity, 0.01f, "the new one fades in")
    }

    /** Rectangles appearing fade in as points do. */
    @Test
    fun rectanglesAppearingFadeIn() {
        val chart = rectangles(Span(2.0, 4.0, 2.0, 4.0))
        assertEquals(0.25f, interpolate(collapsed(chart), chart, 0.25f).layers.single().opacity, 0.01f)
    }
}
