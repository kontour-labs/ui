package io.kontour.ui.components.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.locale.NameWidth
import kotlinx.datetime.DayOfWeek

/**
 * The widest abbreviated weekday name in [formats]' language, set in [style]:
 * what a column has to be to show weekdays abbreviated rather than as letters.
 *
 * Measured rather than assumed. A month's and a week's headings went to letters
 * below a fixed 40dp and 48dp, which holds "Wed" at 100% type in English and
 * holds neither 200% type nor a language whose abbreviations are longer, where
 * the name was cut at the column's edge instead. The fixed widths stay as the
 * least, so nothing at 100% in English changes.
 */
@Composable
internal fun widestWeekdayName(formats: LocaleFormats, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(formats, style, density) {
        val widest = DayOfWeek.entries.maxOf { day ->
            measurer.measure(formats.weekdayName(day, NameWidth.Abbreviated), style).size.width
        }
        with(density) { widest.toDp() }
    }
}

/**
 * Whether a column [column] wide shows weekdays abbreviated: when it has room
 * for the [widest] of them and is no narrower than the grid's own [least].
 */
internal fun abbreviatesWeekdays(column: Dp, widest: Dp, least: Dp): Boolean = column >= maxOf(least, widest)
