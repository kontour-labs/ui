package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.CircularStepProgress
import io.kontour.ui.components.display.StepProgress
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Progress within a step: the steps before it full, and the step itself filled
 * as far as it has got, in a row and round a ring.
 *
 * Asked for as *"i want to be able to add progress within a step (from 0-1,
 * just like the existing linear/circular progress components)"*.
 */
class StepProgressWithinTest {

    @Test
    fun aRowFillsTheStepAsFarAsItHasGot() {
        val frame = Scene(width = 400, height = 80, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.padding(start = Left.dp, top = Top.dp).width(Width.dp)) {
                    StepProgress(
                        current = 3,
                        total = 4,
                        stepProgress = 0.5f,
                        gap = 0.dp,
                        height = 12.dp,
                        colour = Fill,
                        trackColour = Track,
                    )
                }
            }
        }.use { it.frames(40) }

        val row = (Top + 6) * Density
        val inked = (0 until frame.width).filter { frame.colourAt(it, row) == Fill }
        val end = inked.last() + 1
        val expected = (Left + Width * 2.5f / 4) * Density
        assertTrue(
            abs(end - expected) <= 2f,
            "two steps done and the third half way should fill to x=$expected, and the fill ends at x=$end",
        )
    }

    @Test
    fun aRingFillsTheStepAsFarAsItHasGot() {
        val frame = Scene(width = 300, height = 300, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.padding(start = Left.dp, top = Top.dp)) {
                    CircularStepProgress(
                        current = 2,
                        total = 4,
                        stepProgress = 0.5f,
                        gap = 0.dp,
                        size = Ring.dp,
                        strokeWidth = 10.dp,
                        colour = Fill,
                        trackColour = Track,
                    )
                }
            }
        }.use { it.frames(40) }

        // A step and a half of four is 135 degrees round from twelve o'clock.
        assertEquals(Fill, frame.onRing(120f), "short of the fill's end, the ring should be filled")
        assertEquals(Track, frame.onRing(150f), "past the fill's end, the ring should be its track")
    }

    /** Measured, a step that is also `working` is a fill and not a loop that never settles. */
    @Test
    fun aMeasuredStepIsNotAlsoAChase() {
        Scene(width = 400, height = 80) {
            StepProgress(current = 2, total = 4, working = true, stepProgress = 0.3f)
        }.use { scene ->
            scene.frames(90)
            assertFalse(scene.stillAnimating(), "a step measured at 30% went on asking for frames as if it were working")
        }
    }

    private fun BufferedImage.colourAt(x: Int, y: Int): Color = Color(getRGB(x, y)).copy(alpha = 1f)

    /** The ring's colour on its centre line, [degrees] clockwise from twelve o'clock. */
    private fun BufferedImage.onRing(degrees: Float): Color {
        val centre = (Left + Ring / 2f) * Density to (Top + Ring / 2f) * Density
        val radius = (Ring - 10) / 2f * Density
        val radians = degrees * PI / 180
        val x = centre.first + radius * sin(radians)
        val y = centre.second - radius * cos(radians)
        return colourAt(x.roundToInt(), y.roundToInt())
    }

    private companion object {
        const val Density = 2
        const val Left = 20
        const val Top = 10
        const val Width = 160
        const val Ring = 100

        val Fill = Color(0xFF2244CC)
        val Track = Color(0xFF999999)
    }
}
