package io.kontour.ui.charts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a chart's tooltip says and where it stands, and a chart with no
 * selection, which has none and answers nothing.
 */
@OptIn(ExperimentalTestApi::class)
class ChartTooltipTest {

    private class Spend(val month: String, val what: String, val amount: Double)

    private val quarter = listOf(
        Spend("Jul", "Rent", 1450.0), Spend("Jul", "Food", 520.0),
        Spend("Aug", "Rent", 1450.0), Spend("Aug", "Food", 610.0),
        Spend("Sep", "Rent", 1450.0), Spend("Sep", "Food", 480.0),
    )

    private fun ComposeUiTest.bars(selection: ChartSelectionState<String>?) {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
                        selection = selection,
                        showLegend = false,
                        animateData = false,
                    ) {
                        bar(quarter, x = { it.month }, y = { it.amount }, series = { ChartSeries(it.what) }, stacking = ChartStacking.Stacked)
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.tapChart(xFraction: Float) {
        onNodeWithTag("chart").performTouchInput { click(Offset(width * xFraction, height / 2f)) }
        waitForIdle()
    }

    /** A stack's tooltip adds up its parts under them. */
    @Test
    fun aStacksTooltipSaysItsTotal() = runComposeUiTest {
        bars(ChartSelectionState(null))
        tapChart(0.5f)
        onNodeWithText("Total").assertExists()
        onNodeWithText("2,060").assertExists()
    }

    /**
     * No selection, and the chart is a picture: no focus, no action on any
     * of its nodes, and a tap on it shows nothing. Asked for as *"make it
     * possible to have a chart without any interaction"*.
     */
    @Test
    fun aChartWithoutASelectionAnswersNothing() = runComposeUiTest {
        bars(selection = null)
        onAllNodes(isFocusable()).assertCountEquals(0)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertCountEquals(0)
        tapChart(0.5f)
        onNodeWithText("Total").assertDoesNotExist()
        onNodeWithText("2,060").assertDoesNotExist()
    }

    /**
     * A pie's tooltip stands over the finger that chose its sector, rather
     * than over the sector's middle: *"when you hover over/tap a pie chart,
     * can we make the tooltip show up where your mouse/finger is"*.
     */
    @Test
    fun aPiesTooltipIsWhereTheFingerIs() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    SectorChart(
                        modifier = Modifier.size(240.dp).testTag("pie"),
                        selection = rememberChartSelectionState(),
                        showLegend = false,
                        animateData = false,
                    ) {
                        // Rent the right half from the top, then food.
                        sectors(listOf(Spend("Sep", "Rent", 50.0), Spend("Sep", "Food", 50.0)), value = { it.amount }, series = { ChartSeries(it.what) })
                    }
                }
            }
        }
        val pie = onNodeWithTag("pie").getBoundsInRoot()
        // Low in rent's half, well away from its middle.
        val tapX = pie.left + (pie.right - pie.left) * 0.75f
        val tapY = pie.top + (pie.bottom - pie.top) * 0.8f
        onNodeWithTag("pie").performTouchInput { click(Offset(width * 0.75f, height * 0.8f)) }
        waitForIdle()
        val tip = onNodeWithText("50%").getBoundsInRoot()
        assertTrue(tip.bottom < tapY, "the tooltip should stand above the finger: its foot at ${tip.bottom}, the finger at $tapY")
        assertTrue(tapY - tip.bottom < 60.dp, "and just above it, not over the sector's top: ${tapY - tip.bottom} away")
        val middle = (tip.left + tip.right) / 2
        assertTrue(abs((middle - tapX).value) < 40f, "and over it: its middle at $middle, the finger at $tapX")
    }
}
