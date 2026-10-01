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

    /** Backs completed while a release was carrying on, to go when it has. */
    private var waiting = 0

    override fun onBackStarted(event: NavigationEvent) {
        // A new gesture while the last is still being carried home is not
        // relayed: the tree is mid-gesture already. Its completion waits.
        if (input.releasing) return
        following = true
        speed.resetTracking()
        frameTimes = event.frameTimeMillis > 0L
        track(event)
        input.start(event.swipeEdge, Offset(event.touchX, event.touchY))
        if (event.progress > 0f) input.move(event.progress, Offset(event.touchX, event.touchY))
    }

    override fun onBackProgressed(event: NavigationEvent) {
        if (!following) return
        track(event)
        input.move(event.progress, Offset(event.touchX, event.touchY))
    }

    override fun onBackCompleted() {
        when {
            following -> {
                following = false
                carry(commit = true, velocity = maxOf(speed.calculateVelocity(), 0f))
            }
            input.releasing -> waiting++
            else -> input.complete()
        }
    }

    override fun onBackCancelled() {
        if (!following) return
        following = false
        carry(commit = false, velocity = minOf(speed.calculateVelocity(), 0f))
    }

    private fun track(event: NavigationEvent) {
        progress = event.progress
        val at = if (frameTimes) event.frameTimeMillis else clock.elapsedNow().inWholeMilliseconds
        speed.addDataPoint(at, progress)
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
