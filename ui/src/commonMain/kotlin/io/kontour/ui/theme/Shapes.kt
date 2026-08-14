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
 */
@Immutable
data class Shapes(
    val extraSmall: CornerBasedShape = RoundedCornerShape(4.dp),
    val small: CornerBasedShape = RoundedCornerShape(8.dp),
    val medium: CornerBasedShape = RoundedCornerShape(12.dp),
    val large: CornerBasedShape = RoundedCornerShape(16.dp),
    val extraLarge: CornerBasedShape = RoundedCornerShape(24.dp),
    val pill: CornerBasedShape = RoundedCornerShape(percent = 50),
)
