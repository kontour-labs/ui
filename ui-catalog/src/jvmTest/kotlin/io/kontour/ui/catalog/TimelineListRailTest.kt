package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.TimelineList
import io.kontour.ui.components.display.TimelineDefaults
import io.kontour.ui.components.display.TimelineListDefaults
import io.kontour.ui.components.display.TimelineListScope
import io.kontour.ui.components.display.TimelineListStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A stop list's rail, read off the pixels down the column it runs in.
 *
 * Each row draws half the leg above its node and half the one below, so the
 * things that can go wrong are all at the seams between rows: a gap where the
 * halves fail to meet, two dots touching where they meet, a node that sits
 * somewhere other than its label's first line, and progress changing colour in
 * the wrong place.
 */
class TimelineListRailTest {

    /** Rows of every height, and the rail runs unbroken from the first node to the last. */
    @Test
    fun aSolidRailIsUnbrokenAcrossRowsOfAnyHeight() {
        for (style in TimelineListStyle.entries) {
            for (direction in LayoutDirection.entries) {
                val image = render(style, direction) { mixedRows(ConnectorStyle.Solid) }
                val gaps = blanks(image, railX(image, direction))
                val widest = gaps.maxOrNull() ?: 0
                assertTrue(
                    widest <= NodeGapPx + Antialias,
                    "$style $direction: the rail has a gap of ${widest}px, and the only gaps it " +
                        "should have are the ${NodeGapPx}px either side of each node",
                )
            }
        }
    }

    /**
     * Dotted legs meet at the seam with one gap between their last dots, not two
     * dots touching and not a gap twice the size.
     */
    @Test
    fun dotsMeetAcrossASeamAtTheirOwnSpacing() {
        for (style in TimelineListStyle.entries) {
            val image = render(style, LayoutDirection.Ltr) { mixedRows(ConnectorStyle.Dotted) }
            val x = railX(image, LayoutDirection.Ltr)
            val runs = inkRuns(image, x)
            // The nodes are the long runs; every other run is one dot.
            val dots = runs.filter { it.last - it.first + 1 < NodePx - Antialias }
            assertTrue(dots.size > 6, "$style: expected a dotted rail, found runs $runs")
            val merged = dots.filter { it.last - it.first + 1 > DotPx + Antialias }
            assertTrue(merged.isEmpty(), "$style: dots touching at $merged — two halves of a leg doubled up")
            val gaps = blanks(image, x)
            assertTrue(
                gaps.max() <= DotPx * 2 + Antialias,
                "$style: a gap of ${gaps.max()}px in a dotted rail whose pitch is about ${DotPx * 2}px",
            )
        }
    }

