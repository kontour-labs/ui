package io.kontour.ui.components.display

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import io.kontour.ui.foundation.Surface
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme

/** How a card separates itself from the page behind it. */
enum class CardVariant {
    /** Lifted by a shadow. The default; reads clearly on a busy or coloured ground. */
    Elevated,

    /** A hairline instead of a shadow. Quieter, and better when cards are stacked densely. */
    Outlined,

    /** A tinted fill, no shadow or border. For a card inside another surface. */
    Filled,
}

/**
 * A container that groups related content.
 *
 * ```
 * Card {
 *     Text("Perth Station", style = Theme.typography.titleMedium)
 *     Text("Platform 3", color = Theme.colors.contentMuted)
 * }
 *
 * Card(onClick = ::openStop) { … }   // whole card is one target
 * ```
 *
 * Passing [onClick] makes the whole card a single button — which is what you
 * want for a card that navigates somewhere. Do **not** then put other buttons
 * inside it: nested targets inside a clickable card are ambiguous to a pointer
 * and impossible to reach with a screen reader, because the outer node swallows
 * the inner ones. If the card needs its own actions, leave it non-clickable and
 * put a button in it.
 *
 * A clickable card does not shrink on press — see [io.kontour.ui.theme.Motion].
 * At card size the scale reads as the layout jolting rather than the control
 * responding, so the tonal wash carries the feedback alone.
 *
 * @param contentPadding Applied inside the card. Pass `PaddingValues(0.dp)` when
 *   the card holds something edge-to-edge, like an image or a list.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.Elevated,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = Theme.shapes.medium,
    color: Color = cardColorFor(variant),
    border: BorderStroke? = cardBorderFor(variant),
    shadow: Shadow = cardShadowFor(variant),
    contentPadding: PaddingValues = PaddingValues(Theme.spacing.md),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }

    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactions,
                    // pressScale = 1f: a card is too large to shrink convincingly.
                    indication = kontourIndication(shape, pressScale = 1f),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
            } else {
                Modifier
            }
        ),
        shape = shape,
        color = color,
        border = border,
        shadow = shadow,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
private fun cardColorFor(variant: CardVariant): Color = when (variant) {
    CardVariant.Elevated, CardVariant.Outlined -> Theme.colors.surface
    CardVariant.Filled -> Theme.colors.surfaceSunken
}

@Composable
private fun cardBorderFor(variant: CardVariant): BorderStroke? = when (variant) {
    CardVariant.Outlined -> BorderStroke(Theme.sizing.borderWidth, Theme.colors.outline)
    else -> null
}

@Composable
private fun cardShadowFor(variant: CardVariant): Shadow = when (variant) {
    CardVariant.Elevated -> Theme.elevation.low
    else -> Shadow.None
}
