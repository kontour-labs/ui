package io.kontour.ui.components.calendar

import io.kontour.ui.foundation.fillPattern
import io.kontour.ui.theme.clipProxy
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.layout.layout
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
 * at an hour's 64dp is 16dp, and growing it would cover the next one. A screen
 * reader reaches every event as its own node, and a keyboard by Tab.
 *
 * @param byDays Whether a move carries it by whole days — a bar, or a line in a
 *   month — rather than through the hours.
 * @param ghost Where an edit would put it: drawn lifted, and not a control.
 * @param blockHeight How tall a block is drawn, where the column knows as it
 *   composes: for the default content to fit its title to.
 * @param focusableOn The days the keyboard reaches it on, by Tab from the grid
 *   it is in: only while the grid's cursor is on one of them. Null, always.
 * @param contentWidth How wide, in pixels, what is inside is laid out, where
 *   that is not the box's width: the width a block opening out or closing back
 *   is on its way to, read as it is measured. Laid out at the width of each
 *   frame instead, its title was wrapped again every frame of the ease, and
 *   jumped about. Null: the box's width.
 * @param contentAt Where what is inside is placed along the box, in pixels
 *   from its start, given how wide it came out: a copy reaching on past the
 *   days on show keeps its title where the event's own is, and a bar's title
 *   stays where it is on the screen as the pages slide under it. Read as it
 *   is placed, so a swipe's every frame places it again and composes nothing.
 *   Null: at the box's start.
 * @param stacked Whether it is laid over another event: ringed in the
 *   surface's colour, so where it starts over the one under it reads as two.
 * @param listedOn The day whose list of events it is in, not on the days: a
 *   month's day list. Carried from there it goes by days from that day, wherever
 *   the list is over, keeps no line of a week, and has no ends to drag — the
 *   list's day is not where it starts or stops.
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
    contentWidth: (() -> Int)? = null,
    contentAt: ((width: Int) -> Int)? = null,
    stacked: Boolean = false,
    listedOn: LocalDate? = null,
) {
    scope.state.eventCompositions++
    val colours = toneColours(event.tone)
    // A block is a box of time, cornered like the library's other small boxes;
    // a bar and a line are labels across days, and capsules as chips are —
    // square at an end that is not the event's own: see [cutShape].
    val shape = cutShape(
        if (presentation == EventPresentation.Block) Theme.shapes.extraSmall else Theme.shapes.control,
        presentation,
        clippedStart,
        clippedEnd,
    )
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
    val ring = Theme.colours.surface
    val ringWidth = Theme.sizing.borderWidthStrong
    val edit = scope.edit
    // The event as it was, while an edit carries it: there, faded, so where it
    // came from stays in view.
    val carried = !ghost && edit.live && edit.event?.id == event.id
    // While one event in the hours is held, carried or adjusted, the rest
    // step back: faded, so it is the one thing to look at.
    val focused = edit.focus
    val target = when {
        carried -> CarriedAlpha
        !ghost && focused != null && focused != event.id -> UnfocusedAlpha
        else -> 1f
    }
    val fade = remember { Animatable(target) }
    val spec = Theme.motion.tweenDefault<Float>()
    LaunchedEffect(target) {
        // Put down, it takes the place of the copy that was over it at once:
        // faded back up over a fifth of a second, widened over the events
        // beside it, it let them show through it as it came back.
        if (fade.targetValue == CarriedAlpha && target == 1f) fade.snapTo(target) else fade.animateTo(target, spec)
    }

    Box(
        modifier
            .then(if (ghost) Modifier.clearAndSetSemantics { } else Modifier.eventControl(event, scope, byDays, presentation, focusableOn, listedOn)),
    ) {
        // Its ground and what is on it cut to its shape; its handles not, so
        // a block's grips can sit across its edges, clear of its title.
        //
        // Faded each on its own, not all of it at once: a fade draws into a
        // layer as big as what it is on, and the grips past the edge were cut
        // off until the event, put down, had faded back up.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = fade.value }
                .then(if (stacked) Modifier.drawBehind { drawRing(shape, ring, ringWidth.toPx()) } else Modifier)
                .clip(shape.clipProxy())
                .background(fill)
                // Its pattern on its ground, under its words, in a wash of its
                // ink: on a pale block or a solid bar alike, the hatching is
                // there and the title over it still reads.
                .then(
                    if (presentation == EventPresentation.Line) {
                        Modifier
                    } else {
                        Modifier.fillPattern(event.pattern, ink.copy(alpha = EventPatternInk))
                    }
                )
                .then(if (edge != null) Modifier.border(edge, shape) else Modifier),
            // A block's text from its top, as time runs; a bar's and a line's level
            // in its row, as a chip's label is.
            contentAlignment = if (presentation == EventPresentation.Block) Alignment.TopStart else Alignment.CenterStart,
        ) {
            Box(
                if (contentWidth == null && contentAt == null) {
                    Modifier
                } else {
                    Modifier.layout { measurable, constraints ->
                        val width = contentWidth?.invoke()?.coerceAtLeast(0) ?: constraints.maxWidth
                        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = width))
                        layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(contentAt?.invoke(placeable.width) ?: 0, 0) }
                    }
                },
            ) {
                CompositionLocalProvider(LocalBlockHeight provides blockHeight) {
                    ProvideContentColour(ink) { scope.eventContent(event, presentation) }
                }
            }
        }
        // A block's edges, in its hours; a bar's ends, by whole days. Not on
        // an end cut off by the edge of the week or the page: it is not the
        // event's end there.
        val edges = presentation != EventPresentation.Line
        if (!ghost && listedOn == null && edges && scope.resizable(event) && (!scope.touch || edit.active == event.id)) {
            val bar = presentation == EventPresentation.Bar
            if (!clippedStart) ResizeHandle(event, scope, atStart = true, bar = bar) { fade.value }
            if (!clippedEnd) ResizeHandle(event, scope, atStart = false, bar = bar) { fade.value }
        }
    }
}

