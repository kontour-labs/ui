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
): PullToRefreshState {
    var refreshing by mutableStateOf(false)
    val pull = rememberPullToRefreshState()
    val rows = rememberLazyListState()
    list(rows)
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = { refreshing = false; onRefresh() },
        state = pull,
        modifier = Modifier.fillMaxSize().reportBounds(report),
    ) {
        LazyColumn(state = rows, modifier = Modifier.fillMaxSize().background(Color.White)) {
            items(40) { index ->
                Box(
                    Modifier
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
