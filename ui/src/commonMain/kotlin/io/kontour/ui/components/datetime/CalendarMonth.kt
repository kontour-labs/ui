package io.kontour.ui.components.datetime

import io.kontour.ui.theme.clipProxy
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.locale.DateTimeConventions
import io.kontour.ui.locale.DateStyle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.interaction.FeedbackIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.style.TextOverflow
import io.kontour.ui.foundation.SurfaceDefaults
import io.kontour.ui.foundation.Icon
import io.kontour.ui.locale.ListStyle
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.insetFocusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.interaction.rememberHoldFeedback
import io.kontour.ui.interaction.rememberTapFeedback
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.invisible
import io.kontour.ui.theme.lines
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import io.kontour.ui.theme.animateComponentColour

/** How a day sits within a selected range. Drives the cell's shape and fill. */
enum class RangePosition { None, Start, Middle, End, StartAndEnd }

/**
 * What a calendar says about a day besides its number: dots, a few characters
 * and an icon under it, and a colour of its own.
 *
 * ```kotlin
 * DatePicker(
 *     value = date,
 *     onValueChange = { date = it },
 *     decorationFor = { day ->
 *         fares[day]?.let { fare ->
 *             DayDecoration(
 *                 text = Theme.formats.currency(fare, "AUD"),
 *                 textColour = if (fare < 40) Theme.colours.status.success.solid else Color.Unspecified,
 *                 contentDescription = "from ${fare.toInt()} dollars",
 *             )
 *         }
 *     },
 * )
 * ```
 *
 * The text and the icon share a line under the number, a day's width: four or
 * five characters, or an icon and two or three, and anything longer is cut at
 * the cell's edge. The dots go under that, up to three of them. A calendar given
 * decorations makes every week a line taller for the text, so it does not change
 * height as it pages; one that only draws dots can say so with
 * `showDecorationLine = false`, and keep its weeks as they were.
 *
 * **On a chosen day the decoration takes the day's own ink**, as its number
 * does, so it reads on the fill; in the band of a range it keeps its colours.
 * [keepColours] keeps them everywhere — on the chosen day's fill too, where a
 * dot is ringed and a line of text sits on a patch of the page's own ground so
 * it still reads, and on a day that cannot be chosen.
 *
 * @param dots A dot under the day for each, in its colour: "has departures",
 *   one per calendar the day has events in. Three at most are drawn.
 * @param text A few characters under the number: a fare, a count, *Full*.
 * @param textColour The text's ink. Unspecified, the day's muted ink.
 * @param icon A small glyph on the same line, before [text].
 * @param iconColour The icon's ink. Unspecified, the text's.
 * @param containerColour The day's own colour: its circle filled with it, and
 *   its number in the ink that reads on it. For a public holiday, a day sold
 *   out, how busy each day is. A chosen day and a range are drawn over it,
 *   unless [keepColours].
 * @param keepColours Keep every colour here whatever the day's state: chosen,
 *   in a range or unavailable. Off, a chosen day draws the decoration in its own
 *   ink, and a day that cannot be chosen greys it.
 * @param contentDescription What a screen reader says for the decoration, after
 *   the date: [text] when null, as `"$42"` might be "from 42 dollars".
 */
@Immutable
class DayDecoration(
    val dots: List<Color> = emptyList(),
    val text: String? = null,
    val textColour: Color = Color.Unspecified,
    val icon: ImageVector? = null,
    val iconColour: Color = Color.Unspecified,
    val containerColour: Color = Color.Unspecified,
    val keepColours: Boolean = false,
    val contentDescription: String? = null,
) {
    /** Whether there is anything for the line under the number. */
    internal val lined: Boolean get() = text != null || icon != null

    override fun equals(other: Any?): Boolean =
        this === other || other is DayDecoration && dots == other.dots && text == other.text &&
            textColour == other.textColour && icon == other.icon && iconColour == other.iconColour &&
            containerColour == other.containerColour && keepColours == other.keepColours &&
            contentDescription == other.contentDescription

    override fun hashCode(): Int {
        var h = dots.hashCode()
        h = h * 31 + text.hashCode()
        h = h * 31 + textColour.hashCode()
        h = h * 31 + icon.hashCode()
        h = h * 31 + iconColour.hashCode()
        h = h * 31 + containerColour.hashCode()
        h = h * 31 + keepColours.hashCode()
        h = h * 31 + contentDescription.hashCode()
        return h
    }

    override fun toString(): String =
        "DayDecoration(dots=$dots, text=$text, textColour=$textColour, icon=$icon, iconColour=$iconColour, " +
            "containerColour=$containerColour, keepColours=$keepColours, contentDescription=$contentDescription)"
}

/** What [CalendarMonth] uses when it is not told otherwise. */
object CalendarMonthDefaults {
    /** Breathing room around a day cell, outside a range only. */
    val CellInset: Dp = 1.dp

    /**
     * The widest a day cell gets: the size the type scale is right for.
     *
     * A month grid at a phone's width puts a cell at about this, which is where
     * `bodyMedium` was chosen, and it is where the grid stops. Cells used to go
     * on growing with the width, the digits with them to two thirds again, so a
     * picker on a desktop was half a window of large numbers in larger circles:
     * *"i've always found the date picker to be really big and spaced out,
     * compared to a lot of our other components"*.
     */
    val ReferenceCell: Dp = 44.dp