/**
 * An event's shape, square at an end the view cuts it off at rather than one it
 * ends at — a bar running on into the week before or after, or off the page; a
 * block running on past midnight — so a rounded end always means the event ends
 * there, and a square one that it carries on.
 *
 * A bar's ends are its start and end sides, mirrored right to left with its
 * corners; a block's, its top and its foot. A line has no ground to shape.
 */
internal fun cutShape(base: CornerBasedShape, presentation: EventPresentation, clippedStart: Boolean, clippedEnd: Boolean): CornerBasedShape {
    if (presentation == EventPresentation.Line || (!clippedStart && !clippedEnd)) return base
    val square = CornerSize(0)
    return if (presentation == EventPresentation.Block) {
        base.copy(
            topStart = if (clippedStart) square else base.topStart,
            topEnd = if (clippedStart) square else base.topEnd,
            bottomEnd = if (clippedEnd) square else base.bottomEnd,
            bottomStart = if (clippedEnd) square else base.bottomStart,
        )
    } else {
        base.copy(
            topStart = if (clippedStart) square else base.topStart,
            bottomStart = if (clippedStart) square else base.bottomStart,
            topEnd = if (clippedEnd) square else base.topEnd,
            bottomEnd = if (clippedEnd) square else base.bottomEnd,
        )
    }
}

/**
 * A bar across days [from] to [to] of a row of [cell]-wide days: in from each
 * end by [gap], except at an end it runs on past — into the day or the week
 * before or after, or off the page — where it runs to the edge, with no gap, so
 * it meets the piece on the other side of the line and reads as one event.
 * Square there too: see [cutShape].
 */
internal fun Modifier.barAcross(cell: Dp, from: Int, to: Int, continuesBefore: Boolean, continuesAfter: Boolean, gap: Dp): Modifier {
    val before = if (continuesBefore) 0.dp else gap
    val after = if (continuesAfter) 0.dp else gap
    return padding(start = cell * from + before).width(cell * (to - from + 1) - before - after)
}

/** A ring [width] wide just outside [shape], round what this draws. */
private fun DrawScope.drawRing(shape: Shape, colour: Color, width: Float) {
    val outline = shape.createOutline(Size(size.width + width, size.height + width), layoutDirection, this)
    translate(-width / 2, -width / 2) { drawOutline(outline, colour, style = Stroke(width)) }
}

/** How faded the event left behind is while an edit carries it. */
private const val CarriedAlpha = 0.4f

