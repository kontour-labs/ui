package io.kontour.ui.catalog

import io.kontour.ui.locale.LocaleFormats
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.ActivityCalendar
import io.kontour.ui.components.datetime.ActivityCalendarColours
import io.kontour.ui.components.datetime.ActivityLegend
import io.kontour.ui.components.datetime.ActivityMark
import io.kontour.ui.foundation.SystemIcons
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An activity calendar's cells, read off the pixels.
 *
 * A year of 12dp cells 3dp apart is 792dp, far wider than the 300dp scene, so
 * these also check the calendar opens on its latest week. At density two a cell
 * is 24px and the pitch 30px, and the cell for [end] — Friday, the fifth row of
 * a week from Monday — sits in the last column against the viewport's end edge.
 */
class ActivityCalendarPixelTest {

    private val end = LocalDate(2026, 6, 5)
    private val colours = ActivityCalendarColours(
        levels = listOf(Empty, Level1, Level2),
        label = Color.Black,
        selection = Ring,
        today = Color.Gray,
    )

    @Test
    fun aBusyDayIsTheBusiestShadeAndTheCalendarOpensOnIt() {
        for (direction in LayoutDirection.entries) {
            val (image, bounds) = render(direction, selected = null)
            val (x, y) = centreOfEnd(bounds, direction)
            assertTrue(
                image.near(x, y, Level2),
                "$direction: the busiest day, in the latest week, should be at the end edge in the " +
                    "busiest shade — was ${hex(image.getRGB(x, y))}",
            )
            val (quietX, quietY) = x to y - Pitch.toInt()
            assertTrue(image.near(quietX, quietY, Empty), "$direction: the day before had nothing: ${hex(image.getRGB(quietX, quietY))}")
        }
    }

    @Test
    fun thePickedDayHasARingRoundIt() {
        val (image, bounds) = render(LayoutDirection.Ltr, selected = end)
        val (x, y) = centreOfEnd(bounds, LayoutDirection.Ltr)
        // In the gap between it and the one above, where the ring runs: 2dp
        // wide, its middle 2dp out from the cell.
        val above = y - (Cell / 2).toInt() - 4
        assertTrue(image.near(x, above, Ring), "no ring above the picked day: ${hex(image.getRGB(x, above))}")
        assertTrue(image.near(x, y, Level2), "the ring should leave the cell its colour")
    }

    /**
     * Marks drawn over their cells: a dog-ear in the top end corner, a dot under
     * the middle, a fill in place of the shade, and an icon in the middle — right
     * to left, the dog-ear is in the top left.
     */
    @Test
    fun marksAreDrawnWhereTheySay() {
        for (direction in LayoutDirection.entries) {
            val (image, bounds) = render(direction, selected = null) { date, _ ->
                when (date) {
                    end -> listOf(ActivityMark(corner = Ear, dot = Dot))
                    end.minus(DatePeriod(days = 1)) -> listOf(ActivityMark(fill = Fill, icon = SystemIcons.Star, contentColour = Color.Black))
                    else -> emptyList()
                }
            }
            val (x, y) = centreOfEnd(bounds, direction)
            val top = y - (Cell / 2).toInt()
            val endward = if (direction == LayoutDirection.Ltr) 1 else -1
            val farCorner = x + endward * (Cell / 2 - 4).toInt()
            val nearCorner = x - endward * (Cell / 2 - 4).toInt()
            assertTrue(image.near(farCorner, top + 4, Ear), "$direction: the top end corner is folded: ${hex(image.getRGB(farCorner, top + 4))}")
            assertTrue(image.near(nearCorner, top + 4, Level2), "$direction: and the other one is not: ${hex(image.getRGB(nearCorner, top + 4))}")
            assertTrue(image.near(x, top + 19, Dot), "$direction: a dot under the middle: ${hex(image.getRGB(x, top + 19))}")
            // Thursday, the row above.
            val thursday = y - Pitch.toInt()
            assertTrue(image.near(nearCorner, thursday, Fill), "$direction: a fill in place of the shade")
            // A thin outline glyph at 14px is antialiased grey more than black.
            val inked = (-7..7).sumOf { dx ->
                (-7..7).count { dy ->
                    val p = image.getRGB(x + dx, thursday + dy)
                    ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3 < 140
                }
            }
            assertTrue(inked > 10, "$direction: an icon in the middle of the cell, $inked dark pixels")
        }
    }

