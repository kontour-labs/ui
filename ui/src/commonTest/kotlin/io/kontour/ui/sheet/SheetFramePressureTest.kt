package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import io.kontour.ui.PhaseCounts
import io.kontour.ui.countPhases
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a bottom sheet costs on every frame that it moves.
 *
 * A sheet sliding open or being dragged is doing one thing: moving. Its size has
 * not changed, its content has not changed, and the only difference between one
 * frame and the next is where it sits. Compose has a phase for that, and it is
 * neither measure nor draw.
 *
 * ### Counting, because timing lied twice
 *
 * Two earlier attempts at this failed. The first counted recompositions, found
 * almost none, and concluded the sheet was fine. The second timed rasterisation
 * in a software renderer and compared the ratio against a phone's GPU, which is
 * not a comparison at all.
 *
 * Measure, layout and draw *invalidation* are different: they are CPU-bound
 * Kotlin running the identical code on a JVM and on a phone. A count taken here
 * is the count a phone sees. What a phone then *does* with a draw is far more
 * expensive than what this does with one — which only makes the count matter
 * more.
 *
 * ### The three numbers, and which one was the finding
 *
 * - **Measures** would be the worst of the three, and are not the problem here.
 *   The surface's height does track the animating offset, but the `layout` block
 *   between it and the content measures that content with `maxHeight = Infinity`
 *   — constant constraints, so the content is insulated and genuinely does not
 *   re-measure. Counted anyway as a guard: it is a real trap, and the next
 *   change to that block could fall into it.
 * - **Draws** are the finding. Measured at **0.9 per frame** while sliding, and
 *   at zero once fixed. The sheet's surface used to be sized to
 *   `containerHeight - offset` so that it always reached the bottom of the
 *   container — correct, and re-sized on every frame the sheet moved. A node
 *   whose size changes cannot keep the drawing it recorded last frame, so the
 *   whole sheet was re-recorded sixty times a second; on a phone that
 *   re-rasterises its two blurred `dropShadow` layers with it. A surface that is
 *   always `containerHeight` tall and simply starts lower down gets the same
 *   picture and never changes size.
 *
 *   Worth recording what did *not* fix it, because it is the obvious move:
 *   swapping `Modifier.offset` for a `graphicsLayer` translation changed the
 *   count not at all. Placement was never the problem — resizing was. With the
 *   size held constant, `offset` costs zero draws, so it stayed.
 * - **Anchor rebuilds** are the passenger, measured at **1.9 per frame**:
 *   [SheetState.updateAnchors] runs from the content's measure block and again
 *   from `onGloballyPositioned`. Each allocates a map, scans it pairwise for
 *   duplicates and rebuilds a `DraggableAnchors`. Its inputs are the container,
 *   the content and the detents; not one of them changes while a sheet slides.
 *
 * ### Budgets are per frame, and every case has a control
 *
 * An absolute count would pin this to however many frames the spring happens to
 * take, which is a motion-token decision and nothing to do with cost.
 *
 * And each case asserts first that the sheet actually moved. That is not
 * ceremony: the first draft of the drag case dragged a sheet that had never been
 * opened, so it had no anchors, so it went nowhere — and passed, with every
 * counter reading zero.
 */
@OptIn(ExperimentalTestApi::class)
class SheetFramePressureTest {

    @Test
    fun aSlidingSheetIsNotReRecordedEveryFrame() {
        var frames = 0
        var rebuilds = 0
        var settledAt = Float.NaN
        val counts = PhaseCounts()

        runComposeUiTest {
            var visible by mutableStateOf(false)
            lateinit var sheet: SheetState

            setContent { Harness(visible) { state -> sheet = state; Body(counts) } }
            waitForIdle()

            mainClock.autoAdvance = false
            visible = true
            // Mounting and the first measurement are legitimate first-time work.
            repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
            counts.reset()
            val rebuiltBefore = sheet.anchorRebuilds

            while (frames < MovingFrames && sheet.isMoving) {
                mainClock.advanceTimeByFrame()
                frames++
            }
            rebuilds = sheet.anchorRebuilds - rebuiltBefore
            settledAt = sheet.offset
        }


        assertTrue(frames > 8, "the sheet settled in $frames frames — too few to measure anything")
        assertTrue(
            settledAt.isFinite() && settledAt > 0f,
            "the sheet never moved: its offset ended at $settledAt",
        )

        assertBudget("measured", counts.measures, frames)
        assertBudget("drawn", counts.draws, frames)
        assertBudget("re-anchored", rebuilds, frames)
    }

