package io.kontour.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
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
    // A colour of its own, which holds still whatever the one round it is doing.
    CompositionLocalProvider(
        LocalContentColour provides colour,
        LocalContentColourInMotion provides null,
        content = content,
    )
}

/**
 * Runs [content] with a content colour that is fading to [towards], which [Text]
 * and [Icon] read as they draw.
 *
 * For a label whose colour follows its control's state: a tab's, a filter
 * chip's, a segment's. Handed on through [LocalContentColour] as it fades, the
 * colour changed on every frame of the fade, and a provided value that changes
 * composes everything under it again, skipping nothing — the label's row, its
 * text and its icons, every frame, for a colour the draw pass can change on its
 * own. Raised 2026-10-29 for 0.5.1: a tab bar composed some four scopes a
 * frame for each change of tab.
 *
 * So [LocalContentColour] carries [towards], which changes once, when the state
 * does, and the fade goes by [LocalContentColourInMotion] to the text and icons
 * that inherit it, which draw each frame of it in its colour on that frame: the
 * same colours, frame by frame, as before. Anything else under it that reads
 * [LocalContentColour] itself sees where the fade is going.
 */
@Composable
internal fun ProvideContentColourInMotion(colour: State<Color>, towards: Color, content: @Composable () -> Unit) {
    val moving = remember(colour, towards) { ContentColourInMotion(towards, colour) }
    CompositionLocalProvider(
        LocalContentColour provides towards,
        LocalContentColourInMotion provides moving,
        content = content,
    )
}

/** A content colour fading to [towards], read as it draws. See [ProvideContentColourInMotion]. */
@Stable
internal class ContentColourInMotion(val towards: Color, private val colour: State<Color>) : ColorProducer {
    override fun invoke(): Color = colour.value
}

/**
 * The fade a [ProvideContentColourInMotion] round this is making, or null.
 *
 * Cleared by [ProvideContentColour], whose colour is its own. A colour provided
 * any other way is told apart by its value: content that inherits a colour
 * other than [ContentColourInMotion.towards] has had one of its own set, and
 * does not fade.
 */
internal val LocalContentColourInMotion = compositionLocalOf<ContentColourInMotion?> { null }

/** The fade the colour this inherits is making, or null when it is not one. */
@Composable
@ReadOnlyComposable
internal fun inheritedColourInMotion(): ContentColourInMotion? {
    val moving = LocalContentColourInMotion.current ?: return null
    return if (LocalContentColour.current == moving.towards) moving else null
}
