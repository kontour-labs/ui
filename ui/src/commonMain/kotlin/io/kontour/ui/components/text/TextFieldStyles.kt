package io.kontour.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import io.kontour.ui.theme.Theme

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
data class TextFieldColors(
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
    val verticalPadding: Dp,
    val gap: Dp,
)

/** Defaults for the text field family. */
object TextFieldDefaults {

    @Composable
    @ReadOnlyComposable
    fun colors(variant: TextFieldVariant = TextFieldVariant.Outlined): TextFieldColors {
        val c = Theme.colors
        return TextFieldColors(
            container = if (variant == TextFieldVariant.Filled) c.surfaceSunken else Color.Transparent,
            containerFocused = if (variant == TextFieldVariant.Filled) c.surfaceSunken else Color.Transparent,
            containerDisabled = c.surfaceSunken,
            content = c.content,
            contentDisabled = c.contentDisabled,
            placeholder = c.contentSubtle,
            label = c.contentMuted,
            labelFocused = c.accent,
            border = if (variant == TextFieldVariant.Filled) Color.Transparent else c.outlineStrong,
            borderFocused = c.accent,
            borderError = c.danger.solid,
            borderDisabled = c.outline,
            helper = c.contentMuted,
            error = c.danger.onContainer,
            cursor = c.accent,
            selectionBackground = c.accentContainer,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun metrics(): TextFieldMetrics = TextFieldMetrics(
        minHeight = Theme.sizing.controlHeightLarge,
        horizontalPadding = Theme.spacing.sm,
        verticalPadding = Theme.spacing.sm,
        gap = Theme.spacing.xs,
    )
}
