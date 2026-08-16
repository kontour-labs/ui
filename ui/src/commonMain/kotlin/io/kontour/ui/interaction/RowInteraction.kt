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

/**
 * The toggle of the row a control is sitting in, when the row owns the tap.
 *
 * The companion to [LocalRowInteractionSource], and it exists for the same
 * reason one step further on: a `Switch` inside a `SelectionRow` should be
 * *draggable*, and a drag is not a tap the row can own. The row's click target
 * covers the whole row and a thumb dragged across its track means one specific
 * thing — but the switch was handed `onCheckedChange = null` and had nothing to
 * call.
 *
 * So the row publishes its own toggle here and the switch drags against it. The
 * tap still belongs to the row, which is the part that must not change: a
 * `SelectionRow` where only the switch is tappable is a row with a 48dp target
 * pretending to be a 360dp one.
 *
 * `null` for a row that is not toggleable — a radio row can only ever set true,
 * and there is nothing for a drag to express.
 */
val LocalRowToggle = staticCompositionLocalOf<((Boolean) -> Unit)?> { null }
