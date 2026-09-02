package io.kontour.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.invisible

/**
 * How a text field is drawn.
 *
 * [Outlined] is the default: a hairline box that thickens and takes the accent
 * when focused. [Filled] sits in a sunken well with no border — right for dense
 * forms and for fields inside a card, where a grid of boxes gets noisy.
 */
enum class TextFieldVariant { Outlined, Filled }

/** Resolved colours for a text field across its states. */
@Immutable
data class TextFieldColours(
    val container: Color,
    val containerFocused: Color,
    val containerDisabled: Color,
    val content: Color,
    val contentDisabled: Color,
    val placeholder: Color,
    val label: Color,
    val labelFocused: Color,
    val border: Color,
    val borderFocused: Color,
    val borderError: Color,
    val borderDisabled: Color,
    val helper: Color,
    val error: Color,
    val cursor: Color,
    val selectionBackground: Color,
) {
    @Composable
    @ReadOnlyComposable
    internal fun border(enabled: Boolean, focused: Boolean, isError: Boolean): Color = when {
        !enabled -> borderDisabled
        // Error outranks focus: a focused invalid field is still invalid, and
        // the accent ring would hide that.
        isError -> borderError
        focused -> borderFocused
        else -> border
    }

    @Composable
    @ReadOnlyComposable
    internal fun container(enabled: Boolean, focused: Boolean): Color = when {
        !enabled -> containerDisabled
        focused -> containerFocused
        else -> container
    }
}

/** Metrics for one text field size. */
@Immutable
data class TextFieldMetrics(
    val minHeight: Dp,
    val horizontalPadding: Dp,
    /**
     * The padding on a side that holds an icon rather than text.
     *
     * Smaller than [horizontalPadding], and it has to be: a glyph does not fill
     * its own box. `iconMedium` is 20dp holding 12dp of chevron, so 4dp of clear
     * space comes free on each side — pad an icon like text and its *ink* lands
     * 4dp further in than the text's does. A `Select` measured 13dp of clear
     * space to the left of its value and 16dp to the right of its chevron, which
     * is what "the padding is asymmetric" looks like from the outside.
     *
     * Taking the 4dp off the padding instead of nudging the icon keeps it a
     * layout fact rather than a drawing trick, and it is the same 4dp every
     * icon set leaves, because they are all drawn on a grid with a margin.
     */
    val iconPadding: Dp,
    val verticalPadding: Dp,
    val gap: Dp,
)

/** Defaults for the text field family. */
object TextFieldDefaults {

    @Composable
    @ReadOnlyComposable
    fun colours(variant: TextFieldVariant = TextFieldVariant.Outlined): TextFieldColours {
        val c = Theme.colours
        return TextFieldColours(
            // `invisible()`, not `Color.Transparent`. `FieldScaffold` animates
            // this to `containerFocused` and back, and `Color.Transparent` is
            // black — so an outlined field faded *through* a half-opaque
            // near-black in both directions. That was the grey flash on focus
            // and the grey flash on blur, and it was two frames long.
            container = if (variant == TextFieldVariant.Filled) {
                c.surfaceSunken
            } else {
                c.accent.container.invisible()
            },
            // Focus tints the *ground*, not only the border. A 2dp accent edge
            // is the whole of "you are typing here" today, and on a form of six
            // fields that is a thin line the eye has to go looking for. The tint
            // is the thing you see without looking.
            containerFocused = if (variant == TextFieldVariant.Filled) {
                c.accent.container
            } else {
                // Outlined has no ground of its own, so the tint is what appears
                // — at half strength, because it is arriving rather than
                // changing.
                c.accent.container.copy(alpha = 0.5f)
            },
            containerDisabled = c.surfaceSunken,
            content = c.content,
            contentDisabled = c.contentDisabled,
            placeholder = c.contentSubtle,
            label = c.contentMuted,
            labelFocused = c.accent.solid,
            // Filled had `Color.Transparent` unconditionally — the only
            // component in the library that stated the high-contrast problem
            // outright. A filled field is `surfaceSunken` on `background`, so
            // with no border there is nothing on screen saying where the input
            // starts.
            border = if (variant == TextFieldVariant.Filled) {
                // Invisible rather than transparent for the same reason as
                // `container` above: this animates to `borderFocused`.
                if (highContrast()) c.outline else c.outline.invisible()
            } else {
                c.outlineStrong
            },
            borderFocused = c.accent.solid,
            borderError = c.danger.solid,
            borderDisabled = c.outline,
            helper = c.contentMuted,
            error = c.danger.onContainer,
            cursor = c.accent.solid,
            // The *solid* accent at a third, not the container tint.
            //
            // It was `accent.container` — which is exactly the colour a focused
            // field paints its own ground, so selecting text inside one
            // highlighted it in the colour it was already sitting on. On a
            // filled field the two were the same value and the selection was
            // invisible; on an outlined one the ground is that colour at half
            // strength, so the highlight was the same hue one step darker,
            // which reads as a rendering artefact rather than as a selection.
            //
            // A translucent solid instead: it deepens whatever it lands on
            // rather than matching it, so it separates from the focused ground,
            // the unfocused ground and the page alike — and it is what every
            // text editor does, for the same reason.
            selectionBackground = c.accent.solid.copy(alpha = SelectionAlpha),
        )
    }

    @Composable
    @ReadOnlyComposable
    fun metrics(): TextFieldMetrics = TextFieldMetrics(
        minHeight = Theme.sizing.controlHeightLarge,
        horizontalPadding = Theme.spacing.sm,
        // One step down the scale, which is the 4dp of clear space an icon's own
        // box already gives it. See `TextFieldMetrics.iconPadding`.
        iconPadding = Theme.spacing.xs,
        verticalPadding = Theme.spacing.sm,
        gap = Theme.spacing.xs,
    )
}

/**
 * How much of the accent a text selection lays over the ground beneath it.
 *
 * A third: enough to be unmistakable against a focused field's own tint, not so
 * much that the text on top of it loses its contrast — which is the failure at
 * the other end, and a worse one, because a selection you cannot read is harder
 * to work with than one you cannot see.
 */
private const val SelectionAlpha = 0.35f
