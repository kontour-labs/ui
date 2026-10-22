package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A selection marker that fades in keeps its shadow through the fade.
 *
 * Found with `SegmentedShadowArrivalTest`, 2026-10-21, and fixed before 0.5.0
 * at the user's asking: a marker that arrives with its group is drawn whole
 * now, but one that fades in later — under reduced motion, where a new
 * selection fades the marker out and in at its new place rather than sliding
 * it, or a marker coming back after fading out — faded in a layer that, while
 * it was not opaque, was drawn offscreen at the marker's own size. A segmented
 * control's thumb has its shadow below it, outside that size, so for the
 * whole fade the thumb came up with no shadow and the shadow landed on the
 * last frame: the late shadow of the original report, in the cases the
 * arrival fix left.
 *
 * Under reduced motion the selection moves from the first option to the
 * third, and on every frame of the fade in which the thumb's fill is partly
 * there, the row just below it must be partly shadowed too, not the well's
 * own colour.
 */
class SelectionMarkerFadeShadowTest {

    @Test
    fun aThumbFadingInUnderReducedMotionBringsItsShadowWithIt() {
        var selected by mutableIntStateOf(0)
        var bounds = Rect.Zero
        Scene(Side, Side, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Theme.colours.background)) {
                SegmentedControl(
                    options = listOf("Day", "Week", "Month"),
                    selectedIndex = selected,
                    onSelectedIndexChange = {},
                    modifier = Modifier.padding(Theme.spacing.xl).width(300.dp).reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            val before = scene.frames(30)
            onComposeUiThread { selected = 2 }
            val fading = List(Watched) { scene.frame() }
            val settled = scene.frames(60)

            // The third option's middle, across; down it, where the thumb's
            // fill gives way to its shadow, read from the settled frame.
            val x = (bounds.left + bounds.width * 5f / 6f).toInt()
            val middle = bounds.center.y.toInt()
            val fill = settled.getRGB(x, middle)
            val shadowRow = (middle until bounds.bottom.toInt()).first { y -> settled.getRGB(x, y) != fill }
            val well = before.getRGB(x, shadowRow)
            check(levels(settled.getRGB(x, shadowRow), well) > Tolerance) {
                "nothing under the settled thumb at $shadowRow to call its shadow, so this measures nothing"
            }
            check(levels(before.getRGB(x, middle), fill) > Tolerance) {
                "the thumb was already at the third option, so nothing faded in"
            }

            val bare = fading.mapIndexedNotNull { index, frame ->
                val thumb = levels(frame.getRGB(x, middle), before.getRGB(x, middle))
                val shadow = levels(frame.getRGB(x, shadowRow), well)
                if (thumb > Tolerance && shadow <= Tolerance) "  · frame $index: thumb $thumb levels in, its shadow none" else null
            }
            assertTrue(
                bare.isEmpty(),
                "the thumb faded in without its shadow, which arrived only once it was opaque:\n" + bare.joinToString("\n"),
            )
        }
    }

    /** The largest difference of one channel between two pixels. */
    private fun levels(p: Int, q: Int): Int = maxOf(
        abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)),
        abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF)),
        abs((p and 0xFF) - (q and 0xFF)),
    )

    private companion object {
        const val Side = 600

        /** Frames watched after the selection moves: past `Motion.fast`'s fade, with room. */
        const val Watched = 16

        /** Levels a pixel may be off and still be called the same. */
        const val Tolerance = 2
    }
}
