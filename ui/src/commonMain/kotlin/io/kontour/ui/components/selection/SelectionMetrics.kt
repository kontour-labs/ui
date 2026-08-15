package io.kontour.ui.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
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
