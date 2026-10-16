package io.kontour.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
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
            LegendSwatch(model, series, colours, patterns)
            Text(series.name, style = Theme.typography.labelMedium, colour = Theme.colours.content)
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

@Composable
private fun LegendSwatch(model: ChartModel, series: ChartSeries, colours: ChartColours, patterns: Boolean) {
    val mark = model.marks.firstOrNull { mark -> mark.runs.any { it.series == series } }
    val look = lookOf(model.slotOf(series), colours.series, patterns, mark?.style, series.style)
    val tiles = remember { PatternTiles() }
    val sizing = Theme.sizing
    // Read once: a series with no mark yet has a square swatch, as a bar's.
    val kind = mark?.kind
    val markSymbol = mark?.symbol
    val markShowsPoints = mark?.showPoints == true
    val lineLike = kind == MarkKind.Line || kind == MarkKind.Point
    val symbolShown = kind == MarkKind.Point || (lineLike && (markShowsPoints || patterns))
    Spacer(
        Modifier
            .size(width = if (lineLike) LineSwatchWidth else SwatchSize, height = SwatchSize)
            .drawBehind {
                if (lineLike) {
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
                } else {
                    val swatch = Path().apply {
                        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(ChartDefaults.BarCornerRadius.toPx())))
                    }
                    drawPath(swatch, look.colour)
                    tiles.brush(look.fill, colours.patternInk, SwatchTile.roundToPx(), sizing.borderWidth.toPx())?.let { drawPath(swatch, it) }
                }
            },
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
