package io.kontour.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.interaction.pressable
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.ListStyle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.toneColours

/**
 * One event's box, bar or line, with what the calendar keeps for itself round
 * whatever [content] draws: its colour, its press, its edge at high contrast,
 * and what a screen reader says it is.
 *
 * Short events are below the 48dp a control is otherwise given: a quarter-hour
 * at an hour's 48dp is 12dp, and growing it would cover the next one. A screen
 * reader reaches every event as its own node, and a keyboard by Tab.
 */
@Composable
internal fun EventChip(
    event: CalendarEvent,
    presentation: EventPresentation,
    scope: CalendarScope,
    modifier: Modifier = Modifier,
) {
    val colours = toneColours(event.tone)
    val shape = Theme.shapes.control
    val stripe = Theme.sizing.borderWidthStrong
    val (fill, ink) = when (presentation) {
        EventPresentation.Block -> colours.container to colours.onContainer
        EventPresentation.Bar -> colours.solid to colours.onSolid
        EventPresentation.Line -> Color.Transparent to Theme.colours.content
    }
    val edge = if (presentation == EventPresentation.Line) null else contrastEdge(colours.border)
    val description = eventDescription(event, scope.formats, Theme.strings)
    val click = scope.onEventClick
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .then(if (edge != null) Modifier.border(edge, shape) else Modifier)
            .then(
                // A block's tone at its start edge, so a column of pale blocks
                // in different tones still tells them apart at a glance.
                if (presentation == EventPresentation.Block) {
                    Modifier.drawBehind {
                        val width = stripe.toPx()
                        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                        drawRect(colours.solid, topLeft = Offset(x, 0f), size = Size(width, size.height))
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (click != null) {
                    Modifier.pressable(
                        onClick = { click(event) },
                        shape = shape,
                        enabled = scope.enabled,
                        minimumTouchTarget = false,
                    )
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        ProvideContentColour(ink) { scope.eventContent(event, presentation) }
    }
}

/**
 * An event as a screen reader says it: its title, its day, its time or that
 * it is all day, and where — a list, in the reader's own way of writing one.
 */
internal fun eventDescription(event: CalendarEvent, formats: LocaleFormats, strings: Strings): String {
    val lastDay = if (event.end > event.start) event.end.plusMinutes(-1).date else event.start.date
    val days = if (lastDay == event.start.date) {
        formats.date(event.start.date, DateStyle.Full)
    } else {
        strings.timeRange(formats.date(event.start.date, DateStyle.Full), formats.date(lastDay, DateStyle.Full))
    }
    val time = if (event.allDay) strings.allDay else strings.timeRange(formats.time(event.start.time), formats.time(event.end.time))
    return formats.list(listOfNotNull(event.title, days, time, event.location), ListStyle.Unit)
}

/** The ink for text on the calendar's own ground: muted for the days outside the month, and not at high contrast. */
@Composable
@ReadOnlyComposable
internal fun quietInk(): Color = if (highContrast()) Theme.colours.content else Theme.colours.contentMuted
