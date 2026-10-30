package io.kontour.ui.input

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * [onPress] when a press goes down anywhere but on this node: what lets
 * something the reader chose go when they tap elsewhere, a selected point on a
 * chart or a row left showing its actions.
 *
 * ```kotlin
 * Row(Modifier.onPressOutside(enabled = chosen != null) { chosen = null }) { … }
 * ```
 *
 * The press is only watched, never taken, so it goes on to do what it was
 * aimed at: a tap on a button beside the chart presses the button too.
 *
 * Presses are seen by an [io.kontour.ui.overlay.OverlayHost] round the app,
 * which reports every one, and by this node itself; with no host, a press
 * elsewhere is not seen. Compared on the screen, so a press in a popup or a
 * dialog of its own window counts as elsewhere too.
 *
 * @param enabled Whether to watch at all. Off while there is nothing to let go
 *   of, so a screen of charts is not told of every tap for nothing.
 */
@Composable
fun Modifier.onPressOutside(enabled: Boolean = true, onPress: () -> Unit): Modifier {
    val placed = remember { Placed() }
    placed.watchOutside(enabled, PressWatchers, null, onPress)
    return onGloballyPositioned { placed.coordinates = it }.reportsPresses()
}

/**
 * [onPress] when a press goes down anywhere but on this node's parent: for a
 * zero-size probe that stands for its parent, as a [io.kontour.ui.overlay.Tooltip]'s
 * does. See [onPressOutside].
 */
@Composable
internal fun Modifier.onPressOutsideParent(
    enabled: Boolean = true,
    alsoInside: AlsoInside? = null,
    onPress: () -> Unit,
): Modifier {
    val placed = remember { Placed() }
    placed.watchOutside(enabled, PressWatchers, alsoInside, onPress)
    return onGloballyPositioned { placed.coordinates = it.parentLayoutCoordinates ?: it }.reportsPresses()
}

/**
 * [onPressOutside], with a second place a press counts as on rather than
 * elsewhere: [alsoInside], wherever it is laid out. A tooltip's control is the
 * node, and its bubble, drawn in the overlay host far from it, is the second
 * place, so a tap on the bubble leaves it open.
 */
@Composable
internal fun Modifier.onPressOutside(enabled: Boolean, alsoInside: AlsoInside, onPress: () -> Unit): Modifier {
    val placed = remember { Placed() }
    placed.watchOutside(enabled, PressWatchers, alsoInside, onPress)
    return onGloballyPositioned { placed.coordinates = it }.reportsPresses()
}

/**
 * Somewhere else a press counts as on, for [onPressOutside]: where the node
 * marked with [alsoInside] was last laid out. Not state, as [Placed] is not.
 */
internal class AlsoInside {
    internal var coordinates: LayoutCoordinates? = null
}

/** Marks this node as [area]: a press on it is not elsewhere to what watches with [area]. */
internal fun Modifier.alsoInside(area: AlsoInside): Modifier = onGloballyPositioned { area.coordinates = it }

/**
 * [onScroll] when a scroll starts anywhere but on this node — a wheel turned,
 * a trackpad's fingers pushed, sideways or down — as [onPressOutside] is told
 * of a press: what closes a swipe row left showing its actions when a carousel,
 * a table or the list beside it is scrolled from a mouse or a trackpad.
 *
 * A finger's scroll needs none of this, because it starts with a press. A
 * wheel's and a trackpad's have no press, and the row stayed open while the
 * reader scrolled something else across: asked for as *"can we apply that too
 * if you start side-scrolling somewhere else?"*. Added 2026-10-21 for 0.5.0.
 *
 * Seen as presses are: by an [io.kontour.ui.overlay.OverlayHost] round the
 * app, and by this node itself. Only watched, never taken, so the scroll still
 * scrolls what it was aimed at. Internal while a swipe row is the only thing
 * that wants it; a chart's selection, the other user of [onPressOutside],
 * keeps its point through a scroll of the page.
 */
@Composable
internal fun Modifier.onScrollOutside(enabled: Boolean = true, onScroll: () -> Unit): Modifier {
    val placed = remember { Placed() }
    placed.watchOutside(enabled, ScrollWatchers, null, onScroll)
    return onGloballyPositioned { placed.coordinates = it }.reportsScrolls()
}

