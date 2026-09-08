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
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The handle you are holding is the one you can see.
 *
 * Reported as: drag one end of a range past the other and the thumb under your
 * finger disappears behind the one it is shoving. The draw loop takes a literal
 * `listOf(start, end)` and painter order does the rest, so the end thumb is on
 * top always — including when it is the start thumb that is being dragged.
 *
 * ### How you tell two identical circles apart
 *
 * You cannot, by colour: both thumbs are drawn by the same `sliderThumb` with
 * the same ring and the same fill. Nor by silhouette — the union of two shapes
 * is the same whichever went down first.
 *
 * What order changes is where the **ring** lands. `sliderThumb` paints a
 * ring-coloured rounded rect and then a fill-coloured one inside it, so the
 * thumb drawn second stamps its ring across the first one's fill. Count the runs
 * of fill along the row through both centres:
 *
 * * the **smaller** thumb on top — its ring cuts the larger one's fill in two,
 *   giving three runs;
 * * the **larger** thumb on top — it covers the other outright, giving one.
 *
 * At `minDistance = 0` a pushed thumb is welded to the pusher and drawn at
 * exactly the same place (`drawnEnd = easedStart + gapFraction`, and the gap is
 * zero), so the two are concentric and only the scale differs: the held thumb is
 * the bigger one. That makes the run count a clean integer rather than a
 * threshold.
 */
class RangeSliderThumbOrderTest {

    /**
     * Runs of [fill]-coloured pixels along [row].
     *
     * Not `runsIn`, which counts ink against a background — here the background
     * *is* ink, and what matters is the fill being interrupted by a ring.
     */
    private fun BufferedImage.fillRuns(row: Int, fill: Int): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (x in 0 until width) {
            val p = getRGB(x, row)
            val same = abs((p shr 16 and 0xFF) - (fill shr 16 and 0xFF)) < 24 &&
                abs((p shr 8 and 0xFF) - (fill shr 8 and 0xFF)) < 24 &&
                abs((p and 0xFF) - (fill and 0xFF)) < 24
            if (same && start < 0) start = x
            if (!same && start >= 0) {
                runs += start..(x - 1)
                start = -1
            }
        }
        if (start >= 0) runs += start..(width - 1)
        return runs
    }

    @Test
    fun theThumbBeingDraggedIsDrawnOverTheOneItIsPushing() {
        var range by mutableStateOf(0.25f..0.75f)
        var bounds = Rect.Zero
        var runs = emptyList<IntRange>()
        var restingRuns = emptyList<IntRange>()

        Scene(width = 600, height = 200) {
            KontourTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    RangeSlider(
                        value = range,
                        onValueChange = { range = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            val resting = scene.frames(6)
            val row = bounds.center.y.toInt()
            // The fill colour, read off the render rather than named: the thumb's
            // centre at rest is fill and nothing else.
            val fill = resting.getRGB(bounds.center.x.toInt(), row)
            restingRuns = resting.fillRuns(row, fill)

            // Grab the start thumb and shove it past the end one. Held, not
            // released — the whole claim is about what you can see *while*
            // dragging.
            scene.press(bounds.alongX(0.25f))
            scene.frames(1)
            scene.move(bounds.alongX(0.60f))
            scene.frames(1)
            scene.move(bounds.alongX(0.90f))
            runs = scene.frames(8).fillRuns(row, fill)
            scene.release(bounds.alongX(0.90f))
        }

        // The control, and it is worth stating what it actually looks like
        // rather than what a first draft assumed. At rest the row crosses the
        // start thumb, the band, and the end thumb — and each thumb's ring
        // separates its own fill from the band, so that is *three* runs, a
        // narrow one at each end and a wide one between: measured
        // [168..207, 212..387, 392..431] on a 600px scene. If the sampler
        // cannot see that much, the reading below means nothing.
        assertEquals(
            3,
            restingRuns.size,
            "at rest the row should cross the start thumb, the band and the end " +
                "thumb as three runs of fill, but it was $restingRuns",
        )
        val band = restingRuns[1]
        assertTrue(
            band.last - band.first > (restingRuns[0].last - restingRuns[0].first) * 2,
            "the middle run should be the band between the thumbs and so much " +
                "wider than either thumb, but the three were $restingRuns — the " +
                "sampler is not seeing what this test thinks it is",
        )

        // Concentric now, and the band between them has zero width, so every
        // run in this row belongs to the thumbs.
        assertEquals(
            1,
            runs.size,
            "while the start thumb was dragged onto the end thumb, the row " +
                "through both showed ${runs.size} runs of fill ($runs). More " +
                "than one means the thumb being pushed is stamping its ring " +
                "across the one under the finger — the smaller circle drawn " +
                "over the larger one, which is the handle you are holding " +
                "disappearing behind the one it is shoving.",
        )
    }
}
