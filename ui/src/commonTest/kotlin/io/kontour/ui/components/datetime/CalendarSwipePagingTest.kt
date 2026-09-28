package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A date picker's month follows a sideways swipe — asked for from a phone as
 * "allow scrolling between month when swiping the blank space" — and paging never
 * moves the page it is on: "randomly scrolls up sometimes when changing month".
 * Monday first throughout.
 */
@OptIn(ExperimentalTestApi::class)
class CalendarSwipePagingTest {

    /** Held part way, the month has moved with the finger and the next one is beside it; let go short, it comes back. */
    @Test
    fun theMonthFollowsTheFingerAndComesBackShortOfTheLine() = runComposeUiTest {
        val navigation = single(LocalDate(2026, 6, 1))
        val seventeenth = centreOf("17")
        onRoot().performTouchInput {
            down(seventeenth)
            repeat(10) { moveBy(Offset(-8f, 0f), delayMillis = 40) }
        }
        waitForIdle()
        val moved = centreOf("17").x - seventeenth.x
        assertTrue(abs(moved + 80f) < 3f, "the month should follow the finger 80px left, it moved $moved")
        assertTrue(onAllNodesWithText("17").fetchSemanticsNodes().size >= 2, "the next month should be showing beside it")
        onRoot().performTouchInput { up() }
        mainClock.advanceTimeBy(1000)
        assertEquals(LocalDate(2026, 6, 1), navigation.visibleMonth, "a short, slow swipe should come back")
        assertTrue(abs(centreOf("17").x - seventeenth.x) < 1f, "and settle where it was")
    }

    /** Past a third of the way, letting go goes on to the next month; the other way, the one before. */
    @Test
    fun pastAThirdItPages() = runComposeUiTest {
        val navigation = single(LocalDate(2026, 6, 1))
        val width = gridWidth()
        val from = centreOf("17")
        onRoot().performTouchInput {
            down(from)
            repeat(20) { moveBy(Offset(-width * 0.45f / 20f, 0f), delayMillis = 40) }
            // Still for a moment, so the release carries no flick.
            moveBy(Offset.Zero, delayMillis = 200)
            up()
        }
        mainClock.advanceTimeBy(1000)
        assertEquals(LocalDate(2026, 7, 1), navigation.visibleMonth, "a swipe left past a third should show July")
        val back = centreOf("17")
        onRoot().performTouchInput {
            down(back)
            repeat(20) { moveBy(Offset(width * 0.45f / 20f, 0f), delayMillis = 40) }
            moveBy(Offset.Zero, delayMillis = 200)
            up()
        }
        mainClock.advanceTimeBy(1000)
        assertEquals(LocalDate(2026, 6, 1), navigation.visibleMonth, "a swipe right past a third should show June again")
    }

    /**
     * Landing on a month is the end of the same movement: no frame after the
     * swipe lets go shows the month it left in the grid. It used to hand over to
     * the crossing, which drew the old month for a frame — "it flickers back to
     * the old month before the month/year at the top changes".
     */
    @Test
    fun landingDoesNotFlickerBackToTheOldMonth() = runComposeUiTest {
        val navigation = single(LocalDate(2026, 6, 1))
        val width = gridWidth()
        val juneSeventeenth = centreOf("17")
        val grid = onAllNodesWithText("1").onFirst().fetchSemanticsNode().boundsInRoot.left - 4f
        val gridEnd = grid + width + 8f
        mainClock.autoAdvance = false
        onRoot().performTouchInput {
            down(juneSeventeenth)
            repeat(20) { moveBy(Offset(-width * 0.45f / 20f, 0f), delayMillis = 40) }
            moveBy(Offset.Zero, delayMillis = 200)
            up()
        }
        var landedAt = -1
        repeat(60) { frame ->
            mainClock.advanceTimeByFrame()
            if (navigation.visibleMonth == LocalDate(2026, 6, 1)) return@repeat
            if (landedAt < 0) landedAt = frame
            // June's 17th is a Wednesday, July's a Friday: a 17 in June's place,
            // inside the grid, is June drawn again.
            val june = onAllNodesWithText("17").fetchSemanticsNodes().map { it.boundsInRoot.center }.filter {
                it.x in grid..gridEnd && abs(it.x - juneSeventeenth.x) < 2f && abs(it.y - juneSeventeenth.y) < 2f
            }
            assertTrue(june.isEmpty(), "on frame $frame, after landing on July, June's 17th was drawn in the grid again")
        }
        mainClock.autoAdvance = true
        assertTrue(landedAt >= 0, "the swipe never landed")
    }

