package io.kontour.ui.charts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import kotlin.math.abs
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
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

    /** Where a place's node is across the chart: its band. */
    private fun ComposeUiTest.band(month: String): Pair<Float, Float> {
        val chart = onNodeWithTag("chart").fetchSemanticsNode().boundsInRoot
        val node = onNode(SemanticsMatcher("$month's place") { node ->
            SemanticsProperties.ContentDescription in node.config &&
                node.config[SemanticsProperties.ContentDescription].any { it.startsWith("$month: ") }
        }).fetchSemanticsNode().boundsInRoot
        return node.left - chart.left to node.right - chart.left
    }

    /** A mouse dragged across, from [from] to [to] in the chart's pixels, along [y] of its height. */
    private fun ComposeUiTest.drag(from: Float, to: Float, y: Float = 0.5f) {
        onNodeWithTag("chart").performMouseInput {
            moveTo(Offset(from, height * y))
            press()
            for (step in 1..10) moveTo(Offset(from + (to - from) * step / 10f, height * y))
            release()
        }
        waitForIdle()
    }

    /**
     * A drag across bars reports the months whose bands it reaches into: from
     * the far side of February's to the near side of April's is February to
     * April.
     */
    @Test
    fun aDragReportsThePlacesItReachesInto() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        bars(range)
        val feb = band("Feb")
        val apr = band("Apr")
        drag(feb.first + (feb.second - feb.first) * 0.85f, apr.first + (apr.second - apr.first) * 0.15f)
        assertEquals("Feb", range.xStart, "the range should start in the band it was dragged from")
        assertEquals("Apr", range.xEnd, "and end in the band it was let go in")
        assertNull(range.yStart, "a range across has no extent up")
    }

    /**
     * Among bars a drag's range is drawn from where it went down to where it
     * was let go. It was snapped to whole bars, so it jumped a band at a time
     * under the pointer: *"it should just start selecting from where i start
     * and stop where i stop"*.
     */
    @Test
    fun aDragAcrossBarsIsDrawnWhereItWent() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(domain = 0.0..400.0),
                        modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
                        rangeSelection = range,
                        colours = ChartDefaults.colours(series = listOf(Bars), grid = Color.Transparent, selection = Probe),
                        showLegend = false,
                        animateData = false,
                    ) {
                        bar(spending, x = { it.month }, y = { it.amount })
                    }
                }
            }
        }
        val feb = band("Feb")
        val apr = band("Apr")
        val from = feb.first + (feb.second - feb.first) * 0.7f
        val to = apr.first + (apr.second - apr.first) * 0.3f
        drag(from, to)
        // Above the bars, which reach no higher than a little over half way.
        val (left, right) = edgesAcross(0.2f)
        assertTrue(abs(left - from) <= 2f, "the range's start should be where the drag went down, $from, not at $left")
        assertTrue(abs(right - to) <= 2f, "and its end where it was let go, $to, not at $right")
    }

    /**
     * Along categories on a chart of points, what a drag reports is the
     * places inside it, not the bands it reaches into: one from past
     * February's point to short of March's takes in no place, so it reports
     * nothing. It is drawn while the drag goes on, so the reader sees what
     * they are marking, and let go it is gone, as nothing was marked.
     */
    @Test
    fun aDragBetweenTwoPointsReportsNothing() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(domain = 0.0..400.0),
                        modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
                        rangeSelection = range,
                        colours = ChartDefaults.colours(series = listOf(Bars), grid = Color.Transparent, selection = Probe),
                        showLegend = false,
                        animateData = false,
                    ) {
                        point(spending, x = { it.month }, y = { it.amount })
                    }
                }
            }
        }
        val feb = band("Feb")
        val mar = band("Mar")
        val from = feb.first + (feb.second - feb.first) * 0.6f
        val to = mar.first + (mar.second - mar.first) * 0.4f
        onNodeWithTag("chart").performMouseInput {
            moveTo(Offset(from, height * 0.5f))
            press()
            for (step in 1..10) moveTo(Offset(from + (to - from) * step / 10f, height * 0.5f))
        }
        waitForIdle()
        assertTrue(range.isEmpty, "no place is inside the drag, so none is reported: ${range.xStart}..${range.xEnd}")
        val (left, right) = edgesAcross(0.2f)
        assertTrue(abs(left - from) <= 2f && abs(right - to) <= 2f, "but the drag is drawn where it is, $from to $to, not $left to $right")
        onNodeWithTag("chart").performMouseInput { release() }
        waitForIdle()
        assertTrue(range.isEmpty, "let go, it is still nothing")
        val pixels = onNodeWithTag("chart").captureToImage().toPixelMap()
        val row = (pixels.height * 0.2f).toInt()
        assertTrue((0 until pixels.width).none { near(pixels[it, row], Probe) }, "and nothing is drawn")
    }

    /** A box on number axes, drawn from corner to corner where the drag went. */
    @Test
    fun aBoxIsDrawnWhereTheDragWent() = runComposeUiTest {
        val range = ChartRangeSelectionState<Number, Number>(ChartRangeMode.Box)
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Chart(
                        xAxis = ChartAxis.number(domain = 0.0..50.0),
                        yAxis = ChartAxis.number(domain = 0.0..20.0),
                        modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
                        rangeSelection = range,
                        colours = ChartDefaults.colours(series = listOf(Bars), grid = Color.Transparent, selection = Probe),
                        showLegend = false,
                        animateData = false,
                    ) {
                        point(listOf(Day(1.0, 1.0)), x = { it.temperature }, y = { it.use })
                    }
                }
            }
        }
        val chart = onNodeWithTag("chart").fetchSemanticsNode().boundsInRoot
        val width = chart.right - chart.left
        val height = chart.bottom - chart.top
        val start = Offset(width * 0.3f, height * 0.7f)
        val end = Offset(width * 0.65f, height * 0.35f)
        onNodeWithTag("chart").performMouseInput {
            moveTo(start)
            press()
            for (step in 1..10) moveTo(start + (end - start) * (step / 10f))
            release()
        }
        waitForIdle()
        val (left, right) = edgesAcross(0.5f)
        val (top, bottom) = edgesUp(0.5f)
        assertTrue(abs(left - start.x) <= 2f && abs(right - end.x) <= 2f, "across from ${start.x} to ${end.x}, not $left to $right")
        assertTrue(abs(top - end.y) <= 2f && abs(bottom - start.y) <= 2f, "up from ${start.y} to ${end.y}, not $bottom to $top")
    }

    /** The first and last columns of the range's edge along the row [y] of the way down the chart. */
    private fun ComposeUiTest.edgesAcross(y: Float): Pair<Float, Float> {
        val pixels = onNodeWithTag("chart").captureToImage().toPixelMap()
        val row = (pixels.height * y).toInt()
        val edge = (0 until pixels.width).filter { near(pixels[it, row], Probe) }
        assertTrue(edge.isNotEmpty(), "no range was drawn")
        return edge.first().toFloat() to edge.last().toFloat() + 1f
    }

    /** The first and last rows of the range's edge down the column [x] of the way across the chart. */
    private fun ComposeUiTest.edgesUp(x: Float): Pair<Float, Float> {
        val pixels = onNodeWithTag("chart").captureToImage().toPixelMap()
        val column = (pixels.width * x).toInt()
        val edge = (0 until pixels.height).filter { near(pixels[column, it], Probe) }
        assertTrue(edge.isNotEmpty(), "no range was drawn")
        return edge.first().toFloat() to edge.last().toFloat() + 1f
    }

    private fun near(a: Color, b: Color) = abs(a.red - b.red) < 0.3f && abs(a.green - b.green) < 0.3f && abs(a.blue - b.blue) < 0.3f

    private companion object {
        val Probe = Color(0xFFFF00FF)
        val Bars = Color(0xFF2060C0)
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

    /**
     * A finger's hold, taken and not yet moved, is felt once and draws the
     * range at once as a line the plot's height where the finger is, which
     * opens into the band as it moves. Until then it reports nothing, is said
     * as nothing, and fades nothing: the whole chart faded but for a hairline.
     * It drew nothing until the finger moved, and said nothing as the hold
     * took: *"a bit of feedback would be nice to know the selection has
     * started... just a line which then turns into the box as you start
     * dragging would be nice"*.
     */
    @Test
    fun aHoldIsFeltAndDrawnAsALineUntilItMoves() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        val felt = mutableListOf<FeedbackIntent>()
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Chart(
                            xAxis = ChartAxis.category<String>(),
                            yAxis = ChartAxis.number(domain = 0.0..400.0),
                            modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
                            rangeSelection = range,
                            colours = ChartDefaults.colours(series = listOf(Bars), grid = Color.Transparent, selection = Probe),
                            showLegend = false,
                            animateData = false,
                        ) {
                            bar(spending, x = { it.month }, y = { it.amount })
                        }
                    }
                }
            }
        }
        val chart = onNodeWithTag("chart")
        val bounds = chart.fetchSemanticsNode().boundsInRoot
        val feb = band("Feb")
        val start = Offset(feb.first + (feb.second - feb.first) * 0.3f, bounds.height * 0.5f)
        // June's bar, well clear of the range, low down where every bar is.
        val jun = band("Jun")
        fun june() = chart.captureToImage().toPixelMap()[((jun.first + jun.second) / 2).toInt(), (bounds.height * 0.8f).toInt()]
        val unfaded = june()
        chart.performTouchInput { down(start) }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        assertEquals(listOf(FeedbackIntent.LongPress), felt, "the hold taking should be felt, once")
        // Above the bars, which reach no higher than a little over half way.
        val (left, right) = edgesAcross(0.2f)
        assertTrue(abs(left - start.x) <= 2f && right - left <= 4f, "a line at ${start.x} should be drawn, not $left to $right")
        assertTrue(range.isEmpty, "a line marks nothing yet: ${range.xStart}..${range.xEnd}")
        val said = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).fetchSemanticsNodes()
        assertTrue(said.isEmpty(), "and is said as nothing")
        assertEquals(unfaded, june(), "and fades nothing")
        val to = start.x + (bounds.width * 0.3f)
        chart.performTouchInput {
            for (step in 1..10) moveTo(Offset(start.x + (to - start.x) * step / 10f, start.y))
        }
        waitForIdle()
        val (opened, closed) = edgesAcross(0.2f)
        assertTrue(abs(opened - start.x) <= 2f && abs(closed - to) <= 2f, "moved, it opens from ${start.x} to $to, not $opened to $closed")
        assertTrue(!range.isEmpty, "and marks a range")
        assertTrue(
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).fetchSemanticsNodes().isNotEmpty(),
            "which is said",
        )
        assertTrue(june() != unfaded, "and fades what is outside it")
        chart.performTouchInput { up() }
        waitForIdle()
        assertEquals(listOf(FeedbackIntent.LongPress), felt, "felt once a hold, not again as it moved or lifted")
    }

    /** A hold let go without moving lets go of its line: nothing was marked. */
    @Test
    fun aHoldLiftedWithoutMovingMarksNothing() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        bars(range)
        val chart = onNodeWithTag("chart")
        chart.performTouchInput { down(Offset(width * 0.3f, height * 0.5f)) }
        mainClock.advanceTimeBy(1000)
        chart.performTouchInput { up() }
        waitForIdle()
        assertTrue(range.isEmpty)
        assertNull(range.acrossFrom, "and nothing is left drawn")
    }

    /**
     * A box's hold draws the two lines through the point, across the plot and
     * up it, the edges it opens from.
     */
    @Test
    fun aBoxHoldIsDrawnAsTheLinesThroughItsPoint() = runComposeUiTest {
        val range = ChartRangeSelectionState<Number, Number>(ChartRangeMode.Box)
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Chart(
                        xAxis = ChartAxis.number(domain = 0.0..50.0),
                        yAxis = ChartAxis.number(domain = 0.0..20.0),
                        modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
                        rangeSelection = range,
                        colours = ChartDefaults.colours(series = listOf(Bars), grid = Color.Transparent, selection = Probe),
                        showLegend = false,
                        animateData = false,
                    ) {
                        point(listOf(Day(1.0, 1.0)), x = { it.temperature }, y = { it.use })
                    }
                }
            }
        }
        val bounds = onNodeWithTag("chart").fetchSemanticsNode().boundsInRoot
        val start = Offset(bounds.width * 0.5f, bounds.height * 0.5f)
        onNodeWithTag("chart").performTouchInput { down(start) }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        val (left, right) = edgesAcross(0.2f)
        assertTrue(abs(left - start.x) <= 2f && right - left <= 4f, "a line up through ${start.x}, not $left to $right")
        val (top, bottom) = edgesUp(0.8f)
        assertTrue(abs(top - start.y) <= 2f && bottom - top <= 4f, "and one across through ${start.y}, not $top to $bottom")
        assertTrue(range.isEmpty, "a point marks nothing yet")
        val end = start + Offset(bounds.width * 0.2f, -bounds.height * 0.2f)
        onNodeWithTag("chart").performTouchInput {
            for (step in 1..10) moveTo(start + (end - start) * (step / 10f))
            up()
        }
        waitForIdle()
        val (boxLeft, boxRight) = edgesAcross(0.4f)
        assertTrue(abs(boxLeft - start.x) <= 2f && abs(boxRight - end.x) <= 2f, "it opens into a box from ${start.x} to ${end.x}, not $boxLeft to $boxRight")
        assertTrue(!range.isEmpty, "which is marked")
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

    /**
     * A tap or a click off the chart lets go of its range, as it lets go of a
     * chosen place: asked for 2026-10-21, *"if I have a range marked on the
     * graph and I click/tap elsewhere, it should deselect"*.
     */
    @Test
    fun aTapElsewhereLetsGoOfTheRange() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        bars(range)
        range.select("Feb", "Apr")
        waitForIdle()
        // Below the 240dp chart, in the host round it.
        onRoot().performTouchInput { click(Offset(width / 2f, height - 4f)) }
        waitForIdle()
        assertTrue(range.isEmpty, "a tap off the chart should let go of the range: ${range.xStart}..${range.xEnd}")
        range.select("Feb", "Apr")
        waitForIdle()
        onRoot().performMouseInput { click(Offset(width / 2f, height - 4f)) }
        waitForIdle()
        assertTrue(range.isEmpty, "and so should a click: ${range.xStart}..${range.xEnd}")
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

    /**
     * A range kept while the data changes can name a category the data no
     * longer has. It encoded as NaN, and writing it for a screen reader
     * rounded NaN to an index and threw: now it is neither drawn nor said.
     */
    @Test
    fun aRangeWhoseCategoriesHaveGoneIsNeitherDrawnNorSaid() = runComposeUiTest {
        val range = ChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
        val data = mutableStateOf(spending)
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.size(360.dp, 240.dp).testTag("chart"),
                        rangeSelection = range,
                        showLegend = false,
                        animateData = false,
                    ) {
                        bar(data.value, x = { it.month }, y = { it.amount })
                    }
                }
            }
        }
        range.select("Feb", "Apr")
        waitForIdle()
        data.value = spending.filter { it.month !in listOf("Feb", "Mar", "Apr") }
        waitForIdle()
        val chart = onNode(isFocusable())
        val said = chart.fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }
        assertTrue(said == null || "Selected" !in said, "a range of categories that have gone should not be said: $said")
    }
}
