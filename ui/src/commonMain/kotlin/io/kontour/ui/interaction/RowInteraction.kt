package io.kontour.ui.interaction

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The interactions of the row a control is sitting in, when the row owns the tap.
 *
 * A `SelectionRow` puts the click target on the whole row and hands its control
 * `onCheckedChange = null` — the control is there to *show* state, and the row
 * decides. But showing state includes showing the press: a `Switch` stretches its
 * thumb while held, a `Checkbox` and a `RadioButton` respond too, and all three
 * read that from their own `interactionSource`. Given a null callback they build
 * a source nobody ever pushes anything into, so tapping the row moved the value
 * and the control never flinched.
 *
 * The row provides its source here; a control with no callback of its own falls
 * back to it. Nothing at the call site changes — a control that already has an
 * interaction source, or its own callback, ignores this entirely.
 *
 * `InteractionSource`, not `MutableInteractionSource`: this is for *reading*
 * somebody else's presses. A control that emitted into the row's source would be
 * reporting presses on behalf of a target it does not own.
 */
val LocalRowInteractionSource = staticCompositionLocalOf<InteractionSource?> { null }
