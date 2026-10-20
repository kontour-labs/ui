package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.charts.Sparkline
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A sparkline whose values change moves to its new shape rather than
 * replacing it, and under reduced motion is simply there.
 *
 * Asked for as *"can we make it if the data in the sparkline changes, it
 * animates?"*.
 */
class SparklineChangeTest {

    @Test
    fun newValuesMoveTheLine() {
        val heights = watch(reduceMotion = false)
        val top = heights.first()
        val bottom = heights.last()
        assertTrue(bottom - top > Height / 2, "the line should have ended low where it began high: $heights")
        val between = heights.filter { it > top + Height * 0.2f && it < bottom - Height * 0.2f }
        assertTrue(between.isNotEmpty(), "the line jumped from high to low without passing between: $heights")
        assertTrue(heights.zipWithNext().all { (a, b) -> b >= a - 1 }, "the line should only go down on its way: $heights")
    }

    @Test
    fun underReducedMotionItIsThereAtOnce() {
        val heights = watch(reduceMotion = true)
        val bottom = heights.last()
        assertTrue(heights.drop(1).all { kotlin.math.abs(it - bottom) <= 1 }, "under reduced motion the line moved: $heights")
    }

    /**
     * The height of the line's ink in one column near its end, a frame at a
     * time from just before the values change to well after.
     */
    private fun watch(reduceMotion: Boolean): List<Int> {
        var values by mutableStateOf(listOf(1, 2, 3, 4, 5, 6, 7, 8))
        val heights = mutableListOf<Int>()
        Scene(width = 600, height = 160, reduceMotion = reduceMotion) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Sparkline(
                    values,
                    modifier = Modifier.padding(start = Left.dp, top = Top.dp).size(Width.dp, Height.dp),
                    colour = Ink,
                )
            }
        }.use { scene ->
            // Long enough for its first drawing in to finish.
            scene.renderUntil(timeoutMillis = 1_500) { false }
            heights += scene.frame().inkIn(Column)
            values = listOf(8, 7, 6, 5, 4, 3, 2, 1)
            repeat(40) { heights += scene.frame().inkIn(Column) }
            assertFalse(scene.stillAnimating(), "settled on its new values, it went on asking for frames")
        }
        return heights
    }

    /** Where the ink is in pixel column [x], from the top of the sparkline, in dp. */
    private fun BufferedImage.inkIn(x: Int): Int {
        val ys = (0 until height).filter { y -> Color(getRGB(x, y)).let { it.red < 0.5f && it.blue > 0.5f } }
        check(ys.isNotEmpty()) { "no line in column $x" }
        return (ys.sum() / ys.size) / Density - Top
    }

    private companion object {
        const val Density = 2
        const val Left = 20
        const val Top = 10
        const val Width = 200
        const val Height = 40

        /** Near the end of the line, where rising and falling are furthest apart. */
        const val Column = (Left + Width * 9 / 10) * Density

        val Ink = Color(0xFF2244CC)
    }
}
