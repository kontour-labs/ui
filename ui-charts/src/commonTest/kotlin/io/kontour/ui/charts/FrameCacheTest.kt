package io.kontour.ui.charts

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
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.fail

/**
 * A chart measured again with nothing about its words changed takes the frame
 * it laid out last, and lays out a new one for anything that changes them.
 *
 * Its plot is measured again for every place a finger or a pointer selects,
 * since the tooltip's probe is placed there, and it measured every tick label,
 * title and rule label each time: 26 frames laid out in a drag of 40 along a
 * line of thirty days. Counted by whether the frame is the same one, which is
 * what says nothing was measured.
 */
@OptIn(ExperimentalTestApi::class)
class FrameCacheTest {

    private val formats = LocaleFormats.english()

    private class Tools(val measurer: TextMeasurer, val density: Density, val label: TextStyle, val title: TextStyle)

    private fun chart(scale: Double = 1.0): ResolvedChart {
        val x = ChartAxis.number(title = "Day")
        val y = ChartAxis.number(title = "Riders")
        val days = (0 until 30).map { it to (400 + it * 37 % 300) * scale }
        return resolve(
            ChartRecorder(x, y).apply {
                line(days, x = { it.first }, y = { it.second })
                rule(y = 500.0, label = "Capacity")
            }.build(),
            x, y, formats,
        )
    }

    private fun withTools(test: (Tools) -> Unit) {
        var tools: Tools? = null
        runComposeUiTest {
            setContent {
                KontourTheme {
                    tools = Tools(rememberTextMeasurer(), LocalDensity.current, Theme.typography.labelSmall, Theme.typography.labelMedium)
                }
            }
            waitForIdle()
            test(tools ?: fail("no text measurer"))
        }
    }

    private fun FrameCache.layOut(
        t: Tools,
        chart: ResolvedChart,
        width: Float = 720f,
        height: Float = 480f,
        label: TextStyle = t.label,
        density: Density = t.density,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        writeCells: Boolean = false,
    ) = layOut(chart, width, height, t.measurer, label, t.title, 8f, density, layoutDirection, writeCells)

    @Test
    fun theSameChartAtTheSameSizeIsNotLaidOutAgain() = withTools { t ->
        val cache = FrameCache()
        val chart = chart()
        val first = cache.layOut(t, chart)
        assertSame(first, cache.layOut(t, chart), "measured again with nothing changed, the frame was laid out again")
        // And it is the frame the chart would have laid out itself.
        val fresh = layOutFrame(chart, 720f, 480f, t.measurer, t.label, t.title, 8f, t.density, LayoutDirection.Ltr)
        assertEquals(fresh.plot, first.plot)
        assertEquals(fresh.xLabels.map { it.layout.layoutInput.text.text }, first.xLabels.map { it.layout.layoutInput.text.text })
        assertEquals(fresh.yLabels.map { it.layout.layoutInput.text.text }, first.yLabels.map { it.layout.layoutInput.text.text })
    }

    @Test
    fun anythingThatChangesTheWordsLaysItOutAgain() = withTools { t ->
        val cache = FrameCache()
        val chart = chart()
        var last = cache.layOut(t, chart)
        fun changed(what: String, frame: ChartFrame) {
            assertNotSame(last, frame, "$what should have laid the frame out again")
            last = frame
        }
        changed("new data", cache.layOut(t, chart(scale = 2.0)))
        changed("the chart it was before", cache.layOut(t, chart))
        changed("a narrower chart", cache.layOut(t, chart, width = 360f))
        changed("a shorter one", cache.layOut(t, chart, width = 360f, height = 240f))
        changed("larger type", cache.layOut(t, chart, width = 360f, height = 240f, label = t.title))
        changed("another density", cache.layOut(t, chart, width = 360f, height = 240f, label = t.title, density = Density(3f)))
        changed(
            "a larger type scale",
            cache.layOut(t, chart, width = 360f, height = 240f, label = t.title, density = Density(3f, fontScale = 2f)),
        )
        changed(
            "right to left",
            cache.layOut(t, chart, width = 360f, height = 240f, label = t.title, density = Density(3f, fontScale = 2f), layoutDirection = LayoutDirection.Rtl),
        )
        changed(
            "a heat map's values written",
            cache.layOut(
                t, chart, width = 360f, height = 240f, label = t.title, density = Density(3f, fontScale = 2f),
                layoutDirection = LayoutDirection.Rtl, writeCells = true,
            ),
        )
    }
}
