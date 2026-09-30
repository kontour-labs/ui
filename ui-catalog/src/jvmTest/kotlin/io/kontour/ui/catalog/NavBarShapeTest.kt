package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Basket
import com.composables.icons.tabler.outline.Car
import com.composables.icons.tabler.outline.User
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavBarDefaults
import io.kontour.ui.nav.NavBarItemWidth
import io.kontour.ui.nav.NavBarMarker
import io.kontour.ui.nav.NavBarStyle
import io.kontour.ui.nav.NavDefaults
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A nav bar in another shape, with destinations in another size: a rounded
 * rectangle, cells that share it, and a highlight around the whole current cell.
 *
 * Asked for from a screenshot of a tab bar: *"I'd like the ability to set
 * different sizes and shapes for the nav items, and floating nav bar."*
 *
 * Read from the pixels, on a red page with a white bar and a blue marker.
 */
class NavBarShapeTest {

    private companion object {
        const val Density = 2f
        val Page = Color(0xFFFF0000)
        val Marker = Color(0xFF0000FF)

        /** A dp either way: rounding, on a bar laid out in whole pixels. */
        const val Slack = 1f * Density
    }

    private val cells = mutableListOf(Rect.Zero, Rect.Zero, Rect.Zero)

    private fun render(
        style: NavBarStyle = NavBarStyle.Floating,
        shape: Shape? = null,
        itemWidth: NavBarItemWidth = NavBarItemWidth.Natural,
        marker: @Composable () -> NavBarMarker = { NavBarDefaults.glyphMarker() },
        showLabels: Boolean = true,
    ): BufferedImage = Scene(width = 800, height = 300, density = Density, reduceMotion = true) {
        Box(Modifier.fillMaxSize().background(Page), contentAlignment = Alignment.BottomCenter) {
            NavBar(
                style = style,
                shape = shape,
                showLabels = showLabels,
                itemWidth = itemWidth,
                marker = marker(),
                colours = NavDefaults.colours(
                    container = Color.White,
                    content = Color.Black,
                    indicator = Marker,
                    selectedContent = Color.White,
                ),
            ) {
                item(true, {}, key = "drive", icon = Tabler.Outline.Car, modifier = Modifier.reportBounds { cells[0] = it }) { +"Drive" }
                item(false, {}, key = "shop", icon = Tabler.Outline.Basket, modifier = Modifier.reportBounds { cells[1] = it }) { +"Shop" }
                item(false, {}, key = "you", icon = Tabler.Outline.User, modifier = Modifier.reportBounds { cells[2] = it }) { +"You" }
            }
        }
    }.use { it.frames(10) }

    private fun isMarker(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return b > 200 && r < 80 && g < 80
    }

    /** The white bar, and not the red page or the bar's shadow on it. */
    private fun isBar(argb: Int): Boolean = ((argb shr 8) and 0xFF) > 150

