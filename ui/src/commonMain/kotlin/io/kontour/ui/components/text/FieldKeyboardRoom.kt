package io.kontour.ui.components.text

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates

/**
 * Where a field in an overlay is, for asking to be seen above the keyboard
 * once it has stopped moving. See `FieldScaffold`.
 *
 * **Why only in an overlay.** A page's scroller is made shorter by the
 * keyboard — a scaffold pads by it, or the window is resized — so bringing a
 * field into the scroller's view is bringing it above the keyboard. A sheet's
 * scroller is not: the sheet hands the keyboard to its content as padding at
 * the end of the list, and the scroller stays the sheet's height, keyboard
 * included. A field behind the keyboard was in the scroller's view already, so
 * nothing scrolled. The field asks for itself and, under it, as much as the
 * keyboard covers of it; the padding is what gives the scroller room to move
 * that far.
 *
 * **Why once it has stopped.** A sheet arriving with the keyboard carries the
 * field up the screen. Measured while it moves, the field is under the
 * keyboard by however far the sheet still has to come, and the content was
 * scrolled by that much — content pushed up, as reported 2026-10-20, from
 * inside the sheet this time rather than by the window. So the field waits
 * until it is on the screen and where it was a frame ago.
 */
internal class FieldKeyboardRoom {
    var coordinates: LayoutCoordinates? = null

    private fun placed(): LayoutCoordinates? = coordinates?.takeIf { it.isAttached }

    /**
     * Waits a frame at a time until the field is on the screen and has not
     * moved since the frame before, for at most [StillFramesLimit] frames.
     */
    suspend fun awaitRest() {
        var last: Float? = null
        repeat(StillFramesLimit) {
            withFrameNanos { }
            val field = placed() ?: return@repeat
            val top = field.localToRoot(Offset.Zero).y
            val onScreen = top < field.findRootCoordinates().size.height
            if (onScreen && top == last) return
            last = top
        }
    }

    /**
     * The field's own bounds and, under them, as much as a [keyboard] that
     * many pixels tall covers of it. Null when the field is not placed, which
     * asks for the whole field.
     */
    fun boundsAbove(keyboard: Int): Rect? {
        val field = placed() ?: return null
        val bottom = field.localToRoot(Offset(0f, field.size.height.toFloat())).y
        val covered = keyboardCovers(bottom, field.findRootCoordinates().size.height.toFloat(), keyboard.toFloat())
        return Rect(0f, 0f, field.size.width.toFloat(), field.size.height + covered)
    }
}

/**
 * How far a keyboard [keyboard] pixels tall, at the foot of a window
 * [windowHeight] tall, covers something whose bottom edge is at [bottom].
 */
internal fun keyboardCovers(bottom: Float, windowHeight: Float, keyboard: Float): Float =
    (bottom - (windowHeight - keyboard)).coerceAtLeast(0f)

/** About a second and a half: longer than any overlay takes to arrive. */
private const val StillFramesLimit = 90
