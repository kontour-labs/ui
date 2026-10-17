package io.kontour.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.theme.Theme

/** One series' line in a chart's tooltip: its colour, its name, and its value there. */
internal class TooltipRow(val colour: Color, val name: String, val value: String)

/**
 * What a chart's tooltip says when the caller does not say it: [title], the
 * place, over a line for each series with its colour beside its name and its
 * value, in the tooltip's own slots.
 *
 * It was the place and each series' "name, value" joined into one string, a
 * line apiece, which read as a paragraph rather than as a key. A swatch says
 * which line is which series without the legend, and the value set apart says
 * what is being told.
 */
@Composable
internal fun ContentScope.ChartTooltipBody(title: String?, rows: List<TooltipRow>) {
    val spacing = Theme.spacing
    // The bubble's slots, called on it by name: inside a row or a column of
    // its own, its `+` is the bubble's and not the row's.
    val slots = this
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        if (!title.isNullOrBlank()) with(slots) { +AnnotatedString(title, SpanStyle(fontWeight = FontWeight.SemiBold)) }
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                // Not through `+`: a slot's painter takes the slot's ink, and
                // this is the one mark here whose colour is the point.
                Box(Modifier.size(Theme.sizing.iconSmall / 2).background(row.colour, Theme.shapes.extraSmall))
                with(slots) {
                    if (row.name.isNotBlank()) +row.name
                    +AnnotatedString(row.value, SpanStyle(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

/** [series]'s colour in [chart], as its first mark draws it. */
internal fun seriesColour(chart: ResolvedChart, series: ChartSeries, colours: ChartColours, patterns: Boolean): Color {
    val layer = chart.layers.firstOrNull { it.series == series }
    return lookOf(chart.model.slotOf(series), colours.series, patterns, layer?.mark?.style, series.style).colour
}
