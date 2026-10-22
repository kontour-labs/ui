package io.kontour.ui.charts

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.TooltipDefaults
import io.kontour.ui.theme.KontourTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a chart answers a finger, a key and a parent that scrolls: a tooltip
 * that timed out comes back for a tap, a chart that only marks ranges leaves
 * a plain drag and the arrows to what is round it, the arrows start from the
 * reading end right to left, and a pie toggled twice in a sweep goes on from
 * where it is.
 */
@OptIn(ExperimentalTestApi::class)
class ChartInteractionTest {

    private class Spend(val month: String, val what: String, val amount: Double)

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun")
    private val spending = months.mapIndexed { i, month -> Spend(month, "Rent", 100.0 + i * 20) }

    private val aRow = SemanticsMatcher("a row of the tooltip") { node ->
        val said = if (SemanticsProperties.ContentDescription in node.config) node.config[SemanticsProperties.ContentDescription] else emptyList()
        said.any { it.startsWith("Rent, ") }
    }

    private fun ComposeUiTest.tooltipShown(): Boolean = onAllNodes(aRow, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * A finger's tooltip goes a while after it lifts, and the place stays
     * chosen. A tap on the same place again brought nothing back, since what
     * was chosen had not changed and the tooltip stayed put away for it.
     */
    @Test
    fun aTapOnThePlaceAgainBringsBackItsTooltip() = runComposeUiTest {
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
                        bar(spending, x = { it.month }, y = { it.amount }, series = { ChartSeries(it.what) })
                    }
                }
            }
        }
        val chart = onNodeWithTag("chart")
        chart.performTouchInput { click(Offset(width * 0.5f, height / 2f)) }
        waitForIdle()
        assertTrue(tooltipShown(), "a tap should show the place's tooltip")
        mainClock.advanceTimeBy(TooltipDefaults.TouchDuration.inWholeMilliseconds + 500)
        waitForIdle()
        assertTrue(!tooltipShown(), "and it should go a while after")
        chart.performTouchInput { click(Offset(width * 0.5f, height / 2f)) }
        waitForIdle()
        assertTrue(tooltipShown(), "a tap on the same place should bring it back")
    }

    /** The same for a pie: a tap on the sector again brings its tooltip back. */
    @Test
    fun aTapOnTheSectorAgainBringsBackItsTooltip() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    SectorChart(
                        modifier = Modifier.size(240.dp).testTag("pie"),
                        selection = rememberChartSelectionState(),
                        showLegend = false,
                        animateData = false,
                    ) {
                        sectors(listOf("Rent" to 50, "Food" to 50), value = { it.second }, series = { ChartSeries(it.first) })
                    }
                }
            }
        }
        // The sector's own node says it, and so does the tooltip's row.
        fun saidTimes() = onAllNodesWithContentDescription("Rent, 50%").fetchSemanticsNodes().size
        val pie = onNodeWithTag("pie")
        pie.performTouchInput { click(Offset(width * 0.75f, height * 0.5f)) }
        waitForIdle()
        assertEquals(2, saidTimes(), "a tap should show rent's tooltip")
        mainClock.advanceTimeBy(TooltipDefaults.TouchDuration.inWholeMilliseconds + 500)
        waitForIdle()
        assertEquals(1, saidTimes(), "and it should go a while after")
        pie.performTouchInput { click(Offset(width * 0.75f, height * 0.5f)) }
        waitForIdle()
        assertEquals(2, saidTimes(), "a tap on the same sector should bring it back")
    }

    /**
     * A chart that marks ranges and chooses no place takes no plain drag
     * across: a finger marks a range there after a hold, so a drag without
     * one is the page's, and scrolls what the chart is in.
     */
    @Test
    fun aChartWithOnlyARangeLeavesAPlainDragToItsParent() = runComposeUiTest {
        val scroll = ScrollState(0)
        setContent {
            KontourTheme(reduceMotion = true) {
                Row(Modifier.size(300.dp, 240.dp).horizontalScroll(scroll).testTag("row")) {
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.size(600.dp, 240.dp),
                        rangeSelection = rememberChartRangeSelectionState(),
                        showLegend = false,
                        animateData = false,
                    ) {
                        bar(spending, x = { it.month }, y = { it.amount })
                    }
                }
            }
        }
        onNodeWithTag("row").performTouchInput {
            swipe(Offset(width * 0.8f, height * 0.5f), Offset(width * 0.2f, height * 0.5f), durationMillis = 300)
        }
        waitForIdle()
        assertTrue(scroll.value > 0, "a plain drag across a chart that only marks ranges should scroll its parent")
    }

    /**
     * With nothing to choose, the arrows, Home and End are not the chart's:
     * they go on to what is round it. Shift and an arrow still stretch a range.
     */
    @Test
    fun aChartWithOnlyARangeLeavesThePlainArrowsToItsParent() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        val heard = mutableListOf<Key>()
        setContent {
            KontourTheme(reduceMotion = true) {
                Box(Modifier.onKeyEvent { if (it.type == KeyEventType.KeyDown) heard += it.key; false }) {
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.size(360.dp, 240.dp),
                        rangeSelection = range,
                        showLegend = false,
                        animateData = false,
                    ) {
                        bar(spending, x = { it.month }, y = { it.amount })
                    }
                }
            }
        }
        val chart = onNode(isFocusable())
        chart.requestFocus()
        chart.performKeyInput { pressKey(Key.DirectionRight) }
        chart.performKeyInput { pressKey(Key.MoveEnd) }
        assertTrue(Key.DirectionRight in heard, "Right should go on to the parent: $heard")
        assertTrue(Key.MoveEnd in heard, "End should go on to the parent: $heard")
        assertTrue(range.isEmpty)
    }

    /**
     * Right to left, with nothing chosen, Right starts from the far end of the
     * reading, as Left does left to right: the last place. It started from the
     * first, which is under the reader's hand there.
     */
    @Test
    fun rightToLeftRightStartsFromTheLastPlace() = runComposeUiTest {
        val selection = ChartSelectionState<String>(null)
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Chart(
                            xAxis = ChartAxis.category<String>(),
                            yAxis = ChartAxis.number(),
                            modifier = Modifier.size(360.dp, 240.dp),
                            selection = selection,
                            showLegend = false,
                            animateData = false,
                        ) {
                            bar(spending, x = { it.month }, y = { it.amount })
                        }
                    }
                }
            }
        }
        val chart = onNode(isFocusable())
        chart.requestFocus()
        chart.performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals("Jun", selection.x)
        chart.performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals("May", selection.x)
    }

    /**
     * Two sectors hidden one after the other, the second while the first's
     * sweep is still going: the pie goes on from where it is drawn. It went
     * back to where it was before the first, and swept from there, since what
     * it swept from was written only once a sweep had finished.
     */
    @Test
    fun aPieToggledTwiceInASweepGoesOnFromWhereItIs() = runComposeUiTest {
        val visibility = ChartSeriesVisibilityState(emptySet())
        val a = ChartSeries("A")
        val b = ChartSeries("B")
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(reduceMotion = false) {
                SectorChart(
                    modifier = Modifier.size(200.dp).testTag("pie"),
                    seriesVisibility = visibility,
                    colours = ChartDefaults.colours(series = listOf(InkA, InkB, InkC)),
                    showLegend = false,
                    showPatterns = false,
                ) {
                    sectors(listOf("A" to 1, "B" to 1, "C" to 1), value = { it.second }, series = { ChartSeries(it.first) })
                }
            }
        }
        repeat(120) { mainClock.advanceTimeByFrame() }
        val whole = startOfC()
        assertTrue(abs(whole - 240) <= 3, "C should start two thirds of the way round, not at $whole")

        visibility.hide(a)
        repeat(8) { mainClock.advanceTimeByFrame() }
        val before = startOfC()
        assertTrue(before in 185..232, "A's sweep should be part of the way: C starts at $before")

        visibility.hide(b)
        repeat(2) { mainClock.advanceTimeByFrame() }
        val after = startOfC()
        assertTrue(abs(after - before) <= 8, "the pie went back to where it was before the first toggle: C started at $before, then at $after")
    }

    /** How far round from the top, clockwise, C's sector starts, in degrees: the first of it from a third of the way round. */
    private fun ComposeUiTest.startOfC(): Int {
        val pixels = onNodeWithTag("pie").captureToImage().toPixelMap()
        return (120..359).first { degrees -> near(colourAt(pixels, degrees), InkC) }
    }

    private fun colourAt(pixels: PixelMap, degrees: Int): Color {
        val radius = min(pixels.width, pixels.height) / 2f * 0.7f
        val angle = (degrees - 90) * PI / 180
        val x = pixels.width / 2f + radius * cos(angle).toFloat()
        val y = pixels.height / 2f + radius * sin(angle).toFloat()
        return pixels[x.toInt(), y.toInt()]
    }

    private fun near(a: Color, b: Color) = abs(a.red - b.red) < 0.1f && abs(a.green - b.green) < 0.1f && abs(a.blue - b.blue) < 0.1f

    private companion object {
        val InkA = Color(0xFFD02020)
        val InkB = Color(0xFF20A040)
        val InkC = Color(0xFF2040D0)
    }
}
