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
 *         indicator = ScaleColours.bands {
 *             band(from = 0f, colour = green)
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
 * The values a band starts and stops at are the ones the dial reads — revolutions,
 * degrees, decibels — and the dial places them on its own `valueRange` when it
 * draws. The scale is said once, on the dial, and the bands never repeat it: a band
 * runs until the next one starts, and the last one to wherever the scale ends.
 *
 * **Any stretch no band covers is the dial's own colour** — the theme's accent, as
 * an unbanded dial is. That is the scale before the first band starts, and the gap
 * after a band given an `until`.
 */
@Immutable
class ScaleColours @PublishedApi internal constructor(
    /** A solid colour or an even gradient; empty for bands. */
    private val colours: List<Color>,
    /** The bands, in the dial's units; null for a solid colour or a gradient. */
    private val bands: List<ColourBand>?,
    private val smoothing: Float,
) {

    /**
     * The colour stops along the scale, as fractions of it from 0 at the start to 1
     * at the end, for a dial reading [range] whose own colour is [default]. Ascending;
     * two at one place is a hard edge.
     */
    internal fun stops(range: ClosedFloatingPointRange<Float>, default: Color): List<Pair<Float, Color>> {
        val bands = bands ?: return if (colours.size == 1) {
            listOf(0f to colours[0])
        } else {
            val last = colours.lastIndex.toFloat()
            colours.mapIndexed { index, colour -> index / last to colour }
        }

        val span = range.endInclusive - range.start
        fun fractionOf(value: Float): Float =
            if (span <= 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)

        // The runs of colour from one end of the scale to the other, the gaps
        // between bands included: a band stops at its `until` or where the next one
        // starts, whichever is first, so two bands never overlap and a later start
        // always wins. In the order they fall on the scale, whatever order given.
        val sorted = bands.sortedBy { it.from }
        val runs = mutableListOf<Triple<Float, Float, Color>>()
        var reached = 0f
        sorted.forEachIndexed { index, band ->
            val start = fractionOf(band.from)
            val stop = listOfNotNull(band.until, sorted.getOrNull(index + 1)?.from).minOrNull()
            val end = if (stop == null) 1f else fractionOf(stop)
            if (end <= start) return@forEachIndexed
            if (start > reached) runs += Triple(reached, start, default)
            runs += Triple(start, end, band.colour)
            reached = end
        }
        if (reached < 1f) runs += Triple(reached, 1f, default)

        val share = smoothing.coerceIn(0f, 1f)
        return buildList {
            add(0f to runs.first().third)
            for (index in 0 until runs.lastIndex) {
                val (from, edge, colour) = runs[index]
                val (_, to, next) = runs[index + 1]
                // Each blend reaches at most halfway into the runs either side, so at
                // full smoothing a band is its own colour only at its middle and
                // blends the rest of the way to its neighbours.
                val half = share * minOf(edge - from, to - edge) / 2f
                add(edge - half to colour)
                add(edge + half to next)
            }
            add(1f to runs.last().third)
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ScaleColours && other.colours == colours && other.bands == bands &&
            other.smoothing == smoothing

    override fun hashCode(): Int {
        var result = colours.hashCode()
        result = 31 * result + bands.hashCode()
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
         * Bands of colour at values on the scale, each from where it starts until the
         * next begins — or until its own `until`.
         *
         * ```kotlin
         * ScaleColours.bands {
         *     band(from = 0f, colour = green)
         *     band(from = 6_000f, colour = amber)
         *     band(from = 8_000f, colour = red)
         * }
         * ```
         *
         * @param smoothing How far each edge between two runs of colour blends, from
         *   0 — hard edges, one colour and then the next — to 1, where each band is
         *   its own colour only at its middle and blends the rest of the way to its
         *   neighbours. A blend never reaches further than halfway into either side,
         *   so a narrow band keeps its colour at its middle however smooth the rest.
         * @param content The bands, in the dial's own units and in any order. Run in
         *   place — the function is inline — so theme colours can be read inside it.
         */
        inline fun bands(
            smoothing: Float = 0f,
            content: ColourBandScope.() -> Unit,
        ): ScaleColours {
            val scope = ColourBandScopeImpl().apply(content)
            return ScaleColours(emptyList(), scope.bands.toList(), smoothing)
        }
    }
}

/** The bands of a [ScaleColours.bands]. */
@Stable
@LayoutScopeMarker
interface ColourBandScope {

    /**
     * The scale is [colour] from [from] until the next band starts, or until [until]
     * if that comes first — values on the dial's own scale. Past [until], until the
     * next band, the scale is the dial's own colour. A value past either end of the
     * scale is held at it.
     *
     * @param until Where this band stops, if it stops before the next one starts.
     *   Null runs it on to the next band, or to the end of the scale.
     */
    fun band(from: Float, colour: Color, until: Float? = null)
}

/** One band: [colour] from [from] to [until], in the dial's units. */
@PublishedApi
internal data class ColourBand(val from: Float, val until: Float?, val colour: Color)

@PublishedApi
internal class ColourBandScopeImpl : ColourBandScope {
    val bands = mutableListOf<ColourBand>()

    override fun band(from: Float, colour: Color, until: Float?) {
        bands += ColourBand(from, until, colour)
    }
}