    /**
     * Several marks share a day: a second corner folds the other top corner, two
     * dots sit side by side, and two icons sit in the two halves of the cell.
     */
    @Test
    fun severalMarksOnOneDayShareTheCell() {
        for (direction in LayoutDirection.entries) {
            val (image, bounds) = render(direction, selected = null) { date, _ ->
                when (date) {
                    end -> listOf(
                        ActivityMark(corner = Ear, dot = Dot),
                        ActivityMark(corner = Ear2, dot = Dot2),
                    )
                    end.minus(DatePeriod(days = 1)) -> listOf(
                        ActivityMark(fill = Fill, icon = SystemIcons.Star, contentColour = Color.Black),
                        ActivityMark(icon = SystemIcons.Star),
                    )
                    else -> emptyList()
                }
            }
            val (x, y) = centreOfEnd(bounds, direction)
            val top = y - (Cell / 2).toInt()
            val endward = if (direction == LayoutDirection.Ltr) 1 else -1
            val farCorner = x + endward * (Cell / 2 - 4).toInt()
            val nearCorner = x - endward * (Cell / 2 - 4).toInt()
            assertTrue(image.near(farCorner, top + 4, Ear), "$direction: the first corner, top end: ${hex(image.getRGB(farCorner, top + 4))}")
            assertTrue(image.near(nearCorner, top + 4, Ear2), "$direction: the second, top start: ${hex(image.getRGB(nearCorner, top + 4))}")
            // Two dots, the first on the start side: centres about 2.6px either side.
            val startDot = x - endward * 3
            val endDot = x + endward * 2
            assertTrue(image.near(startDot, top + 19, Dot), "$direction: the first dot, startward: ${hex(image.getRGB(startDot, top + 19))}")
            assertTrue(image.near(endDot, top + 19, Dot2), "$direction: the second dot, endward: ${hex(image.getRGB(endDot, top + 19))}")
            // Two icons, one in each half of Thursday's cell.
            val thursday = y - Pitch.toInt()
            fun inkIn(from: Int, to: Int) = (from..to).sumOf { px ->
                (-5..5).count { dy ->
                    val p = image.getRGB(px, thursday + dy)
                    ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3 < 140
                }
            }
            val left = inkIn(x - 10, x - 2)
            val right = inkIn(x + 2, x + 10)
            assertTrue(left > 4 && right > 4, "$direction: an icon in each half of the cell, $left and $right dark pixels")
        }
    }

    /**
     * The gradient legend is one bar blended through the shades — colours between
     * them, which a row of squares never has — from empty at the start side.
     */
    @Test
    fun theGradientLegendBlendsFromEmptyAtTheStart() {
        for (direction in LayoutDirection.entries) {
            val (steps, stepsBounds) = render(direction, selected = null, legend = ActivityLegend.Steps)
            val (bar, barBounds) = render(direction, selected = null, legend = ActivityLegend.Gradient)
            val between = Color(0xFF4C994C)
            assertTrue(legendRow(steps, stepsBounds).none { steps.near(it.first, it.second, between) }, "$direction: squares have nothing between their shades")
            val row = legendRow(bar, barBounds)
            val mid = row.firstOrNull { bar.near(it.first, it.second, between) }
            assertTrue(mid != null, "$direction: a gradient should pass through colours between its shades")
            val darkest = row.filter { bar.near(it.first, it.second, Level2) }.map { it.first }
            val lightest = row.filter { bar.near(it.first, it.second, Empty) }.map { it.first }
            assertTrue(darkest.isNotEmpty() && lightest.isNotEmpty(), "$direction: the bar runs from empty to full")
            if (direction == LayoutDirection.Ltr) {
                assertTrue(lightest.min() < darkest.max(), "LTR: empty on the left, full on the right")
            } else {
                assertTrue(lightest.max() > darkest.min(), "RTL: empty on the right, full on the left")
            }
        }
    }

    /** The pixels of the row through the middle of the legend, under the grid. */
    private fun legendRow(image: BufferedImage, bounds: Rect): List<Pair<Int, Int>> {
        val gridBottom = (bounds.top + 7 * Pitch).toInt()
        val y = (bounds.bottom.toInt() - 1 downTo gridBottom).first { y ->
            (bounds.left.toInt() until bounds.right.toInt()).any { image.near(it, y, Level2) }
        } - 4
        return (bounds.left.toInt() until bounds.right.toInt()).map { it to y }
    }

    private fun render(
        direction: LayoutDirection,
        selected: LocalDate?,
        legend: ActivityLegend = ActivityLegend.None,
        marksFor: ((LocalDate, Int) -> List<ActivityMark>)? = null,
    ): Pair<BufferedImage, Rect> {
        var bounds = Rect.Zero
        lateinit var image: BufferedImage
        Scene(width = 300, height = 300) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    ActivityCalendar(
                        activity = mapOf(end to 9, end.minus(DatePeriod(days = 2)) to 1),
                        end = end,
                        modifier = Modifier.reportBounds { bounds = it },
                        selected = selected,
                        marksFor = marksFor,
                        colours = colours,
                        cellSize = 12.dp,
                        showMonthLabels = false,
                        showWeekdayLabels = false,
                        legend = legend,
                        formats = LocaleFormats.english(),
                    )
                }
            }
        }.use { image = it.frames(4) }
        return image to bounds
    }

    /** The middle of [end]'s cell: the last column, against the end edge, fifth row. */
    private fun centreOfEnd(bounds: Rect, direction: LayoutDirection): Pair<Int, Int> {
        val x = if (direction == LayoutDirection.Ltr) bounds.right - Cell / 2 else bounds.left + Cell / 2
        val y = bounds.top + 4 * Pitch + Cell / 2
        return x.toInt() to y.toInt()
    }

    private fun BufferedImage.near(x: Int, y: Int, colour: Color): Boolean {
        val p = getRGB(x, y)
        val c = colour.toArgb()
        return listOf(16, 8, 0).all { shift -> abs((p shr shift and 0xFF) - (c shr shift and 0xFF)) < 12 }
    }

    private fun hex(p: Int) = "#" + (p and 0xFFFFFF).toString(16).padStart(6, '0')

    private companion object {
        val Empty = Color(0xFFEEEEEE)
        val Level1 = Color(0xFF88CC88)
        val Level2 = Color(0xFF116611)
        val Ring = Color(0xFFCC0000)
        val Ear = Color(0xFFCC00CC)
        val Dot = Color(0xFF0000CC)
        val Ear2 = Color(0xFF00CCCC)
        val Dot2 = Color(0xFFCC6600)
        val Fill = Color(0xFFEECC00)
        const val Cell = 24f
        const val Pitch = 30f
    }
}
