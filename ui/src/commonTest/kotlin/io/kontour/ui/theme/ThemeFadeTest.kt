package io.kontour.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The theme cross-fade, both halves of it.
 *
 * The colour half shipped with no test at all; the elevation half was left
 * undone because interpolating a `List<ShadowSpec>` of unequal length looked
 * like a crash waiting to happen. Both are covered here, and the length case is
 * the one that was the reason to stop.
 */
class ThemeFadeTest {

    private fun spec(alpha: Float, blur: Int = 8) =
        ShadowSpec(colour = Color.Black, alpha = alpha, offsetY = 2.dp, blurRadius = blur.dp)

    private fun scale(alpha: Float) = Elevation(
        low = Shadow(listOf(spec(alpha))),
        medium = Shadow(listOf(spec(alpha))),
        high = Shadow(listOf(spec(alpha))),
        overlay = Shadow(listOf(spec(alpha))),
    )

    @Test
    fun theEndpointsAreExact() {
        val light = scale(0.1f)
        val dark = scale(0.5f)

        assertEquals(light, lerp(light, dark, 0f), "at zero the fade is not the start")
        assertEquals(dark, lerp(light, dark, 1f), "at one the fade is not the end")
    }

    @Test
    fun theMidpointIsHalfWay() {
        val mid = lerp(scale(0.1f), scale(0.5f), 0.5f)

        assertEquals(
            0.3f,
            mid.medium.layers.single().alpha,
            absoluteTolerance = 1e-5f,
            message = "half way between 0.1 and 0.5 is 0.3",
        )
    }

    @Test
    fun geometryInterpolatesAndNotJustAlpha() {
        val start = Shadow(listOf(spec(0.1f, blur = 4)))
        val stop = Shadow(listOf(spec(0.1f, blur = 20)))

        assertEquals(
            12.dp,
            lerp(start, stop, 0.5f).layers.single().blurRadius,
            "the blur did not travel — a fade that moves alpha while the blur " +
                "jumps is worse than no fade at all",
        )
    }

    /** The case that was the reason not to do this. */
    @Test
    fun aLayerTheOtherScaleDoesNotHaveFadesInPlace() {
        val one = Shadow(listOf(spec(0.2f, blur = 6)))
        val two = Shadow(listOf(spec(0.2f, blur = 6), spec(0.4f, blur = 30)))

        val mid = lerp(one, two, 0.5f)

        assertEquals(2, mid.layers.size, "the longer scale's layer went missing")
        assertEquals(
            0.2f,
            mid.layers[1].alpha,
            absoluteTolerance = 1e-5f,
            message = "the unmatched layer should fade from nothing to 0.4, so " +
                "half way is 0.2",
        )
        assertEquals(
            30.dp,
            mid.layers[1].blurRadius,
            "the unmatched layer must keep its own geometry and fade *in place*; " +
                "pairing it against a default ShadowSpec would slide a zero-blur " +
                "shadow in from the origin",
        )
    }

    @Test
    fun anEmptyTierMeetingALayeredOneDoesNotThrow() {
        val mid = lerp(Shadow.None, Shadow(listOf(spec(0.4f))), 0.5f)

        assertEquals(1, mid.layers.size)
        assertEquals(0.2f, mid.layers.single().alpha, absoluteTolerance = 1e-5f)
        assertEquals(Shadow.None, lerp(Shadow.None, Shadow.None, 0.5f), "empty stays empty")
    }

