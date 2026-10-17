package io.kontour.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A chart asked for its intrinsic size, as a `FlowRow` and `IntrinsicSize`
 * ask: answered, not thrown.
 *
 * Both charts' inner layouts took their width from what they were given, and
 * asked for their intrinsic width they are given no bound, so they laid
 * themselves out `Int.MAX_VALUE` wide and Compose threw. The gallery's charts
 * page, which puts its donut in a `FlowRow` beside its sparklines, crashed on
 * opening.
 */
@OptIn(ExperimentalTestApi::class)
class ChartIntrinsicsTest {

    private val parts = listOf("Rent" to 50, "Food" to 30, "Fun" to 20)

    @Test
    fun bothChartsCanBeAskedTheirIntrinsicSize() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                FlowRow(Modifier.width(360.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectorChart(Modifier.widthIn(min = 200.dp).weight(1f).height(160.dp).testTag("sectors"), animateData = false) {
                        sectors(parts, value = { it.second }, series = { ChartSeries(it.first) })
                    }
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.width(IntrinsicSize.Min).height(160.dp).testTag("chart"),
                        animateData = false,
                    ) {
                        bar(parts, x = { it.first }, y = { it.second })
                    }
                    Text("Beside them")
                }
            }
        }
        waitForIdle()
        assertTrue(onNodeWithTag("sectors").fetchSemanticsNode().size.width > 0, "the sector chart was laid out at nothing")
        onNodeWithTag("chart").assertExists()
    }

    @Test
    fun aChartInAFlowRowStillDraws() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                FlowRow(Modifier.width(360.dp)) {
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.fillMaxWidth().height(160.dp).testTag("chart"),
                        contentDescription = "Spending",
                        animateData = false,
                    ) {
                        bar(parts, x = { it.first }, y = { it.second })
                    }
                }
            }
        }
        waitForIdle()
        // A bar's place is only a node once the chart has laid itself out at
        // a real size, which the intrinsic pass before it must not undo.
        onNodeWithContentDescription("Rent: 50").assertExists()
    }
}