    @Test
    fun aDraggedSheetIsNotReRecordedEveryFrame() {
        var frames = 0
        var travelled = 0f
        val counts = PhaseCounts()

        runComposeUiTest {
            var visible by mutableStateOf(false)
            lateinit var sheet: SheetState

            setContent { Harness(visible) { state -> sheet = state; Body(counts) } }
            waitForIdle()

            // Opened and settled first: a sheet that has never been shown has no
            // anchors, and dragging one moves it exactly nowhere.
            mainClock.autoAdvance = false
            visible = true
            repeat(OpenFrames) { mainClock.advanceTimeByFrame() }
            counts.reset()

            val startedAt = sheet.offset
            repeat(DraggedFrames) { step ->
                sheet.anchoredState.dispatchRawDelta(if (step % 2 == 0) -6f else -4f)
                mainClock.advanceTimeByFrame()
                frames++
            }
            travelled = abs(sheet.offset - startedAt)
        }


        assertTrue(
            travelled > 20f,
            "the sheet moved ${travelled}px over $frames frames of dragging, so this " +
                "measured a sheet standing still",
        )

        assertBudget("measured", counts.measures, frames)
        assertBudget("drawn", counts.draws, frames)
    }

    /** One modal sheet in a host, which is the shape an app uses. */
    @Composable
    private fun Harness(visible: Boolean, content: @Composable (SheetState) -> Unit) {
        KontourTheme {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    val state = rememberSheetState(
                        detents = listOf(
                            SheetDetent.Hidden,
                            SheetDetent.Half,
                            SheetDetent.Expanded,
                        ),
                        initialDetent = SheetDetent.Hidden,
                    )
                    ModalBottomSheet(
                        visible = visible,
                        onDismissRequest = {},
                        state = state,
                    ) {
                        content(state)
                    }
                }
            }
        }
    }

    /** Something with a real cost to measure and draw, so a repeat is not free. */
    @Composable
    private fun Body(counts: PhaseCounts) {
        Box(Modifier.fillMaxWidth().height(320.dp).countPhases(counts)) {
            Text("Perth Underground — Platform 2, Joondalup line")
        }
    }

    private fun assertBudget(what: String, count: Int, frames: Int) {
        assertTrue(
            count <= frames / GenerousBudget,
            "the sheet's content was $what $count times over $frames frames of moving — " +
                "about ${rate(count, frames)} per frame. A sheet that is only moving should " +
                "be recorded once and then translated; anything approaching one per frame " +
                "means it is being moved by placement rather than by a graphics layer, or " +
                "is rebuilding state that has not changed.",
        )
    }

    private fun rate(count: Int, frames: Int): String {
        if (frames == 0) return "0"
        val tenths = count * 10 / frames
        return "${tenths / 10}.${tenths % 10}"
    }

    private companion object {
        /** Enough for the sheet to mount and take its first measurement. */
        const val SettleFrames = 4

        /** Enough for it to open and come fully to rest. */
        const val OpenFrames = 40

        /** Long enough for a spring to settle, short enough to bound a hang. */
        const val MovingFrames = 240

        const val DraggedFrames = 40

        /**
         * At most one of anything per this many frames.
         *
         * Not zero. A sheet legitimately re-measures, re-draws and re-anchors
         * when something real changes — the content resizing, a spring crossing
         * a detent boundary as it settles, the container changing underneath it
         * — and a budget of zero would make this a test about Compose's
         * scheduling rather than about the sheet. Eight is an order of magnitude
         * below the one-per-frame it exists to catch and comfortably above
         * anything honest.
         */
        const val GenerousBudget = 8
    }
}
