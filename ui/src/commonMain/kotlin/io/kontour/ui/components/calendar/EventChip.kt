package io.kontour.ui.components.calendar

import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import kotlinx.datetime.LocalDate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.TodayFlashAlpha
import io.kontour.ui.components.datetime.LocalTodayFlash
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.PickUpAxis
import io.kontour.ui.interaction.detectPickUp
import io.kontour.ui.interaction.pressable
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.ListStyle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.toneColours

/**
 * One event's box, bar or line, with what the calendar keeps for itself round
 * whatever its `eventContent` draws: its colour, its press, its edge at high
 * contrast, what a screen reader says it is — and, where the calendar is given
 * the callbacks, its move and its edges.
 *
 * Short events are below the 48dp a control is otherwise given: a quarter-hour
 * at an hour's 48dp is 12dp, and growing it would cover the next one. A screen
 * reader reaches every event as its own node, and a keyboard by Tab.
 *
 * @param byDays Whether a move carries it by whole days — a bar, or a line in a
 *   month — rather than through the hours.
 * @param ghost Where an edit would put it: drawn lifted, and not a control.
 * @param blockHeight How tall a block is drawn, where the column knows as it
 *   composes: for the default content to fit its title to.
 * @param focusableOn The days the keyboard reaches it on, by Tab from the grid
 *   it is in: only while the grid's cursor is on one of them. Null, always.
 */
@Composable
internal fun EventChip(
    event: CalendarEvent,
    presentation: EventPresentation,
    scope: CalendarScope,
    modifier: Modifier = Modifier,
    byDays: Boolean = presentation != EventPresentation.Block,
    clippedStart: Boolean = false,
    clippedEnd: Boolean = false,
    ghost: Boolean = false,
    focusableOn: ClosedRange<LocalDate>? = null,
    blockHeight: Dp? = null,
) {
    val colours = toneColours(event.tone)
    // A block is a box of time, cornered like the library's other small boxes;
    // a bar and a line are labels across days, and capsules as chips are.
    val shape = if (presentation == EventPresentation.Block) Theme.shapes.extraSmall else Theme.shapes.control
    val (fill, ink) = when (presentation) {
        EventPresentation.Block -> colours.container to colours.onContainer
        EventPresentation.Bar -> colours.solid to colours.onSolid
        EventPresentation.Line -> (if (ghost) colours.container else Color.Transparent) to Theme.colours.content
    }
    val edge = when {
        ghost -> BorderStroke(Theme.sizing.borderWidthStrong, colours.solid)
        presentation == EventPresentation.Line -> null
        // A block as a banner is: its tone's ground and its tone's hairline.
        presentation == EventPresentation.Block -> BorderStroke(Theme.sizing.borderWidth, colours.border)
        else -> contrastEdge(colours.border)
    }
    val edit = scope.edit
    // The event as it was, while an edit carries it: there, faded, so where it
    // came from stays in view.
    val carried = !ghost && edit.live && edit.event?.id == event.id

    Box(
        modifier
            .graphicsLayer { alpha = if (carried) CarriedAlpha else 1f }
            .clip(shape)
            .background(fill)
            .then(if (edge != null) Modifier.border(edge, shape) else Modifier)
            .then(if (ghost) Modifier.clearAndSetSemantics { } else Modifier.eventControl(event, scope, byDays, presentation, focusableOn)),
        // A block's text from its top, as time runs; a bar's and a line's level
        // in its row, as a chip's label is.
        contentAlignment = if (presentation == EventPresentation.Block) Alignment.TopStart else Alignment.CenterStart,
    ) {
        CompositionLocalProvider(LocalBlockHeight provides blockHeight) {
            ProvideContentColour(ink) { scope.eventContent(event, presentation) }
        }
        // A block's edges, in its hours; a bar's ends, by whole days. Not on
        // an end cut off by the edge of the week or the page: it is not the
        // event's end there.
        val edges = presentation != EventPresentation.Line
        if (!ghost && edges && scope.resizable(event) && (!scope.touch || edit.active == event.id)) {
            val bar = presentation == EventPresentation.Bar
            if (!clippedStart) ResizeHandle(event, scope, atStart = true, bar = bar)
            if (!clippedEnd) ResizeHandle(event, scope, atStart = false, bar = bar)
        }
    }
}

/** How faded the event left behind is while an edit carries it. */
private const val CarriedAlpha = 0.4f