    /**
     * The month and year cross-fade with the swipe, so the title is the new month
     * by the time the swipe lands rather than changing after it.
     */
    @Test
    fun theTitleCrossFadesWithTheSwipe() = runComposeUiTest {
        single(LocalDate(2026, 6, 1))
        val width = gridWidth()
        val title = onAllNodesWithText("June 2026").onFirst().fetchSemanticsNode().boundsInRoot
        fun header(): List<Int> {
            val pixels = onRoot().captureToImage().toPixelMap()
            val left = (title.left - 60f).toInt().coerceAtLeast(0)
            val right = (title.right + 60f).toInt().coerceAtMost(pixels.width - 1)
            return buildList {
                for (y in title.top.toInt()..title.bottom.toInt()) for (x in left..right) add(pixels[x, y].hashCode())
            }
        }
        fun differ(a: List<Int>, b: List<Int>) = a.zip(b).count { (p, q) -> p != q }
        val june = header()
        onRoot().performTouchInput {
            down(centreOf("17"))
            repeat(10) { moveBy(Offset(-width * 0.5f / 10f, 0f), delayMillis = 40) }
        }
        waitForIdle()
        val halfway = header()
        onRoot().performTouchInput {
            moveBy(Offset(-width * 0.2f, 0f), delayMillis = 40)
            moveBy(Offset.Zero, delayMillis = 200)
            up()
        }
        mainClock.advanceTimeBy(1000)
        val july = header()
        assertTrue(differ(june, july) > 20, "the title did not change from June to July")
        assertTrue(
            differ(halfway, june) > 20 && differ(halfway, july) > 20,
            "halfway through the swipe the title was one month or the other, not on its way between them",
        )
    }

    /**
     * A second swipe caught mid-settle carries on from the month the first was
     * taking it to: two quick swipes are two months. It used to start again from
     * the month the first had left, so a quick run of them went back and forth
     * between the same two — "it just keeps repeating between two months".
     */
    @Test
    fun quickSwipesKeepGoing() = runComposeUiTest {
        val navigation = single(LocalDate(2026, 6, 1))
        val width = gridWidth()
        val on = centreOf("17")
        mainClock.autoAdvance = false
        repeat(3) {
            onRoot().performTouchInput {
                down(on)
                repeat(6) { moveBy(Offset(-width * 0.45f / 6f, 0f), delayMillis = 16) }
                up()
            }
            // Caught well before it has settled.
            repeat(3) { mainClock.advanceTimeByFrame() }
        }
        mainClock.autoAdvance = true
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        assertEquals(LocalDate(2026, 9, 1), navigation.visibleMonth, "three quick swipes left from June should reach September")
    }

    /** A short quick flick pages, however little of the way it went. */
    @Test
    fun aFlickPages() = runComposeUiTest {
        val navigation = single(LocalDate(2026, 6, 1))
        onRoot().performTouchInput {
            down(centreOf("17"))
            repeat(5) { moveBy(Offset(-14f, 0f), delayMillis = 8) }
            up()
        }
        mainClock.advanceTimeBy(1000)
        assertEquals(LocalDate(2026, 7, 1), navigation.visibleMonth, "a flick left should show July")
    }

    /** Right to left, the months run the other way: a swipe right is the next month. */
    @Test
    fun rightToLeftTheSwipeIsMirrored() = runComposeUiTest {
        val navigation = single(LocalDate(2026, 6, 1), LayoutDirection.Rtl)
        val width = gridWidth()
        onRoot().performTouchInput {
            down(centreOf("17"))
            repeat(20) { moveBy(Offset(width * 0.45f / 20f, 0f), delayMillis = 40) }
            moveBy(Offset.Zero, delayMillis = 200)
            up()
        }
        mainClock.advanceTimeBy(1000)
        assertEquals(LocalDate(2026, 7, 1), navigation.visibleMonth, "right to left, a swipe right should show July")
    }

    /** A drag up the calendar is the page's: it scrolls, and the month stays. */
    @Test
    fun aVerticalDragStillScrollsThePage() = runComposeUiTest {
        lateinit var navigation: CalendarNavigationState
        lateinit var scroll: ScrollState
        setContent {
            KontourTheme {
                navigation = rememberCalendarNavigationState(LocalDate(2026, 6, 1))
                scroll = rememberScrollState()
                Column(Modifier.height(400.dp).verticalScroll(scroll)) {
                    Box(Modifier.width(420.dp).padding(24.dp)) {
                        DatePicker(value = null, onValueChange = {}, navigation = navigation, firstDayOfWeek = DayOfWeek.MONDAY)
                    }
                    Box(Modifier.height(800.dp))
                }
            }
        }
        onRoot().performTouchInput {
            down(centreOf("17"))
            repeat(10) { moveBy(Offset(2f, -15f), delayMillis = 16) }
            up()
        }
        mainClock.advanceTimeBy(1000)
        assertEquals(LocalDate(2026, 6, 1), navigation.visibleMonth, "a drag up should not page")
        assertTrue(scroll.value > 50, "a drag up the calendar should scroll the page, scrolled ${scroll.value}")
    }

