package io.kontour.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What is outside a marked range fades, so what is inside stands out: asked
 * for as *"i'd like to subtly highlight the sections of chart we do have
 * selected"*, and chosen as the marks outside fading a little, with the wash
 * behind the range kept.
 */
@OptIn(ExperimentalTestApi::class)
class ChartRangeFocusTest {

    private class Spend(val month: String, val amount: Double)

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun")
    private val spending = months.map { Spend(it, 300.0) }

    private fun ComposeUiTest.chart(reduceMotion: Boolean = true, content: @Composable () -> Unit) {
        setContent {
            KontourTheme(reduceMotion = reduceMotion) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.background(Ground)) { content() }
                }
            }
        }
    }

    @Composable
    private fun colours(): ChartColours =
        ChartDefaults.colours(series = listOf(Ink), grid = Color.Transparent, selection = Probe, ground = Ground)

    private fun ComposeUiTest.bars(range: ChartRangeSelectionState<String, Number>, reduceMotion: Boolean = true) = chart(reduceMotion) {
        Chart(
            xAxis = ChartAxis.category<String>(),
            yAxis = ChartAxis.number(domain = 0.0..400.0),
            modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
            rangeSelection = range,
            colours = colours(),
            showLegend = false,
            animateData = false,
        ) {
            bar(spending, x = { it.month }, y = { it.amount })
        }
    }

    /** Where a place's node is across the chart, and so the middle of its bar. */
    private fun ComposeUiTest.middleOf(month: String): Int {
        val chart = onNodeWithTag("chart").fetchSemanticsNode().boundsInRoot
        val node = onNode(SemanticsMatcher("$month's place") { node ->
            SemanticsProperties.ContentDescription in node.config &&
                node.config[SemanticsProperties.ContentDescription].any { it.startsWith("$month: ") }
        }).fetchSemanticsNode().boundsInRoot
        return ((node.left + node.right) / 2 - chart.left).toInt()
    }

    private fun ComposeUiTest.pixels(): PixelMap = onNodeWithTag("chart").captureToImage().toPixelMap()

    /** How strongly [colour] is [Ink] over [Ground]: 1 for the ink itself, 0 for the ground. */
    private fun strength(colour: Color): Float {
        // Along the channel the two differ most in.
        val red = (colour.red - Ground.red) / (Ink.red - Ground.red)
        val green = (colour.green - Ground.green) / (Ink.green - Ground.green)
        return if (abs(Ink.red - Ground.red) > abs(Ink.green - Ground.green)) red else green
    }

    /** A bar's strength low down, clear of the range's edge and of its corners. */
    private fun ComposeUiTest.barStrength(month: String, pixels: PixelMap = pixels()): Float =
        strength(pixels[middleOf(month), (pixels.height * 0.8f).toInt()])

    @Test
    fun aBarOutsideTheRangeFadesAndOneInsideDoesNot() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        bars(range)
        assertTrue(abs(barStrength("Jan") - 1f) < 0.05f, "with nothing marked every bar is whole: ${barStrength("Jan")}")
        range.select("Feb", "Mar")
        waitForIdle()
        val pixels = pixels()
        val inside = barStrength("Feb", pixels)
        val outside = barStrength("May", pixels)
        assertTrue(abs(inside - 1f) < 0.05f, "a bar in the range should be whole, not $inside")
        assertTrue(abs(outside - 0.6f) < 0.05f, "a bar outside it should be at 60%, not $outside")
        assertTrue(abs(barStrength("Jan", pixels) - 0.6f) < 0.05f, "on either side of it")
        range.clear()
        waitForIdle()
        assertTrue(abs(barStrength("May") - 1f) < 0.05f, "let go, every bar is whole again")
    }

    /**
     * A drag from most of the way across February's band is read as from
     * February, so February's bar is in it, though most of it is outside
     * where the drag went.
     */
    @Test
    fun aBarIsInsideARangeThatReachesIntoItsBand() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        bars(range)
        // As a drag marks it, in the axis's own units: Feb is place 1.
        range.mark(1.4, 2.2, null, null, "Feb", "Mar", null, null)
        waitForIdle()
        assertTrue(abs(barStrength("Feb") - 1f) < 0.05f, "February is in the range it reports, and so whole: ${barStrength("Feb")}")
        assertTrue(abs(barStrength("Apr") - 0.6f) < 0.05f, "April is not: ${barStrength("Apr")}")
    }

    /** The fade comes in over a few frames, and goes again as the range is let go: no pop. */
    @Test
    fun theFadeComesInAndGoesAgain() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        bars(range, reduceMotion = false)
        waitForIdle()
        mainClock.autoAdvance = false
        range.select("Feb", "Mar")
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val early = barStrength("May")
        assertTrue(early > 0.62f && early < 0.98f, "a few frames in, a bar outside should be on its way: $early")
        mainClock.advanceTimeBy(1_000)
        assertTrue(abs(barStrength("May") - 0.6f) < 0.05f, "and then faded")
        range.clear()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val back = barStrength("May")
        assertTrue(back > 0.62f && back < 0.98f, "let go, it should come back over a few frames: $back")
        mainClock.advanceTimeBy(1_000)
        assertTrue(abs(barStrength("May") - 1f) < 0.05f, "and then be whole")
    }

    /** A line is cut at the range's edges: whole between them, faded outside. */
    @Test
    fun aLineIsSplitAtTheRangesEdges() = runComposeUiTest {
        val range = ChartRangeSelectionState<Number, Number>(ChartRangeMode.Across)
        chart {
            Chart(
                xAxis = ChartAxis.number(domain = 0.0..10.0),
                yAxis = ChartAxis.number(domain = 0.0..10.0),
                modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
                rangeSelection = range,
                colours = colours(),
                showLegend = false,
                animateData = false,
            ) {
                line((0..10).toList(), x = { it }, y = { 5 })
            }
        }
        range.select(4.0, 6.0)
        waitForIdle()
        val pixels = pixels()
        // The range's edges, along a row above the line.
        val edgeRow = (pixels.height * 0.2f).toInt()
        val edges = (0 until pixels.width).filter { near(pixels[it, edgeRow], Probe) }
        assertTrue(edges.isNotEmpty(), "no range was drawn")
        val left = edges.first()
        val right = edges.last()
        // Down each column through the middle of the plot, clear of the
        // labels, the line where it is strongest.
        fun lineAt(x: Int): Float = ((pixels.height * 0.3f).toInt()..(pixels.height * 0.7f).toInt()).maxOf { y ->
            pixels[x, y].takeIf { inkish(it) }?.let(::strength) ?: 0f
        }
        for (x in left + 3..right - 3) {
            assertTrue(abs(lineAt(x) - 1f) < 0.08f, "inside the range the line should be whole, not ${lineAt(x)} at $x ($left to $right)")
        }
        for (x in listOf(left - 12, left - 4, right + 4, right + 12)) {
            assertTrue(abs(lineAt(x) - 0.6f) < 0.08f, "outside it the line should be at 60%, not ${lineAt(x)} at $x ($left to $right)")
        }
    }

    private class Day(val warmth: Double, val use: Double)

    /**
     * In a box, a point is inside where both its values are: one beside the
     * box and one above it fade, though the one above is across the box's
     * width, and the one in it does not.
     */
    @Test
    fun aBoxFadesThePointsOutsideIt() = runComposeUiTest {
        val range = ChartRangeSelectionState<Number, Number>(ChartRangeMode.Box)
        val days = listOf(Day(25.0, 10.0), Day(25.0, 18.0), Day(5.0, 10.0))
        chart {
            Chart(
                xAxis = ChartAxis.number(domain = 0.0..50.0),
                yAxis = ChartAxis.number(domain = 0.0..20.0),
                modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
                rangeSelection = range,
                colours = colours(),
                showLegend = false,
                animateData = false,
            ) {
                point(days, x = { it.warmth }, y = { it.use })
            }
        }
        range.select(20.0, 30.0, 5.0, 15.0)
        waitForIdle()
        val pixels = pixels()
        // The box's edges, along a row and down a column through its middle.
        val across = (0 until pixels.width).filter { near(pixels[it, (pixels.height * 0.5f).toInt()], Probe) }
        assertTrue(across.isNotEmpty(), "no box was drawn")
        val middle = (across.first() + across.last()) / 2
        val up = (0 until pixels.height).filter { near(pixels[middle, it], Probe) }
        val top = up.first()
        val bottom = up.last()
        // Each point's strongest pixel, by where it is: in the box, above it, or to its left.
        var inBox = 0f
        var above = 0f
        var beside = 0f
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val colour = pixels[x, y]
            if (near(colour, Probe) || !inkish(colour)) continue
            val s = strength(colour)
            when {
                x in across.first()..across.last() && y in top..bottom -> inBox = max(inBox, s)
                x in across.first()..across.last() && y < top -> above = max(above, s)
                x < across.first() -> beside = max(beside, s)
            }
        }
        assertTrue(abs(inBox - 1f) < 0.08f, "the point in the box should be whole, not $inBox")
        assertTrue(abs(above - 0.6f) < 0.08f, "the one above it, inside it across, should be at 60%, not $above")
        assertTrue(abs(beside - 0.6f) < 0.08f, "the one beside it, inside it up, should be at 60%, not $beside")
    }

    /** Whether [colour] is the ink at some strength over the ground, rather than a label's grey or the range's wash. */
    private fun inkish(colour: Color): Boolean {
        val s = strength(colour)
        if (s < 0.3f) return false
        val expected = Color(
            Ground.red + (Ink.red - Ground.red) * s,
            Ground.green + (Ink.green - Ground.green) * s,
            Ground.blue + (Ink.blue - Ground.blue) * s,
        )
        return near(colour, expected, 0.04f)
    }

    private fun near(a: Color, b: Color, by: Float = 0.3f) = abs(a.red - b.red) < by && abs(a.green - b.green) < by && abs(a.blue - b.blue) < by

    private companion object {
        val Probe = Color(0xFFFF00FF)
        val Ink = Color(0xFF2060C0)
        val Ground = Color.White
    }
}
