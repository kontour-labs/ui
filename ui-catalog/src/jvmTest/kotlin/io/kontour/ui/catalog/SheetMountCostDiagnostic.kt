package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Surface
import io.kontour.ui.overlay.BackdropStyle
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.OverlayHostState
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.overlay.rememberOverlayHostState
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.DragHandle
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.kontourElevation
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Which part of a sheet's mount frame costs, by taking the parts away.
 *
 * `SheetOpenCostDiagnostic` established *which frame*: the second one, once per
 * open, 23.2ms with an **empty** sheet in it against 1.5ms on the frame after —
 * so two thirds of it is machinery a caller cannot make cheaper by putting less
 * in the sheet. This file establishes *which machinery*, the only way a frame
 * can be split without a profiler: build it one piece at a time and subtract.
 *
 * ### The ladder
 *
 * Eleven rungs, each a superset of the one below it, one change per rung. The
 * delta between two adjacent rungs is the cost of the single thing that differs.
 *
 * Rungs 1 to 9 push a bare [OverlayEntry] around a plain [BottomSheet], which is
 * what `ModalBottomSheet` assembles for itself — so nothing here is a replica,
 * but every flag it hardcodes becomes something this can turn off.
 *
 * ### Two things that would make the table a lie, and what is done about them
 *
 * **A bare entry mounts a frame earlier than a `ModalBottomSheet` does.** The
 * component flips `visible`, a `LaunchedEffect` runs at the end of that frame,
 * and the entry appears on the next one; pushed directly, it appears on the
 * frame after the push. So the rows are compared on **the worst frame of the
 * open**, never on a frame index.
 *
 * **A replica that has drifted explains nothing.** Rung 9 is everything the real
 * component has, assembled by hand; rung 10 is the real component. If they
 * disagree by more than half, every delta above rung 0 is void, and that is an
 * assertion rather than a note.
 *
 * ### Warm-up
 *
 * Every rung runs twice and the first is discarded. `SheetOpenCostDiagnostic`
 * learned this the hard way twice over: the first measurement in a JVM measures
 * the JIT, and a warm-up that does not draw the same things measures the font
 * instead — an *empty* warm-up left its lighter arm reading 98ms against the
 * heavy one's 65.
 *
 * ### What it found
 *
 * ```
 *   rung                                worst       Δ
 *   0  host alone                         0.2      —
 *   1  + bare entry, empty                1.5     1.2
 *   ...
 *   6  + sheet, no shadow, no drag        3.9     1.5
 *   7  + the two shadow layers            8.5     4.5
 *   8  + drag handle                      8.7     0.2
 *   9  + draggable                        9.0     0.3
 *   10 the real ModalBottomSheet          9.9     0.8
 * ```
 *
 * **The shadow, by a factor of three over anything else.** Everything the host
 * does — the entry, the focus flip, the scrim, clearing the application's
 * semantics, giving it a layer to recede in — comes to 2.2ms between them.
 *
 * And a blur is **linear in the area it covers**: 1.5ms at 200dp tall, 2.8 at
 * 450, 6.6 at 900, 13.3 at 1800. The sheet's surface was as tall as the
 * *container* at every detent, so a sheet that only ever opens to a third of the
 * window was blurring a whole window's worth of shadow into the region hanging
 * below it. `SheetState.surfaceHeight` is the fix that came out of this, and
 * `SheetOpenCostDiagnostic` has the before and after.
 *
 * ### One hypothesis this cancelled
 *
 * `dropShadow` over an `Outline.Rounded` is a shape Skia can blur analytically;
 * over an `Outline.Generic` — twelve cubics, which is what a `SquircleShape`
 * returns — it might have a mask to raster first. Swapping the sheet's shape for
 * a `RoundedCornerShape` in this ladder made rung 7 more than twice as cheap,
 * which looked conclusive and was not: changing a `Surface`'s shape changes what
 * it **clips** with as well as what its shadow is blurred over, and the clip was
 * most of the difference.
 *
 * Isolated — two `dropShadow`s on a bare `Box`, no clip and no fill — the two
 * outlines cost **5.5ms and 5.4ms**. The outline is not the cost; the area is,
 * which is what the sweep below measures and what the fix acts on.
 *
 * ### A diagnostic, not a gate
 *
 * One catastrophe bound and one methodology assertion. What this is for is the
 * table it prints.
 */
