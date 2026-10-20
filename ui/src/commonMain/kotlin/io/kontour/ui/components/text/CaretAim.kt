package io.kontour.ui.components.text

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import kotlin.math.abs

/**
 * Where the caret goes for a press on a field's frame that is not on its text
 * — its leading icon, its padding, a list row's label: the nearer end of the
 * text, or in a field of several lines, the nearer end of the line level with
 * the press.
 *
 * The press only focused the field, and focus kept the selection it last had,
 * so the caret came back halfway through a word the reader had long since left:
 * *"if i tap my cursor like halfway into some text in a text field, then tap
 * out, then tap on the leading icon, it puts my cursor exactly where i left it.
 * i'd like it to show up on the side closest to where i tapped"*. A press on
 * the text itself is the input's, and puts the caret where it lands, as before.
 *
 * Written by the input as it is laid out, and read only when a press comes.
 */
internal class CaretAim {
    /** The input's text, as it was last laid out. */
    var layout: (() -> TextLayoutResult?)? = null

    /** Where the input is, to bring a press on the frame into its own coordinates. */
    var input: LayoutCoordinates? = null

    /** How far the input has scrolled its text: across on one line, down on several. */
    var scroll: ScrollState? = null

    var singleLine: Boolean = true

    /** Puts [state]'s caret where a press at [at] on [frame] sends it, if the field has been laid out. */
    fun place(state: TextFieldState, frame: LayoutCoordinates?, at: Offset) {
        val offset = offsetFor(state.text.length, frame, at) ?: return
        if (state.selection != TextRange(offset)) state.edit { selection = TextRange(offset) }
    }

    /** The caret's offset in a text [length] long for a press at [at] on [frame]: see [CaretAim]. */
    fun offsetFor(length: Int, frame: LayoutCoordinates?, at: Offset): Int? {
        val text = layout?.invoke() ?: return null
        val input = input?.takeIf { it.isAttached } ?: return null
        val local = frame?.takeIf { it.isAttached }?.let { input.localPositionOf(it, at) } ?: return null
        val scrolled = scroll?.value?.toFloat() ?: 0f
        val point = if (singleLine) local + Offset(scrolled, 0f) else local + Offset(0f, scrolled)
        fun nearer(start: Int, end: Int): Int {
            // By where each end is drawn, so a right-to-left line's start is on
            // the right, and a value set at the field's end has its start
            // nearer a label before it.
            val startX = text.getHorizontalPosition(start, usePrimaryDirection = true)
            val endX = text.getHorizontalPosition(end, usePrimaryDirection = true)
            return if (abs(point.x - startX) <= abs(point.x - endX)) start else end
        }
        if (singleLine || text.lineCount <= 1) {
            // The whole text's ends in the field's own terms, whatever its
            // output transformation draws in between.
            return if (nearer(0, text.layoutInput.text.length) == 0) 0 else length
        }
        val line = text.getLineForVerticalPosition(point.y)
        // Before a line's break, not after it, so the caret stays on the line.
        return nearer(text.getLineStart(line), text.getLineEnd(line, visibleEnd = true)).coerceIn(0, length)
    }
}

/** Where a field's frame was placed, to bring a press on it into the input's coordinates. */
internal class FrameSpot {
    var coordinates: LayoutCoordinates? = null
}
