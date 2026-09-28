@file:OptIn(ExperimentalComposeUiApi::class)

package io.kontour.ui.input

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.fromKeyword

/**
 * The browser's own cursors, by their CSS keyword.
 *
 * Every [Cursor] has one, so the web draws the real shape for all ten: `col-resize`
 * over a pane splitter, `grab` over a scrollbar thumb, `progress` over work that
 * leaves the page usable.
 *
 * This file used to return null for everything, on the strength of a note saying
 * Compose kept its CSS cursor `internal`. The class is internal; the way to make
 * one is not. `PointerIcon.fromKeyword` is public, and was found by reading the
 * published klib rather than the source a note had been written from — the same
 * mistake, once removed, that the common file's post-mortem is about.
 *
 * `fromKeyword` is marked experimental, and the opt-in is taken here and nowhere
 * else. If a Compose upgrade renames it, this file stops compiling rather than
 * the web quietly losing its cursors, and the common fallback to the nearest of
 * the four shapes is still there to fall back to.
 */
internal actual fun platformPointerIcon(cursor: Cursor): PointerIcon? = when (cursor) {
    Cursor.ResizeColumn -> ResizeColumnIcon
    Cursor.ResizeRow -> ResizeRowIcon
    Cursor.Grab -> GrabIcon
    Cursor.Grabbing -> GrabbingIcon
    Cursor.NotAllowed -> NotAllowedIcon
    Cursor.Progress -> ProgressIcon
    // Compose's own, the same on every platform.
    Cursor.Default, Cursor.Pointer, Cursor.Text, Cursor.Crosshair -> null
}

// Made once rather than per call: `pointerCursor` runs in composition.
private val ResizeColumnIcon by lazy { PointerIcon.fromKeyword("col-resize") }
private val ResizeRowIcon by lazy { PointerIcon.fromKeyword("row-resize") }
private val GrabIcon by lazy { PointerIcon.fromKeyword("grab") }
private val GrabbingIcon by lazy { PointerIcon.fromKeyword("grabbing") }
private val NotAllowedIcon by lazy { PointerIcon.fromKeyword("not-allowed") }
private val ProgressIcon by lazy { PointerIcon.fromKeyword("progress") }