    /**
     * How wide a month grid gets: seven cells at [ReferenceCell].
     *
     * It needs saying because uncapped, `fillMaxWidth` means "as wide as the
     * container", and a desktop window is a container. A calendar is not a thing
     * that wants a whole window, and a picker handed one filled it.
     */
    val MaxWidth: Dp = ReferenceCell * 7
}

/**
 * One month's grid of days.
 *
 * The reusable part of every date picker — a calendar surface with no opinion
 * about how selection works, so single-date, range and multi-select pickers all
 * build on the same grid rather than each drawing their own.
 *
 * ```
 * CalendarMonth(
 *     month = visibleMonth,
 *     isSelected = { it == chosen },
 *     onSelectedChange = { chosen = it },
 *     isDateSelectable = { it >= today },
 * )
 * ```
 *
 * Selection is expressed as predicates rather than as a value, because that is
 * the only shape that serves all three selection modes without the grid needing
 * to know which one it is in.
 *
 * @param month Any date within the month to show; only its year and month matter.
 * @param enabled Whether any day can be chosen. Disabled, every day is shown
 *   the way an unselectable one is, and a drag selects nothing.
 * @param isDateSelectable Days for which this returns false are shown but not
 *   selectable — greyed rather than hidden, so the calendar keeps its shape and
 *   the user can see *why* a date is unavailable.
 * @param decorationFor What to draw on a day besides its number — dots, a few
 *   characters, an icon, a colour of its own: see [DayDecoration]. Null for a
 *   day drawn as usual.
 * @param showDecorationLine Whether the weeks have a line under the numbers for
 *   decorations' text and icons: every week a line taller, in every month, so
 *   the calendar does not change height as it pages. On whenever there are
 *   decorations; off for a calendar that only draws dots, whose weeks then stay
 *   as they were and whose text and icons are not drawn.
 * @param rangePositionOf Where a day sits in a selected range. Days in the
 *   middle get a square fill so the run reads as continuous; the ends get the
 *   rounded cap.
 */
