package io.kontour.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.ProvideContentColor
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.theme.Theme

/**
 * A key cap — one key, or one accelerator.
 *
 * ```kotlin
 * Kbd { +"⌘S" }
 * Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
 *     Kbd { +"Ctrl" }
 *     Kbd { +"K" }
 * }
 * ```
 *
 * Bordered and monospaced, because both are doing a job. The border says *this
 * is a thing you press* rather than a word in the sentence around it, which is
 * the whole reason `<kbd>` exists in HTML. The monospace says the glyphs are
 * literal: a lowercase `l` and a `1` have to be told apart in something the user
 * is about to type.
 *
 * Announced as nothing. A shortcut beside a menu item is a hint for people
 * looking at a keyboard, and a screen reader reading "Share, command S" after
 * every row is noise — the row already announces what it does. When a shortcut
 * is the *only* thing conveying an action, put it in the label.
 *
 * @param content One key or one accelerator. Multi-key chords read better as
 *   several of these in a row with a gap than as one long cap. Kept to one line:
 *   a key cap that wraps is not a key cap.
 */
@Composable
fun Kbd(
    modifier: Modifier = Modifier,
    color: Color = Theme.colors.contentMuted,
    content: @Composable ContentScope.() -> Unit,
) {
    val shape = Theme.shapes.extraSmall
    Row(
        modifier = modifier
            .clearAndSetSemantics { }
            .defaultMinSize(minWidth = KbdDefaults.MinWidth, minHeight = KbdDefaults.Height)
            .clip(shape)
            .background(Theme.colors.surfaceSunken, shape)
            .border(Theme.sizing.borderWidth, Theme.colors.outline, shape)
            .padding(horizontal = Theme.spacing.xxs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideTextStyle(Theme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)) {
            ProvideContentColor(color) {
                ContentSlot(iconSize = Theme.sizing.iconSmall, maxLines = 1, content = content)
            }
        }
    }
}

object KbdDefaults {
    /** Square-ish for a single character, so a row of caps lines up. */
    val MinWidth: Dp = 20.dp
    val Height: Dp = 20.dp
}
