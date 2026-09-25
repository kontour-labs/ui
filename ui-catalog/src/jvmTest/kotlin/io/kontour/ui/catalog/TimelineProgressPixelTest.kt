package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.HorizontalTimeline
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.TimelineDefaults
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.foundation.Text
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A [Timeline] and a [HorizontalTimeline] travelled to a point, read off their
 * pixels with the motion still: which nodes and legs are in the progress colour,
 * the halo on the stop the journey is at, and a leg split where the journey is.
 */
class TimelineProgressPixelTest {

    private val red = Color(0xFFD00000)
    private val grey = Color(0xFF909090)

    /** Down the page at a stop: the nodes up to it red, the one after grey, and a halo round it. */
    @Test
    fun aTimelineAtAStopColoursWhatItHasPassed() {
        val items = arrayOfNulls<Rect>(3)
        val image = vertical(progress = 1f, items)
        val nodes = items.map { node(it!!) }
        assertColour(red, image, nodes[0], "the first stop is passed")
        assertColour(red, image, nodes[1], "the second is where the journey is")
        assertColour(grey, image, nodes[2], "the third is not reached")
        assertHalo(image, nodes[1], "the stop the journey is at")
        assertNoHalo(image, nodes[0], "a stop already passed")
        assertColour(red, image, nodes[0].first to (nodes[0].second + nodes[1].second) / 2, "the leg behind")
        assertColour(grey, image, nodes[1].first to (nodes[1].second + nodes[2].second) / 2, "the leg ahead")
    }

    /** Halfway along a leg: its first half red, its second grey, and no stop has the halo. */
    @Test
    fun aTimelineBetweenStopsSplitsTheLeg() {
        val items = arrayOfNulls<Rect>(3)
        val image = vertical(progress = 0.5f, items)
        val nodes = items.map { node(it!!) }
        val top = nodes[0].second + ClearPx
        val bottom = items[0]!!.bottom.toInt()
        assertColour(red, image, nodes[0].first to top + (bottom - top) / 4, "a quarter of the way along")
        assertColour(grey, image, nodes[0].first to top + (bottom - top) * 3 / 4, "three quarters of the way along")
        nodes.forEachIndexed { i, node -> assertNoHalo(image, node, "stop $i, with the journey between two") }
    }

    /** An item wrapped in something else still counts in its place. */
    @Test
    fun aWrappedItemCountsInItsPlace() {
        val items = arrayOfNulls<Rect>(3)
        val image = vertical(progress = 1f, items, wrapMiddle = true)
        assertHalo(image, node(items[1]!!), "the wrapped second item is the one the journey is at")
        assertColour(grey, image, node(items[2]!!), "and the third is still not reached")
    }

    /** Across the page, the same: nodes to the left of the journey red, the rest grey. */
    @Test
    fun aHorizontalTimelineColoursWhatItHasPassed() {
        val labels = arrayOfNulls<Rect>(3)
        lateinit var image: BufferedImage
        Scene(width = 600, height = 200, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                HorizontalTimeline(progress = 1f, colours = TimelineDefaults.colours(node = Color.Black, rail = grey, progress = red)) {
                    repeat(3) { i ->
                        TimelineItem(
                            modifier = Modifier.reportBounds { labels[i] = it },
                            connector = if (i == 2) ConnectorStyle.None else ConnectorStyle.Solid,
                        ) { Text("Stage $i") }
                    }
                }
            }
        }.use { image = it.frames(6) }
        // Across, the node is a band above its label, a node's radius and a gap in.
        val nodes = labels.map { (it!!.left + ClearPx).toInt() to (it.top - RailAboveLabelPx).toInt() }
        assertColour(red, image, nodes[0], "the first stage is passed")
        assertColour(red, image, nodes[1], "the second is where it is")
        assertColour(grey, image, nodes[2], "the third is not reached")
        assertHalo(image, nodes[1], "the stage it is at", across = true)
        assertTrue(labels[0]!!.left >= PulseRoomPx - 1, "the first node should have room before it for its pulse: ${labels[0]}")
    }

    private fun vertical(progress: Float, items: Array<Rect?>, wrapMiddle: Boolean = false): BufferedImage {
        lateinit var image: BufferedImage
        Scene(width = 400, height = 400, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Timeline(progress = progress, colours = TimelineDefaults.colours(node = Color.Black, rail = grey, progress = red)) {
                    repeat(3) { i ->
                        val item = @Composable {
                            TimelineItem(
                                modifier = Modifier.reportBounds { items[i] = it },
                                connector = if (i == 2) ConnectorStyle.None else ConnectorStyle.Solid,
                            ) {
                                Text("Stop $i")
                                Text("Platform ${i + 1}")
                            }
                        }
                        if (wrapMiddle && i == 1) Box { item() } else item()
                    }
                }
            }
        }.use { image = it.frames(6) }
        return image
    }

    /** The centre of a vertical item's node: half its gutter in, and a gap and a radius down. */
    private fun node(item: Rect) = (item.left + GutterHalfPx).toInt() to (item.top + ClearPx).toInt()

    private fun assertColour(expected: Color, image: BufferedImage, at: Pair<Int, Int>, what: String) {
        val p = image.getRGB(at.first, at.second)
        val want = listOf((expected.red * 255).toInt(), (expected.green * 255).toInt(), (expected.blue * 255).toInt())
        val got = listOf(p shr 16 and 0xFF, p shr 8 and 0xFF, p and 0xFF)
        assertTrue(got.zip(want).all { (a, b) -> kotlin.math.abs(a - b) < 40 }, "$what: expected $want at $at, was $got")
    }

    /** A pixel in the ring between the node and the gap round it: the halo's pink, or the page's white. */
    private fun haloAt(image: BufferedImage, node: Pair<Int, Int>, across: Boolean): Triple<Int, Int, Int> {
        // Beside the node across the rail, where no leg runs.
        val (x, y) = if (across) node.first to node.second - HaloProbePx else node.first + HaloProbePx to node.second
        val p = image.getRGB(x, y)
        return Triple(p shr 16 and 0xFF, p shr 8 and 0xFF, p and 0xFF)
    }

    private fun assertHalo(image: BufferedImage, node: Pair<Int, Int>, what: String, across: Boolean = false) {
        val (r, g, b) = haloAt(image, node, across)
        assertTrue(r > 200 && g in 140..225 && b in 140..225, "$what should have a halo: ($r, $g, $b)")
    }

    private fun assertNoHalo(image: BufferedImage, node: Pair<Int, Int>, what: String) {
        val (r, g, b) = haloAt(image, node, across = false)
        assertTrue(r > 245 && g > 245 && b > 245, "$what should have no halo: ($r, $g, $b)")
    }

    private companion object {
        /** At density two: half the 28dp gutter, the node's 6dp radius and 2dp gap, and between the two. */
        const val GutterHalfPx = 28
        const val ClearPx = 16
        const val HaloProbePx = 14
        const val RailAboveLabelPx = 32
        /** Before the first node across: three quarters of its 8dp clearance, for the pulse. */
        const val PulseRoomPx = 12
    }
}
