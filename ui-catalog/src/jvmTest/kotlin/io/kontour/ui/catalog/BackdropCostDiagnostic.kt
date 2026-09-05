package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.BackdropStyle
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.OverlayHostState
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.theme.KontourTheme
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a blurred backdrop costs per frame, on this machine.
 *
 * Timed rather than counted, which is a departure. Everywhere else in this suite
 * a stopwatch would be measuring the runner: a recomposition count is the same
 * number on a JVM and on a phone, so that is what gets asserted. But a blur adds
 * no passes to count — the cost *is* rasterisation, one full-screen offscreen
 * render per frame — so there is nothing to count and a number from a stopwatch
 * is the only honest answer.
 *
 * Which makes this a **diagnostic, not a gate**. It asserts only that the blur
 * has not become catastrophic, at a threshold no reasonable machine would trip;
 * the useful output is the ratios it prints. Pinning a millisecond figure here
 * would fail on a loaded CI runner and teach everyone to ignore it.
 *
 * ### Two things this measured wrongly for two rounds
 *
 * **It timed `Scene.frame`,** which encodes every frame to PNG and decodes it
 * back through `ImageIO`. That is several times the cost of drawing the frame,
 * and it is present identically in both halves of a ratio — so every figure this
 * file printed was a blur cost diluted by two image codecs, and the dilution was
 * large enough to hide the finding below. `Scene.advance` renders and discards.
 *
 * **It started the stopwatch after the animation had finished,** and said so:
 * *"warm, fully open, and every frame from here is the steady state the number
 * is about — not the spring settling."* Defensible, and precisely backwards. At
 * rest the fraction is 1, so the radius is a constant that Skia rasterises once
 * and reuses. The instrument was measuring the single condition under which the
 * defect cannot occur, and would have gone on reporting a healthy ratio while
 * every sheet on the site stuttered.
 *
 * Undiluted and split, the numbers say something simpler and worse than the
 * theory they were taken to test: the blur costs about **eight times a whole
 * frame** and costs it *all the time*, settled as much as arriving. It is not an
 * animation problem. See [quantisingTheBlurRadiusBuysNothing] for the mechanism
 * that was expected and is not there.
 *
 * If the ratio ever matters to an app — a live map redrawing under a sheet is
 * the case — `KontourTheme(backdropBlur = false)` turns it off for the same
 * picture minus the texture.
 */
class BackdropCostDiagnostic {

    @Test
    fun aBlurredBackdropCostsUnderTwelveTimesAnUnblurredFrame() {
        val measured = listOf(BackdropStyle.Blur, BackdropStyle.BlurAndScale).map { style ->
            val with = profile(style, blur = true)
            val without = profile(style, blur = false)
            println(
                ("%s — opening %.1fms/frame against %.1f (%.2f×), settled %.1f against %.1f (%.2f×), " +
                    "worst frame %.1fms")
                    .format(
                        style,
                        with.openingCost, without.openingCost, with.openingCost / without.openingCost,
                        with.settledCost, without.settledCost, with.settledCost / without.settledCost,
                        with.worst,
                    )
            )
            style to (with.settledCost / without.settledCost)
        }

        for ((style, ratio) in measured) {
            assertTrue(
                ratio < 12.0,
                ("a settled %s backdrop took %.2f× as long to render as an unblurred one. " +
                    "The measured figure when this bound was set was 8.3× for Blur, so this is " +
                    "past the point where something has changed — a second offscreen pass, a " +
                    "layer that stopped being reused, or a radius that grew.")
                    .format(style, ratio),
            )
        }
    }

