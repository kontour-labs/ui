package io.kontour.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.interaction.DirectionDecisionShare
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Back from anywhere on the page, as iOS 26 does it: a sideways pan towards the
 * trailing edge, started on the content rather than at the leading edge.
 *
 * ```kotlin
 * AnimatedContent(page, transitionSpec = { … }) { current ->
 *     Box(Modifier.fillMaxSize().contentSwipeBack(enabled = current != Home)) { … }
 * }
 * ```
 *
 * `rememberPageTransitionStrategy` in `:ui-nav3` puts it on every page with
 * somewhere to go back to, and `ListDetailPaneScaffold` on its detail; this is
 * for a page that is neither.
 *
 * **It yields.** The page watches the pan only after everything on it has had
 * its turn, so a carousel, a row's swipe actions, a slider or a horizontal list
 * takes the pan first, and a pan any of them consumed is never back. Only a pan
 * that goes more sideways than up or down past the touch slop, and towards the
 * trailing edge, becomes one. A pan starting at the leading edge itself is left
 * to the edge gesture.
 *
 * **Let go, it carries on at the finger's speed.** Decided as UIKit decides it:
 * by where the page would come to rest if it coasted on at that speed, so a
 * moderate swipe a third of the way across goes back and a slow one stays. The
 * page goes the rest of the way on a spring that starts at the speed the finger
 * left it — no pause at the moment of letting go, and a quick flick finishes
 * quickly. A flick back springs it home the same way.
 *
 * Only under [BackStyle.Swipe] — iOS's — and only for touch; and only while
 * something would answer back, so on a page with nowhere to go it does nothing.
 *
 * @param enabled Off for a page that cannot go back — the root of its stack.
 */
@Composable
fun Modifier.contentSwipeBack(enabled: Boolean = true): Modifier {
    if (!enabled || LocalBackStyle.current != BackStyle.Swipe) return this
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher ?: return this
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pan = remember(rtl) {
        BackPan(
            rightwards = !rtl,
            edge = if (rtl) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT,
            pass = PointerEventPass.Main,
            // The edge gesture owns the edge itself.
            startsAt = { x, window, density ->
                val fromLeading = if (rtl) window - x else x
                fromLeading >= EdgePanWidth.value * density
            },
        )
    }
    return swipeBack(pan, rememberSwipeBackInput(dispatcher))
}

/**
 * A pan the library tracks itself, handed to the back dispatcher as if it were
 * the platform's gesture — so it drives exactly what an edge pan would.
 *
 * The difference is the release. A platform gesture reports that it was let go
 * and nothing more, and a `NavDisplay` finishes it from there at the
 * transition's own pace, a frame after the pop: the page stops for a moment
 * when the finger lifts, and a flick leaves as slowly as a drag. This one keeps
 * reporting progress after the finger lifts, on a critically damped spring
 * seeded with the finger's speed, and says completed only when the page is
 * already where the pop leaves it.
 */
