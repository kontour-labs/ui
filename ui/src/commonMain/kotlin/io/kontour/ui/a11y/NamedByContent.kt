package io.kontour.ui.a11y

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether what is being composed sits inside a control, or a row, that a screen
 * reader names by its own content: a clickable `Card`, a `ListItem`, a
 * `SelectionRow`, a button's label.
 *
 * Such a container merges everything inside it into one node, and a merged
 * node's description is read *instead of* its text — on Android in place of
 * it, and in the browser as the node's `aria-label`, which wins over what the
 * node says. So a picture with a description of its own, an [Avatar][io.kontour.ui.components.display.Avatar]
 * that announced its person's name, became the row's whole name: a chat row
 * was "Ada Lovelace", without the last message, and a card was its photo.
 *
 * A component that would otherwise describe itself reads this and stays quiet,
 * or says what it has to say as text, read in its place among the rest.
 */
internal val LocalNamedByContent = staticCompositionLocalOf { false }

/** [content], told that it is named by its container's content. See [LocalNamedByContent]. */
@Composable
internal fun NamedByContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNamedByContent provides true, content = content)
}
