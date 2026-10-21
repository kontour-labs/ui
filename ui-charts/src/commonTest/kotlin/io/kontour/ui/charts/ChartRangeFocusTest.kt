package io.kontour.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
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

    /**
     * Months along categories, as a line, as points, or as bars under a line:
     * the line and the points a flat 200 of 0 to 400, and the bars 100, so
     * the line runs clear above them.
     */
    private fun ComposeUiTest.months(
        range: ChartRangeSelectionState<String, Number>,
        selection: ChartSelectionState<String>? = null,
        content: ChartScope<String, Number>.() -> Unit,
    ) = chart {
        Chart(
            xAxis = ChartAxis.category<String>(),
            yAxis = ChartAxis.number(domain = 0.0..400.0),
            modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
            selection = selection,
            rangeSelection = range,
            colours = colours(),
            showLegend = false,
            animateData = false,
            content = content,
        )
    }

    private val flat = months.map { Spend(it, 200.0) }
    private val low = months.map { Spend(it, 100.0) }

    /** [share] of the way across [month]'s band, in the chart's pixels, from its place's node. */
    private fun ComposeUiTest.into(month: String, share: Float): Float {
        val chart = onNodeWithTag("chart").fetchSemanticsNode().boundsInRoot
        val node = onNode(SemanticsMatcher("$month's place") { node ->
            SemanticsProperties.ContentDescription in node.config &&
                node.config[SemanticsProperties.ContentDescription].any { it.startsWith("$month: ") }
        }).fetchSemanticsNode().boundsInRoot
        return node.left - chart.left + (node.right - node.left) * share
    }

    /** A mouse pressed at [from] and moved to [to] along the middle of the chart, and let go unless [hold]. */
    private fun ComposeUiTest.drag(from: Float, to: Float, hold: Boolean = false) {
        onNodeWithTag("chart").performMouseInput {
            moveTo(Offset(from, height * 0.5f))
            press()
            for (step in 1..10) moveTo(Offset(from + (to - from) * step / 10f, height * 0.5f))
            if (!hold) release()
        }
        waitForIdle()
    }

    /** The range's drawn edges, along a row high in the plot, clear of the marks. */
    private fun edgesOf(pixels: PixelMap): Pair<Int, Int> {
        val row = (pixels.height * 0.15f).toInt()
        val edges = (0 until pixels.width).filter { near(pixels[it, row], Probe) }
        assertTrue(edges.isNotEmpty(), "no range was drawn")
        return edges.first() to edges.last()
    }

    /** The line's strength down column [x], where it is strongest: above a mixed chart's bars. */
    private fun lineAt(pixels: PixelMap, x: Int): Float = ((pixels.height * 0.25f).toInt()..(pixels.height * 0.6f).toInt()).maxOf { y ->
        pixels[x, y].takeIf { inkish(it) }?.let(::strength) ?: 0f
    }

    /** The strongest ink within a few pixels of [x], down the middle of the plot: a point there. */
    private fun pointAt(pixels: PixelMap, x: Int): Float = (x - 3..x + 3).maxOf { lineAt(pixels, it) }

    /**
     * Along categories a line is whole exactly between the range's drawn
     * edges, as it is on any other axis: *"it'd be cool if the highlight
     * stopped exactly at the boundaries of the selection"*. It was whole out
     * to the near edge of February's band, though the drag began most of the
     * way across it.
     */
    @Test
    fun aLineAlongCategoriesIsWholeExactlyToTheDragsEdges() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        months(range) { line(flat, x = { it.month }, y = { it.amount }) }
        drag(into("Feb", 0.7f), into("Apr", 0.3f))
        val pixels = pixels()
        val (left, right) = edgesOf(pixels)
        for (x in listOf(left + 4, (left + right) / 2, right - 4)) {
            assertTrue(abs(lineAt(pixels, x) - 1f) < 0.08f, "inside the range the line should be whole, not ${lineAt(pixels, x)} at $x ($left to $right)")
        }
        for (x in listOf(left - 4, left - 10, right + 4, right + 10)) {
            assertTrue(abs(lineAt(pixels, x) - 0.6f) < 0.08f, "just outside it the line should be at 60%, not ${lineAt(pixels, x)} at $x ($left to $right)")
        }
    }

    /**
     * A point is inside a range where it is, not where its category's band
     * is: a drag from past February's point leaves it out, though it reaches
     * into February's band, and what is reported is what is whole. *"not
     * selecting points and stuff that aren't explicitly inside the
     * selection"*.
     */
    @Test
    fun aPointWhoseBandTheRangeReachesIntoButWhosePlaceItMissesFades() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        months(range) { point(flat, x = { it.month }, y = { it.amount }) }
        drag(into("Feb", 0.7f), into("Apr", 0.7f))
        val pixels = pixels()
        assertTrue(abs(pointAt(pixels, middleOf("Feb")) - 0.6f) < 0.08f, "February's point is outside the drag: ${pointAt(pixels, middleOf("Feb"))}")
        assertTrue(abs(pointAt(pixels, middleOf("Mar")) - 1f) < 0.08f, "March's is inside it: ${pointAt(pixels, middleOf("Mar"))}")
        assertTrue(abs(pointAt(pixels, middleOf("Apr")) - 1f) < 0.08f, "and so is April's: ${pointAt(pixels, middleOf("Apr"))}")
        assertTrue(abs(pointAt(pixels, middleOf("May")) - 0.6f) < 0.08f, "May's is not: ${pointAt(pixels, middleOf("May"))}")
        assertEquals("Mar", range.xStart, "the range starts at the first place inside it")
        assertEquals("Apr", range.xEnd)
    }

    /**
     * Bars and a line together: the bars are taken by their bands, so a drag
     * from most of the way across February reports February and its bar is
     * whole, while the line is whole only from where the drag began.
     */
    @Test
    fun aMixedChartTakesBarsByTheirBandsAndCutsTheLineAtTheEdges() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        months(range) {
            bar(low, x = { it.month }, y = { it.amount })
            line(flat, x = { it.month }, y = { it.amount })
        }
        drag(into("Feb", 0.7f), into("Apr", 0.3f))
        assertEquals("Feb", range.xStart, "the bars' bands decide what a mixed chart reports")
        assertEquals("Apr", range.xEnd)
        val pixels = pixels()
        assertTrue(abs(barStrength("Feb", pixels) - 1f) < 0.05f, "February's bar is in it: ${barStrength("Feb", pixels)}")
        assertTrue(abs(barStrength("Apr", pixels) - 1f) < 0.05f, "and April's: ${barStrength("Apr", pixels)}")
        assertTrue(abs(barStrength("May", pixels) - 0.6f) < 0.05f, "May's is not: ${barStrength("May", pixels)}")
        val (left, right) = edgesOf(pixels)
        assertTrue(abs(lineAt(pixels, left + 4) - 1f) < 0.08f, "the line is whole just inside the drag: ${lineAt(pixels, left + 4)}")
        assertTrue(abs(lineAt(pixels, left - 4) - 0.6f) < 0.08f, "and faded just before it, over February's bar: ${lineAt(pixels, left - 4)}")
        assertTrue(abs(lineAt(pixels, right + 4) - 0.6f) < 0.08f, "and just past it, over April's: ${lineAt(pixels, right + 4)}")
    }

    /** Bars alone are taken by their bands: a drag from late in February's band takes its bar, and reports it. */
    @Test
    fun aDragAcrossBarsTakesEachBarWhoseBandItReachesInto() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        bars(range)
        drag(into("Feb", 0.85f), into("Apr", 0.15f))
        assertEquals("Feb", range.xStart)
        assertEquals("Apr", range.xEnd)
        val pixels = pixels()
        assertTrue(abs(barStrength("Feb", pixels) - 1f) < 0.05f, "February's bar is in it: ${barStrength("Feb", pixels)}")
        assertTrue(abs(barStrength("Apr", pixels) - 1f) < 0.05f, "and April's: ${barStrength("Apr", pixels)}")
        assertTrue(abs(barStrength("Jan", pixels) - 0.6f) < 0.05f, "January's is not: ${barStrength("Jan", pixels)}")
        assertTrue(abs(barStrength("May", pixels) - 0.6f) < 0.05f, "nor May's: ${barStrength("May", pixels)}")
    }

    /** Shift and the arrows take whole places, and what they report is what is whole. */
    @Test
    fun theKeysHighlightThePointsTheyReport() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        val selection = ChartSelectionState<String>(null)
        months(range, selection) { point(flat, x = { it.month }, y = { it.amount }) }
        val chart = onNode(isFocusable())
        chart.requestFocus()
        chart.performKeyInput { pressKey(Key.DirectionRight) }
        chart.performKeyInput { pressKey(Key.DirectionRight) }
        chart.performKeyInput {
            withKeyDown(Key.ShiftLeft) {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionRight)
            }
        }
        waitForIdle()
        assertEquals("Feb", range.xStart)
        assertEquals("Apr", range.xEnd)
        val pixels = pixels()
        for (month in listOf("Feb", "Mar", "Apr")) {
            assertTrue(abs(pointAt(pixels, middleOf(month)) - 1f) < 0.08f, "$month's point is in the range: ${pointAt(pixels, middleOf(month))}")
        }
        for (month in listOf("Jan", "May")) {
            assertTrue(abs(pointAt(pixels, middleOf(month)) - 0.6f) < 0.08f, "$month's is not: ${pointAt(pixels, middleOf(month))}")
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