    /**
     * Quantising the blur radius buys nothing, and this is why it was not done.
     *
     * Three layers over identical content, differing in one thing: how many
     * distinct blur radii they ask Skia for across the same run of frames.
     *
     * * **constant** — one radius, never rewritten. The layer block does not even
     *   re-run, so nothing about the effect changes from frame to frame.
     * * **stepped** — the fraction is rewritten every frame and quantised to five
     *   steps, so the block re-runs and asks for three distinct radii.
     * * **continuous** — the fraction is rewritten every frame and used raw, which
     *   is what `overlayBackdrop` does: every frame a radius it has never seen.
     *
     * Round 23 opened on the theory that the third was the expensive one — that a
     * blur at a constant radius could be rasterised once and reused, and one that
     * moved could not, which would have made every sheet opening pay for a full
     * screen of gaussian at a novel radius while a settled dialog paid once. It
     * would have explained "big animations are rough and everything else is
     * smooth" exactly.
     *
     * **Measured: 195.8ms, 208.8ms and 207.6ms per frame.** All three are the same
     * number. Skia does not cache a blurred layer across frames even when neither
     * the radius nor the content behind it has changed at all, so there is no
     * cache for a moving radius to miss. Stepping the radius was designed, costed
     * and then cancelled by this test before it was written.
     *
     * What is left is the plain cost of the pass, which
     * [aBlurredBackdropCostsUnderTwelveTimesAnUnblurredFrame] puts at 8.5× a
     * whole frame, and which [radiusSweep] shows is nearly flat in the radius —
     * so it cannot be made cheaper by blurring less, only by blurring fewer
     * pixels or fewer times.
     *
     * The assertion is one-directional and generous on purpose. It is not
     * defending a performance property; it is defending the *reasoning* above,
     * and it should fail the day a Skia release starts caching these layers —
     * because on that day stepping the radius becomes worth doing after all.
     */
    @Test
    fun quantisingTheBlurRadiusBuysNothing() {
        val constant = radiusCost(steps = null, moving = false)
        val stepped = radiusCost(steps = 5, moving = true)
        val continuous = radiusCost(steps = null, moving = true)

        println(
            "blur radius: constant %.1fms/frame · stepped %.1f (%.2f×) · continuous %.1f (%.2f×)"
                .format(constant, stepped, stepped / constant, continuous, continuous / constant)
        )
        println("radius sweep: " + radiusSweep())

        assertTrue(
            continuous < constant * 1.5,
            ("a blur radius rewritten every frame (%.1fms/frame) now costs materially more than " +
                "a constant one (%.1fms). Something began caching these layers, which means " +
                "quantising the fraction in `overlayBackdrop` would buy back the difference — " +
                "see this test's KDoc for the design that was cancelled on the old numbers.")
                .format(continuous, constant),
        )
    }

    /**
     * What the blur costs at a quarter, a half, one and two times the radius.
     *
     * Printed rather than asserted. It is the number that decides whether
     * `BackdropDefaults.BlurRadius` is a performance dial: if halving the radius
     * halves the cost then 24dp is worth arguing about, and if the curve is flat
     * then the radius is a purely visual choice and the only lever left is
     * whether the pass happens at all.
     *
     * **Measured: 186.2ms, 193.8ms, 200.2ms, 223.4ms** at a quarter, a half, one
     * and two times 24dp, against 26ms for the same frame unblurred. Eight times
     * the radius for twenty-three percent of the cost — the pass is very nearly
     * flat, because what it is paying for is the full-screen offscreen render and
     * not the size of the kernel. So the radius is a purely visual choice, 24dp
     * stays, and nothing is bought by softening the picture.
     */
    private fun radiusSweep(): String =
        listOf(0.25f, 0.5f, 1f, 2f).joinToString(" · ") { scale ->
            "%.2f× radius: %.1fms".format(scale, radiusCost(steps = null, moving = false, scale = scale))
        }

    /** Per-frame milliseconds either side of an overlay arriving. */
    private class Profile(times: DoubleArray) {
        /** The mean over the frames the entry is animating in. */
        val openingCost = times.take(OpeningFrames).average()

        /** The mean once it has arrived — what this file used to report alone. */
        val settledCost = times.drop(OpeningFrames).average()

        val worst = times.max()
    }

