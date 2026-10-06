package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.kontour.ui.components.datetime.CalendarMonth
import io.kontour.ui.components.datetime.CalendarMonthDefaults
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.pressable
import io.kontour.ui.locale.NameWidth
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import io.kontour.ui.theme.toneColours
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * Twelve small months, as many to a row as fit, each busy day marked in the
 * tone of its first event. A month's name opens the month; a day, the day.
 */
@Composable
internal fun YearView(scope: CalendarScope) {
    val pager = scope.pager
    val swipe = with(pager) { Modifier.swipesPeriods(scope.enabled, scope.edit, scope.touch) }
    val tones = Tone.entries.associateWith { toneColours(it).solid }
    BoxWithConstraints(Modifier.fillMaxWidth().then(swipe)) {
        val columns = (maxWidth / CalendarMonthDefaults.MaxWidth).toInt().coerceIn(1, MaxColumns)
        val scrolls = constraints.hasBoundedHeight
        val scroll = rememberScrollState()
        PeriodStrip(pager, Modifier.fillMaxWidth()) { year ->
            YearPage(
                scope = scope,
                year = year,
                columns = columns,
                tones = tones,
                modifier = if (scrolls) Modifier.verticalScroll(scroll, flingBehavior = ScrollDefaults.flingBehavior()) else Modifier,
            )
        }
    }
}

private const val MaxColumns = 4

@Composable
private fun YearPage(scope: CalendarScope, year: Int, columns: Int, tones: Map<Tone, Color>, modifier: Modifier) {
    val first = LocalDate(year, 1, 1)
    val busy = remember(scope.events, year) {
        val last = LocalDate(year, 12, 31)
        val marks = HashMap<LocalDate, Tone>()
        for (event in scope.eventsBetween(first, last).sortedBy { it.start }) {
            var day = maxOf(event.start.date, first)
            val end = if (event.end > event.start) event.end.plusMinutes(-1).date else event.start.date
            while (day <= end && day <= last) {
                marks.getOrPut(day) { event.tone }
                day = day.plus(1, DateTimeUnit.DAY)
            }
        }
        marks
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        for (row in Month.entries.chunked(columns)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
                for (month in row) {
                    val firstOfMonth = LocalDate(year, month, 1)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            scope.formats.monthName(month, NameWidth.Wide),
                            style = Theme.typography.titleSmall,
                            maxLines = 1,
                            modifier = Modifier
                                .pressable(
                                    onClick = { scope.state.show(firstOfMonth, CalendarView.Month) },
                                    shape = Theme.shapes.capsule,
                                    enabled = scope.enabled,
                                    minimumTouchTarget = false,
                                )
                                .semantics { heading() }
                                .padding(horizontal = Theme.spacing.xs, vertical = Theme.spacing.xxs),
                        )
                        CalendarMonth(
                            month = firstOfMonth,
                            isSelected = { false },
                            onSelectedChange = { day -> scope.state.show(day, CalendarView.Day) },
                            enabled = scope.enabled,
                            today = scope.today,
                            markerFor = { day -> busy[day]?.let(tones::getValue) },
                            formats = scope.formats,
                            firstDayOfWeek = scope.state.periods.firstDayOfWeek,
                        )
                    }
                }
                // A short last row keeps its months the width of the rest.
                repeat(columns - row.size) { Column(Modifier.weight(1f)) {} }
            }
        }
    }
}
