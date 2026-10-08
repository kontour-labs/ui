package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.graphicsLayer
import io.kontour.ui.components.datetime.CalendarMonthOutline
import kotlinx.coroutines.channels.Channel
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
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
        // **Lazy where it scrolls.** A year is twelve months of forty-odd days,
        // each a control; composed all at once, switching to the year took
        // several times a month's first frame — felt as the switch lagging —
        // for the two or three months a phone shows. A row at a time, as it
        // comes into view. Given no height, there is nothing to scroll and
        // nothing to leave out: every row, in a column.
        val scrolls = constraints.hasBoundedHeight
        // Opened at the month the calendar is on — its row at the top — rather
        // than at January, wherever in the year that is.
        val openAt = (scope.state.date.month.ordinal / columns)
        val stagger = remember { Stagger() }
        LaunchedEffect(stagger) { stagger.run() }
        PeriodStrip(pager, Modifier.fillMaxWidth()) { year ->
            // A list of its own to each year: two are side by side while the
            // pager slides, and a lazy list's state belongs to one list.
            val list = rememberLazyListState(initialFirstVisibleItemIndex = openAt)
            YearPage(scope = scope, year = year, columns = columns, tones = tones, lazy = if (scrolls) list else null, stagger = stagger)
        }
    }
}

private const val MaxColumns = 4

@Composable
private fun YearPage(scope: CalendarScope, year: Int, columns: Int, tones: Map<Tone, Color>, lazy: LazyListState?, stagger: Stagger) {
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
    val rows = remember(columns) { Month.entries.chunked(columns) }
    val gap = Theme.spacing.md
    if (lazy != null) {
        LazyColumn(
            state = lazy,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap),
            flingBehavior = ScrollDefaults.flingBehavior(),
        ) {
            items(rows.size, key = { it }) { index -> YearRow(scope, year, rows[index], columns, busy, tones, stagger) }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in rows) YearRow(scope, year, row, columns, busy, tones, stagger)
        }
    }
}

/** One row of a year's months, each as wide as the rest. */
@Composable
private fun YearRow(
    scope: CalendarScope,
    year: Int,
    row: List<Month>,
    columns: Int,
    busy: Map<LocalDate, Tone>,
    tones: Map<Tone, Color>,
    stagger: Stagger,
) {
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
                // Its turn: the days, faded in if they had to wait for it.
                val turn = remember(stagger) { stagger.take() }
                val waited = remember(stagger) { !stagger.reached(turn) }
                if (stagger.reached(turn)) {
                    val shown = remember { Animatable(if (waited) 0f else 1f) }
                    val fade = Theme.motion
                    LaunchedEffect(shown) { if (waited) shown.animateTo(1f, fade.tweenFast()) }
                    CalendarMonth(
                        month = firstOfMonth,
                        isSelected = { false },
                        onSelectedChange = { day -> scope.state.show(day, CalendarView.Day) },
                        enabled = scope.enabled,
                        today = scope.today,
                        markerFor = { day -> busy[day]?.let(tones::getValue) },
                        formats = scope.formats,
                        firstDayOfWeek = scope.state.periods.firstDayOfWeek,
                        modifier = Modifier.graphicsLayer { alpha = shown.value },
                    )
                } else {
                    CalendarMonthOutline(month = firstOfMonth, formats = scope.formats, firstDayOfWeek = scope.state.periods.firstDayOfWeek)
                }
            }
        }
        // A short last row keeps its months the width of the rest.
        repeat(columns - row.size) { Column(Modifier.weight(1f)) {} }
    }
}

/**
 * The months' turns to compose their days, one a frame. A month's days are
 * forty-odd controls, and the two or three a phone shows, composed in the frame
 * the view switched, were the frame the switch took — felt as it lagging. So a
 * month takes its place at once, as an outline as tall as it will be, and its
 * days arrive a frame apart, the first of them — the month the calendar is on,
 * at the top — in the switch's own frame.
 */
@Stable
private class Stagger {
    private var issued = 0
    private var released by mutableIntStateOf(1)
    private val waiting = Channel<Unit>(Channel.CONFLATED)

    /** The next turn. */
    fun take(): Int {
        waiting.trySend(Unit)
        return issued++
    }

    /** Whether [turn] has come. */
    fun reached(turn: Int): Boolean = turn < released

    /** A turn a frame while there are turns waiting; nothing, not even a frame, while there are none. */
    suspend fun run() {
        while (true) {
            if (released >= issued) waiting.receive()
            withFrameNanos { }
            if (released < issued) released++
        }
    }
}
