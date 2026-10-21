package io.kontour.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.onPressOutside
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.Tooltip
import io.kontour.ui.overlay.TooltipStyle
import io.kontour.ui.overlay.TooltipDefaults
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay

/**
 * How a chart's choices are stepped through with the arrow keys: [Along] a row
 * of places, which stops at its ends, or [Round] a circle of parts, which goes
 * on round.
 */
internal enum class ChartSteps { Along, Round }

/**
 * Whether a chart's tooltip is out. Put away when the chart moves under it, by
 * a scroll or anything else, until the next thing is chosen or the same thing
 * is chosen again: a tooltip left where its place was is pointing at nothing.
 * A pointer's first choice waits [TooltipDefaults.HoverDelay] for it to rest,
 * as every other tooltip does; a touch's goes after
 * [TooltipDefaults.TouchDuration], as every other does.
 */
@Stable
internal class ChartTip {
    private var awayFrom by mutableStateOf<Any?>(NoChoice)
    var resting by mutableStateOf(false)

    /** Whether a finger or a pen is down on the chart, choosing. */
    var held by mutableStateOf(false)
    val placed = Placed()

    /** Whether the tooltip for [chosen] is out. */
    fun shownFor(chosen: Any?): Boolean = chosen != null && chosen != awayFrom && !resting

    /** Puts the tooltip for [chosen] away, until something else is chosen. */
    fun putAway(chosen: Any?) {
        awayFrom = chosen
    }

    /**
     * How many times a tap or a key has asked for the tooltip again: its time
     * on a touch screen starts again from each, though what is chosen is the
     * same.
     */
    var asked by mutableIntStateOf(0)
        private set

    /**
     * Brings the tooltip back for whatever is chosen, by a tap or a key that
     * chose it again. A tooltip timed out or put away stayed away until
     * something else was chosen, so a tap on the same place again did nothing:
     * the reader had to choose another place and come back. A resting mouse
     * does not ask: one still over its place has had its tooltip already.
     */
    fun bringBack() {
        awayFrom = NoChoice
        asked++
    }

    /** Nothing put away yet: unlike `null`, which is a chart with nothing chosen. */
    private object NoChoice
}

@Composable
internal fun rememberChartTip(): ChartTip {
    val tip = remember { ChartTip() }
    LaunchedEffect(tip.resting) {
        if (tip.resting) {
            delay(TooltipDefaults.HoverDelay)
            tip.resting = false
        }
    }
    return tip
}

/**
 * Everything a chart is chosen from, the same for a `Chart` and a
 * `SectorChart`: a tap, a hold then a drag any way, a resting mouse, and the
 * arrow keys, Home, End and Escape; a press anywhere else lets go, as a mouse
 * that rested on it does as it leaves; and moved, by a scroll, its tooltip goes. One place for them, so a gesture added to one
 * chart is not one the other lacks.
 *
 * @param chosen What is chosen now, as [index] of [count]; `null` for nothing.
 * @param dragsAcross Whether a plain drag across chooses too, without a hold
 *   first: a row of places is crossed that way, and a circle is not, where an
 *   upright start would be the page's scroll.
 * @param selectOnHover Whether a resting mouse chooses what it is over. Off,
 *   a mouse chooses by a click, as a finger does by a tap, and moving over the
 *   chart chooses nothing and lets go of nothing.
 * @param pickAt Chooses what is under a point, if anything, and says whether
 *   anything was: a pie's corners, outside its circle, have nothing.
 * @param pick Chooses the [index]th; `null` for a chart with nothing to
 *   choose, one that only marks ranges, whose arrows, Home and End are then
 *   left to what is round it.
 * @param brush How a range is marked, for a chart that marks one; see
 *   [ChartBrush].
 */
