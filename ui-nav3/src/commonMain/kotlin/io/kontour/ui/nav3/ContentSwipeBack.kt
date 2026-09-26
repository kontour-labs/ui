package io.kontour.ui.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.motion.BackStyle
import kotlin.math.abs

/**
 * Back from anywhere on the page, as iOS 26 does it: a sideways pan towards the
 * trailing edge, started on the content rather than at the leading edge.
 *
 * **It yields.** The page watches the pan only after everything on it has had
 * its turn — the main pass, from the innermost node out — so a carousel, a
 * row's swipe actions, a slider or a horizontal list takes the pan first, and
 * a pan any of them consumed is never back. Only a pan that goes mostly
 * sideways, twice as far as it goes up or down, past the platform's touch slop
 * and towards the trailing edge becomes one. A pan that starts at the leading
 * edge itself is left to the platform's own edge gesture.
 *
 * Only under [BackStyle.Swipe], only for touch, and only while something would
 * answer back — so at the root of a stack it does nothing at all.
 */
@Composable
internal fun Modifier.contentSwipeBack(style: BackStyle): Modifier {
    if (style != BackStyle.Swipe) return this
    val owner = LocalNavigationEventDispatcherOwner.current ?: return this
    val input = remember { ContentSwipeInput() }
    DisposableEffect(owner, input) {
        owner.navigationEventDispatcher.addInput(input)
        onDispose { owner.navigationEventDispatcher.removeInput(input) }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slop = LocalViewConfiguration.current.touchSlop
    return this
        .onPlaced { input.coordinates = it }
        .pointerInput(input, rtl, slop) {
            val leadingEdge = LeadingEdgeLeftToPlatform.toPx()
            val commitVelocity = CommitVelocity.toPx()
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(PointerEventPass.Main)
                    val first = down.changes.firstOrNull() ?: continue
                    if (!first.pressed || first.previousPressed || first.type != PointerType.Touch) continue
                    if (!input.armed) continue
                    // In the window's coordinates, not the page's: the page moves
                    // with the finger once the gesture is under way, so a finger
                    // measured against it would hardly seem to move at all.
                    val start = input.toWindow(first.position)
                    // The platform's edge pan owns the edge itself.
                    val fromLeading = if (rtl) input.windowWidth - start.x else start.x
                    if (fromLeading < leadingEdge) continue

                    val id = first.id
                    val tracker = VelocityTracker()
                    tracker.addPosition(first.uptimeMillis, start)
                    var claimed = false
                    var claimOffset = 0f
                    var finished = false
                    while (!finished) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == id }
                        if (change == null) {
                            if (claimed) input.cancel()
                            break
                        }
                        val at = input.toWindow(change.position)
                        tracker.addPosition(change.uptimeMillis, at)
                        val delta = at - start
                        val towardsTrailing = if (rtl) -delta.x else delta.x
                        if (!claimed) {
                            // Somebody on the page took it; it was theirs.
                            if (change.isConsumed) break
                            if (!change.pressed) break
                            if (abs(delta.x) > slop || abs(delta.y) > slop) {
                                if (towardsTrailing > slop && abs(delta.x) > 2 * abs(delta.y)) {
                                    claimed = true
                                    claimOffset = towardsTrailing
                                    input.start(at, rtl)
                                } else {
                                    break
                                }
                            }
                        }
                        if (claimed) {
                            change.consume()
                            val progress = ((towardsTrailing - claimOffset) / size.width).coerceIn(0f, 1f)
                            if (change.pressed) {
                                input.progress(progress, at, rtl)
                            } else {
                                val velocity = tracker.calculateVelocity().x.let { if (rtl) -it else it }
                                if (progress > CommitFraction || velocity > commitVelocity) {
                                    input.complete()
                                } else {
                                    input.cancel()
                                }
                                finished = true
                            }
                        }
                    }
                }
            }
        }
}

/**
 * The page's pan, handed to the back dispatcher as if it were the platform's
 * own gesture — so it drives exactly what an edge pan would.
 */
private class ContentSwipeInput : NavigationEventInput() {
    /** Whether anything would answer back; nothing is watched while it would not. */
    var armed = false
        private set

    /** The page's, live: read at each event, so it carries wherever the page has moved to. */
    var coordinates: LayoutCoordinates? = null

    fun toWindow(local: Offset): Offset =
        coordinates?.takeIf { it.isAttached }?.localToWindow(local) ?: local

    /** The window's width, for how far a pan starts from the leading edge in right-to-left. */
    val windowWidth: Float
        get() = coordinates?.takeIf { it.isAttached }?.findRootCoordinates()?.size?.width?.toFloat() ?: 0f

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        armed = hasEnabledHandlers
    }

    fun start(at: Offset, rtl: Boolean) =
        dispatchOnBackStarted(NavigationEvent(edge(rtl), 0f, at.x, at.y))

    fun progress(progress: Float, at: Offset, rtl: Boolean) =
        dispatchOnBackProgressed(NavigationEvent(edge(rtl), progress, at.x, at.y))

    fun complete() = dispatchOnBackCompleted()

    fun cancel() = dispatchOnBackCancelled()

    /** The edge back comes from: the leading one. */
    private fun edge(rtl: Boolean) = if (rtl) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT
}

/** A pan starting this close to the leading edge is the platform's edge gesture. */
private val LeadingEdgeLeftToPlatform = 24.dp

/** UIKit's: past half the width, or a flick, goes back. */
private const val CommitFraction = 0.5f

/** A flick towards the trailing edge this fast goes back wherever it ends. */
private val CommitVelocity = 1000.dp
