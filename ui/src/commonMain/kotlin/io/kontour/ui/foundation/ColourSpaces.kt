package io.kontour.ui.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A colour as hue, saturation and value — the three axes a picker is built on.
 *
 * ### Why a picker needs this and RGB will not do
 *
 * The two-dimensional square in every colour picker ever made is saturation
 * against value at one hue, and there is no pair of RGB channels that draws it.
 * Dragging in it has to leave the hue alone, which in RGB means changing all
 * three channels together by amounts that depend on which one is largest. So the
 * picker holds an [Hsv] and converts on the way out, rather than holding a
 * [Color] and trying to recover the hue from it — **a colour that has reached
 * pure white or pure black has no hue left in it at all**, and a picker that
 * re-derives one has a slider that jumps to red the moment the value hits zero.
 *
 * @param hue Degrees, `0..360`. 360 and 0 are the same colour; both are accepted
 *   and [toColour] treats them alike.
 * @param saturation `0..1`, from grey to the full hue.
 * @param value `0..1`, from black to the brightest the hue goes.
 */
@Immutable
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/**
 * A colour as hue, saturation and lightness.
 *
 * The other of the two cylinders, and the one CSS writes. It is not a relabelled
 * [Hsv]: HSL's 0.5 lightness is the *full* hue and 1.0 is white, where HSV's 1.0
 * value is the full hue and white needs the saturation taken out as well. The
 * two disagree about saturation for the same colour, which is why a picker that
 * offers both has to convert rather than rename.
 *
 * @param hue Degrees, `0..360`.
 * @param saturation `0..1`.
 * @param lightness `0..1`, from black through the hue at `0.5` to white.
 */
@Immutable
data class Hsl(val hue: Float, val saturation: Float, val lightness: Float)

/**
 * This colour's hue, saturation and value.
 *
 * Converted to sRGB first. A `Color` carries its own colour space, and the same
 * three numbers mean different colours in sRGB and in Display P3 — a picker that
 * skipped this would report one hue and draw another on a wide-gamut screen.
 *
 * **Hue is 0 for a grey**, because a grey has no hue to report. That is a lossy
 * answer and it is the reason a picker should keep its own [Hsv] rather than
 * round-trip through a `Color` on every frame: drag the value to zero and back
 * up, and a re-derived hue would have come back red.
 */
fun Color.toHsv(): Hsv {
    val rgb = convert(ColorSpaces.Srgb)
    val r = rgb.red
    val g = rgb.green
    val b = rgb.blue
    val top = max(r, max(g, b))
    val bottom = min(r, min(g, b))
    val chroma = top - bottom
    return Hsv(
        hue = hueOf(r, g, b, top, chroma),
        saturation = if (top <= 0f) 0f else chroma / top,
        value = top,
    )
}

/** The colour these three describe, at [alpha]. */
fun Hsv.toColour(alpha: Float = 1f): Color {
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val chroma = v * s
    val (r, g, b) = rgbOf(hue, chroma, v - chroma)
    return Color(r, g, b, alpha.coerceIn(0f, 1f))
}

/** This colour's hue, saturation and lightness. See [toHsv] on greys and gamut. */
fun Color.toHsl(): Hsl {
    val rgb = convert(ColorSpaces.Srgb)
    val r = rgb.red
    val g = rgb.green
    val b = rgb.blue
    val top = max(r, max(g, b))
    val bottom = min(r, min(g, b))
    val chroma = top - bottom
    val lightness = (top + bottom) / 2f
    // The denominator is how much room is left either side of the lightness, and
    // it closes to nothing at both ends — so white and black are unsaturated by
    // construction rather than by a special case.
    val room = 1f - abs(2f * lightness - 1f)
    return Hsl(
        hue = hueOf(r, g, b, top, chroma),
        saturation = if (room <= 0f) 0f else (chroma / room).coerceIn(0f, 1f),
        lightness = lightness,
    )
}

/** The colour these three describe, at [alpha]. */
fun Hsl.toColour(alpha: Float = 1f): Color {
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val chroma = (1f - abs(2f * l - 1f)) * s
    val (r, g, b) = rgbOf(hue, chroma, l - chroma / 2f)
    return Color(r, g, b, alpha.coerceIn(0f, 1f))
}