    /** In a range picker a drag from a day is the range, and one from the weekday initials pages. */
    @Test
    fun aRangePickerSwipesOnlyFromWhereARangeCannotStart() = runComposeUiTest {
        var range: Pair<LocalDate, LocalDate?>? = null
        lateinit var navigation: CalendarNavigationState
        setContent {
            KontourTheme {
                navigation = rememberCalendarNavigationState(LocalDate(2026, 6, 1))
                var start by remember { mutableStateOf<LocalDate?>(null) }
                var end by remember { mutableStateOf<LocalDate?>(null) }
                Box(Modifier.width(420.dp).padding(24.dp)) {
                    DateRangePicker(
                        start = start,
                        end = end,
                        onRangeChange = { s, e ->
                            start = s
                            end = e
                            range = s to e
                        },
                        navigation = navigation,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    )
                }
            }
        }
        waitForIdle()
        val width = gridWidth()
        val from = centreOf("17")
        onRoot().performTouchInput {
            down(from)
            repeat(12) { moveBy(Offset(-width * 0.3f / 12f, 0f), delayMillis = 40) }
            up()
        }
        mainClock.advanceTimeBy(1000)
        assertEquals(LocalDate(2026, 6, 1), navigation.visibleMonth, "a drag from a day should not page")
        // Leftward from the 17th: the 17th is the range's end.
        assertTrue(range?.second == LocalDate(2026, 6, 17) && range?.first != LocalDate(2026, 6, 17), "a drag from a day should pick a range, picked $range")

        // Level with the weekday initials: the row above the first week.
        val initials = Offset(centreOf("17").x, centreOf("1").y - (centreOf("8").y - centreOf("1").y))
        onRoot().performTouchInput {
            down(initials)
            repeat(20) { moveBy(Offset(-width * 0.45f / 20f, 0f), delayMillis = 40) }
            moveBy(Offset.Zero, delayMillis = 200)
            up()
        }
        mainClock.advanceTimeBy(1000)
        assertEquals(LocalDate(2026, 7, 1), navigation.visibleMonth, "a swipe from the weekday initials should page")
    }

    /**
     * Every month is six weeks tall, so paging from a five-week month to a
     * six-week one and back never changes the picker's height — which, at the
     * end of a scrolling list, was the list scrolling back up.
     */
    @Test
    fun pagingNeverMovesThePage() = runComposeUiTest {
        lateinit var navigation: CalendarNavigationState
        lateinit var list: LazyListState
        setContent {
            KontourTheme {
                // March 2026 is six weeks from Monday; April is five.
                navigation = rememberCalendarNavigationState(LocalDate(2026, 3, 1))
                list = rememberLazyListState()
                LazyColumn(Modifier.height(300.dp).width(420.dp), state = list) {
                    item { Box(Modifier.fillMaxWidth().height(200.dp)) }
                    item {
                        DatePicker(
                            value = null,
                            onValueChange = {},
                            navigation = navigation,
                            nextIcon = Arrow,
                            previousIcon = Arrow,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            modifier = Modifier.testTag("picker"),
                        )
                    }
                }
            }
        }
        runOnIdle { list.requestScrollToItem(1, 10_000) }
        waitForIdle()
        val march = onNodeWithTag("picker").fetchSemanticsNode().size.height
        val scrolled = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
        // As the arrow does; the arrow itself is scrolled out of reach.
        runOnIdle { navigation.step(1) }
        mainClock.advanceTimeBy(1000)
        assertEquals(LocalDate(2026, 4, 1), navigation.visibleMonth)
        val april = onNodeWithTag("picker").fetchSemanticsNode().size.height
        assertEquals(march, april, "a five-week month should be as tall as a six-week one")
        assertEquals(scrolled, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset, "paging moved the list")
    }

    private fun ComposeUiTest.single(month: LocalDate, direction: LayoutDirection = LayoutDirection.Ltr): CalendarNavigationState {
        lateinit var navigation: CalendarNavigationState
        setContent {
            KontourTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    navigation = rememberCalendarNavigationState(month)
                    Box(Modifier.width(420.dp).padding(24.dp)) {
                        DatePicker(value = null, onValueChange = {}, navigation = navigation, firstDayOfWeek = DayOfWeek.MONDAY)
                    }
                }
            }
        }
        waitForIdle()
        return navigation
    }

    private fun ComposeUiTest.centreOf(day: String): Offset =
        onAllNodesWithText(day).onFirst().fetchSemanticsNode().boundsInRoot.center

    /** Seven columns, from the 1st of June (a Monday) to the 7th (a Sunday). */
    private fun ComposeUiTest.gridWidth(): Float = abs(centreOf("7").x - centreOf("1").x) * 7f / 6f

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