@Composable
fun CalendarMonth(
    month: LocalDate,
    isSelected: (LocalDate) -> Boolean,
    onSelectedChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDateSelectable: (LocalDate) -> Boolean = { true },
    /**
     * A range being dragged out, reported as (where the finger went down, where
     * it is now).
     *
     * The calendar owns the anchor rather than the caller, because it is gesture
     * state and not selection state — a caller that had to hold it would be
     * holding something it can neither set nor meaningfully restore. Which end
     * is the start and which the end is the caller's business; a drag leftwards
     * reports a `to` earlier than its `from`.
     *
     * `null` leaves the calendar tap-only.
     */
    onDragSelect: ((from: LocalDate, to: LocalDate) -> Unit)? = null,
    today: LocalDate? = null,
    decorationFor: ((LocalDate) -> DayDecoration?)? = null,
    showDecorationLine: Boolean = decorationFor != null,
    rangePositionOf: ((LocalDate) -> RangePosition)? = null,
    formats: LocaleFormats = Theme.formats,
    /**
     * Where the week starts.
     *
     * Monday by default, which is Australia, most of Europe and the ISO week.
     * North America starts on Sunday and a few calendars start on Saturday.
     *
     * A shortcut, not a second source of truth: it defaults from
     * [DateTimeConventions.firstDayOfWeek], which is where the answer has always
     * lived, and setting it derives a `formats` for the week arithmetic rather
     * than being consulted separately. So an app-wide choice is one field on the
     * token group and a one-off is one argument here — the same arrangement
     * `Theme.strings` and every component's own string parameter already use.
     */
    firstDayOfWeek: DayOfWeek = formats.conventions.firstDayOfWeek,
) {
    // One object downstream, so nothing can read the week's start from two
    // places and disagree.
    val weekFormats = remember(formats, firstDayOfWeek) { formats.startingOn(firstDayOfWeek) }

    val firstOfMonth = remember(month) { LocalDate(month.year, month.month, 1) }
    val daysInMonth = remember(firstOfMonth) {
        firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    }
    val leadingBlanks = remember(firstOfMonth, weekFormats) {
        weekFormats.conventions.columnOf(firstOfMonth.dayOfWeek)
    }

    // The drag this month draws from. A date picker holds one over its pager, so
    // that a drag outlives the month it started in — see `CalendarDragState`. A
    // month on its own, handed `onDragSelect`, holds its own.
    // A disabled month is one whose every day is unselectable, which is already
    // drawn and already refused — so `enabled` goes through that and nothing new.
    val selectable: (LocalDate) -> Boolean = if (enabled) isDateSelectable else NoDateSelectable
    val host = LocalCalendarDrag.current
    val own = if (host == null && onDragSelect != null) rememberCalendarDrag() else null
    val drag = host ?: own
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    if (own != null) {
        SideEffect {
            own.onSelect = onDragSelect
            own.isSelectable = selectable
            own.geometry = {
                GridGeometry.of(firstOfMonth, weekFormats, Offset.Zero, own.width / Columns, own.rowHeight, rtl)
            }
        }
        // A caller paging a month on its own while a finger is down: the drag
        // carries on in the new month from where the finger is.
        LaunchedEffect(own, firstOfMonth) { own.track() }
    }

    /**
     * Whether a range is being dragged out in this month right now, from the anchor
     * landing until the release has settled.
     *
     * A tapped day lands like a stone and a dragged one should not: the cap
     * bounced into place on every cell the finger crossed, so a drag across a
     * fortnight was fourteen separate arrivals. See `DayCell`, where this
     * chooses between the two. And while it holds, the band is the grid's to
     * draw, not the days' — see `drawLiveBand`.
     */
    val live = drag != null && drag.live && drag.month == firstOfMonth
    val todayFlash = LocalTodayFlash.current
    val paging = live && drag.onStep != null
    val motion = Theme.motion

    /**
     * The rings round the month's first and last days, while a finger is down.
     *
     * The other half of dragging across a border, beside the header's arrows
     * pressed with a second finger: *"as you drag it towards the start or end of a
     * month, a small arrow next to the first/last day pointing to the
     * previous/next month appears. When you drag over that arrow for enough
     * time … it switches to the corresponding month, and the drag continues."*
     * Then, having used it: *"make it part of the first/last day's box … it
     * should fade in as you get closer … when the user is close vertically as
     * well"* — so it is a ring round the day, and how near the finger is sets how
     * much of each shows, in the draw pass. This only fades the pair out when the
     * finger lifts.
     */
    val rings by animateFloatAsState(
        if (paging && drag.pressed) 1f else 0f,
        motion.tweenFast(),
        label = "edgeRings",
    )

    // One measurement for the whole month, not forty-two.
    //
    // The day numbers scale with their cells — see `DayCell` — and a cell is a
    // seventh of the grid by construction, so the grid's own width answers it
    // for every cell at once. A `BoxWithConstraints` per cell would be
    // forty-two subcompositions to derive one number that is the same number
    // every time.
    //
    // `widthIn` outside `fillMaxWidth`, and the order is the whole thing:
    // `fillMaxWidth` fixes the constraints at [W, W] first, and a cap then
    // coerces [0, max] against a minimum of W — so on any container wider than
    // the cap, written the other way round, the cap is silently discarded.
    BoxWithConstraints(modifier.widthIn(max = CalendarMonthDefaults.MaxWidth).fillMaxWidth()) {
        val cellSize = maxWidth / 7
        // A week no taller than a cell is wide, and no taller than
        // `MaxRowHeight` however wide it is — and a line of the decorations'
        // type taller than that where they have a line, at the reader's type
        // size, so the number does not move up to make room for one.
        val decorationLine = with(LocalDensity.current) { Theme.typography.labelSmall.lineHeight.toDp() }
        val lined = showDecorationLine
        val rowHeight = minOf(cellSize, MaxRowHeight) + if (lined) decorationLine else 0.dp
        val colours = Theme.colours
        // The dwell arrow's ring: a line and a half, thickening with the tier.
        val arrowStroke = Theme.sizing.lines(1.5f)
    // On the range's cap the ring is faded at the standard tier and solid at the enhanced one.
    val capRingAlpha = if (highContrast()) 1f else CapRingAlpha
        val insetPx = with(LocalDensity.current) { CalendarMonthDefaults.CellInset.toPx() }
        Column(Modifier.fillMaxWidth()) {
        WeekdayInitials(weekFormats)

        val cells = leadingBlanks + daysInMonth
        // At least as many weeks as the picker round it asks for — six, so every
        // month is the same height. See `LocalCalendarRows`.
        val rows = maxOf((cells + 6) / 7, LocalCalendarRows.current)

        // The weeks. The hit test is derived from the grid rather than from each
        // cell's reported bounds: the columns are seven equal weights and every
        // row is one cell tall, so it is the same arithmetic the layout does, and
        // forty-two `onGloballyPositioned` callbacks would buy nothing over it.
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (own != null) Modifier.calendarDragInput(own) else Modifier)
                // Where the weeks start under the weekday initials, for a drag
                // held above the pager. Relative to this month's own box, which
                // the pager's slide does not move.
                // And how tall its rows really are: see `GridGeometry.rowHeight`.
                .then(
                    if (drag != null) {
                        Modifier.onGloballyPositioned {
                            if (host != null) host.gridTop = it.positionInParent().y
                            drag.rowHeight = it.size.height.toFloat() / rows
                        }
                    } else {
                        Modifier
                    }
                )
                .drawBehind {
                    val state = drag ?: return@drawBehind
                    val anchor = state.anchor ?: return@drawBehind
                    if (!live) return@drawBehind
                    val anchorAt = if (anchor.sameMonth(firstOfMonth)) leadingBlanks + anchor.day - 1 else null
                    val moving = state.head
                    val forward = when {
                        anchorAt == null -> anchor < firstOfMonth
                        moving != null && moving != anchor -> moving > anchor
                        else -> state.bandRow * Columns + state.bandX >= anchorAt + 0.5f
                    }
                    drawLiveBand(
                        leadingBlanks = leadingBlanks,
                        daysInMonth = daysInMonth,
                        rows = rows,
                        anchorAt = anchorAt,
                        forward = forward,
                        boundary = state::boundary,
                        colour = colours.accent.container,
                        inset = insetPx,
                        rtl = rtl,
                    )
                }
                .drawWithContent {
                    drawContent()
                    val state = drag ?: return@drawWithContent
                    if (rings <= 0f) return@drawWithContent
                    val cell = size.width / Columns
                    val rowHeight = size.height / rows
                    val halfCell = minOf(cell, rowHeight) / 2f
                    val first = leadingBlanks
                    val last = leadingBlanks + daysInMonth - 1
                    for (edgeDay in DwellEdge.entries) {
                        val index = if (edgeDay == DwellEdge.Previous) first else last
                        val column = index % Columns + 0.5f
                        val row = index / Columns + 0.5f
                        // In full while its dwell runs, however far past the finger is.
                        val near = if (state.edge == edgeDay) 1f else ringPresence(distance(state.finger, Offset(column, row)))
                        val presence = near * rings
                        if (presence <= 0f) continue
                        val date = LocalDate(month.year, month.month, index - leadingBlanks + 1)
                        // On its own day's fill: the cap's ink when the day is an
                        // end of the range, the page's otherwise.
                        val onCap = date == state.head || date == state.anchor
                        drawEdgeArrow(
                            day = Offset((if (rtl) Columns - column else column) * cell, row * rowHeight),
                            halfCell = halfCell,
                            pointsLeft = (edgeDay == DwellEdge.Previous) != rtl,
                            progress = if (state.edge == edgeDay) state.dwell.value else 0f,
                            alpha = presence,
                            ring = if (onCap) colours.onPrimary.copy(alpha = capRingAlpha) else colours.outlineStrong,
                            fill = if (onCap) colours.onPrimary else colours.primary,
                            chevron = if (onCap) colours.onPrimary else colours.content,
                            stroke = arrowStroke.toPx(),
                        )
                    }
                },
        ) {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth().height(rowHeight)) {
                for (column in 0 until 7) {
                    val cellIndex = row * 7 + column
                    val dayOfMonth = cellIndex - leadingBlanks + 1

                    if (dayOfMonth < 1 || dayOfMonth > daysInMonth) {
                        Box(Modifier.weight(1f))
                    } else {
                        val date = LocalDate(month.year, month.month, dayOfMonth)
                        DayCell(
                            date = date,
                            modifier = Modifier.weight(1f),
                            selected = isSelected(date),
                            enabled = selectable(date),
                            isToday = date == today,
                            decoration = decorationFor?.invoke(date),
                            lined = lined,
                            rangePosition = rangePositionOf?.invoke(date) ?: RangePosition.None,
                            formats = formats,
                            cellSize = cellSize,
                            rowHeight = rowHeight,
                            dragging = live,
                            travelling = live && drag.leaning == date,
                            flash = if (date == today) todayFlash else null,
                            // Where the cap is, in cells, measured from this cell's
                            // own place in the grid. Read in the layer: it changes on
                            // every pointer move and moves exactly one cell in
                            // forty-two.
                            //
                            // And gated on the date under the finger. The anchor is a
                            // cap too, and without the gate it read the same travel
                            // and stepped away with every day the moving end crossed.
                            capTravel = {
                                if (drag != null && drag.leaning == date) drag.cap.value else Offset.Zero
                            },
                            onSelectedChange = onSelectedChange,
                        )
                    }
                }
            }
        }
        }
        }
    }
}

