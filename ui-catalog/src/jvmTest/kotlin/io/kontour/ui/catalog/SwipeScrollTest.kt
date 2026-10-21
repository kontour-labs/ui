package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Archive
import io.kontour.icons.tabler.outline.Trash
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeActionsDefaults
import io.kontour.ui.components.list.SwipeActionsState
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberSwipeActionsState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A row's actions from a trackpad and a mouse, with no finger to drag them.
 *
 * Reported as wanting it "more sensitive to side scrolling" on both — touch was
 * fine. Two causes. A scroll's delta is in notches and was read as pixels, so a
 * mouse's click moved the row three pixels of an 88dp action. And a Mac's
 * trackpad arrives as a *pan*, which the row did not listen for at all.
 *
 * Two 88dp actions at this scene's density of 2 reveal at 352px, and settle open
 * once let go of with the first of them out in full, at 176px.
 */
class SwipeScrollTest {

    /**
     * Four clicks bring the first of two actions out in full — 96dp against its 88 —
     * which is where a released row opens.
     */
    @Test
    fun aFewNotchesOfSidewaysScrollOpenTheActions() {
        val (state, settled) = row { scene, at, _ ->
            repeat(4) {
                scene.scroll(at, Offset(1f, 0f))
                scene.frame()
            }
        }
        assertTrue(abs(state.offset) > 0f || settled == SwipeValue.End, "four notches did not move the row")
        assertEquals(
            SwipeValue.End, settled,
            "four clicks of a sideways wheel left the row at $settled. A notch is a " +
                "unit, not a pixel; at three pixels a notch this took dozens of clicks.",
        )
    }

    @Test
    fun aMostlyVerticalScrollIsTheListsAndLeavesTheRowAlone() {
        val (state, settled) = row { scene, at, _ ->
            repeat(6) {
                scene.scroll(at, Offset(0.2f, 1f))
                scene.frame()
            }
        }
        assertEquals(0f, state.offset, "a vertical scroll with a little drift moved the row")
        assertEquals(SwipeValue.Resting, settled)
    }

    @Test
    fun aTrackpadPanMovesTheRowWithTheFingersAndSettlesOnItsEnd() {
        var during = 0f
        var ran = 0
        val (_, settled) = row(onArchive = { ran++ }) { scene, at, state ->
            scene.pan(PointerEventType.PanStart, at)
            repeat(10) {
                scene.pan(PointerEventType.PanMove, at, Offset(-24f, 0f))
                scene.frame()
            }
            during = state.offset
            scene.pan(PointerEventType.PanEnd, at)
        }
        assertTrue(
            abs(during - -240f) < 2f,
            "ten pans of 24px left the row at $during, where the fingers had moved it 240",
        )
        assertEquals(SwipeValue.End, settled, "let go past half the actions, a pan left the row at $settled")
        assertEquals(0, ran, "a pan let go short of the point of no return ran the action")
    }

    /**
     * The full swipe, from a trackpad. Reported as *"i can't complete a swipe
     * actions full swipe with side scrolling in trackpad/mouse side-scrolling"*:
     * a scroll opened the actions and never ran one. Twenty pans of 24px carry
     * the row 480px, past the point of no return at 448 (the actions' 352 and
     * 48dp more).
     *
     * Against the arm above, which stops at 240 and must run nothing.
     */
    @Test
    fun aTrackpadPanCarriedPastThePointOfNoReturnRunsTheOutermostAction() {
        var ran = 0
        row(onArchive = { ran++ }, settleFrames = 120) { scene, at, _ ->
            scene.pan(PointerEventType.PanStart, at)
            repeat(20) {
                scene.pan(PointerEventType.PanMove, at, Offset(-24f, 0f))
                scene.frame()
            }
            scene.pan(PointerEventType.PanEnd, at)
        }
        assertEquals(1, ran, "a pan let go past the point of no return ran the outermost action $ran time(s)")
    }