    /** The marker's extent, in pixels, anywhere in the image. */
    private fun markerBox(image: BufferedImage): Rect {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (isMarker(image.getRGB(x, y))) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + 1)
                bottom = maxOf(bottom, y + 1)
            }
        }
        assertTrue(right > 0, "no marker was drawn at all")
        return Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    @Test
    fun equalCellsShareTheBarAndTouch() {
        render(itemWidth = NavBarItemWidth.Equal)
        val widths = cells.map { it.width }
        for (w in widths) {
            assertTrue(abs(w - widths.average()) <= Slack, "equal cells came out ${widths}px wide")
        }
        for (i in 0 until 2) {
            val gap = cells[i + 1].left - cells[i].right
            assertTrue(abs(gap) <= Slack, "equal cells should touch; ${gap}px between cells $i and ${i + 1}")
        }

        // The control: natural widths are not equal and do not touch, so the
        // above measured the option rather than the words.
        render(itemWidth = NavBarItemWidth.Natural)
        val gaps = (0 until 2).map { cells[it + 1].left - cells[it].right }
        assertTrue(gaps.all { it > Slack }, "natural destinations touched too — ${gaps}px — so equal was not measured")
    }

    @Test
    fun aDestinationMarkerCoversTheWholeCell() {
        val box = markerBox(render(itemWidth = NavBarItemWidth.Equal, marker = { NavBarDefaults.destinationMarker() }))
        val cell = cells[0]
        assertTrue(
            abs(box.left - cell.left) <= Slack && abs(box.right - cell.right) <= Slack,
            "the marker runs ${box.left}–${box.right}px across a cell of ${cell.left}–${cell.right}px",
        )
        assertTrue(
            abs(box.top - cell.top) <= Slack && abs(box.bottom - cell.bottom) <= Slack,
            "the marker runs ${box.top}–${box.bottom}px down a cell of ${cell.top}–${cell.bottom}px",
        )
    }

    /** How much of the marker's colour there is along one row. */
    private fun inkAcross(image: BufferedImage, y: Float): Int =
        (0 until image.width).count { isMarker(image.getRGB(it, y.toInt())) }

    /**
     * By default the marker follows a floating bar's capsule: a capsule itself,
     * as far from the bar's end as from its top.
     *
     * Reported as the new defaults looking *"really terrible with labels
     * enabled"*: labels make the bar tall, its ends round, and a marker in the
     * theme's medium corner sat inside them as a box in a pill, closer to the
     * rounding at the corners than anywhere else.
     */
    @Test
    fun byDefaultTheMarkerIsACapsuleConcentricWithAFloatingBar() {
        val image = render(itemWidth = NavBarItemWidth.Equal, marker = { NavBarDefaults.destinationMarker() })
        val box = markerBox(image)
        // Four dp in from its top a capsule this tall is 30dp narrower than its
        // middle; the medium corner, about 12.
        val across = inkAcross(image, box.top + 4f * Density)
        assertTrue(
            across < box.width - 24f * Density,
            "4dp into the marker it is ${across}px across, of ${box.width}px — its ends are not a capsule's",
        )
        // The bar's own white, not the first pixel off the page: its shadow falls
        // further below than beside it.
        fun isBar(x: Int, y: Int) = image.getRGB(x, y).let { (it shr 16 and 0xFF) > 240 && (it and 0xFF) > 240 }
        val barLeft = (0 until image.width).first { isBar(it, box.center.y.toInt()) }
        val barTop = (0 until image.height).first { isBar(box.center.x.toInt(), it) }
        val end = box.left - barLeft
        val top = box.top - barTop
        assertTrue(abs(end - top) <= Slack * 2, "the marker is ${end}px from the bar's end and ${top}px from its top")
    }

    /** And in a docked bar, which has no corners to follow, the theme's medium corner. */
    @Test
    fun inADockedBarTheMarkerHasTheMediumCorner() {
        val image = render(
            style = NavBarStyle.Docked,
            itemWidth = NavBarItemWidth.Equal,
            marker = { NavBarDefaults.destinationMarker() },
        )
        val box = markerBox(image)
        val across = inkAcross(image, box.top + 4f * Density)
        assertTrue(
            across > box.width - 24f * Density,
            "4dp into a docked bar's marker it is ${across}px across, of ${box.width}px — a capsule, not a corner",
        )
    }

    @Test
    fun aGlyphMarkerTakesTheSizeItIsGiven() {
        val image = render(marker = { NavBarDefaults.glyphMarker(size = DpSize(64.dp, 32.dp)) })
        val box = markerBox(image)
        assertTrue(abs(box.width - 64 * Density) <= Slack, "a 64dp marker came out ${box.width / Density}dp wide")
        assertTrue(abs(box.height - 32 * Density) <= Slack, "a 32dp marker came out ${box.height / Density}dp tall")

        // And the default is still the 40dp circle.
        val circle = markerBox(render())
        assertTrue(abs(circle.width - 40 * Density) <= Slack, "the default marker came out ${circle.width / Density}dp wide")
    }

    @Test
    fun aFloatingBarTakesTheShapeItIsGiven() {
        /** The pixel 2dp in from the bar's top-left corner: the bar's, or the page's. */
        fun cornerIsBar(image: BufferedImage): Boolean {
            val midY = cells[0].center.y.toInt()
            val left = (0 until image.width).first { isBar(image.getRGB(it, midY)) }
            // The middle destination is not the current one: white down to the bar's top.
            val midX = image.width / 2
            val top = (0 until image.height).first { isBar(image.getRGB(midX, it)) }
            return isBar(image.getRGB(left + (2 * Density).toInt(), top + (2 * Density).toInt()))
        }
        assertTrue(cornerIsBar(render(shape = RoundedCornerShape(4.dp))), "a 4dp-cornered bar left its corner to the page")
        assertTrue(!cornerIsBar(render()), "the default capsule filled its corner, so the shape was not measured")
    }
}