@OptIn(ExperimentalTestApi::class)
class SheetMountCostDiagnostic {

    @Test
    fun whichPartOfTheMountFrameCosts() {
        val steps = ladder()

        // **The whole ladder is warmed before any of it is measured**, and that
        // is not the same as warming each rung.
        //
        // Warming per rung is what `SheetOpenCostDiagnostic` does and it is
        // enough there, where the arms differ only in what is inside a sheet.
        // Here the rungs bring in code none of the rungs below them ran — a
        // `dropShadow`, an `anchoredDraggable` — so a rung measured early pays
        // for JIT that every rung after it inherits, and the deltas tilt
        // downhill. Measured: rung 7 and a *second, identical* run of the same
        // configuration at the end of the file read 14.5ms and 8.9ms.
        repeat(2) { steps.forEach { it.run() } }

        val rungs = steps.map { Rung(it.name, it.isolates, it.measure()) }

        println("mounting a sheet, one piece at a time — ms on the worst frame of the open")
        println("  ${"rung".padEnd(34)}${"worst".padStart(7)}${"Δ".padStart(8)}  isolates")
        var previous = 0L
        for ((index, rung) in rungs.withIndex()) {
            val delta = if (index == 0) 0L else rung.worst - previous
            println(
                "  ${rung.name.padEnd(34)}${rung.worst.ms().padStart(7)}" +
                    (if (index == 0) "      —" else delta.ms().padStart(8)) +
                    "  ${rung.isolates}"
            )
            previous = rung.worst
        }

        // The closure line. An unexplained remainder is the most useful thing
        // this file can show, so it is printed rather than left to be inferred.
        val summed = rungs.last().worst - rungs.first().worst
        println()
        println("  rung 10 − rung 0 = ${summed.ms()}ms, which the rungs above account for in full by construction")
        println("  replica check    — rung 9 ${rungs[9].worst.ms()} against the real component's ${rungs[10].worst.ms()}")

        // Why rung 7 is worth a fix rather than a shrug: a blur is linear in
        // the area it covers, so a surface that is taller than the sheet ever
        // gets is paying for a shadow nobody can see.
        println()
        println("  the same two layers, over a taller and taller surface:")
        for ((height, cost) in areaSweep()) {
            println("    420 x ${height.toString().padStart(4)}  ${cost.ms().padStart(6)}")
        }

        assertTrue(
            rungs.last().worst < CatastropheNanos,
            "the worst frame of a sheet opening was ${rungs.last().worst.ms()}ms, " +
                "which is past anything this is trying to tell apart — read the " +
                "table above rather than this line",
        )

        val hand = rungs[9].worst
        val real = rungs[10].worst
        assertTrue(
            real > 0 && abs(hand - real).toDouble() / real < ReplicaTolerance,
            "the hand-assembled sheet's worst frame is ${hand.ms()}ms and the real " +
                "`ModalBottomSheet`'s is ${real.ms()}ms. Every delta in the table " +
                "above is a subtraction between two hand-assembled rungs, so if " +
                "the top of the ladder is not the component it is named after, " +
                "none of them is about the component either.",
        )
    }

    private class Rung(val name: String, val isolates: String, val worst: Long)

    /** One rung, measurable on demand so the ladder can be warmed as a whole. */
    private inner class Step(val name: String, val isolates: String, val run: () -> Long) {

        /**
         * The **smallest** worst-frame of several runs, not the median.
         *
         * Noise on a wall clock is one-directional — a scheduler, a GC, another
         * fork — so it only ever adds. The minimum is therefore the closest
         * estimate of what the work itself takes, and it is what makes a ladder
         * of supersets monotonic. A median of five did not: it reported rung 8
         * at 9.6ms against rung 7's 16.5, a superset costing less than what it
         * contains.
         */
        fun measure(): Long = List(Passes) { run() }.min()
    }

