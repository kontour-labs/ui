package io.kontour.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import io.kontour.ui.theme.toneColours
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * An event taken up keeps the shape it had where it was: square, and out to
 * the edge, at an end it runs on past — into the day or the week either side,
 * or past midnight — and round only where it really ends. And carried
 * sideways, the rest of it comes with it: *"if we drag it one way, it needs to
 * look like the rest of the event is attached to the bar as we move it"*.
 *
 * The copy a carry draws was a capsule cut to the days on show, in from both
 * ends by the gap between events whatever the event did there; leant toward
 * the finger, it came away from the side it ran on past.
 *
 * Found by the copy's colour, its tone at full strength and opaque: the event
 * left behind is the same colour, faded.
 */
@OptIn(ExperimentalTestApi::class)
class CarriedBarShapeTest {

    private class Probe {
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        var solid = Color.Unspecified
    }

    private fun ComposeUiTest.calendar(view: CalendarView, events: List<CalendarEvent>, scrollTo: LocalTime = LocalTime(8, 0)): Probe {
        val probe = Probe()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                KontourTheme(darkTheme = false, reduceMotion = false) {
                    CompositionLocalProvider(LocalInputModality provides InputModality.Mouse) {
                        OverlayHost(Modifier.size(700.dp, 600.dp).background(Color.White)) {
                            probe.formats = Theme.formats
                            probe.strings = Theme.strings
                            probe.solid = toneColours(Tone.Danger).solid
                            EventCalendar(
                                state = rememberEventCalendarState(LocalDate(2026, 10, 14), view, scrollTo, DayOfWeek.MONDAY),
                                events = events,
                                now = null,
                                header = null,
                                onEventMove = { _, _, _ -> },
                                modifier = Modifier.size(700.dp, 600.dp),
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        return probe
    }

    private fun ComposeUiTest.pieces(probe: Probe, event: CalendarEvent): List<Rect> =
        onAllNodes(hasContentDescription(eventDescription(event, probe.formats, probe.strings)))
            .fetchSemanticsNodes().map { it.boundsInRoot }

    /** Taken up at [at] with a mouse, and the pointer brought back to where it took hold: the copy leans nowhere. */
    private fun ComposeUiTest.takeUp(at: Offset) {
        onRoot().performMouseInput {
            moveTo(at)
            press()
            repeat(4) { moveBy(Offset(5f, 0f)) }
            moveBy(Offset(-20f, 0f))
        }
        waitForIdle()
    }

    private fun PixelMap.isSolid(x: Float, y: Float, solid: Color): Boolean {
        val c = this[x.toInt(), y.toInt()]
        return c.alpha > 0.95f && abs(c.red - solid.red) < 0.05f && abs(c.green - solid.green) < 0.05f && abs(c.blue - solid.blue) < 0.05f
    }

    private fun ComposeUiTest.image(): PixelMap = onRoot().captureToImage().toPixelMap()

    /** Tuesday to Thursday, seen on the Wednesday in between: it runs on past both sides of the day. */
    private val conference = CalendarEvent(1, "Conference", startDate = LocalDate(2026, 10, 13), endDate = LocalDate(2026, 10, 15), tone = Tone.Danger)

    @Test
    fun aBarTakenUpInADayKeepsItsSquareEndsOutToTheEdges() = runComposeUiTest {
        val probe = calendar(CalendarView.Day, listOf(conference))
        val bar = pieces(probe, conference).single()
        takeUp(bar.center)
        val image = image()
        val corners = listOf(
            "top start" to Offset(bar.left + 1f, bar.top + 1f),
            "foot start" to Offset(bar.left + 1f, bar.bottom - 2f),
            "top end" to Offset(bar.right - 2f, bar.top + 1f),
            "foot end" to Offset(bar.right - 2f, bar.bottom - 2f),
        )
        val missing = corners.filterNot { (_, at) -> image.isSolid(at.x, at.y, probe.solid) }.map { it.first }
        assertTrue(missing.isEmpty(), "the copy of a bar that runs on past both sides of the day is not square and out to the edge at its $missing corners")
        onRoot().performMouseInput { release() }
    }

    @Test
    fun aBarCarriedSidewaysInADayBringsTheRestOfItselfWithIt() = runComposeUiTest {
        val probe = calendar(CalendarView.Day, listOf(conference))
        val bar = pieces(probe, conference).single()
        val y = bar.center.y
        // Leant toward the start, well clear of the side a carry pages at.
        onRoot().performMouseInput {
            moveTo(bar.center)
            press()
            repeat(10) { moveBy(Offset(-bar.width * 0.03f, 0f)) }
        }
        waitForIdle()
        val toStart = image()
        assertTrue(toStart.isSolid(bar.right - 2f, y, probe.solid), "carried toward the start, the copy came away from the end of the day, where the event runs on into the next")
        assertTrue(toStart.isSolid(bar.left + 1f, y, probe.solid), "carried toward the start, the copy left the start of the day")
        // And toward the end, past where it took hold.
        onRoot().performMouseInput { repeat(20) { moveBy(Offset(bar.width * 0.03f, 0f)) } }
        waitForIdle()
        val toEnd = image()
        assertTrue(toEnd.isSolid(bar.left + 1f, y, probe.solid), "carried toward the end, the copy came away from the start of the day, where the event runs on from the day before")
        assertTrue(toEnd.isSolid(bar.right - 2f, y, probe.solid), "carried toward the end, the copy left the end of the day")
        onRoot().performMouseInput { release() }
    }

    @Test
    fun aMonthsBarTakenUpKeepsItsSquareEndsAtTheWeeksItRunsBetween() = runComposeUiTest {
        // Saturday to Tuesday: the end of one week's row and the start of the next.
        val trip = CalendarEvent(2, "Perth trip", startDate = LocalDate(2026, 10, 10), endDate = LocalDate(2026, 10, 13), tone = Tone.Danger)
        val probe = calendar(CalendarView.Month, listOf(trip))
        val (first, second) = pieces(probe, trip).sortedBy { it.top }
        takeUp(first.center)
        val image = image()
        assertTrue(
            image.isSolid(first.right - 2f, first.top + 1f, probe.solid) && image.isSolid(first.right - 2f, first.bottom - 2f, probe.solid),
            "the copy's piece in the first week is not square and out to the week's end, where the event runs on into the next",
        )
        assertTrue(
            image.isSolid(second.left + 1f, second.top + 1f, probe.solid) && image.isSolid(second.left + 1f, second.bottom - 2f, probe.solid),
            "the copy's piece in the second week is not square and out to the week's start, where the event runs on from the last",
        )
        onRoot().performMouseInput { release() }
    }

    @Test
    fun anEventInTheHoursTakenUpKeepsItsSquareFootAtMidnight() = runComposeUiTest {
        // Tuesday ten at night to two on Wednesday: two pieces, the first square at its foot.
        val flight = CalendarEvent(3, "Night flight", LocalDateTime(2026, 10, 13, 22, 0), LocalDateTime(2026, 10, 14, 2, 0), tone = Tone.Danger)
        val probe = calendar(CalendarView.Week, listOf(flight), scrollTo = LocalTime(20, 0))
        val evening = pieces(probe, flight).maxBy { it.top }
        takeUp(evening.center)
        // Opened out to its day by now, as the event under it is.
        val opened = pieces(probe, flight).maxBy { it.top }
        val image = image()
        // A block's copy is its tone's ground inside its tone's edge: the edge,
        // there at the very corners. Midnight is the foot of the hours' frame,
        // whose own line is over the last pixel.
        assertTrue(
            image.isSolid(opened.left + 1f, opened.bottom - 2f, probe.solid) && image.isSolid(opened.right - 2f, opened.bottom - 2f, probe.solid),
            "the copy of an event running on past midnight is not square and down to midnight at its foot",
        )
        onRoot().performMouseInput { release() }
    }
}
