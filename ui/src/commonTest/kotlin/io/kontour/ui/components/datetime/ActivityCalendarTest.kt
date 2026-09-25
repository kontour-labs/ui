package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
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

    /**
     * Long press, slide, lift: the day under the finger when it lifts is the one
     * picked — not the one the press began on — and a scrub is not also a tap.
     */
    @Test
    fun aScrubPicksTheDayUnderTheFingerWhenItLifts() = runComposeUiTest {
        val picked = mutableListOf<LocalDate>()
        setContent {
            KontourTheme {
                OverlayHost { Calendar(onDayClick = { picked += it }) }
            }
        }
        onRoot().performTouchInput {
            down(centreOf(5, 1, density))
            advanceEventTime(LongPress)
            moveTo(centreOf(6, 1, density))
            moveTo(centreOf(7, 2, density))
            up()
        }
        // Column 7, row 2: Wednesday this week.
        assertEquals(listOf(LocalDate(2026, 6, 3)), picked)
    }

    /** Slid off the grid before lifting, nothing is picked. */
    @Test
    fun aScrubLiftedOffTheGridPicksNothing() = runComposeUiTest {
        val picked = mutableListOf<LocalDate>()
        setContent {
            KontourTheme {
                OverlayHost { Calendar(onDayClick = { picked += it }) }
            }
        }
        onRoot().performTouchInput {
            down(centreOf(5, 1, density))
            advanceEventTime(LongPress)
            moveTo(centreOf(6, 1, density))
            moveTo(centreOf(6, 12, density))
            up()
        }
        assertEquals(emptyList(), picked)
    }

    /** A mark's description follows the day's count, in its tooltip and in its week's words. */
    @Test
    fun aMarkIsSaidAfterTheCount() = runComposeUiTest {
        setContent {
            KontourTheme {
                OverlayHost {
                    Calendar(
                        onDayClick = {},
                        markFor = { date, _ -> if (date == end) ActivityMark(corner = Color.Red, description = "Payday") else null },
                    )
                }
            }
        }
        onRoot().performMouseInput { moveTo(centreOf(7, 4, density)) }
        mainClock.advanceTimeBy(TooltipDelay)
        waitForIdle()
        onNodeWithText("4 activities on Friday, 5 June 2026. Payday").assertExists()
        onNode(hasContentDescription("Payday", substring = true)).assertExists()
    }

    /** Left to fit, the cells are big enough to pick: 20dp at least, so 24dp with the gap. */
    @Test
    fun cellsLeftToFitAreBigEnoughToPick() = runComposeUiTest {
        var width = 0
        setContent {
            KontourTheme {
                Box(Modifier.width(200.dp).onSizeChanged { width = it.width }) {
                    ActivityCalendar(
                        activity = activity,
                        end = end,
                        weeks = 53,
                        weekdayLabels = false,
                        monthLabels = false,
                        legend = false,
                    )
                }
            }
        }
        // 53 weeks in 200dp would be 3dp cells; they stay 20dp and scroll instead.
        val column = onAllNodes(hasContentDescription("Week of", substring = true))[0].fetchSemanticsNode().size.width
        assertEquals((20 * density.density).toInt(), column, "a week's column should be a 20dp cell wide")
        assertTrue(width > 0)
    }

    @androidx.compose.runtime.Composable
    private fun Calendar(
        selected: LocalDate? = null,
        markFor: ((LocalDate, Int) -> ActivityMark?)? = null,
        onDayClick: (LocalDate) -> Unit,
    ) {
        ActivityCalendar(
            activity = activity,
            end = end,
            weeks = 8,
            selected = selected,
            onDayClick = onDayClick,
            markFor = markFor,
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

        /** Past a long press's timeout. */
        const val LongPress = 700L
    }
}
