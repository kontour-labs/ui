package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.PullToRefreshState
import io.kontour.ui.components.list.rememberPullToRefreshState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three things a pull has to get right that a phone never showed.
 *
 * The gesture was nested-scroll and nothing else. That is the whole story for a
 * finger — a list dispatches what it cannot use and the indicator picks it up —
 * and it leaves two holes that only appear away from a touchscreen, plus one
 * that was there all along and only shows on the way back.
 */
class PullToRefreshGestureTest {

    /** 23c — a `LazyColumn` does not drag with a mouse, so nothing happened. */
    @Test
    fun aMouseDragAtTheTopRefreshes() {
        var refreshes = 0
        var bounds = Rect.Zero

        Scene(width = 400, height = 600) {
            Pull(onRefresh = { refreshes++ }, report = { bounds = it })
        }.use { scene ->
            scene.frames(4)
            assertTrue(bounds.height > 0f, "the container never reported a size")
            val from = Offset(bounds.center.x, bounds.top + 40f)
            scene.drag(
                from = from,
                to = Offset(from.x, from.y + PastThreshold),
                steps = 24,
                pointer = PointerType.Mouse,
            )
            scene.frames(20)
        }

        assertEquals(
            1,
            refreshes,
            "dragging the list down ${PastThreshold.toInt()}px with a mouse refreshed " +
                "nothing. Nested scroll is the only way in, and a desktop list does " +
                "not drag — so on desktop and the web the gesture did not exist",
        )
    }

    /**
     * 23d — and a wheel at the top is not a pull.
     *
     * ### It holds for a reason this test cannot see, so it proves the wheel arrives
     *
     * Removing the `UserInput` gate in the connection does not make this fail: at
     * the top of a list a wheel notch is simply declined, and Compose does not
     * hand an unconsumed *wheel* delta to nested scroll the way it hands on a
     * drag. So the guard in the component is belt and braces over a platform
     * behaviour, and an assertion that the indicator stayed at zero would
     * otherwise be indistinguishable from the wheel never having been delivered.
     *
     * Hence the first half: the list is scrolled **down** by wheel, which it can
     * take, and that has to move before the second half means anything.
     */
    @Test
    fun aWheelAtTheTopPullsNothing() {
        var refreshes = 0
        var bounds = Rect.Zero
        var reached = 0f
        var scrolledDown = 0
        var state: PullToRefreshState? = null
        var listState: LazyListState? = null

        Scene(width = 400, height = 600) {
            state = Pull(
                onRefresh = { refreshes++ },
                report = { bounds = it },
                list = { listState = it },
            )
        }.use { scene ->
            scene.frames(4)
            val pull = requireNotNull(state)
            val list = requireNotNull(listState)

            // Down the list, which the wheel can do — this is the instrument
            // check, not the claim.
            repeat(8) {
                scene.scroll(bounds.center, Offset(0f, 1f))
                scene.frame()
            }
            scene.renderUntil(timeoutMillis = 3_000) { !list.isScrollInProgress }
            scrolledDown = list.firstVisibleItemIndex * 10_000 +
                list.firstVisibleItemScrollOffset

            // ...and back up, past the top and then some.
            repeat(24) {
                scene.scroll(bounds.center, Offset(0f, -1f))
                scene.frame()
                reached = maxOf(reached, pull.progress)
            }
            scene.renderUntil(timeoutMillis = 3_000) { !list.isScrollInProgress }
            scene.frames(20)
            reached = maxOf(reached, pull.progress)
        }

        assertTrue(
            scrolledDown > 0,
            "a wheel notch did not move the list at all, so nothing below this line " +
                "would have been a measurement of anything",
        )
        assertEquals(
            0f,
            reached,
            "a mouse wheel at the top of the list pulled the indicator to $reached. " +
                "Spinning a wheel is a request to read what is above, and there is " +
                "nothing above; a pull is a sustained gesture with a pointer down",
        )
        assertEquals(0, refreshes, "a wheel refreshed the list")
    }

