package io.kontour.ui.components.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.TextFieldDefaults
import io.kontour.ui.components.text.TextFieldVariant
import io.kontour.ui.foundation.Hsl
import io.kontour.ui.foundation.Hsv
import io.kontour.ui.foundation.colourFromHex
import io.kontour.ui.foundation.toColour
import io.kontour.ui.foundation.toHex
import io.kontour.ui.foundation.toHsl
import io.kontour.ui.foundation.toHsv
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

/**
 * How a colour is written down.
 *
 * Four notations rather than one, because the people who type into a colour
 * picker have already learned one of them somewhere else: a hex off a brand
 * sheet, RGB out of a design tool, HSL out of a stylesheet. Offering only hex
 * makes everyone else convert in their head.
 *
 * [Hsv] and [Hsl] are not the same numbers for the same colour — see
 * [io.kontour.ui.foundation.Hsl].
 */
enum class ColourFormat {
    /** `#RRGGBB`, or `#RRGGBBAA` with opacity on. Alpha last, as CSS writes it. */
    Hex,

    /** Three channels, `0..255`. */
    Rgb,

    /** Hue in degrees, saturation and value as percentages. */
    Hsv,

    /** Hue in degrees, saturation and lightness as percentages. What CSS means. */
    Hsl,
}

/** What a notation is called on its switch. Enum names are not user-facing words. */
internal val ColourFormat.label: String
    get() = when (this) {
        ColourFormat.Hex -> "Hex"
        ColourFormat.Rgb -> "RGB"
        ColourFormat.Hsv -> "HSV"
        ColourFormat.Hsl -> "HSL"
    }

/**
 * The colour written out, and typeable.
 *
 * ### The two-way binding, and the loop it would otherwise be
 *
 * A field bound to a colour has to push the colour in and pull typing out, and
 * the naive version of that oscillates: the reader types `0f8`, the picker
 * resolves it, the canonical spelling `#00FF88` comes back, and the field
 * rewrites itself under the cursor mid-word.
 *
 * So the field is only rewritten when the incoming text means a **different
 * colour** than what is already in it. `0f8` and `#00FF88` parse to the same
 * thing, so nothing is touched; a colour picked on the area above parses to
 * something else, so the field follows it. The same rule covers `051` against
 * `51` in a channel field, and it is one comparison rather than a rule per
 * notation.
 */
@Composable
internal fun ColourFields(
    colour: Color,
    onColourChange: (Color) -> Unit,
    enabled: Boolean,
    format: ColourFormat,
    onFormatChange: ((ColourFormat) -> Unit)?,
    withAlpha: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        if (onFormatChange != null) {
            SegmentedControl(
                options = ColourFormat.entries.map { it.label },
                selected = ColourFormat.entries.indexOf(format),
                onSelectedChange = { onFormatChange(ColourFormat.entries[it]) },
                enabled = enabled,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Preview(colour)
            when (format) {
                ColourFormat.Hex -> HexField(colour, onColourChange, enabled, withAlpha)
                ColourFormat.Rgb -> RgbFields(colour, onColourChange, enabled)
                ColourFormat.Hsv -> HsvFields(colour, onColourChange, enabled)
                ColourFormat.Hsl -> HslFields(colour, onColourChange, enabled)
            }
            if (withAlpha && format != ColourFormat.Hex) {
                Channel(
                    label = "A",
                    value = (colour.alpha * 100f).roundToInt(),
                    range = 0..100,
                    enabled = enabled,
                ) { onColourChange(colour.copy(alpha = it / 100f)) }
            }
        }
    }
}

/**
 * The colour itself, at the head of the row.
 *
 * On a chequerboard, for the reason the opacity track is: a swatch fading to
 * transparent and one fading to the surface behind it are the same picture.
 */
@Composable
private fun Preview(colour: Color) {
    Box(
        Modifier
            .size(PreviewSize)
            .clip(Theme.shapes.small)
            .drawBehind {
                chequerboard()
                drawRect(colour)
            }
    )
}

