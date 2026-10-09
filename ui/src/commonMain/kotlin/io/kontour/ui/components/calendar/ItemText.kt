package io.kontour.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.pressable
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.toneColours

/**
 * The type of an event's line or bar: the small label's, a little smaller and
 * lighter. A day in a phone's month is about 50dp, and at the label's own size
 * and weight its title was three letters and an ellipsis.
 */
@Composable
internal fun calendarItemStyle(): TextStyle {
    val small = Theme.typography.labelSmall
    return small.copy(fontSize = small.fontSize * ItemTypeScale, fontWeight = FontWeight.Medium)
}

/** The item type's size against the small label's. */
private const val ItemTypeScale = 0.92f

/**
 * One line of [text] that is cut where it runs out of room, its last few
 * letters fading out rather than given up to an ellipsis: in a narrow day an
 * ellipsis is a letter's room, and a fade says the same in none. Cut hard at
 * high contrast, where a fade is ink that is neither there nor not.
 */
@Composable
internal fun FadingText(text: String, style: TextStyle, modifier: Modifier = Modifier, colour: Color = Color.Unspecified) {
    var cut by remember { mutableStateOf(false) }
    val fade = if (highContrast()) 0.dp else ItemFade
    Text(
        text,
        style = style,
        colour = colour,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { cut = it.hasVisualOverflow },
        modifier = modifier
            .graphicsLayer { compositingStrategy = if (cut && fade > 0.dp) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
            .drawWithContent {
                drawContent()
                if (!cut || fade <= 0.dp) return@drawWithContent
                val width = minOf(fade.toPx(), size.width / 3f)
                val rtl = layoutDirection == LayoutDirection.Rtl
                val from = if (rtl) width else size.width - width
                val to = if (rtl) 0f else size.width
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = from, endX = to),
                    topLeft = Offset(if (rtl) 0f else from, 0f),
                    size = Size(width, size.height),
                    blendMode = BlendMode.DstOut,
                )
            },
    )
}

/** How much of a cut title fades out. */
private val ItemFade = 12.dp

/**
 * Which of a line's parts it has room for, the title always: its dot, and the
 * time it starts.
 */
internal data class LineParts(val dot: Boolean, val time: Boolean)

/**
 * What a line [width] wide shows, its dot [dot] wide, its time [time], [gap]
 * between each: the time only with [minTitle] left for the title after it, and
 * the dot only with that left after the dot alone — in a phone's month the
 * time took the day and left the title two letters.
 */
internal fun lineParts(width: Int, dot: Int, time: Int, gap: Int, minTitle: Int): LineParts = when {
    width - dot - gap - time - gap >= minTitle -> LineParts(dot = true, time = true)
    width - dot - gap >= minTitle -> LineParts(dot = true, time = false)
    else -> LineParts(dot = false, time = false)
}

/** The least of a line's title worth giving up its time or dot for, against the type's size: about four letters. */
private const val MinTitleEms = 2.4f

/**
 * A timed event in a month's day, or a week's list: its tone's dot, its start
 * and its title, in that order, as many of the first two as leave the title
 * [lineParts]' room. Given more [lines], zoomed in: the dot and the title,
 * then its time on a line of its own, then where it is.
 */
@Composable
internal fun EventLine(event: CalendarEvent, lines: Int = 1) {
    if (lines > 1) return DetailedItem(event, lines, dot = true)
    val style = calendarItemStyle()
    val dotSize = Theme.spacing.xs
    val gap = Theme.spacing.xxs
    val pad = Theme.spacing.xxs
    Layout(
        content = {
            Box(Modifier.size(dotSize).background(toneColours(event.tone).solid, Theme.shapes.capsule))
            Text(Theme.formats.time(event.start.time), style = style, colour = Theme.colours.contentMuted, maxLines = 1, softWrap = false)
            FadingText(event.title, style)
        },
    ) { (dotM, timeM, titleM), constraints ->
        val side = pad.roundToPx()
        val gapPx = gap.roundToPx()
        val width = (constraints.maxWidth - side * 2).coerceAtLeast(0)
        val dotP = dotM.measure(Constraints())
        val timeP = timeM.measure(Constraints())
        val parts = lineParts(width, dotP.width, timeP.width, gapPx, (style.fontSize.toPx() * MinTitleEms).toInt())
        val used = (if (parts.dot) dotP.width + gapPx else 0) + (if (parts.time) timeP.width + gapPx else 0)
        val titleP = titleM.measure(Constraints(maxWidth = (width - used).coerceAtLeast(0)))
        val height = constraints.constrainHeight(maxOf(titleP.height, timeP.height, dotP.height))
        layout(constraints.constrainWidth(side * 2 + used + titleP.width), height) {
            var x = side
            if (parts.dot) {
                dotP.placeRelative(x, (height - dotP.height) / 2)
                x += dotP.width + gapPx
            }
            if (parts.time) {
                timeP.placeRelative(x, (height - timeP.height) / 2)
                x += timeP.width + gapPx
            }
            titleP.placeRelative(x, (height - titleP.height) / 2)
        }
    }
}