    /**
     * Colours and shadows are driven by one fade, and the shadow steps inside it.
     *
     * ### What this used to assert, and why it changed
     *
     * It used to require the shadow's alpha to be strictly *between* the two
     * scales part-way through — the property the shared `Animatable` was added
     * for, against a defect where shadows cut to their dark-mode strength on the
     * **first** frame and sat there while the surfaces beneath them travelled.
     *
     * The shadow is a step function of the same fraction now, and the reason is
     * measured rather than preferred: `ThemeFadeCostDiagnostic` finds that blurs
     * are about four fifths of what a theme fade costs, and a blur whose alpha
     * and radius move every frame cannot reuse the one it rasterised on the
     * frame before. `lerpTheme` has the numbers.
     *
     * So the thing to guard is no longer "it is in between". It is the pair of
     * facts that make the step invisible and keep the original defect fixed:
     * the shadow is still at the **old** scale while the surface has already
     * left its starting colour, and it is at the **new** one before the surface
     * has arrived. A shadow that cut on frame one fails the first; one that
     * never changed at all fails the second.
     *
     * Sampled frame by frame rather than at a chosen millisecond, so the claim
     * does not depend on the tween's duration or on where `standard` easing puts
     * the midpoint.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theShadowStepsOnceWhileTheSurfaceIsStillTravelling() = runComposeUiTest {
        var dark by mutableStateOf(false)
        var seenColour: Color? = null
        var seenAlpha: Float? = null

        val lightColours = kontourColourScheme(dark = false, contrast = ContrastLevel.Standard)
        val darkColours = kontourColourScheme(dark = true, contrast = ContrastLevel.Standard)
        val lightAlpha = kontourElevation(dark = false).medium.layers.first().alpha
        val darkAlpha = kontourElevation(dark = true).medium.layers.first().alpha

        mainClock.autoAdvance = false
        setContent {
            KontourTheme(darkTheme = dark) {
                seenColour = Theme.colours.surface
                seenAlpha = Theme.elevation.medium.layers.first().alpha
                Box(Modifier.fillMaxSize())
            }
        }

        mainClock.advanceTimeByFrame()
        assertEquals(lightColours.surface, seenColour, "did not start light")

        dark = true
        val path = mutableListOf<Pair<Color, Float>>()
        repeat(FadeFrames) {
            mainClock.advanceTimeByFrame()
            path += seenColour!! to seenAlpha!!
        }

        val alphas = path.map { it.second }.distinct()
        assertEquals(
            listOf(lightAlpha, darkAlpha),
            alphas,
            "the shadow's alpha took ${alphas.size} distinct values across the " +
                "fade. It is meant to take exactly two, in that order: the old " +
                "scale, then the new one. More than two means it is interpolating " +
                "again — every elevated surface on screen re-rasterising both of " +
                "its blurs on every frame, which is four fifths of what a fade " +
                "costs. One means it is not travelling with the scheme at all.",
        )

        val stepped = path.indexOfFirst { it.second == darkAlpha }
        assertTrue(stepped > 0, "the shadow was at the dark scale on the first frame of the fade")
        assertNotEquals(
            lightColours.surface,
            path[stepped - 1].first,
            "the surface had not moved at all by the frame before the shadow " +
                "stepped, so the step is not inside the fade — it is the cut the " +
                "shared `Animatable` was added to remove",
        )
        assertNotEquals(
            darkColours.surface,
            path[stepped].first,
            "the surface had already arrived by the frame the shadow stepped, so " +
                "the step is at the end of the fade rather than in the middle of " +
                "it, which is the most visible place to put it rather than the least",
        )
    }

    private companion object {
        /**
         * Long enough to contain the whole of `tweenDefault` at 60Hz.
         *
         * 220ms is fourteen frames; twenty leaves room for the tween's duration
         * to be retuned without this quietly sampling only half of it.
         */
        const val FadeFrames = 20
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun turningTheFadeOffSwitchesInstantly() = runComposeUiTest {
        var dark by mutableStateOf(false)
        var seenAlpha: Float? = null
        val darkAlpha = kontourElevation(dark = true).medium.layers.first().alpha

        mainClock.autoAdvance = false
        setContent {
            KontourTheme(darkTheme = dark, animateThemeChanges = false) {
                seenAlpha = Theme.elevation.medium.layers.first().alpha
                Box(Modifier.fillMaxSize())
            }
        }
        mainClock.advanceTimeByFrame()

        dark = true
        mainClock.advanceTimeByFrame()

        assertEquals(darkAlpha, seenAlpha, "the opt-out still animated")
    }
}
