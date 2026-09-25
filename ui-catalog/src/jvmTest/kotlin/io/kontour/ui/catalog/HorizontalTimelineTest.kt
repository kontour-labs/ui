package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.HorizontalTimeline
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.foundation.Text
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [TimelineItem]s laid across the page by a [HorizontalTimeline].
 *
 * Across, a node sits at the start of its item with the content under it, and its
 * connector runs to the item's end edge, where the next node begins. An item's
 * modifier is its label's, so the rail is found above the bounds it reports. The same
 * geometry as down the page, turned. So the checks are the vertical timeline's
 * turned too: every connector style reaches the end of its item, measured against
 * the solid one, and the layout mirrors right to left.
 */
class HorizontalTimelineTest {

    /**
     * A dotted or dashed connector reaches as far along its item as a solid one.
     *
     * The same fault the vertical connector had — a dash pattern abandoned
     * wherever the run ran out — would show here as a gap before the next node.
     * Four widths, so the run length against the pitch is not right by luck.
     */
    @Test
    fun everyConnectorReachesTheEndOfItsItem() {
        val short = mutableListOf<String>()
        for (width in listOf(120, 121, 122, 123)) {
            val solid = reach(ConnectorStyle.Solid, width)
            for (style in listOf(ConnectorStyle.Dotted, ConnectorStyle.Dashed)) {
                val other = reach(style, width)
                if (solid - other > Tolerance) short += "$style at ${width}dp: ${other}px against ${solid}px"
            }
        }
        assertTrue(short.isEmpty(), "a connector stopped short of its item's end — ${short.joinToString("; ")}")
    }

    /** Short stages share the width evenly; a long one sets every stage's width. */
    @Test
    fun equalWidthsAreEqual() {
        val short = equalWidths(listOf("Ordered", "Packed", "Here"))
        assertTrue(short.all { abs(it - short[0]) < 1f }, "equal widths came out $short")
        assertTrue(abs(short.sum() - 800f) < 3f, "three short stages should share the width, came to ${short.sum()}")

        val long = equalWidths(listOf("Ordered", "Packed and handed to the courier", "Here"))
        assertTrue(long.all { abs(it - long[0]) < 1f }, "equal widths came out $long")
        assertTrue(long.sum() > 800f, "a long stage should widen them all past the page, came to ${long.sum()}")
    }

    private fun equalWidths(labels: List<String>): List<Float> {
        val bounds = arrayOfNulls<Rect>(labels.size)
        Scene(width = 800, height = 200) {
            HorizontalTimeline(equalWidths = true) {
                labels.forEachIndexed { i, label ->
                    TimelineItem(
                        modifier = Modifier.reportBounds { bounds[i] = it },
                        connector = if (i == labels.lastIndex) ConnectorStyle.None else ConnectorStyle.Solid,
                    ) { Text(label) }
                }
            }
        }.use { it.frames(4) }
        return bounds.map { it!!.width }
    }

    @Test
    fun aTimelineWiderThanThePageScrolls() {
        var first = Rect.Zero
        Scene(width = 600, height = 200) {
            HorizontalTimeline {
                repeat(8) { i ->
                    TimelineItem(modifier = if (i == 0) Modifier.reportBounds { first = it } else Modifier) {
                        Text("Stage ${i + 1} of the delivery")
                    }
                }
            }
        }.use { scene ->
            scene.frames(4)
            val before = first.left
            scene.drag(from = Offset(500f, 60f), to = Offset(100f, 60f))
            scene.frames(20)
            assertTrue(first.left < before - 200f, "a drag to the left should scroll the timeline; it went from $before to ${first.left}")
        }
    }

    /** Right to left, the first node is at the right and the rail runs leftwards from it. */
    @Test
    fun rightToLeftStartsAtTheRight() {
        var first = Rect.Zero
        lateinit var image: BufferedImage
        Scene(width = 600, height = 200) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    HorizontalTimeline {
                        TimelineItem(
                            modifier = Modifier.reportBounds { first = it },
                            nodeColour = Color.Black,
                            connectorColour = Color.Black,
                        ) { Text("Ordered") }
                        TimelineItem(connector = ConnectorStyle.None) { Text("Here") }
                    }
                }
            }
        }.use { image = it.frames(4) }
        assertTrue(abs(first.right - 600f) < 1f, "the first item should sit against the right edge: $first")
        val row = (first.top - RailAboveLabel).toInt()
        val ink = (0 until image.width).filter { image.dark(it, row) }
        assertTrue(ink.isNotEmpty(), "nothing drawn along the rail")
        assertTrue(ink.max() > first.right - 2 * NodeRadiusPx - 8, "the node should be at the right of its item; ink ends at ${ink.max()}")
        assertTrue(ink.min() < first.left + 4, "the rail should run to the item's left, its end; ink starts at ${ink.min()}")
    }

    /** How far along its band the ink of one item goes, in one style, at one width. */
    private fun reach(style: ConnectorStyle, widthDp: Int): Float {
        var bounds = Rect.Zero
        lateinit var image: BufferedImage
        Scene(width = 400, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                HorizontalTimeline {
                    TimelineItem(
                        connector = style,
                        nodeColour = Color.Black,
                        connectorColour = Color.Black,
                        modifier = Modifier.width(widthDp.dp).reportBounds { bounds = it },
                    ) { Text("Packed") }
                }
            }
        }.use { image = it.frames(8) }
        assertTrue(bounds.width > 0f, "the item never reported a size")
        val row = (bounds.top - RailAboveLabel).toInt()
        val last = (bounds.left.toInt() until minOf(image.width, (bounds.right + Overhang).toInt()))
            .lastOrNull { image.dark(it, row) } ?: error("nothing drawn along the rail for $style")
        return last.toFloat()
    }

    private fun BufferedImage.dark(x: Int, y: Int): Boolean {
        val p = getRGB(x, y)
        return ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3 < 200
    }

    private companion object {
        /**
         * The rail above an item's label, which is what its modifier measures:
         * half the 16dp band, and the 8dp gap under it, at density two.
         */
        const val RailAboveLabel = 32f
        const val NodeRadiusPx = 12f
        const val Overhang = 8f
        const val Tolerance = 2f
    }
}