/**
 * An event as a control: its press, its move — a pointer's, the keyboard's,
 * a screen reader's actions — and what it is called.
 */
@Composable
private fun Modifier.eventControl(
    event: CalendarEvent,
    scope: CalendarScope,
    byDays: Boolean,
    presentation: EventPresentation,
    focusableOn: ClosedRange<LocalDate>?,
): Modifier {
    // Whether it has edges to adjust, and whether they go by whole days.
    val edges = presentation != EventPresentation.Line
    val bar = presentation == EventPresentation.Bar
    val description = eventDescription(event, scope.formats, scope.strings)
    val click = scope.onEventClick
    val movable = scope.movable(event)
    val resizable = scope.resizable(event)
    val edit = scope.edit
    val placed = remember { PlacedAt() }
    val press = remember { PressAt() }
    val current by rememberUpdatedState(event)
    val isTouch by rememberUpdatedState(scope.touch)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val step = edit.stepMinutes.toLong()
    val actions = buildList {
        if (movable) {
            add(CustomAccessibilityAction(scope.strings.moveEarlier) { scope.moveBy(current, -step, resize = false); true })
            add(CustomAccessibilityAction(scope.strings.moveLater) { scope.moveBy(current, step, resize = false); true })
            add(CustomAccessibilityAction(scope.strings.moveToPreviousDay) { scope.moveBy(current, -MinutesPerDay.toLong(), resize = false); true })
            add(CustomAccessibilityAction(scope.strings.moveToNextDay) { scope.moveBy(current, MinutesPerDay.toLong(), resize = false); true })
        }
        // A bar longer or shorter by a day; anything else by a step.
        val resizeStep = if (bar) MinutesPerDay.toLong() else step
        if (resizable && (bar || !event.allDay)) {
            add(CustomAccessibilityAction(scope.strings.makeLonger) { scope.moveBy(current, resizeStep, resize = true, days = bar); true })
            add(CustomAccessibilityAction(scope.strings.makeShorter) { scope.moveBy(current, -resizeStep, resize = true, days = bar); true })
        }
        if (scope.deletable(event)) {
            add(CustomAccessibilityAction(scope.strings.deleteEvent) { scope.delete(current); true })
        }
    }

    if (movable) {
        DisposableEffect(edit, event.id) {
            onDispose {
                edit.draggables.remove(placed)
                edit.events.remove(placed)
            }
        }
    }

    return this
        .onGloballyPositioned { coordinates ->
            placed.coordinates = coordinates
            if (movable) {
                edit.draggables[placed] = coordinates
                edit.events[placed] = Grab(coordinates, current, byDays, edges = edges)
            }
        }
        .then(
            if (focusableOn != null) {
                // Read when focus moves, not in composition: the cursor's day,
                // or the event being carried from the keyboard.
                Modifier.focusProperties {
                    canFocus = scope.state.date in focusableOn || (edit.live && edit.event?.id == current.id)
                }
            } else {
                Modifier
            },
        )
        .then(
            if (click != null) {
                Modifier.pressable(onClick = { click(event) }, shape = Theme.shapes.control, enabled = scope.enabled, minimumTouchTarget = false)
            } else if (movable || resizable || scope.deletable(event)) {
                // Not a button, but still somewhere the keyboard can pick it up from.
                Modifier.focusable(enabled = scope.enabled)
            } else {
                Modifier
            },
        )
        .then(if (movable && !scope.touch) Modifier.pointerCursor(Cursor.Grab) else Modifier)
        .then(
            if (movable) {
                Modifier.notesPress(press).pointerInput(edit, event.id, byDays) {
                    var taking: PointerId? = null
                    detectPickUp(
                        axis = PickUpAxis.Free,
                        immediate = { !isTouch },
                        // A scroll of the hours, or a swipe of the pages, is not a hold.
                        givesWay = true,
                        onPointer = { taking = it },
                        onPickUp = { at ->
                            val coordinates = placed.coordinates?.takeIf { it.isAttached } ?: return@detectPickUp
                            val root = coordinates.localToRoot(press.position ?: at)
                            scope.takeUp(current, root, byDays, top = coordinates.localToRoot(Offset.Zero).y, pointer = taking, hold = isTouch, edges = edges)
                            // A mouse is already on its way: the proposal follows it from here.
                            if (!isTouch) edit.move(coordinates.localToRoot(at))
                        },
                        // The calendar follows the carry from here: see `carriesEdits`.
                        onCarry = { _, _ -> },
                        onPutDown = {},
                        onCancel = {},
                    )
                }
            } else {
                Modifier
            },
        )
        .then(
            if (scope.hasMenu(event, edges = edges)) {
                Modifier.secondaryClick { at ->
                    placed.coordinates?.takeIf { it.isAttached }?.let { scope.openMenu(it.localToRoot(at), current, edges = edges) }
                }
            } else {
                Modifier
            },
        )
        .then(
            if (movable || resizable || scope.deletable(event)) {
                Modifier.onKeyEvent { key ->
                    if (key.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val holding = edit.live && edit.event?.id == current.id
                    when {
                        !holding && (key.key == Key.Delete || key.key == Key.Backspace) && scope.deletable(current) -> {
                            scope.delete(current)
                            true
                        }
                        !holding && movable && key.key == Key.Spacebar -> {
                            edit.beginKeyboard(current)
                            scope.announcer.say(scope.strings.dragPickedUp(current.title))
                            true
                        }
                        !holding -> false
                        key.key == Key.Escape -> {
                            edit.cancel()
                            scope.announcer.say(scope.strings.dragCancelled(current.title))
                            true
                        }
                        key.key == Key.Enter || key.key == Key.NumPadEnter || key.key == Key.Spacebar -> {
                            scope.putDown()
                            true
                        }
                        key.key == Key.DirectionUp || key.key == Key.DirectionDown -> {
                            val by = if (key.key == Key.DirectionUp) -step else step
                            if (key.isShiftPressed) {
                                // A bar's end by a day; a block's by a step, within its day.
                                if (resizable && bar) {
                                    edit.nudge(if (by > 0) MinutesPerDay.toLong() else -MinutesPerDay.toLong(), resizeEnd = true, byDaysEnd = true)
                                } else if (resizable) {
                                    edit.nudge(by, resizeEnd = true)
                                }
                            } else if (movable) {
                                edit.nudge(by)
                            }
                            true
                        }
                        key.key == Key.DirectionLeft || key.key == Key.DirectionRight -> {
                            // The day before is on the left, unless the reading runs the other way.
                            val later = (key.key == Key.DirectionRight) != rtl
                            val day = if (later) MinutesPerDay.toLong() else -MinutesPerDay.toLong()
                            if (key.isShiftPressed) {
                                if (resizable && bar) edit.nudge(day, resizeEnd = true, byDaysEnd = true)
                            } else if (movable) {
                                edit.nudge(day)
                            }
                            true
                        }
                        else -> false
                    }
                }
            } else {
                Modifier
            },
        )
        .semantics(mergeDescendants = true) {
            contentDescription = description
            if (actions.isNotEmpty()) customActions = actions
        }
}

/**
 * One of a block's edges, to drag its start or its end by — or a [bar]'s ends,
 * across its days, a whole day at a time: always there for a mouse, with its
 * cursor, and on a touchscreen once "Adjust time" is chosen from the event's
 * menu, until a press anywhere else.
 */
@Composable
private fun BoxScope.ResizeHandle(event: CalendarEvent, scope: CalendarScope, atStart: Boolean, bar: Boolean) {
    val edit = scope.edit
    val placed = remember { PlacedAt() }
    val current by rememberUpdatedState(event)
    val active = scope.touch && edit.active == event.id
    val grip = toneColours(event.tone).solid
    DisposableEffect(edit) {
        onDispose {
            edit.draggables.remove(placed)
            edit.events.remove(placed)
        }
    }
    Box(
        Modifier
            .then(
                if (bar) {
                    Modifier.align(if (atStart) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().width(HandleHeight)
                } else {
                    Modifier.align(if (atStart) Alignment.TopCenter else Alignment.BottomCenter).fillMaxWidth().height(HandleHeight)
                },
            )
            .onGloballyPositioned { coordinates ->
                placed.coordinates = coordinates
                edit.draggables[placed] = coordinates
                edit.events[placed] = Grab(coordinates, current, byDays = bar, edge = true)
            }
            .pointerCursor(if (bar) Cursor.ResizeColumn else Cursor.ResizeRow)
            .pointerInput(edit, event.id, atStart, bar) {
                var taking: PointerId? = null
                detectPickUp(
                    axis = if (bar) PickUpAxis.Free else PickUpAxis.Vertical,
                    immediate = { true },
                    onPointer = { taking = it },
                    onPickUp = { at ->
                        val coordinates = placed.coordinates?.takeIf { it.isAttached } ?: return@detectPickUp
                        val root = coordinates.localToRoot(at)
                        edit.beginResize(current, atStart, root, byDays = bar, top = coordinates.localToRoot(Offset.Zero).y)
                        if (!edit.live) return@detectPickUp
                        taking?.let { edit.take(it, root, hold = false) }
                    },
                    // The calendar follows the drag from here: see `carriesEdits`.
                    onCarry = { _, _ -> },
                    onPutDown = {},
                    onCancel = {},
                )
            }
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            val size = if (bar) Modifier.width(GripHeight).height(BarGripHeight) else Modifier.width(GripWidth).height(GripHeight)
            // On a bar, its own colour is the ground: the grip in the ink on it.
            val colour = if (bar) toneColours(event.tone).onSolid else grip
            Box(size.background(colour, Theme.shapes.capsule))
        }
    }
}

private val HandleHeight = 10.dp
private val GripWidth = 24.dp
private val GripHeight = 4.dp
private val BarGripHeight = 12.dp

/**
 * Moves [event] by [minutes] — or only its end, when [resize] — as a screen
 * reader's action does it: straight to the callback, and read out.
 */
internal fun CalendarScope.moveBy(event: CalendarEvent, minutes: Long, resize: Boolean, days: Boolean = false) {
    if (resize) {
        val end = if (days) {
            barEnd(event.start.epochMinute, event.end.epochMinute, minutes, if (event.allDay) MinutesPerDay else edit.stepMinutes)
        } else {
            resizedEnd(event.start.epochMinute, event.end.epochMinute, event.end.epochMinute + minutes, edit.stepMinutes)
        }
        if (end == event.end.epochMinute) return
        val newEnd = localDateTimeOfEpochMinute(end)
        onEventResize?.invoke(event, event.start, newEnd)
        announce(event.title, event.start, newEnd)
    } else {
        val start = event.start.plusMinutes(minutes)
        val end = event.end.plusMinutes(minutes)
        onEventMove?.invoke(event, start, end)
        announce(event.title, start, end)
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

/**
 * Today's date as the date picker marks it: a hairline circle round the
 * number, so the calendar and the picker beside it agree about what today
 * looks like.
 */
@Composable
internal fun Modifier.todayRing(isToday: Boolean): Modifier {
    if (!isToday) return this
    // And the date picker's pulse inside it when today is gone back to: drawn
    // from the flash in the draw pass, so a pulse is a redraw of one circle.
    val flash = LocalTodayFlash.current
    val colour = Theme.colours.accent.solid
    val shape = Theme.shapes.capsule
    return border(Theme.sizing.borderWidth, Theme.colours.outlineStrong, shape).then(
        if (flash == null) {
            Modifier
        } else {
            Modifier.drawBehind {
                val amount = flash()
                if (amount > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), colour, alpha = amount * TodayFlashAlpha)
            }
        },
    )
}

/**
 * The line round the calendar's grid, and between its days: the stronger
 * outline at high contrast.
 */
@Composable
@ReadOnlyComposable
internal fun gridLine(): Color = if (highContrast()) Theme.colours.outlineStrong else Theme.colours.outline

/**
 * Takes [event] up at [root] — by whole days, or through the hours — for
 * [pointer] to carry, and on a touchscreen, where a finger held still took it,
 * felt and given its menu.
 */
internal fun CalendarScope.takeUp(event: CalendarEvent, root: Offset, byDays: Boolean, top: Float?, pointer: PointerId?, hold: Boolean, edges: Boolean = !byDays) {
    if (byDays) edit.beginMoveByDays(event, root, top) else edit.beginMove(event, root)
    if (!edit.live) return
    pointer?.let { edit.take(it, root, hold) }
    if (hold) {
        edit.pickedUp()
        openMenu(root, event, edges = edges)
    }
}

/** A secondary click — a mouse's right button — at a point in this node. */
internal fun Modifier.secondaryClick(onClick: (Offset) -> Unit): Modifier = pointerInput(onClick) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
            val change = event.changes.firstOrNull() ?: continue
            // Taken already by something inside: an event's own menu, over its day's.
            if (change.isConsumed) continue
            change.consume()
            onClick(change.position)
        }
    }
}
