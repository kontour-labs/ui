package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.SegmentedControl
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The thumb sits on its segment once the finger has gone, at every text size.
 *
 * Written to catch a defect that turned out not to exist, and kept because what
 * it does catch is real and nothing else was checking it.
 *
 * ### What was suspected
 *
 * `SegmentedControl`'s thumb leans toward the finger, and the lean is clamped so
 * the thumb can never be pulled off the end of the track:
 *
 * ```kotlin
 * pulled.coerceIn(
 *     segment / 2f - base,
 *     (trackWidth - segment / 2f - base).coerceAtLeast(segment / 2f - base),
 * )
 * ```
 *
 * `base` is `indicator.drawn.center.x`, published from a `SideEffect` during
 * composition; `trackWidth` arrives from `onSizeChanged` during layout. The
 * reading was that a re-measure makes the two disagree, one bound crosses zero,
 * and `coerceIn` returns that bound instead of the `0f` a resting thumb should
 * get — a thumb translated while standing still. `fingerX` is `NaN` until the
 * first drag and is never reset, which would make it appear only for someone who
 * has dragged once, and would fit "sometimes" and "doesn't always".
 *
 * **It does not happen.** Two renders of the same control in the same state —
 * one dragged there, one set programmatically — are **pixel-identical at 100%,
 * 130% and 200%**. Once the layout settles the two numbers agree, and the
 * segments are equal-weight children of a fixed-width row, so a change of type
 * scale does not move them at all. The suspicion was built on a disagreement
 * that lasts at most one frame.
 *
 * ### What it does guard
 *
 * That the lean relaxes when the finger lifts. `engaged` springs to zero and
 * multiplies the pull, and dropping that one factor — the kind of thing a
 * refactor does — leaves the thumb permanently leaning at the last place a
 * finger was. Canaried exactly that way: **5,759 pixels differ, across a 185px
 * span**, at all three text sizes.
 *
 * ### The measurement
 *
 * Both runs end with segment 2 selected and no finger down, so every pixel
 * should match. What does not match is the lean, and the count of mismatched
 * pixels is how big it is.
 */
class SegmentedThumbAlignmentTest {

    private val width = 900
    private val height = 200
    private val options = textScales.map { it.first }

    /**
     * The control after it reaches segment 2, at a device font scale of [endScale].
     *
     * The scale changes *after* the selection settles, which is the reporter's
     * order: the Text size control is the one whose own type is being changed.
     *
     * `reduceMotion = false` on purpose. Under reduced motion `engaged` is
     * pinned to zero and there is no lean to get wrong, so a test that took the
     * suite's usual determinism setting would pass without touching the code
     * path it is named after.
     */
    private fun settled(byDragging: Boolean, endScale: Float): BufferedImage {
        var selected by mutableStateOf(0)
        var scale by mutableStateOf(1f)
        var track = Rect.Zero
        Scene(width, height, reduceMotion = false) {
            CompositionLocalProvider(LocalDensity provides Density(2f, scale)) {
                Box(
                    Modifier.fillMaxSize().background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    SegmentedControl(
                        options = options,
                        selected = selected,
                        onSelectedChange = { selected = it },
                        modifier = Modifier.width(380.dp).reportBounds { track = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(8)
            val segment = track.width / options.size
            if (byDragging) {
                scene.drag(
                    from = Offset(track.left + segment * 0.5f, track.center.y),
                    to = Offset(track.left + segment * 2.5f, track.center.y),
                )
            } else {
                selected = 2
            }
            // Long enough for the travel spring and for `engaged` to relax all
            // the way back to zero. A lean still decaying is not a defect.
            scene.frames(60)
            scale = endScale
            return scene.frames(60)
        }
    }

    /**
     * Having dragged once must not move the thumb afterwards — at any text size.
     *
     * The two images are the same control, in the same state, with the same
     * finger-free present. The only thing that differs is a `Float` the
     * component kept from a gesture that is over.
     */
    @Test
    fun aFinishedDragLeavesTheThumbWhereATapWould() {
        val drifted = mutableListOf<String>()
        for (scale in listOf(1f, 1.3f, 2f)) {
            val tapped = settled(byDragging = false, endScale = scale)
            val dragged = settled(byDragging = true, endScale = scale)
            val (count, span) = difference(tapped, dragged)
            if (count > 0) {
                drifted += "  · text size $scale: $count pixels differ, " +
                    "across x ${span.first}..${span.second} (${span.second - span.first + 1}px wide)"
            }
        }
        assertTrue(
            drifted.isEmpty(),
            "the thumb moved because the control had once been dragged:\n" +
                drifted.joinToString("\n") +
                "\nAt rest the pull is multiplied by `engaged`, which has sprung " +
                "to zero, so the lean should be exactly `0f` — and the two " +
                "renders should be identical. Either that factor has been lost, " +
                "or `coerceIn` is returning a bound because `indicator.drawn` " +
                "and `trackWidth` disagree about how wide a segment is.",
        )
    }

    /** Differing pixel count, and the horizontal span they cover. */
    private fun difference(a: BufferedImage, b: BufferedImage): Pair<Int, Pair<Int, Int>> {
        var count = 0
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                if (a.getRGB(x, y) == b.getRGB(x, y)) continue
                count++
                if (x < left) left = x
                if (x > right) right = x
            }
        }
        return count to (if (count == 0) 0 to 0 else left to right)
    }
}
