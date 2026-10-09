package io.kontour.ui.components.calendar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.arrowRing
import io.kontour.ui.components.datetime.drawEdgeArrow
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.lines

/**
 * The date range picker's arrows, at either side of the days while an event
 * is carried: asked for as *the little left/right arrows that show up when you
 * get close to the start/end of the month as you're dragging*.
 *
 * The same ring and chevron, drawn by the same code, filling over the dwell
 * that pages on — see `EditDwell`, which also gives it the picker's rumble —
 * so holding a carry at the side says what it is about to do.
 *
 * - **Level with the pointer**, following it up and down, against the days'
 *   outer edge: not in the edge day's middle, which in a day view is the
 *   middle of the screen. Under a finger, [Lift] above it instead: level with
 *   it, a finger at the edge covered it. A mouse's pointer covers nothing, and
 *   an arrow above it read as somewhere else.
 * - **Near the edge only**: fading in from [FadeFrom] of it to whole at the
 *   [DwellZone] where the dwell starts, the same distance in every view. By
 *   the day's width it came in from two and a half days away, which a day
 *   view's one wide day put anywhere on the screen.
 * - **One size**: a week's or a month's, whatever is carried. Sized by the day
 *   it was in, an all-day bar's 24dp row made it tiny.
 *
 * On a disc of the surface's colour, since unlike a date picker's day it can be
 * over the event being carried. Over the whole calendar and read in the draw
 * pass, so a carry recomposes nothing for it.
 */
@Composable
internal fun BoxScope.EditArrows(scope: CalendarScope) {
    val edit = scope.edit
    // Only where the days page sideways: a month scrolls up and down instead.
    val sideways = scope.state.view == CalendarView.Day || scope.state.view == CalendarView.Week
    val carrying = sideways && edit.kind == EditKind.Move && edit.pointer != null
    val shown by animateFloatAsState(if (carrying) 1f else 0f, Theme.motion.tweenFast(), label = "editArrows")
    if (!carrying && shown == 0f) return
    val placed = remember { PlacedAt() }
    val colours = Theme.colours
    val stroke = Theme.sizing.lines(1.5f)
    val ring = colours.outlineStrong
    val fill = colours.primary
    val chevron = colours.content
    val disc = colours.surface
    val touch = scope.touch
    Spacer(
        Modifier
            .matchParentSize()
            .onGloballyPositioned { placed.coordinates = it }
            .drawBehind {
                val pointer = edit.lastPointer ?: return@drawBehind
                val origin = placed.coordinates?.takeIf { it.isAttached }?.localToRoot(Offset.Zero) ?: return@drawBehind
                val shownDates = edit.shown() ?: return@drawBehind
                val places = if (edit.byDays) edit.days else edit.columns
                var left = Float.MAX_VALUE
                var right = -Float.MAX_VALUE
                for ((date, coordinates) in places.values) {
                    if (date !in shownDates || !coordinates.isAttached || coordinates.size.width <= 0) continue
                    val x = coordinates.localToRoot(Offset.Zero).x
                    left = minOf(left, x)
                    right = maxOf(right, x + coordinates.size.width)
                }
                if (left > right) return@drawBehind
                val half = ArrowHalfCell.toPx()
                val (out, radius) = arrowRing(half)
                val pad = radius * DiscShare
                val arrows = arrowPlaces(
                    left = left,
                    right = right,
                    pointer = pointer,
                    dwellSide = edit.dwellSide,
                    inset = pad + EdgeInset.toPx(),
                    lift = if (touch) Lift.toPx() else 0f,
                    fadeFrom = FadeFrom.toPx(),
                    full = DwellZone.toPx(),
                )
                for (place in arrows) {
                    val side = place.side
                    val held = edit.dwellSide == side
                    val alpha = place.presence * shown
                    if (alpha <= 0f) continue
                    val centre = place.centre - origin
                    drawCircle(disc, radius = pad, center = centre, alpha = alpha)
                    drawEdgeArrow(
                        // `drawEdgeArrow` puts the ring `out` from a day's middle toward its side.
                        day = Offset(centre.x - side * out, centre.y),
                        halfCell = half,
                        pointsLeft = side < 0,
                        progress = if (held) edit.dwellProgress else 0f,
                        alpha = alpha,
                        ring = ring,
                        fill = fill,
                        chevron = chevron,
                        stroke = stroke.toPx(),
                    )
                }
            },
    )
}

/** One side's arrow: which side, -1 the left; where its ring's centre is, in the root; and how much of it shows. */
internal class ArrowPlace(val side: Int, val centre: Offset, val presence: Float)

/**
 * Where the arrows go for days from [left] to [right] and a pointer at
 * [pointer], all in the root: against each outer edge, [inset] in, [lift]
 * above the pointer; showing from [fadeFrom] of that edge, whole by [full] of it, and
 * whole on the side [dwellSide] is held at.
 */
internal fun arrowPlaces(
    left: Float,
    right: Float,
    pointer: Offset,
    dwellSide: Int,
    inset: Float,
    fadeFrom: Float,
    full: Float,
    lift: Float = 0f,
): List<ArrowPlace> = listOf(-1, 1).map { side ->
    // How far in from that side the pointer is: past it is as near as can be.
    val distance = if (side < 0) pointer.x - left else right - pointer.x
    val presence = if (dwellSide == side) 1f else ((fadeFrom - distance) / (fadeFrom - full)).coerceIn(0f, 1f)
    ArrowPlace(side, Offset(if (side < 0) left + inset else right - inset, pointer.y - lift), presence)
}

/** The disc behind the ring, against the ring's radius. */
private const val DiscShare = 1.6f

/** Half the day the ring is sized by: a week's or a month's, whatever is carried. */
private val ArrowHalfCell = 24.dp

/** How far from the days' side the arrow starts to show; it is whole at the [DwellZone]. */
private val FadeFrom = 72.dp

/** Where the dwell starts, as `EditDwell` has it. */
private val DwellZone = 24.dp

/** How far above the pointer the arrow is: clear of the fingertip at the edge, and still beside it. */
private val Lift = 40.dp

/** The gap between the disc and the days' outer edge. */
private val EdgeInset = 2.dp
