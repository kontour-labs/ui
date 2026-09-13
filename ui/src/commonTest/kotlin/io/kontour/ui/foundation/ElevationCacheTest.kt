package io.kontour.ui.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A surface that recomposes without changing does not redraw its shadow.
 *
 * Every elevated `Surface` in the library draws its shadow through
 * `Modifier.elevation`, which folds one
 * `androidx.compose.ui.draw.dropShadow(shape) { … }` per [io.kontour.ui.theme.ShadowSpec]
 * and gives each a lambda closing over that spec. `DropShadowElement` compares
 * that lambda, and no two lambda instances are ever equal — so for the life of
 * this library, *every recomposition of every surface* handed Compose a modifier
 * element it had to treat as new. Updating the node throws away the blur it had
 * already rasterised, which on `Elevation.low` is two silhouettes, the wider one
 * a 50dp blur.
 *
 * ### Why this is counted rather than timed
 *
 * The same argument `SheetFramePressureTest` makes: a draw *count* is CPU-bound
 * Kotlin and the count taken here is the count a phone takes. What the phone
 * then does with a shadow redraw is far more expensive than what a software
 * rasteriser does with one, which only makes the count matter more.
 *
 * Three diagnostics already *time* this — `ShadowCostDiagnostic`,
 * `ThemeFadeCostDiagnostic` and `ThemeSwitchCostDiagnostic` — and each says in
 * its own docstring that its assertion is a catastrophe bound no reasonable
 * machine reaches. They are the right shape for comparing two scenes in one run
 * and the wrong shape for holding a fix: none of them goes red when a shadow
 * starts being re-rasterised for nothing again.
 *
 * ### The probe, and why it is a shape
 *
 * [CountingShape] counts `createOutline`. Everything in `Surface`'s chain that
 * takes a shape asks for the outline as it draws, so the count is a proxy for
 * "this surface redrew" — and the two other holders, `clip` and `background`,
 * both have value-equal modifier elements, so they do not invalidate anything by
 * themselves. What is being counted is one number going up when nothing about
 * the picture changed.
 *
 * ### What forces the recomposition
 *
 * A `staticCompositionLocalOf`, because that is what the real case is.
 * `Surface` is skippable, so a parent recomposing with equal arguments does not
 * reach it — and it would be easy to write this test in a way that exercises
 * nothing. A *static* local has no fine-grained invalidation: changing one
 * restarts the whole subtree under the provider, every surface in it included.
 * `KontourTheme` provides its colours, shapes and elevation that way, which is
 * why a theme change — the thing reported as "ridiculously laggy" — recomposes
 * an entire application once per frame of the fade, while the elevation scale
 * deliberately *steps* rather than interpolating, so `shadow` is the same
 * instance for nearly all of it. There was a cache to hit on almost every frame
 * and nothing was hitting it.
 */
@OptIn(ExperimentalTestApi::class)
class ElevationCacheTest {

    private val LocalTick = staticCompositionLocalOf { 0 }

    /** A shape that reports how many times it has been asked for its outline. */
    private class CountingShape : Shape {
        var outlines = 0

        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            outlines++
            return RectangleShape.createOutline(size, layoutDirection, density)
        }
    }

    @Test
    fun anUnchangedElevatedSurfaceDrawsItsShadowOnce() {
        val shape = CountingShape()
        var settled = 0

        runComposeUiTest {
            var tick by mutableStateOf(0)
            setContent {
                KontourTheme {
                    CompositionLocalProvider(LocalTick provides tick) {
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = shape,
                            shadow = Theme.elevation.low,
                        ) {
                            Box(Modifier.size(40.dp))
                        }
                    }
                }
            }
            waitForIdle()
            settled = shape.outlines

            repeat(Recompositions) {
                tick++
                waitForIdle()
            }

            assertTrue(
                settled > 0,
                "the shape was never asked for an outline, so this test is " +
                    "measuring a surface that does not draw — check that " +
                    "`Surface` still passes `shape` to its shadow, its clip and " +
                    "its background",
            )
        }

        val afterwards = shape.outlines - settled
        assertEquals(
            0,
            afterwards,
            "an elevated Surface asked its shape for an outline $afterwards more " +
                "time(s) across $Recompositions recompositions in which nothing " +
                "about it changed — same shadow instance, same shape, same " +
                "colour. That is the shadow being re-rasterised for nothing, and " +
                "on a phone it is the theme fade's frame rate. `Surface` " +
                "remembers the elevation modifier so that the chain it hands " +
                "Compose compares equal; `Modifier.elevation` builds a fresh " +
                "capturing lambda per layer, so calling it again per " +
                "recomposition is what this catches.",
        )
    }

    private companion object {
        /** Fourteen, the number of frames a 220ms theme fade takes at 60Hz. */
        const val Recompositions = 14
    }
}
