package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.MarqueeDefaults
import io.kontour.ui.motion.marquee
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * It scrolls only when it has to, and never when the user has asked it not to.
 *
 * `Modifier.marquee` is a wrapper, and a wrapper's tests are about the wrapping:
 * the pace and the [io.kontour.ui.theme.Motion.reduceMotion] rule are what this
 * library added, and both are invisible in a still. So the test watches a strip
 * of pixels over time and asks whether it moved.
 *
 * ### Why pixels rather than a state object
 *
 * There is nothing to ask. Foundation's marquee is a layout modifier with no
 * public state — no offset to read, no "is running" flag. What it does is move
 * ink, so what a test can check is whether the ink moved. That also happens to
 * be the claim worth checking, rather than a proxy for it.
 */
class MarqueeTest {

    @Test
    fun textTooWideForItsBoxScrolls() {
        assertTrue(
            movesOverTime(TooWide, reduceMotion = false),
            "a label wider than its box never moved — the marquee is not running, " +
                "so the end of the text can never be read",
        )
    }

    @Test
    fun textThatFitsStaysStill() {
        assertTrue(
            !movesOverTime(Fits, reduceMotion = false),
            "a label that fits its box is scrolling anyway — the modifier is safe " +
                "to apply unconditionally only if it does nothing in the common case",
        )
    }

    /**
     * The one animation in the library that is switched off rather than softened.
     *
     * Everything else degrades — a spring becomes a tween, a slide becomes a
     * fade. This one stops, because there is no gentler version of "forever",
     * and perpetual movement at the edge of vision is the specific thing that
     * preference exists to stop.
     */
    @Test
    fun reducedMotionStopsItEntirely() {
        assertTrue(
            !movesOverTime(TooWide, reduceMotion = true),
            "the label is still scrolling under reduced motion",
        )
    }

    /** Whether the strip of pixels under the label changes between two samples. */
    private fun movesOverTime(text: String, reduceMotion: Boolean): Boolean {
        Scene(
            width = Width,
            height = Height,
            density = Density.toFloat(),
            reduceMotion = reduceMotion,
        ) {
            Box(
                Modifier.fillMaxSize().background(Theme.colors.background).padding(16.dp),
                Alignment.CenterStart,
            ) {
                Box(Modifier.width(BoxWidth.dp)) {
                    Text(text = text, maxLines = 1, modifier = Modifier.marquee())
                }
            }
        }.use { scene ->
            // Past the initial pause, so the first sample is taken while it is
            // already under way — sampling from rest would report "still" for a
            // marquee that simply had not set off yet.
            val settled = scene.frames(framesFor(MarqueeDefaults.PauseMillis + Settle))
            val later = scene.frames(framesFor(Travel))
            return !settled.sameAs(later)
        }
    }

    private fun BufferedImage.sameAs(other: BufferedImage): Boolean {
        if (width != other.width || height != other.height) return false
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (getRGB(x, y) != other.getRGB(x, y)) return false
            }
        }
        return true
    }

    private fun framesFor(millis: Int): Int = (millis / FrameMillis).coerceAtLeast(1)

    private companion object {
        const val Density = 2
        const val Width = 600
        const val Height = 160

        /** Narrower than [TooWide] needs and wider than [Fits] needs. */
        const val BoxWidth = 120

        const val TooWide = "Elizabeth Quay Bus Station, Stand E"
        const val Fits = "Stand E"

        const val FrameMillis = 16

        /** Past the initial pause and into the travel. */
        const val Settle = 200

        /** Long enough that reading pace has moved the text a visible distance. */
        const val Travel = 400
    }
}
