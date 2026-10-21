package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.calendar.CalendarEvent
import io.kontour.ui.components.calendar.CalendarView
import io.kontour.ui.components.calendar.EventCalendar
import io.kontour.ui.components.calendar.rememberEventCalendarState
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * While an event is carried, or an edge of it dragged, a faint outline of it
 * is drawn where it would land if let go now: on the step, under the lifted
 * copy, which leans toward the pointer off it.
 *
 * Asked 2026-10-21, for 0.5.0: *"in the event calendar, can we show a faint
 * outline of the event where we're going to drop the event?"* The copy leans
 * toward the pointer by the detent's pull, half a step at most, and hops from
 * step to step, so where it is drawn is not quite where it lands: the outline
 * is.
 *
 * **Where it lands is found by the same drag under reduced motion,** where the
 * copy does not lean at all and so is drawn on the step exactly. The same drag
 * at full motion leans the copy off it, and the edge of the step the copy has
 * left uncovered has to have the outline on it — and the pixels just outside
 * that edge nothing that was not there before the drag. Compared with the
 * calendar at rest, so the grid's lines and the event left behind are not
 * mistaken for it; the bands are clear of both.
 */
class EventLandingOutlineTest {

    private val width = 1000
    private val height = 1400

    /** More than antialiasing or a faded fill: the outline's edge over the page is well past it. */
    private val Inked = 40

    /** Wednesday, 14 October 2026, in a week from Monday the 12th. */
    private val wednesday = LocalDate(2026, 10, 14)

    private fun at(date: LocalDate, hour: Int) = LocalDateTime(date, LocalTime(hour, 0))

    private class Case(
        val view: CalendarView,
        val events: List<CalendarEvent>,
        val direction: LayoutDirection = LayoutDirection.Ltr,
        val contrast: ContrastLevel = ContrastLevel.Standard,
        /** Where the pointer takes hold, from where each event was placed. */
        val grab: (Map<Any, Rect>) -> Offset,
        /** Where it is carried to, a stop at a time, each from where it took hold. */
        val stops: (Map<Any, Rect>, Offset) -> List<Offset>,
    )

    private class Stop(val frame: BufferedImage, val ghost: Rect)

    private class Outcome(
        val rest: BufferedImage,
        val pressed: BufferedImage,
        val stops: List<Stop>,
        val released: BufferedImage,
        val edge: Color,
    )

    /** The copy's key in what was placed: it is drawn with an id of the calendar's own. */
    private object Copy

