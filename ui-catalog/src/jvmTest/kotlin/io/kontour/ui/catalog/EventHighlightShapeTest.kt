package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.calendar.CalendarEvent
import io.kontour.ui.components.calendar.CalendarView
import io.kontour.ui.components.calendar.EventCalendar
import io.kontour.ui.components.calendar.rememberEventCalendarState
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * An event's hover and press wash is the event's own shape.
 *
 * Reported 2026-10-21, for 0.5.0: "the hovering/tapping highlight/inset inside
 * the event in an event calendar is not the same shape as the event itself".
 * It was not. An event was pressed in `Theme.shapes.control`, a capsule capped
 * at 18dp, whatever it was drawn in: a block in the hours is `extraSmall`, so
 * its corners stood out past the wash's rounder ones, and a bar or a block cut
 * where it runs on into the next day or week is square at that end, where the
 * wash was still round. In the schedule the row's ground came before its press:
 * its edge was drawn over the wash, and the wash shrank with the press while
 * the row did not, so a press was a smaller box inside the event's.
 *
 * Measured the way `NavIndicationShapeTest` measures a highlight against what
 * it previews, by subtraction. Three renders of one calendar: without the
 * event, with it at rest, and with it pressed. *At rest minus without* is the
 * event's ground and nothing else; *pressed minus at rest* is the wash and
 * nothing else. The event draws no content of its own here, so both are its
 * shape alone. Under reduced motion, so the press does not shrink the event
 * and the difference is the wash, not the ground's edge moving in.
 *
 * **Compared as two sets, each firm against the other's faintest.** Ground
 * that is plainly ground, at half its difference in the event's middle and all
 * round it, has to be washed at all, at [Faint]; and wash that is plainly wash
 * has to be on the ground at all. Compared edge to edge, row by row, at one
 * threshold each, two identical shapes read as different ones: the wash is a
 * few percent over the ground, weaker still over its darker edge, and across
 * the top of a corner, where the edge is nearly level, a pixel's difference in
 * where an antialiased edge was read became five or ten across. Taken this way
 * an antialiased edge is neither, and a corner of the wrong shape is still
 * dozens of pixels.
 */
class EventHighlightShapeTest {

    /** Any difference at all: past what the same frame drawn twice differs by. */
    private val Faint = 3

    /**
     * Pixels allowed on the wrong side. With the wash in the event's shape the
     * most was 3, in the schedule; in `control`, the fewest was 67, a month's
     * bar at its one square end, and the schedule's was 3,599.
     */
    private val Stray = 20

    /** Wednesday, 14 October 2026, in a week from Monday the 12th. */
    private val day = LocalDate(2026, 10, 14)

    /** On the Monday, so the strip of all-day events is there with or without the event. */
    private val anchor = CalendarEvent("anchor", "Anchor", LocalDate(2026, 10, 12), LocalDate(2026, 10, 12))

    private fun at(date: LocalDate, hour: Int) = LocalDateTime(date, LocalTime(hour, 0))

    /** The largest difference between two pixels in any one channel. */
    private fun difference(p: Int, q: Int): Int {
        val dr = abs(((p shr 16) and 0xFF) - ((q shr 16) and 0xFF))
        val dg = abs(((p shr 8) and 0xFF) - ((q shr 8) and 0xFF))
        val db = abs((p and 0xFF) - (q and 0xFF))
        return maxOf(dr, dg, db)
    }

