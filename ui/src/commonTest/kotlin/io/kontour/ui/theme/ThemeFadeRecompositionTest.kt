package io.kontour.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A theme cross-fade recomposes what reads a colour, and nothing else.
 *
 * ### The 99.9ms frame
 *
 * Reported from a phone: changing theme drops frames badly, with a 99.9ms peak.
 * `ColourSchemeAnimation`'s own KDoc named the cause and argued it was worth
 * paying — [LocalColourScheme] is a `staticCompositionLocalOf`, a static local
 * does not track reads, so providing a new value invalidates **everything**
 * below the provider. A fade provides a new scheme on every frame of a couple of
 * hundred milliseconds, so the whole application recomposed about thirteen
 * times, in a row, as fast as the device could manage.
 *
 * The argument was that a theme change is *supposed* to repaint everything. It
 * is — once. Thirteen times is not thirteen times more correct.
 *
 * ### What is asserted, and why it is two numbers
 *
 * A single "nothing recomposes" assertion would pass against a fade that had
 * stopped working, so both directions are here. A composable that reads a colour
 * has to recompose **many** times: it is drawing a colour that is moving. One
 * that reads none has to recompose **once** — the target scheme really did
 * change, the static local really should say so, and that one pass is the cost
 * that was always correct.
 *
 * The probes are skippable by construction, for the reason
 * `WindowSizeRecompositionTest` gives at its own: a capturing lambda is a new
 * instance every composition, so a probe built from one can never skip and the
 * lower number would be unreachable whatever the theme did.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeFadeRecompositionTest {

    @Test
    fun onlyTheColourReadersRecomposeAcrossAFade() {
        var dark by mutableStateOf(false)
        val blind = Compositions()
        val sighted = Compositions()

        runComposeUiTest {
            // The fade has to be walked frame by frame: this is a measurement of
            // how many compositions it costs, and letting the clock run to idle
            // would collapse the whole thing into one.
            mainClock.autoAdvance = false
            setContent {
                // A fade, asked for: it is off by default now, and what this
                // measures is what it costs when an app turns it on. See
                // `KontourTheme`'s `animateThemeChanges`.
                KontourTheme(darkTheme = dark, animateThemeChanges = true) {
                    Box(Modifier.fillMaxSize()) {
                        Blind(blind)
                        Sighted(sighted)
                    }
                }
            }
            mainClock.advanceTimeByFrame()
            waitForIdle()

            val restingBlind = blind.count
            val restingSighted = sighted.count
            assertTrue(
                restingBlind > 0 && restingSighted > 0,
                "the content never composed at all, so this measured nothing",
            )

            dark = true
            repeat(FadeFrames) {
                mainClock.advanceTimeByFrame()
                waitForIdle()
            }

            val fading = sighted.count - restingSighted
            val untouched = blind.count - restingBlind

            assertTrue(
                fading >= MinimumFadeFrames,
                "a composable reading `Theme.colours.surface` recomposed $fading " +
                    "time(s) across a theme fade. The surface is a different " +
                    "colour on every frame of one — if this is small, the fade " +
                    "has stopped fading and the other assertion here is passing " +
                    "for the wrong reason.",
            )
            assertTrue(
                untouched <= AtMostTheTargetChange,
                "a composable that reads no colour at all recomposed $untouched " +
                    "time(s) across a theme fade, against $fading for one that " +
                    "does. It should be once: the target scheme changed and the " +
                    "static local has to say so. Any more means the frames of the " +
                    "fade are going through the static local too, which " +
                    "invalidates the whole application per frame — the 99.9ms " +
                    "peak this exists to hold down.",
            )
        }
    }

    /**
     * And the theme itself is not one of the colour readers.
     *
     * The fade's frame was read in `KontourTheme`'s own body, to provide the
     * content colour from it, so the root of the theme recomposed on every frame
     * of a fade: every default parameter worked out again — the type scale's
     * fonts among them — every platform setting asked for again and every
     * `remember` in it compared, for the one colour that was moving. Nothing
     * below it recomposed for it, which is why the test above could not see it;
     * this one watches the theme's own scope.
     */
    @OptIn(ExperimentalComposeRuntimeApi::class)
    @Test
    fun theThemeItselfDoesNotRecomposeAcrossAFade() {
        var dark by mutableStateOf(false)
        val watch = RootWatch()
        val sighted = Compositions()

        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                val composition = currentComposer.composition
                remember { composition.setObserver(watch) }
                // The first scope entered from here is the theme's own.
                watch.armed = true
                KontourTheme(darkTheme = dark, animateThemeChanges = true) {
                    watch.armed = false
                    Box(Modifier.fillMaxSize()) { Sighted(sighted) }
                }
            }
            mainClock.advanceTimeByFrame()
            waitForIdle()
            check(watch.root != null) { "the theme's scope was never seen, so this measured nothing" }

            val restingRoot = watch.entries
            val restingSighted = sighted.count

            dark = true
            repeat(FadeFrames) {
                mainClock.advanceTimeByFrame()
                waitForIdle()
            }

            val fading = sighted.count - restingSighted
            val root = watch.entries - restingRoot
            assertTrue(fading >= MinimumFadeFrames, "the fade recomposed a colour reader $fading time(s): it did not fade")
            assertTrue(
                root <= AtMostTheTargetChange * 2,
                "KontourTheme's own body recomposed $root time(s) across a fade, against " +
                    "$fading for a composable reading a colour. Once is the target scheme " +
                    "arriving — and a scope can be reported entering twice on one pass — " +
                    "so anything past that is the fade's frames being read at the root.",
            )
        }
    }

    private companion object {
        /** Comfortably past a `tweenDefault`, walked one at a time. */
        const val FadeFrames = 40

        /**
         * Fewer than this and the fade is not a fade.
         *
         * A `tweenDefault` is a few hundred milliseconds, so a real one is a dozen
         * or more frames. Five is that with room for the spec to be tuned.
         */
        const val MinimumFadeFrames = 5

        /**
         * One for the target scheme, and one of slack.
         *
         * The scheme and the elevation scale are provided in the same call, so a
         * theme change is one invalidation. The slack is for a pass landing
         * separately rather than for a second change; the number this is guarding
         * against is thirteen.
         */
        const val AtMostTheTargetChange = 2
    }
}

/** @see WindowSizeRecompositionTest — a counter the compiler can skip past. */
@Stable
private class Compositions {
    var count = 0
}

/**
 * Counts the times the first scope entered after [armed] is entered again.
 *
 * Armed just before the call to `KontourTheme`, so that scope is the theme's.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
private class RootWatch : CompositionObserver {
    var armed = false
    var root: RecomposeScope? = null
    var entries = 0

    override fun onScopeEnter(scope: RecomposeScope) {
        if (armed && root == null) root = scope
        if (scope === root) entries++
    }

    override fun onBeginComposition(composition: ObservableComposition) = Unit
    override fun onReadInScope(scope: RecomposeScope, value: Any) = Unit
    override fun onScopeExit(scope: RecomposeScope) = Unit
    override fun onEndComposition(composition: ObservableComposition) = Unit
    override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit
    override fun onScopeDisposed(scope: RecomposeScope) = Unit
}

/** Reads nothing from the theme. */
@Composable
private fun Blind(into: Compositions) {
    into.count++
}

/** Reads one colour, and uses it, so nothing can elide the read. */
@Composable
private fun Sighted(into: Compositions) {
    val surface = Theme.colours.surface
    into.count++
    check(surface != Color.Unspecified) { "the theme handed out an unspecified surface" }
}
