package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.display.Card
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the **first** light↔dark switch costs, against the fourth.
 *
 * Reported from an Android phone: switching light and dark still stutters, about
 * 66.6ms peak — *"however, I've noticed it only really happens when I first open
 * the app. After I switch it a few times, it's really smooth."*
 *
 * ### Why none of the three theme diagnostics could answer this
 *
 * They all flip `dark` exactly once per scene, and two of them run a warm-up whose
 * whole purpose is to erase first-run effects. `ThemeSwitchCostDiagnostic` goes
 * further and records having *seen* this effect before throwing it away — "the
 * first row came out at 28.57ms and the same configuration, measured again later
 * in the same test, at 14.71 — the JIT compiling the render path, arriving as a
 * doubling that looked exactly like a result". That was the right call for what
 * that file measures. It is the thing this file is for.
 *
 * So there is **no discarded warm-up here**, and that is the design. The first
 * flip is the measurement.
 *
 * ### What a result would mean
 *
 * Nothing in the library holds a cache that light↔dark moves. The one
 * hand-written cache, `SquirclePaths`, is keyed on size, radii and smoothing, none
 * of which a scheme touches; `ColourScheme` and `Elevation` are rebuilt on *every*
 * switch, warm or cold. So a first flip that costs more than a later one here is
 * the JVM warming up, and on a phone the equivalent is the missing baseline
 * profile — a debug build has none, so the first run through any code path is
 * interpreted. `FrameReadout` already says so for sheets.
 *
 * If flip 1 and flip 4 come out level, the honest reading of a debug-build phone
 * figure is that it is a debug-build figure, and the comparison to make is
 * `:showcase:android:installRelease`.
 *
 * What this cannot do is *fix* it: a baseline profile needs a macrobenchmark
 * module and a device.
 *
 * ### A diagnostic, not a gate
 *
 * The assertion is a catastrophe bound no reasonable machine reaches. What this is
 * for is the table it prints.
 */
@OptIn(ExperimentalTestApi::class)
class FirstThemeSwitchCostDiagnostic {

    @Test
    fun whatTheFirstThemeSwitchCosts() {
        val flips = flipCosts()

        println("light<->dark, ms per frame across the fade, one scene flipped $Flips times")
        println("  flip       " + (1..Frames).joinToString(" ") { "f$it".padStart(6) })
        flips.forEachIndexed { index, costs ->
            println("  ${(index + 1).toString().padEnd(11)}" + costs.joinToString(" ") { it.ms().padStart(6) })
        }
        println()
        println("  worst frame " + flips.joinToString(" · ") { it.max().ms() })
        println("  total       " + flips.joinToString(" · ") { it.sum().ms() })
        val first = flips.first().sum()
        val last = flips.last().sum()
        println(
            "  first/last  ${first.ms()} vs ${last.ms()} — " +
                if (last > 0) "%.2fx".format(first.toDouble() / last) else "n/a"
        )

        assertTrue(
            first < CatastropheNanos,
            "the first theme switch took ${first.ms()}ms of wall clock across " +
                "$Frames frames, which is past anything this is trying to tell " +
                "apart — read the table above rather than this line",
        )
    }

    /**
     * Wall clock per frame across each of [Flips] successive fades, one scene.
     *
     * One scene throughout, because the question is what repetition warms — a fresh
     * scene per arm would re-pay composition and layout each time and drown it.
     * The clock is hand-driven so each `advanceTimeByFrame` is one frame of work
     * and what is timed is how long producing it actually took, which is what a
     * dropped frame is. `FirstOpenCostDiagnostic` does the same and says why.
     */
    private fun flipCosts(): List<List<Long>> {
        val runs = mutableListOf<List<Long>>()
        var dark by mutableStateOf(false)
        runDesktopComposeUiTest(width = Width, height = Height) {
            mainClock.autoAdvance = false
            setContent {
                // The fade, asked for: it is off by default now, and this file's
                // subject is what its frames cost. See `animateThemeChanges`.
                KontourTheme(darkTheme = dark, animateThemeChanges = true) {
                    // Elevated surfaces, because the two theme diagnostics both
                    // put shadows at four fifths of what a fade frame costs. A
                    // workload without them measures the cheap part.
                    Column(Modifier.fillMaxSize().padding(Theme.spacing.md)) {
                        repeat(Cards) { index ->
                            Card {
                                Row(Modifier.padding(Theme.spacing.sm)) {
                                    Text("Row $index", style = Theme.typography.bodyMedium)
                                }
                            }
                            Button(onClick = {}) { Text("Button $index") }
                        }
                    }
                }
            }
            repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
            waitForIdle()

            repeat(Flips) {
                dark = !dark
                val costs = mutableListOf<Long>()
                repeat(Frames) {
                    val started = System.nanoTime()
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    costs += System.nanoTime() - started
                }
                runs += costs
                repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
                waitForIdle()
            }
        }
        return runs
    }

    private fun Long.ms(): String = "${this / 1_000_000}.${(this / 100_000) % 10}"

    private companion object {
        const val Width = 900
        const val Height = 1600

        /** Enough frames to contain the whole of `tweenDefault` at 60Hz. */
        const val Frames = 16

        /**
         * Four, which is what the report describes.
         *
         * *"After I switch it a few times, it's really smooth"* — so the shape to
         * look for is a first row well above the rest and then a flat floor. Two
         * flips could not tell that from noise; four can, and each one is a fade
         * plus a settle rather than a cheap repeat.
         */
        const val Flips = 4

        /** Long enough for a fade to finish before the next flip starts. */
        const val SettleFrames = 30

        /** Enough elevated surfaces that shadows dominate, as they do on a phone. */
        const val Cards = 10

        const val CatastropheNanos = 10_000_000_000L
    }
}
