package io.kontour.ui.input

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor as AwtCursor

/**
 * AWT's predefined cursors, where one of them means the same thing.
 *
 * There are fourteen, and none is a grab, a forbidden sign or a *background*
 * busy — `WAIT_CURSOR` is the hourglass that says the application has stopped.
 * Those return null and draw the nearest of Compose's four instead, which loses a
 * little polish rather than saying something untrue.
 */
internal actual fun platformPointerIcon(cursor: Cursor): PointerIcon? = when (cursor) {
    Cursor.ResizeColumn -> ResizeColumnIcon
    Cursor.ResizeRow -> ResizeRowIcon
    Cursor.Grabbing -> GrabbingIcon
    Cursor.Grab, Cursor.NotAllowed, Cursor.Progress -> null
    // Compose's own, the same on every platform.
    Cursor.Default, Cursor.Pointer, Cursor.Text, Cursor.Crosshair -> null
}

// Made once rather than per call: `pointerCursor` runs in composition, and
// `getPredefinedCursor` touches AWT only when one of these is first asked for.
private val ResizeColumnIcon by lazy { awt(AwtCursor.E_RESIZE_CURSOR) }
private val ResizeRowIcon by lazy { awt(AwtCursor.S_RESIZE_CURSOR) }
private val GrabbingIcon by lazy { awt(AwtCursor.MOVE_CURSOR) }

private fun awt(type: Int): PointerIcon = PointerIcon(AwtCursor.getPredefinedCursor(type))