@Stable
internal class SwipeBackInput(
    /**
     * How a release finishes. Null, the default, is iOS's: a critically damped
     * spring, UIKit's pace. Otherwise the least speed, in progress per second,
     * of a release paced by the finger instead — see `SystemBackRelay`, which
     * is Android's and why.
     */
    private val pacedAtLeast: Float? = null,
) : NavigationEventInput() {

    /** Whether anything would answer back; no pan is claimed while nothing would. */
    var armed = false
        private set

    /** Told whenever [armed] changes — how a relay knows whether to take the platform's back. */
    var onArmedChanged: ((Boolean) -> Unit)? = null

    /** A released gesture is still carrying on; a new pan waits for it. */
    var releasing = false
        private set

    private var added = false
    private var edge = NavigationEvent.EDGE_LEFT
    private var at = Offset.Zero
    private var progress = 0f

    override fun onAdded(dispatcher: NavigationEventDispatcher) {
        added = true
    }

    override fun onRemoved() {
        added = false
        armed = false
        onArmedChanged?.invoke(false)
    }

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        armed = hasEnabledHandlers
        onArmedChanged?.invoke(hasEnabledHandlers)
    }

    fun start(edge: Int, at: Offset) {
        if (!added) return
        this.edge = edge
        this.at = at
        progress = 0f
        dispatchOnBackStarted(NavigationEvent(edge, 0f, at.x, at.y))
    }

    fun move(progress: Float, at: Offset) {
        if (!added) return
        this.progress = progress
        this.at = at
        dispatchOnBackProgressed(NavigationEvent(edge, progress, at.x, at.y))
    }

    /**
     * Carries the gesture the rest of the way: to 1 and completed when
     * [commit], back to 0 and cancelled otherwise.
     *
     * @param velocity The finger's speed as it lifted, in progress per second,
     *   positive towards going back.
     */
    suspend fun release(commit: Boolean, velocity: Float) {
        if (!added) return
        releasing = true
        try {
            val target = if (commit) 1f else 0f
            val at = releaseCurve(progress, target, velocity)
            // Timed from the moment the finger lifted, taken as a frame before
            // the first one this sees, rather than from that first frame: an
            // animation timed from its first frame spends it at where it
            // started, and that frame of stillness is the pause this is here
            // to remove.
            var start = Long.MIN_VALUE
            var done = false
            while (!done) {
                done = withFrameNanos { now ->
                    if (start == Long.MIN_VALUE) start = now - FrameNanos
                    val (value, finished) = at(now - start)
                    move(if (finished) target else value, this.at)
                    finished
                }
            }
            if (added) {
                if (commit) dispatchOnBackCompleted() else dispatchOnBackCancelled()
            }
        } catch (e: CancellationException) {
            if (added) dispatchOnBackCancelled()
            throw e
        } finally {
            releasing = false
        }
    }

    /**
     * Where a release from [from] to [target], let go at [velocity], is after a
     * time in nanoseconds, and whether it has arrived.
     */
    private fun releaseCurve(from: Float, target: Float, velocity: Float): (Long) -> Pair<Float, Boolean> {
        val least = pacedAtLeast
        if (least == null) {
            // Aimed a little past the end and stopped where it crosses it, so it
            // arrives at speed rather than creeping over the last few pixels,
            // which is how a critically damped spring ends and how UIKit's
            // does not.
            val aim = if (target >= 1f) 1f + Overshoot else -Overshoot
            val spring = TargetBasedAnimation(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = ReleaseStiffness,
                    visibilityThreshold = 1f / 1000f,
                ),
                typeConverter = Float.VectorConverter,
                initialValue = from,
                targetValue = aim,
                initialVelocity = velocity,
            )
            return { played ->
                val value = spring.getValueFromNanos(played)
                value to (spring.isFinishedFromNanos(played) || (if (target >= 1f) value >= 1f else value <= 0f))
            }
        }
        // Paced by the finger: the rest of the way at the speed it let go at,
        // or at [least] if it was slower, leaving at exactly the finger's speed
        // and easing a little into the end. A cubic from where it is to where
        // it is going, its first slope the finger's and its last half the
        // pace's — which keeps it moving forwards throughout.
        //
        // **Never from a standstill.** Leaving at least half the pace: a finger
        // whose speed could not be read is not a finger that stopped, and a
        // release that left at nothing paused on the lift and then picked up,
        // which on a quick swipe read as the page dragging its feet.
        val distance = target - from
        val remaining = abs(distance)
        if (remaining < 1e-4f) return { target to true }
        val along = if (distance > 0f) velocity else -velocity
        val pace = maxOf(along, least)
        val seconds = (remaining / pace).coerceIn(MinReleaseSeconds, MaxReleaseSeconds)
        val leaving = (along.coerceAtLeast(0f) * seconds / remaining).coerceIn(LeastLeaving, 2f)
        val arriving = 0.5f
        return { played ->
            val u = (played / 1e9f / seconds).coerceIn(0f, 1f)
            val u2 = u * u
            val u3 = u2 * u
            val eased = (u3 - 2f * u2 + u) * leaving + (-2f * u3 + 3f * u2) + (u3 - u2) * arriving
            (from + distance * eased) to (u >= 1f)
        }
    }

    /** Ends a gesture whose finger went away without lifting — another gesture took it. */
    fun abandon() {
        if (added) dispatchOnBackCancelled()
    }

    /** Back with no gesture at all — a key, a button: completed at once. */
    fun complete() {
        if (added) dispatchOnBackCompleted()
    }
}

