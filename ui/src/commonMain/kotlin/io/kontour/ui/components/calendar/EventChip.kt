package io.kontour.ui.components.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
) {
    val colours = toneColours(event.tone)
    val shape = Theme.shapes.control
    val stripe = Theme.sizing.borderWidthStrong
    val (fill, ink) = when (presentation) {
        EventPresentation.Block -> colours.container to colours.onContainer
        EventPresentation.Bar -> colours.solid to colours.onSolid
        EventPresentation.Line -> (if (ghost) colours.container else Color.Transparent) to Theme.colours.content
    }
    val edge = when {
        ghost -> BorderStroke(Theme.sizing.borderWidthStrong, colours.solid)
        presentation == EventPresentation.Line -> null
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
            .then(if (ghost) Modifier.clearAndSetSemantics { } else Modifier.eventControl(event, scope, byDays)),
    ) {
        ProvideContentColour(ink) { scope.eventContent(event, presentation) }
        if (!ghost && presentation == EventPresentation.Block && scope.resizable(event) && (!scope.touch || edit.active == event.id)) {
            if (!clippedStart) ResizeHandle(event, scope, atStart = true)
            if (!clippedEnd) ResizeHandle(event, scope, atStart = false)
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
private fun Modifier.eventControl(event: CalendarEvent, scope: CalendarScope, byDays: Boolean): Modifier {
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
        if (resizable && !event.allDay) {
            add(CustomAccessibilityAction(scope.strings.makeLonger) { scope.moveBy(current, step, resize = true); true })
            add(CustomAccessibilityAction(scope.strings.makeShorter) { scope.moveBy(current, -step, resize = true); true })
        }
    }

    if (movable) {
        DisposableEffect(edit, event.id) {
            onDispose { edit.draggables.remove(placed) }
        }
    }

    return this
        .onGloballyPositioned { coordinates ->
            placed.coordinates = coordinates
            if (movable) edit.draggables[placed] = coordinates
        }
        .then(
            if (click != null) {
                Modifier.pressable(onClick = { click(event) }, shape = Theme.shapes.control, enabled = scope.enabled, minimumTouchTarget = false)
            } else if (movable || resizable) {
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
                    fun rootOf(change: PointerInputChange): Offset? =
                        placed.coordinates?.takeIf { it.isAttached }?.localToRoot(change.position)
                    detectPickUp(
                        axis = PickUpAxis.Free,
                        immediate = { !isTouch },
                        onPickUp = { at ->
                            val root = placed.coordinates?.takeIf { it.isAttached }?.localToRoot(press.position ?: at) ?: return@detectPickUp
                            if (byDays) edit.beginMoveByDays(current, root) else edit.beginMove(current, root)
                            if (isTouch) edit.pickedUp()
                        },
                        onCarry = { change, _ ->
                            change.consume()
                            rootOf(change)?.let(edit::move)
                        },
                        onPutDown = {
                            val held = edit.event?.id == current.id
                            val moved = edit.start != current.start || edit.end != current.end
                            if (held && !moved) {
                                // Held and let go where it was: on a touchscreen,
                                // its edges get their handles.
                                edit.cancel()
                                if (isTouch) edit.active = if (edit.active == current.id) null else current.id
                            } else {
                                scope.putDown()
                            }
                        },
                        onCancel = { edit.cancel() },
                    )
                }
            } else {
                Modifier
            },
        )
        .then(
            if (movable || resizable) {
                Modifier.onKeyEvent { key ->
                    if (key.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val holding = edit.live && edit.event?.id == current.id
                    when {
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
                                if (resizable) edit.nudge(by, resizeEnd = true)
                            } else if (movable) {
                                edit.nudge(by)
                            }
                            true
                        }
                        key.key == Key.DirectionLeft || key.key == Key.DirectionRight -> {
                            // The day before is on the left, unless the reading runs the other way.
                            val later = (key.key == Key.DirectionRight) != rtl
                            if (movable) edit.nudge(if (later) MinutesPerDay.toLong() else -MinutesPerDay.toLong())
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
 * One of a block's edges, to drag its start or its end by: always there for a
 * mouse, with its cursor, and on a touchscreen once the event has been held
 * and let go.
 */
@Composable
private fun BoxScope.ResizeHandle(event: CalendarEvent, scope: CalendarScope, atStart: Boolean) {
    val edit = scope.edit
    val placed = remember { PlacedAt() }
    val current by rememberUpdatedState(event)
    val active = scope.touch && edit.active == event.id
    val grip = toneColours(event.tone).solid
    DisposableEffect(edit) { onDispose { edit.draggables.remove(placed) } }
    Box(
        Modifier
            .align(if (atStart) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .height(HandleHeight)
            .onGloballyPositioned { coordinates ->
                placed.coordinates = coordinates
                edit.draggables[placed] = coordinates
            }
            .pointerCursor(Cursor.ResizeRow)
            .pointerInput(edit, event.id, atStart) {
                detectPickUp(
                    axis = PickUpAxis.Vertical,
                    immediate = { true },
                    onPickUp = { at ->
                        val root = placed.coordinates?.takeIf { it.isAttached }?.localToRoot(at) ?: return@detectPickUp
                        edit.beginResize(current, atStart, root)
                    },
                    onCarry = { change, _ ->
                        change.consume()
                        placed.coordinates?.takeIf { it.isAttached }?.localToRoot(change.position)?.let(edit::move)
                    },
                    onPutDown = { scope.putDown() },
                    onCancel = { edit.cancel() },
                )
            }
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(Modifier.width(GripWidth).height(GripHeight).background(grip, Theme.shapes.capsule))
        }
    }
}

private val HandleHeight = 10.dp
private val GripWidth = 24.dp
private val GripHeight = 4.dp

/**
 * Moves [event] by [minutes] — or only its end, when [resize] — as a screen
 * reader's action does it: straight to the callback, and read out.
 */
internal fun CalendarScope.moveBy(event: CalendarEvent, minutes: Long, resize: Boolean) {
    if (resize) {
        val end = maxOf(event.end.epochMinute + minutes, event.start.epochMinute + edit.stepMinutes)
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
