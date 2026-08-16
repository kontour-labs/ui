package io.kontour.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.kontour.ui.theme.Theme

/**
 * A horizontal rule.
 *
 * ```
 * Column {
 *     ListItem { +… }
 *     HorizontalDivider(startIndent = Theme.spacing.md)   // aligned to the text
 *     ListItem { +… }
 * }
 * ```
 *
 * Before reaching for one, consider whether spacing would do the job. A list
 * whose rows are separated by whitespace usually reads better than one ruled
 * into a grid; dividers earn their place when rows are dense enough that the
 * eye needs help tracking across them.
 *
 * Uses [io.kontour.ui.theme.ColorScheme.outline], which is decorative and
 * deliberately below the 3:1 that a *control* boundary needs — a divider that
 * meets control contrast looks like a border.
 *
 * @param startIndent Inset from the leading edge, for aligning the rule with
 *   text rather than with the container. Respects RTL.
 */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = Theme.sizing.dividerThickness,
    color: Color = Theme.colors.outline,
    startIndent: Dp = Dp.Hairline,
    endIndent: Dp = Dp.Hairline,
) {
    Box(
        modifier
            // Purely presentational: a screen reader stopping on a rule is noise.
            .semantics { }
            .fillMaxWidth()
            .padding(start = startIndent, end = endIndent)
            .height(thickness)
            .background(color)
    )
}

/**
 * A vertical rule, for separating side-by-side content.
 *
 * Needs a bounded height from its parent — inside a `Row`, that usually means
 * `Modifier.height(IntrinsicSize.Min)` on the row, or an explicit height here.
 */
@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = Theme.sizing.dividerThickness,
    color: Color = Theme.colors.outline,
    topIndent: Dp = Dp.Hairline,
    bottomIndent: Dp = Dp.Hairline,
) {
    Box(
        modifier
            .semantics { }
            .fillMaxHeight()
            .padding(top = topIndent, bottom = bottomIndent)
            .width(thickness)
            .background(color)
    )
}
