package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Surface
import io.kontour.ui.overlay.CoachmarkStep
import io.kontour.ui.overlay.CoachmarkTour
import io.kontour.ui.overlay.CoachmarkTransition
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.coachmarkStep
import io.kontour.ui.overlay.rememberCoachmarkTour
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tour's marks go from one step to the next as asked — a fade, or a ball —
 * in any tone, to a step that points at nothing, and across screens.
 *
 * Asked for as *"better guided tours that takes the user across the app"*, *"a
 * coachmark that acts as a modal, not pointing to anything in particular"*,
 * *"animate between the marks as they change … one for just fading one out and
 * fading the new one in, and another for the mark shrinking into ball, zipping to
 * it's new position, and expanding back into a new mark"*, and *"allow different
 * colours that aren't just the accent colour"*.
 *
 * Read from pixels: how much of the screen is the mark's colour, frame by frame,
 * says whether it shrank to a ball or faded, and where the dim is says whether the
 * page stayed dimmed once.
 */
class CoachmarkTransitionTest {

    /** A card for a step that points at nothing, in the middle, over a dim with no hole. */
    @Test
    fun aStepThatPointsAtNothingIsACardInTheMiddle() {
        var tour: CoachmarkTour? = null
        var accent = Color.Unspecified
        Scene(width = Width, height = Height, density = 2f, reduceMotion = true) {
            tour = rememberCoachmarkTour(Welcome)
            accent = Theme.colours.accent.solid
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Theme.colours.background)) {
                    CoachmarkStep(tour!!, Welcome, title = "Welcome aboard", message = "A minute's tour of the app.")
                }
            }
        }.use { scene ->
            val undimmed = luminance(scene.frames(4).getRGB(10, 10))
            tour!!.start()
            val shown = scene.frames(30)
            assertTrue(undimmed - luminance(shown.getRGB(10, 10)) > 40, "a step that points at nothing did not dim the screen")
            assertTrue(undimmed - luminance(shown.getRGB(Width - 10, Height - 10)) > 40, "the dim has a hole in it, with nothing to light")
            val card = shown.boundsOf(accent)
            assertTrue(card != null, "no card was drawn")
            assertTrue(
                abs(card.center.x - Width / 2f) < 12f && abs(card.center.y - Height / 2f) < 12f,
                "the card is at $card, not in the middle of the ${Width}x$Height screen",
            )
        }
    }

    /**
     * In a morph the mark becomes a ball between the steps — a little of its
     * colour, never none — and in a fade it is gone between them, never small.
     */
    @Test
    fun aMorphGoesByBallAndAFadeGoesByNothing() {
        val morph = inkBetweenSteps(CoachmarkTransition.Morph)
        val fade = inkBetweenSteps(CoachmarkTransition.Fade)
        val bubble = morph.first()
        // A ball of 10dp radius at density 2: about 1250 pixels.
        assertTrue(morph.min() > 400, "in a morph the mark's colour left the screen between steps (fewest $morph)")
        assertTrue(morph.min() < bubble / 4, "in a morph the mark never got smaller than ${morph.min()} of its $bubble — no ball")
        assertTrue(fade.min() < 200, "in a fade the mark never left before the next came: fewest ${fade.min()} of $bubble")
    }

    /** A step in a tone of its own is drawn in it. */
    @Test
    fun aStepIsDrawnInItsTone() {
        var tour: CoachmarkTour? = null
        var danger = Color.Unspecified
        var accent = Color.Unspecified
        Scene(width = Width, height = Height, density = 2f, reduceMotion = true) {
            tour = rememberCoachmarkTour(First)
            danger = Theme.colours.danger.solid
            accent = Theme.colours.accent.solid
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Theme.colours.background)) {
                    Target(Modifier.align(Alignment.Center).coachmarkStep(tour!!, First, "Delete", "Gone for good.", tone = Tone.Danger))
                }
            }
        }.use { scene ->
            scene.frames(4)
            tour!!.start()
            val shown = scene.frames(30)
            assertTrue((shown.count(danger)) > 2000, "a step in Danger was not drawn in it")
            assertEquals(0, shown.count(accent), "a step in Danger still drew the accent")
        }
    }

    /**
     * A tour goes to the screen its next step is on, and the page stays dimmed
     * once from the one screen to the other.
     */
    @Test
    fun aTourGoesAcrossScreensUnderOneDim() {
        var tour: CoachmarkTour? = null
        var second = Rect.Zero
        val went = mutableListOf<String>()
        Scene(width = Width, height = Height, density = 2f) {
            var screen by remember { mutableStateOf(First) }
            tour = rememberCoachmarkTour(First, Second) { step ->
                went += step
                screen = step
            }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Theme.colours.background)) {
                    if (screen == First) {
                        Target(Modifier.align(Alignment.TopStart).coachmarkStep(tour!!, First, "Plan a trip", "Start here."))
                    } else {
                        Target(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .reportBounds { second = it }
                                .coachmarkStep(tour!!, Second, "Saved trips", "They live here."),
                        )
                    }
                }
            }
        }.use { scene ->
            val corner = { image: BufferedImage -> luminance(image.getRGB(Width - 10, 10)) }
            val undimmed = corner(scene.frames(4))
            tour!!.start()
            val dimmed = corner(scene.frames(40))
            assertTrue(undimmed - dimmed > 40, "the tour did not dim the page")

            tour!!.next()
            val between = (1..60).map { corner(scene.frame()) }
            assertEquals(listOf(First, Second), went, "the tour was not taken to each step's screen")
            assertTrue(
                between.all { abs(it - dimmed) <= 3 },
                "between the screens the dim went from $dimmed to ${between.min()}..${between.max()}: it lifted or doubled. $between",
            )
            val there = scene.frames(20)
            assertTrue(luminance(there.at(second)) > dimmed + 40, "the second screen's step was not lit")
        }
    }

    /** A step that never turns up is passed over, the way the tour was going. */
    @Test
    fun aStepThatNeverTurnsUpIsPassedOver() {
        var tour: CoachmarkTour? = null
        Scene(width = Width, height = Height, density = 2f, reduceMotion = true) {
            tour = rememberCoachmarkTour(First, "missing", Second)
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Theme.colours.background)) {
                    Target(Modifier.align(Alignment.TopStart).coachmarkStep(tour!!, First, "Plan a trip", "Start here."))
                    Target(Modifier.align(Alignment.BottomEnd).coachmarkStep(tour!!, Second, "Saved trips", "They live here."))
                }
            }
        }.use { scene ->
            scene.frames(4)
            tour!!.start()
            scene.frames(10)
            tour!!.next()
            var frames = 0
            while (tour!!.current != Second && frames < 400) {
                scene.frame()
                frames++
            }
            assertEquals(Second, tour!!.current, "the tour stayed on a step with nothing on screen")
            tour!!.previous()
            frames = 0
            while (tour!!.current != First && frames < 400) {
                scene.frame()
                frames++
            }
            assertEquals(First, tour!!.current, "going back, the missing step was not passed over backwards")
        }
    }

    /** Next twice in a row, mid-morph, lands on the step it was sent to, lit. */
    @Test
    fun nextTwiceMidMorphLandsOnTheLastStep() {
        var tour: CoachmarkTour? = null
        var third = Rect.Zero
        Scene(width = Width, height = Height, density = 2f) {
            tour = rememberCoachmarkTour(First, Second, Third)
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Theme.colours.background)) {
                    Target(Modifier.align(Alignment.TopStart).coachmarkStep(tour!!, First, "One", "The first."))
                    Target(Modifier.align(Alignment.TopEnd).coachmarkStep(tour!!, Second, "Two", "The second."))
                    Target(Modifier.align(Alignment.BottomCenter).reportBounds { third = it }.coachmarkStep(tour!!, Third, "Three", "The third."))
                }
            }
        }.use { scene ->
            val undimmed = luminance(scene.frames(4).getRGB(Width / 2, Height / 2))
            tour!!.start()
            scene.frames(30)
            tour!!.next()
            scene.frames(8)
            tour!!.next()
            val settled = scene.frames(60)
            assertEquals(Third, tour!!.current)
            assertTrue(abs(luminance(settled.at(third)) - undimmed) < 20, "the last step's control was not lit after Next twice mid-way")
        }
    }

    /** How much of each frame is the mark's colour, from one step to the next of a two-step tour. */
    private fun inkBetweenSteps(transition: CoachmarkTransition): List<Int> {
        var tour: CoachmarkTour? = null
        var accent = Color.Unspecified
        val ink = mutableListOf<Int>()
        Scene(width = Width, height = Height, density = 2f) {
            tour = rememberCoachmarkTour(First, Second, transition = transition)
            accent = Theme.colours.accent.solid
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Theme.colours.background)) {
                    Target(Modifier.align(Alignment.TopStart).coachmarkStep(tour!!, First, "Plan a trip", "Start here."))
                    Target(Modifier.align(Alignment.BottomEnd).coachmarkStep(tour!!, Second, "Saved trips", "They live here."))
                }
            }
        }.use { scene ->
            scene.frames(4)
            tour!!.start()
            ink += scene.frames(40).count(accent)
            tour!!.next()
            repeat(50) { ink += scene.frame().count(accent) }
        }
        return ink
    }

    @Composable
    private fun Target(modifier: Modifier) {
        Surface(modifier = modifier.size(TargetSize.dp), containerColour = Theme.colours.surfaceSunken, content = {})
    }

    /** Pixels within a hair of [colour]: the mark's own fill, not its edges. */
    private fun BufferedImage.count(colour: Color): Int {
        val want = colour.toArgb()
        var n = 0
        for (y in 0 until height) for (x in 0 until width) if (near(getRGB(x, y), want)) n++
        return n
    }

    private fun BufferedImage.boundsOf(colour: Color): Rect? {
        val want = colour.toArgb()
        var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var r = -1; var b = -1
        for (y in 0 until height) for (x in 0 until width) if (near(getRGB(x, y), want)) {
            l = minOf(l, x); t = minOf(t, y); r = maxOf(r, x); b = maxOf(b, y)
        }
        return if (r < 0) null else Rect(l.toFloat(), t.toFloat(), (r + 1).toFloat(), (b + 1).toFloat())
    }

    private fun near(a: Int, b: Int): Boolean =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= 6 &&
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= 6 &&
            abs((a and 0xFF) - (b and 0xFF)) <= 6

    private fun luminance(pixel: Int): Int =
        (((pixel shr 16) and 0xFF) + ((pixel shr 8) and 0xFF) + (pixel and 0xFF)) / 3

    private fun BufferedImage.at(bounds: Rect): Int =
        getRGB(bounds.center.x.toInt().coerceIn(0, width - 1), bounds.center.y.toInt().coerceIn(0, height - 1))

    private companion object {
        const val Width = 600
        const val Height = 800
        const val TargetSize = 60
        const val Welcome = "welcome"
        const val First = "plan"
        const val Second = "saved"
        const val Third = "alerts"
    }
}