@Composable
internal fun Modifier.chartSelectionInput(
    enabled: Boolean,
    tip: ChartTip,
    chosen: Any?,
    index: Int?,
    count: Int,
    steps: ChartSteps,
    dragsAcross: Boolean,
    selectOnHover: Boolean,
    said: String?,
    interactionSource: MutableInteractionSource?,
    pickAt: (Offset) -> Boolean,
    pick: ((Int) -> Unit)?,
    clear: () -> Unit,
    brush: ChartBrush? = null,
    /**
     * Whether a range is marked now, read by the caller from its own value: not
     * from [ChartBrush.marked], which reads a state the chart writes afresh on
     * every composition, so that asking it here, in the same composition,
     * recomposed the chart for ever and no test with a range in it went idle.
     */
    marked: Boolean = false,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val latestBrush by rememberUpdatedState(brush)
    // Off, it takes nothing, and still says what is chosen and marked on it.
    if (!enabled) return if (said != null) semantics { stateDescription = said } else this
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The gestures are keyed on nothing that changes, and read these as they
    // are now. Keyed on the chart, a gesture was torn down whenever it was
    // resolved again, which a recomposition of the caller does: a selection
    // made during a drag recomposed it, and the drag stopped at its first step.
    val latestPickAt by rememberUpdatedState(pickAt)
    val latestClear by rememberUpdatedState(clear)
    val latestChosen by rememberUpdatedState(chosen)
    val latestSelectOnHover by rememberUpdatedState(selectOnHover)
    val touched: (Offset) -> Unit = { offset ->
        tip.resting = false
        latestPickAt(offset)
    }
    val latestTouched by rememberUpdatedState(touched)
    // Where a mouse's button went down on a chart that marks ranges, while it
    // is down: from there a drag is the range's. See the pointer's own loop.
    val mouse = remember { MousePress() }
    return this
        // A tap elsewhere lets go, of the chosen place and of a marked range,
        // as a tap elsewhere puts any tooltip away; it still presses whatever
        // it was aimed at. The range's was asked for 2026-10-21: *"if I have a
        // range marked on the graph and I click/tap elsewhere, it should
        // deselect"*.
        .onPressOutside(enabled = chosen != null || marked) {
            latestClear()
            latestBrush?.clear?.invoke()
        }
        .onGloballyPositioned { coordinates ->
            val now = coordinates.positionInWindow()
            val before = tip.placed.at
            tip.placed.at = now
            if (before != null && (now - before).getDistance() > tip.placed.slop) tip.putAway(latestChosen)
        }
        // A tap lets go of a range as well as choosing a place: it is what
        // a reader does to start again.
        .pointerInput(Unit) {
            detectTapGestures {
                latestBrush?.clear?.invoke()
                tip.bringBack()
                latestTouched(it)
            }
        }
        .then(
            if (dragsAcross) {
                Modifier.pointerInput(Unit) {
                    // Not a mouse's, on a chart that marks ranges: its drag is
                    // the range's from the press, and this one, starting
                    // sooner, moved the place under it until the range took
                    // over, so the first stretch of every range drew the
                    // selected place's line and tooltip under the pointer.
                    detectHorizontalDragGestures(onDragStart = { if (mouse.at == null) latestTouched(it) }) { change, _ ->
                        if (mouse.at != null) return@detectHorizontalDragGestures
                        change.consume()
                        latestTouched(change.position)
                    }
                }
            } else {
                Modifier
            },
        )
        // Held, then dragged any way: a drag that starts upright is the page's
        // scroll, so this is the one that can wander off the line. On a chart
        // that marks ranges it marks one: a plain drag across still moves the
        // selected place, which is what a finger does more often.
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { at -> latestBrush?.start?.invoke(at) ?: latestTouched(at) },
                onDragEnd = { latestBrush?.end?.invoke() },
                onDragCancel = { latestBrush?.end?.invoke() },
            ) { change, _ ->
                change.consume()
                val marking = latestBrush
                if (marking != null) marking.drag(change.position) else latestTouched(change.position)
            }
        }
        .pointerInput(Unit) {
            // Whether what is chosen was chosen by the mouse resting on it,
            // which it lets go of as it leaves.
            var hovering = false
            // A mouse marks a range by dragging: where the button went down,
            // in [mouse], and whether it has moved far enough from there to be
            // marking.
            var marking = false
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    // A finger or a pen on the chart: its tooltip's time does
                    // not start until it lifts. See `ChartTooltipProbe`.
                    if (change.type != PointerType.Mouse) {
                        tip.held = event.changes.any { it.pressed }
                        continue
                    }
                    when (event.type) {
                        PointerEventType.Press -> if (latestBrush != null && event.buttons.isPrimaryPressed) {
                            mouse.at = change.position
                        }
                        PointerEventType.Release -> {
                            if (marking) {
                                latestBrush?.end?.invoke()
                                change.consume()
                            }
                            marking = false
                            mouse.at = null
                        }
                        // Arriving as much as moving: the event a pointer
                        // crossing onto the chart brings is an enter.
                        PointerEventType.Enter, PointerEventType.Move -> {
                            val from = mouse.at
                            val ranges = latestBrush
                            if (from != null && ranges != null && change.pressed) {
                                if (!marking && (change.position - from).getDistance() > viewConfiguration.touchSlop) {
                                    marking = true
                                    ranges.start(from)
                                }
                                // Marking, the drag is the range's alone: not the
                                // place's under it, nor a drag across's.
                                if (marking) {
                                    ranges.drag(change.position)
                                    change.consume()
                                    continue
                                }
                            }
                            // A pointer that does not choose by hovering is only a
                            // pointer: it chooses with a click, as a finger does
                            // with a tap. It was the place under it whatever
                            // the chart said, so a chart that marks ranges drew
                            // its bar and its tooltip wherever a drag was about
                            // to start or had just ended: *"how do i disable the
                            // hover bar that comes up in charts? even if i turn
                            // tooltips off, it's still on"*.
                            //
                            // Nor while its button is down on a chart that marks
                            // ranges, short of the drag that marks one: it is a
                            // click on its way or the start of a drag, and the
                            // place under it moving with it fought the range.
                            if (!latestSelectOnHover) {
                                hovering = false
                                continue
                            }
                            if (from != null) continue
                            // A pointer arriving rests before the tooltip shows; one
                            // already showing moves with it.
                            if (latestChosen == null) tip.resting = true
                            hovering = true
                            // Off a pie's circle, though still in its box, is off
                            // the pie: what it chose lets go, and its tooltip
                            // fades. It stayed until the pointer left the box:
                            // *"the tooltip in the pie chart should fade out when
                            // you leave the circle, not the bounding box"*.
                            if (!latestPickAt(change.position) && latestChosen != null) {
                                tip.resting = false
                                latestClear()
                            }
                        }
                        // Gone off the chart, its tooltip goes with it, at once.
                        // It stayed until a scroll moved the chart under it:
                        // *"when you stop hovering, the tooltip should disappear.
                        // it shouldn't wait for you to scroll"*.
                        //
                        // Unless it has not gone off it, only under something
                        // laid over it: its own tooltip, which a pointer can
                        // reach before what it is over has moved the tooltip
                        // on. Let go there, the tooltip went, the pointer was
                        // on the chart again, and the tooltip came back after
                        // a rest: *"the tooltip sometimes disappears for a sec
                        // then comes back. it also happens if i drag/hover into
                        // the tooltip"*. Chosen where it is instead, which
                        // moves the tooltip on from under it.
                        PointerEventType.Exit -> if (hovering) {
                            val at = change.position
                            val covered = at.x >= 0f && at.y >= 0f && at.x < size.width && at.y < size.height
                            if (covered && latestPickAt(at)) continue
                            hovering = false
                            tip.resting = false
                            if (latestChosen != null) latestClear()
                        }
                        else -> Unit
                    }
                }
            }
        }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || count == 0) return@onKeyEvent false
            val at = index ?: -1
            val last = count - 1
            // Shift and an arrow across stretch a range a place at a time, on
            // a chart that marks one.
            val ranges = latestBrush
            if (ranges != null && event.isShiftPressed && steps == ChartSteps.Along) {
                val by = when (event.key) {
                    Key.DirectionRight -> if (rtl) -1 else 1
                    Key.DirectionLeft -> if (rtl) 1 else -1
                    else -> 0
                }
                if (by != 0) {
                    ranges.stretch(by)
                    return@onKeyEvent true
                }
            }
            val next = when (event.key) {
                // From nothing, the way across the reading goes starts at its
                // far end, Left left to right and Right right to left. Right
                // to left it started from the first place, as Right does
                // left to right.
                Key.DirectionRight -> if (rtl) (if (at < 0) last else at - 1) else at + 1
                Key.DirectionLeft -> if (rtl) at + 1 else if (at < 0) last else at - 1
                // Round a circle the way it is drawn, with Down on and Up back.
                Key.DirectionDown -> if (steps == ChartSteps.Round) at + 1 else return@onKeyEvent false
                Key.DirectionUp -> if (steps == ChartSteps.Round) (if (at < 0) last else at - 1) else return@onKeyEvent false
                Key.MoveHome -> 0
                Key.MoveEnd -> last
                Key.Escape -> {
                    val ranges = latestBrush
                    val marked = ranges != null && ranges.marked()
                    if (at < 0 && !marked) return@onKeyEvent false
                    // A range first, then the place: one thing let go a press.
                    if (marked) ranges.clear() else latestClear()
                    return@onKeyEvent true
                }
                else -> return@onKeyEvent false
            }
            // Nothing to choose, on a chart that only marks ranges: the keys
            // that move a choice are not the chart's, and go on to what is
            // round it. They were taken, and did nothing.
            val choose = pick ?: return@onKeyEvent false
            tip.resting = false
            tip.bringBack()
            choose(
                when (steps) {
                    ChartSteps.Along -> next.coerceIn(0, last)
                    ChartSteps.Round -> if (next < 0) last else next % count
                },
            )
            true
        }
        .focusRing(source, shape = Theme.shapes.small)
        .focusable(enabled = true, interactionSource = source)
        .semantics { if (said != null) stateDescription = said }
}

