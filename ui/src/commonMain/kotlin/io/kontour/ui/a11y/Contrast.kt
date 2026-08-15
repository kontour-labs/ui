package io.kontour.ui.a11y

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.LocalContrastLevel
import io.kontour.ui.theme.Theme
import kotlin.math.max
import kotlin.math.min

/**
 * The contrast thresholds the design system holds itself to.
 *
 * Sourced from WCAG 2.1 success criteria 1.4.3 (Contrast Minimum), 1.4.6
 * (Contrast Enhanced) and 1.4.11 (Non-text Contrast).
 */
object ContrastThreshold {
    /** AA body text, and any text below 18pt / 14pt-bold. */
    const val BODY_TEXT = 4.5f

    /** AA large text — 18pt and up, or 14pt bold and up. */
    const val LARGE_TEXT = 3.0f

    /** AA non-text: the boundary of an interactive control, focus indicators, icons. */
    const val NON_TEXT = 3.0f

    /** AAA body text. What [io.kontour.ui.theme.ContrastLevel.High] targets. */
    const val BODY_TEXT_ENHANCED = 7.0f

    /** AAA large text. */
    const val LARGE_TEXT_ENHANCED = 4.5f
}

/**
 * The WCAG contrast ratio between two colours, from `1.0` (identical) to `21.0`
 * (black on white).
 *
 * A translucent [foreground] is composited over [background] first, because a
 * ratio computed against an un-composited colour is meaningless — it is the
 * blended result the eye actually sees. [background] is assumed opaque; if it
 * is not, composite it over its own ground before calling.
 *
 * ```
 * contrastRatio(Color(0xFFBB86FC), Color.White)  // 2.10 — fails body text
 * contrastRatio(Color(0xFF6D28D9), Color.White)  // 7.10 — passes AAA
 * ```
 */
fun contrastRatio(foreground: Color, background: Color): Float {
    val resolved = if (foreground.alpha < 1f) foreground.compositeOver(background) else foreground
    val a = resolved.luminance()
    val b = background.luminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}

/** Whether [foreground] on [background] clears [threshold]. */
fun meetsContrast(
    foreground: Color,
    background: Color,
    threshold: Float = ContrastThreshold.BODY_TEXT,
): Boolean = contrastRatio(foreground, background) >= threshold

/**
 * Picks whichever of [light] or [dark] reads better on [background].
 *
 * For colours the design system does not control — a route colour straight out
 * of a GTFS feed, a user-chosen accent, an image's dominant tone — where the
 * label has to stay legible whatever arrives.
 *
 * ```
 * val labelColor = contentColorFor(routeColor)   // white on a dark route, ink on a yellow one
 * ```
 */
fun contentColorFor(
    background: Color,
    light: Color = Color.White,
    dark: Color = Color(0xFF121212),
): Color = if (contrastRatio(light, background) >= contrastRatio(dark, background)) light else dark

/** Whether the user has asked for the enhanced-contrast tier. */
@Composable
@ReadOnlyComposable
fun highContrast(): Boolean = LocalContrastLevel.current == ContrastLevel.High

/**
 * An edge for a container that has none, at [ContrastLevel.High] only.
 *
 * ```kotlin
 * Surface(border = border ?: contrastEdge()) { … }
 * ```
 *
 * This is the half of the high-contrast work that a darker `outline` cannot do
 * on its own. A component with a border gets a stronger one for free when the
 * scheme changes; a component *without* one gets nothing, and most of the
 * library's containers are in the second group by design. An elevated `Card` is
 * white on white with a shadow for an edge — a shadow that does not change
 * between tiers, and measures **1.3:1** against the page in high-contrast dark.
 * `TextFieldVariant.Filled` set its border to `Color.Transparent` at every tier,
 * which is the same problem stated outright.
 *
 * Returns `null` at [ContrastLevel.Standard], so a call site reads as "this
 * container's border, or an edge if the user needs one" and the standard tier is
 * untouched by construction. Every high-contrast golden in the suite moved when
 * this landed and no standard-contrast golden did, which is the shape the change
 * was supposed to have.
 *
 * @param color Defaults to [io.kontour.ui.theme.ColorScheme.outline], which the
 *   high-contrast schemes already darken to near-ink. Pass a tone's own border
 *   where the container has one, so the edge stays part of the component rather
 *   than a grey rectangle drawn around it.
 */
@Composable
@ReadOnlyComposable
fun contrastEdge(color: Color = Theme.colors.outline): BorderStroke? =
    if (highContrast()) BorderStroke(Theme.sizing.borderWidth, color) else null
