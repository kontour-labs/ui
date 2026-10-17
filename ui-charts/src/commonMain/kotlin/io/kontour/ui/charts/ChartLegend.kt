package io.kontour.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.fillPattern
import io.kontour.ui.theme.Theme

/**
 * A chart's legend: each named series, with a swatch drawn the way the series
 * is — a dash of its line with its symbol, or a square of its fill — so that a
 * reader matches a series by its pattern as well as its colour.
 *
 * Kept out of what a screen reader hears, which names every series already in
 * the chart's summary; read again, the legend would be the same words twice.
 */
@Composable
internal fun ChartLegend(
    model: ChartModel,
    colours: ChartColours,
    patterns: Boolean,
    stacked: Boolean,
) {
    val named = model.series.filter { it.name.isNotBlank() }
    val spacing = Theme.spacing
    val item: @Composable (ChartSeries) -> Unit = { series ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            SeriesSwatch(model, series, colours, patterns)
            Text(series.name, style = Theme.typography.labelMedium)
        }
    }
    val hidden = Modifier.clearAndSetSemantics { }
    if (stacked) {
        Column(hidden, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) { named.forEach { item(it) } }
    } else {
        FlowRow(
            modifier = hidden,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            named.forEach { item(it) }
        }
    }
}

/**
 * A series' key, drawn the way the series is — a dash of its line with its
 * symbol, or a square of its fill — for the legend and a tooltip's rows alike,
 * so that a series is told by the same mark wherever it is named.
 */
@Composable
internal fun SeriesSwatch(model: ChartModel, series: ChartSeries, colours: ChartColours, patterns: Boolean) {
    val mark = model.marks.firstOrNull { mark -> mark.runs.any { it.series == series } }
    val look = lookOf(model.slotOf(series), colours.series, patterns, mark?.style, series.style)
    // Read once: a series with no mark yet has a square swatch, as a bar's.
    val kind = mark?.kind
    if (mark == null || (kind != MarkKind.Line && kind != MarkKind.Point)) {
        FillSwatch(look, colours)
        return
    }
    val markSymbol = mark.symbol
    val symbolShown = kind == MarkKind.Point || mark.showPoints || patterns
    val sizing = Theme.sizing
    Spacer(
        Modifier
            .size(width = LineSwatchWidth, height = SwatchSize)
            .drawBehind {
                if (kind == MarkKind.Line) {
                    val width = sizing.borderWidthStrong.toPx()
                    val path = Path().apply {
                        moveTo(0f, size.height / 2)
                        lineTo(size.width, size.height / 2)
                    }
                    drawPath(path, look.colour, style = Stroke(width, cap = StrokeCap.Round, pathEffect = look.line.effect(width)))
                }
                if (symbolShown) {
                    val symbol = Path()
                    addSymbol(symbol, markSymbol ?: look.symbol, Offset(size.width / 2, size.height / 2), ChartDefaults.PointSize.toPx())
                    drawPath(symbol, look.colour)
                }
            },
    )
}

/**
 * A square of [look]'s fill, its pattern over its colour: a bar's, an area's
 * or a sector's key. The library's own [fillPattern] and [contrastEdge], so a
 * swatch is hatched as an event on a calendar is, and edged at the high
 * contrast tier as an activity calendar's key is.
 */
@Composable
internal fun FillSwatch(look: SeriesLook, colours: ChartColours) {
    val shape = Theme.shapes.extraSmall
    val edge = contrastEdge()
    Box(
        Modifier
            .size(SwatchSize)
            .background(look.colour, shape)
            .fillPattern(look.fill, colours.patternInk, shape, SwatchTile)
            .then(if (edge != null) Modifier.border(edge, shape) else Modifier),
    )
}

/**
 * A legend's swatch: the size of a small label's capital letters, so it sits
 * in the line of text it introduces. A line's is longer, to have room for its
 * dashes and its symbol together.
 */
private val SwatchSize = 12.dp
private val LineSwatchWidth = 20.dp

/** A swatch's pattern, finer than the plot's, so that two repeats fit in it. */
private val SwatchTile = 6.dp
