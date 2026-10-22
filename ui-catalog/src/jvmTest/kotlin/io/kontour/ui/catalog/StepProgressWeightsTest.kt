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
import kotlin.test.assertTrue

/**
 * Steps of different lengths: a short step and a long one, in a row and round
 * a ring, each its share of the whole.
 *
 * Asked for as *"the ability to change the proportions of each step? like they
 * should be the same by default, but i might want a short step and a long
 * step"*.
 */
class StepProgressWeightsTest {

    @Test
    fun aShortStepBeforeALongOneFillsItsQuarter() {
        val frame = Scene(width = 400, height = 80, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.padding(start = Left.dp, top = Top.dp).width(Width.dp)) {
                    StepProgress(
                        current = 1,
                        total = 2,
                        weights = listOf(1f, 3f),
                        gap = 0.dp,
                        height = 12.dp,
                        colour = Fill,
                        trackColour = Track,
                    )
                }
            }
        }.use { it.frames(40) }

        val row = (Top + 6) * Density
        val end = (0 until frame.width).last { frame.colourAt(it, row) == Fill } + 1
        val expected = (Left + Width / 4f) * Density
        assertTrue(abs(end - expected) <= 2f, "the short first step of 1 to 3 should fill to x=$expected, and fills to x=$end")
    }

    @Test
    fun aShortArcBeforeALongOneFillsItsQuarter() {
        val frame = Scene(width = 300, height = 300, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.padding(start = Left.dp, top = Top.dp)) {
                    CircularStepProgress(
                        current = 1,
                        total = 2,
                        weights = listOf(1f, 3f),
                        gap = 0.dp,
                        size = Ring.dp,
                        strokeWidth = 10.dp,
                        colour = Fill,
                        trackColour = Track,
                    )
                }
            }
        }.use { it.frames(40) }

        // A quarter of the ring is 90 degrees round from twelve o'clock.
        assertEquals(Fill, frame.onRing(60f), "inside the short first step, the ring should be filled")
        assertEquals(Track, frame.onRing(120f), "inside the long second step, the ring should be its track")
    }

    /** Without one positive weight a step, the steps are even, as they are with none. */
    @Test
    fun weightsThatDoNotFitAreEven() {
        fun firstEnd(weights: List<Float>?): Int {
            val frame = Scene(width = 400, height = 80, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Box(Modifier.padding(start = Left.dp, top = Top.dp).width(Width.dp)) {
                        StepProgress(current = 1, total = 2, weights = weights, gap = 0.dp, height = 12.dp, colour = Fill, trackColour = Track)
                    }
                }
            }.use { it.frames(40) }
            val row = (Top + 6) * Density
            return (0 until frame.width).last { frame.colourAt(it, row) == Fill } + 1
        }
        val even = firstEnd(null)
        assertEquals(even, firstEnd(listOf(1f, 3f, 5f)), "three weights for two steps should be ignored")
        assertEquals(even, firstEnd(listOf(1f, 0f)), "a step of no length should not be drawn: the weights should be ignored")
    }

    private fun BufferedImage.colourAt(x: Int, y: Int): Color = Color(getRGB(x, y)).copy(alpha = 1f)

    private fun BufferedImage.onRing(degrees: Float): Color {
        val centreX = (Left + Ring / 2f) * Density
        val centreY = (Top + Ring / 2f) * Density
        val radius = (Ring - 10) / 2f * Density
        val radians = degrees * PI / 180
        return colourAt((centreX + radius * sin(radians)).roundToInt(), (centreY - radius * cos(radians)).roundToInt())
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