/** Where a mouse's button went down on a chart, while it is down; `null` while it is not. Not state: written and read by the gestures. */
private class MousePress {
    var at: Offset? = null
}

/**
 * How a chart marks a range: a drag that [start]s at a point and [drag]s to
 * another, its [end], a tap or Escape that [clear]s it, Shift and an arrow
 * that [stretch] it by a place either way, and whether anything is [marked].
 */
internal class ChartBrush(
    val start: (Offset) -> Unit,
    val drag: (Offset) -> Unit,
    val end: () -> Unit,
    val clear: () -> Unit,
    val stretch: (Int) -> Unit,
    val marked: () -> Boolean,
)

/**
 * A chart's tooltip, from a probe placed where the choice is: the caller's
 * [tooltip] for [shown], or [body]. A touch's goes after
 * [TooltipDefaults.TouchDuration], as every other tooltip does, and the choice
 * stays; counted from when the finger lifts, not from when it first chose.
 * Counted from then, a finger dragging along a chart for four seconds lost its
 * tooltip in the middle of the drag, until it reached the next place.
 *
 * A card, standing off the probe on [side]: beside a place, so that the
 * values it reads out are not covered by it, or above a pointer on a pie. It
 * was the inverse bubble a control's tooltip is, with its arrow, centred over
 * the place, and it sat on the bars and the line it was about.
 */
