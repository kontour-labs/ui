package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.Copy
import io.kontour.ui.components.display.Card
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.SideSheet
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the *first* open of a sheet costs, frame by frame, against later opens.
 *
 * Reported from a phone: the first time any sheet opens it "jumps between a few
 * frames and is then open", the first dropdown "just appears" with its arrow
 * snapped round, and every open after that is fine. It happens again after
 * coming back to a component left alone for a while.
 *
 * ### Why this is measurable here at all
 *
 * Because of one number in the report: **40.4ms worst, on iOS and on Android.**
 * Two different GPUs, two different graphics APIs, one figure. That rules out the
 * candidate at the top of this repository's list — Skia compiling a Metal
 * pipeline the first time a blur or a shadow is drawn would not land on the same
 * millisecond on Vulkan. What is left is CPU-bound work, and CPU-bound work is
 * the kind this container can measure honestly: the same Kotlin runs here.
 *
 * ### The experiment, and what each arm rules out
 *
 * A per-frame wall clock across the frames of an opening sheet, in three arms
 * inside one JVM so that the JIT is equally warm in all of them:
 *
 * | arm | scene | content | what a slow arm means |
 * |---|---|---|---|
 * | `first` | new | new | nothing yet; this is the reported case |
 * | `again` | same | same | the cost is paid on *every* open, not the first |
 * | `different` | new | **different text and icons** | the cost is per content |
 *
 * `again` is the control that says the effect is a first-time effect at all.
 * `different` is the one that discriminates: a fresh scene re-runs composition
 * and layout either way, so if `different` is slow while `again` is fast, what
 * costs is filling caches that are keyed by *content* — glyphs for text not yet
 * shaped, an `ImageVector` an icon set builds on first access — rather than
 * composing a tree.
 *
 * A **discarded warm-up arm** runs first and is thrown away, for the reason
 * `ThemeSwitchCostDiagnostic` records: the first measurement in a JVM is a
 * measurement of the JIT, and it once produced a 2x difference that looked
 * exactly like a finding.
 *
 * ### A diagnostic, not a gate
 *
 * The assertion is a catastrophe bound no reasonable machine reaches. What this
 * is for is the table it prints.
 */
@OptIn(ExperimentalTestApi::class)
class FirstOpenCostDiagnostic {

    @Test
    fun whatTheFirstOpenOfASheetCosts() {
        // Thrown away: the JIT pass.
        frameCosts(Plain)

        val first = frameCosts(Plain).first()
        val again = frameCosts(Plain, opens = 2).last()
        val different = frameCosts(Other).first()

        println("first open of a sheet, ms per frame across the opening animation")
        println("  arm        " + (1..Frames).joinToString(" ") { "f$it".padStart(6) })
        for ((name, costs) in listOf("first" to first, "again" to again, "different" to different)) {
            println("  ${name.padEnd(11)}" + costs.joinToString(" ") { it.ms().padStart(6) })
        }
        println()
        println("  worst frame — first ${first.max().ms()}, again ${again.max().ms()}, different ${different.max().ms()}")
        println("  total       — first ${first.sum().ms()}, again ${again.sum().ms()}, different ${different.sum().ms()}")

        assertTrue(
            first.sum() < CatastropheNanos,
            "opening a sheet for the first time took ${first.sum().ms()}ms of wall " +
                "clock across $Frames frames, which is past anything this is " +
                "trying to tell apart — read the table above rather than this line",
        )
    }

    /**
     * Wall clock per frame while a sheet opens, for the [opens]th open.
     *
     * The clock is hand-driven so that each `advanceTimeByFrame` is one frame of
     * work and nothing else, and what is timed is the real cost of producing it.
     * That is the opposite of the fixed-16ms reasoning the two theme diagnostics
     * use — they compare per-frame cost at a forced cadence; this one is about
     * how long a frame actually takes, because that is what a dropped frame is.
     */
    private fun frameCosts(content: @Composable () -> Unit, opens: Int = 1): List<List<Long>> {
        val runs = mutableListOf<List<Long>>()
        var visible by mutableStateOf(false)
        runDesktopComposeUiTest(width = Width, height = Height) {
            mainClock.autoAdvance = false
            setContent {
                KontourTheme {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) { content() }
                        SideSheet(visible = visible, onDismissRequest = { visible = false }) {
                            content()
                        }
                    }
                }
            }
            settle()

            repeat(opens) {
                visible = true
                val costs = mutableListOf<Long>()
                repeat(Frames) {
                    val started = System.nanoTime()
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    costs += System.nanoTime() - started
                }
                runs += costs
                visible = false
                settle()
            }
        }
        return runs
    }

    private fun androidx.compose.ui.test.ComposeUiTest.settle() {
        repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
        waitForIdle()
    }

    private fun Long.ms(): String = "${this / 1_000_000}.${(this / 100_000) % 10}"

    private companion object {
        const val Width = 900
        const val Height = 1600

        /** Enough frames to contain a snappy spring at 60Hz. */
        const val Frames = 12

        /** Long enough for an opening or closing sheet to finish before the next arm. */
        const val SettleFrames = 40

        const val CatastropheNanos = 10_000_000_000L

        /** Six rows of text and an icon each — a sheet's worth of content. */
        val PlainRows = listOf(
            "Perth Underground" to Tabler.Outline.Bus,
            "Elizabeth Quay" to Tabler.Outline.Clock,
            "Esplanade" to Tabler.Outline.Calendar,
            "Claremont" to Tabler.Outline.Bell,
            "Cottesloe" to Tabler.Outline.Bookmark,
            "Fremantle" to Tabler.Outline.Copy,
        )

        /**
         * The same shape, different glyphs and different icons.
         *
         * Deliberately no word and no icon shared with [PlainRows]: what this arm
         * is for is a scene whose *caches* are cold while the code paths around
         * them are hot, and a shared glyph would warm part of what is being
         * measured.
         */
        val OtherRows = listOf(
            "Kwinana Freeway" to Tabler.Outline.Bookmark,
            "Mitchell Highway" to Tabler.Outline.Copy,
            "Graham Farmer" to Tabler.Outline.Bus,
            "Roe Highway" to Tabler.Outline.Bell,
            "Tonkin Highway" to Tabler.Outline.Calendar,
            "Leach Highway" to Tabler.Outline.Clock,
        )

        val Plain: @Composable () -> Unit = { Rows(PlainRows) }
        val Other: @Composable () -> Unit = { Rows(OtherRows) }
    }
}

@Composable
private fun Rows(rows: List<Pair<String, ImageVector>>) {
    Column(Modifier.padding(Theme.spacing.md)) {
        for ((label, icon) in rows) {
            Card {
                Row(Modifier.padding(Theme.spacing.sm)) {
                    Icon(icon, contentDescription = null)
                    Text(label, style = Theme.typography.bodyMedium)
                }
            }
            Text(label, style = Theme.typography.bodySmall)
        }
    }
}
