package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.ToastHost
import io.kontour.ui.overlay.ToastHostState
import io.kontour.ui.overlay.ToastPosition
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a toast does when the finger lets go.
 *
 * Two reports, from mobile web: *"when you drag it, it doesn't rubber band back
 * to the original position when you let it go"*, and *"I can't drag them to
 * dismiss them now"*.
 *
 * ### The gap these live in
 *
 * `ToastStackTest` has exactly one test that touches the pointer, and its helper
 * ends at `scene.release(...)` — the last statement in the scene block. **No
 * frame is rendered after it and nothing is measured after it.** Every
 * assertion in that test reads a value captured before the release. So nothing
 * anywhere asserted that a released toast returns to where it started, and
 * nothing anywhere asserted that a toast can be swiped away at all.
 *
 * ### Why the moves are not spaced a frame apart
 *
 * `Scene.drag` renders a frame after every move, and `ToastStackTest`'s helper
 * does the same by hand. That is **the one condition under which the defect
 * disappears**: `Modifier.draggable` calls its delta handler once per pointer
 * event, and the accumulator this component used to keep was a value captured at
 * composition — so one move per frame always read a fresh base and always
 * summed correctly. A real finger emits several events per frame.
 *
 * `SliderDragTest` records the identical bug in the identical API and says the
 * same thing about `swipeRight`. So the drags here send their moves **without a
 * frame between them**, which is what a finger does.
 */
class ToastSwipeTest {

    @Test
    fun aDragReleasedShortOfTheThresholdReturnsToWhereItStarted() {
        val short = swipe(travel = -30f)
        val long = swipe(travel = -60f)

        assertEquals(
            short.before, short.after,
            "a toast dragged 30px and released settled ${short.after - short.before}px " +
                "from where it started. Below the threshold the card springs back, " +
                "and back means back",
        )
        assertEquals(
            long.before, long.after,
            "a toast dragged 60px and released settled ${long.after - long.before}px " +
                "from where it started",
        )
        // The pair is the part that cannot be fooled. A single drag returning to
        // *a* resting place proves nothing — a card that keeps a fraction of
        // every drag also comes to rest, just somewhere new each time. Two
        // distances have to agree.
        assertEquals(
            short.after, long.after,
            "a 30px drag settled at ${short.after} and a 60px drag at ${long.after}. " +
                "Where a toast comes to rest is not allowed to depend on how far " +
                "it was dragged",
        )
    }

    @Test
    fun aSwipeTowardTheAnchoredEdgeTakesTheToastAway() {
        // A bottom-anchored stack dismisses downward. Well past `SwipeAway`,
        // which is a third of the card's own height.
        val far = swipe(travel = 120f)

        assertTrue(
            far.gone,
            "a toast swiped 120px toward the edge it dismisses to is still on " +
                "screen, ${far.after - far.before}px from where it started. That " +
                "is the whole gesture — if this fails there is no way to send one " +
                "away by hand at all",
        )
    }

    @Test
    fun theSurvivorsSitInOnePlaceHoweverFarTheSwipeWent() {
        // Two toasts, the front one swiped away, at two different distances.
        //
        // Not "back where it started": dismissing a card removes its `Peek` from
        // the stack, so the survivors legitimately sit somewhere new. The first
        // version of this asserted the old position and failed by 22px — which
        // is the peek, not a leftover. The number was right and the assertion
        // was wrong.
        //
        // What must not depend on the swipe is *where they end up*. The drag
        // offset is shared by the whole stack so the pills travel with the front
        // card, and nothing used to put it back when that card was dismissed —
        // so every survivor stayed displaced by however far the finger went, for
        // as long as the stack lived.
        val near = swipe(travel = 120f, toasts = 2)
        val far = swipe(travel = 260f, toasts = 2)

        assertTrue(
            near.gone && far.gone,
            "a 120px swipe ${if (near.gone) "took the toast away" else "did not"} " +
                "and a 260px one ${if (far.gone) "did" else "did not"} — both have " +
                "to dismiss before this can say anything about the survivors",
        )
        // Without this the assertion below passes for the worst possible
        // reason. A survivor that kept the whole swipe is pushed clean off the
        // bottom of the scene, and two runs that both show *nothing* agree
        // perfectly. Measured: with the reset removed, both came back empty and
        // the test was green.
        assertTrue(
            near.height > 0 && far.height > 0,
            "the toast left behind is not on screen at all — ${near.height}px of " +
                "stack after a 120px swipe and ${far.height}px after a 260px one. " +
                "It kept the swipe and went over the edge with it",
        )
        assertEquals(
            near.after, far.after,
            "the toast left behind sits at ${near.after} after a 120px swipe and " +
                "at ${far.after} after a 260px one. Where the stack rests is not " +
                "allowed to remember how far the card that left was dragged",
        )
    }

