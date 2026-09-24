package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Archive
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What it takes to run a swipe action, and which action a full swipe runs.
 *
 * Reported twice, from two phones, about the same gesture: on Android *"the
 * swiping is still too fiddly"* — a flick aimed at the actions ran one — and on iOS
 * *"the swipe is way too hard to do"* — a deliberate drag across the row did not.
 * Both came from deciding a commit by how the finger was *moving* when it let go.
 *
 * ### A point of no return instead
 *
 * The commit is now a place on the row, not a speed: a little way past the actions
 * and at least half the row. Released past it, the row commits at any speed;
 * released short of it, it never does, however hard it was thrown. A buzz marks the
 * line and the outermost action visibly takes the strip there, so the user can see
 * and feel the decision before they make it.
 *
 * ### The numbers
 *
 * Two 88dp actions at this scene's density of 2 reveal at 352px, and the point of
 * no return is `max(352 + 96, 0.55 · 600)` = 448px. `Scene` timestamps its pointer
 * events off the frame clock and renders a frame per move, so a move is 16ms and
 * the velocity is the step size times 62.5 px/s.
 */
class SwipeCommitTest {

    /**
     * Carried past the point of no return, the row commits — slowly or thrown.
     *
     * The slow one is the iOS report: it is the gesture a person makes when they
     * mean it, and the old rule wanted it thrown as well.
     */
    @Test
    fun aDragPastThePointOfNoReturnCommitsAtAnySpeed() {
        assertEquals(
            "Remove",
            swipe(steps = SlowSteps, settleBeforeRelease = true),
            "a drag carried past the point of no return and let go of gently ran " +
                "nothing. Past the line, letting go is the decision; how fast the " +
                "finger was moving is not.",
        )
        assertEquals(
            "Remove",
            swipe(steps = FlickSteps),
            "the same travel, flicked, did not run the action",
        )
    }

    /**
     * Thrown hard but let go of short of the line, it only opens.
     *
     * The Android report: a flick aimed at the actions carried on to run one.
     * 400px in four moves is 6,250px/s — as hard as a thumb throws — and lands past
     * the actions and short of the 448px line.
     */
    @Test
    fun aFlickShortOfThePointOfNoReturnOnlyReveals() {
        assertEquals(
            null,
            swipe(steps = FlickSteps, travel = ShortOfTheLine),
            "a hard flick let go of short of the point of no return ran the action. " +
                "A flick opens or closes the actions; only the line commits.",
        )
    }

    /**
     * Over the line and back again, and nothing runs.
     *
     * The line is where the decision is made, so it has to be undoable: a user who
     * feels the buzz and changes their mind backs off it.
     */
    @Test
    fun backingOffThePointOfNoReturnCancelsTheCommit() {
        assertEquals(
            null,
            swipe(steps = SlowSteps, backTo = ShortOfTheLine, settleBeforeRelease = true),
            "the row was carried past the point of no return and brought back short " +
                "of it before letting go, and the action still ran",
        )
    }

    /**
     * And it is the outermost action a full swipe runs, not the first to opt in.
     *
     * Order runs edge-inward, so the first declared action is the one at the screen
     * edge — the one the row is sliding onto, and the one a full swipe visibly
     * becomes. `isFullSwipeAction` used to pick the action as well as enable the
     * gesture, so a row whose *inner* action set it committed the inner one, against
     * the component's own documented convention.
     */
    @Test
    fun aFullSwipeRunsTheOutermostActionEvenWhenAnInnerOneOptedIn() {
        assertEquals(
            "Remove",
            swipe(steps = SlowSteps, optIn = OptIn.InnerOnly),
            "the inner action opted in and the outer one did not, and the swipe ran " +
                "the inner one. A full swipe is the action at the screen edge " +
                "arriving and taking the row; there is only one action it can mean.",
        )
    }

    /** A side where nothing opted in still has no full swipe at all. */
    @Test
    fun aSideWithNoOptInStillCannotBeCommitted() {
        assertEquals(null, swipe(steps = SlowSteps, optIn = OptIn.Neither))
        assertEquals(null, swipe(steps = FlickSteps, optIn = OptIn.Neither))
    }

