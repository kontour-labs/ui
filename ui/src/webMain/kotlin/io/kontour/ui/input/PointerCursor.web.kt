package io.kontour.ui.input

import androidx.compose.ui.input.pointer.PointerIcon

/**
 * Nothing yet, and not for want of a shape.
 *
 * Compose for the web has exactly the class this needs — `BrowserCursor`, a
 * [PointerIcon] made from a CSS `cursor` keyword, which would give every [Cursor]
 * its own shape — and keeps it `internal`. A library cannot construct one, so the
 * web draws the nearest of the four common shapes: the hand over a scrollbar thumb
 * and a pane splitter, which is what they showed before they had shapes of their
 * own. When Compose makes it public, this `when` is the whole of the change:
 *
 * ```
 * ResizeColumn → "col-resize"   ResizeRow → "row-resize"
 * Grab → "grab"                 Grabbing → "grabbing"
 * NotAllowed → "not-allowed"    Progress → "progress"
 * ```
 *
 * Setting the canvas's CSS cursor directly was the alternative, and it would lose:
 * Compose writes that same property on every hover change, so the two would take
 * turns.
 */
internal actual fun platformPointerIcon(cursor: Cursor): PointerIcon? = null
