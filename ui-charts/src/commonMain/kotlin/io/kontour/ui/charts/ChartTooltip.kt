package io.kontour.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/** One series' line in a chart's tooltip: its key, its name, and its value there. */
internal class TooltipRow(val swatch: @Composable () -> Unit, val name: String, val value: String)

/**
 * What a chart's tooltip says when the caller does not say it: [title], the
 * place, quieter than the figures under it; a row for each series there, its
 * legend's swatch and its name at the start and its value at the end; and
 * [total], where the place's values make one, under a rule.
 *
 * Raised 2026-10-21 for 0.5.0, after *"it still looks a bit rough"*: the title
 * was as loud as the values, and the values sat straight after their names, so
 * a column of figures did not line up down the card. Each value is now at the
 * card's end, in figures of one width, the way a table's numbers are, and the
 * card is as wide as its widest row so that they all end at one edge.
 *
 * Each row is one node to a screen reader, said as "Route 950, 12", as the
 * key and value list it was before said it. The swatch is the legend's, so a
 * series hatched or dashed with patterns on is told apart by more than its
 * colour here too.
 */
@Composable
internal fun ContentScope.ChartTooltipBody(title: String?, rows: List<TooltipRow>, total: TooltipRow? = null) {
    val typography = Theme.typography
    val spacing = Theme.spacing
    Column(
        Modifier.width(IntrinsicSize.Max),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        if (!title.isNullOrBlank()) {
            Text(title, Modifier.padding(bottom = spacing.xxs), colour = Theme.colours.contentMuted, style = typography.labelSmall)
        }
        rows.forEach { row -> TooltipLine(row) }
        if (total != null) {
            HorizontalDivider(Modifier.padding(vertical = spacing.xxs), colour = Theme.colours.outlineSubtle)
            TooltipLine(total)
        }
    }
}

@Composable
private fun TooltipLine(row: TooltipRow) {
    val spacing = Theme.spacing
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        row.swatch()
        if (row.name.isNotBlank()) Text(row.name, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
        // At least this much between a name and its value, and the rest of
        // the card's width once a longer row has set it.
        Spacer(Modifier.widthIn(min = spacing.sm).weight(1f))
        Text(
            row.value,
            style = Theme.typography.labelMedium.copy(fontFeatureSettings = TabularFigures),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            softWrap = false,
        )
    }
}

/** Figures of one width, so a column of them lines up digit under digit. */
private const val TabularFigures = "tnum"
