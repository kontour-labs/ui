package io.kontour.ui.components.action

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.LocalTextStyle
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.contentScope
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.theme.Theme

/**
 * A button that is only its label, at the size of the text around it.
 *
 * ```kotlin
 * TextButton(onClick = ::forgot) { +"Forgot your password?" }
 * ```
 *
 * `ButtonVariant.Ghost` is the nearest thing a [Button] has and it is still a
 * button: a fixed `height`, horizontal padding, a shape and a 48dp target that
 * takes up room. That is right under a form and wrong at the end of a sentence,
 * where the thing wanted is a word you can press. This draws the label and
 * nothing else — **the target is reserved without being painted**, so it is 48dp
 * to a finger and the height of a line to the layout.
 *
 * **In a sentence, this is the wrong tool.** A button cannot wrap with the words
 * either side of it, and a screen reader announces it as a button in a list of
 * buttons rather than as a link in the flow of the text. Use
 * [linkedText][io.kontour.ui.foundation.linkedText], which produces a real
 * `LinkAnnotation` inside the paragraph. This one is for a label that stands on
 * its own — under a field, at the end of a card, beside a heading.
 *
 * **Not underlined, and that is the difference from an inline link rather than
 * an inconsistency with it.** WCAG 1.4.1 asks that a link *inside a block of
 * text* be told apart by more than its colour, because there is text either side
 * of it to be told apart from. A standalone label has no neighbours to be
 * confused with, and the underline there reads as a mistake — which is why every
 * platform's own "Forgot password?" is a coloured word without one.
 *
 * Press and hover are a tint behind the word, matching the inline link exactly;
 * see [io.kontour.ui.foundation.LinkDefaults.styles] for why a link's feedback
 * cannot be the scale every filled control uses.
 *
 * @param colour The label's colour. Accent by default, which is the interactive
 *   colour; a destructive text button takes `Theme.colours.danger.solid`.
 * @param content The label, as a slot — so `+"Save"`, `+icon` and
 *   `icon(image, description)` all work, and an icon drawn here is sized to the
 *   text rather than to an icon scale.
 */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colour: Color = Theme.colours.accent.solid,
    shape: Shape = Theme.shapes.small,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowContentScope.() -> Unit,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val colours = Theme.colours

    val ink = when {
        !enabled -> colours.contentDisabled
        pressed -> colours.accent.onContainer
        else -> colour
    }
    val ground = if (enabled && (pressed || hovered)) colours.accent.container else Color.Transparent

    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { role = Role.Button }
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .background(ground, shape)
            .pointerCursor(enabled = enabled)
            .clickable(
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            // Enough for the tint to clear the glyphs and no more. A text button
            // that reserved a control's padding would push the words it sits
            // beside apart, which is the thing it exists not to do.
            .padding(horizontal = TintPadding),
        horizontalArrangement = Arrangement.spacedBy(TextGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColour provides ink) {
            contentScope(iconSize = textSizedIcon(), content = content)
        }
    }
}

/**
 * The icon-only [TextButton] — a glyph at the size of the text beside it.
 *
 * ```kotlin
 * Row {
 *     Text("Perth Underground")
 *     TextIconButton(icon = Tabler.Outline.InfoCircle, contentDescription = "About this stop", onClick = ::explain)
 * }
 * ```
 *
 * [IconButton] is the same idea at a control's scale: it has a `ButtonSize`, a
 * shape, a variant and a 40dp box it paints. Next to a line of text that box is
 * taller than the line, so a heading with one in it grows and the baseline stops
 * lining up. This is sized from `LocalTextStyle` instead, so it matches whatever
 * it is standing beside — including at 200% font scale, where a fixed icon size
 * would be the one thing on the line that did not grow.
 *
 * It still reserves 48dp to a finger without painting it, and it still needs a
 * [contentDescription]: an icon with no text beside it is the only thing saying
 * what the button does.
 */
@Composable
fun TextIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colour: Color = Theme.colours.accent.solid,
    shape: Shape = Theme.shapes.small,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val colours = Theme.colours

    val ink = when {
        !enabled -> colours.contentDisabled
        pressed -> colours.accent.onContainer
        else -> colour
    }
    val ground = if (enabled && (pressed || hovered)) colours.accent.container else Color.Transparent

    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        size = textSizedIcon(),
        tint = ink,
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .background(ground, shape)
            .pointerCursor(enabled = enabled)
            .clickable(
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    )
}

/**
 * An icon as tall as the current text, so the two grow together.
 *
 * `Theme.sizing.iconSmall` is a constant, and a constant next to text is the one
 * thing on the line that does not answer the reader's font scale — at 200% the
 * label doubles and the glyph beside it does not. `fontSize` is already scaled
 * by the time it is read here, so taking the icon from it is what keeps them the
 * same size at every setting.
 *
 * Falls back to the icon scale when the ambient style has no size of its own,
 * which is the only case where there is nothing to match.
 */
@Composable
private fun textSizedIcon(): Dp {
    val size = LocalTextStyle.current.fontSize
    if (!size.isSpecified) return Theme.sizing.iconSmall
    return with(LocalDensity.current) { size.toDp() }
}

/** The tint's clearance around the label. Not padding a caller can feel. */
private val TintPadding: Dp = 2.dp

/** Between an icon and the word it belongs to, at text scale. */
private val TextGap: Dp = 4.dp
