package io.kontour.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * The colour text and icons draw in, unless they are told otherwise.
 *
 * Set by [Surface] from the ground it paints, so content on a dark card is
 * light without every call site having to know what colour the card is. That
 * cascade is what makes it possible to move a block of UI between a light
 * surface and a dark one without editing it.
 *
 * Defaults to `Color.Unspecified`, which [Text] and [Icon] resolve to
 * `Theme.colors.content`. `KontourTheme` provides the real value at the root.
 */
val LocalContentColor = compositionLocalOf { Color.Unspecified }

/**
 * The text style inherited by [Text] when none is passed.
 *
 * Set to `Theme.typography.bodyMedium` at the theme root. Components that
 * establish a typographic context — a list item's supporting text, a button's
 * label — override it for their subtree with [ProvideTextStyle] so nested
 * [Text] calls inherit rather than each specifying a style.
 */
val LocalTextStyle = compositionLocalOf { TextStyle.Default }

/**
 * Runs [content] with [value] as the inherited text style.
 *
 * Merges with the current style rather than replacing it, so a partial style —
 * just a weight, say — keeps the family and size it was nested inside.
 */
@Composable
fun ProvideTextStyle(value: TextStyle, content: @Composable () -> Unit) {
    val merged = LocalTextStyle.current.merge(value)
    CompositionLocalProvider(LocalTextStyle provides merged, content = content)
}

/** Runs [content] with [color] as the inherited content colour. */
@Composable
fun ProvideContentColor(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides color, content = content)
}
