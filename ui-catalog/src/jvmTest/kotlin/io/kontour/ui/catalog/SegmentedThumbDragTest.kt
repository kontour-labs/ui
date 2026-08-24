package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.SegmentedControl
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The finger carries the thumb, rather than the thumb chasing the selection.
 *
 * The control has been drag-*aware* since Round 8: a drag across it changed the
 * selection as it crossed each boundary. What it was not was draggable. The
 * thumb sprang from one segment to the next once the value had already changed,
 * so the only part of the gesture you could see was the part that was over —
 * which is a thumb reacting to a drag, not a thumb being dragged, on the one
 * control whose whole identity is having a moving part.
 *
 * ### Measured with the finger held still, in two places inside one segment
 *
 * Counting distinct positions across a drag was the first attempt and it
 * **passed against the defect**: the indicator springs from segment to segment,
 * and a spring passes through as many intermediate positions as you care to
 * sample. Movement was never the question.
 *
 * The question is whether the thumb knows *where in the segment* the finger is.
 * So the finger is put down at a segment's centre and held until everything
 * settles, then put down near that same segment's edge and held. The selection
 * is the same in both; a thumb parked on its segment is in the same place in
 * both. A thumb being carried is not.
 *
 * The thumb is the one light shape on a sunken track, which is what makes it
 * findable without knowing where it should be.
 */
class SegmentedThumbDragTest {

    @Test
    fun theThumbLeansTowardWhereTheFingerIsInsideOneSegment() {
        val atCentre = heldAt(0.37f)
        val nearEdge = heldAt(0.47f)

        assertTrue(
            atCentre.selected == nearEdge.selected,
            "the two holds landed on different segments (${atCentre.selected} and " +
                "${nearEdge.selected}) — this compares two places inside one",
        )
        assertTrue(
            atCentre.thumb >= 0 && nearEdge.thumb >= 0,
            "the thumb was not found: ${atCentre.thumb}, ${nearEdge.thumb}",
        )
        assertTrue(
            nearEdge.thumb - atCentre.thumb > Lean,
            "the thumb sits at ${atCentre.thumb}px with the finger at the middle " +
                "of a segment and ${nearEdge.thumb}px with it near that segment's " +
                "edge — ${nearEdge.thumb - atCentre.thumb}px apart. Both holds are " +
                "on the same segment, so a thumb that is merely parked on the " +
                "selection is in the same place in both, and this one is.",
        )
    }

    /** Where the thumb settled with a finger held at [fraction], and on what. */
    private class Held(val thumb: Int, val selected: Int)

    private fun heldAt(fraction: Float): Held {
        var selected by mutableStateOf(0)
        var bounds = Rect.Zero
        var thumb = -1

        Scene(width = 700, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                SegmentedControl(
                    options = Options,
                    selected = selected,
                    onSelectedChange = { selected = it },
                    modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(4)
            assertTrue(bounds.width > 0f, "the control never reported a size")
            // Released nowhere: the finger goes down at the far left, travels to
            // the target and stays there. Held rather than tapped, because the
            // lean only exists while something is pulling on the thumb.
            scene.drag(
                from = bounds.alongX(0.08f),
                to = bounds.alongX(fraction),
                steps = 20,
                release = false,
            )
            thumb = scene.frames(30).thumbCentre(bounds)
        }

        return Held(thumb, selected)
    }

    /**
     * The centre of the thumb — the one light shape inside the sunken track.
     *
     * Read a third of the way down rather than through the middle, so the
     * segment labels drawn over the thumb cannot break its run in two.
     */
    private fun BufferedImage.thumbCentre(bounds: Rect): Int {
        val row = (bounds.top + bounds.height * 0.25f).toInt().coerceIn(0, height - 1)
        val track = getRGB(bounds.left.toInt() + 6, row)

        var best = -1
        var bestRun = 0
        var start = -1
        for (x in bounds.left.toInt()..(bounds.right.toInt() - 1).coerceAtMost(width - 1)) {
            val differs = kotlin.math.abs((getRGB(x, row) and 0xFF) - (track and 0xFF)) > 8
            if (differs && start < 0) start = x
            if (!differs && start >= 0) {
                if (x - start > bestRun) {
                    bestRun = x - start
                    best = (start + x) / 2
                }
                start = -1
            }
        }
        return best
    }

    private companion object {
        val Options = listOf("Depart", "Arrive", "Both", "Neither")

        /**
         * How far apart the two holds must put the thumb.
         *
         * The two are a tenth of the control apart, and `DetentPull` is 0.45, so
         * a carried thumb leans about 4.5% of the control — 28px of the 620 it
         * gets here. Ten is comfortably above antialiasing and comfortably below
         * the real answer.
         */
        const val Lean = 10
    }
}
