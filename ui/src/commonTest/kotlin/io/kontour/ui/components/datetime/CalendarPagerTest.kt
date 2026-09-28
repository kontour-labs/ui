package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One pager moves a calendar's months, however they are asked to move: a swipe,
 * the arrows, a jump. Asked for from a phone — "the month-switching model needs to
 * be redone around the swipe gesture, and then the left/right buttons being
 * secondary" — along with the chooser's arrow that "just jumps into position once
 * it's settled".
 */
@OptIn(ExperimentalTestApi::class)
class CalendarPagerTest {

    /**
     * An arrow moves the months side by side, a page apart, as a swipe does. They
     * used to cross in a separate slide a third of the width long, fading.
     */
    @Test
    fun theArrowsMoveTheMonthsAsASwipeDoes() = runComposeUiTest {
        val navigation = picker(LocalDate(2026, 6, 1))
        val width = gridWidth()
        mainClock.autoAdvance = false
        onNodeWithContentDescription("Next month").performClick()
        repeat(2) { mainClock.advanceTimeByFrame() }
        // Both Wednesdays, so in the same column of their months.
        val june = dayAt("June", "17")
        val july = dayAt("July", "15")
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(june != null && july != null, "mid-move, both June and July should be on the strip: $june, $july")
        assertEquals(
            width,
            july.center.x - june.center.x,
            4f,
            "mid-move July was ${july.center.x - june.center.x}px after June, not a page ($width px) — it was not on the pager",
        )
        assertEquals(LocalDate(2026, 7, 1), navigation.visibleMonth)
    }

    /**
     * A far jump travels one page, not a year of them: the month it goes to comes
     * in beside the one in view, and nothing between them is drawn on the way.
     */
    @Test
    fun aFarJumpComesInBesideTheMonthInView() = runComposeUiTest {
        val navigation = picker(LocalDate(2026, 6, 1))
        val width = gridWidth()
        mainClock.autoAdvance = false
        runOnIdle { navigation.jumpTo(LocalDate(2027, 6, 1)) }
        repeat(4) { mainClock.advanceTimeByFrame() }
        // Both Wednesdays.
        val here = dayAt("June 2026", "17")
        val there = dayAt("June 2027", "16")
        val between = dayAt("December", "17")
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(here != null && there != null, "mid-jump, both Junes should be in view: $here, $there")
        assertEquals(width, there.center.x - here.center.x, 4f, "the month jumped to did not come in a page beside")
        assertTrue(between == null, "a month between the two was drawn on the way: $between")
        assertEquals(LocalDate(2027, 6, 1), navigation.visibleMonth)
        assertTrue(dayAt("June 2027", "16") != null, "the jump did not land on June 2027")
    }

    /**
     * The chooser's arrow moves with the title as the months do, and is already
     * where it belongs when they land. September to August is three letters
     * narrower: the title's width used to hold at the widest month in view while
     * the swipe ran and snap to August's once it had landed, taking the arrow with
     * it in a single frame.
     */
    @Test
    fun theChoosersArrowTravelsWithTheTitle() = runComposeUiTest {
        val navigation = picker(LocalDate(2026, 9, 1), chooser = true)
        val width = onNodeWithTag("picker").fetchSemanticsNode().size.width.toFloat()
        val from = dayCentre("September", "16")
        val edges = mutableListOf<Float>()
        fun edge() = onAllTitleNodes().right
        mainClock.autoAdvance = false
        edges += edge()
        onRoot().performTouchInput {
            down(from)
            repeat(10) { moveBy(Offset(width * 0.45f / 10f, 0f), delayMillis = 16) }
            up()
        }
        repeat(60) {
            mainClock.advanceTimeByFrame()
            edges += edge()
        }
        mainClock.autoAdvance = true
        assertEquals(LocalDate(2026, 8, 1), navigation.visibleMonth, "the swipe should have landed on August")
        val travel = abs(edges.last() - edges.first())
        val steps = edges.zipWithNext { a, b -> abs(b - a) }
        assertTrue(travel > 6f, "September's and August's titles should differ in width: $edges")
        assertTrue(
            steps.max() <= travel * 0.6f,
            "the arrow moved ${steps.max()}px of its $travel in one frame — it jumped rather than travelled: $edges",
        )
    }

    private fun ComposeUiTest.picker(month: LocalDate, chooser: Boolean = false): CalendarNavigationState {
        lateinit var navigation: CalendarNavigationState
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                OverlayHost(Modifier.fillMaxSize()) {
                    navigation = rememberCalendarNavigationState(month)
                    Box(Modifier.width(420.dp).padding(24.dp)) {
                        DatePicker(
                            value = null,
                            onValueChange = {},
                            navigation = navigation,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            previousIcon = Arrow,
                            nextIcon = Arrow,
                            chooserIcon = if (chooser) Arrow else null,
                            modifier = Modifier.testTag("picker"),
                        )
                    }
                }
            }
        }
        waitForIdle()
        return navigation
    }

    /** The cell for [day] whose full date names every one of [month]'s words, if one is drawn. */
    private fun ComposeUiTest.dayAt(month: String, day: String): Rect? {
        val words = month.split(" ")
        return onAllNodes(hasText(day) and SemanticsMatcher("in $month") { node ->
            val date = node.config.getOrNull(SemanticsProperties.StateDescription) ?: return@SemanticsMatcher false
            words.all { it in date }
        }).fetchSemanticsNodes().firstOrNull()?.let { Rect(it.positionInRoot, it.size.toSize()) }
    }

    private fun ComposeUiTest.dayCentre(month: String, day: String): Offset =
        requireNotNull(dayAt(month, day)) { "no $day in $month" }.center

    /** The title and its arrow: the one button whose words are a month and year. */
    private fun ComposeUiTest.onAllTitleNodes(): Rect =
        onAllNodes(hasClickAction() and hasText("2026", substring = true)).fetchSemanticsNodes().single().boundsInRoot

    /** Seven columns, from one Monday to the next. */
    private fun ComposeUiTest.gridWidth(): Float {
        val monday = requireNotNull(dayAt("June", "1"))
        val nextMonday = requireNotNull(dayAt("June", "8"))
        // A week down is the same column, so a column is a seventh of a row from
        // the 1st to the 7th.
        val sunday = requireNotNull(dayAt("June", "7"))
        assertEquals(monday.center.x, nextMonday.center.x, 1f)
        return (sunday.center.x - monday.center.x) * 7f / 6f
    }

    private companion object {
        val Arrow: ImageVector = ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 4f)
                lineTo(16f, 12f)
                lineTo(8f, 20f)
                close()
            }
        }.build()
    }
}
