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
        ShadowSpec(color = Color.Black, alpha = alpha, offsetY = 2.dp, blurRadius = blur.dp)

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
     * Colours and shadows are at the same point of the same fade.
     *
     * The property the shared `Animatable` exists for. Two animations with the
     * same spec would pass this today and stop passing the day one spec is
     * tuned, which is why they are one.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun bothHalvesMoveTogether() = runComposeUiTest {
        var dark by mutableStateOf(false)
        var seenColors: Color? = null
        var seenAlpha: Float? = null

        val lightColors = kontourColorScheme(dark = false, contrast = ContrastLevel.Standard)
        val darkColors = kontourColorScheme(dark = true, contrast = ContrastLevel.Standard)
        val lightAlpha = kontourElevation(dark = false).medium.layers.first().alpha
        val darkAlpha = kontourElevation(dark = true).medium.layers.first().alpha

        mainClock.autoAdvance = false
        setContent {
            KontourTheme(darkTheme = dark) {
                seenColors = Theme.colors.surface
                seenAlpha = Theme.elevation.medium.layers.first().alpha
                Box(Modifier.fillMaxSize())
            }
        }

        mainClock.advanceTimeByFrame()
        assertEquals(lightColors.surface, seenColors, "did not start light")

        dark = true
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(80)

        val colorMid = seenColors!!
        val alphaMid = seenAlpha!!

        assertNotEquals(lightColors.surface, colorMid, "the surface never left its start")
        assertNotEquals(darkColors.surface, colorMid, "the surface arrived in one frame")
        assertTrue(
            alphaMid > lightAlpha && alphaMid < darkAlpha,
            "the shadow is at $alphaMid, outside the $lightAlpha..$darkAlpha it " +
                "should be crossing — it cut to its destination while the " +
                "surface under it was still travelling",
        )
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
