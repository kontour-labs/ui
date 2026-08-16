package io.kontour.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * How the user is currently driving the interface.
 *
 * The same component should not behave identically to a finger, a mouse and a
 * keyboard, and the platform is a poor proxy for which one is in play — a
 * Chromebook is Android with a trackpad, an iPad has a pointer, a phone browser
 * is "web" but touch-first. So the design system tracks the *last used* input
 * and adapts:
 *
 * | | [Touch] | [Mouse] | [Keyboard] | [Stylus] |
 * |---|---|---|---|---|
 * | Minimum target | full 44–48dp | 24dp | n/a | full |
 * | Hover states | off | on | off | off |
 * | Focus ring | hidden | hidden | **shown** | hidden |
 * | Tooltips | long-press | hover | on focus | long-press |
 * | Scrollbars | overlay, fading | persistent | persistent | overlay |
 *
 * The focus-ring row is the important one. Showing a ring on every tap looks
 * broken; never showing one makes the app unusable by keyboard. Tracking
 * modality is what lets both be right.
 */
enum class InputModality {
    Touch,
    Mouse,
    Keyboard,
    Stylus,
    ;

    /** True when the pointer can hover — i.e. hover states are worth drawing. */
    val supportsHover: Boolean get() = this == Mouse

    /** True when a focus indicator should be visible. */
    val showsFocusRing: Boolean get() = this == Keyboard

    /** True when targets need to accommodate a fingertip. */
    val needsLargeTargets: Boolean get() = this == Touch || this == Stylus
}

/**
 * The active input modality.
 *
 * Defaults to [InputModality.Touch] — the conservative choice, since assuming
 * touch only costs a mouse user some extra padding, whereas assuming mouse
 * gives a touch user targets too small to hit.
 */
val LocalInputModality = staticCompositionLocalOf { InputModality.Touch }

/** Mutable holder behind [LocalInputModality]. Created by [rememberInputModalityState]. */
@Stable
class InputModalityState internal constructor(initial: InputModality) {
    var current: InputModality by mutableStateOf(initial)
        internal set
}

@Composable
fun rememberInputModalityState(
    initial: InputModality = InputModality.Touch,
): InputModalityState = remember { InputModalityState(initial) }

/**
 * Watches pointer and key events and keeps [state] up to date.
 *
 * Applied once by `KontourTheme` at the root of the composition; you should not
 * need to call it yourself.
 *
 * Pointer events are observed on [PointerEventPass.Initial] so the modality is
 * already correct by the time the component underneath handles the same event
 * and decides whether to draw a hover or a focus ring.
 */
fun Modifier.trackInputModality(state: InputModalityState): Modifier = this
    .pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pointerType = event.changes.firstOrNull()?.type ?: continue
                state.current = when (pointerType) {
                    PointerType.Touch -> InputModality.Touch
                    PointerType.Stylus, PointerType.Eraser -> InputModality.Stylus
                    else -> InputModality.Mouse
                }
            }
        }
    }
    .onPreviewKeyEvent { event ->
        // Only *traversal* keys imply keyboard-driven navigation. Typing into a
        // text field is not a reason to start painting focus rings across the
        // screen — the user is already looking at the caret.
        if (event.type == KeyEventType.KeyDown && event.key in TraversalKeys) {
            state.current = InputModality.Keyboard
        }
        false // never consume; this is an observer, not a handler
    }

private val TraversalKeys = setOf(
    Key.Tab,
    Key.DirectionUp,
    Key.DirectionDown,
    Key.DirectionLeft,
    Key.DirectionRight,
    Key.PageUp,
    Key.PageDown,
    Key.MoveHome,
    Key.MoveEnd,
)