    /**
     * At the point of no return, the outermost action takes the strip.
     *
     * Counted rather than sampled at a point. The buttons are internal, have no
     * semantics of their own and move every frame, so what is observable is ink: how
     * much of the vacated strip is the outer action's colour and how much is the
     * inner one's. Short of the line, two actions of equal width share it; past it,
     * the inner one has folded into the outer.
     *
     * Held rather than released, so the measurement is of the gesture rather than of
     * what it committed to. Under reduced motion, so the takeover is a cut and the
     * frame after the line already shows it.
     */
    @Test
    fun atThePointOfNoReturnTheOutermostActionTakesTheStrip() {
        val (shortOuter, shortInner) = heldAt(ShortOfTheLine)
        assertTrue(
            shortOuter > 0 && shortInner > shortOuter / 2,
            "held short of the point of no return, the outer action holds " +
                "${shortOuter}px of the strip and the inner one ${shortInner}px. " +
                "Short of the line they share it.",
        )
        val (outer, inner) = heldAt(Travel)
        assertTrue(
            outer > shortOuter && inner == 0,
            "held past the point of no return, the outer action holds ${outer}px of " +
                "the strip and the inner one still ${inner}px. Past the line the " +
                "action that will run is the only one showing.",
        )
    }

    /** Each action's ink with the row held [travel] px open, not released. */
    private fun heldAt(travel: Float): Pair<Int, Int> {
        var bounds = Rect.Zero
        var frame: BufferedImage? = null

        Scene(width = Width, height = Height, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                SwipeActions(
                    end = twoActions(OptIn.OuterOnly),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RowHeight.dp)
                        .reportBounds { bounds = it },
                ) {
                    ListItem { +"Perth Underground" }
                }
            }
        }.use { scene ->
            scene.frames(4)
            val from = Offset(Width - 20f, bounds.center.y)
            scene.drag(
                from = from,
                to = Offset(from.x - travel, from.y),
                steps = SlowSteps,
                release = false,
            )
            frame = scene.frames(2)
            scene.release(Offset(from.x - travel, from.y))
        }
        return requireNotNull(frame).inkOf(bounds)
    }

    /**
     * A full swipe runs the action **and puts the row back**.
     *
     * Reported after the first version of the settle shipped: *"when you swipe it all
     * the way to the left/right, it just stays there instead of resetting"*. The row
     * was parked mid-strip with nothing having run.
     *
     * One cause. A spring handed the release's velocity overshoots its target, and
     * the committed anchor is the end of the draggable's range, so `scrollBy` clamps
     * there and consumes nothing for the rest of the excursion. The settle tracked
     * the animation's own value through that, banking the refused pixels, and the
     * return leg paid them back out of the offset. `AnchoredDraggableState` only
     * adopts an anchor when the offset is within half a pixel of it, so landing tens
     * of pixels short meant no `settledValue`, no action and no reset.
     *
     * Two assertions, because either one alone passes on the defect: the action ran,
     * *and* there is no action ink left on screen.
     *
     * **Motion on, unlike everything else in this file**, and that is the assertion
     * rather than an oversight. `springOrTween` gives a *tween* under reduced motion,
     * a tween does not overshoot, and without an overshoot there is nothing to clamp
     * and nothing to bank. Written with `reduceMotion = true` the first time, this
     * test passed against the defect it was written for.
     */
    @Test
    fun aFullSwipeRunsTheActionAndPutsTheRowBack() {
        var ran: String? = null
        var bounds = Rect.Zero
        var frame: BufferedImage? = null

        Scene(width = Width, height = Height) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                SwipeActions(
                    end = twoActions(OptIn.OuterOnly) { ran = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RowHeight.dp)
                        .reportBounds { bounds = it },
                ) {
                    ListItem { +"Perth Underground" }
                }
            }
        }.use { scene ->
            scene.frames(4)
            val from = Offset(Width - 20f, bounds.center.y)
            // All the way across, thrown.
            scene.drag(
                from = from,
                to = Offset(20f, from.y),
                steps = FlickSteps,
            )
            frame = scene.frames(SettleFrames * 2)
        }

        assertEquals("Remove", ran, "a full swipe ran nothing at all")
        val (outer, inner) = requireNotNull(frame).inkOf(bounds)
        assertTrue(
            outer == 0 && inner == 0,
            "the action ran and the row is still showing ${outer}px of one action " +
                "and ${inner}px of the other — it stayed where the swipe left it " +
                "instead of going back",
        )
    }

    /**
     * An ordinary flick from rest reveals.
     *
     * The smallest case of the Android report: one action, a 150px flick at
     * 1,172px/s, well short of the line. It opens the action and nothing runs.
     */
    @Test
    fun anOrdinaryFlickFromRestRevealsRatherThanCommitting() {
        var ran: String? = null
        var bounds = Rect.Zero

        Scene(width = Width, height = Height, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                SwipeActions(
                    end = listOf(
                        SwipeAction(
                            label = "Remove",
                            icon = Tabler.Outline.Trash,
                            onAction = { ran = "Remove" },
                            background = Outer,
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RowHeight.dp)
                        .reportBounds { bounds = it },
                ) {
                    ListItem { +"Perth Underground" }
                }
            }
        }.use { scene ->
            scene.frames(4)
            val from = Offset(Width - 20f, bounds.center.y)
            scene.drag(
                from = from,
                to = Offset(from.x - ShortFlick, from.y),
                steps = ShortFlickSteps,
            )
            scene.frames(SettleFrames)
        }

        assertEquals(
            null,
            ran,
            "a 150px flick deleted the row. It is a flick, and it is nowhere near " +
                "far enough to be aimed at a commit 600px away.",
        )
    }

    /**
     * Which action ran, or null if none did, after a drag [travel] px toward the
     * leading edge in [steps] moves — carried back to [backTo] px if given — and let
     * go of either at once or, with [settleBeforeRelease], after the finger has been
     * still long enough that it carries no speed.
     */
    private fun swipe(
        steps: Int,
        optIn: OptIn = OptIn.OuterOnly,
        travel: Float = Travel,
        backTo: Float? = null,
        settleBeforeRelease: Boolean = false,
    ): String? {
        var ran: String? = null
        var bounds = Rect.Zero

        Scene(width = Width, height = Height, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                SwipeActions(
                    end = twoActions(optIn) { ran = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RowHeight.dp)
                        .reportBounds { bounds = it },
                ) {
                    ListItem { +"Perth Underground" }
                }
            }
        }.use { scene ->
            scene.frames(4)
            val from = Offset(Width - 20f, bounds.center.y)
            val out = Offset(from.x - travel, from.y)
            scene.drag(from = from, to = out, steps = steps, release = false)
            var end = out
            if (backTo != null) {
                end = Offset(from.x - backTo, from.y)
                repeat(steps) { step ->
                    val t = (step + 1).toFloat() / steps
                    scene.move(Offset(out.x + (end.x - out.x) * t, from.y))
                    scene.frame()
                }
            }
            // Past the velocity tracker's 40ms "the finger has stopped" horizon.
            if (settleBeforeRelease) scene.frames(4)
            scene.release(end)
            scene.frames(SettleFrames)
        }
        return ran
    }

    /**
     * Two trailing actions, declared edge-inward: `Remove` at the screen edge and
     * `Archive` against the row.
     */
    private fun twoActions(optIn: OptIn, onRun: (String) -> Unit = {}): List<SwipeAction> =
        listOf(
            SwipeAction(
                label = "Remove",
                icon = Tabler.Outline.Trash,
                onAction = { onRun("Remove") },
                background = Outer,
                isFullSwipeAction = optIn == OptIn.OuterOnly,
            ),
            SwipeAction(
                label = "Archive",
                icon = Tabler.Outline.Archive,
                onAction = { onRun("Archive") },
                background = Inner,
                isFullSwipeAction = optIn == OptIn.InnerOnly,
            ),
        )

    /** How many pixels of the row's band are each action's colour. */
    private fun BufferedImage.inkOf(row: Rect): Pair<Int, Int> {
        var outer = 0
        var inner = 0
        val top = row.top.toInt() + 4
        val bottom = row.bottom.toInt() - 4
        for (y in top until bottom) {
            for (x in 0 until width) {
                val rgb = getRGB(x, y)
                val r = rgb shr 16 and 0xFF
                val g = rgb shr 8 and 0xFF
                val b = rgb and 0xFF
                if (r > 150 && g < 100 && b < 100) outer++
                if (b > 150 && r < 100 && g < 100) inner++
            }
        }
        return outer to inner
    }

    private enum class OptIn { OuterOnly, InnerOnly, Neither }

    private companion object {
        const val Width = 600
        const val Height = 200
        const val RowHeight = 60

        /** Past the 448px point of no return with room to spare. */
        const val Travel = 520f

        /** Past the 352px reveal, short of the 448px point of no return. */
        const val ShortOfTheLine = 400f

        /** 130px a move at 16ms: 8125px/s, well past any definition of a flick. */
        const val FlickSteps = 4

        /**
         * 13px a move: 812px/s, a hand moving. Let go of mid-move that is just over
         * the 800px/s this component counts as a flick, so the cases that mean a
         * gentle release let the finger come to rest first.
         */
        const val SlowSteps = 40

        /** A flick that is genuinely one and is genuinely short. 1172px/s. */
        const val ShortFlick = 150f
        const val ShortFlickSteps = 8

        const val SettleFrames = 40

        /** Pure, so a pixel can be classified without knowing the theme. */
        val Outer = Color.Red
        val Inner = Color.Blue
    }
}
