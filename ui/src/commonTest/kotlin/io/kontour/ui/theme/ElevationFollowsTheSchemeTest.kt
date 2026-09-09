package io.kontour.ui.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which shadow scale a custom [ColourScheme] gets.
 *
 * `kontourElevation` doubles its alphas on a dark ground, because a soft black
 * shadow on a near-black surface is invisible — that is the whole reason it
 * takes a parameter. The question this test settles is *which* input decides:
 * the `darkTheme` flag, or the scheme actually being drawn.
 *
 * They are the same thing for every caller who lets both default, which is why
 * nothing noticed. They come apart the moment an app passes a scheme of its
 * own — and an app passing a dark scheme is not obliged to also flip a flag
 * whose documented job is picking between the *built-in* schemes it just
 * declined to use. Doing that gets light-mode alphas on near-black surfaces:
 * every card, menu and dialog in the app loses its shadow, and the app looks
 * flat for a reason nothing on screen explains.
 *
 * So the scheme decides. [ColourScheme.isDark] is already what `Surface`, `Tag`
 * and `Skeleton` ask when they need to know, and this makes the elevation
 * default agree with them.
 */
class ElevationFollowsTheSchemeTest {

    /** `low`'s contact layer: 0.04 on a light ground, ×2.4 on a dark one. */
    private val lightAlpha = 0.04f
    private val darkAlpha = 0.04f * 2.4f

    @OptIn(ExperimentalTestApi::class)
    private fun alphaUnder(darkTheme: Boolean, colours: ColourScheme): Float {
        var alpha = Float.NaN
        runComposeUiTest {
            setContent {
                KontourTheme(darkTheme = darkTheme, colours = colours) {
                    alpha = Theme.elevation.low.layers.first().alpha
                }
            }
        }
        return alpha
    }

    @Test
    fun aDarkSchemeGetsDarkShadowsThoughTheFlagSaysLight() {
        assertEquals(
            darkAlpha,
            alphaUnder(darkTheme = false, colours = darkColourScheme()),
            absoluteTolerance = 1e-5f,
            message = "a dark scheme drew with light-mode shadow alphas, so every " +
                "raised surface in an app that supplies its own dark palette is " +
                "flat. The elevation default is reading the darkTheme flag rather " +
                "than the scheme it was handed",
        )
    }

    @Test
    fun aLightSchemeGetsLightShadowsThoughTheFlagSaysDark() {
        assertEquals(
            lightAlpha,
            alphaUnder(darkTheme = true, colours = lightColourScheme()),
            absoluteTolerance = 1e-5f,
            message = "a light scheme drew with dark-mode shadow alphas — 2.4× too " +
                "heavy on a white ground, which reads as a smudge under every card",
        )
    }

    @Test
    fun theBuiltInPairingsAreUnchanged() {
        // The case every existing caller is in, pinned so the fix cannot be a
        // swap that happens to satisfy the two above.
        assertEquals(
            lightAlpha,
            alphaUnder(darkTheme = false, colours = lightColourScheme()),
            absoluteTolerance = 1e-5f,
            message = "the ordinary light pairing moved",
        )
        assertEquals(
            darkAlpha,
            alphaUnder(darkTheme = true, colours = darkColourScheme()),
            absoluteTolerance = 1e-5f,
            message = "the ordinary dark pairing moved",
        )
    }
}
