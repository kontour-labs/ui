package io.kontour.ui.charts

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Bars across a continuous axis are as wide as the chart's closest places
 * allow, every series alike: a series with fewer bars than the others is not
 * wider for it.
 */
@OptIn(ExperimentalTestApi::class)
class BarWidthTest {

    private class Day(val day: Int, val what: String, val amount: Double)

    /**
     * Refunds on two days a week apart, stacked on a fortnight's daily sales.
     * Each part's width came from its own series' closest places, so a refund
     * was a week's bar wide, standing out past the sales under it either side.
     */
    @Test
    fun aSparseSeriesInAStackIsAsWideAsTheStack() = runComposeUiTest {
        val sales = (1..14).map { Day(it, "Sales", 10.0) }
        val refunds = listOf(Day(1, "Refunds", 3.0), Day(8, "Refunds", 3.0))
        setContent {
            KontourTheme(reduceMotion = true) {
                Chart(
                    xAxis = ChartAxis.number(),
                    yAxis = ChartAxis.number(),
                    modifier = Modifier.size(420.dp, 200.dp),
                    colours = ChartDefaults.colours(series = listOf(SalesInk, RefundsInk), grid = Color.Transparent),
                    showLegend = false,
                    showPatterns = false,
                    animateData = false,
                ) {
                    bar(sales + refunds, x = { it.day }, y = { it.amount }, series = { ChartSeries(it.what) }, stacking = ChartStacking.Stacked)
                }
            }
        }
        val pixels = onRoot().captureToImage().toPixelMap()
        val sale = widestRun(pixels, SalesInk)
        val refund = widestRun(pixels, RefundsInk)
        assertTrue(sale > 4, "the sales bars should have been drawn: $sale")
        assertTrue(refund <= sale + 2, "a refund should be as wide as the sale it stands on: $refund px against $sale")
    }

    /** The widest run of [ink] along any row of [pixels]: one bar's width, where bars stand apart. */
    private fun widestRun(pixels: PixelMap, ink: Color): Int {
        var widest = 0
        for (y in 0 until pixels.height) {
            var run = 0
            for (x in 0 until pixels.width) {
                if (near(pixels[x, y], ink)) {
                    run++
                    widest = maxOf(widest, run)
                } else {
                    run = 0
                }
            }
        }
        return widest
    }

    private fun near(a: Color, b: Color) = abs(a.red - b.red) < 0.06f && abs(a.green - b.green) < 0.06f && abs(a.blue - b.blue) < 0.06f

    private companion object {
        val SalesInk = Color(0xFF2060C0)
        val RefundsInk = Color(0xFFD03020)
    }
}
