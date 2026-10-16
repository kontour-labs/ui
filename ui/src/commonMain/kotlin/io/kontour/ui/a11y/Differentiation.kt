package io.kontour.ui.a11y

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import io.kontour.ui.platform.platformPrefersDifferentiateWithoutColour

/**
 * Whether what tells things apart should be more than their colour: a pattern
 * as well as a hue, a symbol as well as a tint.
 *
 * Colour alone is never meant to carry meaning (WCAG 1.4.1), and the library's
 * components each pair it with something else already: a link is underlined,
 * a selected chip has an edge. This is for the drawings where a second cue
 * costs something in the ordinary case, so is added only when it is wanted:
 * the series of a chart, which are told apart by colour and given hatching and
 * symbols here.
 *
 * It is wanted when the reader has said so. iOS has a setting by this name,
 * *Differentiate Without Colour*; Android's nearest is colour correction, which
 * a reader turns on because they see some colours as one. The web and the
 * desktop have nothing to read, and answer `false`.
 *
 * An app with its own setting provides [LocalDifferentiateWithoutColour], which
 * wins over the platform's.
 */
@Composable
fun differentiateWithoutColour(): Boolean =
    LocalDifferentiateWithoutColour.current ?: platformPrefersDifferentiateWithoutColour()

/**
 * An app's own answer to [differentiateWithoutColour], or `null` — the default —
 * to take the platform's.
 */
val LocalDifferentiateWithoutColour = staticCompositionLocalOf<Boolean?> { null }
