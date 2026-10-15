package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowSizeClass
import io.kontour.ui.locale.HourCycle
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
 * calendar and the wheels side by side wherever the two fit across, and one
 * half at a time under a segmented control where they do not — and never the
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
 */
@OptIn(ExperimentalTestApi::class)
class DateTimePickerTest {

    /**
     * The picker's value and the room it is given: [width] across and [height]
     * high, or at a null height, in a page that scrolls on a large phone's
     * window. A null [cycle] is the theme's clock.
     */
    private class Picked(start: LocalDateTime, width: Int, height: Int?, cycle: HourCycle?) {
        var value by mutableStateOf(start)
        val changes = mutableListOf<LocalDateTime>()
        var finished = 0
        var width by mutableStateOf(width)
        var height by mutableStateOf(height)
        var cycle by mutableStateOf(cycle)
    }

    /**
     * A picker [width] wide, given [height] — a small phone's room by default,
     * where the two halves take turns — or, at null, in a page that scrolls.
     */
    private fun ComposeUiTest.picker(
        width: Int = 360,
        height: Int? = 480,
        cycle: HourCycle? = null,
        rtl: Boolean = false,
        fontScale: Float = 1f,
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
                        DateTimePicker(
                            value = picked.value,
                            onValueChange = {
                                picked.value = it
                                picked.changes += it
                            },
                            onValueChangeFinished = { picked.finished++ },
                            minuteStep = 5,
                            formats = formats,
                            modifier = modifier.width(picked.width.dp),
                        )
                    }
                    val given = picked.height
                    if (given != null) {
                        picker(Modifier.height(given.dp))
                    } else {
                        // A large phone's window, provided as the theme would
                        // read it: tall enough for anything.
                        CompositionLocalProvider(LocalWindowSizeClass provides WindowSizeClass.of(430.dp, 932.dp)) {
                            Column(Modifier.verticalScroll(rememberScrollState())) { picker(Modifier) }
                        }
                    }
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

    /** Whether the segmented control is there, saying the time as 8:15 am or 08:15. */
    private fun ComposeUiTest.segmented(): Boolean =
        onAllNodesWithText(":15", substring = true).fetchSemanticsNodes().isNotEmpty()

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
        // The time's segment says the time — 8:15 am, or 08:15, as the clock
        // writes it — and shows the wheels.
        onNodeWithText(":15", substring = true).performClick()
        waitForIdle()
        onNodeWithContentDescription("Minute").performTouchInput { swipeUp(durationMillis = 200) }
        waitForIdle()
        assertTrue(picked.changes.isNotEmpty(), "the minute wheel changed nothing")
        assertTrue(picked.changes.all { it.date == LocalDate(2026, 6, 18) }, "turning the minutes moved the day: ${picked.changes}")
        assertNotEquals(15, picked.value.minute, "the minute wheel did not turn")
        assertEquals(8, picked.value.hour, "turning the minutes moved the hour")
        assertEquals(1, picked.finished, "one flick settled ${picked.finished} times")
    }

    @Test
    fun onAPhoneOneHalfShowsAtATime() = runComposeUiTest {
        picker()
        assertEquals(0, onAllNodes(hasContentDescription("Minute")).fetchSemanticsNodes().size, "the wheels show with the calendar")
        onAllNodesWithText("24").onFirst().assertExists()
        onNodeWithText(":15", substring = true).performClick()
        waitForIdle()
        assertEquals(0, onAllNodesWithText("24").fetchSemanticsNodes().size, "the calendar shows over the wheels")
        onNodeWithContentDescription("Minute").assertExists()
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
        assertTrue(!segmented(), "side by side there is still a segmented control")
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
            assertTrue(!segmented(), "$where, the two are one at a time")
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
        assertTrue(!segmented(), "a 24-hour clock does not fit beside the calendar at 412dp")
        picked.cycle = HourCycle.H12
        waitForIdle()
        assertTrue(segmented(), "a 12-hour clock's three wheels fit beside the calendar at 412dp")
        picked.width = 480
        waitForIdle()
        assertTrue(!segmented(), "a 12-hour clock's three wheels do not fit beside the calendar at 480dp")
    }

    /** Larger type widens the wheels; side by side or one at a time, they are still never under the calendar. */
    @Test
    fun largerTypeIsSideBySideOrOneAtATime() = runComposeUiTest {
        val picked = picker(width = 360, height = 900, cycle = HourCycle.H23, fontScale = 1.3f)
        for (width in listOf(360, 393, 412, 430, 480, 640)) {
            picked.width = width
            waitForIdle()
            val calendar = calendar() ?: continue
            val minute = minuteWheel() ?: continue
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
        assertEquals(0, onAllNodesWithText(":15", substring = true).fetchSemanticsNodes().size, "side by side there is still a segmented control")
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
