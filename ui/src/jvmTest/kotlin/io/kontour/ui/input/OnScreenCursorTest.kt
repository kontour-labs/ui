package io.kontour.ui.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import java.awt.Cursor as AwtCursor

/**
 * Every cursor, on a real window, under a real mouse.
 *
 * The rest of the cursor tests stop one step short of the screen: they record the
 * [androidx.compose.ui.input.pointer.PointerIcon] a scene asks the platform for,
 * and trust the platform to show it. This is that last step. A `ComposeWindow`
 * with one tile per [Cursor], a `Robot` moving the system pointer over each, and
 * the AWT cursor read back off the component under it — which is what the window
 * system draws.
 *
 * **It needs a display**, so it is not part of `jvmTest`. It runs as
 * `:ui:jvmOnScreenTest`, which CI runs under `xvfb-run`, and it fails outright
 * rather than passing quietly when there is no display to run on.
 */
class OnScreenCursorTest {

    private val tiles = Cursor.entries

    /** What AWT shows for each, from `PointerCursor.jvm.kt` and Compose's own four. */
    private val expected = mapOf(
        Cursor.Default to AwtCursor.DEFAULT_CURSOR,
        Cursor.Pointer to AwtCursor.HAND_CURSOR,
        Cursor.Text to AwtCursor.TEXT_CURSOR,
        Cursor.Crosshair to AwtCursor.CROSSHAIR_CURSOR,
        Cursor.ResizeColumn to AwtCursor.E_RESIZE_CURSOR,
        Cursor.ResizeRow to AwtCursor.S_RESIZE_CURSOR,
        Cursor.Grab to AwtCursor.HAND_CURSOR,
        Cursor.Grabbing to AwtCursor.MOVE_CURSOR,
        Cursor.NotAllowed to AwtCursor.DEFAULT_CURSOR,
        Cursor.Progress to AwtCursor.DEFAULT_CURSOR,
    )

    @Test
    fun eachCursorReachesTheWindow() {
        assertFalse(
            GraphicsEnvironment.isHeadless(),
            "there is no display, so there is no window to put a cursor on — run this " +
                "under xvfb-run, as CI does",
        )

        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow()
            window.setSize(TileStride * tiles.size + 40, 160)
            window.setLocation(0, 0)
            window.setContent {
                KontourTheme {
                    Box {
                        tiles.forEachIndexed { index, cursor ->
                            Box(
                                Modifier
                                    .offset(x = (TileStride * index).dp, y = 20.dp)
                                    .size(TileSize.dp)
                                    .pointerCursor(cursor)
                            )
                        }
                    }
                }
            }
            window.isVisible = true
        }

        val robot = Robot().apply { autoDelay = 20 }
        try {
            robot.waitForIdle()
            Thread.sleep(SettleMillis)

            val seen = linkedMapOf<Cursor, Int>()
            tiles.forEachIndexed { index, cursor ->
                val origin = onScreen(window.contentPane)
                val centre = Point(
                    origin.x + TileStride * index + TileSize / 2,
                    origin.y + 20 + TileSize / 2,
                )
                // Twice: the theme learns that the input is a mouse from the first
                // movement, and a cursor gated on that appears from the second.
                robot.mouseMove(centre.x - 2, centre.y)
                robot.mouseMove(centre.x, centre.y)
                robot.waitForIdle()
                Thread.sleep(SettleMillis)
                seen[cursor] = cursorAt(window, centre).type
            }

            capture(robot, window)

            assertEquals(
                expected.mapValues { name(it.value) },
                seen.mapValues { name(it.value) },
                "the cursor each tile put on the real window",
            )
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }

    private fun onScreen(component: Component): Point {
        lateinit var point: Point
        SwingUtilities.invokeAndWait { point = component.locationOnScreen }
        return point
    }

    /** The cursor of the deepest component under [screen], which is what AWT draws. */
    private fun cursorAt(window: ComposeWindow, screen: Point): AwtCursor {
        lateinit var cursor: AwtCursor
        SwingUtilities.invokeAndWait {
            val local = Point(screen).also { SwingUtilities.convertPointFromScreen(it, window.contentPane) }
            val deepest = SwingUtilities.getDeepestComponentAt(window.contentPane, local.x, local.y)
                ?: window.contentPane
            cursor = deepest.cursor ?: (window.contentPane as Container).cursor
        }
        return cursor
    }

    /** A picture of the window, for a person — a screen capture has no pointer in it. */
    private fun capture(robot: Robot, window: ComposeWindow) {
        val out = System.getProperty("kontour.onScreen.captureDir") ?: return
        lateinit var bounds: java.awt.Rectangle
        SwingUtilities.invokeAndWait { bounds = window.bounds }
        val image: BufferedImage = robot.createScreenCapture(bounds)
        File(out).mkdirs()
        ImageIO.write(image, "png", File(out, "on-screen-cursors.png"))
    }

    private fun name(type: Int): String = AwtCursor.getPredefinedCursor(type).name

    private companion object {
        const val TileSize = 60
        const val TileStride = 80
        const val SettleMillis = 150L
    }
}
