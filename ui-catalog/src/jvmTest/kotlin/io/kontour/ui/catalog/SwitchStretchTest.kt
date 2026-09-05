package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.Switch
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The thumb's stretch *is* the travel, not a second animation reacting to it.
 *
 * It used to be a spring of its own driven by `dragging || fraction.isRunning`
 * — a boolean *about* whether the position was animating, which structurally
 * cannot overlap the animation it describes. Reported as "the thumb expands,
 * moves, then contracts as three phases", and the frames say exactly that:
 *
 * ```
 * frame  2  left=57  width=48   moving, and not yet stretched at all
 * frame  6  left=77  width=59   leading edge has hit the end of its travel
 * frame  9  left=77  width=63   parked, and still inflating
 * frame 18  left=94  width=46   deflated, a third of a second after arriving
 * ```
 *
 * ### The assertion is that the stretch does not lag the travel
 *
 * By the time the thumb is a quarter of the way across, it is already
 * stretching. A spring that has to be started by the *first* spring cannot do
 * that — it is still winding up while the thumb is already moving, which is the
 * first of the three phases. Nothing here says how wide it should get; that is
 * a design decision and this is a test about sequencing.
 *
 * Widths are measured against a colour threshold, so an edge lands within a
 * pixel or two either way; [Slack] is that, and it is well under the 13px of
 * stretch the component actually applies.
 */
class SwitchStretchTest {

    @Test
    fun theStretchDoesNotLagTheTravel() {
        var checked by mutableStateOf(false)
        var bounds = Rect.Zero
        val thumbs = mutableListOf<IntRange>()
        var resting = 0

        Scene(width = 300, height = 160) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            val settled = scene.frames(20)
            assertTrue(bounds.width > 0f, "the switch never reported a size")
            resting = settled.thumbRun(bounds).count()

            scene.press(bounds.centre())
            scene.frame()
            scene.release(bounds.centre())
            repeat(60) { thumbs += scene.frame().thumbRun(bounds) }
        }

        // The thumb's *leading* edge, which is the one travelling: it starts at
        // the near end and finishes against the far one, and unlike the centre
        // it does not drift while the thumb is merely changing width.
        val lead = thumbs.map { it.last }
        val start = lead.first()
        val arrival = lead.max()
        val distance = arrival - start
        assertTrue(
            distance > 16,
            "the thumb's leading edge moved ${distance}px, so this measured a switch at rest",
        )

        val widest = thumbs.maxOf { it.count() }
        assertTrue(
            widest > resting + Slack,
            "the thumb never stretched at all: ${widest}px against ${resting}px at rest",
        )

        val quarter = thumbs.indexOfFirst { it.last >= start + distance / 4 }
        val widthThere = thumbs[quarter].count()
        assertTrue(
            widthThere > resting + Slack,
            "a quarter of the way across — frame $quarter — the thumb was ${widthThere}px " +
                "wide, against ${resting}px at rest and ${widest}px at its widest. The " +
                "stretch is supposed to *be* the travel, growing as the thumb sets off; " +
                "still round a quarter of the way over means it is a second animation " +
                "waiting for the first to start, which is the first of the three phases " +
                "this exists to stop.",
        )

        // And the other end of the same claim: once the leading edge is home,
        // the thumb is round. A boolean-driven stretch is still deflating for
        // another third of a second after the thumb has stopped.
        val home = thumbs.indexOfFirst { it.last >= arrival }
        val settledWidth = thumbs.drop(home + 2).firstOrNull()?.count() ?: 0
        assertTrue(
            settledWidth <= resting + Slack,
            "two frames after arriving the thumb was still ${settledWidth}px wide against " +
                "${resting}px at rest — the third phase",
        )
    }

    private companion object {
        /** Antialiasing at a colour threshold, not a real difference. */
        const val Slack = 3
    }
}

/**
 * The thumb's horizontal extent, as a pixel range.
 *
 * The track is filled and the thumb sits on it, so both are ink against the
 * page and a run finder cannot tell them apart. The track's own colour is taken
 * from a row just inside its top edge — above the thumb, which is inset — and
 * the thumb is then whatever differs from it along the centre line.
 */
private fun BufferedImage.thumbRun(switch: Rect): IntRange {
    val track = getRGB(switch.center.x.toInt(), switch.top.toInt() + 2)
    val row = switch.center.y.toInt()
    var first = -1
    var last = -1
    for (x in switch.left.toInt() until switch.right.toInt()) {
        if (differs(getRGB(x, row), track)) {
            if (first < 0) first = x
            last = x
        }
    }
    return if (first < 0) IntRange.EMPTY else first..last
}

private fun differs(a: Int, b: Int): Boolean =
    abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > 40 ||
        abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > 40 ||
        abs((a and 0xFF) - (b and 0xFF)) > 40

private fun Rect.centre() = androidx.compose.ui.geometry.Offset(center.x, center.y)
