package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The receded screen's corner arrives gradually rather than all at once.
 *
 * Reported alongside the inset: "that animation just snaps into place, can we
 * make it ramp a bit smoother?" The travel was never the problem — 12dp over
 * 220ms is smooth by any measure. The **corner** was: the clip switched on at
 * `f > 0`, and on the first frame that clipped at all the content was still full
 * size and suddenly had a 34dp bite taken out of each of its corners. One frame,
 * the largest single change in the whole animation, and it lands before the
 * screen has visibly moved.
 *
 * So the radius ramps with the fraction — from the display's own corner at rest,
 * where a clip is invisible because the bezel already draws that curve, to the
 * settled one when the screen is fully back.
 *
 * ### Measured at the corner, against the edge
 *
 * A corner's radius is not a number this scene can ask for, and the shapes are
 * private. What it can do is count how much of the corner has been cut away: a
 * square corner shows content right up to the pixel, and a round one shows the
 * band's black there instead. So the measurement is "how far in from the corner
 * does the content start", taken along the diagonal, and the claim is that it
 * grows rather than arriving whole.
 *
 * Against a *fixed* reference on the same frame — the content's straight edge —
 * because the content is also scaling, and an absolute pixel count would move
 * with the scale whether or not the radius did.
 */
class BackdropCornerRampTest {

    @Test
    fun theCornerGrowsWithTheRecedeRatherThanArrivingWhole() {
        val bites = bitesThroughTheRecede()
        val settled = bites.last()
        val firstCut = bites.first { it > 0 }

        assertTrue(
            settled > 4,
            "the content's corner was never rounded at all, even fully receded: " +
                "the sequence was $bites. This measured nothing.",
        )
        assertTrue(
            firstCut < settled / 3,
            "the first frame that clipped at all cut ${firstCut}px into the corner, " +
                "against ${settled}px settled — the sequence was $bites. That is the " +
                "snap, and it is a corner rather than a scale: the clip switches on " +
                "at `f > 0`, and with a constant radius the largest single change in " +
                "the animation lands on the frame before the screen has visibly " +
                "moved.",
        )
    }

    /**
     * How deep the corner is cut, once per frame of the recede.
     *
     * The first draft sampled two fixed frame numbers and passed against the very
     * behaviour it was written to catch. Two mistakes, both worth recording
     * because both look reasonable: the early frame was taken before the fraction
     * had left zero, so *nothing* was clipped and both versions measured the same
     * nought; and the diagonal walk started at the host's own corner rather than
     * the content's, so most of what it counted was the side margin the recede
     * was opening.
     *
     * A sequence fixes the first — whatever frame the clip arrives on is in it —
     * and measuring from the content's own corner fixes the second.
     */
    private fun bitesThroughTheRecede(): List<Int> {
        var open by mutableStateOf(false)
        val bites = mutableListOf<Int>()

        Scene(width = Width, height = Height, density = Density.toFloat()) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ModalBottomSheet(visible = open, onDismissRequest = {}) {
                    Text("Rename favourite")
                }
            }
        }.use { scene ->
            scene.frames(3)
            open = true
            repeat(RecedeFrames) { bites += scene.frame().cornerBite() }
        }
        return bites
    }

    /**
     * How far along the diagonal from the *content's own* top-left corner the
     * content begins.
     *
     * Zero is a square corner. The band behind a receded screen is black and the
     * content is white, so "content" is the first light pixel — no threshold
     * picked by eye, because the two things being told apart are the extremes of
     * the range.
     */
    private fun BufferedImage.cornerBite(): Int {
        // The content's own edges, which move as it scales: an absolute corner
        // would count the margin the recede is opening rather than the radius.
        val top = (0 until Height).firstOrNull { y -> isLight(Width / 2, y) } ?: return 0
        val left = (0 until Width).firstOrNull { x -> isLight(x, top + Inboard) } ?: return 0

        var step = 0
        while (step < Width / 4 && !isLight(left + step, top + step)) step++
        return step
    }

    /** White content against the band's black, with nothing in between to confuse. */
    private fun BufferedImage.isLight(x: Int, y: Int): Boolean =
        (getRGB(x, y) shr 8 and 0xFF) > LightChannel

    private companion object {
        const val Density = 2
        const val Width = 600
        const val Height = 900

        /** Long enough for the sheet to open and settle at its detent. */
        const val RecedeFrames = 40

        /**
         * How far below the content's top edge to look for its left edge.
         *
         * Clear of the corner being measured and well above the sheet, so it
         * finds the content's straight side rather than its curve.
         */
        const val Inboard = 200

        /** Halfway, which separates white content from a black band outright. */
        const val LightChannel = 0x80
    }
}