    private fun ladder(): List<Step> = listOf(
        Step("0  host alone", "the baseline frame") {
            worstFrame { _, _ -> }
        },
        Step("1  + bare entry, empty", "EntryHost itself") {
            worstFrame { host, _ -> host.show(entry { Empty() }) }
        },
        Step("2  + trapFocus", "the focus flip on the app node") {
            worstFrame { host, _ -> host.show(entry(trap = true) { Empty() }) }
        },
        Step("3  + transparent scrim", "the scrim's pointer nodes and fade") {
            worstFrame { host, _ ->
                host.show(entry(trap = true, scrim = ScrimStyle.Transparent) { Empty() })
            }
        },
        Step("4  + dimmed scrim", "clearAndSetSemantics over the app") {
            worstFrame { host, _ ->
                host.show(entry(trap = true, scrim = ScrimStyle.Dimmed) { Empty() })
            }
        },
        Step("5  + receding backdrop", "the app's full-screen graphicsLayer") {
            worstFrame { host, _ ->
                host.show(
                    entry(trap = true, scrim = ScrimStyle.Dimmed, backdrop = BackdropStyle.Scale) {
                        Empty()
                    }
                )
            }
        },
        Step("6  + sheet, no shadow, no drag", "sheet layout, Surface, first anchors") {
            worstFrame(shadow = false) { host, state ->
                host.show(sheetEntry(state) { Sheet(state, handle = false, drag = false) })
            }
        },
        Step("7  + the two shadow layers", "dropShadow at 6dp and 28dp") {
            worstFrame { host, state ->
                host.show(sheetEntry(state) { Sheet(state, handle = false, drag = false) })
            }
        },
        Step("8  + drag handle", "DragHandle's animations and gestures") {
            worstFrame { host, state ->
                host.show(sheetEntry(state) { Sheet(state, handle = true, drag = false) })
            }
        },
        Step("9  + draggable", "anchoredDraggable, nestedScroll, fling") {
            worstFrame { host, state ->
                host.show(sheetEntry(state) { Sheet(state, handle = true, drag = true) })
            }
        },
        Step("10 the real ModalBottomSheet", "closure") { modalWorstFrame() },
    )

    /**
     * What `Elevation.overlay`'s two layers cost over a taller and taller box.
     *
     * Nothing but the shadow: no clip, no fill, no sheet. The question is
     * whether a blur is paid per edge or per area, and the answer decides
     * whether making the sheet's surface shorter is worth anything.
     */
    private fun areaSweep(): List<Pair<Int, Long>> =
        listOf(200, 450, 900, 1800).map { height ->
            repeat(2) { shadowOnly(height) }
            height to List(Passes) { shadowOnly(height) }.min()
        }

