package io.kontour.ui.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.LocalRelaysSystemBack
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.RegisterBackHandler
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

/**
 * Android's back gesture, relayed so that it finishes at the finger's speed.
 *
 * Reported from a phone: *"on android, it doesn't take the velocity of the
 * predictive back swipe into account when you go back."* A released gesture was
 * finished by the page transition's own fixed pace, so a flick slowed down the
 * moment it was let go. The outermost host now takes the platform's back through
 * a relay and carries a released gesture the rest of the way itself.
 *
 * The platform is played by a [DirectNavigationEventInput] on a dispatcher that
 * stands for the activity's, with each event's frame time given, as Android's
 * are.
 */
@OptIn(ExperimentalTestApi::class)
class SystemBackRelayTest {

    private object Info : NavigationEventInfo()

    /** Whatever back reaches the page, written down, with the frame each arrived on. */
    private class Recorder : NavigationEventHandler<NavigationEventInfo>(Info, true) {
        val progress = mutableListOf<Float>()
        var started = 0
        var completed = 0
        var cancelled = 0

        override fun onBackStarted(event: NavigationEvent) {
            started++
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

    /** Whether the platform's dispatcher has anything that would answer back. */
    private class Probe : NavigationEventInput() {
        var answered = false

        override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
            answered = hasEnabledHandlers
        }
    }

    private class Rig(val platform: DirectNavigationEventInput, val probe: Probe, val recorder: Recorder)

    private fun ComposeUiTest.rig(listening: () -> Boolean = { true }): Rig {
        val dispatcher = NavigationEventDispatcher()
        val platform = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val probe = Probe().also { dispatcher.addInput(it) }
        val recorder = Recorder()
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides owner,
                LocalBackStyle provides BackStyle.Predictive,
                LocalRelaysSystemBack provides true,
            ) {
                KontourTheme(darkTheme = false) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize())
                        if (listening()) RegisterBackHandler(LocalNavigationEventDispatcherOwner.current, recorder)
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        return Rig(platform, probe, recorder)
    }