@Composable
private fun RowScope.HexField(
    colour: Color,
    onColourChange: (Color) -> Unit,
    enabled: Boolean,
    withAlpha: Boolean,
) {
    SyncedField(
        canonical = colour.toHex(includeAlpha = withAlpha),
        parse = ::colourFromHex,
        onParsed = onColourChange,
        label = ColourFormat.Hex.label,
        keyboardType = KeyboardType.Text,
        enabled = enabled,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun RowScope.RgbFields(colour: Color, onColourChange: (Color) -> Unit, enabled: Boolean) {
    val alpha = colour.alpha
    Channel("R", (colour.red * 255f).roundToInt(), 0..255, enabled) {
        onColourChange(Color(it / 255f, colour.green, colour.blue, alpha))
    }
    Channel("G", (colour.green * 255f).roundToInt(), 0..255, enabled) {
        onColourChange(Color(colour.red, it / 255f, colour.blue, alpha))
    }
    Channel("B", (colour.blue * 255f).roundToInt(), 0..255, enabled) {
        onColourChange(Color(colour.red, colour.green, it / 255f, alpha))
    }
}

@Composable
private fun RowScope.HsvFields(colour: Color, onColourChange: (Color) -> Unit, enabled: Boolean) {
    val hsv = colour.toHsv()
    val alpha = colour.alpha
    Channel("H", hsv.hue.roundToInt(), 0..360, enabled) {
        onColourChange(hsv.copy(hue = it.toFloat()).toColour(alpha))
    }
    Channel("S", (hsv.saturation * 100f).roundToInt(), 0..100, enabled) {
        onColourChange(hsv.copy(saturation = it / 100f).toColour(alpha))
    }
    Channel("V", (hsv.value * 100f).roundToInt(), 0..100, enabled) {
        onColourChange(hsv.copy(value = it / 100f).toColour(alpha))
    }
}

@Composable
private fun RowScope.HslFields(colour: Color, onColourChange: (Color) -> Unit, enabled: Boolean) {
    val hsl = colour.toHsl()
    val alpha = colour.alpha
    Channel("H", hsl.hue.roundToInt(), 0..360, enabled) {
        onColourChange(hsl.copy(hue = it.toFloat()).toColour(alpha))
    }
    Channel("S", (hsl.saturation * 100f).roundToInt(), 0..100, enabled) {
        onColourChange(hsl.copy(saturation = it / 100f).toColour(alpha))
    }
    Channel("L", (hsl.lightness * 100f).roundToInt(), 0..100, enabled) {
        onColourChange(hsl.copy(lightness = it / 100f).toColour(alpha))
    }
}

/** One whole number between two bounds, in a field the width of its neighbours. */
@Composable
private fun RowScope.Channel(
    label: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    SyncedField(
        canonical = value.toString(),
        // Out of range is not a value. Clamping instead would mean a reader who
        // typed 300 into a channel watched it become 255 under their finger, at
        // which point the second digit they meant to type is gone.
        parse = { text -> text.trim().toIntOrNull()?.takeIf { it in range } },
        onParsed = onValueChange,
        label = label,
        keyboardType = KeyboardType.Number,
        enabled = enabled,
        modifier = Modifier.weight(1f),
    )
}

/**
 * A field that follows a value without fighting whoever is typing in it.
 *
 * See [ColourFields] for the rule. [parse] is what makes it work: the guard
 * compares *meanings* rather than strings, so it cannot be confused by a
 * different spelling of the same colour.
 */
@Composable
private fun <T> SyncedField(
    canonical: String,
    parse: (String) -> T?,
    onParsed: (T) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val state: TextFieldState = rememberTextFieldState(canonical)
    val read by rememberUpdatedState(parse)
    val parsed by rememberUpdatedState(onParsed)

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            // `snapshotFlow` hands over the current value first, and the current
            // value is the one that was just put in — reporting it would emit a
            // change nobody made, on the frame the picker appears.
            .drop(1)
            .collect { typed -> read(typed)?.let(parsed) }
    }

    LaunchedEffect(canonical) {
        if (read(state.text.toString()) != read(canonical)) {
            state.setTextAndPlaceCursorAtEnd(canonical)
        }
    }

    TextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        label = label,
        variant = TextFieldVariant.Filled,
        keyboardType = keyboardType,
        // Tighter than a form's field, because four of them have to share a
        // picker's width. At the standard padding a channel field is 74dp wide
        // holding 22dp of text, and 255 renders as "25" with the last digit cut
        // off — measured, in a 320dp picker, which is the width this is for.
        //
        // The label above is what keeps that legible rather than cramped: the
        // letter says which channel, so the box only ever holds up to three
        // digits and never needs room for a word.
        metrics = TextFieldDefaults.metrics().copy(
            minHeight = Theme.sizing.controlHeightMedium,
            horizontalPadding = Theme.spacing.xxs,
            verticalPadding = Theme.spacing.xxs,
            gap = Theme.spacing.xxs,
        ),
    )
}

private val PreviewSize: Dp = 28.dp