/** A [SwipeBackInput] added to [dispatcher] for as long as this is composed. */
@Composable
internal fun rememberSwipeBackInput(
    dispatcher: NavigationEventDispatcher,
    pacedAtLeast: Float? = null,
): SwipeBackInput {
    val input = remember(pacedAtLeast) { SwipeBackInput(pacedAtLeast) }
    DisposableEffect(dispatcher, input) {
        dispatcher.addInput(input)
        onDispose { dispatcher.removeInput(input) }
    }
    return input
}

/** Where a pan becomes back, and which way it has to go. */
internal class BackPan(
    /** The finger goes back by moving right across the window; left if false. */
    val rightwards: Boolean,
    /** The edge the gesture reports it came from. */
    val edge: Int,
    /**
     * [PointerEventPass.Main] to yield to what is inside, which sees the pan
     * first; [PointerEventPass.Initial] to claim it before them, as UIKit's
     * edge pan does.
     */
    val pass: PointerEventPass,
    /** Whether a pan starting at `x` in a window `window` wide may become back, at `density` pixels a dp. */
    val startsAt: (x: Float, window: Float, density: Float) -> Boolean,
    /**
     * The screen edge's: claims as early as a control deciding its direction
     * does, and gives way to a control that took the press itself.
     *
     * The edge and a swipe row under it both want a sideways pan, and whoever
     * claims first has it. Claimed at the touch slop, the edge lost to any row —
     * a row decides at [io.kontour.ui.interaction.DirectionDecisionShare] of it
     * — so a swipe from the very edge of the screen over a row of swipe actions
     * opened the row. Reported from a phone, with how iOS does it: from the edge
     * it is back, from anywhere else it is the row. Deciding at the same point in
     * the initial pass, the edge is first.
     *
     * A slider pressed inside the band is different: it took the press itself,
     * before anything moved, and a slider against the edge that could not be
     * dragged would be the page's fault.
     */
    val early: Boolean = false,
)

/**
 * Turns [pan] into back through [input], over a travel of this node's width or
 * of [travel], when given.
 */
