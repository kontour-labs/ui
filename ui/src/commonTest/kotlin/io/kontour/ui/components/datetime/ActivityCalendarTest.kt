package io.kontour.ui.components.datetime

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An activity calendar picked by touch, pointer, keyboard and screen reader.
 *
 * Eight weeks to Friday 5 June 2026, a week from Monday to a column, 12dp cells
 * 3dp apart and nothing around them, so a day's cell is arithmetic: column times
 * 15dp across, row times 15dp down.
 */
@OptIn(ExperimentalTestApi::class)
class ActivityCalendarTest {

    private val end = LocalDate(2026, 6, 5)
    private val activity = mapOf(end to 4, end.minus(DatePeriod(days = 7)) to 2)

    private fun centreOf(column: Int, row: Int, density: Float) =
        Offset((column * 15 + 6) * density, (row * 15 + 6) * density)

    @Test
    fun aTapPicksTheDayUnderIt() = runComposeUiTest {
        val picked = mutableListOf<LocalDate>()
        setContent {
            KontourTheme {
                Calendar(onDayClick = { picked += it })
            }
        }
        // Column 7 is this week; row 4 is Friday.
        onRoot().performTouchInput { click(centreOf(7, 4, density)) }
        onRoot().performTouchInput { click(centreOf(6, 0, density)) }
        assertEquals(listOf(end, LocalDate(2026, 5, 25)), picked)
    }

    @Test
    fun aRestingPointerShowsTheDaysCount() = runComposeUiTest {
        setContent {
            KontourTheme {
                OverlayHost { Calendar(onDayClick = {}) }
            }
        }
        onRoot().performMouseInput { moveTo(centreOf(7, 4, density)) }
        mainClock.advanceTimeBy(TooltipDelay)
        waitForIdle()
        onNodeWithText("4 activities on Friday, 5 June 2026").assertExists()
    }

    /**
     * A week is one node for a screen reader: what happened that week, selected
     * if the picked day is in it, and an action to pick each of its days.
     */
    @Test
    fun aWeekSaysWhatHappenedAndPicksEachOfItsDays() = runComposeUiTest {
        val picked = mutableListOf<LocalDate>()
        setContent {
            KontourTheme {
                Calendar(selected = end, onDayClick = { picked += it })
            }
        }
        val week = onNode(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .fetchSemanticsNode()
        val said = week.config[SemanticsProperties.ContentDescription].single()
        assertTrue(said.startsWith("Week of 1 Jun 2026"), "the week is named by its first day: $said")
        assertTrue("4 activities on Friday, 5 June 2026" in said, "and says what happened: $said")
        val actions = week.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(5, actions.size, "a day to pick for each day of the week so far")
        actions.last().action()
        assertEquals(listOf(end), picked)
    }

    @Test
    fun theArrowsMoveByAWeekAcrossAndEnterPicks() = runComposeUiTest {
        val picked = mutableListOf<LocalDate>()
        val start = end.minus(DatePeriod(days = 14))
        setContent {
            KontourTheme {
                // Hosted: the keyboard's cursor shows its day's count in a tooltip.
                OverlayHost { Calendar(selected = start, onDayClick = { picked += it }) }
            }
        }
        onNode(isFocusable()).requestFocus()
        onNode(isFocusable()).performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionUp)
            pressKey(Key.Enter)
        }
        assertEquals(listOf(start.plusDays(7 - 1)), picked)
    }

    /** The theme's words reach the week nodes: a translated app says its own "Week of". */
    @Test
    fun theThemesWordsNameTheWeeks() = runComposeUiTest {
        setContent {
            KontourTheme(strings = Strings(weekOf = { "Woche vom $it" })) {
                Calendar(onDayClick = {})
            }
        }
        onNode(hasContentDescription("Woche vom 1 Jun 2026", substring = true)).assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun Calendar(selected: LocalDate? = null, onDayClick: (LocalDate) -> Unit) {
        ActivityCalendar(
            activity = activity,
            end = end,
            weeks = 8,
            selected = selected,
            onDayClick = onDayClick,
            cellSize = 12.dp,
            monthLabels = false,
            weekdayLabels = false,
            legend = false,
            formats = DateTimeFormats(dayFirst = true, firstDayOfWeek = DayOfWeek.MONDAY),
        )
    }

    private fun LocalDate.plusDays(days: Int) = minus(DatePeriod(days = -days))

    private companion object {
        /** Past the pointer's resting time. */
        const val TooltipDelay = 600L
    }
}