    /**
     * A list moved by anything but a scroll gesture is still where it was moved
     * to, and a mouse drag there is not a pull.
     *
     * The list is sent halfway down by its state, as a scrollbar's thumb, a
     * key, `animateScrollToItem` or a restored position sends it, none of which
     * passes through nested scroll. The pull used to know where the list was
     * only from what it saw of the scrolls going past, so it still believed the
     * list was at its top, and a mouse drag halfway down it pulled.
     */
    @Test
    fun aMouseDragHalfwayDownAListMovedByItsStateIsNotAPull() {
        var refreshes = 0
        var bounds = Rect.Zero
        var reached = 0f
        var state: PullToRefreshState? = null
        var listState: LazyListState? = null

        Scene(width = 400, height = 600) {
            state = Pull(onRefresh = { refreshes++ }, report = { bounds = it }, list = { listState = it })
        }.use { scene ->
            scene.frames(4)
            val pull = requireNotNull(state)
            val list = requireNotNull(listState)
            onComposeUiThread { list.requestScrollToItem(20) }
            scene.frames(4)
            assertEquals(20, list.firstVisibleItemIndex, "the list never moved, so the drag below proves nothing")

            val from = Offset(bounds.center.x, bounds.top + 40f)
            scene.drag(
                from = from,
                to = Offset(from.x, from.y + PastThreshold),
                steps = 24,
                release = false,
                pointer = PointerType.Mouse,
            ) { _, _ -> reached = maxOf(reached, pull.progress) }
            scene.release(Offset(from.x, from.y + PastThreshold), PointerType.Mouse)
            scene.frames(20)
        }

        assertEquals(
            0f,
            reached,
            "a mouse drag halfway down a list moved there by its state pulled the " +
                "indicator to $reached of its threshold. The list was not at its top",
        )
        assertEquals(0, refreshes, "a mouse drag halfway down the list refreshed it")
    }

    /**
     * And the other way: a list scrolled down by the wheel and brought back to
     * its top by its state is at its top, and a mouse drag there pulls.
     *
     * The wheel going down told the pull the list had left its top; nothing
     * that goes past the pull says it came back.
     */
    @Test
    fun aMouseDragAtTheTopOfAListBroughtBackByItsStatePulls() {
        var refreshes = 0
        var bounds = Rect.Zero
        var listState: LazyListState? = null

        Scene(width = 400, height = 600) {
            Pull(onRefresh = { refreshes++ }, report = { bounds = it }, list = { listState = it })
        }.use { scene ->
            scene.frames(4)
            val list = requireNotNull(listState)
            repeat(8) {
                scene.scroll(bounds.center, Offset(0f, 1f))
                scene.frame()
            }
            scene.renderUntil(timeoutMillis = 3_000) { !list.isScrollInProgress }
            assertTrue(list.firstVisibleItemIndex > 0, "the wheel never moved the list down")
            onComposeUiThread { list.requestScrollToItem(0) }
            scene.frames(4)
            assertEquals(0, list.firstVisibleItemIndex, "the list never came back to its top")

            val from = Offset(bounds.center.x, bounds.top + 40f)
            scene.drag(
                from = from,
                to = Offset(from.x, from.y + PastThreshold),
                steps = 24,
                pointer = PointerType.Mouse,
            )
            scene.frames(20)
        }

        assertEquals(
            1,
            refreshes,
            "a mouse drag at the top of a list brought back there by its state " +
                "refreshed nothing: the pull still believed the list was down where " +
                "the wheel had left it",
        )
    }

