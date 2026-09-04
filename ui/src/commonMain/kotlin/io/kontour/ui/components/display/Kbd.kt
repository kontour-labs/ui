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
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.theme.SquircleShape
import io.kontour.ui.theme.Theme

/**
 * A key cap — one key, or one accelerator.
 *
 * ```kotlin
 * Kbd { +"${KbdDefaults.Command}S" }
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
/**
 * A keycap's own corner, for the same reason a checkbox has one.
 *
 * [KbdDefaults.Height] and [KbdDefaults.MinWidth] are both 20dp, so a
 * single-character key is a 20dp square — and once
 * [io.kontour.ui.theme.Shapes.extraSmall] became 10dp, that square was a circle.
 * A keycap that is round is not a keycap; the whole point of the component is
 * that it looks like the thing on the keyboard.
 */
private val KbdShape = SquircleShape(6.dp)

@Composable
fun Kbd(
    modifier: Modifier = Modifier,
    colour: Color = Theme.colours.contentMuted,
    content: @Composable ContentScope.() -> Unit,
) {
    val shape = KbdShape
    Row(
        modifier = modifier
            .clearAndSetSemantics { }
            .defaultMinSize(minWidth = KbdDefaults.MinWidth, minHeight = KbdDefaults.Height)
            .clip(shape)
            .background(Theme.colours.surfaceSunken, shape)
            .border(Theme.sizing.borderWidth, Theme.colours.outline, shape)
            .padding(horizontal = Theme.spacing.xxs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Centred on the glyph's own box, not on the font's line.
        //
        // A monospace face has no ⌘ or ⇧ in it, so those come from whatever
        // fallback the platform picks — with its own ascent and descent, and a
        // line box that does not line up with the digits and letters beside it.
        // Left to the default the cap centres the *line*, which puts a borrowed
        // glyph high in the box on one platform and low on another.
        //
        // `Trim.Both` throws away the leading above and below, and `Center`
        // shares what is left equally, so what ends up in the middle of the cap
        // is the ink rather than the metrics of whichever font supplied it.
        ProvideTextStyle(
            Theme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            )
        ) {
            ProvideContentColour(colour) {
                ContentSlot(iconSize = Theme.sizing.iconSmall, maxLines = 1, content = content)
            }
        }
    }
}

object KbdDefaults {
    /** Square-ish for a single character, so a row of caps lines up. */
    val MinWidth: Dp = 20.dp
    val Height: Dp = 20.dp

    /**
     * The glyphs a keyboard prints on its own keys.
     *
     * Named rather than pasted. Every one of these is a character most keyboards
     * cannot type and most people cannot describe — ⌥ is "option" or "alt"
     * depending on who is asking, and half the internet writes ⌃ as `^`. A
     * call site with `KbdDefaults.Option` in it says which key it means; one
     * with a literal says only what somebody's clipboard had at the time.
     *
     * They are the *Unicode* glyphs, so they render from the platform's own
     * fonts and match what is printed on the key. Which key an app should show
     * is its own business — a Mac writes ⌘S and Windows writes Ctrl+S for the
     * same command — and that decision belongs above the design system.
     */
    const val Command: String = "\u2318"

    /** ⌥ — option on a Mac, alt elsewhere. */
    const val Option: String = "\u2325"

    /** ⇧ — shift. */
    const val Shift: String = "\u21E7"

    /** ⌃ — control. */
    const val Control: String = "\u2303"

    /** ⏎ — return or enter. */
    const val Return: String = "\u23CE"

    /** ⌫ — backspace, the one that deletes backwards. */
    const val Backspace: String = "\u232B"

    /** ⌦ — forward delete. */
    const val Delete: String = "\u2326"

    /** ⎋ — escape. */
    const val Escape: String = "\u238B"

    /** ⇥ — tab. */
    const val Tab: String = "\u21E5"

    /** ⇪ — caps lock. */
    const val CapsLock: String = "\u21EA"

    /** The four arrows, for a component that draws a key rather than a word. */
    const val ArrowUp: String = "\u2191"
    const val ArrowDown: String = "\u2193"
    const val ArrowLeft: String = "\u2190"
    const val ArrowRight: String = "\u2192"

    /** ⇞ and ⇟ — page up and page down. */
    const val PageUp: String = "\u21DE"
    const val PageDown: String = "\u21DF"

    /** ␣ — the space bar, where a blank cap would read as a mistake. */
    const val Space: String = "\u2423"
}
