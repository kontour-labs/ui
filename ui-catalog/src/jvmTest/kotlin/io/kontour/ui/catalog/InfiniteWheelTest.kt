package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.WheelPicker
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The infinite wheel: rows above *and* below, inside its own box, turning the
 * way it is dragged.
 *
 * Three faults in one component, reported together as "options appear only above
 * the selection, extend past the container, and scrolling is backwards". They
 * are independent and each has its own mechanism.
 *
 * **Only above — not covered here, and deliberately not.** The drum draws
 * `visibleItems + 2` rows inside a `Box` exactly `visibleItems` tall, and a
 * `Column` hands each child what is left of its own height, so the last two were
 * measured against nothing. `requiredHeight` on the column is the remedy for
 * that and is applied.
 *
 * What is *not* here is a test for it, because the obvious one cannot be
 * trusted. Rows fade with distance from the band and `wheelFade` bottoms out at
 * **0.2 alpha rather than zero**, so a threshold-based ink detector reports "no
 * row" for a row that is present and faint. A measurement was taken with the
 * clipping off and the whole scene dumped: three bands of detectable ink, 80px
 * apart, with the strongest — the selected row — one row above the box's centre.
 * That is consistent with a row-collapse *and* with an off-by-one in the offset
 * *and* with the detector simply missing the 0.2-alpha rows, and nothing in that
 * picture separates the three.
 *
 * A green test that cannot tell those apart is worse than no test. This half of
 * the report stays open.
 *
 * **Past the container.** Those extra rows are outside the box by design, and
 * the box did not clip, so they drew over whatever the picker was sitting in.
 *
 * **Backwards.** The delta was negated twice: `reverseDirection = true` on the
 * `scrollable`, which is the "dragging up rolls the drum forward" convention and
 * carries a comment saying so, and a bare `- delta` in the arithmetic that did
 * not. Only the one without an explanation survived review.
 */
class InfiniteWheelTest {


    @Test
    fun nothingIsDrawnOutsideTheWheelsOwnBox() {
        val (bounds, image) = wheel()

        // A generous margin either side of the wheel, in the padding the test
        // put there. Any ink here escaped the component.
        val strays = mutableListOf<String>()
        for (y in 4 until image.height step 2) {
            val outside = y < bounds.top - 2 || y > bounds.bottom + 2
            if (!outside) continue
            val x = bounds.center.x.toInt()
            if (!isBackground(image.getRGB(x, y))) strays += "y=$y"
        }

        assertTrue(
            strays.isEmpty(),
            "the drum drew outside its own bounds at ${strays.take(6)}. It lays " +
                "out two rows more than it shows so an edge row is half-visible; " +
                "those belong inside the box, which has to cut them off.",
        )
    }

    @Test
    fun draggingUpTurnsTheDrumForward() {
        var selected by mutableStateOf(5)
        var bounds = Rect.Zero

        Scene(width = 320, height = 560, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(30.dp)) {
                WheelPicker(
                    items = (0..23).toList(),
                    selected = selected,
                    onSelectedChange = { selected = it },
                    label = { it.toString().padStart(2, '0') },
                    infinite = true,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(4)
            val started = selected
            // Upward, which on every wheel in this library and on both platforms'
            // own rolls the drum toward *later* values.
            scene.drag(
                from = Offset(bounds.center.x, bounds.center.y + bounds.height * 0.3f),
                to = Offset(bounds.center.x, bounds.center.y - bounds.height * 0.3f),
                steps = 20,
            )
            scene.frames(8)

            assertTrue(
                selected != started,
                "dragging did not move the drum at all — it is still on $started",
            )
            // 0..23 wraps, so "forward" is measured the short way round.
            val forward = ((selected - started) % 24 + 24) % 24
            assertTrue(
                forward in 1..11,
                "dragging up moved the drum from $started to $selected, which is " +
                    "$forward steps forward — i.e. backwards. The delta is negated " +
                    "by `reverseDirection` on the `scrollable` and must not be " +
                    "negated again in the arithmetic.",
            )
        }
    }

    /** A wheel on a white ground, with room around it for stray ink to land in. */
    private fun wheel(): Pair<Rect, BufferedImage> {
        var bounds = Rect.Zero
        var image: BufferedImage? = null
        Scene(width = 320, height = 560, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(30.dp)) {
                WheelPicker(
                    items = (0..23).toList(),
                    selected = 5,
                    onSelectedChange = {},
                    label = { it.toString().padStart(2, '0') },
                    infinite = true,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            image = scene.frames(3)
        }
        return bounds to requireNotNull(image)
    }



    private fun isBackground(rgb: Int): Boolean {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return r > 245 && g > 245 && b > 245
    }
}
