package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.components.display.BranchTimeline
import io.kontour.ui.components.display.BranchTimelineColours
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.TimelineList
import io.kontour.ui.components.display.TimelineDefaults
import io.kontour.ui.foundation.Text
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A branching history drawn on the same rail as a stop list, read off the pixels.
 *
 * The rows are the same rows and the lanes the same rail, so a history with one
 * lane has to come out exactly as a [TimelineList] of the same stops does, and a
 * wider one has its lanes a lane apart from there and its text half a gutter
 * past the last of them.
 */
class BranchTimelineGeometryTest {

    private val black = BranchTimelineColours(lanes = listOf(Color.Black), muted = Color.Gray)

    /** One lane, every connector style: the same picture as a stop list, pixel for pixel. */
    @Test
    fun aOneLaneHistoryIsATimelineList() {
        val styles = listOf(ConnectorStyle.Solid, ConnectorStyle.Dashed, ConnectorStyle.Dotted, ConnectorStyle.None)
        val commits = styles.indices.map { "c$it" }
        val listLabels = arrayOfNulls<Rect>(styles.size)
        val branchLabels = arrayOfNulls<Rect>(styles.size)
        lateinit var list: BufferedImage
        lateinit var branch: BufferedImage
        Scene(width = 360, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TimelineList(colours = TimelineDefaults.colours(node = Color.Black, rail = Color.Black)) {
                    styles.forEachIndexed { i, style ->
                        item(connector = style) { label { Text("Stop $i", Modifier.reportBounds { listLabels[i] = it }) } }
                    }
                }
            }
        }.use { list = it.frames(4) }
        Scene(width = 360, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                BranchTimeline(
                    items = commits,
                    id = { it },
                    // Newest first: each commit's parent is the one below it.
                    parents = { listOfNotNull(commits.getOrNull(commits.indexOf(it) + 1)) },
                    colours = black,
                ) { commit ->
                    val i = commits.indexOf(commit)
                    item(connector = styles[i]) { label { Text("Stop $i", Modifier.reportBounds { branchLabels[i] = it }) } }
                }
            }
        }.use { branch = it.frames(4) }
        assertEquals(listLabels.toList(), branchLabels.toList(), "the labels should land in the same places")
        val differing = (0 until list.height).sumOf { y -> (0 until list.width).count { x -> list.getRGB(x, y) != branch.getRGB(x, y) } }
        assertTrue(differing == 0, "$differing pixels differ between a one-lane history and the same stops as a list")
    }

    /** Lanes a lane width apart from the first, and the text half a gutter past the last. */
    @Test
    fun lanesAreALaneWidthApartAndTheTextClearsTheLastByHalfAGutter() {
        for (direction in LayoutDirection.entries) {
            var mLabel = Rect.Zero
            lateinit var image: BufferedImage
            Scene(width = 360, height = 300) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        BranchTimeline(
                            items = listOf("m" to listOf("b", "f"), "f" to listOf("a"), "b" to listOf("a"), "a" to emptyList()),
                            id = { it.first },
                            parents = { it.second },
                            colours = black,
                        ) { commit ->
                            item { label { Text(commit.first, Modifier.reportBounds { if (commit.first == "m") mLabel = it }) } }
                        }
                    }
                }
            }.use { image = it.frames(4) }
            // Row "b": lane 0 has b's node, lane 1 has f's line passing it.
            val y = (image.height * 0.55).toInt().coerceAtMost(image.height - 1)
            val inkAt = { fromStart: Int ->
                val x = if (direction == LayoutDirection.Ltr) fromStart else image.width - 1 - fromStart
                dark(image.getRGB(x, y))
            }
            val lanes = (0 until 120).filter(inkAt)
            val centres = lanes.fold(mutableListOf<MutableList<Int>>()) { runs, x ->
                if (runs.isNotEmpty() && runs.last().last() == x - 1) runs.last() += x else runs += mutableListOf(x)
                runs
            }.map { it.average().toFloat() }
            assertEquals(2, centres.size, "$direction: expected two lanes in b's row, ink at $lanes")
            assertNear(LaneZeroPx, centres[0], "$direction: the first lane")
            assertNear(LaneZeroPx + LaneWidthPx, centres[1], "$direction: the second lane")
            val textStart = if (direction == LayoutDirection.Ltr) mLabel.left else image.width - mLabel.right
            assertNear(LaneZeroPx + LaneWidthPx + HalfGutterPx, textStart, "$direction: the text")
        }
    }

    /** A dotted line bending across to another lane keeps its dots all the way round. */
    @Test
    fun aDottedMergeCurveKeepsItsDots() {
        lateinit var image: BufferedImage
        Scene(width = 360, height = 300) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                BranchTimeline(
                    items = listOf("m" to listOf("b", "f"), "f" to listOf("a"), "b" to listOf("a"), "a" to emptyList()),
                    id = { it.first },
                    parents = { it.second },
                    colours = black,
                ) { commit ->
                    item(connector = if (commit.first == "m") ConnectorStyle.Dotted else ConnectorStyle.Solid) {
                        +commit.first
                    }
                }
            }
        }.use { image = it.frames(4) }
        // Between the two lanes, in the merge's row, the curve crosses every
        // column: dotted, so some columns in the band between them are blank.
        val from = (LaneZeroPx + 4).toInt()
        val to = (LaneZeroPx + LaneWidthPx - 4).toInt()
        val inked = (from..to).count { x -> (0 until 90).any { y -> dark(image.getRGB(x, y)) } }
        assertTrue(inked in 1 until (to - from + 1), "a dotted curve should leave gaps across the lanes; $inked of ${to - from + 1} columns inked")
    }

    private fun dark(p: Int) = ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3 < 128

    private fun assertNear(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) <= 1.5f, "$what: expected ${expected}px, was ${actual}px")

    private companion object {
        /** At density two: an 8dp inset and half a 28dp gutter, a 20dp lane, and half a gutter to the text. */
        const val LaneZeroPx = 44f
        const val LaneWidthPx = 40f
        const val HalfGutterPx = 28f
    }
}
