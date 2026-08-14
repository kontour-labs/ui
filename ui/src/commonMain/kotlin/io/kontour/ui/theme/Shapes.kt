package io.kontour.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * The corner-radius scale.
 *
 * Tighter than the marketing site on purpose. Uber's structural discipline
 * shows up here more than anywhere else: controls stay near-square so they read
 * as mechanical, and roundness is spent on the two places it earns its keep —
 * [pill] for navigation and chips, [extraLarge] for sheets.
 *
 * | Token | Radius | Used by |
 * |---|---|---|
 * | [extraSmall] | 4dp | Badges, tags, inline code |
 * | [small] | 8dp | Buttons, inputs, checkboxes |
 * | [medium] | 12dp | Cards, list groups, menus |
 * | [large] | 16dp | Dialogs, large cards |
 * | [extraLarge] | 24dp | Bottom sheets, hero panels |
 * | [pill] | fully round | Nav bars, chips, avatars, FABs |
 * | [sheet] | 24dp top only | Bottom sheets |
 * | [sideSheet] | 24dp leading only | Side sheets |
 */
@Immutable
data class Shapes(
    val extraSmall: CornerBasedShape = RoundedCornerShape(4.dp),
    val small: CornerBasedShape = RoundedCornerShape(8.dp),
    val medium: CornerBasedShape = RoundedCornerShape(12.dp),
    val large: CornerBasedShape = RoundedCornerShape(16.dp),
    val extraLarge: CornerBasedShape = RoundedCornerShape(24.dp),
    val pill: CornerBasedShape = RoundedCornerShape(percent = 50),

    /**
     * Bottom sheets. Rounded at the top only — the bottom edge sits against the
     * bottom of the window, and rounding a corner that is off-screen just
     * leaves a sliver of background showing through at full expansion.
     */
    val sheet: CornerBasedShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),

    /**
     * Side sheets and rails, as they appear on the *trailing* edge: rounded on
     * the side facing the content, square against the window edge. Mirror it
     * with [mirrorHorizontally] for a leading-edge sheet.
     */
    val sideSheet: CornerBasedShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
)

/**
 * Swaps a shape's leading and trailing corners.
 *
 * For a panel that can appear on either edge. The rounded side should always be
 * the one facing the content — a rounded corner against the window edge leaves a
 * sliver of background showing through, and a square corner facing the content
 * makes the panel look welded on.
 */
fun CornerBasedShape.mirrorHorizontally(): CornerBasedShape = copy(
    topStart = topEnd,
    topEnd = topStart,
    bottomStart = bottomEnd,
    bottomEnd = bottomStart,
)
