package io.kontour.ui.components.display

import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

/**
 * The colour along a dial's scale — what a [Gauge]'s fill, or a
 * [io.kontour.ui.components.selection.Knob]'s, is painted with.
 *
 * ```kotlin
 * Gauge(
 *     value = rpm,
 *     valueRange = 0f..10_000f,
 *     colours = GaugeDefaults.colours(
 *         indicator = ScaleColours.bands(start = green) {
 *             band(from = 6_000f, colour = amber)
 *             band(from = 8_000f, colour = red)
 *         },
 *     ),
 * )
 * ```
 *
 * One colour, a gradient, or bands. Whichever it is, **a colour belongs to a place
 * on the scale, not to the fill**: the part of the arc at 7,000 is amber whether the
 * fill stops there or carries on to the red, so a reading's colour says where it is.
 *
 * ### Bands are in the dial's own units
 *
 * The values a band starts at are the ones the dial reads — revolutions, degrees,
 * decibels — and the dial maps them onto its own `valueRange` when it draws. The
 * scale is said once, on the dial, and the bands never repeat it: the first colour
 * runs from wherever the scale starts, and the last runs to wherever it ends.
 */
@Immutable
class ScaleColours @PublishedApi internal constructor(
    private val colours: List<Color>,
    /** Where each colour after the first starts, in the dial's units; null for an even gradient. */
    private val starts: List<Float>?,
    private val smoothing: Float,
) {

    /**
     * The colour stops along the scale, as fractions of it from 0 at the start to 1
     * at the end, for a dial reading [range]. Ascending; two at one place is a hard
     * edge.
     */
    internal fun stops(range: ClosedFloatingPointRange<Float>): List<Pair<Float, Color>> {
        if (colours.size == 1) return listOf(0f to colours[0])
        if (starts == null) {
            val last = colours.lastIndex.toFloat()
            return colours.mapIndexed { index, colour -> index / last to colour }
        }

        val span = range.endInclusive - range.start
        fun fractionOf(value: Float): Float =
            if (span <= 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)

        // In the order they fall on the scale, whatever order they were given in.
        val bands = starts.indices.sortedBy { starts[it] }
        val edges = bands.map { fractionOf(starts[it]) }
        val colourOf = listOf(colours[0]) + bands.map { colours[it + 1] }
        val share = smoothing.coerceIn(0f, 1f)

        return buildList {
            add(0f to colourOf[0])
            edges.forEachIndexed { index, edge ->
                // Each blend reaches at most halfway into the bands either side, so
                // at full smoothing a band is its own colour only at its middle and
                // blends the rest of the way to its neighbours.
                val before = edge - (edges.getOrNull(index - 1) ?: 0f)
                val after = (edges.getOrNull(index + 1) ?: 1f) - edge
                val half = share * minOf(before, after) / 2f
                add(edge - half to colourOf[index])
                add(edge + half to colourOf[index + 1])
            }
            add(1f to colourOf.last())
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ScaleColours && other.colours == colours && other.starts == starts &&
            other.smoothing == smoothing

    override fun hashCode(): Int {
        var result = colours.hashCode()
        result = 31 * result + starts.hashCode()
        result = 31 * result + smoothing.hashCode()
        return result
    }

    companion object {
        /** One colour the whole way along. */
        fun solid(colour: Color): ScaleColours = ScaleColours(listOf(colour), null, 0f)

        /**
         * [colours] spread evenly along the scale, blending from one to the next: the
         * first at the start, the last at the end.
         */
        fun gradient(colours: List<Color>): ScaleColours {
            require(colours.isNotEmpty()) { "ScaleColours.gradient needs at least one colour." }
            return ScaleColours(colours.toList(), null, 0f)
        }

        /**
         * Bands of colour at values on the scale: [start] from the start of the scale,
         * and each [ColourBandScope.band] from its value on until the next one begins.
         *
         * ```kotlin
         * ScaleColours.bands(start = green) {
         *     band(from = 6_000f, colour = amber)
         *     band(from = 8_000f, colour = red)
         * }
         * ```
         *
         * @param start The colour from the start of the scale to the first band.
         * @param smoothing How far each edge between two bands blends, from 0 — hard
         *   edges, one colour and then the next — to 1, where each band is its own
         *   colour only at its middle and blends the rest of the way to its
         *   neighbours. A blend never reaches further than halfway into either band,
         *   so a narrow band keeps its colour at its middle however smooth the rest.
         * @param content The bands, in the dial's own units and in any order. Run in
         *   place — the function is inline — so theme colours can be read inside it.
         */
        inline fun bands(
            start: Color,
            smoothing: Float = 0f,
            content: ColourBandScope.() -> Unit,
        ): ScaleColours {
            val scope = ColourBandScopeImpl().apply(content)
            return ScaleColours(listOf(start) + scope.colours, scope.starts, smoothing)
        }
    }
}

/** The bands of a [ScaleColours.bands]. */
@Stable
@LayoutScopeMarker
interface ColourBandScope {

    /**
     * From [from] on — a value on the dial's own scale — the scale is [colour], until
     * the next band starts. A value past either end of the scale is held at it.
     */
    fun band(from: Float, colour: Color)
}

@PublishedApi
internal class ColourBandScopeImpl : ColourBandScope {
    val starts = mutableListOf<Float>()
    val colours = mutableListOf<Color>()

    override fun band(from: Float, colour: Color) {
        starts += from
        colours += colour
    }
}