    /**
     * Times [TotalFrames] frames from the moment an overlay is shown.
     *
     * The entry is built here rather than by opening a `Dialog` or a `SideSheet`,
     * so that [BackdropStyle.Blur] and [BackdropStyle.BlurAndScale] are measured
     * over byte-identical content. The file used to open a `Dialog`, which is
     * `Blur`, and so never exercised the scale, the squircle clip or
     * `backdropGround`'s full-screen path fill at all.
     */
    private fun profile(style: BackdropStyle, blur: Boolean): Profile {
        val host = OverlayHostState()
        return Scene(width = PhoneWidthPx, height = PhoneHeightPx, density = 3f) {
            KontourTheme(backdropBlur = blur) {
                OverlayHost(Modifier.fillMaxSize(), host) { Behind() }
            }
        }.use { scene ->
            scene.advance(WarmUpFrames)
            host.show(
                OverlayEntry(
                    key = "panel",
                    layer = OverlayLayer.Sheet,
                    backdrop = style,
                    content = { Panel() },
                )
            )
            Profile(DoubleArray(TotalFrames) { millis { scene.advance() } })
        }
    }

    private fun radiusCost(steps: Int?, moving: Boolean, scale: Float = 1f): Double {
        var fraction by mutableStateOf(1f)
        return Scene(width = PhoneWidthPx, height = PhoneHeightPx, density = 3f) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val f = fraction
                    val quantised = if (steps == null) f else ceil(f * steps) / steps
                    val radius = FullRadiusPx * scale * quantised
                    renderEffect = if (radius > 0f) BlurEffect(radius, radius) else null
                }
            ) { Behind() }
        }.use { scene ->
            scene.advance(WarmUpFrames)
            val times = DoubleArray(TotalFrames) { frame ->
                if (moving) fraction = 0.5f + 0.5f * (frame + 1) / TotalFrames
                millis { scene.advance() }
            }
            times.average()
        }
    }

    private inline fun millis(block: () -> Unit): Double {
        val started = System.nanoTime()
        block()
        return (System.nanoTime() - started) / 1_000_000.0
    }

    @Composable
    private fun Behind() {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            LazyColumn(Modifier.padding(16.dp)) {
                items((1..40).toList()) { n ->
                    ListItem {
                        +"Elizabeth Quay"
                        supporting { +"Platform $n · Joondalup line" }
                    }
                }
            }
        }
    }

    /** A side sheet's shape, without a `SideSheet` — the same panel for both styles. */
    @Composable
    private fun Panel() {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(Color(0xFFF4F4F5))
                    .padding(24.dp)
            ) {
                Text("Rename favourite")
            }
        }
    }

    private companion object {
        /**
         * A real phone, in the physical pixels `Scene` actually takes.
         *
         * This said `420, 900`, which is not a phone — `ImageComposeScene`'s
         * width and height are **device pixels**, so at density 3 that was
         * 140×300dp, about a ninth of the surface it claimed to be measuring.
         * Blur cost scales with area, so the ratio it produced was measured over
         * a ninth of a screen and the figure that went into a commit message
         * with it was wrong. 1170×2532 is an iPhone 14: 390×844dp at 3×.
         */
        const val PhoneWidthPx = 1170
        const val PhoneHeightPx = 2532

        /** Long enough for the list to have laid out and the JIT to have seen the path. */
        const val WarmUpFrames = 10

        /**
         * Two thirds of a second — comfortably past any entry spring in the library.
         *
         * Kept deliberately short. A blurred frame at this size costs a fifth of a
         * second on a software rasteriser, so every frame in this file is real
         * wall-clock time in everybody's build, and the numbers are stable well
         * before the point where more samples would be worth the minutes.
         */
        const val TotalFrames = 40

        /**
         * The window the entry is still arriving in.
         *
         * Generous rather than tight. A window longer than the animation only
         * pulls the opening figure back towards the settled one, so an effect
         * that survives this split is understated rather than manufactured.
         */
        const val OpeningFrames = 15

        /** `BackdropDefaults.BlurRadius` at density 3, which is what the scenes use. */
        const val FullRadiusPx = 72f
    }
}