    /**
     * Slow notches each move the row, and two open a single action, as
     * [SwipeActionsDefaults.ScrollStep] says.
     *
     * Found in the 0.5.0 review, 2026-10-21: the row settles once a scroll has
     * been quiet for [SwipeActionsDefaults.SettleAfterScroll], and that settle
     * marked what followed as a trackpad coasting, to be swallowed until a
     * quiet spell of a quarter of a second. So notches a fifth of a second
     * apart, a sideways tilt of the wheel or Shift and the wheel at an easy
     * pace, were each taken and ignored after the first, and the row never
     * opened. The clocks are kept together here, the frames' and the wall's
     * that the settle waits on, as a reader's hand keeps them.
     */
    @Test
    fun slowNotchesOfASidewaysWheelEachMoveTheRowAndTwoOpenOneAction() {
        var beforeSecond = 0f
        var afterSecond = 0f
        val (_, settled) = row(actions = 1) { scene, at, state ->
            scene.scroll(at, Offset(1f, 0f))
            scene.paced(200)
            beforeSecond = state.offset
            scene.scroll(at, Offset(1f, 0f))
            scene.advance()
            afterSecond = state.offset
        }
        // A notch is 24dp, 48px at this scene's density.
        assertTrue(
            abs(afterSecond) - abs(beforeSecond) > 40f,
            "a notch a fifth of a second after the last moved the row from $beforeSecond to $afterSecond: it was swallowed",
        )
        assertEquals(SwipeValue.End, settled, "two notches a fifth of a second apart left a row of one action at $settled")
    }

    /** And from a wheel: twelve notches of 24dp, 576px, then quiet. */
    @Test
    fun aWheelCarriedPastThePointOfNoReturnRunsTheOutermostAction() {
        var ran = 0
        row(onArchive = { ran++ }, settleFrames = 120) { scene, at, _ ->
            repeat(12) {
                scene.scroll(at, Offset(1f, 0f))
                scene.frame()
            }
        }
        assertEquals(1, ran, "a scroll that stopped past the point of no return ran the outermost action $ran time(s)")
    }

    /**
     * Swiped all the way across on a trackpad, the row is let go of as it
     * passes the point of no return, not once the trackpad's coasting has died
     * away, nor at the far edge, a whole row's width off, which it never reaches.
     * Reported as *"if you swipe it all the way across, it just stays there for
     * a bit before it decides to respond"*: each coasting event put the settle
     * off again, or cancelled the one under way. A second of coasting at a
     * frame apart runs the action within it, and once.
     */
    @Test
    fun aScrollCarriedPastThePointOfNoReturnRunsTheActionWhileTheTrackpadIsStillCoasting() {
        var ran = 0
        var whileCoasting = 0
        row(onArchive = { ran++ }, settleFrames = 120) { scene, at, _ ->
            repeat(12) {
                scene.scroll(at, Offset(1f, 0f))
                scene.frame()
            }
            repeat(60) {
                scene.scroll(at, Offset(0.05f, 0f))
                scene.frame()
            }
            whileCoasting = ran
        }
        assertEquals(1, whileCoasting, "carried past the point of no return, the row waited out the coasting")
        assertEquals(1, ran, "the coasting after a full swipe ran the action again")
    }