    /** 23e — and the list holds still while the indicator is put away. */
    @Test
    fun theListHoldsStillWhileTheIndicatorComesBack() {
        var bounds = Rect.Zero
        var state: PullToRefreshState? = null
        var listState: LazyListState? = null
        var movedWhileOut = 0
        var sawIndicatorOut = false

        Scene(width = 400, height = 600) {
            state = Pull(onRefresh = {}, report = { bounds = it }, list = { listState = it })
        }.use { scene ->
            scene.frames(4)
            val pull = requireNotNull(state)
            val list = requireNotNull(listState)

            // Out, with a finger — this half is the path a phone uses.
            val from = Offset(bounds.center.x, bounds.top + 40f)
            scene.press(from)
            repeat(Steps) { step ->
                scene.move(Offset(from.x, from.y + PastThreshold * (step + 1) / Steps))
                scene.frame()
            }
            assertTrue(pull.willRefresh, "the pull never reached its threshold")

            // ...and slowly back, without letting go.
            repeat(Steps) { step ->
                scene.move(
                    Offset(from.x, from.y + PastThreshold * (Steps - step - 1) / Steps)
                )
                scene.frame()
                if (pull.progress > 0f) {
                    sawIndicatorOut = true
                    movedWhileOut = maxOf(
                        movedWhileOut,
                        list.firstVisibleItemIndex * 10_000 +
                            list.firstVisibleItemScrollOffset,
                    )
                }
            }
            scene.release(from)
        }

        assertTrue(sawIndicatorOut, "the indicator was never out to watch come back")
        assertEquals(
            0,
            movedWhileOut,
            "the list scrolled while the indicator was still on its way back in. " +
                "Past the threshold the pull resists, and applying that resistance to " +
                "a returning finger as well hands the rest of the movement to the " +
                "list — so putting the indicator away takes the page with it",
        )
    }
}

/** A pull-to-refresh over a long list, with the pieces a test needs to see. */
@androidx.compose.runtime.Composable
private fun Pull(
    onRefresh: () -> Unit,
    report: (Rect) -> Unit,
    list: (LazyListState) -> Unit = {},
    rowTop: (Float) -> Unit = {},
): PullToRefreshState {
    var refreshing by mutableStateOf(false)
    val pull = rememberPullToRefreshState()
    val rows = rememberLazyListState()
    list(rows)
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = { refreshing = false; onRefresh() },
        scrollState = rows,
        state = pull,
        modifier = Modifier.fillMaxSize().reportBounds(report),
    ) {
        LazyColumn(state = rows, modifier = Modifier.fillMaxSize().background(Color.White)) {
            items(40) { index ->
                Box(
                    Modifier
                        .then(if (index == 0) Modifier.reportBounds { rowTop(it.top) } else Modifier)
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(if (index % 2 == 0) Color.LightGray else Color.White)
                )
            }
        }
    }
    return pull
}

/** Comfortably past the 80dp threshold at this scene's density, plus slop. */
private const val PastThreshold = 260f
private const val Steps = 24

/**
 * The content comes down with the finger, not after it.
 *
 * The content's offset was an `animateFloatAsState` whose target was the pull,
 * read in composition: every move recomposed the container and restarted a
 * spring towards where the finger now was, so the list trailed the finger by a
 * few frames for the whole pull, and caught up only when it stopped. A spring is
 * for the way home after the finger lets go.
 */
class PullToRefreshTrackingTest {

    @Test
    fun theContentFollowsTheFingerFrameForFrame() {
        var bounds = Rect.Zero
        var listTop = 0f
        var furthest = 0f
        var caughtUp = 0f

        Scene(width = 400, height = 600) {
            Box(Modifier.fillMaxSize()) {
                Pull(onRefresh = {}, report = { bounds = it }, rowTop = { listTop = it })
            }
        }.use { scene ->
            scene.frames(4)
            val start = listTop
            val from = Offset(bounds.center.x, bounds.top + 40f)
            scene.press(from)
            scene.frame()
            repeat(20) { step ->
                scene.move(Offset(from.x, from.y + (step + 1) * 6f))
                scene.frame()
            }
            // The finger stops. Content that follows it has already stopped too;
            // content chasing it on an animation goes on arriving.
            val atStop = listTop
            furthest = atStop - start
            scene.frames(20)
            caughtUp = listTop - atStop
            scene.release(Offset(from.x, from.y + 120f))
            scene.frames(30)
        }

        assertTrue(furthest > 20f, "the content never came down, so this measured nothing (it moved ${furthest}px)")
        assertTrue(
            kotlin.math.abs(caughtUp) < 1f,
            "after the finger stopped, the content went on moving ${caughtUp}px: it was " +
                "chasing the finger on an animation instead of following it",
        )
    }
}
