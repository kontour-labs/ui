package io.kontour.ui.components.selection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.kontour.ui.foundation.Hsv
import io.kontour.ui.foundation.toColour
import io.kontour.ui.foundation.toHex
import io.kontour.ui.foundation.toHsv
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.freeDragOwning
import io.kontour.ui.interaction.horizontalDragOwning
import io.kontour.ui.theme.Theme

/**
 * How a colour is chosen.
 *
 * The two are not a style preference. A palette answers "which of ours" and a
 * spectrum answers "which colour", and an app that means the first should not
 * offer the second — a brand picker with a full spectrum in it invites an
 * off-brand answer.
 */
enum class ColourPickerMode {
    /** A saturation-and-value area with a hue track under it. Any colour at all. */
    Spectrum,

    /** The swatches and nothing else. A fixed set, chosen by eye. */
    Palette,
}

/**
 * Picks a colour.
 *
 * ```kotlin
 * var accent by remember { mutableStateOf(Color(0xFF3355AA)) }
 *
 * ColourPicker(colour = accent, onColourChange = { accent = it })
 * ```
 *
 * **Every part except the spectrum is optional**, because the same component is
 * asked for very different things: a theme editor wants the area, the hue, the
 * opacity and a hex field; a label picker wants eight swatches and nothing else.
 * Turning a part off removes it rather than disabling it.
 *
 * | | Off by | Why you would |
 * |---|---|---|
 * | Swatches | `swatches = emptyList()` | Nothing worth suggesting |
 * | Opacity | `alphaSlider = false`, the default | Most colours are opaque |
 * | The field | `valueField = false` | Nobody here is going to type a hex |
 * | The notation switch | `onFormatChange = null`, the default | One notation is enough |
 * | The mode switch | `onModeChange = null`, the default | See [ColourPickerMode] |
 *
 * ### It keeps a hue, not a colour
 *
 * The picker holds its own [Hsv] and converts on the way out. That is not an
 * optimisation. **A colour that has reached pure black or pure white has no hue
 * left in it** — the three channels are equal and there is nothing to recover —
 * so a picker that re-derived one from [colour] every frame would watch its own
 * hue track jump to red as the value reached the bottom, and stay there on the
 * way back up. The hue survives a trip to black because it was never discarded.
 *
 * [colour] is still the source of truth, and anything arriving from outside this
 * picker's own gestures is adopted. The comparison is eight-bit sRGB rather than
 * the floats: a round trip through three conversions is accurate to well inside
 * a channel step and not to the bit, so comparing floats would make the picker
 * adopt its own output forever — taking the hue back out of every black.
 *
 * @param onModeChange Non-null puts a switch between [ColourPickerMode.Spectrum]
 *   and [ColourPickerMode.Palette] at the top. Null shows [mode] and no switch.
 * @param alphaSlider Adds an opacity track, over a chequerboard so a
 *   half-transparent colour can be told from the surface behind it.
 * @param valueField Shows the colour written out, and lets it be typed.
 * @param onFormatChange Non-null lets the reader change notation, which swaps
 *   which inputs are shown. Null pins [format].
 * @param swatches Offered under the spectrum, and the whole of
 *   [ColourPickerMode.Palette]. Empty removes the row.
 */
