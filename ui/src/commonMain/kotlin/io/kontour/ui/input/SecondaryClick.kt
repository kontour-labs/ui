package io.kontour.ui.input

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A secondary click — a mouse's right button — at a point in this node: what
 * opens a context menu, on an event calendar's events and its empty time, and
 * on a document tab and its "+".
 */
internal fun Modifier.secondaryClick(onClick: (Offset) -> Unit): Modifier = pointerInput(onClick) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
            val change = event.changes.firstOrNull() ?: continue
            // Taken already by something inside: an event's own menu, over its day's.
            if (change.isConsumed) continue
            change.consume()
            onClick(change.position)
        }
    }
}
