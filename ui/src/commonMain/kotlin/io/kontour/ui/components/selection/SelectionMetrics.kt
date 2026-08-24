package io.kontour.ui.components.selection

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import io.kontour.ui.interaction.LocalRowInteractionSource
import io.kontour.ui.theme.Theme

/**
 * How thick a checkbox, radio or switch draws its outline.
 *
 * ### Why disabled is thinner rather than only paler
 *
 * A disabled control used to differ from an unchecked one by a single colour
 * substitution — `outlineStrong` to `contentDisabled` — which in light mode is
 * `#8A8A8A` against `#A3A3A3`. About 1.4:1, and imperceptible at 20dp. Two
 * states that mean completely different things ("off, press me" and
 * "unavailable, do not bother") were being told apart by a shade of grey that
 * nobody can see.
 *
 * Thinning the stroke gives it a second cue that survives greyscale, low vision
 * and a bad screen: off is a solid outline, unavailable is a fine one. The same
 * reason the selection indicator travels rather than just tinting — a state
 * conveyed by colour alone is a state some people cannot read.
 *
 * All three controls read this so they stay a family. A checkbox that thinned
 * and a switch that did not would read as two different mechanisms.
 */
@Composable
@ReadOnlyComposable
internal fun selectionStroke(enabled: Boolean): Dp =
    if (enabled) Theme.sizing.borderWidthStrong else Theme.sizing.borderWidth

/**
 * How far a selection control moves toward its *next* state while pressed.
 *
 * A switch's thumb has always stretched under the finger, before the value
 * commits — that is what makes it feel like a mechanism with give rather than a
 * picture that changes. A checkbox and a radio button did nothing at all until
 * the press was released, so the same tap read as responsive on one control and
 * dead on the other two.
 *
 * A third of the way. Far enough to be a clear answer to the finger, not so far
 * that a press-and-slide-off looks like it did something — that gesture is how
 * people cancel a tap, and the mark has to be able to come back.
 */
internal const val SelectionPressPreview: Float = 0.33f

/**
 * The interactions to *read* for a control that may be a passenger in a row.
 *
 * A control inside a `SelectionRow` is handed a null callback because the row
 * owns the tap, so its own source never sees a press. The switch has borrowed
 * the row's for a while; the checkbox and the radio button did not, which is why
 * their `LocalRowInteractionSource` mention has been aspirational since it was
 * written.
 *
 * Only when the control is genuinely a passenger: an explicit source or a
 * callback of its own both mean it is the target and owns its own presses.
 */
@Composable
internal fun pressSourceFor(
    own: MutableInteractionSource,
    explicit: MutableInteractionSource?,
    hasCallback: Boolean,
): InteractionSource {
    val row = LocalRowInteractionSource.current
    return if (!hasCallback && explicit == null && row != null) row else own
}
