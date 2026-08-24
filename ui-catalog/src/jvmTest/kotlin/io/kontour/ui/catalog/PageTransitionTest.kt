package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.motion.PageTransition
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The element that is on both pages travels between them.
 *
 * That is the whole claim, and it is the one a screenshot cannot make: at rest,
 * before and after look exactly like two pages. So this flips the target with the
 * clock in hand and asks where the shared element is drawn partway through.
 *
 * ### Found by colour
 *
 * The hero is the only thing on either page drawn in a colour nothing else uses,
 * so its bounding box is "every pixel of that colour" — no test tags, no
 * semantics, and it keeps working while the element is being rendered in the
 * transition's own overlay rather than in either page.
 */
class PageTransitionTest {

    @Test
    fun aSharedElementIsPartwayBetweenTheTwoPages() {
        val page = mutableStateOf(Small)
        Scene(width = Size, height = Size, density = 1f) { Pages(page) }.use { scene ->
            val atSmall = scene.frames(20).heroBounds()
            page.value = Large
            val partway = scene.frames(MidFrames).heroBounds()
            val atLarge = scene.frames(90).heroBounds()

            assertTrue(
                atSmall != null && atLarge != null && partway != null,
                "the hero was not drawn in one of the three frames: small=$atSmall " +
                    "partway=$partway large=$atLarge",
            )
            requireNotNull(atSmall); requireNotNull(atLarge); requireNotNull(partway)

            assertTrue(
                atSmall.width < atLarge.width,
                "the two pages draw the hero at ${atSmall.width}px and " +
                    "${atLarge.width}px — the test needs them to differ before it " +
                    "can tell whether anything moved between them",
            )
            assertTrue(
                partway.width > atSmall.width && partway.width < atLarge.width,
                "partway through, the hero is ${partway.width}px wide, and the two " +
                    "pages draw it at ${atSmall.width} and ${atLarge.width} — it is " +
                    "not between them, so it jumped rather than morphed",
            )
            assertTrue(
                partway.left > atSmall.left && partway.left < atLarge.left,
                "partway through, the hero's left edge is at ${partway.left} and the " +
                    "two pages put it at ${atSmall.left} and ${atLarge.left} — it " +
                    "changed size without travelling",
            )
        }
    }

    /**
     * Under reduced motion there is no morph at all.
     *
     * Not a slower one: an element flying across the screen is the clearest case
     * that preference covers, and half a morph is still something travelling a
     * long way.
     *
     * Asserted over **every** frame of the change rather than at one sampled
     * moment. An earlier draft looked for both heroes drawn at once, which is
     * true of a cross-fade and is only true for as long as the outgoing page is
     * still visible — under reduced motion the exit runs in 150ms, so the test
     * passed or failed on where its one sample happened to land. The invariant
     * does not need a moment: with no morph, the hero is only ever drawn at one
     * of its two sizes, or at both. Never at a size in between.
     */
    @Test
    fun reducedMotionCrossFadesRatherThanMorphing() {
        val page = mutableStateOf(Small)
        Scene(width = Size, height = Size, density = 1f, reduceMotion = true) {
            Pages(page)
        }.use { scene ->
            scene.frames(20)
            page.value = Large

            val between = (1..TransitionFrames)
                .mapNotNull { scene.frame().heroBounds() }
                .filter { it.width > SmallSize + Slack && it.width < LargeSize - Slack }

            assertTrue(
                between.isEmpty(),
                "with reduced motion on, the hero was drawn " +
                    "${between.map { it.width }} px wide during the change — the two " +
                    "pages draw it at $SmallSize and $LargeSize, so anything between " +
                    "them is a bounds morph the preference asked for none of",
            )
        }
    }

    /** Where the hero was drawn, or null if it was not. */
    private fun BufferedImage.heroBounds(): Bounds? {
        var left = width
        var right = -1
        var top = height
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (isHero(getRGB(x, y))) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else Bounds(left, top, right - left + 1, bottom - top + 1)
    }

    private fun BufferedImage.hasHeroAt(x: Int, y: Int): Boolean = isHero(getRGB(x, y))

    /**
     * Whether a pixel is the hero, allowing for it being partly faded.
     *
     * Not equality with the hero's colour, which the first draft used: a
     * cross-fade draws it at partial alpha over the page, so during the very
     * transition under test not one pixel matches exactly and the hero reads as
     * absent. Magenta over any grey stays magenta — red and blue both well above
     * green — however far the fade has got.
     */
    private fun isHero(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return r - g > HueMargin && b - g > HueMargin
    }

    private data class Bounds(val left: Int, val top: Int, val width: Int, val height: Int)

    @androidx.compose.runtime.Composable
    private fun Pages(page: MutableState<String>) {
        PageTransition(target = page.value, modifier = Modifier.fillMaxSize()) { current ->
            Box(Modifier.fillMaxSize().background(Theme.colors.background)) {
                if (current == Small) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .sharedElement(HeroKey)
                            .size(SmallSize.dp)
                            .background(Hero)
                    )
                } else {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .sharedElement(HeroKey)
                            .size(LargeSize.dp)
                            .background(Hero)
                    )
                }
            }
        }
    }

    private companion object {
        const val Size = 400
        const val SmallSize = 60
        const val LargeSize = 200

        const val Small = "list"
        const val Large = "detail"
        const val HeroKey = "hero"

        /**
         * A colour nothing in the theme uses, so "the hero" is a pixel test with
         * no ambiguity in it.
         */
        val Hero = Color(0xFFFF00FF)

        /** Well clear of any grey, and reached long before a fade completes. */
        const val HueMargin = 40

        /** Long enough to cover the whole change at either setting. */
        const val TransitionFrames = 40

        /** Antialiasing at the edges, and nothing more. */
        const val Slack = 4

        /** Into the spring, well short of its landing. */
        const val MidFrames = 6
    }
}
