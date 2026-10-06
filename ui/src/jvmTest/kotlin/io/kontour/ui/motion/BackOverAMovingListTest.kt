package io.kontour.ui.motion

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.LocalTakesBackSwipe
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.RegisterBackHandler
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Back on iOS while a list is still moving from a throw.
 *
 * Reported: *"on ios, you can't swipe back if there is scrolling content that
 * is moving even just a little bit. not even from the very edge."* A list that
 * is still moving takes the press that lands on it — that is how a finger
 * catches a throw — and the swipe back read a press something had taken as a
 * control's, and left it alone. From the edge it gave up on the press; from the
 * content it gave up on the first move the list took.
 *
 * Each case runs with the library's own fling, `ScrollDefaults.flingBehavior()`
 * on the iOS feel, and with Compose's.
 */
@OptIn(ExperimentalTestApi::class)
class BackOverAMovingListTest {

    private object Info : NavigationEventInfo()

    private class Recorder : NavigationEventHandler<NavigationEventInfo>(Info, true) {
        var started = 0
        var completed = 0

        override fun onBackStarted(event: NavigationEvent) {
            started++
        }

        override fun onBackCompleted() {
            completed++
        }
    }

    private fun owner(dispatcher: NavigationEventDispatcher) = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = dispatcher
    }

    @Test
    fun fromTheEdgeAMovingListGoesBack() {
        for (feel in listOf(true, false)) {
            backOverAThrow(LayoutDirection.Ltr, fromEdge = true, iosFeel = feel)
            backOverAThrow(LayoutDirection.Rtl, fromEdge = true, iosFeel = feel)
        }
    }

    @Test
    fun fromTheContentAMovingListGoesBack() {
        for (feel in listOf(true, false)) {
            backOverAThrow(LayoutDirection.Ltr, fromEdge = false, iosFeel = feel)
            backOverAThrow(LayoutDirection.Rtl, fromEdge = false, iosFeel = feel)
        }
    }

    private fun backOverAThrow(direction: LayoutDirection, fromEdge: Boolean, iosFeel: Boolean) = runComposeUiTest {
        val recorder = Recorder()
        lateinit var list: LazyListState
        page(recorder, direction, iosFeel) {
            list = rememberLazyListState()
            Rows(list)
        }
        throwTheList(list)
        val what = "$direction, ${if (fromEdge) "from the edge" else "from the content"}, " +
            "${if (iosFeel) "the library's fling" else "Compose's fling"}"
        onNodeWithTag("list").performTouchInput {
            val ltr = direction == LayoutDirection.Ltr
            val from = when {
                fromEdge -> if (ltr) 4f else width - 4f
                else -> if (ltr) width * 0.3f else width * 0.7f
            }
            val to = if (ltr) width * 0.9f else width * 0.1f
            swipe(Offset(from, centerY), Offset(to, centerY), durationMillis = 200)
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(1, recorder.completed, "$what: a swipe back over a list still moving should go back")
    }

    /**
     * Catching the throw and scrolling the other way is still the list's: what
     * goes up and down over a moving list is never back.
     */
    @Test
    fun upAndDownOnAMovingListStillScrolls() = runComposeUiTest {
        val recorder = Recorder()
        lateinit var list: LazyListState
        page(recorder, LayoutDirection.Ltr, iosFeel = true) {
            list = rememberLazyListState()
            Rows(list)
        }
        throwTheList(list)
        val caught = list.firstVisibleItemIndex
        onNodeWithTag("list").performTouchInput {
            swipe(Offset(width * 0.5f, centerY - 200f), Offset(width * 0.5f + 30f, centerY + 200f), durationMillis = 300)
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(0, recorder.started, "a drag down a moving list is the list's")
        assertTrue(
            list.firstVisibleItemIndex < caught,
            "the list should have gone back up with the finger: it was at row $caught and is at ${list.firstVisibleItemIndex}",
        )
    }

    /**
     * A carousel still settling from a throw takes the next sideways swipe, as
     * it does at rest: its own axis is not the one a vertical list's is.
     */
    @Test
    fun aSettlingCarouselKeepsItsSwipe() = runComposeUiTest {
        val recorder = Recorder()
        lateinit var row: LazyListState
        page(recorder, LayoutDirection.Ltr, iosFeel = true) {
            row = rememberLazyListState()
            LazyRow(
                state = row,
                flingBehavior = ScrollDefaults.flingBehavior(),
                modifier = Modifier.fillMaxWidth().height(120.dp).testTag("row"),
            ) {
                items(100) { Text("Photo $it", Modifier.width(120.dp).height(120.dp)) }
            }
        }
        mainClock.autoAdvance = false
        onNodeWithTag("row").performTouchInput {
            swipe(Offset(width * 0.9f, centerY), Offset(width * 0.1f, centerY), durationMillis = 80)
        }
        repeat(3) { mainClock.advanceTimeByFrame() }
        assertTrue(row.isScrollInProgress, "the row should still be moving from its throw")
        onNodeWithTag("row").performTouchInput {
            swipe(Offset(width * 0.3f, centerY), Offset(width * 0.9f, centerY), durationMillis = 200)
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(0, recorder.started, "a sideways swipe on a settling carousel is the carousel's")
    }

    @Composable
    private fun Rows(list: LazyListState, fling: FlingBehavior = ScrollDefaults.flingBehavior()) {
        LazyColumn(state = list, flingBehavior = fling, modifier = Modifier.fillMaxSize().testTag("list")) {
            items(400) { Text("Departure $it", Modifier.fillMaxWidth().height(48.dp)) }
        }
    }

    /** Throws the list upwards and leaves it moving: the clock stops, a few frames in. */
    private fun ComposeUiTest.throwTheList(list: LazyListState) {
        mainClock.autoAdvance = false
        onNodeWithTag("list").performTouchInput {
            swipe(Offset(centerX, height * 0.85f), Offset(centerX, height * 0.15f), durationMillis = 80)
        }
        repeat(3) { mainClock.advanceTimeByFrame() }
        assertTrue(list.isScrollInProgress, "the list should still be moving from its throw")
    }

    private fun ComposeUiTest.page(
        recorder: Recorder,
        direction: LayoutDirection,
        iosFeel: Boolean,
        body: @Composable () -> Unit,
    ) {
        val platform = NavigationEventDispatcher()
        setContent {
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides owner(platform),
                LocalBackStyle provides BackStyle.Swipe,
                LocalTakesBackSwipe provides true,
                LocalLayoutDirection provides direction,
                LocalScrollFeel provides if (iosFeel) ScrollFeel() else null,
            ) {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        RegisterBackHandler(LocalNavigationEventDispatcherOwner.current, recorder)
                        Box(Modifier.fillMaxSize().contentSwipeBack()) { body() }
                    }
                }
            }
        }
        waitForIdle()
    }
}
