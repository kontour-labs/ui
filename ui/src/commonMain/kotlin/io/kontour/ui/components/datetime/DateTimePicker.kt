package io.kontour.ui.components.datetime

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonDefaults
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.overlay.LocalOverlayHostOrNull
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.Popover
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** What a [DateTimePicker] takes by default. */
object DateTimePickerDefaults {
    /** The date and time now on the system's clock, in its zone: what a picker's Now turns to. */
    fun currentDateTime(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
}

/**
 * Picks a date and a time of day: a [DatePicker] and a [TimePicker], side by
 * side where there is room for both, and otherwise the calendar with the time
 * on a row under it that opens the wheels.
 *
 * ```
 * DateTimePicker(
 *     value = leaveAt,
 *     onValueChange = viewModel::setLeaveAt,
 *     today = today,
 *     isDateSelectable = { it >= today },
 *     minuteStep = 5,
 * )
 * ```
 *
 * Asked for as *"a component for a date and time picker? it should basically
 * just be the two individual components we currently have for that, combined"*,
 * and that is what it is: the month grid with its header, paging, chooser and
 * marks, and the hour and minute wheels on the reader's own clock, each as it
 * is on its own but for the wheels' width, which is their numbers' and not a
 * fixed 72dp. A day chosen keeps the time, and a wheel turned keeps the day.
 *
 * **Side by side wherever they fit.** Wherever the calendar, at 40dp a day, a
 * gap and the wheels fit across, they sit next to each other, centred
 * together, the calendar taking what is left up to its widest. On a 24-hour
 * clock at the default type size that is 390dp or so, which a large phone is;
 * the wheels are measured, so a 12-hour clock's day period, 453dp, and larger
 * type each take more.
 *
 * **Narrower, the calendar and a row for the time**, as an iOS inline picker
 * has them: the calendar across the width, up to its widest, and under it
 * *Time* and the chosen time in a pill at the row's end. Pressing the pill
 * opens the wheels in a popover anchored to it; they set the time as they
 * turn, and a tap outside, back or Escape puts them away. The row is one
 * control's height, where the wheels under the calendar were some 250dp and
 * *"way too much vertical room"*, and both halves of the value show at once on
 * any phone and either clock. The wheels need an [io.kontour.ui.overlay.OverlayHost]
 * to open into, as a menu does.
 *
 * Only the width decides, so nothing changes arrangement as a page scrolls or
 * a sheet is dragged, and the calendar keeps its month through a change of
 * arrangement.
 *
 * `isDateSelectable` turns days away and not times: a time earlier today than
 * now is the caller's to refuse, since only the caller knows whether "now" is
 * the floor.
 *
 * @param value The date and time shown: the day selected in the grid, and the
 *   time on the wheels.
 * @param onValueChange Every change: a day tapped, or each row a wheel passes,
 *   as the time picker's own does.
 * @param minuteStep Coarsens the minute wheel; see [TimePicker].
 * @param now The date and time now, for the **Now** button under the wheels,
 *   beside the calendar or in the popover: pressed, the wheels scroll to the
 *   time — rounded to [minuteStep] — and the day becomes today, the calendar
 *   paging to it. The system clock's by default; null for no button.
 * @param navigation Which month is on show; it opens on [value]'s.
 * @param onValueChangeFinished Called once a change has settled: when a day is
 *   tapped, and when a wheel comes to rest after the reader turned it. A save,
 *   a request or a toast belongs here rather than in [onValueChange], which a
 *   flick of the minutes calls a dozen times. Not called when [value] is set by
 *   the caller.
 * @see DatePicker for [today], [isDateSelectable], [decorationFor],
 *   [showDecorationLine], the icons and [firstDayOfWeek], which
 *   mean what they mean there.
 */
@Composable
fun DateTimePicker(
    value: LocalDateTime,
    onValueChange: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    /** Whether a day can be chosen and the wheels turned; disabled, both show the value and answer nothing. */
    enabled: Boolean = true,
    today: LocalDate? = null,
    isDateSelectable: (LocalDate) -> Boolean = { true },
    decorationFor: ((LocalDate) -> DayDecoration?)? = null,
    showDecorationLine: Boolean = decorationFor != null,
    previousIcon: ImageVector? = SystemIcons.ChevronBack,
    nextIcon: ImageVector? = SystemIcons.ChevronForward,
    todayIcon: ImageVector? = DatePickerDefaults.TodayIcon,
    chooserIcon: ImageVector? = null,
    minuteStep: Int = 1,
    now: (() -> LocalDateTime)? = DateTimePickerDefaults::currentDateTime,
    navigation: CalendarNavigationState = rememberCalendarNavigationState(value.date),
    formats: LocaleFormats = Theme.formats,
    firstDayOfWeek: DayOfWeek = formats.conventions.firstDayOfWeek,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    // Each half changes its own part of whatever the value is by then, not of
    // the value it was composed with: a day tapped while the minutes are still
    // settling keeps the minute they settle on.
    val working = remember { Working(value) }
    working.compose(value)
    val report by rememberUpdatedState(onValueChange)
    val change: (LocalDateTime) -> Unit = { next ->
        if (next != working.value) {
            working.value = next
            report(next)
        }
    }
    val finished by rememberUpdatedState(onValueChangeFinished)
    val date = @Composable { dateModifier: Modifier ->
        DatePicker(
            value = value.date,
            onValueChange = { date ->
                change(LocalDateTime(date, working.value.time))
                // A tap is the whole of a day's change: settled as it lands.
                finished?.invoke()
            },
            enabled = enabled,
            today = today,
            isDateSelectable = isDateSelectable,
            decorationFor = decorationFor,
            showDecorationLine = showDecorationLine,
            previousIcon = previousIcon,
            nextIcon = nextIcon,
            todayIcon = todayIcon,
            chooserIcon = chooserIcon,
            navigation = navigation,
            formats = formats,
            firstDayOfWeek = firstDayOfWeek,
            modifier = dateModifier,
        )
    }
    // Now read once a press, for the wheels' time and the calendar's day alike.
    val pressedAt = remember { arrayOfNulls<LocalDate>(1) }
    val time = @Composable { timeModifier: Modifier ->
        TimeWheels(
            value = value.time,
            onValueChange = { time -> change(LocalDateTime(working.value.date, time)) },
            modifier = timeModifier,
            enabled = enabled,
            minuteStep = minuteStep,
            now = now?.let { read -> { read().also { pressedAt[0] = it.date }.time } },
            formats = formats,
            onValueChangeFinished = onValueChangeFinished,
            narrow = true,
            // The day goes with the time: today, or tomorrow when the time
            // rounded past midnight, and the calendar pages there.
            onNow = { tomorrow ->
                val today = pressedAt[0]
                val next = today?.plus(1, DateTimeUnit.DAY)
                // Tomorrow only where the calendar would let it be chosen.
                // Refused, Now is the end of today instead — the wheels turn to
                // its last step — rather than midnight on a day that stays put,
                // which was 24 hours before the time now.
                val tookTomorrow = tomorrow && next != null && (next == working.value.date || isDateSelectable(next))
                val day = if (tookTomorrow) next else today
                // Only to a day the calendar would let be chosen: one it would
                // refuse, today included, keeps the day there was.
                if (day != null && day != working.value.date && isDateSelectable(day)) {
                    change(LocalDateTime(day, working.value.time))
                    navigation.jumpTo(day)
                }
                tookTomorrow
            },
        )
    }

    // Under the calendar where the wheels do not fit beside it: the same
    // wheels, Now and all, in a popover from the row.
    val row = @Composable {
        TimeRow(
            time = value.time,
            formats = formats,
            enabled = enabled,
            wheels = time,
        )
    }
    // The wheels as they are drawn, but with nothing that changes as they turn,
    // so this copy is measured once and not composed again with every row the
    // real ones pass.
    val probe = @Composable {
        TimeWheels(
            value = LocalTime(0, 0),
            onValueChange = Unheard,
            modifier = Silent,
            enabled = enabled,
            minuteStep = minuteStep,
            now = if (now != null) Midnight else null,
            formats = formats,
            onValueChangeFinished = null,
            onNow = null,
            narrow = true,
        )
    }
    // The gap between the two is small because it is not all the space between
    // them: the calendar's last column has room of its own round its numbers,
    // so a reader sees some 20dp from a Sunday to the hours. Every dp of it is
    // one a phone needs to show the two together.
    val beside = Theme.spacing.xs
    SubcomposeLayout(modifier) { constraints ->
        val gap = beside.roundToPx()
        // **The wheels' own width, measured**, from a copy that is never placed:
        // a 12-hour clock's day period, a long word for it and larger type all
        // widen them, and a width written down here was wrong for all three.
        val wheels = subcompose(Part.Probe, probe).maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        // Side by side where a calendar of days no narrower than a week is
        // tall fits beside them: on the width this is given and nothing else,
        // so the arrangement cannot change under a reader's finger.
        val sideBySide = !constraints.hasBoundedWidth ||
            constraints.maxWidth >= (NarrowestDay * 7).roundToPx() + gap + wheels
        val widest = CalendarMonthDefaults.MaxWidth.roundToPx()

        if (sideBySide) {
            // The wheels as wide as they are, and the calendar what is left up
            // to its widest: given the rest of the row, the wheels sat in the
            // middle of it, some 70dp off the calendar on a wide window —
            // *"there's too much room between the date picker and time
            // picker"*.
            val calendarWidth = if (constraints.hasBoundedWidth) minOf(widest, constraints.maxWidth - gap - wheels) else widest
            val date = subcompose(Part.Date) { date(Modifier) }
                .map { it.measure(Constraints(maxWidth = calendarWidth, maxHeight = constraints.maxHeight)) }
            val time = subcompose(Part.Time) { time(Modifier) }
                .map { it.measure(Constraints(minWidth = wheels, maxWidth = wheels, maxHeight = constraints.maxHeight)) }
            val dateWidth = date.maxOfOrNull { it.width } ?: 0
            val pair = dateWidth + gap + wheels
            val tallest = (date + time).maxOfOrNull { it.height } ?: 0
            val width = constraints.constrainWidth(pair)
            layout(width, constraints.constrainHeight(tallest)) {
                // Centred together across the width, and on each other's
                // middles. Relative, so right to left the calendar is at the
                // right and the wheels come after it, to its left.
                val start = (width - pair) / 2
                date.forEach { it.placeRelative(start, (tallest - it.height) / 2) }
                time.forEach { it.placeRelative(start + dateWidth + gap, (tallest - it.height) / 2) }
            }
        } else {
            // **The calendar whole, and the row as wide as it is**, so the
            // label starts where the calendar does and the pill ends where it
            // ends, whatever width that is.
            val column = if (constraints.hasBoundedWidth) minOf(widest, constraints.maxWidth) else widest
            val date = subcompose(Part.Date) { date(Modifier) }
                .map { it.measure(Constraints(maxWidth = column, maxHeight = constraints.maxHeight)) }
            val dateWidth = date.maxOfOrNull { it.width } ?: 0
            val dateHeight = date.maxOfOrNull { it.height } ?: 0
            val time = subcompose(Part.Row, row)
                .map { it.measure(Constraints(minWidth = dateWidth, maxWidth = dateWidth, maxHeight = constraints.maxHeight)) }
            val rowHeight = time.maxOfOrNull { it.height } ?: 0
            val width = constraints.constrainWidth(column)
            layout(width, constraints.constrainHeight(dateHeight + rowHeight)) {
                val start = (width - dateWidth) / 2
                date.forEach { it.placeRelative(start, 0) }
                time.forEach { it.placeRelative(start, dateHeight) }
            }
        }
    }
}

/**
 * *Time*, and the chosen time in a pill that opens the wheels, under a
 * [DateTimePicker]'s calendar: what an iOS inline picker has there.
 *
 * The pill is a button named for what it sets and saying the time it shows,
 * tinted while its wheels are open. Opened from the keyboard, the focus goes
 * into the wheels as a menu's goes into its rows — to the first thing there
 * that takes it, which is Now, as a drum takes none of its own — and when they
 * close it comes back to the pill rather than to nothing.
 */
@Composable
private fun TimeRow(
    time: LocalTime,
    formats: LocaleFormats,
    enabled: Boolean,
    wheels: @Composable (Modifier) -> Unit,
) {
    val colours = Theme.colours
    val strings = Theme.strings
    val shown = formats.time(time)
    // A popover needs a host and throws without one, and a picker may be drawn
    // where there is none — a test, a preview. There the pill still says the
    // time; it has nowhere to open the wheels.
    val canOpen = LocalOverlayHostOrNull.current != null
    var open by remember { mutableStateOf(false) }
    val modality = LocalInputModality.current
    val interactions = remember { MutableInteractionSource() }
    val pillFocused by interactions.collectIsFocusedAsState()
    val pill = remember { FocusRequester() }
    val focus = remember { WheelsFocus() }

    // In by as much as the calendar's own ink is: the label starts under its
    // back arrow and its first column of days, and the pill ends under its
    // forward arrow and its last. At the edge of the calendar's frame, both
    // stood out past everything above them.
    Row(
        Modifier.heightIn(min = Theme.sizing.controlHeightMedium).padding(horizontal = Theme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.time,
            style = Theme.typography.bodyMedium,
            colour = if (enabled) colours.content else colours.contentDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Said by the pill, which it names: read on its own as well, a
            // screen reader said "Time" twice.
            modifier = Modifier.weight(1f).alignByBaseline().clearAndSetSemantics {},
        )
        // The popover's anchor is this box, the pill's own bounds.
        Box(Modifier.alignByBaseline()) {
            Button(
                onClick = {
                    if (canOpen) {
                        // From the keyboard, the focus goes with it: a key
                        // pressed on a focused pill is the keyboard's press.
                        focus.moveIn = modality == InputModality.Keyboard || pillFocused
                        open = !open
                    }
                },
                modifier = Modifier
                    .focusRequester(pill)
                    .semantics {
                        stateDescription = shown
                        if (canOpen && enabled) {
                            if (open) {
                                collapse { open = false; true }
                            } else {
                                expand { open = true; true }
                            }
                        }
                    },
                enabled = enabled,
                contentDescription = strings.time,
                variant = ButtonVariant.Tertiary,
                size = ButtonSize.Small,
                // The accent while the wheels are open, as iOS tints its pill:
                // what they turn is this. At high contrast the tint is too near
                // its ground to say so alone, and the accent edges it too.
                colours = if (open) {
                    ButtonDefaults.colours(
                        ButtonVariant.Tertiary,
                        container = colours.accent.container,
                        content = colours.accent.onContainer,
                        border = if (highContrast()) colours.accent.onContainer else null,
                    )
                } else {
                    ButtonDefaults.colours(ButtonVariant.Tertiary)
                },
                // The label's type, in figures of one width, so the pill does
                // not twitch as the minutes turn under it.
                metrics = ButtonDefaults.metrics(ButtonSize.Small)
                    .copy(textStyle = Theme.typography.bodyMedium.copy(fontFeatureSettings = TabularFigures)),
                interactionSource = interactions,
            ) { +shown }

            if (canOpen) {
                Popover(
                    visible = open,
                    onDismissRequest = { open = false },
                    // Its end on the pill's, which is the calendar's: the
                    // popover hangs inside the picker's edge.
                    alignment = OverlayAlignment.End,
                    // A drag on a wheel is the wheel's, not the reader leaving.
                    dismissOnScroll = false,
                ) {
                    val inside = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        if (focus.moveIn) requestFocusWithin(inside)
                    }
                    wheels(
                        Modifier
                            // As wide as the wheels, not as the popover may be.
                            .width(IntrinsicSize.Max)
                            .focusRequester(inside)
                            .onFocusChanged { if (it.hasFocus) focus.wasInside = true }
                            .focusGroup(),
                    )
                }
            }
        }
    }

