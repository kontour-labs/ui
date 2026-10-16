package io.kontour.ui.foundation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.theme.ColourScheme
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.ProvideTokens
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.darkColourScheme
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a `SelectableText` lets a reader take, and what it keeps back.
 *
 * Driven the way a reader does it on a desktop — a mouse drag from the first
 * word to the last, then Ctrl+C — into a clipboard that records what it was
 * given. What is asserted is the copied string, because that is the whole of
 * what selection is for: a highlight that copies the wrong words is a defect a
 * screenshot would pass.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class SelectableTextTest {

    /** A clipboard that keeps the last text written to it. */
    private class RecordingClipboard : Clipboard {
        var text: String? = null

        override suspend fun getClipEntry(): ClipEntry? = null

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            val transferable = clipEntry?.nativeClipEntry as? Transferable
            text = transferable?.getTransferData(DataFlavor.stringFlavor) as? String
        }

        override val nativeClipboard: NativeClipboard =
            java.awt.datatransfer.Clipboard("test")
    }

    private fun ComposeUiTest.dragAndCopy(from: String, to: String) {
        val start = onNodeWithText(from).fetchSemanticsNode().boundsInRoot
        val end = onNodeWithText(to).fetchSemanticsNode().boundsInRoot
        onRoot().performMouseInput {
            moveTo(Offset(start.left + 1f, start.center.y))
            press()
            moveTo(Offset((start.left + end.right) / 2f, (start.center.y + end.center.y) / 2f))
            moveTo(Offset(end.right - 1f, end.center.y))
            release()
        }
        waitForIdle()
        onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.C) } }
        waitForIdle()
    }

    private fun ComposeUiTest.notice(clipboard: Clipboard, selectable: Boolean, middle: @Composable () -> Unit) {
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    val body = @Composable {
                        Column {
                            Text("Replacement buses")
                            middle()
                            Text("Buses leave from stand 4.")
                        }
                    }
                    if (selectable) SelectableText { body() } else body()
                }
            }
        }
    }

    @Test
    fun aDragAcrossTheRegionCopiesItsTextButNotTheButtonInIt() = runComposeUiTest {
        val clipboard = RecordingClipboard()
        notice(clipboard, selectable = true) {
            Button(onClick = {}) { +"Plan around it" }
        }
        dragAndCopy(from = "Replacement buses", to = "Buses leave from stand 4.")

        val copied = assertNotNull(clipboard.text, "Ctrl+C after a drag wrote nothing")
        assertTrue("Replacement buses" in copied, "the first line was not copied: \"$copied\"")
        assertTrue("stand 4" in copied, "the last line was not copied: \"$copied\"")
        assertFalse("Plan around it" in copied, "a button's label was copied with the text: \"$copied\"")
    }

    @Test
    fun aClickableRowIsLeftOutAndAPlainOneIsNot() = runComposeUiTest {
        val clipboard = RecordingClipboard()
        notice(clipboard, selectable = true) {
            ListItem { +"Stop 1142, Wellington St" }
            ListItem(onClick = {}) { +"Open the timetable" }
        }
        dragAndCopy(from = "Replacement buses", to = "Buses leave from stand 4.")

        val copied = assertNotNull(clipboard.text)
        assertTrue("Stop 1142" in copied, "a row with no onClick is content, and was not copied: \"$copied\"")
        assertFalse("Open the timetable" in copied, "a clickable row's label was copied: \"$copied\"")
    }

    @Test
    fun redactedTextIsNeverCopied() = runComposeUiTest {
        val clipboard = RecordingClipboard()
        notice(clipboard, selectable = true) {
            Redacted { Text("Fare £4.20") }
        }
        dragAndCopy(from = "Replacement buses", to = "Buses leave from stand 4.")

        val copied = assertNotNull(clipboard.text)
        assertFalse("4.20" in copied, "a redacted string was handed over on copy: \"$copied\"")
    }

    @Test
    fun nothingIsSelectableOutsideARegion() = runComposeUiTest {
        val clipboard = RecordingClipboard()
        notice(clipboard, selectable = false) {}
        dragAndCopy(from = "Replacement buses", to = "Buses leave from stand 4.")

        assertNull(clipboard.text, "text outside a SelectableText was copied: \"${clipboard.text}\"")
    }

    @Test
    fun theThemesSelectionIsItsAccentAndFollowsANestedScheme() = runComposeUiTest {
        var outer: TextSelectionColors? = null
        var outerExpected: TextSelectionColors? = null
        var inner: TextSelectionColors? = null
        var innerExpected: TextSelectionColors? = null
        setContent {
            KontourTheme(reduceMotion = true) {
                outer = LocalTextSelectionColors.current
                outerExpected = expected(Theme.colours)
                ProvideTokens(colours = darkColourScheme()) {
                    inner = LocalTextSelectionColors.current
                    innerExpected = expected(Theme.colours)
                }
            }
        }
        waitForIdle()
        assertEquals(outerExpected, outer)
        assertEquals(innerExpected, inner, "a subtree with another scheme kept the outer scheme's selection")
    }

    /**
     * Code's sunken ground is under the highlight, not over it.
     *
     * It was over it: the paragraph paints a span's background as it draws the
     * glyphs, after `BasicText` has drawn the selection, so a drag across "Run
     * `xxxx` now" lit up the words either side and left the code looking
     * unselected — while copying it all the same. Read from the pixels, because
     * that is the only place it ever went wrong: inside the code's ground, above
     * the glyphs (an `x` has no ascender), before and after the drag.
     */
    @Test
    fun aSelectionIsDrawnOverCodeAsOverTheWordsAroundIt() = runComposeUiTest {
        var layout: TextLayoutResult? = null
        var sunken = Color.Unspecified
        var highlight = Color.Unspecified
        setContent {
            KontourTheme(reduceMotion = true) {
                sunken = Theme.colours.surfaceSunken
                highlight = LocalTextSelectionColors.current.backgroundColor
                SelectableText {
                    Text(
                        richText { +"Run "; code("xxxx"); +" now" },
                        modifier = Modifier.testTag("line"),
                        onTextLayout = { layout = it },
                    )
                }
            }
        }
        waitForIdle()

        val result = assertNotNull(layout)
        val ground = result.getPathForRange(4, 8).getBounds()
        val origin = onNodeWithTag("line").fetchSemanticsNode().positionInRoot
        val x = (origin.x + ground.center.x).toInt()
        val y = (origin.y + ground.top + ground.height * 0.15f).toInt()

        val before = onRoot().captureToImage().toPixelMap()[x, y]
        assertClose(sunken, before, "the code's ground was not drawn where the code is")

        val line = onNodeWithTag("line").fetchSemanticsNode().boundsInRoot
        onRoot().performMouseInput {
            moveTo(Offset(line.left + 1f, line.center.y))
            press()
            moveTo(line.center)
            moveTo(Offset(line.right - 1f, line.center.y))
            release()
        }
        waitForIdle()

        val after = onRoot().captureToImage().toPixelMap()[x, y]
        assertClose(highlight.compositeOver(sunken), after, "the selection was not drawn over the code")
    }

    private fun assertClose(expected: Color, actual: Color, message: String) {
        val off = maxOf(
            abs(expected.red - actual.red),
            abs(expected.green - actual.green),
            abs(expected.blue - actual.blue),
        )
        assertTrue(off <= 3f / 255f, "$message: expected $expected, drew $actual")
    }

    private fun expected(colours: ColourScheme) = TextSelectionColors(
        handleColor = colours.accent.solid,
        backgroundColor = colours.accent.solid.copy(alpha = 0.35f),
    )
}
