package io.kontour.ui.input

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A middle click — a mouse's wheel pressed — on this node: what closes a
 * browser's tab. Taken as the button goes down, on the first pass, so nothing
 * inside or under it takes the press for a click of its own; and [onClick] as
 * it comes up, if it comes up over the node, so a press dragged off and let go
 * elsewhere does nothing, as a click dragged off a button does nothing.
 *
 * A mouse alone has the button; nothing else is told of anything.
 */
internal fun Modifier.middleClick(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(onClick) {
        awaitPointerEventScope {
            var pressed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                if (event.type == PointerEventType.Press) {
                    pressed = event.buttons.isTertiaryPressed && !change.isConsumed
                    if (pressed) change.consume()
                } else if (event.type == PointerEventType.Release && pressed) {
                    pressed = false
                    change.consume()
                    val at = change.position
                    if (at.x in 0f..size.width.toFloat() && at.y in 0f..size.height.toFloat()) onClick()
                }
            }
        }
    }
