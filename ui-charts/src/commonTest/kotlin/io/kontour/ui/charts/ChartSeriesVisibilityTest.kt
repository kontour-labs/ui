package io.kontour.ui.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A legend whose entries hide and show their series: a tap takes a series off
 * the chart and out of what a screen reader hears, keeps its entry, faded, to
 * bring it back, and leaves every other series its colour.
 *
 * Asked for as *"the option to hide a series when you tap its box or label in
 * the legend"*.
 */
@OptIn(ExperimentalTestApi::class)
class ChartSeriesVisibilityTest {

    private class Riders(val day: Int, val route: String, val riders: Int)

    private val week = listOf(
        Riders(1, "950", 640), Riders(1, "951", 410),
        Riders(2, "950", 720), Riders(2, "951", 380),
        Riders(3, "950", 690), Riders(3, "951", 1_455),
    )

    private fun androidx.compose.ui.test.ComposeUiTest.lines(visibility: ChartSeriesVisibility?) {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Chart(
                        xAxis = ChartAxis.number(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.size(400.dp, 300.dp).testTag("chart"),
                        seriesVisibility = visibility,
                        animateData = false,
                    ) {
                        line(week, x = { it.day }, y = { it.riders }, series = { ChartSeries("Route ${it.route}") })
                    }
                }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.summary(): String =
        onNodeWithTag("chart").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()

    @Test
    fun aTapOnALegendEntryHidesItsSeriesAndAnotherShowsIt() = runComposeUiTest {
        val visibility = ChartSeriesVisibility(emptySet())
        lines(visibility)
        assertTrue("Route 951" in summary(), "both routes should be said to start with: ${summary()}")

        val entry = onNode(isToggleable() and hasText("Route 951"))
        entry.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        entry.performClick()
        assertEquals(setOf(ChartSeries("Route 951")), visibility.hidden)
        assertFalse("Route 951" in summary(), "a hidden series should not be said: ${summary()}")
        // Its peak of 1,455 went with it, and the axis closed in on what is left.
        assertFalse("1,455" in summary() || "1,500" in summary(), "the axis should no longer reach the hidden peak: ${summary()}")
        entry.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))

        entry.performClick()
        assertTrue(visibility.hidden.isEmpty())
        assertTrue("Route 951" in summary(), "shown again, it should be said again: ${summary()}")
    }

    /** Without a visibility, the legend is not read: the summary names every series already. */
    @Test
    fun withoutOneTheLegendIsNotASwitch() = runComposeUiTest {
        lines(visibility = null)
        assertEquals(0, onAllNodes(isToggleable()).fetchSemanticsNodes().size)
    }

    /** A hidden series keeps its slot, so every other series keeps its colour and patterns. */
    @Test
    fun hidingASeriesLeavesTheOthersTheirSlots() {
        val a = ChartSeries("A")
        val b = ChartSeries("B")
        val c = ChartSeries("C")
        val run = { series: ChartSeries -> Run(series, doubleArrayOf(0.0), doubleArrayOf(1.0), null, null) }
        val model = ChartModel(
            marks = listOf(
                Mark(MarkKind.Line, listOf(run(a), run(b), run(c)), ChartStacking.None, ChartInterpolation.Linear, false, null, null),
            ),
            rules = emptyList(),
            regions = emptyList(),
            series = listOf(a, b, c),
            xCategories = emptyList(),
            yCategories = emptyList(),
        )
        val shown = model.without(setOf(b))
        assertEquals(listOf(a, c), shown.marks.single().runs.map { it.series })
        assertEquals(2, shown.slotOf(c), "C should keep the third slot, and its colour, with B hidden")
    }

    /** A hidden sector leaves the circle, and the others grow into its share. */
    @Test
    fun aHiddenSectorsShareGoesToTheOthers() = runComposeUiTest {
        val visibility = ChartSeriesVisibility(emptySet())
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Column {
                        SectorChart(
                            modifier = Modifier.size(300.dp, 200.dp),
                            selection = ChartSelectionState(null),
                            seriesVisibility = visibility,
                            animateData = false,
                        ) {
                            sectors(listOf("Rent" to 50, "Food" to 30, "Fun" to 20), value = { it.second }, series = { ChartSeries(it.first) })
                        }
                    }
                }
            }
        }
        onNodeWithContentDescription("Rent, 50%").assertExists()
        onNode(isToggleable() and hasText("Rent")).performClick()
        assertEquals(0, onAllNodesWithContentDescription("Rent", substring = true).fetchSemanticsNodes().size)
        onNodeWithContentDescription("Food, 60%").assertExists()
        onNodeWithContentDescription("Fun, 40%").assertExists()
    }
}