    // Closed with the focus in the wheels, or opened from the keyboard, it
    // comes back to the pill. Left to itself it went with the popover, to
    // nothing, and the next Tab started from the top of the page.
    LaunchedEffect(open) {
        if (!open && (focus.wasInside || focus.moveIn)) {
            focus.wasInside = false
            focus.moveIn = false
            requestFocusWithin(pill)
        }
    }
}

/**
 * Where a [TimeRow]'s focus goes: into the wheels as they open from the
 * keyboard, and back to the pill as they close when it went in or was found
 * in them, and not otherwise, so a tap does not leave a focus behind it.
 * Plain, as it is read when the popover opens and closes and never drawn.
 */
private class WheelsFocus {
    var moveIn = false
    var wasInside = false
}

/**
 * Asks [requester] for the focus, again each frame for a few: a popover just
 * opened, or one just closed and still giving the page back, cannot take it,
 * or give it, on the first.
 */
private suspend fun requestFocusWithin(requester: FocusRequester) {
    for (attempt in 0 until FocusTries) {
        if (runCatching { requester.requestFocus() }.getOrDefault(false)) return
        withFrameMillis { }
    }
}

/** How many frames [requestFocusWithin] asks for. */
private const val FocusTries = 10

/** The OpenType feature for figures of one width. */
private const val TabularFigures = "tnum"

