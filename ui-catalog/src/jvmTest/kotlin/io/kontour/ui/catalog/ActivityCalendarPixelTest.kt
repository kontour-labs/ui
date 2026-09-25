package io.kontour.ui.catalog

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
import io.kontour.ui.components.datetime.DateTimeFormats
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

    private fun render(direction: LayoutDirection, selected: LocalDate?): Pair<BufferedImage, Rect> {
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
                        colours = colours,
                        cellSize = 12.dp,
                        monthLabels = false,
                        weekdayLabels = false,
                        legend = false,
                        formats = DateTimeFormats(firstDayOfWeek = DayOfWeek.MONDAY),
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
        const val Cell = 24f
        const val Pitch = 30f
    }
}
