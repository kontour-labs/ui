package io.kontour.ui.motion

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.LocalTakesBackSwipe
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.RegisterBackHandler
import io.kontour.ui.sheet.ModalSideSheet
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The back swipe the library tracks itself: let go, it carries on at the
 * finger's speed with no pause, and it is decided the way UIKit decides it.
 */
@OptIn(ExperimentalTestApi::class)
class SwipeBackTest {

    private object Info : NavigationEventInfo()

    /** Whatever back reaches, written down. */
    private class Recorder : NavigationEventHandler<NavigationEventInfo>(Info, true) {
        val progress = mutableListOf<Float>()
        var started = 0
        var completed = 0
        var cancelled = 0
        var edge = -1

        override fun onBackStarted(event: NavigationEvent) {
            started++
            edge = event.swipeEdge
        }

        override fun onBackProgressed(event: NavigationEvent) {
            progress += event.progress
        }

        override fun onBackCompleted() {
            completed++
        }

        override fun onBackCancelled() {
            cancelled++
        }
    }

    /** Whether the dispatcher it is added to has anything that would answer back. */
    private class Probe : NavigationEventInput() {
        var answered = false

        override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
            answered = hasEnabledHandlers
        }
    }

    /** Frames of 16ms, counted. */
    private class Frames : MonotonicFrameClock {
        var count = 0
        private var time = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            count++
            time += 16_000_000L
            return onFrame(time)
        }
    }

    private fun owner(dispatcher: NavigationEventDispatcher) = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = dispatcher
    }

    /** A gesture dragged to [at] and let go at [velocity], in progress per second. */
    private fun release(at: Float, commit: Boolean, velocity: Float): Pair<Recorder, Frames> {
        val dispatcher = NavigationEventDispatcher()
        val recorder = Recorder().also { dispatcher.addHandler(it) }
        val input = SwipeBackInput().also { dispatcher.addInput(it) }
        input.start(NavigationEvent.EDGE_LEFT, Offset.Zero)
        input.move(at, Offset.Zero)
        recorder.progress.clear()
        val frames = Frames()
        runBlocking(frames) { input.release(commit, velocity) }
        return recorder to frames
    }

    @Test
    fun aFlickFinishesSoonerThanALetGo() {
        val (still, stillFrames) = release(at = 0.6f, commit = true, velocity = 0f)
        val (flicked, flickFrames) = release(at = 0.6f, commit = true, velocity = 5f)
        assertEquals(1, still.completed)
        assertEquals(1, flicked.completed)
        assertTrue(
            flickFrames.count < stillFrames.count,
            "a flick took ${flickFrames.count} frames to finish, a still release ${stillFrames.count}",
        )
    }

    /**
     * The "blip": nothing may stand still between the finger lifting and the
     * page arriving. Every frame after the release is further on than the one
     * before, from the first.
     */
    @Test
    fun everyFrameAfterTheReleaseMovesOn() {
        for (velocity in listOf(0f, 2f, 6f)) {
            val (recorder, _) = release(at = 0.55f, commit = true, velocity = velocity)
            val seen = recorder.progress
            assertTrue(seen.first() > 0.55f, "the first frame after letting go at $velocity/s did not move: ${seen.first()}")
            seen.zipWithNext().forEachIndexed { frame, (a, b) ->
                assertTrue(b > a, "frame ${frame + 1} after letting go at $velocity/s stood still at $a")
            }
            assertTrue(seen.last() > 0.99f, "completed at ${seen.last()}, short of where the pop leaves the page")
            assertEquals(1, recorder.completed)
        }
    }

    @Test
    fun abandonedItGoesHomeTheSameWay() {
        val (recorder, _) = release(at = 0.4f, commit = false, velocity = -3f)
        assertEquals(0, recorder.completed)
        assertEquals(1, recorder.cancelled)
        recorder.progress.zipWithNext().forEach { (a, b) -> assertTrue(b < a, "stood still at $a on the way home") }
        assertTrue(recorder.progress.last() < 0.01f)
    }

    // --- decided as UIKit decides it ---------------------------------------------

    @Composable
    private fun Page(owner: NavigationEventDispatcherOwner) {
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides owner,
            LocalBackStyle provides BackStyle.Swipe,
        ) {
            KontourTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().contentSwipeBack().testTag("page"))
            }
        }
    }

    @Test
    fun aShortFlickTowardsTheTrailingEdgeGoesBack() = runComposeUiTest {
        val dispatcher = NavigationEventDispatcher()
        val recorder = Recorder().also { dispatcher.addHandler(it) }
        setContent { Page(owner(dispatcher)) }
        onNodeWithTag("page").performTouchInput {
            swipe(Offset(width * 0.3f, centerY), Offset(width * 0.45f, centerY), durationMillis = 60)
        }
        waitForIdle()
        assertEquals(1, recorder.completed, "a quick flick short of half did not go back")
    }

    @Test
    fun pastHalfAndFlickedBackStays() = runComposeUiTest {
        val dispatcher = NavigationEventDispatcher()
        val recorder = Recorder().also { dispatcher.addHandler(it) }
        setContent { Page(owner(dispatcher)) }
        onNodeWithTag("page").performTouchInput {
            down(Offset(width * 0.1f, centerY))
            for (step in 1..20) {
                advanceEventTime(15)
                moveTo(Offset(width * (0.1f + 0.7f * step / 20), centerY))
            }
            for (step in 1..4) {
                advanceEventTime(10)
                moveTo(Offset(width * (0.8f - 0.04f * step), centerY))
            }
            up()
        }
        waitForIdle()
        assertEquals(0, recorder.completed, "a flick back past half went back anyway")
        assertEquals(1, recorder.cancelled)
    }

    // --- the host that takes the swipe over, as on iOS ------------------------------

    private fun ComposeUiTest.bounds(tag: String): Rect {
        waitForIdle()
        return onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    }

    @Composable
    private fun Detached(platform: NavigationEventDispatcher, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides owner(platform),
            LocalBackStyle provides BackStyle.Swipe,
            LocalTakesBackSwipe provides true,
        ) {
            KontourTheme(darkTheme = false) {
                OverlayHost(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    @Test
    fun aDetachedHostGoesBackFromItsEdgeAndLeavesThePlatformNothing() = runComposeUiTest {
        val platform = NavigationEventDispatcher()
        val probe = Probe().also { platform.addInput(it) }
        val recorder = Recorder()
        setContent {
            Detached(platform) {
                RegisterBackHandler(LocalNavigationEventDispatcherOwner.current, recorder)
                // Something against the edge that scrolls sideways, as UIKit's
                // edge pan beats.
                Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).testTag("page")) {
                    repeat(6) { Box(Modifier.width(200.dp)) { Text("12:0$it") } }
                }
            }
        }
        waitForIdle()
        assertFalse(probe.answered, "the platform's dispatcher had something to go back to, so its edge pan is armed")

        onRoot().performTouchInput {
            swipe(Offset(width * 0.5f, centerY), Offset(width * 0.95f, centerY), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(0, recorder.started, "a pan from the middle was taken as the edge's")

        onRoot().performTouchInput {
            swipe(Offset(4f, centerY), Offset(width * 0.8f, centerY), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(NavigationEvent.EDGE_LEFT, recorder.edge)
        assertEquals(1, recorder.completed, "the edge pan did not go back")
    }

    // --- side sheets, swiped away towards their own edge -----------------------------

    @Composable
    private fun Filters(side: SheetSide, visible: Boolean, onDismissRequest: () -> Unit) {
        ModalSideSheet(visible = visible, onDismissRequest = onDismissRequest, side = side) {
            Text("Filters", Modifier.fillMaxSize().testTag("panel"))
        }
    }

    @Test
    fun aSideSheetFollowsADragTowardsItsEdgeAndClosesOnce() = runComposeUiTest {
        val platform = NavigationEventDispatcher()
        var visible by mutableStateOf(true)
        var dismissed = 0
        setContent {
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides owner(platform),
                LocalBackStyle provides BackStyle.Swipe,
            ) {
                KontourTheme(darkTheme = false) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Filters(SheetSide.End, visible) { dismissed++; visible = false }
                    }
                }
            }
        }
        val rest = bounds("panel")

        // The other way: nothing.
        onNodeWithTag("panel", useUnmergedTree = true).performTouchInput {
            swipe(Offset(width * 0.8f, centerY), Offset(width * 0.1f, centerY), durationMillis = 300)
        }
        assertEquals(rest, bounds("panel"), "a drag away from the sheet's edge moved it")
        assertEquals(0, dismissed)

        // Towards its edge: it follows the finger, one to one past the slop.
        onNodeWithTag("panel", useUnmergedTree = true).performTouchInput {
            down(Offset(width * 0.2f, centerY))
            for (step in 1..10) {
                advanceEventTime(20)
                moveTo(Offset(width * 0.2f + 12f * step, centerY))
            }
        }
        val mid = bounds("panel")
        assertTrue(mid.left - rest.left in 80f..120f, "the sheet moved ${mid.left - rest.left}px for a 120px drag")

        // On past half, and let go: closed, once.
        onNodeWithTag("panel", useUnmergedTree = true).performTouchInput {
            for (step in 1..10) {
                advanceEventTime(20)
                moveTo(Offset(width * 0.2f + 120f + width * 0.05f * step, centerY))
            }
            up()
        }
        waitForIdle()
        assertEquals(1, dismissed, "a drag past half did not close the sheet, or closed it twice")
    }

    @Test
    fun theEdgeSwipeClosesASheetOnTheRightAndLeavesADrawerOnTheLeft() = runComposeUiTest {
        val platform = NavigationEventDispatcher()
        var side by mutableStateOf(SheetSide.Start)
        var visible by mutableStateOf(true)
        var dismissed = 0
        setContent {
            Detached(platform) {
                Box(Modifier.fillMaxSize())
                Filters(side, visible) { dismissed++; visible = false }
            }
        }
        val rest = bounds("panel")
        onRoot().performTouchInput {
            swipe(Offset(4f, centerY), Offset(width * 0.9f, centerY), durationMillis = 300)
        }
        assertEquals(rest, bounds("panel"), "the edge swipe moved a drawer on the left, against its own way out")
        assertEquals(0, dismissed)

        side = SheetSide.End
        waitForIdle()
        onRoot().performTouchInput {
            swipe(Offset(4f, centerY), Offset(width * 0.9f, centerY), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(1, dismissed, "the edge swipe did not close the sheet on the right")
    }
}