/** The weekday initials over a month's columns. */
@Composable
private fun WeekdayInitials(weekFormats: LocaleFormats) {
    // Normal weight, which `labelSmall` is not. It was reported that the
    // initials need to be smaller than the dates, and by *size* they already
    // were — 12sp against `bodyMedium`'s 15. What they were not is lighter:
    // `labelSmall` is SemiBold, and semibold at 12 carries about as much ink
    // as normal at 15, so a row that is numerically subordinate read as
    // competing with the numbers under it. The weight is the difference the
    // eye was actually seeing.
    //
    // Overridden here rather than in the scale, because `labelSmall` is
    // shared and its weight is right everywhere it is a *label*. This is a
    // column heading.
    val weekdayStyle = Theme.typography.labelSmall.copy(fontWeight = FontWeight.Normal)
    Row(Modifier.fillMaxWidth()) {
        weekFormats.weekdayInitials().forEachIndexed { index, initial ->
            Box(
                Modifier
                    .weight(1f)
                    // The initials repeat — S for Saturday and Sunday, T for
                    // Tuesday and Thursday — so they are decorative here and
                    // each day cell carries its own full announcement.
                    .semantics { },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = weekdayStyle,
                    colour = Theme.colours.contentMuted,
                    modifier = Modifier.padding(vertical = Theme.spacing.xxs),
                )
            }
        }
    }
}

/**
 * The drag a month or a picker holds: the state, and the three things it reports
 * to the hand — a day crossed, a faint rumble while a month is being held for,
 * and the month paged.
 *
 * A day is a detent in the strictest sense — the selection snaps to one and rests
 * there — so it goes through the same guard and shared rate floor as `Slider`,
 * `WheelPicker` and the rest. A month paged mid-drag is a threshold passed: what
 * the drag is choosing from has just changed under the finger.
 */
@Composable
internal fun rememberCalendarDrag(): CalendarDragState {
    val scope = rememberCoroutineScope()
    val days = rememberDetentTicker()
    val pages = rememberDetentTicker(FeedbackIntent.DragThreshold)
    val hold = rememberHoldFeedback()
    val drag = remember(scope, days, pages, hold) { CalendarDragState(scope, days, pages, hold) }
    val motion = Theme.motion
    SideEffect { drag.motion = motion }
    return drag
}

private fun LocalDate.sameMonth(first: LocalDate): Boolean = year == first.year && month == first.month

/**
 * The fewest weeks a month draws, blank ones at the end making up the rest.
 *
 * A date picker sets six, which every month fits. Months are four, five or six
 * weeks, so a picker that drew each as tall as it was changed height by a week
 * when it paged — and in a scrolling page, a picker changing height under the
 * header is the page moving. Reported from a phone as a date picker that
 * "randomly scrolls up sometimes when changing month". A month on its own, a
 * `CalendarMonth` a caller lays out, is as tall as its weeks.
 */
