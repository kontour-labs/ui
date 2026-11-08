package io.kontour.ui.charts

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A legend asked to stand beside the plot does so only while it fits there.
 *
 * It was held to the plot's height and its entries ran on past it: at 200%
 * type a short chart's six series drew over whatever was under it. Found in
 * the November 2026 audit.
 */
@OptIn(ExperimentalTestApi::class)
class ChartSideLegendTest {

    private class Riders(val day: Int, val route: String, val riders: Int)

    private val routes = listOf("950", "951", "952", "953", "954", "955")
    private val week = (1..3).flatMap { day -> routes.mapIndexed { i, route -> Riders(day, route, 100 * (i + day)) } }

    private fun ComposeUiTest.entries(fontScale: Float): Pair<DpRect, List<DpRect>> {
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Chart(
                        xAxis = ChartAxis.number(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.size(400.dp, 160.dp).testTag("chart"),
                        legendPosition = ChartLegendPosition.End,
                        // Toggleable, so the entries are nodes to measure.
                        seriesVisibility = ChartSeriesVisibilityState(emptySet()),
                        animateData = false,
                    ) {
                        line(week, x = { it.day }, y = { it.riders }, series = { ChartSeries("Route ${it.route}") })
                    }
                }
            }
        }
        waitForIdle()
        val chart = onNodeWithTag("chart").getBoundsInRoot()
        val legend = onAllNodes(isToggleable()).fetchSemanticsNodes().indices.map { onAllNodes(isToggleable())[it].getBoundsInRoot() }
        return chart to legend
    }

    @Test
    fun atTwiceTheTypeTheLegendGoesUnderThePlotAndStaysInTheChart() = runComposeUiTest {
        val (chart, legend) = entries(fontScale = 2f)
        assertEquals(routes.size, legend.size)
        for (entry in legend) {
            assertTrue(entry.bottom <= chart.bottom + 0.5.dp, "an entry ends at ${entry.bottom}, past the chart's ${chart.bottom}")
        }
        assertTrue(legend.all { it.left < chart.left + (chart.right - chart.left) / 3 }, "the legend stayed beside the plot")
    }

    @Test
    fun atTheDefaultTypeItStandsBeside() = runComposeUiTest {
        val (chart, legend) = entries(fontScale = 1f)
        assertTrue(legend.all { it.left > chart.left + (chart.right - chart.left) / 2 }, "a legend that fits beside the plot moved")
    }
}