@Composable
internal fun Modifier.swipeBack(pan: BackPan, input: SwipeBackInput, travel: (() -> Float)? = null): Modifier {
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop
    val placed = remember { Placed() }
    return this
        .onPlaced { placed.coordinates = it }
        .pointerInput(pan, input, slop) { trackBackPan(pan, input, slop, placed, scope, travel) }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.trackBackPan(
    pan: BackPan,
    input: SwipeBackInput,
    slop: Float,
    placed: Placed,
    scope: CoroutineScope,
    travel: (() -> Float)?,
) {
    val sign = if (pan.rightwards) 1f else -1f
    val claimAt = if (pan.early) slop * DirectionDecisionShare else slop
    awaitPointerEventScope {
        while (true) {
            val down = awaitPointerEvent(pan.pass)
            val first = down.changes.firstOrNull() ?: continue
            if (!first.pressed || first.previousPressed || first.type != PointerType.Touch) continue
            if (!input.armed || input.releasing) continue
            // In the window's coordinates, not this node's: the page moves with
            // the finger once the gesture is under way, so a finger measured
            // against it would hardly seem to move at all.
            val start = placed.toWindow(first.position)
            if (!pan.startsAt(start.x, placed.windowWidth, density)) continue

            val id = first.id
            if (pan.early) {
                // The same press, after everything under it has seen it: a control
                // that took the press itself keeps it.
                val settled = awaitPointerEvent(PointerEventPass.Final)
                if (settled.changes.firstOrNull { it.id == id }?.isConsumed == true) continue
            }
            val tracker = VelocityTracker()
            tracker.addPosition(first.uptimeMillis, start)
            var claimed = false
            while (true) {
                val event = awaitPointerEvent(pan.pass)
                val change = event.changes.firstOrNull { it.id == id }
                if (change == null) {
                    if (claimed) input.abandon()
                    break
                }
                val at = placed.toWindow(change.position)
                tracker.addPosition(change.uptimeMillis, at)
                val delta = at - start
                val along = delta.x * sign
                if (!claimed) {
                    // Somebody inside took it; it was theirs.
                    if (change.isConsumed || !change.pressed) break
                    if (abs(delta.x) > claimAt || abs(delta.y) > claimAt) {
                        // Within 45° of sideways, on the content as at the edge:
                        // the test a pan recogniser's delegate makes. It was
                        // twice as far sideways as up or down on the content,
                        // which a thumb arcing across the screen often is not,
                        // and the swipe that missed it did nothing at all. A
                        // list under the page that scrolls up and down has seen
                        // every move first and took any that were more its own.
                        val sideways = abs(delta.x) >= abs(delta.y)
                        if (along > claimAt && sideways) {
                            claimed = true
                            input.start(pan.edge, at)
                        } else {
                            break
                        }
                    }
                }
                if (!claimed) continue
                change.consume()
                val span = (travel?.invoke() ?: size.width.toFloat()).coerceAtLeast(1f)
                // From where the finger went down, not from where the pan was
                // claimed: the page is under the finger, as UIKit's is. Measured
                // from the claim it trailed by the slop the whole way across, and
                // half the width meant half the width and a slop more.
                val progress = (along / span).coerceIn(0f, 1f)
                if (change.pressed) {
                    input.move(progress, at)
                    continue
                }
                // Let go: decided by where it would come to rest. See
                // [releaseCommits].
                val velocity = tracker.calculateVelocity().x * sign
                scope.launch { input.release(releaseCommits(progress, velocity / span), velocity / span) }
                break
            }
        }
    }
}

/** The node's coordinates, live: read at each event, so they carry wherever it has moved to. */
private class Placed {
    var coordinates: LayoutCoordinates? = null

    fun toWindow(local: Offset): Offset =
        coordinates?.takeIf { it.isAttached }?.localToWindow(local) ?: local

    val windowWidth: Float
        get() = coordinates?.takeIf { it.isAttached }?.findRootCoordinates()?.size?.width?.toFloat() ?: 0f
}

/**
 * How close to the leading edge a pan has to start to be the edge gesture:
 * the band the edge pan claims, and the content pan leaves it.
 */
internal val EdgePanWidth = 24.dp

/**
 * Whether a swipe let go at [progress], moving at [velocity] in progress per
 * second towards going back, goes back: whether the page would come to rest
 * past half the width if it coasted on.
 *
 * The coast is UIKit's, `UIScrollView.DecelerationRate.normal`: a scroll view
 * let go at `v` points a second travels `v / 1000 * r / (1 - r)` further, with
 * `r` 0.998 — half a second's worth. It is how *Designing Fluid Interfaces*
 * says to decide where a gesture ends, and what the sheets here decide by.
 *
 * It replaced a rule of two thresholds — past half the width, or faster than
 * 1000dp a second — under which a swipe at an ordinary speed had to be dragged
 * past the middle of the screen, and a moderate one a third of the way across
 * sprang back. *"Sometimes swiping back on iOS feels like it takes a bit too
 * much effort compared to native apps."* A flick back still stays: it coasts
 * the other way.
 */
internal fun releaseCommits(progress: Float, velocity: Float): Boolean =
    progress + velocity * CoastSeconds > CommitFraction

/** UIKit's: a page that comes to rest past half the width goes back. */
private const val CommitFraction = 0.5f

/** How far a release coasts, in seconds of its own speed. See [releaseCommits]. */
private const val CoastSeconds = 0.998f / (1f - 0.998f) / 1000f

/** One frame at sixty a second: how long before its first frame a release began. */
private const val FrameNanos = 16_666_667L

/**
 * The spring a released gesture finishes on: critically damped, so it never
 * turns back, and stiff enough that from halfway with no speed of its own it
 * reaches the end in about a quarter of a second — UIKit's pace.
 */
private const val ReleaseStiffness = Spring.StiffnessLow

/** The longest and shortest a paced release takes, in seconds. */
private const val MinReleaseSeconds = 0.016f

/** The least a paced release leaves at, against its pace: half, so it never sets off from rest. */
private const val LeastLeaving = 0.5f
private const val MaxReleaseSeconds = 0.3f

/** How far past the end the release aims, as a fraction of the travel. */
private const val Overshoot = 0.05f