    private fun shadowOnly(height: Int): Long {
        var worst = 0L
        runDesktopComposeUiTest(width = Width, height = height) {
            mainClock.autoAdvance = false
            setContent {
                Harness(shadow = true) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = Theme.shapes.sheet,
                        shadow = Theme.elevation.overlay,
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
            repeat(Frames) {
                val started = System.nanoTime()
                mainClock.advanceTimeByFrame()
                waitForIdle()
                worst = maxOf(worst, System.nanoTime() - started)
            }
        }
        return worst
    }

    /**
     * The longest frame of one open, in nanoseconds.
     *
     * The worst frame rather than the total, because a stall is a frame — the
     * same reasoning `FrameReadout` is built on — and because it is the one
     * figure a bare entry and a real component can be compared on when they
     * mount a frame apart.
     */
    private fun worstFrame(
        shadow: Boolean = true,
        open: (OverlayHostState, SheetState) -> Unit,
    ): Long {
        var worst = 0L
        runDesktopComposeUiTest(width = Width, height = Height) {
            lateinit var host: OverlayHostState
            lateinit var state: SheetState
            mainClock.autoAdvance = false
            setContent {
                Harness(shadow) {
                    host = rememberOverlayHostState()
                    state = rememberSheetState(
                        detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                        initialDetent = SheetDetent.Hidden,
                    )
                    OverlayHost(Modifier.fillMaxSize(), state = host) {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
            repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
            waitForIdle()

            open(host, state)
            repeat(Frames) {
                val started = System.nanoTime()
                mainClock.advanceTimeByFrame()
                waitForIdle()
                worst = maxOf(worst, System.nanoTime() - started)
            }
        }
        return worst
    }

    private fun modalWorstFrame(): Long {
        var worst = 0L
        var visible by mutableStateOf(false)
        runDesktopComposeUiTest(width = Width, height = Height) {
            mainClock.autoAdvance = false
            setContent {
                Harness(shadow = true) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize())
                        ModalBottomSheet(visible = visible, onDismissRequest = {}) {
                            Box(Modifier.fillMaxSize())
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
                worst = maxOf(worst, System.nanoTime() - started)
            }
        }
        return worst
    }

    @Composable
    private fun Harness(shadow: Boolean, content: @Composable () -> Unit) {
        // The shadow is taken away through the *theme* rather than through the
        // component, because `Surface` reads it from there — which is also the
        // control arm `ShadowCostDiagnostic` uses, so the two files price the
        // same thing the same way.
        val elevation = kontourElevation(dark = false)
        KontourTheme(
            elevation = if (shadow) elevation else elevation.copy(overlay = Shadow.None),
            content = content,
        )
    }

    /** An entry's content, where what is being measured is not in it. */
    @Composable
    private fun Empty() {
        Box(Modifier.fillMaxSize())
    }

    @Composable
    private fun Sheet(
        state: SheetState,
        handle: Boolean,
        drag: Boolean,
        shape: Shape = Theme.shapes.sheet,
    ) {
        // The same two-step `ModalBottomSheet` makes: the entry is pushed, and
        // the sheet is sent to its detent from an effect that runs at the end of
        // the composition the entry first appears in.
        //
        // Without it the hand-built rungs mount a sheet that never travels, so
        // it stays at `Hidden` below the window and everything the sheet only
        // does once it is *visible* is never paid for. The replica assertion is
        // what caught that: rung 9 read 3.2ms against the real component's 11.4.
        LaunchedEffect(state) { state.show() }
        BottomSheet(
            state = state,
            shape = shape,
            draggable = drag,
            dragHandle = if (handle) ({ DragHandle(state = state) }) else null,
        ) {
            Box(Modifier.fillMaxSize())
        }
    }

    private fun entry(
        trap: Boolean = false,
        scrim: ScrimStyle = ScrimStyle.None,
        backdrop: BackdropStyle = BackdropStyle.None,
        content: @Composable () -> Unit,
    ) = OverlayEntry(
        key = Any(),
        layer = OverlayLayer.Sheet,
        scrim = scrim,
        backdrop = backdrop,
        trapFocus = trap,
        content = content,
    )

    /**
     * The entry a `ModalBottomSheet` builds for itself, minus the sheet.
     *
     * `visibility` and `managesOwnExit` matter: without them the host runs its
     * own spring for an entry that is already animating on the sheet's, which is
     * a different open from the one being measured.
     */
    private fun sheetEntry(state: SheetState, content: @Composable () -> Unit) = OverlayEntry(
        key = Any(),
        layer = OverlayLayer.Sheet,
        scrim = ScrimStyle.Dimmed,
        backdrop = BackdropStyle.Scale,
        trapFocus = true,
        managesOwnExit = true,
        visibility = { state.visibleFraction },
        content = content,
    )

    private fun Long.ms(): String {
        val sign = if (this < 0) "-" else ""
        val tenths = abs(this) / 100_000
        return "$sign${tenths / 10}.${tenths % 10}"
    }

    private companion object {
        const val Width = 420
        const val Height = 900

        /** Enough to contain the mount and the travel that follows it. */
        const val Frames = 12
        const val SettleFrames = 20

        /**
         * The **smallest** worst-frame of this many runs, not the median.
         *
         * Noise on a wall clock is one-directional — a scheduler, a GC, another
         * fork — so it only ever adds. The minimum is therefore the closest
         * estimate of what the work itself takes, and it is what makes a ladder
         * of supersets monotonic. A median of five did not: it reported rung 8
         * at 9.6ms against rung 7's 16.5, which is a superset costing less than
         * what it contains and is arithmetically impossible.
         */
        const val Passes = 9

        const val CatastropheNanos = 10_000_000_000L

        /** How far the hand-assembled top rung may sit from the real component. */
        const val ReplicaTolerance = 0.5
    }
}
