package io.kontour.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.PhaseCounts
import io.kontour.ui.countPhases
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What opening an overlay costs the **application behind it**.
 *
 * Every other instrument in this repository measures the thing that appears.
 * This one measures the thing that was already there, and it is the half nobody
 * had looked at.
 *
 * ### Counts, not milliseconds
 *
 * For the reason [PhaseCounts] gives: a measure, a placement and a draw
 * *invalidation* are CPU-bound Kotlin running the identical code on a JVM and on
 * a phone, so a count taken here is the count a phone takes. What a phone then
 * does with a draw is far more expensive than what this does with one, which
 * only makes the count matter more — and it is why the numbers below are worth
 * pinning even though the application in this harness is an empty white box
 * whose re-record costs nothing at all.
 *
 * ### What it found, and what it corrected
 *
 * The hypothesis this was written to test was that `overlayBackdrop`'s
 * full-screen `graphicsLayer` — inserted on the mount frame, absent before it —
 * was the expensive part of a sheet's second frame. **It is not.** It is the
 * *cure*:
 *
 * | arm | mount | the 20 frames after |
 * |---|---|---|
 * | nothing shown | 0 draws | 0 |
 * | entry, no scrim, no backdrop | 1 draw | 0 |
 * | dimmed scrim, **no backdrop** | 1 draw | **11** |
 * | dimmed scrim, `BackdropStyle.Scale` | 1 draw | 0 |
 *
 * One re-record on the mount frame is structural and the same in every arm,
 * including the one where nothing above the application changes at all: an
 * overlay is a **sibling** inside the host's own `Box`, and adding a child to a
 * `Box` re-records it — along with every child that has no layer of its own to
 * keep. Until a backdrop gives the application a layer, it is one of those
 * children.
 *
 * The third row is the finding. A scrim fading in over eleven frames re-records
 * the whole application for every one of them, because there is nothing between
 * the two. `BackdropStyle.Scale` and `Blur` both insert a layer, so every
 * overlay the library itself builds is isolated — but `scrim = Dimmed` with
 * `backdrop = None` is a legal pair a caller can write, and it is the pair that
 * costs a redraw of the entire screen sixty times a second while a scrim fades.
 */
@OptIn(ExperimentalTestApi::class)
class BackdropMountPressureTest {

    /**
     * One re-record of the application per mount, and it is not the backdrop's.
     *
     * Asserted in the arm where **nothing above the application changes** — no
     * scrim, no backdrop, no focus trap, so `overlayBackdrop` returns the chain
     * untouched and `clearAndSetSemantics` never appears. The draw happens
     * anyway, which is what says it is the sibling and not the modifier.
     *
     * The value of pinning it at one: two would mean the work is being done
     * twice, and zero would mean this instrument had stopped watching.
     */
    @Test
    fun mountingAnOverlayRecordsTheApplicationOnce() {
        val bare = mount(backdrop = BackdropStyle.None, scrim = ScrimStyle.None, trap = false)
        val dressed = mount(backdrop = BackdropStyle.Scale, scrim = ScrimStyle.Dimmed, trap = true)

        assertEquals(
            0, idle().mountDraws,
            "the application redrew on a frame with no overlay mounted at all, " +
                "so this instrument is measuring something other than the mount.",
        )
        assertEquals(
            1, bare.mountDraws,
            "mounting an overlay that dims nothing, recedes nothing and traps " +
                "nothing redrew the application ${bare.mountDraws} times. One is " +
                "structural — an overlay is a sibling in the host's `Box`, and a " +
                "`Box` that gains a child re-records what is already in it. More " +
                "than one is that work being done twice.",
        )
        assertEquals(
            1, dressed.mountDraws,
            "mounting a full modal — dimmed, receding, trapping — redrew the " +
                "application ${dressed.mountDraws} times against the bare " +
                "overlay's ${bare.mountDraws}. The backdrop, the semantics clear " +
                "and the focus flip all land on the node above the application " +
                "and none of them is a draw, so the two arms have to agree.",
        )
    }