    /** The box round every pixel [hit] says yes to, as left, top, right, bottom; null for none. */
    private fun box(width: Int, height: Int, hit: (Int, Int) -> Boolean): IntArray? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (hit(x, y)) {
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right, bottom)
    }

    /**
     * Presses the piece of [event] that [piece] picks from where each piece
     * was put, and compares the wash with that piece's ground.
     */
    private fun assertWashIsTheEvent(
        what: String,
        view: CalendarView,
        event: CalendarEvent,
        direction: LayoutDirection = LayoutDirection.Ltr,
        contrast: ContrastLevel = ContrastLevel.Standard,
        reduceMotion: Boolean = true,
        piece: (List<Rect>) -> Rect = { it.first() },
    ) {
        val width = 1000
        val height = 1400
        val placed = mutableListOf<Rect>()
        fun render(events: List<CalendarEvent>, pressAt: Offset? = null): BufferedImage =
            Scene(width, height, reduceMotion = reduceMotion) {
                KontourTheme(darkTheme = false, contrast = contrast, reduceMotion = reduceMotion) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        Box(Modifier.fillMaxSize().background(Theme.colours.background)) {
                            EventCalendar(
                                state = rememberEventCalendarState(
                                    initialDate = day,
                                    initialView = view,
                                    initialScrollTime = LocalTime(0, 0),
                                    firstDayOfWeek = DayOfWeek.MONDAY,
                                ),
                                events = events,
                                modifier = Modifier.fillMaxSize(),
                                now = null,
                                onEventClick = {},
                                hourHeight = 24.dp,
                                eventMenu = null,
                                emptyMenu = null,
                                header = null,
                                // Nothing inside, so what is drawn is the shape alone.
                                eventContent = { shown, _ ->
                                    if (shown.id == event.id) Box(Modifier.fillMaxSize().reportBounds { placed += it })
                                },
                            )
                        }
                    }
                }
            }.use { scene ->
                scene.frames(6)
                if (pressAt != null) scene.press(pressAt)
                // Long enough for the wash to arrive on `tweenFast`; not so
                // long that the press becomes a hold. As many frames at rest,
                // so whatever else is still moving has moved as far in both.
                scene.frames(12)
            }

        val resting = render(listOf(anchor, event))
        // Without the event — or, in the schedule, which lays a day out only
        // where something is on, with it in another tone: every pixel of its
        // ground is another colour then, and nothing round it is.
        val without = if (view == CalendarView.Schedule) {
            render(listOf(anchor, CalendarEvent(event.id, event.title, event.start, event.end, tone = Tone.Danger)))
        } else {
            render(listOf(anchor))
        }
        val target = if (view == CalendarView.Schedule) {
            val ground = requireNotNull(box(width, height) { x, y -> difference(resting.getRGB(x, y), without.getRGB(x, y)) >= Faint }) {
                "$what: the event drew nothing"
            }
            Offset((ground[0] + ground[2]) / 2f, (ground[1] + ground[3]) / 2f)
        } else {
            require(placed.isNotEmpty()) { "$what: the event was never placed" }
            piece(placed.distinct()).center
        }
        val pressed = render(listOf(anchor, event), pressAt = target)

        // Firm: half of what each is in the middle of the event, where the press was.
        val tx = target.x.toInt()
        val ty = target.y.toInt()
        val firmGround = (difference(resting.getRGB(tx, ty), without.getRGB(tx, ty)) / 2).coerceAtLeast(Faint)
        val firmWash = (difference(resting.getRGB(tx, ty), pressed.getRGB(tx, ty)) / 2).coerceAtLeast(Faint)
        fun ground(x: Int, y: Int) = difference(resting.getRGB(x, y), without.getRGB(x, y))
        fun wash(x: Int, y: Int) = difference(resting.getRGB(x, y), pressed.getRGB(x, y))

        val washed = requireNotNull(box(width, height) { x, y -> wash(x, y) >= Faint }) { "$what: pressing the event drew nothing" }
        val (left, top, right, bottom) = washed.toList()

        // Looked for in the wash's box and a margin round it: wide enough to
        // see a ground that reaches past the wash, narrow enough to keep out
        // the event's other pieces and its neighbours.
        val margin = 8
        // Firm, and firm all round it: a pixel of an antialiased edge is
        // neither the shape nor not, and the two renders do not agree on it.
        fun firm(x: Int, y: Int, of: (Int, Int) -> Int, threshold: Int) =
            (-1..1).all { dy -> (-1..1).all { dx -> of((x + dx).coerceIn(0, width - 1), (y + dy).coerceIn(0, height - 1)) >= threshold } }
        val unwashed = mutableListOf<String>()
        val spilt = mutableListOf<String>()
        for (y in (top - margin).coerceAtLeast(0)..(bottom + margin).coerceAtMost(height - 1)) {
            for (x in (left - margin).coerceAtLeast(0)..(right + margin).coerceAtMost(width - 1)) {
                if (wash(x, y) < Faint && firm(x, y, ::ground, firmGround)) unwashed += "($x, $y)"
                if (ground(x, y) < Faint && firm(x, y, ::wash, firmWash)) spilt += "($x, $y)"
            }
        }
        assertTrue(
            unwashed.size <= Stray,
            "$what: ${unwashed.size} pixels of the event's ground are not under the press's wash " +
                "(wash ($left, $top)..($right, $bottom)), among them ${unwashed.take(12)}. " +
                "The wash is not the event's shape.",
        )
        assertTrue(
            spilt.size <= Stray,
            "$what: ${spilt.size} pixels of the press's wash are off the event's ground " +
                "(wash ($left, $top)..($right, $bottom)), among them ${spilt.take(12)}. " +
                "The wash is not the event's shape.",
        )
    }

    @Test
    fun aBlockInTheHoursIsWashedInItsOwnCorners() = assertWashIsTheEvent(
        "a block in the week",
        CalendarView.Week,
        CalendarEvent("block", "Block", at(day, 10), at(day, 12)),
    )

    @Test
    fun aBlockRunningPastMidnightIsWashedSquareAtItsFoot() = assertWashIsTheEvent(
        "a block's evening, running on into the next day",
        CalendarView.Week,
        CalendarEvent("late", "Late", at(LocalDate(2026, 10, 13), 22), at(day, 3)),
        // Tuesday's piece, the lower of the two: from 22:00 down to midnight.
        piece = { pieces -> pieces.maxBy { it.top } },
    )

    @Test
    fun aBlockRunOnFromYesterdayIsWashedSquareAtItsTop() = assertWashIsTheEvent(
        "a block's small hours, run on from the day before",
        CalendarView.Week,
        CalendarEvent("late", "Late", at(LocalDate(2026, 10, 13), 22), at(day, 3)),
        // Wednesday's piece, from midnight.
        piece = { pieces -> pieces.minBy { it.top } },
    )

    @Test
    fun anAllDayBarRunningIntoNextWeekIsWashedSquareAtItsEnd() = assertWashIsTheEvent(
        "an all-day bar running on past the week",
        CalendarView.Week,
        CalendarEvent("away", "Away", LocalDate(2026, 10, 17), LocalDate(2026, 10, 20)),
    )

    @Test
    fun rightToLeftTheCutEndIsSquareOnTheLeft() = assertWashIsTheEvent(
        "an all-day bar running on past the week, right to left",
        CalendarView.Week,
        CalendarEvent("away", "Away", LocalDate(2026, 10, 17), LocalDate(2026, 10, 20)),
        direction = LayoutDirection.Rtl,
    )

    @Test
    fun aMonthsBarCutAtTheWeekIsWashedSquareThere() = assertWashIsTheEvent(
        "a month's bar running on into the next week",
        CalendarView.Month,
        CalendarEvent("away", "Away", LocalDate(2026, 10, 16), LocalDate(2026, 10, 20)),
        // The week of the 12th's piece, the upper.
        piece = { pieces -> pieces.minBy { it.top } },
    )

    @Test
    fun aMonthsBarRunOnFromLastWeekIsWashedSquareAtItsStart() = assertWashIsTheEvent(
        "a month's bar run on from the week before, right to left",
        CalendarView.Month,
        CalendarEvent("away", "Away", LocalDate(2026, 10, 16), LocalDate(2026, 10, 20)),
        direction = LayoutDirection.Rtl,
        piece = { pieces -> pieces.maxBy { it.top } },
    )

    @Test
    fun atHighContrastTheWashIsStillTheEventsShape() {
        assertWashIsTheEvent(
            "a block in the week, at high contrast",
            CalendarView.Week,
            CalendarEvent("block", "Block", at(day, 10), at(day, 12)),
            contrast = ContrastLevel.High,
        )
        assertWashIsTheEvent(
            "an all-day bar running on past the week, at high contrast",
            CalendarView.Week,
            CalendarEvent("away", "Away", LocalDate(2026, 10, 17), LocalDate(2026, 10, 20)),
            contrast = ContrastLevel.High,
        )
    }

    @Test
    fun aScheduleRowIsWashedToItsEdges() = assertWashIsTheEvent(
        "an event in the schedule, pressed with motion on",
        CalendarView.Schedule,
        CalendarEvent("block", "Block", at(day, 10), at(day, 12)),
        // The shrink is what drew this one inside the row, so it is left on.
        reduceMotion = false,
    )
}
