package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowSizeClass
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.locale.HourCycle
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A date and time picker changes the day without the time, and the time
 * without the day, and says once that each change has settled; it shows the
 * calendar and the wheels side by side wherever the two fit across, and the
 * calendar with a row for the time under it where they do not — and never the
 * wheels under the calendar.
 *
 * Asked for as *"can we add a component for a date and time picker? it should
 * basically just be the two individual components we currently have for that,
 * combined"*, and then *"the two things need to be either side-by-side, or have
 * a segmented control that allows the user to swap between them"*: stacked,
 * the two were taller than a phone. Side by side they sit together, a gap
 * apart: *"on wide screens, there's too much room between the date picker and
 * time picker"*, where the wheels had been centred in whatever width the
 * calendar left.
 *
 * Then *"on larger phones, i should be able to see the 'inline' variant of the
 * date-time picker"*, which was answered by stacking the two again where a
 * phone had the height, and that was not it: *"the date/time picker should
 * never be vertical like that. it takes up way too much vertical room"*. Inline
 * is side by side, on any width the calendar and the wheels really fit across.
 *
 * And then the segmented control went too: on a 12-hour clock no phone had the
 * width for both halves, so no phone ever showed the date and the time at once.
 * Narrower than side by side is iOS's inline picker instead — the calendar, and
 * under it a row saying the time, whose pill opens the wheels in a popover.
 */
@OptIn(ExperimentalTestApi::class)
class DateTimePickerTest {

    /**
     * The picker's value and the room it is given: [width] across and [height]
     * high, or at a null height, in a page that scrolls on a large phone's
     * window. A null [cycle] is the theme's clock. [drawn] is how tall the
     * picker came out, in dp.
     */
    private class Picked(start: LocalDateTime, width: Int, height: Int?, cycle: HourCycle?) {
        var value by mutableStateOf(start)
        val changes = mutableListOf<LocalDateTime>()
        var finished = 0
        var width by mutableStateOf(width)
        var height by mutableStateOf(height)
        var cycle by mutableStateOf(cycle)
        var drawn = 0f
        var alone = 0f
    }

