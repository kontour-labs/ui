package io.kontour.ui.charts

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * What a chart is drawn in, besides its series' own colours.
 *
 * From [ChartDefaults.colours], which takes every one from the theme, so a chart
 * follows dark mode and the contrast tier with nothing said.
 *
 * @property series The palette series take their colours from, in slot order.
 *   A chart with more series than colours goes round again, with patterns on so
 *   that the second time round is told apart from the first.
 * @property grid The gridlines across the plot.
 * @property axis The baseline, and a rule's line.
 * @property label The axes' tick labels and titles.
 * @property selection The line that marks the selected value.
 * @property patternInk What a fill pattern is drawn in, over a series' colour.
 * @property ground The page under the chart: the ring around a selected point,
 *   and the gap between a sector chart's sectors.
 */
@Immutable
data class ChartColours(
    val series: List<Color>,
    val grid: Color,
    val axis: Color,
    val label: Color,
    val selection: Color,
    val patternInk: Color,
    val ground: Color,
) {
    init {
        require(series.isNotEmpty()) { "A chart needs at least one series colour" }
    }
}

/**
 * The series palette for every chart below it, in place of the theme's: for an
 * app whose brand has a palette of its own for data.
 *
 * `null`, the default, takes the one for the scheme and the contrast tier in
 * use. A palette given here is used in every tier, so it has to hold 3:1 against
 * the ground in each, and 4.5:1 at the high tier, which the built-in ones are
 * tested to.
 *
 * ```kotlin
 * CompositionLocalProvider(LocalSeriesColours provides brandDataColours) {
 *     Dashboard()
 * }
 * ```
 */
val LocalSeriesColours = staticCompositionLocalOf<List<Color>?> { null }

/**
 * The series palettes, one for each scheme and tier.
 *
 * Six colours each, starting from the brand's violet and spread around the hue
 * circle in OKLCH. Each was chosen as the one furthest, in OKLab, from those
 * already chosen — under normal vision and under simulated protanopia,
 * deuteranopia and tritanopia, the worst of the four — among colours that hold
 * 3:1 against every ground of its scheme (4.5:1 at the high tier) and keep 40°
 * of hue from the rest, so each one also has a name a reader can say. The order
 * is the order they were chosen in: the first two series are the furthest
 * apart, and a chart of two is the commonest chart.
 *
 * `SeriesPaletteTest` holds them to all of that.
 */
internal object SeriesPalettes {
    val Light = listOf(
        Color(0xFF7F51C1), // violet
        Color(0xFFE05D3D), // coral
        Color(0xFF931157), // plum
        Color(0xFF03999F), // teal
        Color(0xFF7E5403), // ochre
        Color(0xFF025884), // navy
    )

    val Dark = listOf(
        Color(0xFFC28EFB), // lilac
        Color(0xFFE3645E), // coral
        Color(0xFF15E5B4), // mint
        Color(0xFFECBE24), // gold
        Color(0xFF19A4B7), // cyan
        Color(0xFFA9C4FC), // periwinkle
    )

    val HighContrastLight = listOf(
        Color(0xFF6331A0), // violet
        Color(0xFFBD3D1B), // rust
        Color(0xFF710341), // plum
        Color(0xFF0C7D87), // teal
        Color(0xFF014069), // navy
        Color(0xFF604008), // umber
    )

    val HighContrastDark = listOf(
        Color(0xFFC28EFB), // lilac
        Color(0xFF5CF1AC), // mint
        Color(0xFFEF852E), // orange
        Color(0xFFF2CF3B), // yellow
        Color(0xFFB3CAFC), // periwinkle
        Color(0xFF14BBC2), // cyan
    )
}
