package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Card
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.BackdropStyle
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.OverlayHostState
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a theme switch costs in the state a reader is actually in when they make
 * one.
 *
 * Reported twice now, the second time as "switching themes and to/from dark mode
 * is still ridiculously laggy". `ThemeFadeCostDiagnostic` measured the fade a
 * round earlier and its finding held — the shadows were four fifths of it, and
 * pinning the elevation scale across the flip took a sixty-card frame from
 * 36.50ms to 34.68. That fix shipped. The complaint did not go away.
 *
 * ### What that diagnostic could not see
 *
 * It renders a bare grid of cards under a `KontourTheme`. **Nobody switches a
 * theme from there.** Both surfaces put the control inside an overlay, and an
 * overlay that dims also blurs everything behind it — so a theme fade can be
 * running underneath a full-screen `BlurEffect`, and the frames being measured
 * were frames where that is never true.
 *
 * `BackdropCostDiagnostic` measures the blur and `ThemeFadeCostDiagnostic`
 * measures the fade, each varying one thing while holding the other at zero.
 * Nothing had measured them together.
 *
 * ### Measured
 *
 * Median of three runs, each the mean of the fourteen frames inside one fade:
 *
 * ```
 *   overlay closed                rest   3.60 · switching  11.13  (+7.53)
 *   overlay open, blurred         rest  26.16 · switching  34.30  (+8.14)
 *   overlay open, blur off        rest   5.56 · switching  12.60  (+7.04)
 *   fade vs cut — no overlay      10.06 vs  4.12 · blurred  33.03 vs 26.33
 * ```
 *
 * Three things fall out, and the first two were not what was expected.
 *
 * **The switch costs the same wherever it happens** — `+7.53`, `+8.14`, `+7.04`
 * are one number. The blur does not make a switch more expensive; it makes every
 * frame more expensive, switch or no switch.
 *
 * **A blurred backdrop is 7.3x the whole rest of the frame, at rest.** 26.16
 * against 3.60 for the same tree with the overlay closed, which is
 * `BackdropCostDiagnostic`'s 7.2–7.9x arriving independently. This is a known
 * and accepted cost of the frosted-glass decision; what is new is that the
 * control for changing themes sits under it on one of the two surfaces, so a
 * fade there spends fourteen frames at 47ms.
 *
 * **The two surfaces are not the same case.** The site's settings `Popover`
 * takes `ScrimStyle.Transparent`, and `OverlayEntry` derives the backdrop from
 * the scrim — so the site does **not** blur, and its lag is the fade's own
 * 2.3x. The gallery's `SettingsSheet` is a `ModalBottomSheet`, which dims and
 * therefore blurs, so a phone is in the 7.3x case. One report, two causes.
 *
 * A browser confirms the site half independently: a theme switch on the built
 * site paces at a p95 of 100–117ms against 16.7 at rest and 50 for a page
 * navigation — a fade frame costing several times a still one, with no blur
 * anywhere near it.
 *
 * ### Where the fade's own cost is
 *
 * `ThemeFadeCostDiagnostic`, re-run alongside this: at sixty cards a fading
 * frame is 40.59ms against 7.22 with no shadow on any tier. **Shadows are 82%
 * of a fade frame**, and holding the elevation scale still across the flip now
 * buys almost nothing — 40.05 against 40.59, where the round that shipped that
 * fix measured 34.68 against 36.50. The cost is not the shadow parameters
 * moving. It is that everything redraws, and redrawing an elevated surface
 * re-rasterises its shadow whether or not the shadow changed.
 *
 * ### The 2x that was nearly reported as a finding
 *
 * The first version of this file timed each configuration once. The first row
 * came out at 28.57ms and the *same configuration*, measured again later in the
 * same test, at 14.71 — the JIT compiling the render path, arriving as a
 * doubling that looked exactly like a result. Hence [Repeats] and the discarded
 * warm-up, and hence this paragraph: a timing file that does not say how it
 * handles warm-up is not reporting what it thinks it is.
 *
 * ### Software rasteriser
 *
 * The caveat every timing file here carries: there is no GPU in a container, so
 * a blur is a per-pixel loop and these numbers are a ceiling rather than a
 * phone's. What survives the difference is the *ratio* between rows measured the
 * same way in the same run.
 *
 * A **diagnostic, not a gate**: the assertion is a catastrophe bound and the
 * useful output is the table.
 */
