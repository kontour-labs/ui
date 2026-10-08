package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.interaction.SustainedFeedback
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * What a held finger, or a secondary click, offers on the calendar — asked for
 * as *"when you tap and hold, it should show a dropdown menu with an option to
 * create a new event, rather than just doing it"*, and an event's own menu to
 * adjust its time or delete it — the handles that "Adjust time" gives going
 * at a tap anywhere else, and a carry that pages on into the next week or month
 * rather than letting go.
 */
@OptIn(ExperimentalTestApi::class)
class EventCalendarMenuTest {

    private val wednesday = LocalDate(2026, 10, 14)
    private val review = CalendarEvent(1, "Design review", LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 11, 0))
    private val trip = CalendarEvent(3, "Perth trip", startDate = LocalDate(2026, 10, 20), endDate = LocalDate(2026, 10, 21))

    private class Probe {
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        var density = 1f
        val moves = mutableListOf<Triple<CalendarEvent, LocalDateTime, LocalDateTime>>()
        val resizes = mutableListOf<Triple<CalendarEvent, LocalDateTime, LocalDateTime>>()
        val creates = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        val deletes = mutableListOf<CalendarEvent>()
        val dates = mutableListOf<LocalDate>()
    }

    private fun ComposeUiTest.calendar(
        view: CalendarView = CalendarView.Week,
        modality: InputModality = InputModality.Touch,
        reduceMotion: Boolean = true,
        feedback: FeedbackDispatcher? = null,
        events: List<CalendarEvent> = listOf(review, trip),
        eventMenu: (@androidx.compose.runtime.Composable EventMenuScope.(CalendarEvent) -> Unit)? = EventCalendarDefaults.EventMenu,
        emptyMenu: (@androidx.compose.runtime.Composable EmptyMenuScope.(LocalDateTime, LocalDateTime) -> Unit)? = EventCalendarDefaults.EmptyMenu,
        moves: Boolean = true,
    ): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = reduceMotion) {
                CompositionLocalProvider(LocalInputModality provides modality, LocalFeedback provides (feedback ?: LocalFeedback.current)) {
                    OverlayHost(Modifier.size(900.dp, 900.dp)) {
                        probe.formats = Theme.formats
                        probe.strings = Theme.strings
                        probe.density = LocalDensity.current.density
                        EventCalendar(
                            state = rememberEventCalendarState(
                                initialDate = wednesday,
                                initialView = view,
                                initialScrollTime = LocalTime(8, 0),
                                firstDayOfWeek = DayOfWeek.MONDAY,
                            ),
                            events = events,
                            now = null,
                            onEventMove = if (moves) ({ event, start, end -> probe.moves += Triple(event, start, end) }) else null,
                            onEventResize = { event, start, end -> probe.resizes += Triple(event, start, end) },
                            onCreateRequest = { start, end -> probe.creates += start to end },
                            onEventDelete = { probe.deletes += it },
                            onDateClick = { probe.dates += it },
                            eventMenu = eventMenu,
                            emptyMenu = emptyMenu,
                            modifier = Modifier.size(900.dp, 900.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        return probe
    }

    private fun ComposeUiTest.boundsOf(event: CalendarEvent, probe: Probe): Rect =
        onNode(hasContentDescription(eventDescription(event, probe.formats, probe.strings))).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.dayWidth(probe: Probe): Float {
        fun heading(date: LocalDate) = onNode(hasContentDescription(probe.formats.date(date, DateStyle.Full)) and hasClickAction())
            .fetchSemanticsNode().boundsInRoot.center.x
        return heading(LocalDate(2026, 10, 15)) - heading(wednesday)
    }

    private fun hour(probe: Probe) = 64f * probe.density

    private fun ComposeUiTest.menuItem(label: String) = onNode(hasText(label) and hasClickAction())

    /** A finger held at [at] past a long press, and let go where it was. */
    private fun ComposeUiTest.hold(at: Offset) {
        onRoot().performTouchInput {
            down(at)
            advanceEventTime(800)
            up()
        }
        waitForIdle()
    }

    /** Monday at 11:00 in the hours, an hour under the review's start. */
    private fun ComposeUiTest.mondayEleven(probe: Probe): Offset {
        val review = boundsOf(review, probe)
        return Offset(review.center.x - dayWidth(probe) * 2f, review.top + hour(probe) + 2f)
    }

    @Test
    fun aFingerHeldOnEmptyTimeOffersANewEventAndMakesNothingUntilItIsChosen() = runComposeUiTest {
        val probe = calendar()
        hold(mondayEleven(probe))
        assertTrue(probe.creates.isEmpty(), "holding made an event without asking: ${probe.creates}")
        menuItem(probe.strings.newEvent).performClick()
        waitForIdle()
        assertEquals(listOf(LocalDateTime(2026, 10, 12, 11, 0) to LocalDateTime(2026, 10, 12, 12, 0)), probe.creates)
        menuItem(probe.strings.newEvent).assertDoesNotExist()
    }

    @Test
    fun aNewEventsMenuDismissedMakesNothing() = runComposeUiTest {
        val probe = calendar()
        hold(mondayEleven(probe))
        menuItem(probe.strings.newEvent).assertExists()
        // A tap away from it, on the header's title.
        onRoot().performTouchInput { down(Offset(8f, 8f)); up() }
        waitForIdle()
        menuItem(probe.strings.newEvent).assertDoesNotExist()
        assertTrue(probe.creates.isEmpty(), "a dismissed menu made its event: ${probe.creates}")
    }

    @Test
    fun aFingerHeldThenDraggedDrawsOutWithNoMenuLeftOpen() = runComposeUiTest {
        val probe = calendar()
        onRoot().performTouchInput {
            down(mondayEleven(probe))
            advanceEventTime(800)
            repeat(8) { moveBy(Offset(0f, hour(probe) / 4f)) }
            up()
        }
        waitForIdle()
        assertEquals(LocalDateTime(2026, 10, 12, 11, 0) to LocalDateTime(2026, 10, 12, 13, 0), probe.creates.single())
        menuItem(probe.strings.newEvent).assertDoesNotExist()
    }

    @Test
    fun aFingerHeldOnAnEventOffersItsTimeAndItsDeletion() = runComposeUiTest {
        val probe = calendar()
        hold(boundsOf(review, probe).center)
        menuItem(probe.strings.adjustTime).assertExists()
        menuItem(probe.strings.deleteEvent).performClick()
        waitForIdle()
        assertEquals(listOf(review), probe.deletes)
        assertTrue(probe.moves.isEmpty(), "a hold let go where it was moved the event")
    }

    @Test
    fun adjustTimeGivesTheEdgesHandlesUntilATapElsewhere() = runComposeUiTest {
        val probe = calendar()
        hold(boundsOf(review, probe).center)
        menuItem(probe.strings.adjustTime).performClick()
        waitForIdle()
        val bounds = boundsOf(review, probe)
        val edge = Offset(bounds.center.x, bounds.bottom - 3f * probe.density)
        // A handle: dragged at once, no hold.
        onRoot().performTouchInput {
            down(edge)
            repeat(6) { moveBy(Offset(0f, hour(probe) / 12f)) }
            up()
        }
        waitForIdle()
        assertEquals(LocalDateTime(2026, 10, 14, 11, 30), probe.resizes.single().third)
        // A tap on empty time: the handles go, and the same drag is a scroll of the hours.
        val tapAt = mondayEleven(probe)
        onRoot().performTouchInput { down(tapAt); up() }
        waitForIdle()
        onRoot().performTouchInput {
            down(edge)
            repeat(6) { moveBy(Offset(0f, hour(probe) / 12f)) }
            up()
        }
        waitForIdle()
        assertEquals(1, probe.resizes.size, "the handles were still there after a tap elsewhere")
    }

    /**
     * An event sharing its time with others, its times being adjusted, is
     * widened to the whole day, over the rest: in a week's lane it was squashed,
     * its handles and its title a third of a narrow column wide.
     */
    /**
     * Widened and narrowed where it can be seen, and not waited on: asked for
     * as *"when it becomes wider and goes back to being narrower, can you make
     * it animate?"* — in about 140ms it was over before it read as movement —
     * and then, at a third of a second, as *"too slow"*. A fifth of one.
     */
    @Test
    fun aTakenEventWidensAndNarrowsOverAVisibleEase() = runComposeUiTest {
        val standup = CalendarEvent(5, "Standup", LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 10, 45))
        val probe = calendar(events = listOf(review, standup), modality = InputModality.Mouse, reduceMotion = false)
        val day = dayWidth(probe)
        val narrow = boundsOf(review, probe).width
        val at = boundsOf(review, probe).center
        mainClock.autoAdvance = false
        // Taken up by a mouse, which carries at once: in focus from there.
        onRoot().performMouseInput {
            moveTo(at)
            press()
            moveBy(Offset(0f, 20f))
        }
        // How far from its lane toward the whole day, at each frame after it was taken.
        fun progress(from: Float, to: Float) = (boundsOf(review, probe).width - from) / (to - from)
        val wide = day - (day - narrow) * 0.02f
        val widening = List(40) { mainClock.advanceTimeByFrame(); progress(narrow, wide) }
        assertTrue(widening[0] < 0.6f && widening[3] < 0.98f, "it was wide at once: $widening")
        assertTrue(widening[17] > 0.95f, "it was still widening at 0.3s: $widening")
        val full = boundsOf(review, probe).width
        onRoot().performMouseInput { release() }
        val narrowing = List(40) { mainClock.advanceTimeByFrame(); progress(full, narrow) }
        assertTrue(narrowing[0] < 0.6f && narrowing[3] < 0.98f, "it was back in its lane at once: $narrowing")
        assertTrue(narrowing[17] > 0.98f, "it was still narrowing at 0.3s: $narrowing")
    }

    @Test
    fun anEventBeingAdjustedIsWidenedToItsWholeDay() = runComposeUiTest {
        val standup = CalendarEvent(5, "Standup", LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 10, 45))
        val probe = calendar(events = listOf(review, standup))
        val day = dayWidth(probe)
        val before = boundsOf(review, probe)
        assertTrue(before.width < day * 0.7f, "the review was not sharing its day to begin with: ${before.width} of $day")
        hold(before.center)
        menuItem(probe.strings.adjustTime).performClick()
        waitForIdle()
        val during = boundsOf(review, probe)
        assertTrue(during.width > day * 0.9f, "the review being adjusted is ${during.width}px wide in a ${day}px day")
        // A tap elsewhere: the handles go, and it goes back to its lane.
        onRoot().performTouchInput { click(Offset(during.center.x + day * 3f, during.center.y)) }
        waitForIdle()
        assertTrue(abs(boundsOf(review, probe).width - before.width) < 1f, "it stayed wide after the handles went")
    }

    /**
     * Opened out from the moment it is held, its menu open, not once "Adjust
     * time" is chosen from it: asked for as *"i'd like it to happen (and the
     * other events to grey out) when you select it, because that's already
     * when they jump to being full width"* — the copy a hold lifts stood at
     * the day's width at once, over an event still in its lane.
     */
    @Test
    fun aHeldEventIsWidenedToItsDayWhileItsMenuIsOpen() = runComposeUiTest {
        val standup = CalendarEvent(5, "Standup", LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 10, 45))
        val probe = calendar(events = listOf(review, standup))
        val day = dayWidth(probe)
        val before = boundsOf(review, probe)
        assertTrue(before.width < day * 0.7f, "the review was not sharing its day to begin with: ${before.width} of $day")
        hold(before.center)
        menuItem(probe.strings.adjustTime).assertExists()
        assertTrue(boundsOf(review, probe).width > day * 0.9f, "held, its menu open, the review is ${boundsOf(review, probe).width}px wide in a ${day}px day")
    }

    /**
     * Each event as tall as its time: asked for as *"i don't like how the
     * height of each hour box is different based on how many events are in
     * it… i should always be able to drag to 15/30/45 intervals, not just
     * 20/40"*. A quarter-hour was grown to fit its title, to about twenty
     * past, and its edge was on no quarter at all.
     */
    @Test
    fun eventsAreAsTallAsTheirTime() = runComposeUiTest {
        val quarter = CalendarEvent(6, "Check-in", LocalDateTime(2026, 10, 13, 9, 0), LocalDateTime(2026, 10, 13, 9, 15))
        val half = CalendarEvent(7, "Sync", LocalDateTime(2026, 10, 13, 11, 0), LocalDateTime(2026, 10, 13, 11, 30))
        val probe = calendar(events = listOf(review, quarter, half))
        val hour = boundsOf(review, probe).height
        assertTrue(abs(boundsOf(quarter, probe).height - (hour + 1f) / 4f) < 2f, "a quarter-hour is ${boundsOf(quarter, probe).height}px of a ${hour}px hour")
        assertTrue(abs(boundsOf(half, probe).height - (hour + 1f) / 2f) < 2f, "a half-hour is ${boundsOf(half, probe).height}px of a ${hour}px hour")
    }

    /** An event with its handles out, by touch: [event] held, then "Adjust time". */
    private fun ComposeUiTest.adjust(event: CalendarEvent, probe: Probe) {
        hold(boundsOf(event, probe).center)
        menuItem(probe.strings.adjustTime).performClick()
        waitForIdle()
    }

    /**
     * An edge taken from beside it, not only from the strip drawn on it:
     * asked for as *"you still have to hit it perfectly to start dragging it.
     * there needs to be a bit more give"*. Taken from 10dp above, it moves by
     * what the finger moves by, not to where the finger is.
     */
    @Test
    fun anEdgeIsTakenFromBesideIt() = runComposeUiTest {
        val probe = calendar()
        adjust(review, probe)
        val top = boundsOf(review, probe)
        onRoot().performTouchInput {
            down(Offset(top.center.x, top.top - 10f * probe.density))
            repeat(8) { moveBy(Offset(0f, -hour(probe) / 8f)) }
            up()
        }
        waitForIdle()
        val (_, start, end) = probe.resizes.singleOrNull() ?: error("the start was not taken: moves ${probe.moves}")
        assertEquals(LocalDateTime(2026, 10, 14, 9, 0), start)
        assertEquals(review.end, end)
    }

    /**
     * A short event's two edges, told apart by the way the finger sets off,
     * as a range slider's thumbs on top of each other are: asked for as
     * *"it's difficult to select the right handle with shorter events… it'd
     * be better if it did something like what the range slider did"*.
     */
    @Test
    fun aShortEventsEdgeIsTheOneTheFingerSetsOffToward() = runComposeUiTest {
        val quarter = CalendarEvent(6, "Check-in", LocalDateTime(2026, 10, 14, 13, 0), LocalDateTime(2026, 10, 14, 13, 15))
        val probe = calendar(events = listOf(quarter))
        adjust(quarter, probe)
        val middle = boundsOf(quarter, probe).center
        onRoot().performTouchInput {
            down(middle)
            repeat(8) { moveBy(Offset(0f, -hour(probe) / 8f)) }
            up()
        }
        waitForIdle()
        assertEquals(Triple(quarter, LocalDateTime(2026, 10, 14, 12, 0), quarter.end), probe.resizes.singleOrNull(), "up from its middle")
        probe.resizes.clear()
        onRoot().performTouchInput {
            down(middle)
            repeat(8) { moveBy(Offset(0f, hour(probe) / 8f)) }
            up()
        }
        waitForIdle()
        assertEquals(Triple(quarter, quarter.start, LocalDateTime(2026, 10, 14, 14, 15)), probe.resizes.singleOrNull(), "down from its middle")
    }

    /**
     * The press that takes a bar's handles away does only that: asked for as
     * *"when i'm adjusting times in the month view, and i click to deselect
     * it, it selects a day. it shouldn't do that until the next click"*.
     */
    @Test
    fun thePressThatTakesTheHandlesAwayIsNotAlsoATapOnTheDay() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month)
        val bar = boundsOf(trip, probe)
        hold(bar.center)
        menuItem(probe.strings.adjustTime).performClick()
        waitForIdle()
        // A day well away from the bar, in the week above it.
        val elsewhere = Offset(bar.left + 10f, bar.top - bar.height * 4f)
        onRoot().performTouchInput { click(elsewhere) }
        waitForIdle()
        assertEquals(emptyList(), probe.dates, "the press that took the handles away also pressed a day")
        onRoot().performTouchInput { click(elsewhere) }
        waitForIdle()
        assertEquals(1, probe.dates.size, "the next press on the day did not press it")
    }

    /**
     * Every hold and secondary click's menu is the app's to build: asked for as
     * *"i'd like to make it so all long-press/right-click actions are
     * configurable in the event calendar"*.
     */
    @Test
    fun anEventsMenuIsTheAppsToBuild() = runComposeUiTest {
        val shared = mutableListOf<CalendarEvent>()
        val probe = calendar(eventMenu = { event ->
            item("Share") { shared += event }
            delete()
        })
        hold(boundsOf(review, probe).center)
        menuItem(probe.strings.adjustTime).assertDoesNotExist()
        menuItem(probe.strings.deleteEvent).assertExists()
        menuItem("Share").performClick()
        waitForIdle()
        assertEquals(listOf(review), shared)
    }

    @Test
    fun anAppsMenuOpensOnAnEventThatCannotMove() = runComposeUiTest {
        val probe = calendar(moves = false, eventMenu = { item("Share") {} })
        hold(boundsOf(review, probe).center)
        menuItem("Share").assertExists()
    }

    @Test
    fun noEventMenuOpensNothing() = runComposeUiTest {
        val probe = calendar(eventMenu = null)
        hold(boundsOf(review, probe).center)
        menuItem(probe.strings.deleteEvent).assertDoesNotExist()
        menuItem(probe.strings.adjustTime).assertDoesNotExist()
    }

    @Test
    fun emptyTimesMenuIsTheAppsToBuildAndIsToldTheTime() = runComposeUiTest {
        val asked = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        val probe = calendar(emptyMenu = { start, end ->
            item("Block out") { asked += start to end }
            newEvent()
        })
        hold(mondayEleven(probe))
        menuItem(probe.strings.newEvent).assertExists()
        menuItem("Block out").performClick()
        waitForIdle()
        assertEquals(LocalDateTime(2026, 10, 12, 11, 0), asked.single().first)
        assertEquals(emptyList(), probe.creates, "a menu of the app's own made an event")
    }

    @Test
    fun aHeldEventDraggedAwayClosesItsMenuAndMoves() = runComposeUiTest {
        val probe = calendar()
        val from = boundsOf(review, probe).center
        onRoot().performTouchInput {
            down(from)
            advanceEventTime(800)
            repeat(8) { moveBy(Offset(0f, hour(probe) / 8f)) }
            up()
        }
        waitForIdle()
        assertEquals(LocalDateTime(2026, 10, 14, 11, 0), probe.moves.single().second)
        menuItem(probe.strings.deleteEvent).assertDoesNotExist()
    }

    @Test
    fun aSecondaryClickOnAnEventOpensItsMenu() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Mouse)
        onRoot().performMouseInput {
            moveTo(boundsOf(review, probe).center)
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
        }
        waitForIdle()
        // A mouse has its edges to drag already: only the deletion.
        menuItem(probe.strings.adjustTime).assertDoesNotExist()
        menuItem(probe.strings.deleteEvent).performClick()
        waitForIdle()
        assertEquals(listOf(review), probe.deletes)
    }

    @Test
    fun deleteFromTheKeyboardAndAScreenReadersAction() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Keyboard)
        val node = onNode(hasContentDescription(eventDescription(review, probe.formats, probe.strings)))
        val actions = node.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        runOnIdle { actions.first { it.label == probe.strings.deleteEvent }.action() }
        node.requestFocus()
        node.performKeyInput { pressKey(Key.Delete) }
        waitForIdle()
        assertEquals(listOf(review, review), probe.deletes)
    }

    @Test
    fun aCarryHeldAtTheWeeksEdgePagesOnAndLandsInTheNextWeek() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Mouse)
        val from = boundsOf(review, probe).center
        val edge = 900f * probe.density - 6f
        val width = dayWidth(probe)
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (edge - from.x) * (it + 1) / 10f, from.y)) }
        }
        // Held there: the next week. The clock by hand, since a carry held at
        // the edge goes on paging for as long as it is held there.
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        val nextMonday = probe.formats.date(LocalDate(2026, 10, 19), DateStyle.Full)
        onNode(hasContentDescription(nextMonday) and hasClickAction()).assertExists("holding at the edge did not page on")
        // And on, to its Wednesday, and let go.
        onRoot().performMouseInput {
            val at = currentPosition
            repeat(10) { moveTo(Offset(at.x - width * 4.5f * (it + 1) / 10f, at.y)) }
            release()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        val (event, start, end) = probe.moves.singleOrNull() ?: error("the carry let go when the week paged")
        assertEquals(review.id, event.id)
        assertEquals(LocalDate(2026, 10, 21), start.date)
        // At whatever time the pointer was level with: the next week has an all-day
        // row this one has not, and the hours are lower by it.
        assertEquals(60L, end.epochMinute - start.epochMinute, "the carry changed the event's length")
    }

    /**
     * Held at the side of the days, a carry is felt as a range picker's dwell
     * is: a rumble that builds while it is held, let go of as the page turns,
     * and the page felt once, as a threshold.
     */
    @Test
    fun aCarryHeldAtTheEdgeRumblesThenFeelsThePageTurn() = runComposeUiTest {
        val story = mutableListOf<String>()
        val feedback = object : FeedbackDispatcher {
            override fun perform(intent: FeedbackIntent) {
                story += intent.name
            }

            override fun sustain(intent: FeedbackIntent): SustainedFeedback {
                story += "held ${intent.name}"
                return object : SustainedFeedback {
                    override fun update(progress: Float) = Unit
                    override fun stop() {
                        story += "let go"
                    }
                }
            }
        }
        val probe = calendar(modality = InputModality.Mouse, feedback = feedback)
        val from = boundsOf(review, probe).center
        val edge = 900f * probe.density - 6f
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (edge - from.x) * (it + 1) / 10f, from.y)) }
        }
        mainClock.autoAdvance = false
        story.clear()
        // Past one dwell, short of a second.
        mainClock.advanceTimeBy(1_000)
        onRoot().performMouseInput { release() }
        mainClock.autoAdvance = true
        waitForIdle()
        val held = story.indexOf("held Hold")
        val letGo = story.indexOf("let go")
        val paged = story.indexOf(FeedbackIntent.DragThreshold.name)
        assertTrue(held >= 0, "no rumble while the carry was held at the edge: $story")
        assertTrue(letGo > held, "the rumble was not let go of: $story")
        assertTrue(paged > letGo, "the page turn was not felt after the rumble: $story")
        assertEquals(1, story.count { it == FeedbackIntent.DragThreshold.name }, "the page was felt more than once: $story")
    }

    /**
     * Held at the side of the days, a carry shows the date range picker's
     * arrow there, its ring filling over the dwell that pages on: what is
     * drawn beside the pointer changes as the hold goes on.
     */
    @Test
    fun aCarryHeldAtTheEdgeShowsTheArrowFilling() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Mouse)
        val from = boundsOf(review, probe).center
        val edge = 900f * probe.density - 6f
        val to = Offset(edge, from.y)
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (to.x - from.x) * (it + 1) / 10f, from.y)) }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(200)
        val early = onRoot().captureToImage().toPixelMap()
        mainClock.advanceTimeBy(350)
        val later = onRoot().captureToImage().toPixelMap()
        // The last day's column, a little either side of the pointer's height.
        val reach = 40f * probe.density
        var changed = 0
        for (x in (to.x - 60f * probe.density).toInt() until minOf(later.width, (to.x + 4f).toInt())) {
            for (y in (to.y - reach).toInt()..(to.y + reach).toInt()) {
                if (y !in 0 until later.height) continue
                if (early[x, y] != later[x, y]) changed++
            }
        }
        onRoot().performMouseInput { release() }
        mainClock.autoAdvance = true
        assertTrue(changed > 20, "nothing beside the pointer filled as the carry was held at the edge ($changed pixels changed)")
    }

    /**
     * Paged twice, the carry still follows: the days it looked for its own in
     * were the first page's, kept from when the calendar last composed, and at
     * the second page none of them were on show — the event stopped following
     * the finger.
     */
    @Test
    fun aCarryHeldLongEnoughToPageTwiceGoesOnFollowing() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Mouse)
        val from = boundsOf(review, probe).center
        val edge = 900f * probe.density - 6f
        val width = dayWidth(probe)
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (edge - from.x) * (it + 1) / 10f, from.y)) }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_700)
        val twoOn = probe.formats.date(LocalDate(2026, 10, 26), DateStyle.Full)
        onNode(hasContentDescription(twoOn) and hasClickAction()).assertExists("holding at the edge did not page on twice")
        onRoot().performMouseInput {
            val at = currentPosition
            repeat(10) { moveTo(Offset(at.x - width * 4.5f * (it + 1) / 10f, at.y)) }
            release()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        val (_, start, _) = probe.moves.singleOrNull() ?: error("the carry let go when the week paged")
        assertEquals(LocalDate(2026, 10, 28), start.date, "the carry stopped following the pointer after the second page")
    }

    /**
     * With the pager's slide: a pointer held still at the edge carries the
     * event into the week paged in, without having to move to be followed.
     */
    @Test
    fun withMotionAStillPointerIsFollowedIntoTheWeekPagedIn() = runComposeUiTest {
        val probe = calendar(modality = InputModality.Mouse, reduceMotion = false)
        val from = boundsOf(review, probe).center
        val edge = 900f * probe.density - 6f
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (edge - from.x) * (it + 1) / 10f, from.y)) }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_250)
        onRoot().performMouseInput { release() }
        mainClock.autoAdvance = true
        waitForIdle()
        val (_, start, _) = probe.moves.singleOrNull() ?: error("the carry let go when the week paged")
        assertTrue(
            start.date >= LocalDate(2026, 10, 19) && start.date <= LocalDate(2026, 10, 25),
            "a still pointer was left on the week before: $start",
        )
    }

    /** As [aMonthsBarCarriedToTheEdgeGoesOnIntoTheNextMonth], with the slide: two months show the same spare days while it runs. */
    @Test
    fun withMotionAMonthsBarGoesOnIntoTheNextMonth() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month, modality = InputModality.Mouse, reduceMotion = false)
        val bar = boundsOf(trip, probe)
        val cell = 900f * probe.density / DaysPerWeek
        val from = Offset(bar.left + cell / 2f, bar.center.y)
        val edge = 900f * probe.density - 6f
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (edge - from.x) * (it + 1) / 10f, from.y)) }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_250)
        onRoot().performMouseInput {
            val at = currentPosition
            repeat(10) { moveTo(Offset(at.x - cell * 3f * (it + 1) / 10f, at.y)) }
            release()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        val (_, start, _) = probe.moves.singleOrNull() ?: error("the carry let go when the month paged")
        assertEquals(kotlinx.datetime.Month.NOVEMBER, start.date.month, "the bar did not go on into November: $start")
    }

    @Test
    fun aMonthsBarCarriedToTheEdgeGoesOnIntoTheNextMonth() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month, modality = InputModality.Mouse)
        val bar = boundsOf(trip, probe)
        val cell = 900f * probe.density / DaysPerWeek
        val from = Offset(bar.left + cell / 2f, bar.center.y)
        val edge = 900f * probe.density - 6f
        onRoot().performMouseInput {
            moveTo(from)
            press()
            repeat(10) { moveTo(Offset(from.x + (edge - from.x) * (it + 1) / 10f, from.y)) }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        onRoot().performMouseInput {
            val at = currentPosition
            repeat(10) { moveTo(Offset(at.x - cell * 3f * (it + 1) / 10f, at.y)) }
            release()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        val (_, start, _) = probe.moves.singleOrNull() ?: error("the carry let go when the month paged")
        assertEquals(kotlinx.datetime.Month.NOVEMBER, start.date.month, "the bar did not go on into November: $start")
    }

    /** A bar held, "Adjust time", and its end dragged by a finger: a day longer. */
    @Test
    fun adjustTimeGivesABarsEndsHandlesThatGoByDays() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month)
        val bar = boundsOf(trip, probe)
        val cell = 900f * probe.density / DaysPerWeek
        hold(Offset(bar.left + cell / 2f, bar.center.y))
        menuItem(probe.strings.adjustTime).performClick()
        waitForIdle()
        val end = Offset(bar.right - 4f * probe.density, bar.center.y)
        onRoot().performTouchInput {
            down(end)
            repeat(10) { moveBy(Offset(cell / 10f, 0f)) }
            up()
        }
        waitForIdle()
        val (_, start, newEnd) = probe.resizes.singleOrNull() ?: error("the bar's handle did not resize it: moves ${probe.moves}")
        assertEquals(trip.start, start)
        assertEquals(trip.end.plusMinutes(MinutesPerDay.toLong()), newEnd)
    }

    @Test
    fun aFingerHeldJustBesideAMonthsLineTakesIt() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month)
        val bar = boundsOf(trip, probe)
        val cell = 900f * probe.density / DaysPerWeek
        // Under the bar, off it, as a fingertip lands on something under half its height.
        val beside = Offset(bar.left + cell / 2f, bar.bottom + 6f * probe.density)
        onRoot().performTouchInput {
            down(beside)
            advanceEventTime(800)
            repeat(8) { moveBy(Offset(cell / 8f, 0f)) }
            up()
        }
        waitForIdle()
        val (event, start, _) = probe.moves.singleOrNull() ?: error("a hold beside the bar did not take it")
        assertEquals(trip.id, event.id)
        assertEquals(trip.start.plusMinutes(MinutesPerDay.toLong()), start)
    }

    @Test
    fun aFingerHeldOnAnEmptyDayInTheMonthOffersANewDay() = runComposeUiTest {
        val probe = calendar(view = CalendarView.Month)
        // Tuesday the 27th, which has nothing on it: low in its box, away from its number.
        val day = onNode(hasContentDescription(probe.formats.date(LocalDate(2026, 10, 27), DateStyle.Full)))
            .fetchSemanticsNode().boundsInRoot
        hold(Offset(day.center.x, day.bottom - 8f * probe.density))
        menuItem(probe.strings.newEvent).performClick()
        waitForIdle()
        val (start, end) = probe.creates.single()
        assertEquals(LocalTime(0, 0), start.time)
        assertEquals(start.date.plusDaysForTest(1).atMidnightForTest(), end)
    }

    private fun LocalDate.plusDaysForTest(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)
    private fun LocalDate.atMidnightForTest(): LocalDateTime = LocalDateTime(year, month, day, 0, 0)
}
