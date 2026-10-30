package io.kontour.ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.fail

/**
 * Where a chart's data is, for its rules' and regions' labels to keep clear
 * of, is found once for the chart a move is heading to and the plot it is
 * drawn in, not again for every frame drawn over them.
 *
 * A chart with a labelled rule found it on every frame it drew: each frame of
 * a range dragged out, of a place dragged along and of a move.
 */
@OptIn(ExperimentalTestApi::class)
class ObstacleCacheTest {

    private val formats = LocaleFormats.english()

    private fun chart(scale: Double = 1.0): ResolvedChart {
        val x = ChartAxis.number()
        val y = ChartAxis.number()
        val days = (0 until 30).map { it to (400 + it * 37 % 300) * scale }
        return resolve(
            ChartRecorder(x, y).apply {
                line(days, x = { it.first }, y = { it.second })
                rule(y = 500.0, label = "Capacity")
            }.build(),
            x, y, formats,
        )
    }

    private fun frameOf(chart: ResolvedChart, width: Float): ChartFrame {
        var measurer: TextMeasurer? = null
        var density: Density? = null
        var frame: ChartFrame? = null
        runComposeUiTest {
            setContent {
                KontourTheme {
                    measurer = rememberTextMeasurer()
                    density = LocalDensity.current
                }
            }
            waitForIdle()
            frame = layOutFrame(chart, width, 480f, measurer ?: fail("no measurer"), TextStyle.Default, TextStyle.Default, 8f, density!!, LayoutDirection.Ltr)
        }
        return frame ?: fail("no frame")
    }

    @Test
    fun theSameChartInTheSamePlotIsNotSearchedAgain() {
        val chart = chart()
        val frame = frameOf(chart, 720f)
        val cache = ObstacleCache()
        val first = cache.of(chart, Mapper(frame, chart.x, chart.y), 8f)
        assertSame(first, cache.of(chart, Mapper(frame, chart.x, chart.y), 8f), "drawn again over the same chart, its data was found again")
        // A plot that has not moved, in a frame of its own, is the same plot.
        val again = frame.withInsets(PlotInsets(frame.plot.left, frame.plot.top, frame.width - frame.plot.right, frame.height - frame.plot.bottom))
        assertSame(first, cache.of(chart, Mapper(again, chart.x, chart.y), 8f))
    }

    @Test
    fun anotherChartOrPlotIsSearchedAgain() {
        val chart = chart()
        val frame = frameOf(chart, 720f)
        val cache = ObstacleCache()
        var last = cache.of(chart, Mapper(frame, chart.x, chart.y), 8f)
        val other = chart(scale = 2.0)
        val now = cache.of(other, Mapper(frame, other.x, other.y), 8f)
        assertNotSame(last, now, "new data should have been searched")
        last = now
        val moved = frame.withInsets(PlotInsets(frame.plot.left + 20f, frame.plot.top, frame.width - frame.plot.right, frame.height - frame.plot.bottom))
        val inMoved = cache.of(other, Mapper(moved, other.x, other.y), 8f)
        assertNotSame(last, inMoved, "a plot that has moved should have been searched")
        assertNotSame(inMoved, cache.of(other, Mapper(moved, other.x, other.y), 4f), "a finer search should have been made again")
    }

    /** And what it finds is where the data is: a box on the line is hit, one well above it is not. */
    @Test
    fun whatItFindsIsWhereTheLineIs() {
        val chart = chart()
        val frame = frameOf(chart, 720f)
        val mapper = Mapper(frame, chart.x, chart.y)
        val found = ObstacleCache().of(chart, mapper, 8f)
        val on = Offset(mapper.px(10.0), mapper.py(chart.layers[0].up[10]))
        assertEquals(true, found.hit(Rect(on - Offset(4f, 4f), Size(8f, 8f))))
        assertEquals(false, found.hit(Rect(Offset(on.x - 4f, frame.plot.top - 40f), Size(8f, 8f))))
    }
}