/** The parts of a [DateTimePicker], each composed once and kept as the width changes. */
private enum class Part { Probe, Date, Time, Row }

/**
 * How narrow a day may be for a [DateTimePicker]'s calendar to sit beside its
 * wheels: as wide as a week is tall, 40dp, so a day is still a square target
 * the height every week already has, and its number has room round it at any
 * type size. A calendar at its widest has 44.
 */
private val NarrowestDay: Dp = MaxRowHeight

/** What is composed only to be measured: nothing a screen reader should find. */
private val Silent = Modifier.clearAndSetSemantics {}

/** The measured wheels' change, which goes nowhere. */
private val Unheard: (LocalTime) -> Unit = {}

/** The measured wheels' Now: a time, so they have the button, and never read. */
private val Midnight: () -> LocalTime = { LocalTime(0, 0) }

/**
 * The chosen date and time, shown as a tappable field.
 *
 * ```
 * var departs by remember { mutableStateOf(LocalDateTime(2026, 3, 14, 9, 5)) }
 * var picking by remember { mutableStateOf(false) }
 *
 * DateTimeField(value = departs, onClick = { picking = true }, label = "Departs")
 *
 * FieldPicker(visible = picking, onDismissRequest = { picking = false }, title = "Departs") {
 *     DateTimePicker(value = departs, onValueChange = { departs = it })
 * }
 * ```
 *
 * The thing you put on a form, as [TimeField] and [DateField] are: tapping it is
 * expected to open a [DateTimePicker] in a [FieldPicker]. Written as the
 * locale writes a date and a time together.
 *
 * @param value The date and time chosen. Always one, as a [DateTimePicker]
 *   always has one: there is no empty state to show a placeholder for.
 * @param supporting A line under the field, as a [DateField]'s.
 * @param errorMessage In place of [supporting], in the error colour, as a
 *   [DateField]'s.
 * @param formats Writes the value; the theme's, by default.
 */
@Composable
fun DateTimeField(
    value: LocalDateTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    supporting: String? = null,
    errorMessage: String? = null,
    formats: LocaleFormats = Theme.formats,
    interactionSource: MutableInteractionSource? = null,
) {
    PickerField(
        text = formats.dateTime(value.date, value.time),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        label = label,
        interactionSource = interactionSource,
        supporting = supporting,
        errorMessage = errorMessage,
    )
}