    /**
     * A receding backdrop **isolates** the application; without one it redraws
     * for every frame a scrim fades.
     *
     * The opposite of what this file was written to check, which is why it is
     * here. `overlayBackdrop`'s `graphicsLayer` is the only thing standing
     * between a scrim's fade and the whole screen behind it: with one, the
     * application is rasterised once and composited; without one it is part of
     * the host's recording and is re-recorded on every frame of the fade.
     *
     * Every dimmed overlay the library builds asks for `Scale` or `Blur`, so
     * this is not a defect in a component — it is the cost of a pair a caller
     * can write, pinned so that nobody makes `BackdropStyle.None` the default
     * for a dimmed entry on the grounds that a layer sounds expensive.
     */
    @Test
    fun aRecedingBackdropIsWhatKeepsTheApplicationFromRedrawingBehindAScrim() {
        val isolated = mount(backdrop = BackdropStyle.Scale, scrim = ScrimStyle.Dimmed, trap = true)
        val exposed = mount(backdrop = BackdropStyle.None, scrim = ScrimStyle.Dimmed, trap = true)

        assertEquals(
            0, isolated.travellingDraws,
            "the application was redrawn ${isolated.travellingDraws} times over " +
                "$TravellingFrames frames behind a receding backdrop. A layer's " +
                "transform is supposed to re-composite rather than re-record — " +
                "that is the entire reason the backdrop uses one — so anything " +
                "here means the whole screen is being redrawn behind every open " +
                "sheet, every frame.",
        )
        assertTrue(
            exposed.travellingDraws > isolated.travellingDraws,
            "a dimmed overlay with no backdrop redrew the application " +
                "${exposed.travellingDraws} times against " +
                "${isolated.travellingDraws} with one. If these ever match, " +
                "either the scrim has stopped animating or the layer has stopped " +
                "isolating, and the second would be a regression this file exists " +
                "to catch.",
        )
    }

    /** What one arm reports. */
    private class Pressure(val mountDraws: Int, val travellingDraws: Int)

    private fun idle(): Pressure = mount(BackdropStyle.None, ScrimStyle.None, false, show = false)

    /**
     * Mounts a bare entry over a counted application and reports both numbers.
     *
     * A bare [OverlayEntry] rather than a `ModalBottomSheet`, because every flag
     * that matters here is one the sheet hardcodes. Its content is an empty
     * `Box`: what is being measured is behind the overlay, not in it.
     */
    private fun mount(
        backdrop: BackdropStyle,
        scrim: ScrimStyle,
        trap: Boolean,
        show: Boolean = true,
    ): Pressure {
        val app = PhaseCounts()
        var mountDraws = 0
        var travellingDraws = 0

        runComposeUiTest {
            lateinit var host: OverlayHostState

            setContent {
                KontourTheme {
                    host = rememberOverlayHostState()
                    // **Remembered, and that is half the instrument.**
                    //
                    // `countPhases` builds a `layout` and a `drawWithContent`
                    // from two fresh lambdas, and both elements compare by
                    // lambda identity — so applied inline it invalidates its own
                    // node on every composition of the node it is applied to. A
                    // mount is a frame on which this content recomposes, so an
                    // inline probe reports a draw for itself and calls it the
                    // application's. Measured: the control arm read one draw
                    // with nothing changed above it, for that reason and not the
                    // one it now reads it for.
                    //
                    // `SheetFramePressureTest` applies it inline and is right to:
                    // it counts frames of a sheet *sliding*, which recompose
                    // nothing. Here the recomposition is the subject.
                    val probe = remember { Modifier.countPhases(app) }
                    OverlayHost(Modifier.fillMaxSize(), state = host) {
                        Box(Modifier.fillMaxSize().then(probe).background(Color.White))
                    }
                }
            }
            waitForIdle()

            mainClock.autoAdvance = false
            // Settled first, so what is counted below is the mount and not the
            // application arriving.
            repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
            waitForIdle()
            app.reset()

            if (show) {
                host.show(
                    OverlayEntry(
                        key = Any(),
                        layer = OverlayLayer.Sheet,
                        scrim = scrim,
                        backdrop = backdrop,
                        trapFocus = trap,
                        content = { Box(Modifier.fillMaxSize()) },
                    )
                )
            }
            mainClock.advanceTimeByFrame()
            waitForIdle()
            mountDraws = app.draws

            // Then the entry's own progress spring, which is what drives both
            // the scrim's fade and the backdrop's scale. The layer, where there
            // is one, exists throughout; only its transform changes.
            app.reset()
            repeat(TravellingFrames) { mainClock.advanceTimeByFrame() }
            waitForIdle()
            travellingDraws = app.draws
        }

        return Pressure(mountDraws, travellingDraws)
    }

    private companion object {
        const val SettleFrames = 8

        /** Long enough to contain the host's entry spring. */
        const val TravellingFrames = 20
    }
}
