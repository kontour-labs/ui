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
 * Three reports, one gesture:
 *
 * - *"The swipe actions are still a little bit too fiddly… I sometimes end up
 *   triggering the action."*
 * - *"when swiping/flicking to trigger the action, please make sure it's the
 *   outermost one that gets triggered"*
 * - *"that outermost action should expand to fill all actions, and the background
 *   colour should be of that action"*
 *
 * ### Why the first one was not a threshold problem
 *
 * `swipePositionalThreshold` had already been raised from 0.4 to 0.55 for it, and
 * the component's own KDoc said raising it was the only lever there is. It was not
 * even the right lever. Foundation resolves an `anchoredDraggable` fling through a
 * `computeTarget` that takes a **velocity** threshold as well as a positional one,
 * and above that threshold — 125dp/s, a private constant, slower than any swipe
 * anybody makes on purpose — direction decides and distance stops mattering. Once
 * the row had passed its actions, the next anchor in the direction of travel was the
 * committed one. Every ordinary flick past the reveal ran the action, whatever the
 * threshold said.
 *
 * ### Controlling the velocity from out here
 *
 * `Scene` timestamps its pointer events off the frame clock and renders a frame per
 * move, so a move is 16ms and the velocity is the step size times 62.5 px/s. The two
 * gestures below travel the **same distance** and differ only in how many steps they
 * take, which is the distinction under test: 520px in 4 steps is 8125px/s and a
 * flick; the same 520px in 40 steps is 812px/s and a hand.
 */
class SwipeCommitTest {

    /**
     * The same travel, twice, at two speeds. Only the flick runs the action.
     *
     * Sixty per cent of the way from the reveal to the commit: past the 0.55 the
     * positional threshold asks for, and short of the `CommitShare` a slow drag now
     * has to earn. On the unfixed component both of these delete the row.
     */
    @Test
    fun aSlowDragRevealsWhereAFlickOfTheSameLengthCommits() {
        assertEquals(
            null,
            swipe(steps = SlowSteps),
            "a slow drag three fifths of the way from the actions to the commit ran " +
                "the action. It is past the positional threshold and nowhere near a " +
                "flick, which is the gesture 'I sometimes end up triggering the " +
                "action' describes.",
        )
        assertEquals(
            "Remove",
            swipe(steps = FlickSteps),
            "the same travel, flicked, did not run the action — a firm throw is " +
                "the gesture the commit is *for*",
        )
    }

    /**
     * And it is the outermost action a flick runs, not the first to opt in.
     *
     * Order runs edge-inward, so the first declared action is the one at the screen
     * edge — the one the row is sliding onto, and the one a full swipe visibly
     * becomes. `isFullSwipeAction` used to pick the action as well as enable the
     * gesture, so a row whose *inner* action set it committed the inner one, against
     * the component's own documented convention.
     */
    @Test
    fun aFlickRunsTheOutermostActionEvenWhenAnInnerOneOptedIn() {
        assertEquals(
            "Remove",
            swipe(steps = FlickSteps, optIn = OptIn.InnerOnly),
            "the inner action opted in and the outer one did not, and the flick ran " +
                "the inner one. A full swipe is the action at the screen edge " +
                "arriving and taking the row; there is only one action it can mean.",
        )
    }

    /** A side where nothing opted in still has no full swipe at all. */
    @Test
    fun aSideWithNoOptInStillCannotBeCommitted() {
        assertEquals(null, swipe(steps = FlickSteps, optIn = OptIn.Neither))
    }

    /**
     * Past the reveal, the outermost action swallows the strip.
     *
     * Counted rather than sampled at a point. The panels are internal, have no
     * semantics of their own and move every frame, so what is observable is ink: how
     * much of the vacated strip is the outer action's colour and how much is the
     * inner one's. Two actions of equal width start at parity, and three fifths of
     * the way to the commit the outer one is four times the inner.
     *
     * Held rather than released, so the measurement is of the gesture rather than of
     * what it committed to.
     */
    @Test
    fun theOutermostActionGrowsIntoTheStripPastTheReveal() {
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
                to = Offset(from.x - Travel, from.y),
                steps = SlowSteps,
                release = false,
            )
            frame = scene.frames(2)
            scene.release(Offset(from.x - Travel, from.y))
        }

        val (outer, inner) = requireNotNull(frame).inkOf(bounds)
        assertTrue(
            outer > 0 && inner >= 0,
            "no action ink at all — the row did not open",
        )
        assertTrue(
            outer > inner * 2,
            "the outer action holds ${outer}px of the strip and the inner one " +
                "${inner}px. Past the reveal the committing action is supposed to be " +
                "growing into the others, and at parity it is not growing at all.",
        )
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
     * An ordinary flick reveals. Only a throw aimed past the row commits.
     *
     * The second half of "still a bit fiddly", and it arrived through the *new* rule
     * rather than the old one. Taking the anchor nearest a flick's projected landing
     * is right for a sheet, whose detents are evenly spaced, and too generous for a
     * row, whose reveal sits 88dp out while its commit is the whole width: a flick
     * projecting 215dp is *nearer* to the commit than to the reveal and runs the
     * action, having travelled 75px.
     *
     * So a commit has to be aimed at, not merely nearest to. 150px in eight moves is
     * 1172px/s here — comfortably a flick, and projected about 429px, which is past
     * the 176px reveal and well short of the 600px commit.
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

    /** Which action ran, or null if none did. */
    private fun swipe(steps: Int, optIn: OptIn = OptIn.OuterOnly): String? {
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
            scene.drag(
                from = from,
                to = Offset(from.x - Travel, from.y),
                steps = steps,
            )
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

        /**
         * Three fifths of the way from the reveal to the commit, plus the touch slop
         * the first moves of any gesture go to.
         *
         * Two 88dp actions at this scene's density of 2 reveal at 352px, and the
         * commit anchor is the row's own width of 600. So the reveal-to-commit
         * distance is 248px and this is `352 + 0.6 * 248 + 20`.
         */
        const val Travel = 520f

        /** 130px a move at 16ms: 8125px/s, well past any definition of a flick. */
        const val FlickSteps = 4

        /** 13px a move: 812px/s, which is a hand moving and not a throw. */
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
