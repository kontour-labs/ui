package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A command palette's results fade out at whichever end there are more of
 * them, and stand square at an end there are not.
 *
 * Asked for as *"in the command palette, can we … use fading edges
 * modifier"*. The rows were cut off square at the panel's foot, and a row cut
 * off at a panel's edge reads as the last one.
 *
 * Read from the pixels: a faded row is the panel's own colour where the list
 * ends, an unfaded one its own.
 */
@OptIn(ExperimentalTestApi::class)
class CommandPaletteFadeTest {

    private fun ComposeUiTest.palette(): Rect {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.requiredSize(400.dp, 640.dp)) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        CommandPalette(
                            visible = true,
                            onDismissRequest = {},
                            commands = List(20) { Command("c$it", "Command $it", onAction = {}) },
                        )
                    }
                }
            }
        }
        waitForIdle()
        // The field scrolls its text too; the list is the tall one.
        return onAllNodes(hasScrollAction()).fetchSemanticsNodes().maxBy { it.boundsInRoot.height }.boundsInRoot
    }

    /** Whether the list's last few lines of pixels, at [end], are all the panel showing through. */
    private fun PixelMap.faded(list: Rect, end: End): Boolean {
        val x = list.center.x.toInt() + 100
        val panel = this[x, list.bottom.toInt() + 2]
        val lines = when (end) {
            End.Top -> list.top.toInt() until list.top.toInt() + 4
            End.Bottom -> list.bottom.toInt() - 4 until list.bottom.toInt()
        }
        return lines.all { this[x, it].near(panel) }
    }

    private fun Color.near(other: Color) =
        abs(red - other.red) < 0.01f && abs(green - other.green) < 0.01f && abs(blue - other.blue) < 0.01f

    private enum class End { Top, Bottom }

    @Test
    fun atTheTopTheFootFadesAndTheHeadDoesNot() = runComposeUiTest {
        val list = palette()
        val image = onRoot().captureToImage().toPixelMap()
        assertTrue(image.faded(list, End.Bottom), "the rows run on past the foot and are cut off square there")
        assertTrue(!image.faded(list, End.Top), "nothing is above the first row, and the head faded anyway")
    }

    @Test
    fun atTheEndTheHeadFadesAndTheFootDoesNot() = runComposeUiTest {
        val list = palette()
        // Up from the first wraps to the last, and brings it into view.
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        val image = onRoot().captureToImage().toPixelMap()
        assertTrue(image.faded(list, End.Top), "the rows scrolled up past the head are cut off square there")
        assertTrue(!image.faded(list, End.Bottom), "nothing is below the last row, and the foot faded anyway")
    }
}
