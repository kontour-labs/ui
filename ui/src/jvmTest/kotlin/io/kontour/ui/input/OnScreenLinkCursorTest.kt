package io.kontour.ui.input

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kontour.ui.components.action.TextButton
import io.kontour.ui.foundation.SelectableText
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.richText
import io.kontour.ui.theme.KontourTheme
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Robot
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import java.awt.Cursor as AwtCursor

/**
 * A link in a line of text shows the hand, selectable or not, and the words
 * around it keep the cursor text has.
 *
 * Reported from the documentation site: a link in the prose showed the
 * text-selection cursor, the I-beam, rather than the hand. The prose there is
 * [SelectableText], so the whole paragraph asks for the I-beam, and the link in
 * it asked for nothing of its own that could win.
 *
 * On a real window, like [OnScreenCursorTest], because the cursor a node asks
 * for and the cursor the window shows are two different things.
 */
class OnScreenLinkCursorTest {

    @Test
    fun aLinkInTextShowsTheHand() {
        assertFalse(
            GraphicsEnvironment.isHeadless(),
            "there is no display — run this under xvfb-run, as CI does",
        )

        var plain = Rect.Zero
        var selectable = Rect.Zero
        var button = Rect.Zero
        var buttonInText = Rect.Zero
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow()
            window.setSize(700, 460)
            window.setLocation(0, 0)
            window.setContent {
                KontourTheme {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            richText { link(LinkWords) {}; +" and the rest of the line" },
                            style = Large,
                            modifier = Modifier.width(600.dp).onGloballyPositioned { plain = it.boundsInWindow() },
                        )
                        SelectableText(Modifier.padding(top = 40.dp)) {
                            Text(
                                richText { link(LinkWords) {}; +" and the rest of the line" },
                                style = Large,
                                modifier = Modifier.width(600.dp).onGloballyPositioned { selectable = it.boundsInWindow() },
                            )
                        }
                        TextButton(
                            onClick = {},
                            modifier = Modifier.padding(top = 20.dp).onGloballyPositioned { button = it.boundsInWindow() },
                        ) { +"Read the guide" }
                        SelectableText {
                            TextButton(
                                onClick = {},
                                modifier = Modifier.onGloballyPositioned { buttonInText = it.boundsInWindow() },
                            ) { +"Read the guide" }
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
            val seen = linkedMapOf<String, String>()
            // A point well inside the link's first word, and one well into the
            // plain words after it.
            for ((name, bounds) in listOf("plain" to plain, "selectable" to selectable)) {
                seen["$name link"] = hover(robot, window, bounds.left + 30f, bounds.center.y)
                seen["$name words"] = hover(robot, window, bounds.left + 470f, bounds.center.y)
            }
            seen["text button"] = hover(robot, window, button.center.x, button.center.y)
            seen["text button in selectable text"] = hover(robot, window, buttonInText.center.x, buttonInText.center.y)
            assertEquals(
                mapOf(
                    "plain link" to name(AwtCursor.HAND_CURSOR),
                    "plain words" to name(AwtCursor.DEFAULT_CURSOR),
                    "selectable link" to name(AwtCursor.HAND_CURSOR),
                    "selectable words" to name(AwtCursor.TEXT_CURSOR),
                    "text button" to name(AwtCursor.HAND_CURSOR),
                    "text button in selectable text" to name(AwtCursor.HAND_CURSOR),
                ),
                seen,
                "the cursor over a link and over the words beside it",
            )
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }

    /** Moves the real pointer to a window point in pixels and reads the cursor under it. */
    private fun hover(robot: Robot, window: ComposeWindow, x: Float, y: Float): String {
        lateinit var origin: Point
        var scale = 1.0
        SwingUtilities.invokeAndWait {
            origin = window.contentPane.locationOnScreen
            scale = window.graphicsConfiguration.defaultTransform.scaleX
        }
        val screen = Point(origin.x + (x / scale).toInt(), origin.y + (y / scale).toInt())
        // Twice: the theme learns the input is a mouse from the first movement.
        robot.mouseMove(screen.x - 2, screen.y)
        robot.mouseMove(screen.x, screen.y)
        robot.waitForIdle()
        Thread.sleep(SettleMillis)
        lateinit var cursor: AwtCursor
        SwingUtilities.invokeAndWait {
            val local = Point(screen).also { SwingUtilities.convertPointFromScreen(it, window.contentPane) }
            val deepest = SwingUtilities.getDeepestComponentAt(window.contentPane, local.x, local.y)
                ?: window.contentPane
            cursor = deepest.cursor ?: window.contentPane.cursor
        }
        return name(cursor.type)
    }

    private fun name(type: Int): String = AwtCursor.getPredefinedCursor(type).name

    private companion object {
        const val LinkWords = "The guide"
        const val SettleMillis = 150L
        val Large = TextStyle(fontSize = 28.sp)
    }
}
