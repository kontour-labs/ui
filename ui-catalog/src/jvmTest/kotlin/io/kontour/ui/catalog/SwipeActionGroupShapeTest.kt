package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Trash
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberSwipeActionsState
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.shape
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A row in a group keeps its grouped corners, and its swipe actions do not.
 *
 * Reported as *"when multiple list items are joined together (i.e. not
 * "only"), their swipe actions are weird shapes. The swipe action should always
 * be the shape of the regular one."* The actions took the row's shape whole, so a
 * middle row's were nearly square and a first row's were rounded only on top.
 *
 * Read from the pixels of a middle row opened on its one action: the row's own
 * corner beside the action is still square — the control, showing the grouped
 * shape reached the row — and the action's corner is round, as an action on a row
 * alone is.
 */
class SwipeActionGroupShapeTest {

    private companion object {
        const val Width = 720
        const val Height = 240
        const val Margin = 16
        const val Density = 2
        const val RowHeight = 64
        val Page = Color.White
        val Action = Color(0xFFCC2222)

        /** Diagonally in from a corner: inside a 4dp corner, outside a container's. */
        const val In = 4

        /** Far enough in from the action's edge to be past its corner. */
        const val Clear = 60
    }

    private fun open(position: GroupPosition): BufferedImage =
        Scene(width = Width, height = Height, density = Density.toFloat(), reduceMotion = true) {
            val grouped = position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical)
            Box(Modifier.fillMaxSize().background(Page).padding(Margin.dp)) {
                SwipeActions(
                    end = listOf(
                        SwipeAction(label = "Remove", icon = Tabler.Outline.Trash, onAction = {}, background = Action),
                    ),
                    state = rememberSwipeActionsState(initialValue = SwipeValue.End),
                    shape = grouped,
                    modifier = Modifier.fillMaxWidth().height(RowHeight.dp),
                ) {
                    ListItem(position = position) { +"Perth Underground" }
                }
            }
        }.use { it.frames(40) }

    private fun BufferedImage.at(x: Int, y: Int): Color = Color(getRGB(x, y)).copy(alpha = 1f)

    private fun isAction(c: Color) = c.red > 0.7f && c.green < 0.3f && c.blue < 0.3f

    /**
     * The action's outer top corner, and the row's top corner facing it — on the
     * row's own top line, which is the list item's and not the swipe row's: the
     * item is shorter than the 64dp given it and sits at the top.
     */
    private fun corners(image: BufferedImage): Pair<Color, Color> {
        val midY = (Margin + RowHeight / 2) * Density
        // The action: the red run on the middle line, from the screen edge in.
        val right = (image.width - 1 downTo 0).first { isAction(image.at(it, midY)) }
        // Its top, read well clear of the corner and above the icon.
        val top = (0 until image.height).first { isAction(image.at(right - Clear, it)) }
        val actionCorner = image.at(right - In, top + In)
        // The row: slid off its own leading edge and cut off there, so it runs
        // from the margin to the first of the page. From the screen's left, as
        // this read it while a swiped row drew past its edge, finds the page
        // in the margin at once: see `SwipeActionClipTest`.
        val rowRight = (Margin * Density until image.width).first { image.at(it, midY) == Page } - 1
        val rowCorner = image.at(rowRight - In, top + In)
        return actionCorner to rowCorner
    }

    @Test
    fun aMiddleRowsActionIsRoundedAsARowAloneWouldBe() {
        val (action, row) = corners(open(GroupPosition.Middle))
        assertTrue(
            row != Page,
            "the middle row's own corner beside its action is page — the grouped shape never reached the row, so this measured nothing",
        )
        assertEquals(Page, action, "the middle row's action is square at the corner, taking the row's grouped shape")
    }

    @Test
    fun aFirstRowsActionIsRoundedAtTheBottomToo() {
        val image = open(GroupPosition.First)
        val midY = (Margin + RowHeight / 2) * Density
        val right = (image.width - 1 downTo 0).first { isAction(image.at(it, midY)) }
        val bottom = (image.height - 1 downTo 0).first { isAction(image.at(right - Clear, it)) }
        assertEquals(
            Page,
            image.at(right - In, bottom - In),
            "a first row's action is square at the bottom, where the row meets the next one",
        )
    }

    @Test
    fun aRowAlonesActionIsUnchanged() {
        val (action, row) = corners(open(GroupPosition.Only))
        assertEquals(Page, action)
        assertEquals(Page, row, "a row alone is round at the corner beside its action")
    }
}