    /**
     * Once a scroll is the row's, the rest of it is too, up and down included,
     * until it is let go of. Reported as *"if you start side-scrolling the
     * action horizontally, it won't scroll vertically"*, wanted, because a
     * trackpad's fingers drift and the list scrolled out from under the row: an
     * event that was only up or down went on to it. After a quiet spell the
     * next scroll up or down is the list's again.
     */
    @Test
    fun aSidewaysScrollKeepsItsUpsAndDownsFromTheList() {
        lateinit var state: SwipeActionsState
        lateinit var list: ScrollState
        var bounds = Rect.Zero
        Scene(width = 700, height = 400) {
            state = rememberSwipeActionsState()
            list = rememberScrollState()
            Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(list)) {
                SwipeActions(
                    state = state,
                    modifier = Modifier.fillMaxWidth().height(72.dp).reportBounds { bounds = it },
                    end = listOf(
                        SwipeAction("Archive", Tabler.Outline.Archive, {}, Color.Blue),
                        SwipeAction("Delete", Tabler.Outline.Trash, {}, Color.Red),
                    ),
                ) {
                    Box(Modifier.fillMaxWidth().height(72.dp).background(Color.White))
                }
                Box(Modifier.fillMaxWidth().height(2000.dp))
            }
        }.use { scene ->
            scene.frames(3)
            val at = bounds.center
            repeat(2) {
                scene.scroll(at, Offset(1f, 0f))
                scene.frame()
            }
            repeat(4) {
                scene.scroll(at, Offset(0f, 1f))
                scene.frame()
                scene.scroll(at, Offset(0.5f, 0.5f))
                scene.frame()
            }
            val moved = state.offset
            assertEquals(0, list.value, "a sideways scroll's ups and downs scrolled the list out from under the row")
            assertTrue(abs(moved) > 0f, "the sideways scroll did not move the row")
            // Quiet, and the row let go of: the next scroll up or down is the list's.
            Thread.sleep(300)
            scene.frames(40)
            repeat(3) {
                scene.scroll(at, Offset(0f, 1f))
                scene.frame()
            }
            scene.frames(10)
            assertTrue(list.value > 0, "after a quiet spell a vertical scroll still did not reach the list")
        }
    }

    @Test
    fun aMostlyVerticalPanIsTheLists() {
        val (state, settled) = row { scene, at, _ ->
            scene.pan(PointerEventType.PanStart, at)
            repeat(10) {
                scene.pan(PointerEventType.PanMove, at, Offset(-3f, -20f))
                scene.frame()
            }
            scene.pan(PointerEventType.PanEnd, at)
        }
        assertEquals(0f, state.offset, "a vertical pan moved the row")
        assertEquals(SwipeValue.Resting, settled)
    }

    /**
     * A row with two end actions; runs [gesture] on its middle, waits out the
     * settle, and returns the state and where it came to rest.
     */
    private fun row(
        onArchive: () -> Unit = {},
        settleFrames: Int = 40,
        actions: Int = 2,
        gesture: (Scene, Offset, SwipeActionsState) -> Unit,
    ): Pair<SwipeActionsState, SwipeValue> {
        lateinit var state: SwipeActionsState
        var bounds = Rect.Zero
        Scene(width = 700, height = 200) {
            state = rememberSwipeActionsState()
            Box(Modifier.fillMaxSize().background(Color.White)) {
                SwipeActions(
                    state = state,
                    modifier = Modifier.fillMaxWidth().height(72.dp).reportBounds { bounds = it },
                    end = listOf(
                        SwipeAction("Archive", Tabler.Outline.Archive, onArchive, Color.Blue),
                        SwipeAction("Delete", Tabler.Outline.Trash, {}, Color.Red),
                    ).take(actions),
                ) {
                    Box(Modifier.fillMaxWidth().height(72.dp).background(Color.White))
                }
            }
        }.use { scene ->
            scene.frames(3)
            gesture(scene, bounds.center, state)
            // A scroll settles on a wall-clock timer; a pan on its end.
            Thread.sleep(300)
            scene.frames(settleFrames)
        }
        return state to state.currentValue
    }

    /**
     * [millis] of frames, each rendered no sooner than the wall clock reaches
     * it: the row's settle waits on the wall's clock, and its spring and the
     * events' times run on the frames'.
     */
    private fun Scene.paced(millis: Int) {
        val start = System.nanoTime()
        repeat(millis / 16) {
            val due = start + (it + 1) * 16_000_000L
            while (System.nanoTime() < due) Thread.sleep(1)
            advance()
        }
    }
}
