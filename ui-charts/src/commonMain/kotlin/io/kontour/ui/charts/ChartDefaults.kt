package io.kontour.ui.charts

import io.kontour.ui.foundation.FillPattern
import io.kontour.ui.foundation.FillPatternDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.differentiateWithoutColour
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.locale.LocalPseudoLocale
import io.kontour.ui.foundation.LocalContainerColour
import io.kontour.ui.components.datetime.ActivityCalendarDefaults
import io.kontour.ui.theme.Theme

/** What [Chart], [SectorChart] and [Sparkline] are drawn with unless told otherwise. */
object ChartDefaults {

    /**
     * Every colour from the theme: the series from the palette for its scheme
     * and contrast tier (or [LocalSeriesColours]), the gridlines in the faintest
     * rule the scheme has, labels in its muted ink, and patterns in a wash of the
     * page's own ground.
     *
     * The [ground] is what the chart sits on: the nearest surface's colour, or
     * the page's. It was the surface colour always, so on a card or the page a
     * sector's gaps, a point's halo and a pin's fill were a different shade
     * from the ground around them.
     */
    @Composable
    @ReadOnlyComposable
    fun colours(
        series: List<Color> = seriesColours(),
        grid: Color = Theme.colours.outlineSubtle,
        axis: Color = Theme.colours.outline,
        label: Color = Theme.colours.contentMuted,
        selection: Color = Theme.colours.contentMuted,
        patternInk: Color = Theme.colours.background.copy(alpha = PatternInkAlpha),
        ground: Color = LocalContainerColour.current.takeOrElse { Theme.colours.background },
        ramp: List<Color> = ramp(full = series.firstOrNull() ?: seriesColours().first()),
    ): ChartColours = ChartColours(series, grid, axis, label, selection, patternInk, ground, ramp)

    /**
     * A heat map's shades, from [empty] to [full], blended as an activity
     * calendar's are, the first a step clear of [empty] so the least value is
     * plainly a value. [full] is the first series' colour, so a heat map is in
     * the charts' colours beside them, and follows dark mode and the contrast
     * tier with the palette. It was the theme's primary, an activity
     * calendar's, which in the library's own theme is an ink near black: a heat
     * map beside violet bars read as a different component.
     */
    @Composable
    @ReadOnlyComposable
    fun ramp(
        empty: Color = Theme.colours.surfaceSunken,
        full: Color = seriesColours().first(),
    ): List<Color> = withoutEmpty(ActivityCalendarDefaults.colours(levels = RampStops, empty = empty, full = full).levels)

    /**
     * The series palette for the scheme and contrast tier in use, or the app's
     * own from [LocalSeriesColours].
     */
    @Composable
    @ReadOnlyComposable
    fun seriesColours(): List<Color> {
        LocalSeriesColours.current?.let { return it }
        val dark = Theme.colours.isDark
        return when {
            highContrast() && dark -> SeriesPalettes.HighContrastDark
            highContrast() -> SeriesPalettes.HighContrastLight
            dark -> SeriesPalettes.Dark
            else -> SeriesPalettes.Light
        }
    }

    /**
     * Whether a chart draws its patterns — dashes, symbols and hatching — as
     * well as its colours: at the high contrast tier, and wherever the reader
     * has asked for things not to be told apart by colour alone.
     */
    @Composable
    fun showPatterns(): Boolean = highContrast() || differentiateWithoutColour()

    /**
     * The words a chart says on its own: [LocalChartStrings]', in the
     * pseudo-locale the gallery is drawing in, if it is drawing in one.
     *
     * Remembered in a pseudo-locale: its phrases are new functions each time
     * they are made, so made at every call, each chart took them for new words
     * and wrote its description again at every recomposition.
     */
    @Composable
    fun strings(): ChartStrings {
        val strings = LocalChartStrings.current
        val pseudo = LocalPseudoLocale.current ?: return strings
        return remember(strings, pseudo) { strings.pseudoLocalised(pseudo) }
    }

    /** The size of a point's symbol. */
    val PointSize: Dp = 7.dp

    /** How much of a band a bar fills, the rest being the gap between bars. */
    const val BarFraction: Float = 0.7f

    /** The gap between a sector chart's sectors. */
    val SectorGap: Dp = 2.dp

    /**
     * The most places a screen reader moves through along a chart. A chart of a
     * year of days is 365 places, which nobody swipes through one at a time, so
     * past this they are said in ranges — "1 March to 7 March" — each with the
     * least and the most of every series in it.
     */
    const val MaxAccessiblePlaces: Int = 60

    /** The height a chart takes when nothing gives it one. */
    val Height: Dp = 200.dp

    /** How strongly a pattern is drawn over a series' colour. */
    const val PatternInkAlpha: Float = 0.6f
}

/** A series' look, resolved from its slot and whatever overrides it. */
internal class SeriesLook(
    val colour: Color,
    val line: LinePattern,
    val symbol: PointSymbol,
    val pattern: FillPattern,
)

/**
 * What series [slot] is drawn with: its colour from [palette], and with
 * [patterns] on, a dash, a symbol and a fill of its own, each in the order its
 * enum or [FillPatternDefaults.Series] lists them, so that slot 0 is always solid. Past the end of the palette,
 * colours go round again with patterns on, so the second time round is told
 * apart from the first.
 */
internal fun lookOf(
    slot: Int,
    palette: List<Color>,
    patterns: Boolean,
    vararg overrides: SeriesStyle?,
): SeriesLook {
    val patterned = patterns || slot >= palette.size
    fun <T> pick(entries: List<T>, plain: T): T = if (patterned) entries[slot % entries.size] else plain
    val override = overrides.filterNotNull()
    return SeriesLook(
        colour = override.firstNotNullOfOrNull { it.colour.takeIf { colour -> colour != Color.Unspecified } }
            ?: palette[slot % palette.size],
        line = override.firstNotNullOfOrNull { it.line } ?: pick(LinePattern.entries, LinePattern.Solid),
        symbol = override.firstNotNullOfOrNull { it.symbol } ?: pick(PointSymbol.entries, PointSymbol.Circle),
        pattern = override.firstNotNullOfOrNull { it.pattern } ?: pick(FillPatternDefaults.Series, FillPattern.Solid),
    )
}

/** How many shades a heat map's ramp is blended through: an activity calendar's levels. */
private const val RampStops = 4

/** An activity calendar's levels past its first, which is a day with nothing: a heat map's least cell is still a value. */
private fun withoutEmpty(levels: List<Color>): List<Color> = levels.drop(1)
