package io.kontour.ui.components.action

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import io.kontour.ui.a11y.LocalTouchTargetOwnedByParent
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.overlay.AnchoredDropdownMenu
import io.kontour.ui.overlay.MenuScope
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.anchorBounds
import io.kontour.ui.motion.ChevronTurn
import io.kontour.ui.theme.Theme

/**
 * One action, with its variants a tap away.
 *
 * ```
 * var open by remember { mutableStateOf(false) }
 *
 * SplitButton(
 *     onClick = ::save,
 *     expanded = open,
 *     onExpandedChange = { open = it },
 *     menuContentDescription = "Other save options",
 *     menu = {
 *         item("Save and close", onClick = ::saveAndClose)
 *         item("Save a copy", onClick = ::saveCopy)
 *     },
 * ) {
 *     +"Save"
 * }
 * ```
 *
 * The left half runs the **default** action immediately; the right half opens
 * the rest. That division is the whole component, and it is what separates it
 * from a `Button` that opens a menu: here the common case costs one tap and
 * never shows a list.
 *
 * ### When it is the wrong control
 *
 * When there is no default. A split button whose main half opens the menu too
 * is a wide chevron, and a plain [Button] with a
 * [io.kontour.ui.overlay.DropdownMenu] says what it means. And when the
 * alternatives are *equal* — three ways of doing a thing, none of them the usual
 * one — use a [ButtonGroup], which does not claim one of them is the answer.
 *
 * ### The seam
 *
 * The two halves sit flush with a hairline between them and only the outside
 * corners round — the same treatment [ButtonGroup] gives its items, from the
 * same `ButtonGroupPosition.shape`, because it is the same idea: separate
 * targets that read as one control.
 *
 * The pair also owns the touch target between them, for the reason
 * [ButtonGroup] does: left to itself each half reserves the platform minimum
 * and centres its visual inside it, and the reserved slack lands *in the seam*
 * — on Android a 1dp join draws at 9dp and stops reading as a join. The row
 * carries the minimum height instead, so a full-height half is still something
 * a finger can hit.
 *
 * @param onClick The default action. Runs on the main half, without opening
 *   anything.
 * @param menuContentDescription Names the chevron half, which has no text of its
 *   own. Required, and separate from the label: a screen reader that reads
 *   "Save, Save" for the two halves has described neither.
 * @param menu The alternatives. Same `MenuScope` a
 *   [io.kontour.ui.overlay.DropdownMenu] takes.
 * @param content The main half's label, in the same `+` vocabulary a [Button]
 *   takes.
 */
@Composable
fun SplitButton(
    onClick: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    chevron: ImageVector = SystemIcons.ChevronDown,
    shape: CornerBasedShape = Theme.shapes.control,
    interactionSource: MutableInteractionSource? = null,
    menu: @Composable MenuScope.() -> Unit,
    content: @Composable RowContentScope.() -> Unit,
) {
    val motion = Theme.motion
    var anchor by remember { mutableStateOf<Rect?>(null) }

    CompositionLocalProvider(LocalTouchTargetOwnedByParent provides true) {
        Row(
            modifier = modifier
                .semantics { isTraversalGroup = true }
                .defaultMinSize(minHeight = Theme.sizing.minTouchTarget)
                // On the pair rather than on the chevron half, so the menu lines
                // up with the whole control. Anchoring it to the chevron alone
                // hangs a 180dp menu off a 40dp button and it reads as belonging
                // to something else.
                .anchorBounds { anchor = it },
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.Seam),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                variant = variant,
                size = size,
                shape = ButtonGroupPosition.First.shape(shape),
                interactionSource = interactionSource,
                content = content,
            )
            IconButton(
                icon = chevron,
                contentDescription = menuContentDescription,
                onClick = { onExpandedChange(!expanded) },
                enabled = enabled,
                variant = variant,
                size = size,
                shape = ButtonGroupPosition.Last.shape(shape),
                // The button's own parameter, not a `graphicsLayer` on the
                // outside: this half is the *end* of the pair, so its container
                // has two rounded corners and two square ones. Turning the
                // container over puts the round pair on the seam.
                // A target, not an angle: `IconButton` springs to it. This
                // used to be a spring of its own feeding a second one, so the
                // chevron here turned through two of them in series and
                // arrived after every other arrow in the library.
                rotation = if (expanded) ChevronTurn else 0f,
            )
        }
    }

    AnchoredDropdownMenu(
        visible = expanded,
        anchor = anchor,
        onDismissRequest = { onExpandedChange(false) },
        side = OverlaySide.Bottom,
        alignment = OverlayAlignment.End,
        content = menu,
    )
}
