package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.PageIndicatorStyle
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.RadioButton
import io.kontour.ui.components.selection.Rating
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The three things stage 3 added, none of which is visible in a golden at rest.
 *
 * - **A checkbox and a radio answer the press**, not only the release. A
 *   switch's thumb has stretched under the finger since it was written; these two
 *   sat still until the value committed, so the same tap read as responsive on
 *   one control and dead on the others.
 * - **A rating's marks fill rather than appear.** Five stars used to blink on
 *   between one frame and the next.
 * - **The worm stretches.** At rest it is a dot; between two pages it is a pill
 *   spanning both, which is the entire difference from the dots style.
 */
class SelectionAnimationTest {

    @Test
    fun aCheckboxRespondsToThePressBeforeTheValueCommits() {
        var bounds = Rect.Zero
        var ink = 0
        var pressedInk = 0

        Scene(width = 200, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Checkbox(checked = false, onCheckedChange = {}, modifier = Modifier.reportBounds { bounds = it })
            }
        }.use { scene ->
            ink = scene.frames(4).inkCount()
            // Held, not tapped: what is being measured is the response to a
            // finger that has not yet decided.
            scene.press(bounds.center)
            pressedInk = scene.frames(6).inkCount()
            scene.release(bounds.center)
        }

        assertTrue(
            pressedInk > ink + 20,
            "an unchecked checkbox drew $ink pixels at rest and $pressedInk under " +
                "a held press — it is not answering the finger",
        )
    }

    @Test
    fun aRadioButtonRespondsToThePressBeforeTheValueCommits() {
        var bounds = Rect.Zero
        var ink = 0
        var pressedInk = 0

        Scene(width = 200, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                RadioButton(selected = false, onClick = {}, modifier = Modifier.reportBounds { bounds = it })
            }
        }.use { scene ->
            ink = scene.frames(4).inkCount()
            scene.press(bounds.center)
            pressedInk = scene.frames(6).inkCount()
            scene.release(bounds.center)
        }

        assertTrue(
            pressedInk > ink + 20,
            "an unselected radio drew $ink pixels at rest and $pressedInk under a " +
                "held press — the dot is not growing under the finger",
        )
    }

    @Test
    fun aRatingsMarksFillRatherThanAppear() {
        var score by mutableStateOf(0f)
        val inks = mutableListOf<Int>()

        Scene(width = 600, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Rating(value = score, contentDescription = "Average rating")
            }
        }.use { scene ->
            scene.frames(4)
            score = 3f
            repeat(10) { inks += scene.frame().inkCount() }
        }

        // Filled stars are solid where empty ones are outlines, so the ink count
        // rises as they fill. Without the easing it rises once and stops.
        val distinct = inks.distinct().size
        assertTrue(
            distinct >= 4,
            "the marks took only $distinct distinct amounts of ink across ten " +
                "frames, so they appeared rather than filled: $inks",
        )
        assertTrue(inks.last() > inks.first(), "the marks never filled at all: $inks")
    }

    @Test
    fun theWormStretchesBetweenTwoDots() {
        var atRest = 0
        var midTravel = 0
        var dots = Rect.Zero
        var pages = Rect.Zero

        Scene(width = 400, height = 300) {
            val carousel = rememberCarouselState { 4 }
            Column(Modifier.fillMaxSize().background(Color.White)) {
                Carousel(
                    state = carousel,
                    contentDescription = "Pages",
                    modifier = Modifier.fillMaxWidth().height(100.dp).reportBounds { pages = it },
                ) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                PageIndicator(
                    carousel,
                    style = PageIndicatorStyle.Worm,
                    modifier = Modifier.reportBounds { dots = it },
                )
            }
        }.use { scene ->
            atRest = scene.frames(6).darkestRun(dots.center.y.toInt())
            // Half a page along, and *held there*: releasing would snap to one
            // page or the other, and halfway is the only position where a worm
            // is anything but a dot.
            scene.drag(
                from = pages.alongX(0.75f),
                to = pages.alongX(0.25f),
                steps = 20,
                release = false,
            )
            midTravel = scene.frames(2).darkestRun(dots.center.y.toInt())
            scene.release(pages.alongX(0.25f))
        }

        assertTrue(atRest > 0, "no worm was drawn at all")
        assertTrue(
            midTravel > atRest * 2,
            "the worm was ${atRest}px wide at rest and ${midTravel}px halfway " +
                "between two pages — it is not stretching, which is the whole of " +
                "what this style is",
        )
    }

    /** How many pixels are not the white ground. */
    private fun BufferedImage.inkCount(): Int {
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = getRGB(x, y)
                if (
                    abs((rgb shr 16 and 0xFF) - 255) > 24 ||
                    abs((rgb shr 8 and 0xFF) - 255) > 24 ||
                    abs((rgb and 0xFF) - 255) > 24
                ) {
                    count++
                }
            }
        }
        return count
    }

    /**
     * The widest run of *near-black* pixels in the row through the dots.
     *
     * The worm is `primary` and the dots under it are `outlineStrong`, so a
     * threshold between the two finds the pill and nothing else.
     */
    private fun BufferedImage.darkestRun(row: Int): Int {
        var best = 0
        var run = 0
        for (x in 0 until width) {
            val rgb = getRGB(x, row)
            val dark = (rgb shr 16 and 0xFF) < 90
            run = if (dark) run + 1 else 0
            if (run > best) best = run
        }
        return best
    }
}