    /** A gesture from the left edge to [to], its events [gapMillis] apart. */
    private fun Rig.drag(to: Float, steps: Int, gapMillis: Long) {
        platform.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f, 1_000L))
        for (step in 1..steps) {
            platform.backProgressed(
                NavigationEvent(NavigationEvent.EDGE_LEFT, to * step / steps, 0f, 0f, 1_000L + gapMillis * step),
            )
        }
    }

    /** Frames until the page hears the gesture finish, at most [limit]. */
    private fun ComposeUiTest.framesUntil(limit: Int = 120, done: () -> Boolean): Int {
        var frames = 0
        while (!done() && frames < limit) {
            mainClock.advanceTimeByFrame()
            frames++
        }
        return frames
    }

    @Test
    fun atRestThePlatformKeepsItsBack() = runComposeUiTest {
        var listening by mutableStateOf(false)
        val rig = rig { listening }
        assertFalse(rig.probe.answered, "with nothing to go back to, the relay still took the platform's back — no way home")
        listening = true
        mainClock.advanceTimeByFrame()
        assertTrue(rig.probe.answered, "with a page to go back to, the relay did not take the platform's back")
    }

    @Test
    fun aFlickFinishesSoonerThanASlowDrag() = runComposeUiTest {
        val rig = rig()
        // To the same place, as a flick: four events 16ms apart.
        rig.drag(to = 0.3f, steps = 4, gapMillis = 16)
        rig.platform.backCompleted()
        val flick = framesUntil { rig.recorder.completed > 0 }

        rig.drag(to = 0.3f, steps = 4, gapMillis = 200)
        rig.platform.backCompleted()
        val drag = framesUntil { rig.recorder.completed > 1 }

        assertEquals(2, rig.recorder.completed)
        assertTrue(flick < drag, "a flick released at 0.3 took $flick frames to finish and a slow drag $drag — the speed is not carried")
        // The transition's own pace was the rest of the way over 350ms: 15 frames
        // from 0.3. A flick has to beat it.
        assertTrue(flick < 12, "a flick released at 0.3 took $flick frames to finish")
    }

    /**
     * A quick swipe let go early leaves at speed, even when the platform's
     * events were too few or too far apart for the last moment's speed to be
     * measured.
     *
     * Reported from a phone: *"if i swipe back quickly using the side-gesture
     * back button (and let go quickly), then sometimes it can feel really
     * sluggish"*. The speed is read from the last 40ms of events, and a quick
     * swipe on Android can leave fewer than two in it — one slow frame as the
     * page behind is first drawn is enough. Measured as nothing, the release
     * left from a standstill and eased in: under a hundredth of the way on its
     * first frame, the page stopping on the finger's lift.
     *
     * Here the swipe reaches 0.12 in 60ms, as two events: too far apart for the
     * last moment's speed, plain as a quick swipe over its whole length.
     */
    @Test
    fun aQuickSwipeLetGoEarlyLeavesAtSpeed() = runComposeUiTest {
        val rig = rig()
        rig.platform.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f, 1_000L))
        rig.platform.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, 0.12f, 0f, 0f, 1_060L))
        rig.recorder.progress.clear()
        rig.platform.backCompleted()
        mainClock.advanceTimeByFrame()
        val first = rig.recorder.progress.firstOrNull()
        assertTrue(first != null, "nothing moved on the first frame after the finger lifted")
        // Half of what the relay's least pace covers in a frame.
        assertTrue(
            first - 0.12f >= LeastFirstFrame,
            "the first frame after letting go at 0.12 moved the page to $first, ${first - 0.12f} of the way: it stopped on the lift",
        )
        framesUntil { rig.recorder.completed > 0 }
        assertEquals(1, rig.recorder.completed)
    }

    /**
     * And when the platform's events cannot be timed at all — delivered
     * together, as a slow frame does on a phone that stamps them on arrival —
     * the release still does not start from a standstill.
     */
    @Test
    fun aSwipeWhoseEventsCameTogetherStillLeavesMoving() = runComposeUiTest {
        val rig = rig()
        // No frame times: stamped on arrival, and they all arrive at once.
        rig.platform.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f, 0L))
        for (step in 1..4) rig.platform.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, 0.03f * step, 0f, 0f, 0L))
        rig.recorder.progress.clear()
        rig.platform.backCompleted()
        mainClock.advanceTimeByFrame()
        val first = rig.recorder.progress.firstOrNull()
        assertTrue(first != null, "nothing moved on the first frame after the finger lifted")
        assertTrue(
            first - 0.12f >= LeastFirstFrame,
            "the first frame after letting go at 0.12 moved the page to $first, ${first - 0.12f} of the way: it stopped on the lift",
        )
        framesUntil { rig.recorder.completed > 0 }
        assertEquals(1, rig.recorder.completed)
    }

    @Test
    fun letGoTheProgressKeepsRisingToTheEndBeforeItCompletes() = runComposeUiTest {
        val rig = rig()
        rig.drag(to = 0.4f, steps = 6, gapMillis = 16)
        rig.recorder.progress.clear()
        rig.platform.backCompleted()
        assertEquals(0, rig.recorder.completed, "completed before the page had got there")
        framesUntil { rig.recorder.completed > 0 }
        val after = rig.recorder.progress
        assertTrue(after.isNotEmpty(), "nothing moved after the finger lifted")
        assertTrue(after.zipWithNext().all { (a, b) -> b > a }, "the release stalled or turned back: $after")
        assertEquals(1f, after.last(), "the release completed at ${after.last()}, short of the end")
        assertEquals(1, rig.recorder.completed)
    }

    @Test
    fun abandonedItReturnsThenCancels() = runComposeUiTest {
        val rig = rig()
        rig.drag(to = 0.35f, steps = 6, gapMillis = 30)
        rig.recorder.progress.clear()
        rig.platform.backCancelled()
        framesUntil { rig.recorder.cancelled > 0 }
        val after = rig.recorder.progress
        assertTrue(after.zipWithNext().all { (a, b) -> b < a }, "the return did not fall steadily: $after")
        assertEquals(0f, after.last())
        assertEquals(1, rig.recorder.cancelled)
        assertEquals(0, rig.recorder.completed)
    }

    @Test
    fun aKeyGoesBackAtOnce() = runComposeUiTest {
        val rig = rig()
        rig.platform.backCompleted()
        assertEquals(1, rig.recorder.completed, "a key or a button waited for a gesture that was never made")
        assertEquals(0, rig.recorder.started)
    }

    /** A dialog's back comes through the relay on Android, and it still closes once, when the page is gone. */
    @Test
    fun aDialogClosesOnceThroughTheRelay() = runComposeUiTest {
        val dispatcher = NavigationEventDispatcher()
        val platform = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        var visible by mutableStateOf(true)
        var dismissals = 0
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides owner,
                LocalBackStyle provides BackStyle.Predictive,
                LocalRelaysSystemBack provides true,
            ) {
                KontourTheme(darkTheme = false) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Dialog(visible = visible, onDismissRequest = { dismissals++; visible = false }) {
                            Text("Leave without saving?")
                        }
                    }
                }
            }
        }
        repeat(20) { mainClock.advanceTimeByFrame() }
        platform.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f, 1_000L))
        platform.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, 0.3f, 0f, 0f, 1_050L))
        platform.backCompleted()
        assertEquals(0, dismissals, "the dialog was dismissed before the gesture had carried it off")
        repeat(30) { mainClock.advanceTimeByFrame() }
        assertEquals(1, dismissals, "a relayed back closed the dialog $dismissals times")
    }

    @Test
    fun aBackDuringAReleaseIsNotLost() = runComposeUiTest {
        val rig = rig()
        rig.drag(to = 0.5f, steps = 4, gapMillis = 16)
        rig.platform.backCompleted()
        // A second back — a gesture and all — while the first is still carrying on.
        rig.drag(to = 0.2f, steps = 2, gapMillis = 16)
        rig.platform.backCompleted()
        framesUntil { rig.recorder.completed >= 2 }
        assertEquals(2, rig.recorder.completed, "two backs in quick succession went back ${rig.recorder.completed} times")
        assertEquals(1, rig.recorder.started, "the second gesture was relayed into the first")
    }

    /**
     * A relay on its own, with a frame clock this test advances a frame of
     * [period] at a time — the screen's real rate, which the relay is not told.
     *
     * `runComposeUiTest`'s clock draws a frame every 16ms and cannot be made to
     * draw one every 8, so the frames here are sent by hand. Each event is
     * stamped with, and followed by, the frame it is drawn on: Android hands
     * input to a frame before its animations run.
     */
    private class Direct(val period: Long) : AutoCloseable {
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(clock + Dispatchers.Unconfined)
        val platform = DirectNavigationEventInput()
        val recorder = Recorder()
        val input = SwipeBackInput(pacedAtLeast = 3.5f)
        var now = 1_000_000_000L

        init {
            NavigationEventDispatcher().apply {
                addInput(platform)
                addHandler(SystemBackRelay(input, scope).also { it.isBackEnabled = true })
            }
            NavigationEventDispatcher().apply {
                addHandler(recorder)
                addInput(input)
            }
        }

        /** A gesture from the left edge, at [progress] on each of its frames in turn. */
        fun swipe(vararg progress: Float) {
            platform.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f, now / 1_000_000L))
            for (p in progress) {
                platform.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, p, 0f, 0f, (now + period) / 1_000_000L))
                frame()
            }
        }

        fun frame() {
            now += period
            clock.sendFrame(now)
        }

        override fun close() = scope.cancel()
    }

    /**
     * On a 120Hz or a 90Hz screen the first frame after letting go moves as far
     * as the frame after it: one of the screen's frames, not one at sixty a
     * second.
     *
     * The release is timed from the moment the finger lifted, and that moment
     * was taken as one 60Hz frame before the release's first frame, because the
     * relay never saw a frame to measure. At 120Hz that is two frames' worth on
     * the first and one on each after it — the page jumping as it was let go, on
     * exactly the phones where a jump shows.
     */
    @Test
    fun aRelayedGestureLeavesAtOneFrameOfThisScreen() {
        for ((hz, step) in listOf(120 to 0.04f, 90 to 0.05f)) {
            Direct(1_000_000_000L / hz).use { rig ->
                rig.swipe(*FloatArray(8) { step * (it + 1) })
                val letGo = step * 8
                rig.recorder.progress.clear()
                rig.platform.backCompleted()
                rig.frame()
                rig.frame()
                val seen = rig.recorder.progress
                assertTrue(seen.size >= 2, "two frames after letting go at ${hz}Hz moved the page ${seen.size} times")
                val first = seen[0] - letGo
                val second = seen[1] - seen[0]
                assertTrue(
                    first < second * 1.15f && first > second * 0.75f,
                    "at ${hz}Hz the first frame after letting go moved $first and the next $second: " +
                        "the release was timed as if a 60Hz frame had passed since the finger lifted",
                )
            }
        }
    }

    /**
     * A quick swipe two 120Hz frames long has its speed read over its whole
     * length, as one two 60Hz frames long does.
     *
     * The speed overall is the floor under the last moment's, for when that
     * reads nothing or reads backwards — here the thumb rolling back a little as
     * it comes off, after a flick. It was read only for a gesture at least 32ms
     * long, two frames at sixty a second, so on a 120Hz phone a swipe of up to
     * three frames counted as no speed at all and left at the least pace.
     */
    @Test
    fun aSwipeTwo120HzFramesLongIsTimedOverItsLength() {
        Direct(8_333_333L).use { rig ->
            rig.swipe(0.2f, 0.125f)
            rig.recorder.progress.clear()
            rig.platform.backCompleted()
            rig.frame()
            val first = rig.recorder.progress.firstOrNull()
            assertTrue(first != null, "nothing moved on the first frame after the finger lifted")
            // 0.125 in two frames is 7.5 of the way a second, and a frame of it
            // 0.0625. Most of that, rather than the least pace's 0.029.
            assertTrue(
                first - 0.125f > 0.045f,
                "the first frame after letting go of a swipe two 120Hz frames long, at 0.125, moved " +
                    "${first - 0.125f}: its speed over its length was not read",
            )
        }
    }

    private companion object {
        /** Half a frame at the relay's least pace, 3.5 of the way a second. */
        const val LeastFirstFrame = 0.028f
    }
}