/**
 * An event in a zoomed-in month, on [lines] lines: its title — after its
 * tone's dot, for a [dot] line — then, for a timed one, the time it starts,
 * then where it is, as many as there are lines for and it has.
 */
@Composable
private fun DetailedItem(event: CalendarEvent, lines: Int, dot: Boolean) {
    val style = calendarItemStyle()
    val muted = Theme.colours.contentMuted
    val formats = Theme.formats
    val rest = buildList {
        if (dot) add(formats.time(event.start.time))
        event.location?.let(::add)
    }.take(lines - 1)
    val gap = Theme.spacing.xxs
    Column(Modifier.padding(horizontal = Theme.spacing.xxs)) {
        // The dot only where it leaves the title its room, as a line's is.
        Layout(
            content = {
                if (dot) Box(Modifier.size(Theme.spacing.xs).background(toneColours(event.tone).solid, Theme.shapes.capsule))
                FadingText(event.title, style)
            },
        ) { measurables, constraints ->
            val gapPx = gap.roundToPx()
            val titleM = measurables.last()
            val dotP = if (measurables.size > 1) measurables.first().measure(Constraints()) else null
            val shown = dotP?.takeIf { constraints.maxWidth - it.width - gapPx >= (style.fontSize.toPx() * MinTitleEms).toInt() }
            val used = if (shown != null) shown.width + gapPx else 0
            val titleP = titleM.measure(Constraints(maxWidth = (constraints.maxWidth - used).coerceAtLeast(0)))
            val height = maxOf(titleP.height, shown?.height ?: 0)
            layout(constraints.constrainWidth(used + titleP.width), height) {
                shown?.placeRelative(0, (height - shown.height) / 2)
                titleP.placeRelative(used, (height - titleP.height) / 2)
            }
        }
        for (line in rest) FadingText(line, style, colour = if (dot) muted else Color.Unspecified)
    }
}

/**
 * An all-day or multi-day event's bar: its title alone, inset by less in a
 * narrow day, so the few letters there are go to the title.
 */
@Composable
internal fun EventBar(event: CalendarEvent, lines: Int = 1) {
    if (lines > 1 && event.location != null) return DetailedItem(event, lines, dot = false)
    val wide = Theme.spacing.xs
    val narrow = Theme.spacing.xxs
    Layout(content = { FadingText(event.title, calendarItemStyle()) }) { (title), constraints ->
        val side = (if (constraints.maxWidth < NarrowItemWidth.roundToPx()) narrow else wide).roundToPx()
        val placeable = title.measure(Constraints(maxWidth = (constraints.maxWidth - side * 2).coerceAtLeast(0)))
        val height = constraints.constrainHeight(placeable.height)
        layout(constraints.constrainWidth(placeable.width + side * 2), height) {
            placeable.placeRelative(side, (height - placeable.height) / 2)
        }
    }
}

/** Below this a bar's title is inset by less. */
internal val NarrowItemWidth: Dp = 56.dp

/**
 * How many more events a day has than it shows, as one of its rows: a chip
 * the size and place of the events above it, rather than a count under them —
 * which, on a busy day in a phone's month, hung below the last event and read
 * as part of it. On [muted], a day outside the month, it is the surface's
 * colour, so it still shows against the day.
 */
@Composable
internal fun MoreChip(
    count: Int,
    muted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = Theme.formats
    val strings = Theme.strings
    val described = strings.moreEvents(formats.quantity(count))
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .background(if (muted) Theme.colours.surface else Theme.colours.surfaceSunken, Theme.shapes.control)
            .pressable(onClick = onClick, shape = Theme.shapes.control, enabled = enabled, minimumTouchTarget = false)
            .semantics { contentDescription = described },
    ) {
        FadingText(
            strings.moreCount(formats.quantity(count)),
            calendarItemStyle(),
            colour = quietInk(),
            modifier = Modifier.padding(horizontal = Theme.spacing.xxs),
        )
    }
}
