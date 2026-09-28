package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.theme.Theme

/**
 * An overlay's title region: [content] in [style], announced as a heading, and
 * laid out in a row so an icon given before the words sits beside them.
 */
@Composable
internal fun OverlayTitle(style: TextStyle, content: @Composable ContentScope.() -> Unit) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideTextStyle(style) { ContentSlot(content = content) }
    }
}
