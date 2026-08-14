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
)
