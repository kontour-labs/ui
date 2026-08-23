package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Surface
import io.kontour.ui.overlay.CoachmarkTour
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.coachmarkStep
import io.kontour.ui.overlay.rememberCoachmarkTour
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dim has a hole in it, and the hole is over the right control.
 *
 * A coach-mark tour that dims everything is a modal with a bubble on it; the
 * spotlight is the whole of what makes "this one, here" unmistakable, and it is
 * the one part that cannot be asserted from the semantics tree — a hole in a
 * scrim has no node. So this reads the two pixels that matter: one inside the
 * lit control, one out in the dark.
 *
 * ### Two controls, so the hole has to be over a particular one
 *
 * A test with a single step passes just as well against a spotlight that lights
 * the whole screen, or one that cuts its hole in the middle regardless. Two
 * controls at known, different places means the assertion is about *which* one
 * is lit, which is the claim.
 */
class CoachmarkTourTest {

    @Test
    fun theSpotlightLightsTheStepItIsOn() {
        var tour: CoachmarkTour? = null
        var first = Rect.Zero
        var second = Rect.Zero

        Scene(width = Width, height = Height, density = Density.toFloat(), reduceMotion = true) {
            val walk = rememberCoachmarkTour(First, Second)
            tour = walk
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Theme.colors.background)) {
                    Target(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .reportBounds { first = it }
                            .coachmarkStep(walk, First, "Plan a trip", "Start here."),
                    )
                    Target(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .reportBounds { second = it }
                            .coachmarkStep(walk, Second, "Saved trips", "They live here."),
                    )
                }
            }
        }.use { scene ->
            scene.frames(4)
            // The two ends of the scale, both measured rather than assumed: the
            // control with no tour running at all, and a control the tour is
            // demonstrably not pointing at.
            val undimmed = scene.frame().at(first)

            requireNotNull(tour).start()
            val onFirst = scene.frames(30)
            val dimmed = onFirst.at(second)
            val lit = Lit(undimmed, dimmed)

            assertTrue(
                lit(onFirst.at(first)),
                "the first step's control is dimmed while its own step is showing " +
                    "— the spotlight has no hole in it, so the tour is a modal with " +
                    "a bubble rather than a light on one control",
            )
            assertTrue(
                !lit(dimmed),
                "the second step's control is undimmed during the first step — the " +
                    "hole is not over the control the tour is talking about",
            )

            requireNotNull(tour).next()
            val onSecond = scene.frames(30)

            assertTrue(
                lit(onSecond.at(second)),
                "after `next()` the second control is still in the dark — the " +
                    "spotlight did not move with the tour",
            )
            assertTrue(
                !lit(onSecond.at(first)),
                "the first control is still lit on the second step, so the tour " +
                    "lights everything it has visited rather than where it is",
            )

            requireNotNull(tour).finish()
            val done = scene.frames(30)

            assertTrue(
                lit(done.at(first)) && lit(done.at(second)),
                "the dim outlived the tour",
            )
        }
    }

    @Test
    fun itWalksForwardsAndBackAndKnowsWhereItIs() {
        var tour: CoachmarkTour? = null
        Scene(width = Width, height = Height, density = Density.toFloat(), reduceMotion = true) {
            tour = rememberCoachmarkTour(First, Second)
            Box(Modifier.fillMaxSize())
        }.use { scene ->
            scene.frames(2)
            val walk = requireNotNull(tour)

            assertTrue(!walk.isRunning, "a tour is running before anyone started it")

            walk.start()
            assertEquals(First, walk.current)
            assertEquals(1, walk.position)
            assertEquals(2, walk.count)
            assertTrue(!walk.hasPrevious && walk.hasNext)

            walk.next()
            assertEquals(Second, walk.current)
            assertTrue(walk.hasPrevious && !walk.hasNext)

            walk.previous()
            assertEquals(First, walk.current, "`previous` did not step back")

            // Past the end, which is the same as being done with it.
            walk.next()
            walk.next()
            assertTrue(!walk.isRunning, "the tour ran past its last step and kept going")
            assertTrue(walk.isFinished, "a walked-through tour did not report itself finished")
        }
    }

    /**
     * Whether a pixel is on the lit side of the scrim.
     *
     * Not equality with the undimmed colour, which the first draft used and which
     * failed for the right reason in the wrong way: the bubble's own shadow lands
     * over the control it points at and lifts it four parts in 255. The scrim
     * moves it by a hundred and twenty. So the question is which of the two
     * measured ends a pixel is nearer, and a shadow can never flip that.
     */
    private class Lit(private val undimmed: Int, private val dimmed: Int) {
        operator fun invoke(pixel: Int): Boolean =
            kotlin.math.abs(luminance(pixel) - luminance(undimmed)) <
                kotlin.math.abs(luminance(pixel) - luminance(dimmed))

        private fun luminance(pixel: Int): Int {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            return (r + g + b) / 3
        }
    }

    /** The pixel at the middle of a reported rectangle. */
    private fun BufferedImage.at(bounds: Rect): Int =
        getRGB(
            bounds.center.x.toInt().coerceIn(0, width - 1),
            bounds.center.y.toInt().coerceIn(0, height - 1),
        )

    @androidx.compose.runtime.Composable
    private fun Target(modifier: Modifier) {
        Surface(
            modifier = modifier.size(TargetSize.dp),
            color = Theme.colors.surfaceSunken,
            content = {},
        )
    }

    private companion object {
        const val Density = 2
        const val Width = 800
        const val Height = 800
        const val TargetSize = 80

        const val First = "plan"
        const val Second = "saved"
    }
}
