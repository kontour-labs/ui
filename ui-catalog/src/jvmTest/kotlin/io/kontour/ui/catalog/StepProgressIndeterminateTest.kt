package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.StepProgress
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An indeterminate `StepProgress` travels; it does not step.
 *
 * `current = null` means "there are four steps and I do not know which one you
 * are on". It drew that by lighting one whole segment at a time —
 * `(phase * total).toInt()`, which takes a continuous phase and keeps only its
 * integer part — so a row whose entire meaning is "no steps, just going" was
 * animated as a sequence of steps. The band that fixes it was already in the
 * file for the `working` case; the indeterminate case simply was not using it.
 *
 * ### Measured as where the ink is, over time
 *
 * The centroid of the lit pixels across a run of frames. A segment snapping on
 * and off puts that centroid at one of four places and nowhere in between; a
 * band travelling along the row puts it somewhere new on every frame. Counting
 * the distinct positions tells the two apart without needing to know how fast
 * either of them is supposed to be.
 */
class StepProgressIndeterminateTest {

    @Test
    fun anIndeterminateRowTravelsRatherThanStepping() {
        val places = mutableSetOf<Int>()
        var blank = 0

        // Tall enough for the padding *and* the row. At 80px — 40dp — the
        // 20dp of padding on each side left the four-dp canvas no height at
        // all, and a first draft of this reported that nothing had been drawn
        // because nothing had been.
        Scene(width = 400, height = 120) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.padding(20.dp).width(320.dp)) {
                    StepProgress(current = null, total = Segments)
                }
            }
        }.use { scene ->
            // Past the first frames, where the infinite transition has not
            // started moving and every sample would be the same one.
            scene.frames(4)
            repeat(Frames) {
                val centroid = scene.frame().litCentroid()
                if (centroid == null) blank++ else places += centroid
            }
        }

        // A band that enters one end of the row as it leaves the other is off
        // both ends for a moment at the turn of each cycle, which is what
        // `LinearProgress` does too. A handful of blank frames is that; most of
        // them blank is a row that is not drawing.
        assertTrue(
            blank < Frames / 4,
            "$blank of $Frames frames had no lit ink at all — the row is dark " +
                "most of the time rather than showing something travelling",
        )
        assertTrue(
            places.size > Segments * 3,
            "the lit ink sat in ${places.size} distinct places over $Frames " +
                "frames of a $Segments-segment row. A travelling band is " +
                "somewhere new on almost every frame; a count near $Segments is " +
                "one whole segment at a time, which is a stepped animation for " +
                "something with no steps.",
        )
    }

    /**
     * The mean x of the lit pixels, or null if none are lit.
     *
     * Lit rather than tracked, told apart by weight: the fill is
     * `Theme.colours.primary`, which is `Palette.Ink`, and the track is
     * `Theme.colours.outline`, a light grey. Both are neutral, so a first draft
     * of this that asked which pixels were *colourful* found none of either and
     * reported that nothing had been drawn.
     */
    private fun BufferedImage.litCentroid(): Int? {
        var sum = 0L
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = getRGB(x, y)
                val lum = (((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF)) / 3
                if (lum > Darkness) continue
                sum += x
                count++
            }
        }
        return if (count == 0) null else (sum / count).toInt()
    }

    private companion object {
        const val Segments = 4
        const val Frames = 40

        /** Ink, not the grey track it runs along and not the white behind it. */
        const val Darkness = 128
    }
}
