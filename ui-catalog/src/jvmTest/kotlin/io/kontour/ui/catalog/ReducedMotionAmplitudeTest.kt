package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.kontour.ui.overlay.AlertDialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reduced motion takes the amplitude out of the largest transform on screen.
 *
 * `Motion`'s four helpers can only make a movement *shorter* — `tweenDefault`
 * and `tweenSlow` swap their duration, `springOrTween` swaps a spring for a
 * tween. None of them can make one *smaller*, so anything whose objection is
 * "it moves too far" rather than "it takes too long" has to read
 * `motion.reduceMotion` for itself. The `overlay/`, `nav/`, `sheet/` and
 * `adaptive/` packages contained no such read at all.
 *
 * The biggest instance by a distance: opening any sheet scales the **whole
 * viewport** back to 94% behind it, and during a drag the screen scales
 * continuously under the finger with no spec in the path to shorten. Reported
 * from a phone, under "reduce motion needs to apply to a few more places".
 *
 * That it is an inconsistency rather than a matter of taste is settled inside
 * the library: `Transitions.fadeThrough` already drops its
 * `scaleIn(initialScale = 0.94f)` under the same preference, and
 * `Indication.kt` drops the press-shrink. The same 0.94, gated in one place and
 * not the other.
 *
 * ### The measurement
 *
 * The content behind the sheet is solid red and fills the host; what it vacates
 * when it recedes is filled black by `backdropGround`. So the depth of the black
 * band at the top of the screen *is* the amplitude of the transform, in pixels,
 * and reduced motion has to take it to zero.
 */
class ReducedMotionAmplitudeTest {

    private val width = 600
    private val height = 800

    /** How many rows of the band the receding content leaves at the top. */
    private fun bandDepth(reduceMotion: Boolean): Int {
        var image: BufferedImage? = null
        Scene(width, height, reduceMotion = reduceMotion) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.Red))
                ModalBottomSheet(visible = true, onDismissRequest = {}) {
                    SheetHeader { +"Settled" }
                }
            }
        }.use { scene ->
            // Well past the entry spring: the claim is about where the backdrop
            // comes to rest, not about a frame inside the animation.
            image = scene.frames(60)
        }
        val frame = requireNotNull(image) { "nothing rendered" }
        val x = width / 2
        var rows = 0
        while (rows < frame.height && isBand(frame.getRGB(x, rows))) rows++
        return rows
    }

    /**
     * How far the appearing dialog's left edge travels, in pixels.
     *
     * Every dialog, menu, popover, tooltip and command palette in the library
     * grows or settles into place through one `Modifier.overlayAppearance`, so
     * it is the second largest amplitude on screen after the backdrop. A dialog
     * starts at 1.06 and settles *down* onto the screen, which means its left
     * edge starts outside where it will finish and walks inward.
     *
     * Sampled across the frames of the entry rather than compared at rest,
     * because at rest the two settings draw the same picture — the whole
     * question is what happens on the way in. Reduced motion also *shortens*
     * the animation, so a single chosen frame would be comparing two different
     * points of two different curves; the travel of the edge across all of them
     * is indifferent to that.
     */
    private fun dialogEdgeTravel(reduceMotion: Boolean): Int {
        val edges = mutableListOf<Int>()
        Scene(width, height, reduceMotion = reduceMotion) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.Red))
                AlertDialog(
                    visible = true,
                    onDismissRequest = {},
                    confirmLabel = "Remove",
                    onConfirm = {},
                ) {
                    +"Remove this favourite?"
                }
            }
        }.use { scene ->
            repeat(16) {
                val frame = scene.frame()
                val edge = panelLeftEdge(frame)
                if (edge >= 0) edges += edge
            }
        }
        assertTrue(edges.size >= 4, "the dialog was never on screen to measure")
        return (edges.max() - edges.min())
    }

    /** The leftmost bright pixel on the middle row: the panel's edge over the scrim. */
    private fun panelLeftEdge(image: BufferedImage): Int {
        val y = image.height / 2
        for (x in 0 until image.width) {
            val argb = image.getRGB(x, y)
            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF
            // The panel is `surfaceRaised` — near-white and neutral. Everything
            // else on this row is red under a scrim, so any pixel whose green
            // channel is high is the panel.
            if (g > 160 && b > 160) return x
        }
        return -1
    }

    /**
     * Whether a pixel is the ground rather than the (dimmed, blurred) content.
     *
     * The band is `Color.Black`; the content is red under a scrim, which darkens
     * it a long way but leaves the red channel far ahead of the other two. A
     * threshold on that difference is indifferent to how heavy the scrim is.
     */
    private fun isBand(argb: Int): Boolean {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return r - maxOf(g, b) < 12
    }

    @Test
    fun aSheetsBackdropDoesNotRecedeUnderReducedMotion() {
        val moving = bandDepth(reduceMotion = false)
        val still = bandDepth(reduceMotion = true)
        assertTrue(
            moving > 0,
            "the backdrop did not recede at all with motion on, so this test is " +
                "measuring nothing. Either `BackdropStyle.BlurAndScale` stopped " +
                "being what a modal sheet asks for, or the ground stopped being " +
                "drawn behind it.",
        )
        assertEquals(
            0,
            still,
            "opening a sheet pushed the whole screen back by ${still}px at the " +
                "top even though the reader has asked for reduced motion — " +
                "against ${moving}px with motion on, so the preference changed " +
                "nothing. `Motion`'s helpers cannot express this: they shorten a " +
                "movement and cannot shrink one, so `Backdrop` has to read " +
                "`motion.reduceMotion` itself.",
        )
    }

    @Test
    fun anOverlayFadesInPlaceUnderReducedMotion() {
        val moving = dialogEdgeTravel(reduceMotion = false)
        val still = dialogEdgeTravel(reduceMotion = true)
        assertTrue(
            moving > 0,
            "the dialog's edge did not move at all with motion on, so this test " +
                "is measuring nothing — `overlayAppearance` has stopped scaling.",
        )
        assertEquals(
            0,
            still,
            "the dialog's left edge travelled ${still}px on its way in under " +
                "reduced motion, against ${moving}px with motion on. Every " +
                "dialog, menu, popover, tooltip and palette appears through " +
                "`overlayAppearance`; under the preference it should fade in " +
                "place, the way `Transitions.fadeThrough` already does.",
        )
    }
}