    private fun perform(case: Case, still: Boolean): Outcome {
        val placed = HashMap<Any, Rect>()
        val ids = case.events.map { it.id }.toSet()
        var edge = Color.Unspecified
        return Scene(width, height, reduceMotion = still) {
            KontourTheme(darkTheme = false, contrast = case.contrast, reduceMotion = still) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides case.direction,
                    // A mouse's carry, taken up as it sets off rather than after a hold.
                    LocalInputModality provides InputModality.Mouse,
                ) {
                    Box(Modifier.fillMaxSize().background(Theme.colours.background)) {
                        edge = Theme.colours.danger.solid
                        EventCalendar(
                            state = rememberEventCalendarState(
                                initialDate = wednesday,
                                initialView = case.view,
                                initialScrollTime = LocalTime(8, 0),
                                firstDayOfWeek = DayOfWeek.MONDAY,
                            ),
                            events = case.events,
                            modifier = Modifier.fillMaxSize(),
                            now = null,
                            // Let go, nothing moves: the app keeps its events where they were.
                            onEventMove = { _, _, _ -> },
                            onEventResize = { _, _, _ -> },
                            hourHeight = 120.dp,
                            eventMenu = null,
                            emptyMenu = null,
                            header = null,
                            // Nothing inside, so what is drawn is the shapes alone.
                            eventContent = { shown, _ ->
                                Box(Modifier.fillMaxSize().reportBounds { placed[if (shown.id in ids) shown.id else Copy] = it })
                            },
                        )
                    }
                }
            }
        }.use { scene ->
            scene.advance(8)
            val rest = scene.frame()
            val from = case.grab(placed)
            val stops = case.stops(placed, from)
            scene.press(from)
            val pressed = scene.frame()
            var was = from
            val seen = stops.map { stop ->
                val steps = 12
                repeat(steps) { i ->
                    val t = (i + 1f) / steps
                    scene.move(Offset(was.x + (stop.x - was.x) * t, was.y + (stop.y - was.y) * t))
                    scene.advance()
                }
                was = stop
                // Past any hop, and the widening to the day.
                scene.advance(20)
                val frame = scene.frame()
                Stop(frame, placed[Copy] ?: fail("no copy was drawn while the event was carried"))
            }
            scene.release(was)
            scene.advance(20)
            Outcome(rest, pressed, seen, scene.frame(), edge)
        }
    }

    /** The largest difference between two pixels in any one channel. */
    private fun difference(p: Int, q: Int): Int {
        val dr = abs(((p shr 16) and 0xFF) - ((q shr 16) and 0xFF))
        val dg = abs(((p shr 8) and 0xFF) - ((q shr 8) and 0xFF))
        val db = abs((p and 0xFF) - (q and 0xFF))
        return maxOf(dr, dg, db)
    }

    /**
     * One band along an edge of [slot]: [inside] a pixel or two in from it,
     * where the outline's line is, or just outside it. Its pixels, a list per
     * place along the edge.
     */
    private class Band(val name: String, val points: List<List<Pair<Int, Int>>>)

    /**
     * The bands of [slot] that [ghost] has left uncovered: the edge across it
     * that the copy leans away from, and the one up and down it, where the
     * copy has gone far enough off it and the slot's side is straight there.
     */
    private fun bands(slot: Rect, ghost: Rect, inside: Boolean): List<Band> {
        val margin = (minOf(slot.width, slot.height) / 2f).coerceAtMost(24f).toInt() + 2
        val left = slot.left.toInt()
        val top = slot.top.toInt()
        val right = slot.right.toInt()
        val bottom = slot.bottom.toInt()
        val across = (left + margin)..(right - margin)
        val down = (top + margin)..(bottom - margin)
        val list = mutableListOf<Band>()
        val rowsTop = if (inside) listOf(top + 1, top + 2) else listOf(top - 4, top - 3)
        val rowsBottom = if (inside) listOf(bottom - 3, bottom - 2) else listOf(bottom + 2, bottom + 3)
        when {
            ghost.top >= slot.top + 4 -> list += Band("top", across.map { x -> rowsTop.map { x to it } })
            ghost.bottom <= slot.bottom - 4 -> list += Band("bottom", across.map { x -> rowsBottom.map { x to it } })
            else -> fail("the copy was drawn at $ghost, on its step at $slot: it did not lean off it, and the outline under it cannot be seen")
        }
        if (!down.isEmpty()) {
            val colsLeft = if (inside) listOf(left + 1, left + 2) else listOf(left - 4, left - 3)
            val colsRight = if (inside) listOf(right - 3, right - 2) else listOf(right + 2, right + 3)
            when {
                ghost.left >= slot.left + 4 -> list += Band("left", down.map { y -> colsLeft.map { it to y } })
                ghost.right <= slot.right - 4 -> list += Band("right", down.map { y -> colsRight.map { it to y } })
            }
        }
        return list
    }

    /** How much of [band] is inked in [frame] that was not in [rest]: a share of the places along it. */
    private fun inked(band: Band, frame: BufferedImage, rest: BufferedImage): Float {
        val hits = band.points.count { place ->
            place.any { (x, y) -> x in 0 until width && y in 0 until height && difference(frame.getRGB(x, y), rest.getRGB(x, y)) > Inked }
        }
        return hits.toFloat() / band.points.size.coerceAtLeast(1)
    }

    /** How much of [band] is [colour] itself, opaque: a share of the places along it. */
    private fun solid(band: Band, frame: BufferedImage, colour: Color): Float {
        val target = colour.toArgb()
        val hits = band.points.count { place ->
            place.any { (x, y) -> x in 0 until width && y in 0 until height && difference(frame.getRGB(x, y), target) <= 12 }
        }
        return hits.toFloat() / band.points.size.coerceAtLeast(1)
    }

    private fun assertOutlined(what: String, case: Case, strong: Boolean = false, leaves: Boolean = true) {
        val landed = perform(case, still = true)
        val carried = perform(case, still = false)
        val slots = landed.stops.map { it.ghost }
        for ((i, stop) in carried.stops.withIndex()) {
            val slot = slots[i]
            val inside = bands(slot, stop.ghost, inside = true)
            val outside = bands(slot, stop.ghost, inside = false)
            for ((band, beyond) in inside.zip(outside)) {
                val on = inked(band, stop.frame, carried.rest)
                val off = inked(beyond, stop.frame, carried.rest)
                assertTrue(
                    on >= 0.3f,
                    "$what, stop ${i + 1}: along the ${band.name} of where it would land ($slot), only ${(on * 100).toInt()}% " +
                        "of the edge is drawn on, with the copy leaning off it to ${stop.ghost}. No outline where it would land.",
                )
                assertTrue(
                    off <= 0.05f,
                    "$what, stop ${i + 1}: just outside the ${band.name} of where it would land ($slot), " +
                        "${(off * 100).toInt()}% is drawn on: the outline is not on the step.",
                )
                val opaque = solid(band, stop.frame, carried.edge)
                if (strong) {
                    assertTrue(opaque >= 0.3f, "$what, stop ${i + 1}: at high contrast the outline's ${band.name} is the event's colour at full strength along only ${(opaque * 100).toInt()}% of it")
                } else {
                    assertTrue(opaque <= 0.05f, "$what, stop ${i + 1}: the outline's ${band.name} is the event's colour at full strength along ${(opaque * 100).toInt()}% of it: not faint")
                }
            }
            // Not where it would have landed a stop before.
            if (leaves && i > 0) {
                for (band in bands(slots[i - 1], carried.stops[i - 1].ghost, inside = true)) {
                    val left = inked(band, stop.frame, carried.rest)
                    assertTrue(left <= 0.05f, "$what, stop ${i + 1}: the outline is still on the ${band.name} of the step before (${slots[i - 1]}), ${(left * 100).toInt()}% of it")
                }
            }
        }
        // Not before the carry sets off, and gone once it is let go.
        for ((i, slot) in slots.withIndex()) {
            for (band in bands(slot, carried.stops[i].ghost, inside = true)) {
                val before = inked(band, carried.pressed, carried.rest)
                val after = inked(band, carried.released, carried.rest)
                assertTrue(before <= 0.05f, "$what: pressed and not yet moved, ${(before * 100).toInt()}% of the ${band.name} of stop ${i + 1}'s step is drawn on")
                assertTrue(after <= 0.05f, "$what: let go, ${(after * 100).toInt()}% of the ${band.name} of stop ${i + 1}'s step is still drawn on")
            }
        }
    }

    private val review = CalendarEvent("review", "Review", at(wednesday, 9), at(wednesday, 10), tone = Tone.Danger)

    /**
     * Carried through the hours: an hour later on Thursday, leaning a little
     * further down and across; then two hours later on Saturday.
     */
    private fun weekMove(contrast: ContrastLevel = ContrastLevel.Standard) = Case(
        view = CalendarView.Week,
        events = listOf(review),
        contrast = contrast,
        // In its middle: within 20dp of an edge, a finger takes the edge.
        grab = { placed -> placed.getValue("review").center },
        stops = { placed, from ->
            val event = placed.getValue("review")
            // Alone in its day, the event is the day's width less the gap.
            val column = event.width + 2f
            val hour = event.height + 2f
            listOf(
                // Six minutes past the hour: on the hour's step, leaning toward the pointer.
                from + Offset(column * 1.3f, hour + hour / 10f),
                from + Offset(column * 3.3f, hour * 2f + hour / 10f),
            )
        },
    )

    @Test
    fun aCarryThroughTheHoursIsOutlinedWhereItWouldLand() = assertOutlined("an event carried through the week", weekMove())

    @Test
    fun atHighContrastTheOutlineIsTheEventsColourAtFullStrength() =
        assertOutlined("an event carried through the week, at high contrast", weekMove(ContrastLevel.High), strong = true)

    @Test
    fun anEndDraggedIsOutlinedWhereItWouldEnd() = assertOutlined(
        "an event's end dragged later",
        Case(
            view = CalendarView.Week,
            events = listOf(review),
            // On the end's handle, just inside the event.
            grab = { placed -> placed.getValue("review").let { Offset(it.center.x, it.bottom - 3f) } },
            stops = { placed, from ->
                val hour = placed.getValue("review").height + 2f
                listOf(
                    // Three minutes short of the next hour: the end on it, the copy's foot above it.
                    from + Offset(0f, hour - hour / 20f),
                    from + Offset(0f, hour * 2f - hour / 20f),
                )
            },
        ),
        // The step before is inside the one after, and under the copy.
        leaves = false,
    )

    private val trip = CalendarEvent("trip", "Trip", LocalDate(2026, 10, 6), LocalDate(2026, 10, 6), tone = Tone.Danger)

    /** Two days on and two weeks down from [trip]: how wide a day is and how tall a week, in the month. */
    private val ruler = CalendarEvent("ruler", "Ruler", LocalDate(2026, 10, 22), LocalDate(2026, 10, 22), tone = Tone.Info)

    @Test
    fun aCarryAcrossTheMonthIsOutlinedWhereItWouldLand() = assertOutlined(
        "a bar carried across the month",
        Case(
            view = CalendarView.Month,
            events = listOf(trip, ruler),
            grab = { placed -> placed.getValue("trip").center },
            stops = { placed, from ->
                val bar = placed.getValue("trip")
                val far = placed.getValue("ruler")
                val day = (far.left - bar.left) / 2f
                val week = (far.top - bar.top) / 2f
                listOf(
                    // Thursday the 8th, a little further on and down.
                    from + Offset(day * 2.25f, week * 0.12f),
                    // Thursday the 15th, the week after.
                    from + Offset(day * 2.25f, week * 1.12f),
                )
            },
        ),
    )

    private val away = CalendarEvent("away", "Away", LocalDate(2026, 10, 13), LocalDate(2026, 10, 13), tone = Tone.Danger)

    @Test
    fun rightToLeftACarryAlongTheAllDayRowIsOutlinedWhereItWouldLand() = assertOutlined(
        "a bar carried along the week's all-day row, right to left",
        Case(
            view = CalendarView.Week,
            events = listOf(away),
            direction = LayoutDirection.Rtl,
            grab = { placed -> placed.getValue("away").center },
            stops = { placed, from ->
                val day = placed.getValue("away").width + 4f
                listOf(
                    // Two days on, to the left, and a little lower.
                    from + Offset(-day * 2.25f, 18f),
                    from + Offset(-day * 4.25f, 18f),
                )
            },
        ),
    )
}
