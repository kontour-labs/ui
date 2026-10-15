package io.kontour.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The platform's back gesture, relayed into a back tree of the library's own so
 * that it finishes at the finger's speed.
 *
 * Android's predictive back reports the gesture as it goes and then only that
 * it was let go, or abandoned. A `NavDisplay` finishes it from there at the
 * transition's own pace: the rest of the page's travel at a fixed speed,
 * whatever the finger was doing. Reported from a phone as the back swipe not
 * taking its velocity into account — a flick left no faster than a slow drag,
 * and slowed down the moment it was let go. The same fixed pace finished the
 * progress Android 16 plays for a press of the three-button bar's back.
 *
 * So the outermost host takes back from the platform through this, as a handler
 * on the platform's dispatcher, and replays it into its own tree through a
 * [SwipeBackInput] — iOS's input, which does not stop reporting when the finger
 * lifts. It measures the gesture's speed from the progress and the frame times
 * the platform reports, and when the platform says the gesture is over it
 * carries the progress the rest of the way at that speed, and says completed
 * or cancelled only when the page is there. Whether it goes back is still the
 * platform's decision; how it gets there is the finger's.
 *
 * **Paced by the finger, not sprung as iOS's is.** iOS's release is UIKit's
 * spring, a quarter of a second from halfway, and from a short gesture that was
 * *slower* than the fixed pace it replaced here: a flick let go at 0.3 took 18
 * frames to finish where the transition had taken 15. A stiffer spring finished
 * sooner and threw the speed away — every release took the same ten frames,
 * flick or not, because a stiff spring's own pull swamps what it starts with.
 * So the release goes the rest of the way at the finger's speed, or at
 * [RelayPace] if it let go more slowly than that, leaving at exactly the
 * finger's speed and easing a little into the end: a flick from 0.3 is gone in
 * about nine frames, a slow let-go in about twelve, a hard flick in four.
 *
 * Enabled exactly while something in the tree would answer back, so at the root
 * of an app the platform keeps its back — the predictive animation home, and
 * leaving the app.
 *
 * Back with no gesture — a key, a button — goes through at once. One arriving
 * while a release is still carrying on waits for it and then goes, rather than
 * being lost to a gesture the platform thinks is over.
 *
 * **Timed by this screen's frames, measured.** The release's first frame, its
 * shortest and the shortest gesture whose speed is read are each a frame, and a
 * frame was taken to be a sixtieth of a second: right at 60Hz, and at 120 the
 * page jumped two frames' worth on letting go. Android does not say how fast
 * its screen is, and one phone runs at 60, 90 and 120 from one minute to the
 * next. So the relay asks for the next frame on each event, as the pan the
 * library tracks itself does, and [SwipeBackInput] measures the gap between
 * two of them.
 */
internal class SystemBackRelay(
    private val input: SwipeBackInput,
    private val scope: CoroutineScope,
) : NavigationEventHandler<NavigationEventInfo>(RelayInfo, false) {

    private val speed = VelocityTracker1D(isDataDifferential = false)
    private val clock = TimeSource.Monotonic.markNow()
    private var following = false
    private var frameTimes = false
    private var progress = 0f

    /** When the gesture began, and from where, and when its last event came: for its speed overall. */
    private var startedAt = 0L
    private var startedFrom = 0f
    private var lastAt = 0L

    /** Backs completed while a release was carrying on, to go when it has. */
    private var waiting = 0

    /**
     * The next frame, asked for on an event: one per event, none while none
     * come, so a finger resting mid-gesture leaves the frame clock idle. See
     * [SwipeBackInput.nextFrame].
     */
    private var frames: Job? = null

    override fun onBackStarted(event: NavigationEvent) {
        // A new gesture while the last is still being carried home is not
        // relayed: the tree is mid-gesture already. Its completion waits.
        if (input.releasing) return
        following = true
        speed.resetTracking()
        frameTimes = event.frameTimeMillis > 0L
        track(event)
        startedAt = lastAt
        startedFrom = event.progress
        input.start(event.swipeEdge, Offset(event.touchX, event.touchY))
        if (event.progress > 0f) input.move(event.progress, Offset(event.touchX, event.touchY))
        watchFrame()
    }

    override fun onBackProgressed(event: NavigationEvent) {
        if (!following) return
        track(event)
        input.move(event.progress, Offset(event.touchX, event.touchY))
        watchFrame()
    }

    override fun onBackCompleted() {
        when {
            following -> {
                following = false
                frames?.cancel()
                carry(commit = true, velocity = maxOf(speed.calculateVelocity(), overall(), 0f))
            }
            input.releasing -> waiting++
            else -> input.complete()
        }
    }

    override fun onBackCancelled() {
        if (!following) return
        following = false
        frames?.cancel()
        carry(commit = false, velocity = minOf(speed.calculateVelocity(), 0f))
    }

    private fun watchFrame() {
        if (frames?.isActive != true) frames = scope.launch { input.nextFrame() }
    }

    private fun track(event: NavigationEvent) {
        progress = event.progress
        val at = if (frameTimes) event.frameTimeMillis else clock.elapsedNow().inWholeMilliseconds
        lastAt = at
        speed.addDataPoint(at, progress)
    }

    /**
     * The gesture's speed over its whole length, for when the last moment's
     * cannot be read.
     *
     * The tracker reads the last 40ms of events, and a quick swipe can leave
     * fewer than two in it: a handful of events in all, or a slow frame as the
     * page behind is first drawn holding them up — and on a phone that stamps
     * them on arrival, arriving together. It measured nothing, and the release
     * left from a standstill: *"if i swipe back quickly using the side-gesture
     * back button (and let go quickly), then sometimes it can feel really
     * sluggish"*. A gesture shorter than two frames is too short to time this
     * way either, and counts as nothing: two of this screen's frames, measured,
     * where it was 32ms whatever the screen — a millisecond short of two at
     * sixty a second, for events stamped to the whole millisecond, which this
     * still is there.
     */
    private fun overall(): Float {
        val elapsed = lastAt - startedAt
        if (elapsed < input.frameNanos() * TimedAtLeastFrames / 1_000_000L - 1) return 0f
        return (progress - startedFrom) * 1_000f / elapsed
    }

    private fun carry(commit: Boolean, velocity: Float) {
        scope.launch {
            input.release(commit, velocity)
            while (waiting > 0) {
                waiting--
                input.complete()
            }
        }
    }

    private object RelayInfo : NavigationEventInfo()
}

/**
 * Relays [platform]'s back into [into] through a [SystemBackRelay] for as long
 * as this is composed.
 */
@Composable
internal fun RelaySystemBack(platform: NavigationEventDispatcher, into: NavigationEventDispatcher) {
    val input = rememberSwipeBackInput(into, pacedAtLeast = RelayPace)
    val scope = rememberCoroutineScope()
    val relay = remember(input, scope) { SystemBackRelay(input, scope) }
    DisposableEffect(platform, relay) {
        input.onArmedChanged = { relay.isBackEnabled = it }
        relay.isBackEnabled = input.armed
        platform.addHandler(relay)
        onDispose {
            input.onArmedChanged = null
            relay.remove()
        }
    }
}

/**
 * The least speed a relayed gesture finishes at, in progress per second: a
 * little brisker than the transition's own pace, the whole way in 350ms, which
 * it replaces.
 */
private const val RelayPace = 3.5f

/** The shortest gesture whose speed overall is worth reading, in frames. */
private const val TimedAtLeastFrames = 2
