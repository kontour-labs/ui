package io.kontour.ui.input

import androidx.compose.ui.input.pointer.PointerIcon
import android.view.PointerIcon as AndroidPointerIcon

/**
 * Android's own pointer shapes, for a mouse or a stylus on a tablet or a
 * Chromebook — which is when anyone sees one.
 *
 * `TYPE_NO_DROP` is precisely what [Cursor.NotAllowed] means: a drop refused. And
 * there is no background busy: `TYPE_WAIT` says the app has stopped, the same
 * untrue promise as AWT's hourglass, so [Cursor.Progress] shows the arrow.
 */
internal actual fun platformPointerIcon(cursor: Cursor): PointerIcon? = when (cursor) {
    Cursor.ResizeColumn -> PointerIcon(AndroidPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
    Cursor.ResizeRow -> PointerIcon(AndroidPointerIcon.TYPE_VERTICAL_DOUBLE_ARROW)
    Cursor.Grab -> PointerIcon(AndroidPointerIcon.TYPE_GRAB)
    Cursor.Grabbing -> PointerIcon(AndroidPointerIcon.TYPE_GRABBING)
    Cursor.NotAllowed -> PointerIcon(AndroidPointerIcon.TYPE_NO_DROP)
    Cursor.Progress -> null
    // Compose's own, the same on every platform.
    Cursor.Default, Cursor.Pointer, Cursor.Text, Cursor.Crosshair -> null
}
