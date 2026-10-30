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
 * | dimmed scrim, **no backdrop** | 1 draw | **11**, and 0 since |
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
 *
 * **And it was not only a caller's.** Changed 2026-10-29 for 0.5.1: a menu, a
 * popover and a sheet that does not recede have a scrim and no backdrop, and so
 * does a dialog wherever the backdrop cannot blur — Android 10 and 11, or a
 * theme without `backdropBlur`. So the scrim has a layer of its own now, and its
 * fade re-records itself rather than the application: the third row reads 0,
 * and `ScrimLayerPressureTest` holds it for each of those overlays.
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
     * A receding backdrop **isolates** the application, and so does the scrim
     * without one.
     *
     * The opposite of what this file was written to check, which is why it is
     * here. `overlayBackdrop`'s `graphicsLayer` was the only thing standing
     * between a scrim's fade and the whole screen behind it: with one, the
     * application is rasterised once and composited; without one it was part
     * of the host's recording and was re-recorded on every frame of the fade —
     * 11 of these 20. The scrim is recorded in a layer of its own now, so the
     * fade re-records only the scrim, and both arms read nothing.
     *
     * Pinned both ways, so that nobody makes `BackdropStyle.None` the default
     * for a dimmed entry on the grounds that a layer sounds expensive, and
     * nobody takes the scrim's layer away on the same grounds. The arm with no
     * backdrop also checks that the scrim was still fading while it counted:
     * nothing redrawn behind a scrim that had stopped animating would say
     * nothing at all.
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
            exposed.fadingFrames > 1,
            "the scrim was part of the way in on ${exposed.fadingFrames} of the $TravellingFrames frames " +
                "counted, so it was not fading while this counted, and the count below says nothing",
        )
        assertEquals(
            0, exposed.travellingDraws,
            "a dimmed overlay with no backdrop redrew the application " +
                "${exposed.travellingDraws} times over $TravellingFrames frames of its scrim fading in. " +
                "The scrim has a layer of its own so that its fade re-records the scrim and nothing " +
                "else; this is that layer gone, and the whole screen recorded again under every frame " +
                "of every menu, popover and dialog that cannot blur.",
        )
    }

    /**
     * A blurring backdrop isolates the application the same way, while the blur
     * grows step by step.
     *
     * Where the blur is drawn smaller — Skia's, and so this JVM — the step is
     * read in a draw block, because the effect and the canvas scale it undoes
     * have to change in the same frame. A draw block that wrapped the
     * application directly would redraw it on each of the ramp's twelve steps,
     * so the application has a layer of its own under it. This is what says it
     * still does.
     */
    @Test
    fun aBlurringBackdropKeepsTheApplicationFromRedrawingAsTheBlurGrows() {
        val blurred = mount(backdrop = BackdropStyle.Blur, scrim = ScrimStyle.Dimmed, trap = true)
        assertEquals(
            1, blurred.mountDraws,
            "mounting a blurring modal redrew the application ${blurred.mountDraws} times; " +
                "one is structural, as for every other overlay.",
        )
        assertEquals(
            0, blurred.travellingDraws,
            "the application was redrawn ${blurred.travellingDraws} times over " +
                "$TravellingFrames frames while a blur behind a dialog grew. Each step " +
                "of the blur should re-composite the application's layer, not re-record it.",
        )
    }

    /** What one arm reports. */
    private class Pressure(val mountDraws: Int, val travellingDraws: Int, val fadingFrames: Int = 0)

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
        var fadingFrames = 0
        val key = Any()

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
                        key = key,
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
            repeat(TravellingFrames) {
                mainClock.advanceTimeByFrame()
                if (host.fractionOf(key).let { it > 0f && it < 1f }) fadingFrames++
            }
            waitForIdle()
            travellingDraws = app.draws
        }

        return Pressure(mountDraws, travellingDraws, fadingFrames)
    }

    private companion object {
        const val SettleFrames = 8

        /** Long enough to contain the host's entry spring. */
        const val TravellingFrames = 20
    }
}
