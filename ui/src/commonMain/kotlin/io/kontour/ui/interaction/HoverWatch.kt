package io.kontour.ui.interaction

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Whether the pointer event being handled right now is a pointer passing over
 * something, with no button or finger down. **A hover plays no haptic.**
 *
 * Reported 2026-10-21 for 0.5.0 from a MacBook Pro's trackpad: *"not really a
 * fan of how hovering things on desktop causes the mac trackpad to rumble"*. A
 * Mac plays its haptics on the trackpad, and only while a finger is on it —
 * which, moving the pointer, it is. So a chart that chose the place under a
 * resting pointer and ticked for it, as it ticks for a finger scrubbed across
 * it, ticked under a finger that was only on its way somewhere else: a tick a
 * place, across every chart the pointer crossed, felt as a rumble. A haptic
 * reports something the hand did, and passing over is not doing anything.
 *
 * **One rule, here, rather than a check at each caller.** The outermost theme
 * watches every pointer event at its root ([watchHover]), on its way in — the
 * initial pass, which reaches the root first — and on its way out — the main
 * pass, which reaches the root last — and says [hovering] between the two for an
 * event that is a hover. What a component does with that event happens in
 * between, so every helper that plays — [DetentTicker], [TapFeedback],
 * [ToggleFeedback], a drag's texture, a hold, a long press — asks here first and
 * a caller passes nothing: a chart of `:ui-charts`, which ticks from its hover
 * as from a drag, is quiet on a hover without a change of its own.
 *
 * A press, a drag with the button held, a click's release, a scroll and a
 * trackpad's pan are not hovers, and play as they did: a slider dragged on a
 * Mac's trackpad ticks there as it does under a finger on a phone.
 *
 * Outside an event — an animation landing, a key pressed — and in a popup's
 * window, which the root does not see, it says not hovering. A haptic it cannot
 * place plays rather than a drag's going quiet.
 */
@Stable
internal class HoverWatch {
    /** True only while a hover's event is being handled. */
    var hovering: Boolean = false
        internal set

    companion object {
        /** Never hovering: for a component outside a theme, and the helpers' default. */
        val Never = HoverWatch()
    }
}

/**
 * Keeps [watch] told whether the event in hand is a hover. Applied once, by the
 * outermost `KontourTheme`, beside the input-modality tracker.
 */
internal fun Modifier.watchHover(watch: HoverWatch): Modifier = pointerInput(watch) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            watch.hovering = event.isHover()
            try {
                // The same event's main pass, which comes back to the root after
                // every component under it has had it.
                awaitPointerEvent(PointerEventPass.Main)
            } finally {
                watch.hovering = false
            }
        }
    }
}

/**
 * A pointer moving, arriving or leaving with nothing pressed. A finger is always
 * pressed; a mouse, a trackpad's pointer and a pen held over the screen are not.
 */
internal fun PointerEvent.isHover(): Boolean =
    (type == PointerEventType.Move || type == PointerEventType.Enter || type == PointerEventType.Exit) &&
        changes.none { it.pressed }

/** The watch in force: the outermost theme's, or [HoverWatch.Never]. */
internal val LocalHoverWatch = staticCompositionLocalOf { HoverWatch.Never }
