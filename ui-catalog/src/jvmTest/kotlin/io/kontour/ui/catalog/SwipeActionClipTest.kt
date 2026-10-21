package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density as UnitDensity
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Archive
import io.kontour.icons.tabler.outline.Pin
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberSwipeActionsState
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A swiped row draws inside its own row, wherever the row is placed.
 *
 * Reported from the messages app's two panes: a chat swiped open on its Pin
 * action carried its avatar, its name and its time past the end of the list
 * pane and over the conversation beside it. The row slides inside
 * `SwipeActions` and nothing in the row clipped the slide, so it was cut off
 * only by whatever happened to stand round it: the edge of the screen on a
 * phone, and nothing at all in a pane, a card or a dialog narrower than the
 * window.
 *
 * Read from the pixels: a column of list on a coloured ground, a row in it
 * swiped open each way, and the ground on both sides of the column the same
 * colour from top to bottom while it is.
 */
class SwipeActionClipTest {

    private companion object {
        const val Width = 720
        const val Height = 200
        const val Density = 2
        const val ListWidth = 200
        const val Inset = 16
        val Beside = Color(0xFF2266CC)
        val Page = Color.White
        val Pin = Color(0xFF8822CC)
        val Archive = Color(0xFFCC2222)
    }

    private fun swiped(to: SwipeValue): BufferedImage =
        Scene(width = Width, height = Height, density = Density.toFloat(), reduceMotion = true) {
            // The neighbours are the ground, not boxes drawn after the column,
            // as a pane beside a list is: a box drawn on top would cover the
            // row whether it was clipped or not.
            Box(Modifier.fillMaxSize().background(Beside)) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .width(ListWidth.dp)
                        .fillMaxHeight()
                        .background(Page)
                        .padding(vertical = Inset.dp),
                ) {
                    SwipeActions(
                        start = listOf(SwipeAction(label = "Pin", icon = Tabler.Outline.Pin, onAction = {}, background = Pin)),
                        end = listOf(
                            SwipeAction(label = "Archive", icon = Tabler.Outline.Archive, onAction = {}, background = Archive),
                        ),
                        state = rememberSwipeActionsState(initialValue = to),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ListItem(onClick = {}) {
                            +"Sam Whitlock"
                            supporting { +"See you at the station" }
                            trailing { +"Yesterday" }
                        }
                    }
                }
            }
        }.use { it.frames(40) }

    private fun BufferedImage.at(x: Int, y: Int): Color = Color(getRGB(x, y)).copy(alpha = 1f)

    private fun near(a: Color, b: Color) =
        abs(a.red - b.red) < 0.1f && abs(a.green - b.green) < 0.1f && abs(a.blue - b.blue) < 0.1f

    /** The column of list, in pixels: centred, [ListWidth] wide. */
    private val listLeft = (Width - ListWidth * Density) / 2
    private val listRight = listLeft + ListWidth * Density

    /** Every pixel of the ground on either side of the column that is not [Beside]. */
    private fun BufferedImage.drawnOnTheGround(): List<Pair<Int, Int>> = buildList {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x in listLeft until listRight) continue
                if (at(x, y) != Beside) add(x to y)
            }
        }
    }

    private fun BufferedImage.shows(colour: Color): Boolean =
        (0 until height).any { y -> (listLeft until listRight).any { x -> near(at(x, y), colour) } }

    private fun assertInsideItsRow(to: SwipeValue, action: Color, towards: String) {
        val image = swiped(to)
        assertTrue(
            image.shows(action),
            "the row's action never showed, so it was not swiped open and this measured nothing",
        )
        val outside = image.drawnOnTheGround()
        assertTrue(
            outside.isEmpty(),
            "a row swiped $towards drew ${outside.size} pixels outside its column, the first at " +
                "${outside.firstOrNull()}: the slide is not clipped to the row, and a row in a pane draws over the pane beside it",
        )
    }

    @Test
    fun aRowSwipedOpenOnItsStartActionsStaysInItsColumn() =
        assertInsideItsRow(SwipeValue.Start, Pin, "towards the trailing edge")

    @Test
    fun aRowSwipedOpenOnItsEndActionsStaysInItsColumn() =
        assertInsideItsRow(SwipeValue.End, Archive, "towards the leading edge")

    /**
     * A row the keyboard has reached shows its focus ring, its sides included.
     *
     * Found in the 0.5.0 review, 2026-10-21, as the ring's sides cut away by
     * the clip that keeps a swiped row in its row. The whole ring had gone,
     * and since before that clip: the row is clipped to its shape where it
     * slides, and a `ListItem` draws its ring outside its bounds. Inside a
     * `SwipeActions` it draws it just inside them, as the actions do theirs.
     * Read just inside the row, half way down it, on each side.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aFocusedRowShowsItsRingSidesAndAll() = runComposeUiTest {
        var ring = Color.Unspecified
        var row = Rect.Zero
        setContent {
            CompositionLocalProvider(LocalDensity provides UnitDensity(Density.toFloat())) {
                KontourTheme {
                    ring = Theme.colours.focusRing
                    CompositionLocalProvider(LocalInputModality provides InputModality.Keyboard) {
                        Box(Modifier.width(Width.dp).height(Height.dp).background(Beside)) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .width(ListWidth.dp)
                                    .fillMaxHeight()
                                    .background(Page)
                                    .padding(vertical = Inset.dp),
                            ) {
                                SwipeActions(
                                    end = listOf(
                                        SwipeAction(label = "Archive", icon = Tabler.Outline.Archive, onAction = {}, background = Archive),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    ListItem(onClick = {}, modifier = Modifier.reportBounds { row = it }) {
                                        +"Sam Whitlock"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Sam Whitlock").requestFocus()
        waitForIdle()
        onNodeWithText("Sam Whitlock").assertIsFocused()
        val image = onRoot().captureToImage().toAwtImage()
        val y = row.center.y.toInt()
        val left = row.left.toInt()
        val right = row.right.toInt()
        fun ringNear(xs: IntProgression) = xs.any { near(image.at(it, y), ring) }
        assertTrue(ringNear(left..left + 6), "a focused row in a SwipeActions showed no ring at its leading side")
        assertTrue(ringNear(right - 1 downTo right - 7), "a focused row in a SwipeActions showed no ring at its trailing side")
    }
}
