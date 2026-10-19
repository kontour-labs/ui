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
import io.kontour.ui.overlay.Tooltip
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
 * a scroll or anything else, until the next thing is chosen: a tooltip left
 * where its place was is pointing at nothing. A pointer's first choice waits
 * [TooltipDefaults.HoverDelay] for it to rest, as every other tooltip does; a
 * touch's goes after [TooltipDefaults.TouchDuration], as every other does.
 */
@Stable
internal class ChartTip {
    private var awayFrom by mutableStateOf<Any?>(NoChoice)
    var resting by mutableStateOf(false)
    val placed = Placed()

    /** Whether the tooltip for [chosen] is out. */
    fun shownFor(chosen: Any?): Boolean = chosen != null && chosen != awayFrom && !resting

    /** Puts the tooltip for [chosen] away, until something else is chosen. */
    fun putAway(chosen: Any?) {
        awayFrom = chosen
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
 * arrow keys, Home, End and Escape; a press anywhere else lets go; and moved,
 * by a scroll, its tooltip goes. One place for them, so a gesture added to one
 * chart is not one the other lacks.
 *
 * @param chosen What is chosen now, as [index] of [count]; `null` for nothing.
 * @param dragsAcross Whether a plain drag across chooses too, without a hold
 *   first: a row of places is crossed that way, and a circle is not, where an
 *   upright start would be the page's scroll.
 * @param pickAt Chooses what is under a point, if anything.
 * @param pick Chooses the [index]th.
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
    said: String?,
    interactionSource: MutableInteractionSource?,
    pickAt: (Offset) -> Unit,
    pick: (Int) -> Unit,
    clear: () -> Unit,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    if (!enabled) return this
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The gestures are keyed on nothing that changes, and read these as they
    // are now. Keyed on the chart, a gesture was torn down whenever it was
    // resolved again, which a recomposition of the caller does: a selection
    // made during a drag recomposed it, and the drag stopped at its first step.
    val latestPickAt by rememberUpdatedState(pickAt)
    val latestClear by rememberUpdatedState(clear)
    val latestChosen by rememberUpdatedState(chosen)
    val touched: (Offset) -> Unit = { offset ->
        tip.resting = false
        latestPickAt(offset)
    }
    val latestTouched by rememberUpdatedState(touched)
    return this
        // A tap elsewhere lets go, as a tap elsewhere puts any tooltip away;
        // it still presses whatever it was aimed at.
        .onPressOutside(enabled = chosen != null) { latestClear() }
        .onGloballyPositioned { coordinates ->
            val now = coordinates.positionInWindow()
            val before = tip.placed.at
            tip.placed.at = now
            if (before != null && (now - before).getDistance() > tip.placed.slop) tip.putAway(latestChosen)
        }
        .pointerInput(Unit) { detectTapGestures { latestTouched(it) } }
        .then(
            if (dragsAcross) {
                Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragStart = { latestTouched(it) }) { change, _ ->
                        change.consume()
                        latestTouched(change.position)
                    }
                }
            } else {
                Modifier
            },
        )
        // Held, then dragged any way: a drag that starts upright is the page's
        // scroll, so this is the one that can wander off the line.
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(onDragStart = { latestTouched(it) }) { change, _ ->
                change.consume()
                latestTouched(change.position)
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    if (event.type == PointerEventType.Move && change.type == PointerType.Mouse) {
                        // A pointer arriving rests before the tooltip shows; one
                        // already showing moves with it.
                        if (latestChosen == null) tip.resting = true
                        latestPickAt(change.position)
                    }
                }
            }
        }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || count == 0) return@onKeyEvent false
            val at = index ?: -1
            val last = count - 1
            val next = when (event.key) {
                Key.DirectionRight -> if (rtl) at - 1 else at + 1
                Key.DirectionLeft -> if (rtl) at + 1 else if (at < 0) last else at - 1
                // Round a circle the way it is drawn, with Down on and Up back.
                Key.DirectionDown -> if (steps == ChartSteps.Round) at + 1 else return@onKeyEvent false
                Key.DirectionUp -> if (steps == ChartSteps.Round) (if (at < 0) last else at - 1) else return@onKeyEvent false
                Key.MoveHome -> 0
                Key.MoveEnd -> last
                Key.Escape -> {
                    if (at < 0) return@onKeyEvent false
                    latestClear()
                    return@onKeyEvent true
                }
                else -> return@onKeyEvent false
            }
            tip.resting = false
            pick(
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

/**
 * A chart's tooltip, from a one-pixel probe placed where the choice is: the
 * caller's [tooltip] for [shown], or [body]. A touch's goes after
 * [TooltipDefaults.TouchDuration], as every other tooltip does, and the choice
 * stays.
 */
@Composable
internal fun <T : Any> ChartTooltipProbe(
    shown: ChartSelection<T>,
    tooltip: (@Composable ContentScope.(ChartSelection<T>) -> Unit)?,
    onDismissRequest: () -> Unit,
    body: @Composable ContentScope.() -> Unit,
) {
    Box {
        Tooltip(visible = true, onDismissRequest = onDismissRequest) {
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
