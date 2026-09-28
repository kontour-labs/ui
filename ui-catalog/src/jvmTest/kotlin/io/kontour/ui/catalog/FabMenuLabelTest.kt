package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.action.FabMenu
import io.kontour.ui.components.action.FabMenuLayout
import io.kontour.ui.overlay.OverlayHost
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A horizontal FAB menu names its buttons, above them and turned.
 *
 * `showLabels` defaulted to `layout == Vertical` because a row of buttons has
 * nowhere to put a horizontal label: two items are 8dp apart and the words are
 * forty wide. So the one arrangement where a reader most needs to be told what
 * three unlabelled icons do was the one arrangement that did not tell them —
 * which is how it was reported, as an accessibility point rather than a visual
 * one. Assistive tech was never affected: the labels were emitted and only not
 * drawn.
 *
 * Above and rotated is what fits, and it fits by arithmetic rather than by
 * looking about right. Two labels turned by θ and sitting `d` apart have
 * `d × sin θ` of perpendicular clearance between them, so at 45° items 56dp apart
 * leave 40dp for a chip about 32dp tall. At 30° the same items leave 28dp and the
 * chips touch.
 *
 * ### Why area rather than a run count
 *
 * The obvious test is to scan a line across the label band and count separate
 * runs of ink. It does not survive contact: each chip carries a shadow whose
 * bleed is wider than the clearance between two chips, and each chip has dark
 * text on it, so a line across one chip is already several runs and a line across
 * three is a number nobody can predict.
 *
 * Congruence is the way in. Every label here is the *same word*, so every chip is
 * the same shape, and three chips that do not overlap ink exactly three times the
 * area of one. Overlap is the only thing that can make the total come out short.
 * That is the property under test stated as an equation, and it needs no
 * threshold chosen by eye — only "is this pixel the chip's surface or not", which
 * a light chip on a dark page answers cleanly and a shadow does not reach.
 */
class FabMenuLabelTest {

    @Test
    fun threeLabelsOnAHorizontalMenuDoNotOverlap() {
        val one = labelArea(items = 1)
        val three = labelArea(items = 3)

        assertTrue(one > 0, "a one-item horizontal menu drew no label at all")
        assertEquals(
            one * 3, three,
            "one label inks $one pixels above the button row and three ink $three, " +
                "where three times $one is ${one * 3}. The labels are the same word " +
                "and therefore the same shape, so a shortfall is two chips sharing " +
                "pixels — which at this angle and this spacing they must not.",
        )
    }

    /**
     * And they are drawn *above* the buttons rather than beside them.
     *
     * The vertical menu's labels sit level with their buttons on whichever side
     * has room, and reusing that placement in a row is the failure mode worth
     * pinning: it would put each label over the button next to it, which reads as
     * a label for the wrong action rather than as a layout problem.
     */
    @Test
    fun theLabelsSitAboveTheButtonRow() {
        var anchor = Rect.Zero
        val frame = render(items = 3, onAnchor = { anchor = it })

        val band = 0 until (anchor.center.y - ItemClearance * Density).toInt()
        assertTrue(
            band.any { y -> frame.chipPixelsIn(y) > 0 },
            "nothing was drawn above the button row, so the labels are either " +
                "missing or level with their buttons — and level, in a row, means " +
                "over the neighbouring button.",
        )
    }

    /** Chip pixels strictly above the item circles, which is where a label belongs. */
    private fun labelArea(items: Int): Int {
        var anchor = Rect.Zero
        val frame = render(items = items, onAnchor = { anchor = it })
        val top = (anchor.center.y - ItemClearance * Density).toInt()
        return (0 until top).sumOf { y -> frame.chipPixelsIn(y) }
    }

    /**
     * Pixels in row [y] that are the chip's own surface.
     *
     * The page is dark and a raised surface is light, so one channel separates
     * them outright. A shadow over a dark page is darker still and never reaches
     * the threshold, which is the whole reason for choosing a dark page.
     */
    private fun BufferedImage.chipPixelsIn(y: Int): Int =
        (0 until width).count { x -> (getRGB(x, y) shr 8 and 0xFF) > SurfaceChannel }

    private fun render(items: Int, onAnchor: (Rect) -> Unit): BufferedImage {
        val expanded = mutableStateOf(true)
        var frame: BufferedImage? = null
        Scene(width = Width, height = Height, density = Density.toFloat()) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Page), Alignment.BottomEnd) {
                    FabMenu(
                        expanded = expanded.value,
                        onExpandedChange = { expanded.value = it },
                        icon = Tabler.Outline.Plus,
                        contentDescription = "Add",
                        layout = FabMenuLayout.Horizontal,
                        modifier = Modifier.reportBounds(onAnchor),
                    ) {
                        // The same word every time, deliberately: the assertion is
                        // that three congruent chips ink three times one chip's
                        // area, and three different words would be three different
                        // areas with nothing to compare.
                        repeat(items) { index ->
                            item(Tabler.Outline.Star, "Nearby", onClick = {})
                        }
                    }
                }
            }
        }.use { scene ->
            // Past the stagger and past each item's own spring, so every label is
            // at full opacity and the area is not being read mid-fade.
            frame = scene.frames(80)
        }
        return requireNotNull(frame)
    }

    private companion object {
        const val Density = 2

        /**
         * A window wide enough that the run is not compressed, with the button
         * where a real one goes.
         *
         * `fabMenuGeometry` divides whatever room is left after the first item
         * among the rest, so a menu with less room than it wants gets a *tighter*
         * step rather than items piled on the wall — which is the right behaviour
         * and the wrong thing to measure labels against. A centred button on a
         * 300dp window halves the room and squeezes three 40dp circles into 29dp
         * steps, and at that spacing the labels genuinely do overlap, correctly.
         * The bottom corner is where a speed dial lives.
         */
        const val Width = 800
        const val Height = 420

        /**
         * How far above the anchor's centre the label band starts, in dp.
         *
         * The items sit on the anchor's own row, so half of the largest footprint
         * — 48dp on Android's touch target — clears every circle. 32dp is past
         * that and still below the labels, which start a further `LabelGap` up.
         */
        const val ItemClearance = 32

        /** Dark enough that nothing raised or shadowed can be confused with it. */
        val Page = Color(0xFF101820)

        /** A raised surface is light; a shadow over [Page] is not. */
        const val SurfaceChannel = 0x90
    }
}
