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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.arrowRing
import io.kontour.ui.components.datetime.drawEdgeArrow
import io.kontour.ui.components.datetime.ringPresence
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.lines
import kotlin.math.abs

/**
 * The date range picker's arrows, at either side of the days while an event
 * is carried: asked for as *the little left/right arrows that show up when you
 * get close to the start/end of the month as you're dragging*.
 *
 * The same ring and chevron, drawn by the same code: in the day at the edge,
 * against its outer side, fading in as the pointer comes within a couple of
 * days of that side, and filling over the dwell that pages on — see
 * `EditDwell` — so holding a carry at the side says what it is about to do.
 * A little above the pointer rather than level with it, where the event being
 * carried is and a finger would cover it, and on a disc of the surface's
 * colour, since unlike a date picker's day it can be over an event. Over the
 * whole calendar and read in the draw pass, so a carry recomposes nothing for
 * it.
 */
@Composable
internal fun BoxScope.EditArrows(scope: CalendarScope) {
    val edit = scope.edit
    val carrying = edit.kind == EditKind.Move && edit.pointer != null
    val shown by animateFloatAsState(if (carrying) 1f else 0f, Theme.motion.tweenFast(), label = "editArrows")
    if (!carrying && shown == 0f) return
    val placed = remember { PlacedAt() }
    val colours = Theme.colours
    val stroke = Theme.sizing.lines(1.5f)
    val ring = colours.outlineStrong
    val fill = colours.primary
    val chevron = colours.content
    val disc = colours.surface
    Spacer(
        Modifier
            .matchParentSize()
            .onGloballyPositioned { placed.coordinates = it }
            .drawBehind {
                val pointer = edit.lastPointer ?: return@drawBehind
                val origin = placed.coordinates?.takeIf { it.isAttached }?.localToRoot(Offset.Zero) ?: return@drawBehind
                val shownDates = edit.shown() ?: return@drawBehind
                val places = if (edit.byDays) edit.days else edit.columns
                val boxes = places.values.mapNotNull { (date, coordinates) ->
                    if (date !in shownDates || !coordinates.isAttached || coordinates.size.width <= 0) null else coordinates.rootBox()
                }
                if (boxes.isEmpty()) return@drawBehind
                val left = boxes.minOf { it.left }
                val right = boxes.maxOf { it.right }
                for (side in intArrayOf(-1, 1)) {
                    val edge = if (side < 0) left else right
                    // The edge day in the pointer's row: the one at that side nearest it up and down.
                    val box = boxes
                        .filter { abs((if (side < 0) it.left else it.right) - edge) < 1f }
                        .minByOrNull { if (pointer.y < it.top) it.top - pointer.y else if (pointer.y > it.bottom) pointer.y - it.bottom else 0f }
                        ?: continue
                    val half = minOf(box.width, box.height, MaxCell.toPx()) / 2f
                    val held = edit.dwellSide == side
                    val near = if (held) 1f else ringPresence(abs(pointer.x - box.center.x) / box.width)
                    val alpha = near * shown
                    if (alpha <= 0f) continue
                    val (out, radius) = arrowRing(half)
                    val pad = radius * DiscShare
                    val y = (pointer.y - Lift.toPx()).coerceIn(box.top + pad, (box.bottom - pad).coerceAtLeast(box.top + pad))
                    val centre = Offset(box.center.x + (if (side < 0) -out else out), y) - origin
                    drawCircle(disc, radius = pad, center = centre, alpha = alpha)
                    drawEdgeArrow(
                        day = Offset(box.center.x, y) - origin,
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

private fun LayoutCoordinates.rootBox(): Rect {
    val at = localToRoot(Offset.Zero)
    return Rect(at.x, at.y, at.x + size.width, at.y + size.height)
}

/** How far above the pointer the arrow is: clear of a fingertip, and of the event under it. */
private val Lift = 36.dp

/** The disc behind the ring, against the ring's radius. */
private const val DiscShare = 1.6f

/** As big as a range picker's day at most: a day in the hours is a column, as tall as the day. */
private val MaxCell = 48.dp
