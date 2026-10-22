package io.kontour.ui.charts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.TooltipDefaults
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
        // One node a row, said the reader's way.
        onNodeWithContentDescription("Total, 2,060").assertExists()
        onNodeWithContentDescription("Food, 610").assertExists()
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
        onNodeWithContentDescription("Total, 2,060").assertDoesNotExist()
    }

    private val aRow = SemanticsMatcher("a row of the tooltip") { node ->
        val said = if (SemanticsProperties.ContentDescription in node.config) node.config[SemanticsProperties.ContentDescription] else emptyList()
        said.any { it.startsWith("Rent, ") }
    }

    private fun ComposeUiTest.tooltipShown(): Boolean = onAllNodes(aRow, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * A finger held on a chart keeps its tooltip however long it is held, and
     * the tooltip goes [TooltipDefaults.TouchDuration] after it lifts. It went
     * that long after it first showed, in the middle of a drag.
     */
    @Test
    fun aHeldFingerKeepsItsTooltip() = runComposeUiTest {
        bars(ChartSelectionState(null))
        onNodeWithTag("chart").performTouchInput { down(Offset(width * 0.5f, height / 2f)) }
        mainClock.advanceTimeBy(5_000)
        assertTrue(tooltipShown(), "the tooltip should stay while the finger is down")
        onNodeWithTag("chart").performTouchInput { up() }
        mainClock.advanceTimeBy(1_000)
        assertTrue(tooltipShown(), "and for a while after it lifts")
        mainClock.advanceTimeBy(TooltipDefaults.TouchDuration.inWholeMilliseconds)
        waitForIdle()
        assertTrue(!tooltipShown(), "and then go")
    }

    /**
     * A mouse that lands on the tooltip, as one moving fast can before the
     * tooltip has moved on, keeps it: it chooses where it is rather than
     * letting go. It let go, the tooltip went, and came back after a rest:
     * *"the tooltip sometimes disappears for a sec then comes back. it also
     * happens if i drag/hover into the tooltip"*.
     */
    @Test
    fun aMouseOnTheTooltipKeepsIt() = runComposeUiTest {
        bars(ChartSelectionState(null))
        val chart = onNodeWithTag("chart")
        chart.performMouseInput { moveTo(Offset(width * 0.3f, height * 0.5f)) }
        mainClock.advanceTimeBy(1_000)
        assertTrue(tooltipShown(), "resting, the mouse should have shown the tooltip")
        // Straight onto the card beside the place.
        val card = onAllNodes(aRow, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        val root = chart.fetchSemanticsNode().boundsInRoot
        chart.performMouseInput { moveTo(Offset(card.center.x - root.left, card.center.y - root.top)) }
        repeat(30) { frame ->
            mainClock.advanceTimeBy(16)
            assertTrue(tooltipShown(), "the tooltip went $frame frames after the mouse landed on it")
        }
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
        // The card's row, said as the sector's own node is: the smaller of the two.
        val tip = onAllNodesWithContentDescription("Rent, 50%").fetchSemanticsNodes()
            .map { node -> with(density) { node.boundsInRoot.let { DpRect(it.left.toDp(), it.top.toDp(), it.right.toDp(), it.bottom.toDp()) } } }
            .minBy { it.bottom - it.top }
        assertTrue(tip.bottom < tapY, "the tooltip should stand above the finger: its foot at ${tip.bottom}, the finger at $tapY")
        assertTrue(tapY - tip.bottom < 60.dp, "and just above it, not over the sector's top: ${tapY - tip.bottom} away")
        val middle = (tip.left + tip.right) / 2
        assertTrue(abs((middle - tapX).value) < 40f, "and over it: its middle at $middle, the finger at $tapX")
    }

    /**
     * A mouse off a pie's circle, still in its box, lets the pie go, and the
     * tooltip with it: *"the tooltip in the pie chart should fade out when you
     * leave the circle, not the bounding box"*.
     */
    @Test
    fun aMouseOffThePiesCircleLetsGo() = runComposeUiTest {
        val selection = ChartSelectionState<ChartSeries>(null)
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    SectorChart(modifier = Modifier.size(240.dp).testTag("pie"), selection = selection, showLegend = false, animateData = false) {
                        sectors(listOf(Spend("Sep", "Rent", 50.0), Spend("Sep", "Food", 50.0)), value = { it.amount }, series = { ChartSeries(it.what) })
                    }
                }
            }
        }
        val pie = onNodeWithTag("pie")
        pie.performMouseInput { moveTo(Offset(width * 0.75f, height * 0.5f)) }
        mainClock.advanceTimeBy(1_000)
        assertTrue(selection.x == ChartSeries("Rent"), "over the circle, the mouse should choose Rent")
        // The box's corner, outside the circle.
        pie.performMouseInput { moveTo(Offset(width * 0.97f, height * 0.03f)) }
        mainClock.advanceTimeBy(100)
        assertTrue(selection.x == null, "off the circle, the pie should let go: ${selection.x}")
    }

    /**
     * A place's values are written to as many digits as the one that needs
     * most, total and all, so the tooltip's column lines up digit under digit:
     * each written on its own, "2" stood over "2.42".
     */
    @Test
    fun aTooltipsColumnIsWrittenInStep() = runComposeUiTest {
        val day = listOf(Spend("Mon", "Rent", 2.0), Spend("Mon", "Food", 2.42), Spend("Tue", "Rent", 1.0), Spend("Tue", "Food", 1.5))
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
                        selection = rememberChartSelectionState(),
                        showLegend = false,
                        animateData = false,
                    ) {
                        bar(day, x = { it.month }, y = { it.amount }, series = { ChartSeries(it.what) }, stacking = ChartStacking.Stacked)
                    }
                }
            }
        }
        tapChart(0.25f)
        onNodeWithContentDescription("Rent, 2.00").assertExists()
        onNodeWithContentDescription("Food, 2.42").assertExists()
        onNodeWithContentDescription("Total, 4.42").assertExists()
    }

    /**
     * A card squeezed by the room beside it gives what room it has to the
     * name, and leaves no gap of its own between the name and the value: the
     * name shared the room with a spacer, half each, and was cut short beside
     * an empty gap of its own length, "Everything …".
     */
    @Test
    fun aSqueezedCardGivesItsRoomToTheName() = runComposeUiTest {
        val day = listOf(Spend("13 Oct", "Rent", 3.0), Spend("13 Oct", "Everything else in the house", 1.0))
        setContent {
            KontourTheme(reduceMotion = true) {
                // Narrow, so that neither side of the place has room for the
                // whole card, and it is squeezed.
                OverlayHost(Modifier.size(150.dp, 300.dp)) {
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.size(150.dp, 240.dp).testTag("chart"),
                        selection = rememberChartSelectionState(),
                        showLegend = false,
                        animateData = false,
                    ) {
                        bar(day, x = { it.month }, y = { it.amount }, series = { ChartSeries(it.what) }, stacking = ChartStacking.Stacked)
                    }
                }
            }
        }
        tapChart(0.5f)
        fun textNode(text: String) = onAllNodes(SemanticsMatcher(text) { node ->
            val said = if (SemanticsProperties.Text in node.config) node.config[SemanticsProperties.Text] else emptyList()
            said.any { it.text == text }
        }, useUnmergedTree = true).fetchSemanticsNodes().first()
        val name = textNode("Everything else in the house").boundsInRoot
        val value = textNode("1").boundsInRoot
        val gap = with(density) { (value.left - name.right).toDp() }
        assertTrue(gap <= 17.dp, "the name should reach to just short of the value, not stop half way: a gap of $gap")
    }
}
