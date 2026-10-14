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

    /** Frames of 16ms, or of [period], counted, the first one [period] after [from]. */
    private class Frames(private val period: Long = 16_000_000L, from: Long = 0L) : MonotonicFrameClock {
        var count = 0
        private var time = from

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            count++
            time += period
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

    /**
     * At 120Hz the first frame after letting go moves as far as the frames after
     * it, and not twice as far.
     *
     * A release is timed from the moment the finger lifted, so that its first
     * frame is not spent standing where it started. That moment was taken as one
     * frame at sixty a second before the first frame the release saw — right at
     * 60Hz, and on a ProMotion iPhone or a 120Hz Android twice the real interval,
     * so the page jumped two frames' worth on the first and then went on at one.
     * The frames the gesture was drawn on are watched now, and the release is
     * timed from the last of them.
     */
    @Test
    fun theFirstFrameAfterLettingGoAt120HzIsOneFramesWorth() {
        val period = 8_333_333L
        val dispatcher = NavigationEventDispatcher()
        val recorder = Recorder().also { dispatcher.addHandler(it) }
        val input = SwipeBackInput().also { dispatcher.addInput(it) }
        input.start(NavigationEvent.EDGE_LEFT, Offset.Zero)
        // The gesture, drawn at 120Hz: its frames are what the release follows on from.
        var lastFrame = 0L
        repeat(10) {
            lastFrame += period
            input.sawFrame(lastFrame)
            input.move(0.3f + it * 0.01f, Offset.Zero)
        }
        recorder.progress.clear()

        runBlocking(Frames(period, from = lastFrame)) { input.release(commit = true, velocity = 4f) }

        val seen = recorder.progress
        val first = seen[0] - 0.39f
        val second = seen[1] - seen[0]
        assertTrue(
            first < second * 1.25f,
            "the first frame after letting go at 120Hz moved $first and the next $second: " +
                "the first was timed as if a 60Hz frame had passed since the last one drawn",
        )
        assertTrue(first > second * 0.75f, "the first frame after letting go moved $first against $second after it")
        assertEquals(1, recorder.completed)
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

    // --- as little effort as UIKit asks for ----------------------------------------

    /**
     * Where a release comes to rest decides it, as UIKit's does: half a
     * second's coast at the speed it was let go at, past half the width.
     */
    @Test
    fun aReleaseGoesBackWhenItWouldComeToRestPastHalf() {
        // A moderate swipe a third of the way across: it would coast past half.
        assertTrue(releaseCommits(progress = 0.3f, velocity = 0.6f))
        // The same place, let go slowly: it would not.
        assertFalse(releaseCommits(progress = 0.3f, velocity = 0.2f))
        // Past half and let go still.
        assertTrue(releaseCommits(progress = 0.55f, velocity = 0f))
        // Past half and flicked back: it coasts home.
        assertFalse(releaseCommits(progress = 0.6f, velocity = -0.5f))
    }

    /**
     * *"Sometimes swiping back on iOS feels like it takes a bit too much effort
     * compared to native apps."* A swipe at an ordinary pace a third of the way
     * across — about 600dp a second here — sprang back: under the old rule it
     * had to pass half the width or go faster than 1000dp a second.
     */
    @Test
    fun aSwipeAtAnOrdinaryPaceAThirdOfTheWayAcrossGoesBack() = runComposeUiTest {
        val dispatcher = NavigationEventDispatcher()
        val recorder = Recorder().also { dispatcher.addHandler(it) }
        setContent { Page(owner(dispatcher)) }
        onNodeWithTag("page").performTouchInput {
            down(Offset(width * 0.1f, centerY))
            for (step in 1..20) {
                // 30ms a step: `moveTo` adds its own 16 to this. Further apart
                // than 40ms, Compose's velocity tracker takes the finger to have
                // stopped, which no screen reporting at 60Hz or more would show.
                advanceEventTime(14)
                moveTo(Offset(width * (0.1f + 0.35f * step / 20), centerY))
            }
            up()
        }
        waitForIdle()
        assertEquals(1, recorder.completed, "a swipe a third of the way across at an ordinary pace sprang back")
    }

    /**
     * A thumb arcs. A swipe 30° off level went nowhere on the page, which asked
     * for twice as much sideways as up or down; at the edge it was 45°.
     */
    @Test
    fun aSwipeOnTheSlantStillGoesBack() = runComposeUiTest {
        val dispatcher = NavigationEventDispatcher()
        val recorder = Recorder().also { dispatcher.addHandler(it) }
        setContent { Page(owner(dispatcher)) }
        onNodeWithTag("page").performTouchInput {
            val from = Offset(width * 0.1f, centerY)
            down(from)
            for (step in 1..20) {
                advanceEventTime(20)
                val dx = width * 0.5f * step / 20
                moveTo(from + Offset(dx, -dx * 0.577f))
            }
            up()
        }
        waitForIdle()
        assertEquals(1, recorder.started, "a swipe 30° off level never became back")
        assertEquals(1, recorder.completed)
    }

    /**
     * The page is under the finger: as far across as the finger has gone since
     * it went down, not since the pan was claimed, which left it a touch slop
     * behind the whole way.
     */
    @Test
    fun thePageIsWhereTheFingerIs() = runComposeUiTest {
        val dispatcher = NavigationEventDispatcher()
        val recorder = Recorder().also { dispatcher.addHandler(it) }
        setContent { Page(owner(dispatcher)) }
        var span = 0f
        onNodeWithTag("page").performTouchInput {
            span = width.toFloat()
            down(Offset(width * 0.1f, centerY))
            for (step in 1..20) {
                advanceEventTime(40)
                moveTo(Offset(width * (0.1f + 0.2f * step / 20), centerY))
            }
        }
        waitForIdle()
        assertEquals(0.2f, recorder.progress.last(), 1f / span, "the page is not where the finger is")
        onNodeWithTag("page").performTouchInput { up() }
        waitForIdle()
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
    fun aSideSheetFollowsADragTowardsItsEdgeAndClosesOnce() = runComposeUiTest { dragASideSheet(BackStyle.Swipe) }

    /** Android's sheet takes the same drag: it is the sheet's, not the platform's back. */
    @Test
    fun onAndroidASideSheetFollowsTheSameDrag() = runComposeUiTest { dragASideSheet(BackStyle.Predictive) }

    /**
     * Reduced motion fades a sheet in and out where it rests, but a drag is never
     * touched: the panel still moves with the finger, and closes once.
     */
    @Test
    fun underReducedMotionADragStillMovesTheSheet() = runComposeUiTest {
        dragASideSheet(BackStyle.Swipe, reduceMotion = true)
    }

    private fun ComposeUiTest.dragASideSheet(style: BackStyle, reduceMotion: Boolean = false) {
        val platform = NavigationEventDispatcher()
        var visible by mutableStateOf(true)
        var dismissed = 0
        setContent {
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides owner(platform),
                LocalBackStyle provides style,
            ) {
                KontourTheme(darkTheme = false, reduceMotion = reduceMotion) {
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

    /**
     * A stack inside the sheet with somewhere to go back to takes the
     * platform's back gesture, but not a drag on the sheet: that closes the
     * sheet the finger is on, and the stack keeps its page.
     */
    @Test
    fun aDragClosesTheSheetAndNotTheStackInsideIt() = runComposeUiTest {
        val platform = NavigationEventDispatcher()
        val stack = Recorder()
        var visible by mutableStateOf(true)
        var dismissed = 0
        setContent {
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides owner(platform),
                LocalBackStyle provides BackStyle.Predictive,
            ) {
                KontourTheme(darkTheme = false) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        ModalSideSheet(visible = visible, onDismissRequest = { dismissed++; visible = false }) {
                            RegisterBackHandler(LocalNavigationEventDispatcherOwner.current, stack)
                            Text("Route 950", Modifier.fillMaxSize().testTag("panel"))
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("panel", useUnmergedTree = true).performTouchInput {
            swipe(Offset(width * 0.1f, centerY), Offset(width * 0.9f, centerY), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(1, dismissed, "the drag did not close the sheet")
        assertEquals(0, stack.started, "the drag reached the stack inside the sheet")
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
