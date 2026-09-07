package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.foundation.Text
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A step in flight shows a spinner where its dot was, on the same line.
 *
 * The node's centre is **not** the gutter's centre. It sits `nodeSize / 2 +
 * NodeGap` from the top of the row so it lines up with the first line of text
 * beside it, whatever height the row turns out to be — and a row with two lines
 * of content is a good deal taller than the node. Dropping a `Spinner` into the
 * gutter with `Alignment.Center`, which is the obvious thing to write, puts it
 * halfway down the row and breaks the rail.
 *
 * So the assertion is about *where*, not about *whether*: a spinner that appears
 * in the wrong place is the failure this is guarding against, and "something is
 * drawn" would pass for it.
 */
class TimelineLoadingTest {

    /** The vertical middle of the gutter's ink, in rows. */
    private fun BufferedImage.nodeCentre(gutter: IntRange, rows: IntRange): Float? {
        var top = -1
        var bottom = -1
        for (y in rows) {
            val inked = gutter.any { x ->
                val p = getRGB(x, y)
                ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3 < 200
            }
            if (inked) {
                if (top < 0) top = y
                bottom = y
            }
        }
        return if (top < 0) null else (top + bottom) / 2f
    }

    private fun centreOfFirstNode(loading: Boolean): Pair<Float, Rect> {
        var bounds = Rect.Zero
        var centre = 0f
        Scene(width = 400, height = 300) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Timeline(Modifier.width(300.dp)) {
                    TimelineItem(
                        // No connector, so the only ink in the gutter is the
                        // node itself — a line running to the bottom of the row
                        // would swamp the measurement.
                        connector = ConnectorStyle.None,
                        loading = loading,
                        nodeColour = Color.Black,
                        modifier = Modifier.height(120.dp).reportBounds { bounds = it },
                    ) {
                        Text("Perth Station")
                        Text("08:12 — Platform 3")
                    }
                }
            }
        }.use { scene ->
            val image = scene.frames(8)
            // The gutter is 28dp wide by default, at the row's leading edge.
            val gutter = bounds.left.toInt()..(bounds.left + 28f * 2f).toInt()
            val rows = bounds.top.toInt() until bounds.bottom.toInt()
            centre = image.nodeCentre(gutter, rows)
                ?: error("nothing was drawn in the gutter at all")
        }
        return centre to bounds
    }

    @Test
    fun aLoadingNodeSitsWhereItsDotWould() {
        val (dot, bounds) = centreOfFirstNode(loading = false)
        val (spinner, _) = centreOfFirstNode(loading = true)

        // The control: on a 120dp row the dot is nowhere near the middle, so
        // "the spinner is where the dot is" and "the spinner is centred" are
        // different claims and this test can tell them apart.
        val middle = bounds.center.y
        assertTrue(
            abs(dot - middle) > 40f,
            "the dot sits ${abs(dot - middle)}px from the row's vertical centre, " +
                "which is not far enough for this test to distinguish the node's " +
                "line from the box's centre",
        )

        assertTrue(
            abs(spinner - dot) < 4f,
            "the spinner stands ${spinner}px down the row where the dot it " +
                "replaces stands at ${dot}px — ${abs(spinner - dot)}px out. A " +
                "loading node has to sit on the same line as every other node, " +
                "or the rail bends around the step that is still going.",
        )
    }
}
