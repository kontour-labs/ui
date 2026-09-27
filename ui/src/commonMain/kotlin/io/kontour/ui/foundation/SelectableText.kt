package io.kontour.ui.foundation

import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.kontour.ui.theme.ColourScheme

/**
 * Lets the reader select and copy the text inside it.
 *
 * ```kotlin
 * SelectableText {
 *     Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
 *         Text(notice.title, style = Theme.typography.titleMedium)
 *         Text(notice.body)
 *     }
 * }
 * ```
 *
 * Nothing in the library is selectable until it is inside one. That is the
 * right default for an app — a screen of controls whose labels light up under
 * a stray drag is a screen that feels broken — and the wrong one for reading:
 * an article, a stop's address, an error a user wants to paste into a message.
 * Wrap what is read, not the whole app.
 *
 * ### Regions
 *
 * A selection runs across everything inside one region, so wrap the reading as
 * a whole — a page of prose is one region, not one per paragraph, or a drag
 * stops at every paragraph's edge. Inside a lazy list, wrap the list: a
 * selection then reaches as far as the list has composed.
 *
 * ### What stays unselectable inside one
 *
 * - **Controls.** A button's, chip's, tab's or navigation destination's label,
 *   a segmented control's options, a menu item and a clickable row are never
 *   part of a selection, so a drag across a paragraph with a button in it picks
 *   up the paragraph. Selection is for content; chrome is pressed. A row with no
 *   `onClick` is content and stays selectable.
 * - **Redacted text**, which draws no glyphs and must not give them up on copy.
 * - Anything the caller wraps in Compose's own `DisableSelection` — a live demo,
 *   a canvas that takes drags.
 *
 * ### Colours and copying
 *
 * The highlight and handles are the theme's accent, the same pair a
 * [io.kontour.ui.components.text.TextField] uses, because
 * [io.kontour.ui.theme.KontourTheme] provides them to every selection beneath
 * it.
 *
 * Copy goes to the platform clipboard. On the web, ⌘C and Ctrl+C copy through
 * the browser's own copy event. Copy from the selection's toolbar or a
 * right-click menu goes through Compose's web clipboard instead, which does not
 * reach the browser at the time of writing — `docs/building/testing.md`,
 * "Three fixes, no number moved".
 *
 * @param modifier For the region, not for the text in it.
 * @param content The text, and whatever sits among it.
 */
@Composable
fun SelectableText(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SelectionContainer(modifier = modifier, content = content)
}

/**
 * How much of the accent a text selection lays over the ground beneath it.
 *
 * A third: enough to be unmistakable against a focused field's own tint, not so
 * much that the text on top of it loses its contrast — which is the failure at
 * the other end, and a worse one, because a selection you cannot read is harder
 * to work with than one you cannot see.
 *
 * Shared by the text field and the theme, so a selection in a field and one in
 * a paragraph beside it are the same colour.
 */
internal const val TextSelectionAlpha = 0.35f

/** The selection highlight and handles for this scheme: its accent, washed and solid. */
internal fun ColourScheme.textSelectionColours(): TextSelectionColors =
    TextSelectionColors(
        handleColor = accent.solid,
        backgroundColor = accent.solid.copy(alpha = TextSelectionAlpha),
    )

/**
 * [content], left out of any selection when [excluded]: a control's label,
 * redacted text.
 *
 * A branch rather than one provider with a changing value, because Compose's
 * `DisableSelection` is the only public way to do it. Flipping [excluded] builds
 * [content] again, so it is for things that rarely flip — a row gaining an
 * `onClick`, text leaving redaction — and not for anything animated.
 */
@Composable
internal fun ExcludeFromSelection(excluded: Boolean, content: @Composable () -> Unit) {
    if (excluded) DisableSelection(content) else content()
}
