package io.kontour.ui.foundation

import kotlin.math.round

/**
 * A line's width, in pixels, made whole: the nearest whole number, and never
 * less than one.
 *
 * A 1dp rule at a density of 1.25 or 1.5 — a browser at 125%, Windows at 150% —
 * is a pixel and a fraction, and a fraction of a pixel can only be drawn as a
 * paler pixel beside it: every rule in a grid came out soft. Whole, it is one
 * crisp pixel, or two.
 */
internal fun snappedLineWidth(width: Float): Float = round(width).coerceAtLeast(1f)

/**
 * Where to centre a line [width] pixels wide — whole, see [snappedLineWidth] —
 * so that it fills whole pixels beside [edge]: after it, or before it.
 *
 * A line centred on a pixel boundary is drawn as two half-strength pixels, one
 * either side, which is a grey smear twice as wide as the line. The calendar's
 * day and hour lines and a table's column lines were drawn that way.
 */
internal fun snappedLineCentre(edge: Float, width: Float, after: Boolean = true): Float =
    round(edge) + if (after) width / 2f else -width / 2f
