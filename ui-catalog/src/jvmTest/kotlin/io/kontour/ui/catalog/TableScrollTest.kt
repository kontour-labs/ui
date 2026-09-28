package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.table.Table
import io.kontour.ui.components.table.TableDefaults
import io.kontour.ui.foundation.Text
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A table scrolled across and down, read off where its cells land.
 *
 * Twenty rows of five 150dp columns in a 400dp scene: 750dp of columns, the
 * first pinned, the rest a 250dp pane that scrolls. Each cell reports where it
 * is, so the checks are about which cells moved, by how much, and which did not.
 */
class TableScrollTest {

    private data class Row(val id: Int)

    private val rows = List(20) { Row(it) }
    private val columns = listOf("Route", "Destination", "Platform", "Departs", "Fare")

    private class Cells {
        val at = HashMap<String, Rect>()
    }

    private fun scene(
        cells: Cells,
        direction: LayoutDirection = LayoutDirection.Ltr,
        withFooter: Boolean = false,
        count: Int = rows.size,
        height: Int = 300,
        stripe: Color = Color.Unspecified,
    ) = Scene(width = 800, height = 800) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Table(
                    items = rows.take(count),
                    modifier = Modifier.fillMaxWidth().height(height.dp).reportBounds { cells.at["table"] = it },
                    key = { it.id },
                    stickyColumns = 1,
                    striped = stripe != Color.Unspecified,
                    colours = if (stripe != Color.Unspecified) {
                        TableDefaults.colours(stripe = stripe)
                    } else {
                        TableDefaults.colours()
                    },
                ) {
                    columns.forEach { title ->
                        column(
                            title,
                            width = 150.dp,
                            footer = if (withFooter && title == "Fare") ({
                                Text("Total", Modifier.reportBounds { cells.at["footer"] = it })
                            }) else null,
                        ) { row ->
                            Text("$title ${row.id}", Modifier.reportBounds { cells.at["$title ${row.id}"] = it })
                        }
                    }
                }
            }
        }
    }

    /**
     * A drag across the rows moves every scrolling column together, and not the
     * pinned one — which is what makes a pinned column worth having.
     */
    @Test
    fun theRestScrollAcrossTogetherPastThePinnedColumn() {
        for (direction in LayoutDirection.entries) {
            val cells = Cells()
            scene(cells, direction).use { scene ->
                scene.frames(4)
                val pinned = cells.at.getValue("Route 1")
                val body = cells.at.getValue("Platform 1").left
                val lower = cells.at.getValue("Platform 3").left
                val towardsEnd = if (direction == LayoutDirection.Ltr) -1 else 1
                scene.drag(Offset(500f, 150f), Offset(500f + towardsEnd * 300f, 150f))
                scene.frames(30)
                assertEquals(pinned, cells.at.getValue("Route 1"), "$direction: the pinned column moved")
                val moved = cells.at.getValue("Platform 1").left - body
                assertTrue(abs(moved) > 100f, "$direction: a 150px drag should scroll the columns; they moved $moved")
                assertEquals(moved, cells.at.getValue("Platform 3").left - lower, "$direction: every row moves as one")
            }
        }
    }

    /** The header is part of the same scroll: dragging on it moves the rows under it. */
    @Test
    fun aDragOnTheHeaderScrollsTheRows() {
        val cells = Cells()
        scene(cells).use { scene ->
            scene.frames(4)
            val before = cells.at.getValue("Platform 1").left
            val header = cells.at.getValue("table").top + 20f
            scene.drag(Offset(600f, header), Offset(300f, header))
            scene.frames(30)
            assertTrue(cells.at.getValue("Platform 1").left < before - 100f, "a drag along the header left the rows where they were")
        }
    }

    /** Down is down: a vertical drag scrolls the rows and does not slide the columns across. */
    @Test
    fun aDragDownTheRowsDoesNotScrollThemAcross() {
        val cells = Cells()
        scene(cells).use { scene ->
            scene.frames(4)
            val across = cells.at.getValue("Platform 1").left
            val down = cells.at.getValue("Platform 1").top
            scene.drag(Offset(500f, 500f), Offset(520f, 200f))
            scene.frames(30)
            assertTrue(cells.at.getValue("Platform 1").top < down - 100f, "the rows should have scrolled up")
            assertEquals(across, cells.at.getValue("Platform 1").left, "and not across")
        }
    }

    /**
     * The footer stays at the bottom of a table given a height, whatever the
     * rows do, and sits straight under the last row of one too short to fill it.
     */
    @Test
    fun theFooterStaysAtTheBottomAndUnderAShortTable() {
        val cells = Cells()
        scene(cells, withFooter = true).use { scene ->
            scene.frames(4)
            val table = cells.at.getValue("table")
            val footer = cells.at.getValue("footer")
            assertTrue(table.bottom - footer.bottom < 80f, "the footer should be at the table's bottom: $footer in $table")
            scene.drag(Offset(500f, 500f), Offset(500f, 200f))
            scene.frames(30)
            assertEquals(footer, cells.at.getValue("footer"), "the footer should not scroll with the rows")
        }

        val short = Cells()
        scene(short, withFooter = true, count = 3, height = 400).use { scene ->
            scene.frames(4)
            val last = short.at.getValue("Fare 2")
            val footer = short.at.getValue("footer")
            assertTrue(footer.top - last.bottom < 80f, "under a short table, the footer follows its last row: $footer after $last")
            assertTrue(short.at.getValue("table").bottom - footer.bottom > 200f, "rather than sitting at the bottom")
        }
    }

    /** A stripe is the row's, pinned and scrolling parts alike. */
    @Test
    fun aStripeRunsUnderThePinnedColumnAndThePane() {
        val stripe = Color(0xFFDDEEFF)
        val cells = Cells()
        lateinit var image: BufferedImage
        scene(cells, stripe = stripe).use { image = it.frames(4) }
        val pinned = cells.at.getValue("Route 1")
        val pane = cells.at.getValue("Platform 1")
        // Beside each cell's text, in its padding, where only the ground shows.
        val inPinned = image.getRGB((pinned.right + 20).toInt(), pinned.center.y.toInt())
        val inPane = image.getRGB((pane.right + 20).toInt(), pane.center.y.toInt())
        val unstriped = image.getRGB((pane.right + 20).toInt(), cells.at.getValue("Platform 2").center.y.toInt())
        assertTrue(close(inPinned, stripe.toArgb()), "the pinned part of row 1 should be striped: ${hex(inPinned)}")
        assertTrue(close(inPane, stripe.toArgb()), "and the scrolling part: ${hex(inPane)}")
        assertTrue(close(unstriped, Color.White.toArgb()), "row 2 is not: ${hex(unstriped)}")
    }

    private fun close(a: Int, b: Int) = listOf(16, 8, 0).all { abs((a shr it and 0xFF) - (b shr it and 0xFF)) < 8 }
    private fun hex(p: Int) = "#" + (p and 0xFFFFFF).toString(16).padStart(6, '0')
}
