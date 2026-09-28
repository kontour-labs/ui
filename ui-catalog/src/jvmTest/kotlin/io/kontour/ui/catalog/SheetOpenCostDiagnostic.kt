package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Which frame of a sheet's opening is the expensive one, and what is on it.
 *
 * Reported from a phone: sheet animations stutter on iOS.
 * `FirstOpenCostDiagnostic` already measured a `SideSheet` and found the second
 * frame of the open costing about 44ms — **in all three of its arms, including
 * the one that opens the same sheet a second time**. So it is not a first-open
 * effect, not the glyph cache and not an `ImageVector` being built: it is
 * something every open pays.
 *
 * This asks the next question, which that one could not: *what* is on that
 * frame. Three arms, differing only in what the sheet holds.
 *
 * | arm | sheet content | a slow frame 2 means |
 * |---|---|---|
 * | `empty` | nothing at all | the cost is the sheet and the host, not the content |
 * | `light` | one line of text | |
 * | `heavy` | twelve list rows | the cost is composing what is inside it |
 *
 * `empty` is the discriminator. If it is as slow as `heavy`, no amount of
 * making a sheet's content cheaper will help and the machinery is where to
 * look; if it is fast, the fix is about *when* the content composes rather than
 * about what it costs — which is the shape the nav drawer's dead time turned
 * out to have.
 *
 * Each arm runs twice and the first is discarded: the first measurement in a JVM
 * is a measurement of the JIT, and the first `Text` in one is a measurement of
 * the font.
 *
 * ### What it found
 *
 * ```
 *   arm          f1     f2     f3     f4     f5     f6     f7     f8
 *   empty       2.3   23.2    1.5    5.6    4.8    5.5    5.3    5.4
 *   light       2.8   22.7    1.3    5.1    4.8    4.6    5.0    5.2
 *   heavy       2.4   35.8    0.9    4.6    4.2    4.3    4.6    4.9
 * ```
 *
 * **The animation is not the stutter.** From the third frame on, a sheet opening
 * costs 4-6ms a frame and re-records nothing — which is what
 * `SheetFramePressureTest` already pins from the other direction. What costs is
 * frame 2, once per open: the frame on which the overlay entry mounts and
 * everything inside it composes and measures for the first time.
 *
 * **And it is mostly not the content.** An *empty* sheet pays 23.2ms there,
 * against 1.5 on the frame after it; twelve list rows add 12.6 on top. So two
 * thirds of it is the machinery — the entry, the scrim, the backdrop, the
 * surface with its two shadow layers, the focus trap, the first
 * `updateAnchors` — and an app cannot make it go away by putting less in its
 * sheets.
 *
 * That is the next thing to pull on, and it is deliberately not pulled on here:
 * this file's job was to find out which frame and what is on it, and a change
 * to the mount path wants its own round with this diagnostic on both sides of
 * it. What can be said already is that it is one frame rather than a
 * continuous cost, which is why it reads as a jump at the start of an
 * otherwise clean animation rather than as a slow one.
 *
 * ### A diagnostic, not a gate
 *
 * The assertion is a catastrophe bound. What this is for is the table.
 */
@OptIn(ExperimentalTestApi::class)
class SheetOpenCostDiagnostic {

    @Test
    fun whichFrameOfASheetOpenCosts() {
        // Each arm runs twice and the first is thrown away. Once was not
        // enough and the reason is worth keeping: a single empty warm-up left
        // the *lighter* text arm reading 98ms against the heavy one's 65,
        // because the first `Text` in a JVM pays for the font. A warm-up has to
        // warm the same things the measurement will use.
        val empty = twice { }
        val light = twice { Text("Perth Underground") }
        val heavy = twice {
            Column(Modifier.fillMaxWidth()) {
                repeat(12) { ListItem { +"Departure ${it + 1}" } }
            }
        }

        println("opening a modal sheet, ms per frame")
        println("  arm      " + (1..Frames).joinToString(" ") { "f$it".padStart(6) })
        for ((name, costs) in listOf("empty" to empty, "light" to light, "heavy" to heavy)) {
            println("  ${name.padEnd(9)}" + costs.joinToString(" ") { it.ms().padStart(6) })
        }
        println()
        println(
            "  worst frame — empty ${empty.max().ms()}, light ${light.max().ms()}, " +
                "heavy ${heavy.max().ms()}"
        )
        println(
            "  total       — empty ${empty.sum().ms()}, light ${light.sum().ms()}, " +
                "heavy ${heavy.sum().ms()}"
        )

        assertTrue(
            heavy.sum() < CatastropheNanos,
            "opening a sheet took ${heavy.sum().ms()}ms of wall clock across $Frames " +
                "frames, which is past anything this is trying to tell apart — read " +
                "the table above rather than this line",
        )
    }

    /**
     * The **smallest** cost seen at each frame position over several runs.
     *
     * One run per arm was enough to find which frame costs and was not enough to
     * tell two fixes for it apart: the same build measured its mount frame at
     * 10.9ms and 16.5ms on consecutive runs, which is wider than the difference
     * being looked for. Noise on a wall clock only ever adds — a scheduler, a
     * GC, another fork — so the minimum at each position is the closest estimate
     * of the work itself, and it is stable enough to compare two builds with.
     *
     * The first run is still discarded outright: the first measurement in a JVM
     * is a measurement of the JIT, and the first `Text` in one is a measurement
     * of the font.
     */
    private fun twice(content: @Composable () -> Unit): List<Long> {
        frameCosts(content)
        val runs = List(Passes) { frameCosts(content) }
        return List(Frames) { frame -> runs.minOf { it[frame] } }
    }

    private fun frameCosts(content: @Composable () -> Unit): List<Long> {
        var visible by mutableStateOf(false)
        val costs = mutableListOf<Long>()

        runDesktopComposeUiTest(width = Width, height = Height) {
            mainClock.autoAdvance = false
            setContent {
                KontourTheme {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize())
                        ModalBottomSheet(visible = visible, onDismissRequest = {}) {
                            Box(Modifier.fillMaxWidth().height(200.dp)) { content() }
                        }
                    }
                }
            }
            repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
            waitForIdle()

            visible = true
            repeat(Frames) {
                val started = System.nanoTime()
                mainClock.advanceTimeByFrame()
                waitForIdle()
                costs += System.nanoTime() - started
            }
        }
        return costs
    }

    private fun Long.ms(): String = "${this / 1_000_000}.${(this / 100_000) % 10}"

    private companion object {
        const val Width = 420
        const val Height = 900
        const val Frames = 12

        /** Runs per arm, after the discarded one. See [twice]. */
        const val Passes = 7
        const val SettleFrames = 20
        const val CatastropheNanos = 10_000_000_000L
    }
}
