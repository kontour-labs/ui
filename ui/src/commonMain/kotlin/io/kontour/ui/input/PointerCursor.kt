package io.kontour.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * Sets the mouse cursor over this component.
 *
 * ```kotlin
 * Modifier
 *     .pointerCursor(enabled = enabled)
 *     .focusRing(interactions, shape)
 *     .clip(shape)
 *     .clickable(interactionSource = interactions, indication = …) { … }
 * ```
 *
 * ### Not part of [focusRing], which is the tidy-looking mistake
 *
 * Almost every component that wants a cursor already asks for a focus ring, so
 * folding one into the other would have been one edit instead of thirty. But
 * [focusRing] returns `this` unchanged unless the element is *focused* — a
 * cursor is a **hover** affordance and has nothing to do with focus, so it
 * would appear only on elements the keyboard had already reached. Removing that
 * early return to make room for it would put a node on every one of those call
 * sites on every platform, including the touch ones where a cursor is
 * meaningless.
 *
 * ### Gated on the modality, like every other hover affordance here
 *
 * `pointerHoverIcon` is inert without a pointer, so this gate buys a node
 * rather than correctness — the same trade `kontourIndication` already makes
 * for its hover wash. On a hybrid device the first mouse movement is what sets
 * the modality, so the icon appears from the second event rather than the
 * first, which is invisible: the movement that reveals a cursor is the one that
 * created it.
 *
 * ### There are four cursors, and that is the whole set
 *
 * Compose Multiplatform's common [PointerIcon] offers [PointerIcon.Default],
 * [PointerIcon.Crosshair], [PointerIcon.Text] and [PointerIcon.Hand]. There is
 * no resize cursor for a pane splitter, no grab cursor for a scrollbar thumb,
 * and no way to add one without a per-target `expect`/`actual` — desktop can
 * build a [PointerIcon] from an AWT `Cursor` and no other target we ship can.
 * So a component that wants a shape outside that set gets the closest of the
 * four rather than a bespoke one, and the library says so rather than
 * pretending otherwise.
 *
 * @param icon Which of the four. [PointerIcon.Hand] for anything that responds
 *   to a click, [PointerIcon.Text] over editable text, and [PointerIcon.Default]
 *   where an enclosing component sets a hand this one should not inherit.
 * @param enabled Pass the component's own `enabled` through. A disabled control
 *   that still shows a hand is telling the user it will answer a click.
 */
@Composable
fun Modifier.pointerCursor(
    icon: PointerIcon = PointerIcon.Hand,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || !LocalInputModality.current.supportsHover) return this
    return pointerHoverIcon(icon)
}
