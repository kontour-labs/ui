package io.kontour.ui.interaction

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.theme.Theme

/**
 * Makes anything pressable the way the library's own controls are.
 *
 * ```kotlin
 * Box(
 *     Modifier
 *         .pressable(
 *             onClick = { open(stop) },
 *             onLongClick = { pin(stop) },
 *             onLongClickLabel = "Pin",
 *         )
 *         .clip(Theme.shapes.container)
 *         .background(Theme.colours.surfaceSunken)
 *         .padding(Theme.spacing.md),
 * ) { StopTile(stop) }
 * ```
 *
 * A `clickable` gets a press; this gets the whole of what pressing something in
 * the library is, in one call, so a component of your own sits among the
 * library's without being the one that feels different:
 *
 * - **the press** — the tonal wash in [shape] and the slight shrink every button
 *   has, with the same floor under a quick tap so it is seen at all, and a hover
 *   wash where there is a pointer to hover;
 * - **the keyboard** — the focus ring, drawn round [shape] and thickened at high
 *   contrast;
 * - **the pointer** — a hand over it on a desktop or the web;
 * - **the target** — at least 48dp to a finger however small it is drawn, the
 *   size every control in the library reserves;
 * - **the long press** — [onLongClick], with the library's long-press haptic
 *   (only ours: the platform's own is turned off, or it would buzz twice), and
 *   [onDoubleClick] if you want one;
 * - **what is said** — [role] and the labels, so a screen reader announces a
 *   button, and names the long press as an action of its own.
 *
 * A plain press makes no haptic, as the library's buttons make none: a press is
 * the least surprising thing a screen does, and what it opens is the answer.
 *
 * **Put it first**, before the `clip` and the ground, as the example does. What
 * comes after it is what it presses: the shrink takes the ground with the
 * content, and the wash is drawn over both in [shape]. A `clip` before it would
 * cut off the focus ring, which is drawn just outside.
 *
 * @param shape The component's own shape: the wash and the focus ring follow it.
 *   Pass the shape it is clipped to, or both square off its corners.
 * @param role What a screen reader calls it. Null for none, where the content
 *   says it already.
 * @param onLongClickLabel What a screen reader calls the long press — "Pin",
 *   "More options". Without one it is announced as a bare long press.
 * @param pressScale How far it shrinks under a press. `1f` for anything large —
 *   a card, a whole row — where the surface moving reads as a flinch.
 * @param minimumTouchTarget Whether it reserves the 48dp target. Off only where
 *   something around it already does.
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    shape: Shape = Theme.shapes.container,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onDoubleClick: (() -> Unit)? = null,
    pressScale: Float = IndicationDefaults.PressScale,
    minimumTouchTarget: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val longPressed = rememberLongPressFeedback()
    val longClick = onLongClick?.let { held ->
        {
            longPressed()
            held()
        }
    }
    return this
        .then(if (minimumTouchTarget) Modifier.minimumTouchTarget() else Modifier)
        .focusRing(interactions, shape, enabled = enabled)
        .pointerCursor(enabled = enabled)
        .combinedClickable(
            interactionSource = interactions,
            indication = kontourIndication(shape, pressScale),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onLongClickLabel = onLongClickLabel,
            onLongClick = longClick,
            onDoubleClick = onDoubleClick,
            // The library's long press, through the feedback everything else
            // uses; the platform's own on top of it would be a second buzz.
            hapticFeedbackEnabled = false,
            onClick = onClick,
        )
}
