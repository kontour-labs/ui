package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.motion.PageTransition
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Two pages are never on screen at the same time.
 *
 * `PageTransition` faded the pages *across* each other: `fadeIn` and `fadeOut`
 * ran together for the whole of the transition, so both were painted on every
 * frame in between. Two pages of the same app are mostly the same furniture a
 * few pixels apart — a title, a bar, a row of tabs — so each of those was drawn
 * twice, offset, at partial opacity. Two half-opaque copies of dark text over a
 * light ground cover about three quarters of it, which is the report: doubled,
 * offset by a few pixels, and reading as bolder.
 *
 * ### Measured with two colours rather than with text
 *
 * Text is what the report is about and it is the wrong thing to measure: two
 * offset copies of the same glyphs at partial alpha is a blur, and telling a
 * blur from a heavier font by counting pixels needs a threshold nobody can
 * defend. So each page draws one saturated square, in different colours and in
 * different places, and the question becomes exactly the one that matters:
 * across the whole transition, is there a frame that holds ink from both?
 */
class PageFadeThroughTest {

    @Test
    fun noFrameHoldsBothPages() {
        var page by mutableStateOf(0)
        var framesWithBoth = 0
        var framesWithEither = 0

        Scene(width = 300, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                PageTransition(target = page) { which ->
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .offset(x = if (which == 0) 20.dp else 100.dp, y = 40.dp)
                                .size(40.dp)
                                .background(if (which == 0) Red else Blue)
                        )
                    }
                }
            }
        }.use { scene ->
            scene.frames(30)
            page = 1
            repeat(Frames) {
                val image = scene.frame()
                val red = image.holds(Red)
                val blue = image.holds(Blue)
                if (red && blue) framesWithBoth++
                if (red || blue) framesWithEither++
            }
        }

        assertTrue(
            framesWithEither > Frames / 2,
            "only $framesWithEither of $Frames frames held either page — the " +
                "transition drew almost nothing, so this measures nothing",
        )
        assertTrue(
            framesWithBoth == 0,
            "$framesWithBoth of $Frames frames held both pages at once. They are " +
                "fading across each other rather than through, so everything the " +
                "two pages have in common is drawn twice, offset, for the whole " +
                "of the transition.",
        )
    }

    /**
     * Whether any pixel is recognisably [colour].
     *
     * A hue test rather than an equality test: both squares are drawn through an
     * alpha, so what reaches the screen is the colour mixed with white, and the
     * channel that stays low is the one that identifies it. Half opacity is
     * plenty to be seen and plenty to be counted.
     */
    private fun BufferedImage.holds(colour: Color): Boolean {
        val wantsRed = colour == Red
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                if (g > Muted) continue
                val identified = if (wantsRed) r > b + Separation else b > r + Separation
                if (identified) return true
            }
        }
        return false
    }

    private companion object {
        val Red = Color(0xFFCC0000)
        val Blue = Color(0xFF0000CC)

        /** How many frames of the transition to sample. */
        const val Frames = 40

        /** Green stays low in both squares and is high everywhere else. */
        const val Muted = 200

        /** How far apart red and blue have to be to say which one this is. */
        const val Separation = 40
    }
}