    private class Swiped(
        /** Where the top of the stack sat before the gesture. */
        val before: Int,
        /** Where it sits once everything has settled. */
        val after: Int,
        /** How much of a stack is still drawn. Zero means nothing is on screen. */
        val height: Int,
        /** Whether the stack lost a card. */
        val gone: Boolean,
    )

    /**
     * Press the front toast, drag it [travel] pixels vertically, let go, settle.
     *
     * Positive is downward, which for the default bottom anchor is the way it
     * dismisses.
     */
    private fun swipe(travel: Float, toasts: Int = 1): Swiped {
        var before = 0
        var after = 0
        var startHeight = 0
        var endHeight = 0

        Scene(width = 600, height = SceneHeight) {
            val state = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(state, position = ToastPosition.Bottom)
                LaunchedEffect(Unit) {
                    // Pinned, so the frame is settled rather than mid-timer and
                    // nothing expires underneath the gesture.
                    repeat(toasts) { state.show("Toast number $it", durationMillis = 0) }
                }
            }
        }.use { scene ->
            val settled = scene.frames(60)
            before = settled.stackTop()
            startHeight = settled.stackHeight()

            // Aimed from the settled frame rather than written down:
            // `ToastStackTest` records pressing a coordinate copied from a taller
            // scene, 240px below the window, which moves nothing and reads
            // exactly like a control refusing to be dragged.
            val x = settled.width / 2f
            val y = settled.stackBottom() - Inside.toFloat()
            scene.press(Offset(x, y))
            // No frame between the moves. See the note on the class.
            for (step in 1..Steps) {
                scene.move(Offset(x, y + travel * step / Steps))
            }
            scene.release(Offset(x, y + travel))

            val rested = scene.frames(SettleFrames)
            after = rested.stackTop()
            endHeight = rested.stackHeight()
        }

        return Swiped(
            before = before,
            after = after,
            height = endHeight,
            gone = endHeight < startHeight,
        )
    }

    /** The topmost row carrying any toast ink. */
    private fun BufferedImage.stackTop(): Int =
        (0 until height).firstOrNull { y -> rowHasToast(y) } ?: height

    private fun BufferedImage.stackBottom(): Int =
        (height - 1 downTo 0).firstOrNull { y -> rowHasToast(y) } ?: 0

    private fun BufferedImage.stackHeight(): Int {
        val top = stackTop()
        return if (top >= height) 0 else stackBottom() - top + 1
    }

    private fun BufferedImage.rowHasToast(y: Int): Boolean =
        (0 until width).any { x ->
            val rgb = getRGB(x, y)
            val mean = (((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF)) / 3
            mean < Ground
        }

    private companion object {
        const val SceneHeight = 400

        /** The ground is white and a toast is near-black; the rest is shadow. */
        const val Ground = 200

        /** Far enough inside the card's bottom edge to be on it rather than its shadow. */
        const val Inside = 20

        /** Enough events that several land inside one frame, as a finger does. */
        const val Steps = 20

        /**
         * Long enough for `springSnappy` to arrive.
         *
         * `Scene.frame` advances 16ms, and the settle is stiffness 1400 with
         * damping 0.9 — a couple of hundred milliseconds. Forty frames is three
         * times over, and a card still moving at the end would fail as loudly.
         */
        const val SettleFrames = 40
    }
}