class ThemeSwitchCostDiagnostic {

    @Test
    fun aThemeSwitchIsMeasuredUnderTheOverlayItIsMadeFrom() {
        val rows = listOf(
            Triple("overlay closed", null, true),
            Triple("overlay open, blurred (ships)", BackdropStyle.Blur, true),
            Triple("overlay open, blur off", BackdropStyle.Blur, false),
        )
        // Thrown away. The first timed configuration in a run came out at 28.57ms
        // against 14.71 for the *same* configuration measured later in the same
        // test — a 2x spread that is the JIT compiling the render path, not
        // anything about a theme. Every number below is a median of [Repeats]
        // runs taken after this.
        repeat(2) {
            millisPerFrame(switching = true, overlay = null, blur = true)
            millisPerFrame(switching = true, overlay = BackdropStyle.Blur, blur = true)
        }

        println("theme switch cost, ms/frame — median of $Repeats runs, each the mean of $Samples frames")
        for ((label, style, blur) in rows) {
            val rest = median(switching = false, overlay = style, blur = blur)
            val switching = median(switching = true, overlay = style, blur = blur)
            println(
                "  %-32s rest %6.2f · switching %6.2f  (+%.2f)"
                    .format(label, rest, switching, switching - rest)
            )
        }

        // What the fade itself is made of, with the overlay out of the way.
        //
        // `fading = false` is the same switch with `animateThemeChanges` off: one
        // frame does all the work and the thirteen after it are still frames, so
        // the mean across the window is what a switch costs when it does not
        // animate. That is the ceiling on what shortening or dropping the fade
        // could buy, and it is measured rather than argued.
        val animated = median(switching = true, overlay = null, blur = true)
        val cut = median(switching = true, overlay = null, blur = true, fading = false)
        val blurredAnimated = median(switching = true, overlay = BackdropStyle.Blur, blur = true)
        val blurredCut = median(switching = true, overlay = BackdropStyle.Blur, blur = true, fading = false)
        println(
            "  fade vs cut — no overlay: %6.2f vs %6.2f · under a blurred overlay: %6.2f vs %6.2f"
                .format(animated, cut, blurredAnimated, blurredCut)
        )
        assertTrue(true)
    }

    /**
     * How many times the tree under the theme recomposes across one switch.
     *
     * The gap this file was written to close, and the reason it is a *count*
     * beside a table of milliseconds: a count is the same number on a JVM and on
     * a phone, so it is the half of this that transfers.
     *
     * [KontourTheme] provides its tokens through `staticCompositionLocalOf`,
     * which does not track reads — changing one invalidates everything below the
     * provider rather than the composables that read it. That is a deliberate
     * trade, argued in `Theme.kt`, and its cost has never been counted. The fade
     * hands the provider a freshly lerped scheme on every frame, so the expected
     * answer is "once per frame of the fade, plus the settling".
     */
    @Test
    fun theWholeTreeRecomposesOncePerFrameOfTheFade() {
        val counter = Compositions()
        var dark by mutableStateOf(false)
        var nanos = 0L
        val scene = ImageComposeScene(width = Canvas, height = CanvasHeight, density = Density(1f)) {
            KontourTheme(darkTheme = dark, reduceMotion = false) {
                Counted(counter) { Page() }
            }
        }
        try {
            repeat(WarmUpFrames) {
                nanos += FrameNanos
                scene.render(nanos).close()
            }
            val settled = counter.count
            dark = true
            repeat(Samples) {
                nanos += FrameNanos
                scene.render(nanos).close()
            }
            val across = counter.count - settled
            println(
                "the tree recomposed $across times across a switch, over $Samples frames " +
                    "(it had settled at $settled after $WarmUpFrames still frames)"
            )
            assertTrue(
                across in 1..(Samples * 2),
                "the tree recomposed $across times across one theme switch, over " +
                    "$Samples frames of fade. Under 1 means the probe is not being " +
                    "invalidated and this measures nothing; over ${Samples * 2} means " +
                    "something is recomposing more than once per frame, which the " +
                    "fade alone cannot explain.",
            )
        } finally {
            scene.close()
        }
    }