    /**
     * A picker [width] wide, given [height] — a small phone's room by default —
     * or, at null, in a page that scrolls; in an overlay host, as an app has.
     * [modality] is the input the reader is using, the theme's by default.
     */
    private fun ComposeUiTest.picker(
        width: Int = 360,
        height: Int? = 480,
        cycle: HourCycle? = null,
        rtl: Boolean = false,
        fontScale: Float = 1f,
        modality: InputModality? = null,
        withCalendarAlone: Boolean = false,
    ): Picked {
        val picked = Picked(LocalDateTime(LocalDate(2026, 6, 18), LocalTime(8, 15)), width, height, cycle)
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                KontourTheme(reduceMotion = true) {
                    val theme = Theme.formats
                    val formats = picked.cycle?.let { theme.withConventions(theme.conventions.copy(hourCycle = it)) } ?: theme
                    val picker = @Composable { modifier: Modifier ->
                        val scale = LocalDensity.current.density
                        DateTimePicker(
                            value = picked.value,
                            onValueChange = {
                                picked.value = it
                                picked.changes += it
                            },
                            onValueChangeFinished = { picked.finished++ },
                            minuteStep = 5,
                            formats = formats,
                            modifier = modifier.width(picked.width.dp).onSizeChanged { picked.drawn = it.height / scale },
                        )
                    }
                    val page = @Composable {
                        OverlayHost(Modifier.fillMaxSize()) {
                            val given = picked.height
                            if (given != null) {
                                picker(Modifier.height(given.dp))
                            } else {
                                // A large phone's window, provided as the theme would
                                // read it: tall enough for anything.
                                CompositionLocalProvider(LocalWindowSizeClass provides WindowSizeClass.of(430.dp, 932.dp)) {
                                    Column(Modifier.verticalScroll(rememberScrollState())) {
                                        picker(Modifier)
                                        // A calendar alone at the same width, to measure the picker by.
                                        if (withCalendarAlone) {
                                            val scale = LocalDensity.current.density
                                            DatePicker(
                                                value = LocalDate(2026, 6, 18),
                                                onValueChange = {},
                                                modifier = Modifier.width(picked.width.dp).onSizeChanged { picked.alone = it.height / scale },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (modality == null) page() else CompositionLocalProvider(LocalInputModality provides modality) { page() }
                }
            }
        }
        waitForIdle()
        return picked
    }

    /**
     * The calendar as it shows, from days no wheel row near 8:15 shares a
     * number with: June 2026's 1st to 4th are its first four columns and the
     * 26th to 28th its last three, and the 29th is in its last week. Null when
     * the calendar is not showing.
     */
    private fun ComposeUiTest.calendar(): Rect? {
        val days = listOf(1, 2, 3, 4, 26, 27, 28, 29).map { day ->
            onAllNodesWithText("$day").fetchSemanticsNodes().firstOrNull()?.boundsInRoot ?: return null
        }
        return Rect(days.minOf { it.left }, days.minOf { it.top }, days.maxOf { it.right }, days.maxOf { it.bottom })
    }

    /** The minute wheel, where it shows. */
    private fun ComposeUiTest.minuteWheel(): Rect? =
        onAllNodes(hasContentDescription("Minute")).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    /** The pill under the calendar that says the time and opens the wheels: a button named "Time". */
    private val isThePill = hasContentDescription("Time") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun ComposeUiTest.pill(): SemanticsNodeInteraction = onNode(isThePill)

    /** Whether the time is a row under the calendar, rather than wheels beside it. */
    private fun ComposeUiTest.inline(): Boolean = onAllNodes(isThePill).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aDayTappedKeepsTheTimeAndHasSettled() = runComposeUiTest {
        val picked = picker()
        onAllNodesWithText("24").onFirst().performClick()
        waitForIdle()
        assertEquals(LocalDateTime(LocalDate(2026, 6, 24), LocalTime(8, 15)), picked.value)
        assertEquals(1, picked.finished, "a tapped day settled ${picked.finished} times")
    }

    @Test
    fun aWheelTurnedKeepsTheDayAndSettlesOnce() = runComposeUiTest {
        val picked = picker()
        // The pill under the calendar opens the wheels.
        pill().performClick()
        waitForIdle()
        onNodeWithContentDescription("Minute").performTouchInput { swipeUp(durationMillis = 200) }
        waitForIdle()
        assertTrue(picked.changes.isNotEmpty(), "the minute wheel changed nothing")
        assertTrue(picked.changes.all { it.date == LocalDate(2026, 6, 18) }, "turning the minutes moved the day: ${picked.changes}")
        assertNotEquals(15, picked.value.minute, "the minute wheel did not turn")
        assertEquals(8, picked.value.hour, "turning the minutes moved the hour")
        assertEquals(1, picked.finished, "one flick settled ${picked.finished} times")
    }

    /**
     * On a 360dp phone and a 12-hour clock, far too narrow for the wheels
     * beside the calendar, the date and the time both show at once: the whole
     * calendar, and under it a row saying the time that adds no more than a
     * row's height. The segmented control showed one or the other.
     */
    @Test
    fun onAPhoneTheDateAndTheTimeShowTogether() = runComposeUiTest {
        val picked = picker(width = 360, height = null, cycle = HourCycle.H12, withCalendarAlone = true)
        val calendar = assertNotNull(calendar(), "the calendar is not showing")
        val pill = assertNotNull(onAllNodes(isThePill).fetchSemanticsNodes().firstOrNull(), "there is no time under the calendar")
            .boundsInRoot
        onNodeWithText("8:15", substring = true).assertExists()
        assertTrue(pill.top >= calendar.bottom, "the time at ${pill.top} is not under the calendar, whose last week ends at ${calendar.bottom}")
        assertTrue(pill.bottom <= picked.drawn, "the time runs to ${pill.bottom}, out of a picker ${picked.drawn}dp tall")
        assertEquals(null, minuteWheel(), "the wheels show before anything opened them")

        // What the time adds: the picker less a calendar alone, at the width.
        val added = picked.drawn - picked.alone
        assertTrue(picked.alone > 0f, "the calendar alone was not drawn")
        assertTrue(added in 1f..56f, "the time adds ${added}dp under a calendar ${picked.alone}dp tall: more than a row")
    }

    /**
     * The pill opens the wheels over the page, not into it: nothing below the
     * picker moves, and the calendar is still there. Turning a wheel sets the
     * time live, as beside the calendar, and a tap outside puts them away.
     */
    @Test
    fun thePillOpensTheWheelsInAnOverlay() = runComposeUiTest {
        val picked = picker(width = 360, height = null, cycle = HourCycle.H12)
        val tall = picked.drawn
        pill().performClick()
        waitForIdle()
        val minute = assertNotNull(minuteWheel(), "pressing the pill opened no wheels")
        assertEquals(tall, picked.drawn, "the wheels opened inside the picker, which grew from $tall to ${picked.drawn}")
        assertNotNull(calendar(), "the calendar went as the wheels opened")
        val pill = pill().fetchSemanticsNode().boundsInRoot
        assertTrue(minute.top >= pill.bottom || minute.bottom <= pill.top, "the wheels at ${minute.top}–${minute.bottom} cover the pill at ${pill.top}–${pill.bottom}")

        onNodeWithContentDescription("Minute").performTouchInput { swipeUp(durationMillis = 200) }
        waitForIdle()
        assertNotEquals(15, picked.value.minute, "the minute wheel did not turn")
        assertEquals(LocalDate(2026, 6, 18), picked.value.date, "turning the minutes moved the day")
        assertEquals(1, picked.finished, "one flick settled ${picked.finished} times")
        val minute2 = picked.value.minute.toString().padStart(2, '0')
        onNodeWithText("8:$minute2", substring = true).assertExists()

        // A tap outside, on the calendar's back arrow, puts them away and
        // does nothing else.
        onRoot().performTouchInput { click(Offset(8f, 8f)) }
        waitForIdle()
        assertEquals(null, minuteWheel(), "a tap outside left the wheels open")
        onNodeWithText("June 2026", substring = true).assertExists("the tap that closed the wheels went through to the calendar")
    }

    /**
     * The pill is a button, named for what it sets and saying the time it
     * shows, so a screen reader says "Time, 8:15 am, button".
     */
    @Test
    fun thePillSaysWhatItIsAndTheTime() = runComposeUiTest {
        picker(width = 360, cycle = HourCycle.H12)
        val config = pill().fetchSemanticsNode().config
        assertEquals(Role.Button, config.getOrNull(SemanticsProperties.Role))
        assertEquals(listOf("Time"), config.getOrNull(SemanticsProperties.ContentDescription))
        val state = config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
        assertTrue("8:15" in state, "the pill says \"$state\", not the time")
    }

    /**
     * From the keyboard, Enter on the pill takes the focus into the wheels,
     * and Escape brings it back to the pill: closed with the focus in it, the
     * popover took the focus with it, to nothing.
     */
    @Test
    fun theFocusGoesIntoTheWheelsAndBackToThePill() = runComposeUiTest {
        picker(width = 360, cycle = HourCycle.H12, modality = InputModality.Keyboard)
        pill().requestFocus()
        waitForIdle()
        pill().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        val minute = assertNotNull(minuteWheel(), "Enter on the pill opened no wheels")
        val focused = onAllNodes(isFocused()).fetchSemanticsNodes()
        assertEquals(1, focused.size, "the focus is on ${focused.size} nodes as the wheels open")
        val inside = focused.single().boundsInRoot
        assertTrue(
            inside.top >= minute.top - 1f && !isThePill.matches(focused.single()),
            "the focus is at $inside, not in the wheels at $minute",
        )

        onNode(isFocused()).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(null, minuteWheel(), "Escape left the wheels open")
        onNode(isFocused()).assertExists("the focus went nowhere as the wheels closed")
        assertTrue(isThePill.matches(onNode(isFocused()).fetchSemanticsNode()), "the focus did not come back to the pill")
    }

    /** Right to left, the row mirrors: the pill is at the left, where the calendar starts. */
    @Test
    fun rightToLeftThePillIsAtTheLeft() = runComposeUiTest {
        picker(width = 360, cycle = HourCycle.H12, rtl = true)
        val calendar = assertNotNull(calendar(), "the calendar is not showing")
        val pill = pill().fetchSemanticsNode().boundsInRoot
        assertTrue(pill.center.x < calendar.center.x, "right to left, the pill at ${pill.left}–${pill.right} is not at the left of a calendar from ${calendar.left} to ${calendar.right}")
    }

    /**
     * *"on larger phones, i should be able to see the 'inline' variant of the
     * date-time picker"*: inline is the two side by side, and a 412dp phone
     * has the width for them on a 24-hour clock. It is not a matter of height,
     * which it has plenty of.
     */
    @Test
    fun onALargePhoneTheWheelsSitBesideTheCalendar() = runComposeUiTest {
        picker(width = 412, height = 900, cycle = HourCycle.H23)
        val calendar = assertNotNull(calendar(), "the calendar is not showing")
        val minute = assertNotNull(minuteWheel(), "the wheels are not showing")
        assertTrue(minute.left >= calendar.right, "the wheels at ${minute.left} are not beside the calendar, whose days end at ${calendar.right}")
        assertTrue(minute.top < calendar.bottom, "the wheels at ${minute.top} are under the calendar, whose last week ends at ${calendar.bottom}")
        assertTrue(!inline(), "side by side there is still a time row")
    }

    /**
     * *"the date/time picker should never be vertical like that. it takes up
     * way too much vertical room"*: at no width, no height, in a page or not,
     * on either clock, are the wheels under the calendar. Where the two show at
     * once they are beside it.
     */
    @Test
    fun theWheelsAreNeverUnderTheCalendar() = runComposeUiTest {
        val picked = picker()
        var together = 0
        val wrong = mutableListOf<String>()
        for (cycle in listOf(HourCycle.H23, HourCycle.H12)) {
            for (width in listOf(320, 360, 393, 412, 430, 480, 560, 640, 900)) {
                for (height in listOf(480, 640, 900, null)) {
                    picked.cycle = cycle
                    picked.width = width
                    picked.height = height
                    waitForIdle()
                    val calendar = calendar() ?: continue
                    val minute = minuteWheel() ?: continue
                    together++
                    val where = "$cycle, ${width}dp wide and ${height?.let { "${it}dp high" } ?: "in a page"}"
                    if (minute.top >= calendar.bottom) {
                        wrong += "$where: the wheels at ${minute.top} are under the calendar, whose last week ends at ${calendar.bottom}"
                    } else if (minute.left < calendar.right && minute.right > calendar.left) {
                        wrong += "$where: the wheels at ${minute.left}–${minute.right} overlap the calendar at ${calendar.left}–${calendar.right}"
                    }
                }
            }
        }
        if (wrong.isNotEmpty()) fail(wrong.joinToString("\n"))
        assertTrue(together > 0, "the calendar and the wheels never showed together, so nothing was checked")
    }

    /**
     * The arrangement is the width's alone: the height a picker is given, and
     * whether it is in a page, change nothing about it, so it cannot change
     * under a reader as the page round it moves.
     */
    @Test
    fun theArrangementHangsOnTheWidthAlone() = runComposeUiTest {
        val picked = picker(width = 412, height = 900, cycle = HourCycle.H23)
        for (height in listOf(480, 640, null)) {
            picked.height = height
            waitForIdle()
            val where = "412dp wide and ${height?.let { "${it}dp high" } ?: "in a page"}"
            assertTrue(!inline(), "$where, the time is a row under the calendar")
            assertNotNull(minuteWheel(), "$where, the wheels are not showing")
        }
    }

    /**
     * The width the two need is the wheels' own: an am/pm wheel makes them
     * wider, so where a 24-hour clock's fit beside the calendar a 12-hour
     * clock's may not.
     */
    @Test
    fun aTwelveHourClockNeedsMoreRoom() = runComposeUiTest {
        val picked = picker(width = 412, height = 900, cycle = HourCycle.H23)
        assertTrue(!inline(), "a 24-hour clock does not fit beside the calendar at 412dp")
        picked.cycle = HourCycle.H12
        waitForIdle()
        assertTrue(inline(), "a 12-hour clock's three wheels fit beside the calendar at 412dp")
        picked.width = 480
        waitForIdle()
        assertTrue(!inline(), "a 12-hour clock's three wheels do not fit beside the calendar at 480dp")
    }

    /**
     * The calendar's month is its own through a change of arrangement: paged
     * on side by side, it is still on that month as a row under it.
     */
    @Test
    fun theCalendarKeepsItsMonthAsTheArrangementChanges() = runComposeUiTest {
        val picked = picker(width = 640, height = 900)
        onNodeWithContentDescription("Next month").performClick()
        waitForIdle()
        onNodeWithText("July 2026", substring = true).assertExists()
        picked.width = 360
        waitForIdle()
        assertTrue(inline(), "at 360dp the time is not a row under the calendar")
        onNodeWithText("July 2026", substring = true).assertExists("the calendar went back to June as the arrangement changed")
    }

    /** Larger type widens the wheels; side by side or a row under the calendar, they are still never under it. */
    @Test
    fun largerTypeIsSideBySideOrARowUnderTheCalendar() = runComposeUiTest {
        val picked = picker(width = 360, height = 900, cycle = HourCycle.H23, fontScale = 1.3f)
        for (width in listOf(360, 393, 412, 430, 480, 640)) {
            picked.width = width
            waitForIdle()
            val calendar = assertNotNull(calendar(), "${width}dp wide in larger type, the calendar is not showing")
            val minute = minuteWheel()
            if (minute == null) {
                assertTrue(inline(), "${width}dp wide in larger type, neither the wheels nor the time row show")
                continue
            }
            assertTrue(minute.left >= calendar.right, "${width}dp wide in larger type, the wheels at ${minute.left} are not beside the calendar, which ends at ${calendar.right}")
        }
    }

    /** Right to left, the calendar comes first, at the right, and the wheels after it. */
    @Test
    fun rightToLeftTheWheelsSitBeforeTheCalendar() = runComposeUiTest {
        picker(width = 640, height = 900, rtl = true)
        val calendar = assertNotNull(calendar(), "the calendar is not showing")
        val minute = assertNotNull(minuteWheel(), "the wheels are not showing")
        assertTrue(minute.right <= calendar.left, "right to left, the wheels end at ${minute.right}, past the calendar's start at ${calendar.left}")
    }

    @Test
    fun wideEnoughTheTwoSitSideBySide() = runComposeUiTest {
        picker(width = 640)
        val day = onAllNodesWithText("24").onFirst().fetchSemanticsNode().boundsInRoot
        val minute = onNodeWithContentDescription("Minute").fetchSemanticsNode().boundsInRoot
        val calendar = onAllNodesWithText("29").onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue(minute.left > day.right, "the wheels at ${minute.left} are not beside the calendar, whose 24th ends at ${day.right}")
        assertTrue(minute.top < calendar.bottom, "the wheels at ${minute.top} are under the calendar, whose last week ends at ${calendar.bottom}")
        assertTrue(!inline(), "side by side there is still a time row")
    }

    @Test
    fun sideBySideTheTwoSitTogether() = runComposeUiTest {
        picker(width = 900)
        // June 2026 starts on a Monday, so the 1st is in the calendar's first
        // column and the 28th in its last. A day's number is centred in a cell
        // 44dp wide, so the calendar's edge is up to 18dp past it.
        val first = onAllNodesWithText("1").onFirst().fetchSemanticsNode().boundsInRoot
        val sunday = onAllNodesWithText("28").onFirst().fetchSemanticsNode().boundsInRoot
        val hour = onNodeWithContentDescription("Hour").fetchSemanticsNode().boundsInRoot
        // The last wheel is the minutes', or the morning and afternoon's on a
        // 12-hour clock.
        val wheels = onAllNodes(hasScrollAction()).fetchSemanticsNodes().maxOf { it.boundsInRoot.right }
        val gap = hour.left - sunday.right
        assertTrue(gap in 0f..56f, "the wheels start ${gap}dp past the calendar's last column, not a gap away")
        val middle = (first.left + wheels) / 2f
        assertTrue(abs(middle - 450f) <= 16f, "the pair is centred at $middle in a picker 900dp wide")
    }
}
