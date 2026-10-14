package io.kontour.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * `Theme.colours.content`. `KontourTheme` provides the real value at the root.
 */
val LocalContentColour = compositionLocalOf { Color.Unspecified }

/**
 * The colour of the ground content is drawn on: the container of the nearest
 * opaque [Surface].
 *
 * For content that paints a ground of its own and has to be the same colour as
 * what it sits on — a page in a navigation stack, a top bar — so that inside a
 * sheet it is the sheet's colour rather than the window's, and nothing draws an
 * edge where it ends and the sheet's own padding begins.
 *
 * `Color.Unspecified` outside any surface; read it with
 * `takeOrElse { Theme.colours.background }`. A translucent surface leaves it
 * as it was, since what shows through is still the ground beneath.
 */
val LocalContainerColour = compositionLocalOf { Color.Unspecified }

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
    // Remembered: a row provides three or four of these each time it composes,
    // and a merge is a new style whose every field is resolved again.
    val outer = LocalTextStyle.current
    val merged = remember(outer, value) { outer.merge(value) }
    CompositionLocalProvider(LocalTextStyle provides merged, content = content)
}

/** Runs [content] with [colour] as the inherited content colour. */
@Composable
fun ProvideContentColour(colour: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColour provides colour, content = content)
}
