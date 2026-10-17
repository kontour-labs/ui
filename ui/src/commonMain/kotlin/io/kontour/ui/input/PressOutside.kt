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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * [onPress] when a press goes down anywhere but on this node: what lets
 * something the reader chose go when they tap elsewhere, a selected point on a
 * chart or a row left showing its actions.
 *
 * ```kotlin
 * Chart(…, modifier = Modifier.onPressOutside(enabled = selection.x != null) { selection.clear() })
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
    val latest by rememberUpdatedState(onPress)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val key = Any()
        PressWatchers.add(key) { screen ->
            val coordinates = placed.coordinates?.takeIf { it.isAttached } ?: return@add
            val bounds = Rect(
                coordinates.localToScreen(Offset.Zero),
                coordinates.localToScreen(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())),
            )
            if (!bounds.contains(screen)) latest()
        }
        onDispose { PressWatchers.remove(key) }
    }
    return onGloballyPositioned { placed.coordinates = it }.reportsPresses()
}

/** Where a node is, for a press elsewhere to be told from one on it. Not state: written in layout, read in input. */
private class Placed {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Everything waiting for a press elsewhere, each told of every press — where,
 * on the screen — to decide for itself whether it was elsewhere: a swipe row
 * showing its actions, a chart's selection, [onPressOutside].
 *
 * One for the process rather than one for each `OverlayHost`: a row inside a
 * host nested in another is closed by a press in the outer one too.
 */
internal object PressWatchers {
    private val watching = LinkedHashMap<Any, (Offset) -> Unit>()

    fun add(key: Any, onPress: (Offset) -> Unit) {
        watching[key] = onPress
    }

    fun remove(key: Any) {
        watching.remove(key)
    }

    /** A press went down at [screen], on the screen. */
    fun pressedAt(screen: Offset) {
        if (watching.isEmpty()) return
        for (onPress in watching.values.toList()) onPress(screen)
    }
}

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