/** Where a node is, for a press elsewhere to be told from one on it. Not state: written in layout, read in input. */
private class Placed {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Watches [watchers] while [enabled], and calls [onOutside] for whatever they
 * report off this node, and off [also] where there is one.
 */
@Composable
private fun Placed.watchOutside(enabled: Boolean, watchers: PointerWatchers, also: AlsoInside?, onOutside: () -> Unit) {
    val latest by rememberUpdatedState(onOutside)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val key = Any()
        watchers.add(key) { screen ->
            val node = coordinates?.takeIf { it.isAttached } ?: return@add
            if (node.onScreen().contains(screen)) return@add
            if (also?.coordinates?.takeIf { it.isAttached }?.onScreen()?.contains(screen) == true) return@add
            latest()
        }
        onDispose { watchers.remove(key) }
    }
}

/** Where a laid-out node is on the screen. */
private fun LayoutCoordinates.onScreen(): Rect =
    Rect(localToScreen(Offset.Zero), localToScreen(Offset(size.width.toFloat(), size.height.toFloat())))

/**
 * Everything waiting for a pointer's news elsewhere, each told of every one —
 * where, on the screen — to decide for itself whether it was elsewhere: a swipe
 * row showing its actions, a chart's selection, [onPressOutside].
 *
 * One for the process rather than one for each `OverlayHost`: a row inside a
 * host nested in another is closed by a press in the outer one too.
 */
internal open class PointerWatchers {
    private val watching = LinkedHashMap<Any, (Offset) -> Unit>()

    fun add(key: Any, onEvent: (Offset) -> Unit) {
        watching[key] = onEvent
    }

    fun remove(key: Any) {
        watching.remove(key)
    }

    /** Whether anything is waiting, so a root can skip the work of saying where. */
    fun isEmpty(): Boolean = watching.isEmpty()

    /** It happened at [screen], on the screen. */
    fun at(screen: Offset) {
        if (watching.isEmpty()) return
        for (onEvent in watching.values.toList()) onEvent(screen)
    }
}

/** Told of every press as it goes down. */
internal object PressWatchers : PointerWatchers() {
    /** A press went down at [screen], on the screen. */
    fun pressedAt(screen: Offset) = at(screen)
}

/** Told of every scroll, from a wheel or a trackpad, as it starts and as it goes on. */
internal object ScrollWatchers : PointerWatchers()

/**
 * Tells [PressWatchers] of every press that goes down in this node, before
 * anything in it sees the press, and takes nothing from it: on an app's root —
 * `OverlayHost` puts it there — so a press anywhere reaches what is watching.
 */
@Composable
internal fun Modifier.reportsPresses(): Modifier {
    val placed = remember { Placed() }
    return onGloballyPositioned { placed.coordinates = it }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val coordinates = placed.coordinates?.takeIf { it.isAttached } ?: return@awaitEachGesture
            PressWatchers.pressedAt(coordinates.localToScreen(down.position))
        }
    }
}

/**
 * Tells [ScrollWatchers] of every scroll in this node — a wheel's notch, a
 * trackpad's pan beginning — before anything in it sees the scroll, and takes
 * nothing from it: on an app's root beside [reportsPresses], so a scroll
 * anywhere reaches what is watching.
 *
 * A wheel's every notch is told, because a wheel's scroll has no start to tell
 * of; a pan is told once, as it starts, and its moves are the same gesture.
 * Either way what is watching hears of it at the first, and lets go then.
 */
@Composable
internal fun Modifier.reportsScrolls(): Modifier {
    val placed = remember { Placed() }
    return onGloballyPositioned { placed.coordinates = it }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val scrolled = when (event.type) {
                    PointerEventType.PanStart -> true
                    // One with no delta says nothing has moved.
                    PointerEventType.Scroll -> event.changes.any { it.scrollDelta != Offset.Zero }
                    else -> false
                }
                if (!scrolled || ScrollWatchers.isEmpty()) continue
                val coordinates = placed.coordinates?.takeIf { it.isAttached } ?: continue
                val change = event.changes.firstOrNull() ?: continue
                ScrollWatchers.at(coordinates.localToScreen(change.position))
            }
        }
    }
}
