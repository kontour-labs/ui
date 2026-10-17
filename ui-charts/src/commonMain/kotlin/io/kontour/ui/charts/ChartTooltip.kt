package io.kontour.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.theme.Theme

/** One series' line in a chart's tooltip: its key, its name, and its value there. */
internal class TooltipRow(val swatch: @Composable () -> Unit, val name: String, val value: String)

/**
 * What a chart's tooltip says when the caller does not say it: [title], the
 * place, over a [KeyValueList] of the series there, each named beside its
 * legend's swatch, in the tooltip's own slots.
 *
 * The library's own rows, compact, rather than rows laid out here: the values
 * line up down the bubble, a row is said as "Route 950, 12", and a change to
 * how a key and a value sit together is made once. The swatch is the legend's,
 * so a series hatched or dashed with patterns on is told apart by more than
 * its colour here too.
 */
@Composable
internal fun ContentScope.ChartTooltipBody(title: String?, rows: List<TooltipRow>) {
    // The bubble's slots, called on it by name: inside a column of its own,
    // its `+` is the bubble's and not the column's.
    val slots = this
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
        if (!title.isNullOrBlank()) with(slots) { +AnnotatedString(title, Bold) }
        KeyValueList(labelWidth = 0.dp, compact = true) {
            rows.forEach { row ->
                item(
                    label = {
                        row.swatch()
                        if (row.name.isNotBlank()) +row.name
                    },
                ) { +AnnotatedString(row.value, Bold) }
            }
        }
    }
}

private val Bold = SpanStyle(fontWeight = FontWeight.SemiBold)
