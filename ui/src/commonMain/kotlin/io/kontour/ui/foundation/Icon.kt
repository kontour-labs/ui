package io.kontour.ui.foundation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import io.kontour.ui.theme.Theme

/**
 * Draws an icon, tinted to the surrounding content colour.
 *
 * The design system ships **no icon set**. Components take an [ImageVector] or
 * a [Painter], so the choice of icon library stays an application decision and
 * `:ui` does not drag a few hundred kilobytes of glyphs into every consumer.
 *
 * ```
 * Icon(MaterialSymbols.Rounded.Search, contentDescription = "Search")
 * Icon(MyIcons.Bus, contentDescription = null)   // decorative, next to a label
 * ```
 *
 * @param contentDescription What a screen reader announces. **Required, and
 *   nullable on purpose** — passing `null` is how you say "this is decorative",
 *   which is a claim worth making explicitly rather than by omission. Pass
 *   `null` when the icon sits beside a label that already says the same thing;
 *   pass a description when the icon is the only thing conveying meaning.
 * @param tint Defaults to [LocalContentColor], so an icon inside a [Surface]
 *   picks up a legible colour automatically. `Color.Unspecified` disables
 *   tinting entirely — for multi-colour artwork.
 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = Theme.sizing.iconMedium,
) {
    Icon(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        size = size,
    )
}

/** [Painter] overload — see the [ImageVector] version for the parameter contract. */
@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = Theme.sizing.iconMedium,
) {
    val resolvedTint = if (tint == Color.Unspecified) Theme.colors.content else tint
    val colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(resolvedTint)

    val semantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    } else {
        // No semantics at all, rather than an empty description: an icon with a
        // blank label is still a node a screen reader stops on.
        Modifier.clearAndSetSemantics {}
    }

    Box(modifier.then(semantics).size(size)) {
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = colorFilter,
            modifier = Modifier.size(size),
        )
    }
}