/**
 * This colour as `#RRGGBB`, or `#RRGGBBAA` when [includeAlpha] is set.
 *
 * **Alpha last, which is not the order a `Color` literal is written in.**
 * `Color(0xFF3355AA)` is ARGB because that is the packed integer; every design
 * tool, every CSS file and every hex a user has ever typed puts alpha at the
 * end. This is the one a person reads and writes, so it is the one they already
 * know. [colourFromHex] accepts both lengths and the three-digit shorthands.
 */
fun Color.toHex(includeAlpha: Boolean = false): String {
    val rgb = convert(ColorSpaces.Srgb)
    val body = byteOf(rgb.red) + byteOf(rgb.green) + byteOf(rgb.blue)
    return "#" + if (includeAlpha) body + byteOf(rgb.alpha) else body
}

/**
 * The colour [text] spells, or `null` if it does not spell one.
 *
 * Accepts `#RGB`, `#RGBA`, `#RRGGBB` and `#RRGGBBAA`, with or without the hash,
 * in either case, with surrounding whitespace. The three- and four-digit forms
 * double each digit, so `#0f8` is `#00ff88` — the CSS rule, and not the same as
 * padding with zeroes.
 *
 * **Null rather than a throw, and null rather than black.** This is what is
 * behind a text field somebody is halfway through typing into: `#33` is not an
 * error to report, it is a colour that is not finished. A parse that threw would
 * have to be wrapped in a try at every call site, and one that returned black
 * would repaint the picker on the way to every colour beginning with a 0.
 */
fun colourFromHex(text: String): Color? {
    val digits = text.trim().removePrefix("#")
    if (digits.any { it.digitToIntOrNull(16) == null }) return null
    val expanded = when (digits.length) {
        3, 4 -> digits.flatMap { listOf(it, it) }.joinToString("")
        6, 8 -> digits
        else -> return null
    }
    fun at(index: Int) = expanded.substring(index * 2, index * 2 + 2).toInt(16) / 255f
    return Color(
        red = at(0),
        green = at(1),
        blue = at(2),
        alpha = if (expanded.length == 8) at(3) else 1f,
    )
}

/**
 * The hue both cylinders share, in degrees.
 *
 * Which channel is on top decides which sixth of the wheel this is; the other two
 * decide how far along it. The `+ 6f` before the modulo is what keeps a hue just
 * below red positive — without it, a colour a degree anticlockwise of red comes
 * back as -1 rather than 359, and a hue slider driven by it jumps to the far end.
 */
private fun hueOf(r: Float, g: Float, b: Float, top: Float, chroma: Float): Float {
    if (chroma <= 0f) return 0f
    val sixth = when (top) {
        r -> (g - b) / chroma
        g -> 2f + (b - r) / chroma
        else -> 4f + (r - g) / chroma
    }
    return ((sixth + 6f) % 6f) * 60f
}

/**
 * The three channels for a hue, a chroma and the amount to lift them all by.
 *
 * HSV and HSL differ only in what they put in [chroma] and [floor], so the wheel
 * itself is written once. Returning a `Triple` rather than three out-parameters
 * costs one allocation per conversion and is not on any frame's hot path — the
 * picker converts when the colour changes, not when it draws.
 */
private fun rgbOf(hue: Float, chroma: Float, floor: Float): Triple<Float, Float, Float> {
    val sixth = ((hue % 360f) + 360f) % 360f / 60f
    val second = chroma * (1f - abs(sixth % 2f - 1f))
    val (r, g, b) = when (sixth.toInt()) {
        0 -> Triple(chroma, second, 0f)
        1 -> Triple(second, chroma, 0f)
        2 -> Triple(0f, chroma, second)
        3 -> Triple(0f, second, chroma)
        4 -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    return Triple(
        (r + floor).coerceIn(0f, 1f),
        (g + floor).coerceIn(0f, 1f),
        (b + floor).coerceIn(0f, 1f),
    )
}

/** One channel as two uppercase hex digits. */
private fun byteOf(channel: Float): String =
    (channel.coerceIn(0f, 1f) * 255f).roundToInt().toString(16).uppercase().padStart(2, '0')
