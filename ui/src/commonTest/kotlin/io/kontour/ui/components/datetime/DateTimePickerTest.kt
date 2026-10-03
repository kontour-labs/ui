package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A date and time picker changes the day without the time, and the time
 * without the day, and says once that each change has settled; on a phone it
 * shows one half at a time under a segmented control, and wider the two sit
 * side by side.
 *
 * Asked for as *"can we add a component for a date and time picker? it should
 * basically just be the two individual components we currently have for that,
 * combined"*, and then *"the two things need to be either side-by-side, or have
 * a segmented control that allows the user to swap between them"*: stacked,
 * the two were taller than a phone.
 */
@OptIn(ExperimentalTestApi::class)
class DateTimePickerTest {

    private class Picked(start: LocalDateTime) {
        var value by mutableStateOf(start)
        val changes = mutableListOf<LocalDateTime>()
        var finished = 0
    }

    private fun ComposeUiTest.picker(width: Int = 360): Picked {
        val picked = Picked(LocalDateTime(LocalDate(2026, 6, 18), LocalTime(8, 15)))
        setContent {
            KontourTheme(reduceMotion = true) {
                DateTimePicker(
                    value = picked.value,
                    onValueChange = {
                        picked.value = it
                        picked.changes += it
                    },
                    onValueChangeFinished = { picked.finished++ },
                    minuteStep = 5,
                    modifier = Modifier.width(width.dp),
                )
            }
        }
        waitForIdle()
        return picked
    }

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
        assertEquals(0, onAllNodes(hasContentDescription("Minute")).fetchSemanticsNodes().size, "the wheels show under the calendar")
        onAllNodesWithText("24").onFirst().assertExists()
        onNodeWithText(":15", substring = true).performClick()
        waitForIdle()
        assertEquals(0, onAllNodesWithText("24").fetchSemanticsNodes().size, "the calendar shows over the wheels")
        onNodeWithContentDescription("Minute").assertExists()
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
}
