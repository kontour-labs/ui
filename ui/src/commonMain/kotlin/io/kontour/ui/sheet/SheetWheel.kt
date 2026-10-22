package io.kontour.ui.sheet

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A wheel or a trackpad over a sheet scrolls what is in it, and only at the end
 * of that does it open the sheet further.
 *
 * Reported from a desktop, 2026-10-21: *"if you have scrollable content in a
 * sheet and the sheet is half-height … scrolling on desktop will make the sheet
 * full height. i don't want that, i want it to just scroll"*. The phone's rule —
 * dragging the content up opens the sheet first, then scrolls it — is
 * [SheetState.nestedScrollConnection]'s, and a wheel's notches reach that
 * connection as `UserInput`, the same as a finger's drag. So every notch went to
 * the sheet until it was at its top detent, and the list moved only after.
 *
 * Two things here, on the two passes of each scroll event:
 *
 * - **Before the content sees it**, the sheet's finger is told a wheel is
 *   turning ([SheetFinger.wheel]), and the connection leaves the scroll to the
 *   content until the next press. Nothing in a scroll's own dispatch says what
 *   made it: a notch arrives at the connection a frame or two after its event,
 *   as an animation's steps.
 * - **After the content**, a notch down that nothing consumed — the list at
 *   its end, or no list at all — opens the sheet a detent, while what it holds
 *   runs on below the screen. A sheet's content is as tall as the window at
 *   every detent and the sheet slides it down, so at `Half` the last of a list
 *   is under the screen's edge, and with the wheel no longer opening the sheet
 *   first nothing else would ever show it. A notch up at the content's top
 *   does nothing: a wheel neither closes a sheet nor drops it.
 *
 * A trackpad on a Mac pans rather than scrolls — `PanStart`, `PanMove`,
 * `PanEnd`, with no press — and a pan reaches the connection as a finger's drag
 * does, so it is told as a wheel is, and its moves past the end open the sheet
 * as notches do. Found in the 0.5.0 review, 2026-10-21: listening for scroll
 * events alone, the fix above held for a mouse's wheel and not for the MacBook
 * trackpad the report came from, which still dragged the sheet.
 *
 * Not a drag, so no haptic ticks and nothing [SheetState.draggedByHand] says;
 * an open the hand asked for by turning a wheel is an `animateTo`, as a tap on a
 * button that opens the sheet is.
 */
internal fun Modifier.sheetWheel(state: SheetState): Modifier = pointerInput(state) {
    coroutineScope {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type !in Turns) continue
                state.finger.wheel()
                // The same event, once everything inside the sheet has had it.
                val after = awaitPointerEvent(PointerEventPass.Main)
                if (after.type != PointerEventType.Scroll && after.type != PointerEventType.PanMove) continue
                val change = after.changes.firstOrNull() ?: continue
                if (change.isConsumed) continue
                val delta = if (after.type == PointerEventType.Scroll) change.scrollDelta else change.panOffset
                // Down the screen, and mostly so: a carousel's sideways scroll
                // is the carousel's, consumed or not.
                if (delta.y <= 0f || abs(delta.y) < abs(delta.x)) continue
                if (state.isMoving) {
                    // Already on its way: the notch is the sheet's, and not the
                    // page's behind it, but it asks for nothing more.
                    if (state.cutOff()) change.consume()
                    continue
                }
                val above = state.detentAbove() ?: continue
                if (!state.cutOff()) continue
                change.consume()
                launch { state.animateTo(above) }
            }
        }
    }
}

/** What a wheel or a trackpad sends over the sheet: a mouse's notches, or a trackpad's pan. */
private val Turns = setOf(
    PointerEventType.Scroll,
    PointerEventType.PanStart,
    PointerEventType.PanMove,
    PointerEventType.PanEnd,
)