@Composable
internal fun <T : Any> ChartTooltipProbe(
    shown: ChartSelection<T>,
    tooltip: (@Composable ContentScope.(ChartSelection<T>) -> Unit)?,
    tip: ChartTip,
    chosen: Any?,
    side: OverlaySide = OverlaySide.End,
    alignment: OverlayAlignment = OverlayAlignment.Start,
    body: @Composable ContentScope.() -> Unit,
) {
    val touch = LocalInputModality.current.needsLargeTargets
    LaunchedEffect(chosen, tip.held, touch, tip.asked) {
        if (touch && !tip.held) {
            delay(TooltipDefaults.TouchDuration)
            tip.putAway(chosen)
        }
    }
    Box {
        Tooltip(
            visible = true,
            side = side,
            alignment = alignment,
            // Its own time is from when it first showed: this one keeps its own. See above.
            onDismissRequest = {},
            style = TooltipStyle.Card,
        ) {
            if (tooltip != null) tooltip(shown) else body()
        }
    }
}

/**
 * How visible a chart is as it first appears, and as its first data arrives
 * where it was [empty]: whole at once, unless motion is reduced, where it fades
 * in, because nothing moves into place.
 */
@Composable
internal fun rememberChartFade(animateData: Boolean, empty: Boolean = false): Animatable<Float, AnimationVector1D> {
    val motion = Theme.motion
    val fades = animateData && motion.reduceMotion
    val fade = remember { Animatable(if (fades) 0f else 1f) }
    LaunchedEffect(empty) {
        // Nothing to show yet: ready to fade in what comes.
        if (empty) {
            if (fades) fade.snapTo(0f)
        } else if (fade.value < 1f) {
            fade.animateTo(1f, motion.tweenFast())
        }
    }
    return fade
}

/**
 * Where a chart was last placed in the window, so a move under its tooltip is
 * seen. Not state: written in layout, read in layout.
 */
internal class Placed {
    var at: Offset? = null

    /** How far it may move before it has moved: past a pixel's rounding. */
    val slop = 2f
}
