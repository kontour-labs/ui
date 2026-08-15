package io.kontour.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.platform.platformMinTouchTarget

/**
 * Fixed measurements: how big a control is, how thick a line is, how much room
 * a finger needs.
 *
 * @property minTouchTarget The smallest area that may receive a tap. Defaults to
 *   the host platform's guideline — 48dp on Android, 44pt on iOS, 24dp where a
 *   mouse is the primary pointer. This is the *interactive* bound, not the
 *   visual one: a 20dp checkbox still gets a 48dp hit area, it does not get
 *   drawn 48dp wide. Enforced by `Modifier.minimumTouchTarget()`.
 */
@Immutable
data class Sizing(
    val minTouchTarget: Dp = platformMinTouchTarget,

    // Icons. Three sizes; anything else is a one-off and should be justified.
    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 20.dp,
    val iconLarge: Dp = 24.dp,

    // Control heights, shared by buttons, inputs and selects so a row of mixed
    // controls lines up without per-call-site padding.
    val controlHeightXSmall: Dp = 28.dp,
    val controlHeightSmall: Dp = 36.dp,
    val controlHeightMedium: Dp = 44.dp,
    val controlHeightLarge: Dp = 52.dp,
    val controlHeightXLarge: Dp = 60.dp,

    // Lines.
    val borderWidth: Dp = 1.dp,
    val borderWidthStrong: Dp = 2.dp,
    val dividerThickness: Dp = 1.dp,

    /** Focus ring stroke. 2dp is the thinnest that stays visible at 200% zoom. */
    val focusRingWidth: Dp = 2.dp,
    /** Gap between the component's edge and its focus ring. */
    val focusRingOffset: Dp = 2.dp,

    /**
     * The travelling bar that marks the selected item in a tab bar, nav bar,
     * rail or drawer.
     *
     * A token rather than a per-component constant because four components draw
     * it, and a selection marker that is 3dp in one surface and 2dp in another
     * reads as two different mechanisms.
     */
    val selectionIndicator: Dp = 3.dp,
)

/**
 * [Sizing] for a contrast tier.
 *
 * Every line in the system is here, and at [ContrastLevel.High] every line gets
 * thicker. This is the cheapest half of the high-contrast work by a distance: a
 * component that reads `Theme.sizing.borderWidth` responds without being touched,
 * and there are far more of those than there are components with a border to add.
 *
 * It used to be `remember { Sizing() }` with no key at all, so a user who asked
 * for high contrast got a scheme with a darker `outline` drawn at exactly the
 * same 1dp — the *colour* of every edge changed and the *weight* of none of them
 * did. A hairline is a hairline whatever colour it is.
 *
 * The steps are one notch each rather than a doubling. A 2dp divider reads as
 * deliberate; a 3dp one reads as a rule, and a list of them reads as a fence.
 */
fun kontourSizing(contrast: ContrastLevel): Sizing = when (contrast) {
    ContrastLevel.Standard -> Sizing()
    ContrastLevel.High -> Sizing(
        borderWidth = 2.dp,
        borderWidthStrong = 3.dp,
        dividerThickness = 2.dp,
        focusRingWidth = 3.dp,
        selectionIndicator = 4.dp,
    )
}