    /** [millisPerFrame] over [Repeats] fresh scenes, middle value. */
    private fun median(
        switching: Boolean,
        overlay: BackdropStyle?,
        blur: Boolean,
        fading: Boolean = true,
    ): Double = List(Repeats) { millisPerFrame(switching, overlay, blur, fading) }
        .sorted()[Repeats / 2]

    /**
     * Renders a switch and reports the mean cost of a frame inside it.
     *
     * The flip happens after the warm-up and after the overlay has settled, so
     * the frames being timed carry the fade and a *stationary* overlay rather
     * than the overlay's own enter animation.
     */
    private fun millisPerFrame(
        switching: Boolean,
        overlay: BackdropStyle?,
        blur: Boolean,
        fading: Boolean = true,
    ): Double {
        var dark by mutableStateOf(false)
        var nanos = 0L
        val host = OverlayHostState()
        val scene = ImageComposeScene(width = Canvas, height = CanvasHeight, density = Density(1f)) {
            KontourTheme(
                darkTheme = dark,
                reduceMotion = false,
                backdropBlur = blur,
                animateThemeChanges = fading,
            ) {
                OverlayHost(Modifier.fillMaxSize(), host) { Page() }
            }
        }
        try {
            repeat(WarmUpFrames) {
                nanos += FrameNanos
                scene.render(nanos).close()
            }
            if (overlay != null) {
                host.show(
                    OverlayEntry(
                        key = "panel",
                        layer = OverlayLayer.Sheet,
                        backdrop = overlay,
                        content = { Panel() },
                    )
                )
                // Long enough for the overlay's own enter to finish, so what is
                // timed below is a still overlay over a fading page.
                repeat(OverlaySettleFrames) {
                    nanos += FrameNanos
                    scene.render(nanos).close()
                }
            }
            if (switching) dark = true
            val started = System.nanoTime()
            repeat(Samples) {
                nanos += FrameNanos
                scene.render(nanos).close()
            }
            return (System.nanoTime() - started) / 1_000_000.0 / Samples
        } finally {
            scene.close()
        }
    }

    /** A page with enough on it to cost something to recompose and redraw. */
    @Composable
    private fun Page() {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            repeat(Cards) {
                Card(modifier = Modifier.padding(6.dp)) {
                    Text("A card with a line of text on it", Modifier.padding(12.dp))
                }
            }
        }
    }

    /** Something to put in the overlay. Its content is not what is measured. */
    @Composable
    private fun Panel() {
        Box(Modifier.padding(24.dp)) { Text("Settings") }
    }

    /**
     * A recomposition counter that can itself skip.
     *
     * `@Stable` and holding a plain `var` rather than a `mutableStateOf`: if the
     * count were state, reading it would subscribe whatever read it and the
     * probe would invalidate the thing it is measuring.
     */
    @Stable
    private class Compositions {
        var count = 0
    }

    @Composable
    private fun Counted(into: Compositions, content: @Composable () -> Unit) {
        into.count++
        content()
    }

    private companion object {
        const val Canvas = 580
        const val CanvasHeight = 700
        const val Cards = 8

        const val FrameNanos = 16_000_000L
        const val WarmUpFrames = 10
        const val OverlaySettleFrames = 30

        /** `tweenDefault` is 220ms, which is fourteen frames at 60Hz. */
        const val Samples = 14

        /**
         * Odd, so the median is a measured value rather than an average of two.
         *
         * Three rather than five, and the canvas is 580x700 rather than
         * 580x1000, because the blurred rows dominate the runtime: at five and
         * the taller canvas this file took **50 seconds** of a nine-minute
         * suite. What fixed the measurement was the discarded warm-up below, not
         * the repeat count — the spread across three warmed runs is under a
         * millisecond.
         */
        const val Repeats = 3
    }
}