internal val LocalCalendarRows = staticCompositionLocalOf { 0 }

/** How strong today's flash is at its brightest: enough to find the day, not to read as a selection. */
internal const val TodayFlashAlpha: Float = 0.45f

/** The arrow's ring on a cap, faint against the cap's ink until the dwell fills it. */
private const val CapRingAlpha: Float = 0.4f

@Composable
private fun DayCell(
    date: LocalDate,
    modifier: Modifier,
    selected: Boolean,
    enabled: Boolean,
    isToday: Boolean,
    decoration: DayDecoration?,
    /** The weeks have room for a decoration's line, whether this day has one or not. */
    lined: Boolean,
    rangePosition: RangePosition,
    formats: LocaleFormats,
    cellSize: Dp,
    rowHeight: Dp,
    /** A drag is live in this month: the band is the grid's, not the days'. */
    dragging: Boolean,
    /** This day is the moving end of that drag. */
    travelling: Boolean,
    /** How far this day is flashed, 0 to 1: today's, when the today button is pressed. */
    flash: (() -> Float)?,
    /**
     * How far past this cell's centre the finger is, in cell widths, or zero for
     * every cell that is not the moving end of the range. Read in the layer.
     */
    capTravel: () -> Offset,
    onSelectedChange: (LocalDate) -> Unit,
) {
    val tap = rememberTapFeedback()
    val colours = Theme.colours
    val strings = Theme.strings
    // The day's own colour, under a selection unless it is kept.
    val containerColour = decoration?.containerColour?.takeIf { it.isSpecified }
    val keep = decoration?.keepColours == true
    val motion = Theme.motion
    val interactions = remember { MutableInteractionSource() }

    val inRange = rangePosition != RangePosition.None
    val isEndpoint = rangePosition == RangePosition.Start ||
        rangePosition == RangePosition.End ||
        rangePosition == RangePosition.StartAndEnd
    val filled = selected || isEndpoint

    // Whether the band carries on into the neighbouring cell. Layout-direction
    // aware by construction: `Start`/`End` are the range's ends, and `start`/`end`
    // padding resolves the same way the shape's `topStart`/`topEnd` do.
    val continuesBefore = rangePosition == RangePosition.Middle ||
        rangePosition == RangePosition.End
    val continuesAfter = rangePosition == RangePosition.Middle ||
        rangePosition == RangePosition.Start

    // Today's circle: its ring when it is not filled, and where its flash is drawn.
    val todayRing: Shape = Theme.shapes.pill

    // A run of selected days reads as continuous because the middle keeps square
    // edges and only the ends are capped.
    //
    // **The end being dragged is a whole pill.** The band under it now reaches
    // into it from wherever the finger is, so its square side had nothing to be
    // square against: leaning down toward the next week, the corner on the band's
    // side hung below the row like a flag. Round on both sides it sits on the band
    // however it leans, and where it sits level the band fills its corners.
    val shape: Shape = when (if (travelling) RangePosition.StartAndEnd else rangePosition) {
        RangePosition.Middle -> RectangleShape
        RangePosition.Start -> Theme.shapes.pill.copy(
            topEnd = androidx.compose.foundation.shape.CornerSize(0),
            bottomEnd = androidx.compose.foundation.shape.CornerSize(0),
        )
        RangePosition.End -> Theme.shapes.pill.copy(
            topStart = androidx.compose.foundation.shape.CornerSize(0),
            bottomStart = androidx.compose.foundation.shape.CornerSize(0),
        )
        else -> Theme.shapes.pill
    }

    /**
     * A day arrives, but it does not leave.
     *
     * Tapping a date should feel like laying down a stone, so a cell joining the
     * selection eases in. Letting it ease *out* again is a different thing
     * entirely, and it only shows when a whole range is replaced: every day of
     * the old one animated away at once, so choosing new dates dragged a trail of
     * grey circles behind it for a fifth of a second — a near-black cap on its
     * way to nothing is grey for most of the tween, and there was one on every
     * day the range used to cover. Nothing about a range you have just replaced
     * is worth watching leave.
     *
     * So the drawn colour is the animated one on the way in and the *target* on
     * the way out. Not `snap()` as the spec, which is a zero-length animation and
     * still costs the frame it takes to start — one frame of the old range at
     * full strength, which is exactly the flash.
     */
    val arriving = filled || inRange

    val containerTarget = when {
        filled -> colours.primary
        inRange -> colours.accent.container
        // The day's own colour, under the selection: the selection is what
        // the reader did, and is drawn over what the app says about the day.
        containerColour != null -> containerColour
        // `invisible()`, not `Color.Transparent`, which is black. This is where
        // a cell animates *from* when it joins a range, and a lerp moves the
        // channels as well as the alpha — so the tint used to arrive out of the
        // dark rather than fading up.
        else -> colours.accent.container.invisible()
    }
    val animatedContainer by animateComponentColour(
        target = containerTarget,
        spec = motion.tweenFast(),
        label = "dayContainer",
    )
    // **Instant while a finger is out**, which is the other half of the report:
    // *"the area between the two points gets filled in as the user drags"*. The
    // fade is right for a range that arrives on a tap and wrong for one being drawn
    // — a hundred milliseconds behind the finger, per cell, reads as the band
    // catching up rather than as the finger filling it.
    //
    // The same shape as the exit above and for the same reason, which is why the
    // two are one expression now: a colour that animates *toward* the finger is the
    // only one of the four that ever wanted to.
    //
    // **And nothing at all in the middle of a live drag**, where the grid draws the
    // band — see `drawLiveBand` — so that it can flow out of days the finger is
    // leaving as well as into the ones it is heading for. Only what is *drawn*
    // changes: the target, and the animation toward it, run as before, so a day
    // handed back at the end of the drag is already the colour it should be.
    val container = when {
        dragging && rangePosition == RangePosition.Middle -> colours.accent.container.invisible()
        arriving && !dragging -> animatedContainer
        else -> containerTarget
    }

    val ownFill = !filled && !inRange && containerColour != null
    // Kept in the band of a range: a circle of the day's own colour on it.
    val keptOnBand = keep && containerColour != null && inRange && !filled
    val labelTarget = when {
        !enabled -> colours.contentDisabled
        filled -> colours.onPrimary
        // The ink that reads on the day's own colour, as a surface's would.
        keptOnBand -> SurfaceDefaults.contentColour(containerColour)
        inRange -> colours.accent.onContainer
        ownFill -> SurfaceDefaults.contentColour(containerColour)
        else -> colours.content
    }
    val animatedLabel by animateComponentColour(
        target = labelTarget,
        spec = motion.tweenFast(),
        label = "dayLabel",
    )
    // Taken away with the fill it is drawn on. Easing the digit back to
    // `content` over a container that has already gone leaves white text on a
    // white ground for the length of the tween.
    val label = if (arriving) animatedLabel else labelTarget
    /**
     * A cap arriving under a finger **travels**; a cap arriving under a tap lands.
     *
     * Tapping a day should feel like laying down a stone, and it does. Dragging a
     * range out is a different gesture and has been through two wrong answers.
     * First it borrowed the tap's animation, so the cap sprang into place on every
     * cell the finger crossed and a drag across a fortnight was fourteen separate
     * bounces chasing the pointer. Then it grew along the track out of the edge the
     * range came from — a `scaleX` from nothing, which is a wipe, and was reported
     * as one: the head should *move* between the two dates instead.
     *
     * So it moves. The cap is full size and drawn at an animated position, which is
     * the whole grid's rather than this cell's — see `cap` in `CalendarMonth` —
     * and springs toward a point pulled a fraction of the way to the finger. That
     * is a range slider's thumb, exactly, and because the position is on both axes
     * a cap crossing into the next week travels up and across to the day it came
     * from. One number, both axes.
     *
     * ### Only a *cap* travels
     *
     * This used to be every cell in the range, and a cell in the middle of a band
     * is not an edge that is moving — it is band. So each cell the finger passed
     * over stopped being an endpoint, became `Middle`, and scaled its container
     * along the track to **nothing**: the range emptied out behind the drag and came
     * back only when the finger lifted. Reported as "they revert to the background
     * colour until the finger lifts", and as the reason a drag animated each date
     * separately rather than extending one strip.
     *
     * ### And only the cap being dragged
     *
     * Both ends of a range being dragged are caps, and both read the one travel. So
     * the anchor, the end the finger put down and left, jumped a cell the way the
     * moving end had come from on every day crossed, and sprang home beside it.
     * Reported as the drag animating both heads. `capTravel` is gated on the date
     * under the finger, so the anchor is `sliding` with nothing to slide by: drawn
     * in place, at full size.
     */
    val sliding = dragging && isEndpoint && rangePosition != RangePosition.StartAndEnd

    val fillScale by animateFloatAsState(
        targetValue = when {
            filled -> 1f
            // A cell not yet in the range sits at zero while a drag is out, so
            // that becoming the cap is a sweep along the track rather than a
            // step from most of the way there. Unused while it is not a cap:
            // nothing below applies a scale to a cell that is neither.
            dragging -> 0f
            else -> 0.7f
        },
        animationSpec = if (sliding) {
            motion.springOrTween(motion.springSnappy)
        } else {
            motion.springOrTween(motion.springBouncy)
        },
        label = "dayFill",
    )

    // On the type scale. The cells stop at the size it was chosen for, so the
    // digit no longer grows with them.
    val dayStyle = if (isToday) Theme.typography.labelMedium else Theme.typography.bodyMedium

    // A whole pill is a circle, as tall as the week: a lone day, today's ring, and
    // the end being dragged. The cell is a little wider than its week is tall, and
    // a pill as wide as the cell was a lozenge. A range's caps and middle fill the
    // cell's width still, since the band is joined across it.
    //
    // With room for a decoration's line the week is taller than a cell is wide,
    // and a circle as tall as it would be wider than the day: there the whole of
    // the cell, a capsule standing up round the number and the line under it.
    val round = travelling || rangePosition == RangePosition.None || rangePosition == RangePosition.StartAndEnd
    // The shape a lone day is: a circle, or that standing capsule.
    val circleSize = if (!lined) Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true) else Modifier.matchCellSize()
    val fillSize = if (round) circleSize else Modifier.matchCellSize()

    Box(
        modifier = modifier
            .fillMaxHeight()
            // Inset on the outside of the range only.
            //
            // A flat 1dp all round put 2dp of gap between every pair of
            // neighbours, so the band a range draws came out as a row of
            // separate tiles — the shape logic above caps only the ends
            // precisely so the middle reads continuous, and the padding was
            // undoing it a pixel at a time.
            .padding(
                start = if (continuesBefore) 0.dp else CalendarMonthDefaults.CellInset,
                end = if (continuesAfter) 0.dp else CalendarMonthDefaults.CellInset,
                top = CalendarMonthDefaults.CellInset,
                bottom = CalendarMonthDefaults.CellInset,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .then(fillSize)
                .graphicsLayer {
                    if (sliding) {
                        // **The cap is where the grid says it is.**
                        //
                        // `capTravel` is the animated cap position measured from this
                        // cell, in cells, on both axes, with the pull toward the
                        // finger already in it — so a crossing within a week is a
                        // slide along the row and one into the next week is a move
                        // up and across to the day the range came from. The pitches
                        // are the cell's own width and its week's height rather than
                        // `size`, which is inset on the outside of the range and would
                        // leave the cap a pixel or two short of each day it passed.
                        val travel = capTravel()
                        translationX = travel.x * cellSize.toPx()
                        translationY = travel.y * rowHeight.toPx()
                    } else if (filled) {
                        scaleX = fillScale
                        scaleY = fillScale
                    }
                }
                .clip(shape.clipProxy())
                .background(container, shape)
        )

        if (keep && containerColour != null && (filled || inRange)) {
            // The day's own colour, kept: a circle of it on the band, a ring of
            // it inside the chosen day's fill.
            Box(
                Modifier
                    .then(circleSize)
                    .then(
                        if (filled) {
                            Modifier.padding(KeptRingInset).border(Theme.sizing.borderWidthStrong, containerColour, todayRing)
                        } else {
                            Modifier.padding(KeptCircleInset).background(containerColour, todayRing)
                        }
                    )
            )
        }

        if (isToday && !filled) {
            // **A circle of its own**, not the fill's outline. The fill is a
            // square in the middle of a range, so today's ring drawn round it
            // there was a pill the width of the cell.
            Box(
                Modifier
                    .then(circleSize)
                    .border(Theme.sizing.borderWidth, colours.outlineStrong, todayRing)
            )
        }

        if (flash != null) {
            // Over the day's fill and under its number: a colour from the theme
            // fading in and out, drawn from the flash in the draw pass alone.
            //
            // **Inside today's circle**, not the cell. The cell's own shape is a
            // square in the middle of a range, and the flash filled the whole of
            // it there, spilling into the band either side of the day.
            val flashColour = colours.accent.solid
            Box(
                Modifier
                    .then(circleSize)
                    .drawBehind {
                        val amount = flash()
                        if (amount > 0f) {
                            drawOutline(
                                todayRing.createOutline(size, layoutDirection, this),
                                flashColour,
                                alpha = amount * TodayFlashAlpha,
                            )
                        }
                    }
            )
        }

        Box(
            Modifier
                .minimumTouchTarget()
                .matchCellSize()
                .clip(shape.clipProxy())
                // Inside the day: the days either side touch it. With no
                // indication, this is the only sign of where the keyboard is.
                .insetFocusRing(interactions, shape, enabled)
                // On the node that is pressed, not on the fill behind it.
                //
                // The date and the selection used to be announced from the
                // decorative box that draws the highlight — a sibling of this
                // one, which a screen reader reaches separately if at all. So
                // the thing a user actually lands on said "18, button" and
                // nothing about it being the date they had chosen.
                //
                // `selectable` rather than `clickable`: a day in a calendar is
                // one of a set of choices, and this is the modifier that says so
                // and carries the state with the action.
                .semantics {
                    val day = formats.date(date, DateStyle.Full)
                    // Where the day is in a range, after the date: only the
                    // ends are selected, so a day inside the band said nothing
                    // a screen reader could hear of being in it, and an end
                    // was not told from the other. A lone start — the range
                    // half chosen, or a range of one day — is its start.
                    val place = when (rangePosition) {
                        RangePosition.Start, RangePosition.StartAndEnd -> strings.rangeStart
                        RangePosition.Middle -> strings.inRange
                        RangePosition.End -> strings.rangeEnd
                        RangePosition.None -> null
                    }
                    // The decoration after that, as it is read on screen.
                    val said = decoration?.let { it.contentDescription ?: it.text }
                    val parts = listOfNotNull(day, place, said)
                    stateDescription = if (parts.size == 1) day else formats.list(parts, ListStyle.Unit)
                }
                .pointerCursor(enabled = enabled)
                .selectable(
                    selected = filled,
                    interactionSource = interactions,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        // A day already chosen, pressed again, is a radio pressed
                        // on its own choice: nothing happened to report.
                        if (!selected) tap()
                        onSelectedChange(date)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // The number where it would be with nothing under it — centred, or
            // in a month with a decoration line at the one place every day's
            // number sits — and the line and the dots under it in room of their
            // own. They used to share a centred column with it, so a day with a
            // dot had its number nudged up, and a row of them read as a ragged
            // line.
            val decorationLine = with(LocalDensity.current) { Theme.typography.labelSmall.lineHeight.roundToPx() }
            val markerGap = with(LocalDensity.current) { MarkerGap.roundToPx() }
            // On the chosen day's fill, a kept colour stands on the page's own
            // ground: a ring round a dot, a patch under the line.
            val standOff = keep && filled
            /** A decoration's ink for a colour of its own, [own], or [fallback] without one. */
            fun ink(own: Color, fallback: Color): Color = when {
                keep && own.isSpecified -> own
                // On its patch of the page's ground, the page's own ink.
                standOff -> fallback
                !enabled -> colours.contentDisabled
                filled -> label
                // On the day's own colour, its colours would not read.
                ownFill -> label
                own.isSpecified -> own
                inRange -> colours.accent.onContainer
                else -> fallback
            }
            val dots = decoration?.dots.orEmpty().take(MaxDayDots)
            Layout(
                modifier = Modifier.matchParentSize(),
                content = {
                    Text(
                        text = formats.digits(date.day.toLong()),
                        style = dayStyle,
                        colour = label,
                        modifier = Modifier.layoutId(DayPart.Number),
                    )
                    if (lined && decoration != null && decoration.lined) {
                        val textInk = ink(decoration.textColour, colours.contentMuted)
                        val iconInk = if (decoration.iconColour.isSpecified) ink(decoration.iconColour, textInk) else textInk
                        Row(
                            modifier = Modifier
                                .layoutId(DayPart.Line)
                                .then(
                                    if (standOff) {
                                        Modifier
                                            .background(colours.surface, Theme.shapes.pill)
                                            .padding(horizontal = KeptCapsulePad)
                                    } else {
                                        Modifier
                                    }
                                ),
                            horizontalArrangement = Arrangement.spacedBy(DotGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            decoration.icon?.let {
                                Icon(it, contentDescription = null, tint = iconInk, size = DecorationIconSize)
                            }
                            decoration.text?.let {
                                Text(
                                    text = it,
                                    style = Theme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                                    colour = textInk,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }
                    }
                    if (dots.isNotEmpty()) {
                        val inks = dots.map { ink(it, it) }
                        val halo = colours.surface
                        Spacer(
                            Modifier
                                .layoutId(DayPart.Marker)
                                .size(
                                    width = MarkerSize * dots.size + DotGap * (dots.size - 1) + KeptHalo * 2,
                                    height = MarkerSize + KeptHalo * 2,
                                )
                                .drawBehind {
                                    val r = MarkerSize.toPx() / 2f
                                    val ring = KeptHalo.toPx()
                                    val pitch = MarkerSize.toPx() + DotGap.toPx()
                                    for ((n, c) in inks.withIndex()) {
                                        val x = ring + r + n * pitch
                                        val centre = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - x else x, size.height / 2f)
                                        if (standOff) drawCircle(halo, r + ring, centre)
                                        drawCircle(c, r, centre)
                                    }
                                }
                        )
                    }
                },
            ) { measurables, constraints ->
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val number = measurables.first { it.layoutId == DayPart.Number }.measure(loose)
                val note = measurables.firstOrNull { it.layoutId == DayPart.Line }?.measure(loose)
                val dot = measurables.firstOrNull { it.layoutId == DayPart.Marker }?.measure(loose)
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                // Centred on its own, whatever is under it; in a month with a
                // decoration line, centred with a line under it, whether this day
                // has one or not.
                val block = number.height + if (lined) decorationLine else 0
                val numberY = ((height - block) / 2).coerceAtLeast(0)
                val underNumber = numberY + number.height
                layout(width, height) {
                    number.placeRelative((width - number.width) / 2, numberY)
                    note?.placeRelative((width - note.width) / 2, underNumber + (decorationLine - note.height) / 2)
                    dot?.let {
                        val below = if (lined) underNumber + decorationLine else underNumber
                        val y = (below + markerGap).coerceAtMost(height - it.height)
                        it.placeRelative((width - it.width) / 2, y)
                    }
                }
            }
        }
    }
}

/** The pieces of a day, as its layout tells them apart. */
private enum class DayPart { Number, Line, Marker }

/** Between the number, or its line, and the dots under it. */
private val MarkerGap = 2.dp

/** A dot under a day. */
private val MarkerSize = 4.dp

/** Between one of a day's dots and the next, and between its icon and its text. */
private val DotGap = 2.dp

/** The most dots a day draws: as many as fit under a number without crowding it. */
private const val MaxDayDots = 3

/** The page's ground round a kept dot on a chosen day's fill. */
private val KeptHalo = 1.dp

/** Either side of a kept line on a chosen day's fill, in the page's ground. */
private val KeptCapsulePad = 2.dp

/** A decoration's icon: as tall as the line's type. */
private val DecorationIconSize = 12.dp

/** How far inside a chosen day's fill its own colour's ring is drawn, kept. */
private val KeptRingInset = 2.dp

/** How far inside the band a kept day's own circle sits, so the band shows round it. */
private val KeptCircleInset = 2.dp

/**
 * The whole of the cell's padded box — **not** a square derived from its width.
 *
 * It was `fillMaxWidth().aspectRatio(1f)`, so the fill's height was its width.
 * The cell's horizontal padding varies with where it sits in a range (0dp in the
 * middle, 1dp at a cap, 2dp on its own) while the vertical padding is fixed, so
 * that turned a deliberate difference in *width* into an accidental difference in
 * *height*: middles drew 1dp taller than the caps and a single-day range drew
 * shortest of all. It also asked for a square taller than the box it was in,
 * which `Box` does not clip, so the difference showed rather than being absorbed.
 *
 * Filling the box instead makes the height uniform by construction — every cell
 * is inset the same 1dp top and bottom — and lets the width go on doing its job
 * of joining the band up.
 */
private fun Modifier.matchCellSize(): Modifier = fillMaxSize()


/**
 * The tallest a week gets, a little under a cell's widest.
 *
 * Square cells stacked six weeks deep made a calendar taller than anything near
 * it on a page for the sake of a row of single digits. A selected day is still a
 * circle, as tall as its week.
 */
internal val MaxRowHeight = 40.dp

/** What a disabled month asks of each day. One instance, so it compares equal. */
private val NoDateSelectable: (LocalDate) -> Boolean = { false }