/** How faded the other events are while one is in focus: see `EventEdit.focus`. */
private const val UnfocusedAlpha = 0.35f

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
    listedOn: LocalDate?,
): Modifier {
    val listed = listedOn != null
    // Whether it has edges to adjust, and whether they go by whole days: not
    // in a day's list, whose day is not where it starts or stops.
    val edges = presentation != EventPresentation.Line && !listed
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
        // A step earlier or later for one in the hours; an all-day event moves by days alone.
        if (movable && !event.allDay && !byDays) {
            add(CustomAccessibilityAction(scope.strings.moveEarlier) { scope.moveBy(current, -step, resize = false); true })
            add(CustomAccessibilityAction(scope.strings.moveLater) { scope.moveBy(current, step, resize = false); true })
        }
        if (movable) {
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

    // Held for its menu alone, where it cannot be carried: an app's own menu
    // opens on any event.
    val offers = scope.touch && scope.hasMenu(event, edges = edges)
    val holdable = movable || offers
    if (holdable) {
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
            // Told again on every frame a scroll moves it, with the same
            // coordinates: written only where something has changed.
            if (movable && edit.draggables[placed] !== coordinates) edit.draggables[placed] = coordinates
            // Empty time under it steps aside for its hold.
            if (holdable) {
                val known = edit.events[placed]
                if (known == null || known.coordinates !== coordinates || known.event != current || known.byDays != byDays || known.edges != edges) {
                    edit.events[placed] = Grab(coordinates, current, byDays, edges = edges)
                }
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
            if (movable || resizable) {
                // Picked up from the keyboard and left — Tab away, a click
                // elsewhere — it is put back, not left carried with nothing to
                // carry it: the pages would not turn and its copy stayed.
                Modifier.onFocusChanged { state ->
                    if (!state.isFocused && edit.live && edit.pointer == null && edit.menu == null && edit.event?.id == current.id) edit.cancel()
                }
            } else {
                Modifier
            },
        )
        .then(
            if (holdable) {
                Modifier.notesPress(press).pointerInput(edit, event.id, byDays, movable) {
                    var taking: PointerId? = null
                    detectPickUp(
                        axis = PickUpAxis.Free,
                        immediate = { !isTouch && movable },
                        // A mouse has nothing to take up here: its menu is a secondary click.
                        accepts = { isTouch || movable },
                        // A scroll of the hours, or a swipe of the pages, is not a hold.
                        givesWay = true,
                        onPointer = { taking = it },
                        onPickUp = { at ->
                            val coordinates = placed.coordinates?.takeIf { it.isAttached } ?: return@detectPickUp
                            val root = coordinates.localToRoot(press.position ?: at)
                            if (!movable) {
                                edit.pickedUp()
                                scope.openMenu(root, current, edges = edges)
                                return@detectPickUp
                            }
                            scope.takeUp(current, root, byDays, top = if (listed) null else coordinates.localToRoot(Offset.Zero).y, pointer = taking, hold = isTouch, edges = edges, day = listedOn)
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
                            edit.beginKeyboard(current, byDays)
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
                            } else if (movable && !byDays) {
                                edit.nudge(by)
                            } else if (movable && scope.state.view == CalendarView.Month) {
                                // By days, up and down are a week in a month, as
                                // the grid's cursor goes and a carry down its
                                // weeks does. In the all-day strip a carry has
                                // no up or down, and neither do they.
                                val week = DaysPerWeek * MinutesPerDay.toLong()
                                edit.nudge(if (by > 0) week else -week)
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
private fun BoxScope.ResizeHandle(event: CalendarEvent, scope: CalendarScope, atStart: Boolean, bar: Boolean, alpha: () -> Float) {
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
                    // Across the edge, half outside: within a quarter-hour's
                    // 16dp, two grips inside it covered its title.
                    Modifier
                        .align(if (atStart) Alignment.TopCenter else Alignment.BottomCenter)
                        .offset(y = if (atStart) -HandleHeight / 2 else HandleHeight / 2)
                        .fillMaxWidth()
                        .height(HandleHeight)
                },
            )
            .onGloballyPositioned { coordinates ->
                placed.coordinates = coordinates
                // Written only where something has changed, as the event's own are.
                if (edit.draggables[placed] !== coordinates) edit.draggables[placed] = coordinates
                val known = edit.events[placed]
                if (known == null || known.coordinates !== coordinates || known.event != current || known.byDays != bar || known.atStart != atStart) {
                    edit.events[placed] = Grab(coordinates, current, byDays = bar, edge = true, atStart = atStart)
                }
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
            Box(size.graphicsLayer { this.alpha = alpha() }.background(colour, Theme.shapes.capsule))
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
        announce(event.title, event.start, newEnd, event.allDay)
    } else {
        val length = event.end.epochMinute - event.start.epochMinute
        // Earlier or later by a step keeps to its day; a day either way is a day.
        val from = if (kotlin.math.abs(minutes) < MinutesPerDay) {
            movedStart(event.start.epochMinute + minutes, length, event.start.date.atMidnight.epochMinute, onOneDay(event.start, event.end), edit.stepMinutes)
        } else {
            event.start.epochMinute + minutes
        }
        if (from == event.start.epochMinute) return
        val start = localDateTimeOfEpochMinute(from)
        val end = localDateTimeOfEpochMinute(from + length)
        onEventMove?.invoke(event, start, end)
        announce(event.title, start, end, event.allDay)
    }
}

/**
 * An event as a screen reader says it: its title, its day, its time or that
 * it is all day, and where — a list, in the reader's own way of writing one.
 */
internal fun eventDescription(event: CalendarEvent, formats: LocaleFormats, strings: Strings): String =
    formats.list(
        listOf(event.title) + eventWhen(event.start, event.end, event.allDay, formats, strings) + listOfNotNull(event.location),
        ListStyle.Unit,
    )

/**
 * When an event from [start] to [end] is, as a screen reader is told it: its
 * day, or its first and last days, and its times or that it is [allDay].
 * Shared by what an event is called and what is read out once it has been
 * moved, so the two say the same thing.
 */
internal fun eventWhen(start: LocalDateTime, end: LocalDateTime, allDay: Boolean, formats: LocaleFormats, strings: Strings): List<String> {
    val lastDay = if (end > start) end.plusMinutes(-1).date else start.date
    val days = if (lastDay == start.date) {
        formats.date(start.date, DateStyle.Full)
    } else {
        strings.timeRange(formats.date(start.date, DateStyle.Full), formats.date(lastDay, DateStyle.Full))
    }
    val time = if (allDay) strings.allDay else spokenTimes(start, end, formats, strings)
    return listOf(days, time)
}

/**
 * How many days on from [start]'s the day [end] is on, for the "+1" after an
 * end time on a later day, as flights and shifts write it: "22:30 – 06:15⁺¹".
 *
 * An end at the midnight that closes the first day is that day's end, not
 * the next's — 0, and "18:00 – 00:00" — as the calendar has it everywhere
 * else: the event is drawn on that day, listed under it and read out as on
 * it alone, and a "+1" would point at a day it has no part of. A later
 * midnight is its own date's, so the number is still the day the end is on:
 * from nine on Monday to midnight going into Wednesday is "09:00 – 00:00⁺²".
 */
internal fun daysOn(start: LocalDateTime, end: LocalDateTime): Int {
    val days = (end.date.toEpochDays() - start.date.toEpochDays()).toInt()
    return if (days == 1 && end.time == Midnight) 0 else days.coerceAtLeast(0)
}

/**
 * An event's times as a line shows them, "09:00 – 10:30", and for one that
 * ends on a later day the end raised by how many: "09:00 – 17:00⁺²". The
 * "+2" is the line's own type, smaller and lifted — not a superscript
 * character, which some faces lack and others draw at a size of their own —
 * straight after the end time wherever a translation puts it.
 */
internal fun eventTimes(start: LocalDateTime, end: LocalDateTime, formats: LocaleFormats, strings: Strings): AnnotatedString {
    val to = formats.time(end.time)
    val range = strings.timeRange(formats.time(start.time), to)
    val days = daysOn(start, end)
    if (days == 0) return AnnotatedString(range)
    val found = range.lastIndexOf(to)
    val at = if (found < 0) range.length else found + to.length
    return buildAnnotatedString {
        append(range, 0, at)
        withStyle(DayOffsetStyle) { append(strings.endDayOffset(formats.quantity(days))) }
        append(range, at, range.length)
    }
}

/**
 * The same times as a screen reader is told them: the later day in words,
 * "09:00 – 17:00, 2 days later", where it would read the "+2" as "plus two",
 * and where the times alone sounded like nine to five on each day.
 */
internal fun spokenTimes(start: LocalDateTime, end: LocalDateTime, formats: LocaleFormats, strings: Strings): String {
    val to = formats.time(end.time)
    val days = daysOn(start, end)
    return strings.timeRange(formats.time(start.time), if (days == 0) to else strings.endDaysLater(to, formats.quantity(days)))
}

/** [this], which is somewhere in [whole], with its styles where it is there: a time's raised "+1", in its line. */
internal fun AnnotatedString.placedIn(whole: String): AnnotatedString {
    val at = whole.indexOf(text)
    if (at < 0 || spanStyles.isEmpty()) return AnnotatedString(whole)
    return buildAnnotatedString {
        append(whole)
        for (span in spanStyles) addStyle(span.item, span.start + at, span.end + at)
    }
}

/**
 * The raised days after an end time, against the line's type: about
 * two-thirds its size, as a superscript is set, its baseline lifted by half
 * its own size so it sits level with the top of the figures beside it.
 */
private val DayOffsetStyle = SpanStyle(fontSize = 0.7.em, baselineShift = BaselineShift.Superscript)

private val Midnight = LocalTime(0, 0)

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
 * felt and given its menu. [day] is the day it is taken from, where that is not
 * the day under [root]: a day's list, open over other days.
 */
internal fun CalendarScope.takeUp(
    event: CalendarEvent,
    root: Offset,
    byDays: Boolean,
    top: Float?,
    pointer: PointerId?,
    hold: Boolean,
    edges: Boolean = !byDays,
    day: LocalDate? = null,
) {
    if (byDays) edit.beginMoveByDays(event, root, top, day) else edit.beginMove(event, root)
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

/** How strongly an event's pattern is drawn in its ink: enough to see, not enough to cross out its title. */
private const val EventPatternInk = 0.22f