@Composable
fun ColourPicker(
    colour: Color,
    onColourChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    mode: ColourPickerMode = ColourPickerMode.Spectrum,
    onModeChange: ((ColourPickerMode) -> Unit)? = null,
    alphaSlider: Boolean = false,
    valueField: Boolean = true,
    format: ColourFormat = ColourFormat.Hex,
    onFormatChange: ((ColourFormat) -> Unit)? = null,
    swatches: List<Color> = ColourPickerDefaults.Swatches,
) {
    var hsv by remember { mutableStateOf(colour.toHsv()) }
    var alpha by remember { mutableFloatStateOf(colour.alpha) }

    LaunchedEffect(colour) {
        if (hsv.toColour(alpha).toArgb() != colour.toArgb()) {
            hsv = colour.toHsv()
            alpha = colour.alpha
        }
    }

    fun emit(next: Hsv = hsv, nextAlpha: Float = alpha) {
        hsv = next
        alpha = nextAlpha
        onColourChange(next.toColour(nextAlpha))
    }

    /**
     * Take a colour from anything that hands one over — a swatch, a field.
     *
     * **A grey keeps the hue the picker is already holding**, rather than taking
     * the zero a grey reports. That is the same rule the area is built on,
     * applied to the other way in: type `0` into the value field and the colour
     * is black, and a black that overwrote the hue would leave the area and the
     * hue track on red with no way back to where you were.
     */
    fun pick(picked: Color) {
        val next = picked.toHsv()
        val hueless = next.saturation == 0f || next.value == 0f
        emit(
            next = if (hueless) next.copy(hue = hsv.hue) else next,
            nextAlpha = picked.alpha,
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        if (onModeChange != null) {
            SegmentedControl(
                options = ColourPickerMode.entries.map { it.label },
                selected = ColourPickerMode.entries.indexOf(mode),
                onSelectedChange = { onModeChange(ColourPickerMode.entries[it]) },
                enabled = enabled,
            )
        }

        if (mode == ColourPickerMode.Spectrum) {
            SaturationValueArea(hsv, { emit(next = it) }, enabled)
            HueTrack(hsv.hue, { emit(next = hsv.copy(hue = it)) }, enabled)
            if (alphaSlider) {
                AlphaTrack(alpha, hsv.toColour(), { emit(nextAlpha = it) }, enabled)
            }
        }

        if (swatches.isNotEmpty()) {
            ColourSwatchPicker(
                value = swatches.firstOrNull { it.toArgb() == colour.toArgb() },
                options = swatches,
                onValueChange = ::pick,
                swatchColour = { it },
                swatchLabel = { it.toHex() },
                enabled = enabled,
                // Smaller than a swatch picker on its own, which is a control a
                // finger lands on directly. Here the row is a shortcut beside a
                // spectrum that can reach the same colours, and ten of them at
                // 40dp wrap to four rows in a 320dp picker — a suggestion taking
                // more room than the thing it is suggesting an alternative to.
                swatchSize = SwatchSize,
            )
        }

        if (valueField) {
            ColourFields(
                colour = hsv.toColour(alpha),
                onColourChange = ::pick,
                enabled = enabled,
                format = format,
                onFormatChange = onFormatChange,
                withAlpha = alphaSlider,
            )
        }
    }
}

/** What a [ColourPicker] offers when the caller does not say. */
object ColourPickerDefaults {

    /**
     * Eight colours around the wheel.
     *
     * Deliberately **not** the theme's own palette. These are a starting point
     * for a colour the reader is choosing for themselves — a label, a calendar,
     * a route — and a row of the brand's greys is no help with that. An app with
     * a set of its own passes it.
     *
     * Eight rather than ten because of what the row costs: each swatch reserves
     * a finger's worth of target whatever size it is drawn at, so a 320dp picker
     * fits four across. Ten is two rows and a pair left over, which reads as a
     * list that ran out rather than a set.
     */
    val Swatches: List<Color> = listOf(
        Color(0xFFE53935),
        Color(0xFFF4511E),
        Color(0xFFFFB300),
        Color(0xFF43A047),
        Color(0xFF00897B),
        Color(0xFF1E88E5),
        Color(0xFF3949AB),
        Color(0xFF8E24AA),
    )
}

/** What a mode is called on its switch. Enum names are not user-facing words. */
private val ColourPickerMode.label: String
    get() = when (this) {
        ColourPickerMode.Spectrum -> "Spectrum"
        ColourPickerMode.Palette -> "Palette"
    }

/**
 * Saturation across, value down, at one hue.
 *
 * Two gradients over one flat colour, which is the whole of it: white to the
 * hue left to right, then transparent to black top to bottom. The same picture
 * computed per pixel from the maths costs some thousands of times as much and
 * is not distinguishable.
 *
 * `freeDragOwning` because this is a two-axis gesture with no axis left for a
 * scroller to keep, and it claims on the **press** — putting a finger down moves
 * the cursor to it rather than waiting for the finger to travel, which is what
 * every colour area does and what makes a single tap a colour. The cost is the
 * documented one: a page cannot be scrolled by dragging from inside the area.
 * That is the right trade here for the same reason it is on a `Slider`.
 */
@Composable
private fun SaturationValueArea(hsv: Hsv, onHsvChange: (Hsv) -> Unit, enabled: Boolean) {
    val scope = rememberCoroutineScope()
    var box by remember { mutableStateOf(Size.Zero) }
    var at by remember { mutableStateOf(Offset.Zero) }
    val strings = Theme.strings
    val pure = remember(hsv.hue) { Hsv(hsv.hue, 1f, 1f).toColour() }

    fun report(position: Offset) {
        if (box.width <= 0f || box.height <= 0f) return
        at = position
        onHsvChange(
            hsv.copy(
                saturation = (position.x / box.width).coerceIn(0f, 1f),
                value = 1f - (position.y / box.height).coerceIn(0f, 1f),
            )
        )
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(AreaAspect)
            .clip(Theme.shapes.small)
            // Taken in the layout phase. Reading `size` inside the draw block
            // and writing it to state is a write from draw, which is the one
            // thing this repository's frame-rate comments keep coming back to:
            // it invalidates the node that is drawing, from inside its own draw.
            .onSizeChanged { box = it.toSize() }
            .freeDragOwning(
                enabled = enabled,
                interactionSource = null,
                scope = scope,
                claimsOn = DragClaim.Press,
                onStart = ::report,
                onDelta = { report(at + it) },
                onEnd = {},
            )
            .semantics { contentDescription = strings.colourArea }
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        cursor(
            Offset(
                hsv.saturation.coerceIn(0f, 1f) * size.width,
                (1f - hsv.value.coerceIn(0f, 1f)) * size.height,
            )
        )
    }
}

/** The whole wheel, left to right, with the current hue marked. */
@Composable
private fun HueTrack(hue: Float, onHueChange: (Float) -> Unit, enabled: Boolean) {
    val strings = Theme.strings
    Track(
        fraction = (((hue % 360f) + 360f) % 360f) / 360f,
        onFractionChange = { onHueChange(it * 360f) },
        enabled = enabled,
        label = strings.colourHue,
        // Seven stops and not six: red is both ends, and a gradient that stopped
        // at magenta would run the last sixth of the wheel backwards through
        // every colour it had already passed.
        background = { Brush.horizontalGradient(HueStops) },
    )
}

/**
 * Transparent to the colour, over a chequerboard.
 *
 * The chequerboard is not decoration: without it a track fading to transparent
 * over a white surface and one fading to white look identical, and the reader
 * cannot tell an invisible colour from a pale one.
 */
@Composable
private fun AlphaTrack(
    alpha: Float,
    opaque: Color,
    onAlphaChange: (Float) -> Unit,
    enabled: Boolean,
) {
    val strings = Theme.strings
    Track(
        fraction = alpha.coerceIn(0f, 1f),
        onFractionChange = onAlphaChange,
        enabled = enabled,
        label = strings.colourOpacity,
        behind = { chequerboard() },
        background = { Brush.horizontalGradient(listOf(opaque.copy(alpha = 0f), opaque)) },
    )
}

/**
 * The shape both tracks are: a capsule of gradient with a thumb on it.
 *
 * Written once rather than twice for the reason the library writes everything
 * once — the hue track and the opacity track differ in their brush and in what
 * is behind it, and every other line was identical, including the three that
 * make them work for a screen reader.
 */
@Composable
private fun Track(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    enabled: Boolean,
    label: String,
    background: DrawScope.() -> Brush,
    behind: (DrawScope.() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(0f) }
    var at by remember { mutableFloatStateOf(0f) }

    fun report(x: Float) {
        if (width <= 0f) return
        at = x
        onFractionChange((x / width).coerceIn(0f, 1f))
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(TrackHeight)
            // Layout phase, not draw. See `SaturationValueArea`.
            .onSizeChanged { width = it.width.toFloat() }
            // The capsule is the clip, not only the fill. The opacity track's
            // chequerboard is drawn as a plain rect underneath the gradient, and
            // without this its square left corner sits outside the rounded track
            // it is supposed to be behind.
            //
            // `capsule` rather than `pill`: this is a lozenge, not a circle, so
            // it takes the family's own curvature — see `Shapes`.
            .clip(Theme.shapes.capsule)
            .horizontalDragOwning(
                enabled = enabled,
                interactionSource = null,
                scope = scope,
                claimsOn = DragClaim.Press,
                onStart = { report(it.x) },
                onDelta = { report(at + it) },
                onEnd = {},
            )
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                // What makes the track reachable without a gesture, which the
                // area above it cannot be: a screen reader adjusts a value, and
                // a value is exactly what this is.
                setProgress { value ->
                    onFractionChange(value.coerceIn(0f, 1f))
                    true
                }
            }
    ) {
        val radius = size.height / 2f
        behind?.invoke(this)
        drawRoundRect(
            brush = background(),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
        cursor(Offset(fraction.coerceIn(0f, 1f) * size.width, size.height / 2f))
    }
}

/**
 * The marker both the area and the tracks use.
 *
 * A white ring with a dark one just outside it, because a single ring in either
 * colour disappears against half of what it can sit on — white on yellow, black
 * on navy. Two rings cannot both be lost at once, and nothing has to know what
 * colour is underneath.
 *
 * Clamped inside the box rather than centred on the value at the edges, so the
 * marker is never half drawn off the side of the control it belongs to.
 */
private fun DrawScope.cursor(at: Offset) {
    val radius = CursorRadius.toPx()
    val edge = radius + CursorStroke.toPx()
    val centre = Offset(
        at.x.coerceIn(edge, (size.width - edge).coerceAtLeast(edge)),
        at.y.coerceIn(edge, (size.height - edge).coerceAtLeast(edge)),
    )
    val stroke = CursorStroke.toPx()
    drawCircle(
        color = Color.Black.copy(alpha = CursorShadowAlpha),
        radius = radius + stroke / 2f,
        center = centre,
        style = Stroke(stroke * 2f),
    )
    drawCircle(color = Color.White, radius = radius, center = centre, style = Stroke(stroke))
}

/**
 * The grey grid behind a transparency track.
 *
 * Drawn rather than tiled from an image, so it is one file lighter and scales
 * with the reader's density instead of blurring at it.
 */
internal fun DrawScope.chequerboard() {
    val square = ChequerSquare.toPx()
    drawRect(Color.White)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else square
        while (x < size.width) {
            drawRect(
                color = ChequerGrey,
                topLeft = Offset(x, y),
                size = Size(
                    width = minOf(square, size.width - x),
                    height = minOf(square, size.height - y),
                ),
            )
            x += square * 2f
        }
        y += square
        row++
    }
}

/** Half the wheel's stops plus the wrap. See [HueTrack]. */
private val HueStops = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)

/** Wider than tall, so the value axis is the short one. A colour area is read across. */
private const val AreaAspect = 1.6f

private val SwatchSize: Dp = 28.dp
private val TrackHeight: Dp = 20.dp
private val CursorRadius: Dp = 7.dp
private val CursorStroke: Dp = 2.dp
private const val CursorShadowAlpha = 0.28f
internal val ChequerSquare: Dp = 5.dp
internal val ChequerGrey = Color(0xFFCCCCCC)
