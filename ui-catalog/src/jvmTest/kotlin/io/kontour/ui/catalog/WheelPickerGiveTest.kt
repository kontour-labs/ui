package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.datetime.WheelPicker
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The drum answers a finger that has run out of values.
 *
 * A finite list has a first and a last, and the list clamps at both and drops
 * what it could not use — so the drum stopped dead under a finger still
 * travelling. That is a boundary the finger cannot feel, and it is exactly the
 * shape of the thing the sheet was fixed for two rounds ago: dragged 292px past
 * its top detent, a sheet's offset stayed at `352.0` for every frame and its
 * crown row never moved a pixel.
 *
 * ### Three properties, and the middle one is the point
 *
 * Anyone can make a drum move further. What makes it read as a rubber band
 * rather than as a second, shorter track is that each pixel of finger buys less
 * than the last — so the first half of an overscroll has to move the drum
 * further than the second.
 *
 * ### Why the gesture is spelled out rather than using `drag`
 *
 * Touch slop. A scrollable eats the first stretch of any touch drag — about
 * 36px at this scene's density — before it reports a single pixel, and a run of
 * equal steps that begins inside that dead zone looks like acceleration: the
 * drum sits still for several frames and then starts moving, which is the
 * opposite of the shape being asserted. So the slop is paid off first, and the
 * measured pull begins from what the drum looks like once it is actually being
 * pulled.
 */
class WheelPickerGiveTest {

    @Test
    fun theDrumStretchesPastItsFirstRowAndSpringsBack() {
        var selected by mutableStateOf(0)
        var bounds = Rect.Zero

        Scene(width = 300, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                WheelPicker(
                    items = (0..9).map { it.toString() },
                    selected = selected,
                    onSelectedChange = { selected = it },
                    modifier = Modifier.reportBounds { bounds = it },
                    label = { it },
                )
            }
        }.use { scene ->
            val atRest = scene.frames(20).firstInkRow(bounds)
            assertTrue(bounds.height > 0f, "the wheel never reported a size")

            // Row zero is already under the centre line, so every pixel of a
            // downward pull is past the end of the list.
            val start = Offset(bounds.center.x, bounds.top + 40f)
            scene.press(start)
            scene.move(start + Offset(0f, SlopPx))
            val base = scene.frame().firstInkRow(bounds)

            val crowns = mutableListOf<Int>()
            repeat(Steps) { step ->
                scene.move(start + Offset(0f, SlopPx + (step + 1) * StepPx))
                crowns += scene.frame().firstInkRow(bounds)
            }
            scene.release(start + Offset(0f, SlopPx + Steps * StepPx))
            val settled = scene.frames(90).firstInkRow(bounds)

            val give = crowns.last() - base
            assertTrue(
                give > 8,
                "the drum moved ${give}px under a ${(Steps * StepPx).toInt()}px pull past " +
                    "its first row. It is supposed to follow the finger a little way and " +
                    "resist, not stop dead at a boundary the finger cannot feel.",
            )

            // Diminishing returns, which is the property that separates a rubber
            // band from a second, shorter track. The finger travels at a
            // constant rate over exactly twice the band's limit, so:
            //
            //  - a stretch that moves the drum one-for-one and then stops dead
            //    has spent the whole limit inside the first half and moves not
            //    one pixel over the second, and
            //  - a stretch with no limit at all moves the same distance over
            //    both halves.
            //
            // Something that resists moves over both and further over the
            // first, which is neither.
            val half = crowns.size / 2
            val early = crowns[half - 1] - base
            val late = crowns.last() - crowns[half - 1]
            assertTrue(
                late > 0,
                "the drum moved ${early}px over the first half of the pull and stopped " +
                    "dead for the second — that is the same rigid boundary moved somewhere " +
                    "else rather than something giving under a finger",
            )
            assertTrue(
                early > late,
                "the drum moved ${early}px over the first half of the pull and ${late}px " +
                    "over the second — at that ratio nothing is resisting, it is simply a " +
                    "second track",
            )

            assertTrue(
                settled <= atRest + 2,
                "the drum rested at ${atRest}px before the pull and ${settled}px after it, " +
                    "so the stretch did not spring back",
            )
            assertTrue(
                selected == 0,
                "the stretch is visual, so nothing should have been selected by it — " +
                    "the drum reports $selected",
            )
        }
    }
}

/**
 * Enough to clear touch slop, paid off before the pull is measured.
 *
 * Comfortably over it rather than exactly it: what is left over is a few pixels
 * of real stretch, which the measurement takes as its baseline, so being
 * generous here costs nothing and being short would cost the whole assertion.
 */
private const val SlopPx = 40f
private const val StepPx = 12f
private const val Steps = 20

/**
 * The first row of [within] carrying ink.
 *
 * Text only, and deliberately: the centre band behind the selected row is
 * `surfaceSunken`, nine values off white, so a threshold that ignores it leaves
 * a measurement that follows the drum rather than the furniture drawn over it.
 */
private fun java.awt.image.BufferedImage.firstInkRow(within: Rect): Int {
    val background = getRGB(1, 1)
    val left = within.left.toInt().coerceAtLeast(0)
    val right = within.right.toInt().coerceAtMost(width)
    for (y in within.top.toInt().coerceAtLeast(0) until within.bottom.toInt().coerceAtMost(height)) {
        for (x in left until right) {
            val here = getRGB(x, y)
            if (kotlin.math.abs((here shr 16 and 0xFF) - (background shr 16 and 0xFF)) > 64 ||
                kotlin.math.abs((here shr 8 and 0xFF) - (background shr 8 and 0xFF)) > 64 ||
                kotlin.math.abs((here and 0xFF) - (background and 0xFF)) > 64
            ) {
                return y
            }
        }
    }
    return within.bottom.toInt()
}