    /**
     * At 1.5 stops the leg from the second stop to the third is half travelled.
     * Still, so the band running along the rest of the leg is not in the picture.
     */
    @Test
    fun progressChangesColourWhereOneRowHandsTheLegToTheNext() {
        val labels = arrayOfNulls<Rect>(3)
        lateinit var image: BufferedImage
        Scene(width = 400, height = 400, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                RecordRail()
                TimelineList(
                    progress = 1.5f,
                    colours = TimelineDefaults.colours(node = Color.Black, rail = Color.Black, progress = Red),
                ) {
                    listOf("Perth", "Walk", "Quay").forEachIndexed { i, name ->
                        item { label { Text(name, Modifier.reportBounds { labels[i] = it }) } }
                    }
                }
            }
        }.use { image = it.frames(6) }
        val x = railX(image, LayoutDirection.Ltr)
        val second = labels[1]!!.center.y
        val third = labels[2]!!.center.y
        val travelled = image.getRGB(x, (second + (third - second) * 0.3f).toInt())
        val ahead = image.getRGB(x, (second + (third - second) * 0.7f).toInt())
        assertTrue(isRed(travelled), "a quarter of the way along the leg should be travelled: ${hex(travelled)}")
        assertTrue(isDark(ahead) && !isRed(ahead), "three quarters along should not be yet: ${hex(ahead)}")
        val before = image.getRGB(x, ((labels[0]!!.center.y + second) / 2).toInt())
        assertTrue(isRed(before), "the whole leg before should be travelled: ${hex(before)}")
    }

    /**
     * The node sits on the middle of the label's first line, whatever is above
     * the label and however many lines it runs to.
     *
     * The text trims its leading equally above its first line and below its
     * last, so a one-line label's own height is a line as drawn, and the middle
     * of any label's first line is half that below its top.
     *
     * The long label runs past the bottom of the scene and is cut short, which is
     * deliberate: a clipped label's height no longer says how much was trimmed,
     * and the node went to its top.
     */
    @Test
    fun theNodeSitsOnTheLabelsFirstLine() {
        val labels = arrayOfNulls<Rect>(3)
        lateinit var image: BufferedImage
        Scene(width = 300, height = 500) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                RecordRail()
                TimelineList(colours = TimelineDefaults.colours(node = Color.Black, rail = Color.White)) {
                    item { label { Text("Perth", Modifier.reportBounds { labels[0] = it }) } }
                    item {
                        overline { +"Route 950" }
                        label { Text("Elizabeth Quay", Modifier.reportBounds { labels[1] = it }) }
                        supporting { +"Stand C" }
                    }
                    item {
                        label {
                            Text(
                                "A stop with a long enough name to run onto a second line here",
                                Modifier.reportBounds { labels[2] = it },
                            )
                        }
                        supporting { +"And a second line under it" }
                    }
                }
            }
        }.use { image = it.frames(6) }
        val x = railX(image, LayoutDirection.Ltr)
        val nodes = inkRuns(image, x).filter { it.last - it.first + 1 >= NodePx - Antialias }
        assertTrue(nodes.size == 3, "expected three nodes, found $nodes")
        val oneLine = labels[0]!!.height
        nodes.forEachIndexed { i, node ->
            val expected = labels[i]!!.top + oneLine / 2
            assertTrue(
                abs(node.centre() - expected) <= 1f,
                "node $i is centred at ${node.centre()}, and its label's first line at $expected",
            )
        }
    }

    private fun TimelineListScope.mixedRows(style: ConnectorStyle) {
        item(connector = style) { +"Perth Station" }
        item(connector = style) {
            overline { +"Route 950" }
            +"Elizabeth Quay"
            supporting { +"Stand C" }
        }
        item(connector = style) {
            +"A stop with a long enough name to run onto a second line in this width"
            supporting { +"Platform 1" }
        }
        item(connector = style) { +"Perth Busport" }
    }

    private fun render(
        style: TimelineListStyle,
        direction: LayoutDirection,
        content: TimelineListScope.() -> Unit,
    ): BufferedImage {
        lateinit var image: BufferedImage
        Scene(width = 360, height = 700) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                RecordRail()
                    TimelineList(
                        style = style,
                        colours = TimelineDefaults.colours(node = Color.Black, rail = Color.Black),
                        content = content,
                    )
                }
            }
        }.use { image = it.frames(6) }
        return image
    }

    /**
     * The column the rail runs down: the row's small inset and half the gutter,
     * at density two — read from the theme by [RecordRail] as the scene composes.
     */
    private var railPx = 0

    @Composable
    private fun RecordRail() {
        railPx = ((Theme.spacing.xs + TimelineListDefaults.GutterWidth / 2).value * 2).toInt()
    }

    private fun railX(image: BufferedImage, direction: LayoutDirection): Int =
        if (direction == LayoutDirection.Ltr) railPx else image.width - railPx - 1

    /** The inked stretches down column [x]. */
    private fun inkRuns(image: BufferedImage, x: Int): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (y in 0 until image.height) {
            val inked = isDark(image.getRGB(x, y))
            if (inked && start < 0) start = y
            if (!inked && start >= 0) {
                runs += start until y
                start = -1
            }
        }
        if (start >= 0) runs += start until image.height
        return runs
    }

    /** The lengths of the blank stretches between the first ink and the last down column [x]. */
    private fun blanks(image: BufferedImage, x: Int): List<Int> =
        inkRuns(image, x).zipWithNext { a, b -> b.first - a.last - 1 }

    private fun isDark(p: Int) = ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3 < 128 || isRed(p)
    private fun isRed(p: Int) = (p shr 16 and 0xFF) > 180 && (p shr 8 and 0xFF) < 80 && (p and 0xFF) < 80
    private fun hex(p: Int) = "#" + (p and 0xFFFFFF).toString(16).padStart(6, '0')

    private companion object {
        val Red = Color(0xFFE00000)
        const val NodeGapPx = 4
        const val NodePx = 24
        const val DotPx = 4
        const val Antialias = 2
    }
}
