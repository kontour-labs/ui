package io.kontour.ui.charts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A range marked on a chart, across it or as a box: asked for as *"can we add
 * the ability to select regions of the chart? obviously again, this should be
 * optional. it should also be possible to make it just a left/right selection,
 * or a box selection"*.
 */
@OptIn(ExperimentalTestApi::class)
class ChartRangeSelectionTest {

    private class Spend(val month: String, val amount: Double)

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun")
    private val spending = months.mapIndexed { i, month -> Spend(month, 100.0 + i * 20) }

    private class Day(val temperature: Double, val use: Double)

    private val days = (0 until 30).map { i -> Day(10.0 + i, 5.0 + (i % 7)) }

    private fun ComposeUiTest.bars(
        range: ChartRangeSelectionState<String, Number>,
        selection: ChartSelectionState<String>? = null,
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
                        showLegend = false,
                        animateData = false,
                    ) {
                        bar(spending, x = { it.month }, y = { it.amount })
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.scatter(range: ChartRangeSelectionState<Number, Number>) {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Chart(
                        xAxis = ChartAxis.number(domain = 0.0..50.0),
                        yAxis = ChartAxis.number(domain = 0.0..20.0),
                        modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
                        rangeSelection = range,
                        showLegend = false,
                        animateData = false,
                    ) {
                        point(days, x = { it.temperature }, y = { it.use })
                    }
                }
            }
        }
    }

    @Test
    fun aMouseDragMarksWholePlacesAcrossBars() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        bars(range)
        onNodeWithTag("chart").performMouseInput {
            // From over Feb to over Apr.
            moveTo(Offset(width * 0.3f, height * 0.5f))
            press()
            for (step in 1..10) moveTo(Offset(width * (0.3f + 0.032f * step), height * 0.5f))
            release()
        }
        waitForIdle()
        assertTrue(range.xStart in listOf("Feb", "Jan"), "the range should start at the place it was dragged from: ${range.xStart}")
        assertTrue(range.xEnd in listOf("Apr", "May"), "and end at the place it was dragged to: ${range.xEnd}")
        assertNull(range.yStart, "a range across has no extent up")
    }

    @Test
    fun aFingerMarksAfterAHoldAndMovesThePlaceWithout() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        val selection = ChartSelectionState<String>(null)
        bars(range, selection)
        val chart = onNodeWithTag("chart")
        // A plain drag across moves the selected place, and marks nothing.
        chart.performTouchInput { swipe(Offset(width * 0.2f, height * 0.5f), Offset(width * 0.8f, height * 0.5f), durationMillis = 300) }
        waitForIdle()
        assertTrue(range.isEmpty, "a plain drag should not mark a range: ${range.xStart}..${range.xEnd}")
        assertTrue(selection.x != null, "it should move the selected place")
        // Held, then dragged: a range.
        chart.performTouchInput {
            down(Offset(width * 0.2f, height * 0.5f))
            advanceEventTime(800)
            for (step in 1..12) moveTo(Offset(width * (0.2f + 0.05f * step), height * 0.5f))
            up()
        }
        waitForIdle()
        assertTrue(!range.isEmpty, "a held drag should mark a range")
        assertTrue(range.xStart != range.xEnd, "across more than one place: ${range.xStart}..${range.xEnd}")
    }

    @Test
    fun aBoxIsMarkedAcrossAndUpInOrder() = runComposeUiTest {
        val range = ChartRangeSelectionState<Number, Number>(ChartRangeMode.Box)
        scatter(range)
        onNodeWithTag("chart").performMouseInput {
            // From low right to high left, so both ends come in the wrong order.
            moveTo(Offset(width * 0.8f, height * 0.7f))
            press()
            for (step in 1..10) moveTo(Offset(width * (0.8f - 0.04f * step), height * (0.7f - 0.04f * step)))
            release()
        }
        waitForIdle()
        val xStart = range.xStart!!.toDouble()
        val xEnd = range.xEnd!!.toDouble()
        val yStart = range.yStart!!.toDouble()
        val yEnd = range.yEnd!!.toDouble()
        assertTrue(xStart < xEnd, "across in order: $xStart..$xEnd")
        assertTrue(yStart < yEnd, "up in order: $yStart..$yEnd")
        assertTrue(xEnd - xStart > 5, "and as wide as the drag: $xStart..$xEnd")
        assertTrue(yEnd - yStart > 2, "and as tall: $yStart..$yEnd")
    }

    @Test
    fun aBoxAlongCategoriesIsARangeAcross() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Box)
        bars(range)
        onNodeWithTag("chart").performMouseInput {
            moveTo(Offset(width * 0.3f, height * 0.7f))
            press()
            for (step in 1..10) moveTo(Offset(width * (0.3f + 0.03f * step), height * (0.7f - 0.04f * step)))
            release()
        }
        waitForIdle()
        assertTrue(!range.isEmpty, "the drag should mark a range")
        assertNull(range.yStart, "a box needs a measure up and across; along categories it is a range across")
    }

    @Test
    fun theKeysStretchARangeFromTheSelectedPlaceAndEscapeLetsGo() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        val selection = ChartSelectionState<String>(null)
        bars(range, selection)
        val chart = onNode(isFocusable())
        chart.requestFocus()
        chart.performKeyInput { pressKey(Key.DirectionRight) }
        chart.performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals("Feb", selection.x)
        chart.performKeyInput {
            withKeyDown(Key.ShiftLeft) {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionRight)
            }
        }
        assertEquals("Feb", range.xStart)
        assertEquals("Apr", range.xEnd)
        chart.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) } }
        assertEquals("Mar", range.xEnd, "back a place")
        val said = chart.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertTrue("Selected Feb to Mar" in said, "the range should be said: $said")
        chart.performKeyInput { pressKey(Key.Escape) }
        assertTrue(range.isEmpty, "Escape lets go of the range first")
        assertEquals("Feb", selection.x, "and leaves the place")
        chart.performKeyInput { pressKey(Key.Escape) }
        assertNull(selection.x, "then the place")
    }

    @Test
    fun aTapLetsGoOfTheRange() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        bars(range)
        range.select("Feb", "Apr")
        waitForIdle()
        onNodeWithTag("chart").performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        waitForIdle()
        assertTrue(range.isEmpty, "a tap should let go of the range")
    }

    @Test
    fun aChartWithOnlyARangeChoosesNoPlace() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        bars(range)
        // Its places are not buttons: there is no place to choose.
        onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnClick)).fetchSemanticsNodes().let {
            assertEquals(0, it.size, "no place should offer to be chosen")
        }
    }
}
