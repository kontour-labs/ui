package io.kontour.ui.components.datetime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a date picker draws on a day besides its number: dots, a line of text
 * and an icon, and a colour of its own — each able to keep its colour.
 *
 * Asked for first as *"the ability to colour and annotate dates"*, and then:
 * *"they are not all that customisable at the moment. additionally in the date
 * range picker, if you've got a decoration on a highlighted date, then it'll just
 * go grey. i want to add customisability and have all that stuff to have the
 * ability to retain its colour"*.
 */
@OptIn(ExperimentalTestApi::class)
class DayDecorationTest {

    private val day = LocalDate(2026, 6, 10)

    // 15 to 21 June 2026 is a Monday-to-Sunday week: the range is 16 to 18.
    private val start = LocalDate(2026, 6, 16)
    private val middle = LocalDate(2026, 6, 17)
    private val end = LocalDate(2026, 6, 18)

    /**
     * A range's days say where in it they are, after the date: only its ends
     * are selected, so a day inside the band was announced as a day like any
     * other, and the two ends as the same thing.
     */
    @Test
    fun aRangesDaysSayWhereInTheRangeTheyAre() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                DateRangePicker(
                    start = start,
                    end = end,
                    onRangeChange = { _, _ -> },
                    today = null,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            }
        }
        waitForIdle()
        fun said(day: String) = onNodeWithText(day).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
        for ((day, place) in listOf("16" to "Range start", "17" to "In range", "18" to "Range end")) {
            val words = said(day)
            assertTrue(place in words && words.indexOf(place) > words.indexOf("2026"), "the $day announces '$words', not '$place' after the date")
        }
        assertTrue("range" !in said("19").lowercase(), "a day outside the range says it is in one: '${said("19")}'")
    }

    @Test
    fun aDecorationsTextIsDrawnUnderItsDayAndReadAfterTheDate() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                DatePicker(
                    value = null,
                    onValueChange = {},
                    decorationFor = { if (it == day) DayDecoration(text = "$42", contentDescription = "42 dollars") else null },
                    navigation = rememberCalendarNavigationState(day),
                )
            }
        }
        waitForIdle()
        // Drawn once, on the 10th, and not a node of its own: the day carries it.
        onAllNodesWithText("$42", useUnmergedTree = true).fetchSemanticsNodes().let {
            assertEquals(1, it.size, "the text was drawn ${it.size} times")
        }
        val tenth = onNodeWithText("10").fetchSemanticsNode()
        val said = tenth.config.getOrNull(SemanticsProperties.StateDescription)
        assertTrue(
            said != null && said.contains("42 dollars") && said.indexOf("42 dollars") > said.indexOf("2026"),
            "the 10th announces '$said' — the decoration's description should follow the date",
        )
    }

    @Test
    fun aDecorationLineMakesTheWeeksTallerAndDotsAloneNeedNot() {
        fun height(decorated: Boolean, line: Boolean): Int {
            var h = 0
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                        KontourTheme(darkTheme = false, reduceMotion = true) {
                            Box(Modifier.testTag(Tag).width(350.dp)) {
                                DatePicker(
                                    value = null,
                                    onValueChange = {},
                                    decorationFor = if (decorated) ({ null }) else null,
                                    showDecorationLine = line,
                                )
                            }
                        }
                    }
                }
                waitForIdle()
                h = onNodeWithTag(Tag).fetchSemanticsNode().size.height
            }
            return h
        }
        val plain = height(decorated = false, line = false)
        val lined = height(decorated = true, line = true)
        val dotsOnly = height(decorated = true, line = false)
        // Six weeks, each a line of `labelSmall` taller: well over a line in all.
        assertTrue(lined - plain >= 6 * 12, "a decoration line made the picker $plain → ${lined}px tall; six weeks should each gain a line")
        assertEquals(plain, dotsOnly, "a picker that only draws dots changed height")
    }

    @Test
    fun aDaysOwnColourFillsItAndARangeIsDrawnOverItUnlessKept() {
        val holiday = Color(0xFF2E7D32)
        fun greens(keep: Boolean): Int {
            var green = 0
            runComposeUiTest {
                range(decorationFor = {
                    if (it == day || it == middle) DayDecoration(containerColour = holiday, keepColours = keep) else null
                })
                green = image().count(fullRect()) { it.near(holiday, 0.01f) }
            }
            return green
        }
        val drawnOver = greens(keep = false)
        val kept = greens(keep = true)
        // The 10th's circle, and not the 17th, which is inside the range and
        // drawn as the range: a circle of a few hundred pixels, not two.
        assertTrue(drawnOver > 200, "the 10th's own colour was not drawn ($drawnOver pixels of it)")
        assertTrue(drawnOver < 2000, "$drawnOver pixels of the day colour: the range's middle day kept its own colour over the band")
        // Kept, the 17th draws a circle of it on the band as well.
        assertTrue(kept > drawnOver + 200, "kept, the 17th's own colour still was not drawn on the band ($drawnOver → $kept)")
    }

    @Test
    fun onTheBandADecorationKeepsItsColoursAndIsNotGreyed() = runComposeUiTest {
        val red = Color(0xFFD32F2F)
        range(decorationFor = { if (it == middle) DayDecoration(dots = listOf(red), text = "$9", textColour = red) else null })
        val reds = image().count(cell(middle)) { it.near(red, 0.12f) }
        assertTrue(reds > 6, "the 17th, in the band of the range, drew its red text and dot in $reds red pixels: it was greyed")
    }

    @Test
    fun onTheRangesEndADecorationTakesItsInkUnlessItKeepsItsColours() {
        val red = Color(0xFFD32F2F)
        fun reds(keep: Boolean): Pair<Int, Int> {
            var result = 0 to 0
            runComposeUiTest {
                var surface = Color.Unspecified
                range(decorationFor = {
                    if (it == start) DayDecoration(dots = listOf(red), text = "$9", textColour = red, keepColours = keep) else null
                }) { surface = Theme.colours.surface }
                val image = image()
                result = image.count(cell(start)) { it.near(red, 0.12f) } to image.count(cell(start)) { it.near(surface, 0.01f) }
            }
            return result
        }
        val (inked, _) = reds(keep = false)
        val (kept, ground) = reds(keep = true)
        assertTrue(inked < 3, "on the chosen end, a decoration that does not keep its colours drew $inked red pixels")
        assertTrue(kept > 6, "kept, the chosen end's decoration drew only $kept red pixels")
        assertTrue(ground > 20, "kept on the chosen end, nothing of the page's ground stood the decoration off the fill ($ground pixels)")
    }

    @Test
    fun aDayDrawsThreeDotsAtMost() = runComposeUiTest {
        val inks = listOf(Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFFBC02D), Color(0xFF7B1FA2))
        range(decorationFor = { if (it == day) DayDecoration(dots = inks) else null })
        val image = image()
        val drawn = inks.map { ink -> image.count(cell(day)) { it.near(ink, 0.05f) } > 0 }
        assertEquals(listOf(true, true, true, false, false), drawn, "which of five dots the 10th drew")
    }

    @Test
    fun anIconIsDrawnOnTheLine() = runComposeUiTest {
        val blue = Color(0xFF1976D2)
        range(decorationFor = { if (it == day) DayDecoration(icon = SystemIcons.Default.close, iconColour = blue) else null })
        val blues = image().count(cell(day)) { it.near(blue, 0.15f) }
        assertTrue(blues > 6, "the 10th's icon was not drawn ($blues blue pixels)")
    }

    /**
     * Today's ring stays a circle inside a range: *"if the current date is part
     * of the highlighted zone, the outline around it becomes a pill"*. It was the
     * fill's outline, and the fill is the cell's whole width in the band.
     */
    @Test
    fun todaysRingInARangeIsACircle() = runComposeUiTest {
        var ring = Color.Unspecified
        range(today = middle, decorationFor = null, line = false) { ring = Theme.colours.outlineStrong }
        val image = image()
        val box = cell(middle)
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var top = Int.MAX_VALUE
        var bottom = Int.MIN_VALUE
        for (y in box.top.toInt() until box.bottom.toInt()) for (x in box.left.toInt() until box.right.toInt()) {
            if (x !in 0 until image.width || y !in 0 until image.height) continue
            if (image[x, y].near(ring, 0.04f)) {
                left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        assertTrue(right > left, "today's ring was not drawn")
        val w = right - left
        val h = bottom - top
        assertTrue(abs(w - h) <= 3, "today's ring in the range is ${w}×$h: a pill, not a circle")
    }

    // --- The scene ---

    /** A range picker on 16–18 June 2026, at 1x and 350 wide. */
    private fun ComposeUiTest.range(
        today: LocalDate? = null,
        decorationFor: ((LocalDate) -> DayDecoration?)?,
        line: Boolean = true,
        read: @Composable () -> Unit = {},
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    read()
                    Box(Modifier.testTag(Tag).width(350.dp).background(Color.White)) {
                        DateRangePicker(
                            start = start,
                            end = end,
                            onRangeChange = { _, _ -> },
                            today = today,
                            decorationFor = decorationFor,
                            showDecorationLine = line,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.image(): PixelMap = onNodeWithTag(Tag).captureToImage().toPixelMap()

    private fun ComposeUiTest.fullRect(): Rect = onNodeWithTag(Tag).fetchSemanticsNode().boundsInRoot

    /** A day's whole cell: its number's node, a seventh of the width across and a week tall. */
    private fun ComposeUiTest.cell(date: LocalDate): Rect {
        val picker = fullRect()
        val number = onNodeWithText("${date.day}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val half = picker.width / 7f / 2f
        val cx = number.center.x - picker.left
        val top = number.top - picker.top - 12f
        return Rect(cx - half, top, cx + half, top + 56f)
    }

    private fun PixelMap.count(rect: Rect, test: (Color) -> Boolean): Int {
        var n = 0
        for (y in rect.top.toInt().coerceAtLeast(0) until rect.bottom.toInt().coerceAtMost(height)) {
            for (x in rect.left.toInt().coerceAtLeast(0) until rect.right.toInt().coerceAtMost(width)) {
                if (test(this[x, y])) n++
            }
        }
        return n
    }

    private fun Color.near(other: Color, by: Float): Boolean =
        abs(red - other.red) <= by && abs(green - other.green) <= by && abs(blue - other.blue) <= by && alpha > 0.9f

    private companion object {
        const val Tag = "picker"
    }
}
