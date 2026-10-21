package io.kontour.ui.charts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A chart whose pointer selects by clicking and not by hovering, asked for as
 * *"how do i disable the hover bar that comes up in charts? even if i turn
 * tooltips off, it's still on. because it doesn't really work if you also have
 * the range select"*.
 */
@OptIn(ExperimentalTestApi::class)
class ChartHoverTest {

    private class Spend(val month: String, val amount: Double)

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun")
    private val spending = months.mapIndexed { i, month -> Spend(month, 100.0 + i * 20) }

    private fun ComposeUiTest.line(
        selection: ChartSelectionState<String>,
        range: ChartRangeSelectionState<String, Number>? = null,
        selectOnHover: Boolean? = null,
    ) {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
                        selection = selection,
                        rangeSelection = range,
                        selectOnHover = selectOnHover ?: (range == null),
                        colours = ChartDefaults.colours(series = listOf(Line), grid = Color.Transparent, selection = Probe),
                        showLegend = false,
                        animateData = false,
                    ) {
                        line(spending, x = { it.month }, y = { it.amount })
                    }
                }
            }
        }
    }

    /** Whether anything is drawn in the selection's colour: its line through a place, or a range's edge. */
    private fun ComposeUiTest.drawsSelection(): Boolean {
        val pixels = onNodeWithTag("chart").captureToImage().toPixelMap()
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) if (near(pixels[x, y], Probe)) return true
        return false
    }

    /** A mouse moved across the chart a step at a time, resting at each. */
    private fun ComposeUiTest.hoverAcross() {
        val chart = onNodeWithTag("chart")
        for (step in 1..9) {
            chart.performMouseInput { moveTo(Offset(width * step / 10f, height * 0.5f)) }
            mainClock.advanceTimeBy(600)
        }
        waitForIdle()
    }

    /** Off, a mouse over the chart selects nothing, and draws nothing for it: no line, and so no tooltip. */
    @Test
    fun aMouseOverAChartThatDoesNotSelectOnHoverSelectsNothing() = runComposeUiTest {
        val selection = ChartSelectionState<String>(null)
        line(selection, selectOnHover = false)
        hoverAcross()
        assertNull(selection.x, "hovering should select nothing")
        assertTrue(!drawsSelection(), "and draw no line through a place")
        val said = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes()
            .count { it.config[SemanticsProperties.Selected] }
        assertEquals(0, said, "no place should be said to be selected")
    }

    /** A click still selects, and a mouse moving on afterwards leaves the place where the click put it. */
    @Test
    fun aClickStillSelectsAndTheMouseMovingOnLeavesIt() = runComposeUiTest {
        val selection = ChartSelectionState<String>(null)
        line(selection, selectOnHover = false)
        val chart = onNodeWithTag("chart")
        chart.performMouseInput {
            moveTo(Offset(width * 0.5f, height * 0.5f))
            click(Offset(width * 0.5f, height * 0.5f))
        }
        waitForIdle()
        val clicked = assertNotNull(selection.x, "a click should select")
        assertTrue(drawsSelection(), "and draw the selected place's line")
        hoverAcross()
        assertEquals(clicked, selection.x, "moving the mouse on should leave the clicked place selected")
        chart.performMouseInput { moveTo(Offset(width * 2f, height * 2f)) }
        waitForIdle()
        assertEquals(clicked, selection.x, "and so should leaving the chart")
    }

    /** By default a chart that marks ranges does not select by hovering, and one that does not mark them does. */
    @Test
    fun aChartThatMarksRangesDoesNotSelectOnHoverByDefault() = runComposeUiTest {
        val selection = ChartSelectionState<String>(null)
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        line(selection, range)
        hoverAcross()
        assertNull(selection.x, "hovering over a chart that marks ranges should select nothing by default")
    }

    @Test
    fun aChartThatMarksNoRangesSelectOnHoverByDefault() = runComposeUiTest {
        val selection = ChartSelectionState<String>(null)
        line(selection)
        hoverAcross()
        assertNotNull(selection.x, "hovering should select, as it always has")
    }

    /**
     * Selecting on hover and marking a range together, a drag marks its range
     * with nothing selected under it: from the press, which is a click on its
     * way or the start of a drag, and not a hover. Before it was far enough to
     * be a drag, the place under the pressed pointer moved with it.
     */
    @Test
    fun aDragMarksItsRangeUndisturbedBySelectingOnHover() = runComposeUiTest {
        val selection = ChartSelectionState<String>(null)
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        line(selection, range, selectOnHover = true)
        val chart = onNodeWithTag("chart")
        val root = chart.fetchSemanticsNode().boundsInRoot
        // Just short of where February's stretch gives way to March's.
        val feb = onNode(SemanticsMatcher("Feb's place") { node ->
            SemanticsProperties.ContentDescription in node.config &&
                node.config[SemanticsProperties.ContentDescription].any { it.startsWith("Feb: ") }
        }).fetchSemanticsNode().boundsInRoot
        val start = Offset(feb.right - root.left - 2f, (root.bottom - root.top) / 2)
        chart.performMouseInput { moveTo(start) }
        mainClock.advanceTimeBy(600)
        assertEquals("Feb", selection.x, "resting, the pointer selects what it is over")
        chart.performMouseInput {
            press()
            // Over into March's stretch, short of a drag.
            moveTo(start + Offset(4f, 0f))
        }
        waitForIdle()
        assertTrue(selection.x != "Mar", "a pressed pointer should not select what it is pressed over")
        chart.performMouseInput {
            for (step in 1..10) moveTo(start + Offset(16f * step, 0f))
        }
        waitForIdle()
        assertNull(selection.x, "marking a range lets go of the place")
        assertTrue(!range.isEmpty, "the drag should mark a range")
        chart.performMouseInput { release() }
        waitForIdle()
        assertNull(selection.x, "and nothing is selected once it is let go")
        // Begun past February's point, the line's range takes in March's
        // first: a line's range reports the places inside it, not the bands
        // it reaches into.
        assertEquals("Mar", range.xStart)
    }

    private fun near(a: Color, b: Color) = abs(a.red - b.red) < 0.15f && abs(a.green - b.green) < 0.15f && abs(a.blue - b.blue) < 0.15f

    private companion object {
        val Probe = Color(0xFFFF00FF)
        val Line = Color(0xFF2060C0)
    }
}
