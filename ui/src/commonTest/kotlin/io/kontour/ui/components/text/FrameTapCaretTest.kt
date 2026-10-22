package io.kontour.ui.components.text

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.TextFieldListItem
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A press on a field off its text — its leading icon, its padding, a list
 * row's label — puts the caret at the end of the text nearer the press, or in
 * a text area at the nearer end of the line level with it, rather than back
 * where the reader last left it.
 *
 * Reported as *"if i tap my cursor like halfway into some text in a text field,
 * then tap out, then tap on the leading icon, it puts my cursor exactly where i
 * left it. i'd like it to show up on the side closest to where i tapped"*.
 */
@OptIn(ExperimentalTestApi::class)
class FrameTapCaretTest {

    private val elsewhere = FocusRequester()

    /** [content] beside something to take the focus away to, with the caret left halfway into [state]. */
    private fun ComposeUiTest.field(state: TextFieldState, content: @androidx.compose.runtime.Composable () -> Unit): DpRect {
        setContent {
            KontourTheme {
                Column {
                    content()
                    Box(Modifier.size(40.dp).focusRequester(elsewhere).focusable())
                }
            }
        }
        state.edit { selection = TextRange(state.text.length / 2) }
        runOnIdle { elsewhere.requestFocus() }
        waitForIdle()
        return onNode(hasSetTextAction()).getBoundsInRoot()
    }

    private fun ComposeUiTest.tapAt(x: Dp, y: Dp) {
        val at = with(density) { Offset(x.toPx(), y.toPx()) }
        onRoot().performTouchInput { click(at) }
        waitForIdle()
    }

    @Test
    fun theLeadingIconPutsTheCaretAtTheStart() = runComposeUiTest {
        val state = TextFieldState("Perth to Fremantle")
        val input = field(state) {
            TextField(state, modifier = Modifier.width(320.dp), leadingIcon = Theme.icons.chevronDown)
        }
        // On the icon, before the input.
        tapAt(input.left - 12.dp, (input.top + input.bottom) / 2)
        assertEquals(TextRange(0), state.selection, "a press on the leading icon should put the caret at the start")
    }

    @Test
    fun theFarPaddingPutsTheCaretAtTheEnd() = runComposeUiTest {
        val state = TextFieldState("Perth")
        val input = field(state) {
            TextField(state, modifier = Modifier.width(320.dp), leadingIcon = Theme.icons.chevronDown)
        }
        // In the padding past the input's end.
        tapAt(input.right + 4.dp, (input.top + input.bottom) / 2)
        assertEquals(TextRange(state.text.length), state.selection, "a press past the text's end should put the caret at the end")
    }

    @Test
    fun inATextAreaItIsTheLineLevelWithThePress() = runComposeUiTest {
        val state = TextFieldState("First line\nSecond line\nThird line")
        val input = field(state) {
            TextArea(state, modifier = Modifier.width(320.dp), lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3))
        }
        val lineHeight = (input.bottom - input.top) / 3
        val second = input.top + lineHeight * 1.5f
        tapAt(input.left - 4.dp, second)
        assertEquals(TextRange("First line\n".length), state.selection, "a press before the second line should put the caret at its start")
        tapAt(input.right + 4.dp, second)
        assertEquals(TextRange("First line\nSecond line".length), state.selection, "a press after it should put the caret at its end, before its break")
    }

    @Test
    fun aListRowsLabelPutsTheCaretAtTheValuesNearerEnd() = runComposeUiTest {
        val state = TextFieldState("Dishwasher")
        val input = field(state) {
            TextFieldListItem(state, modifier = Modifier.width(320.dp), label = "What")
        }
        // On the label, at the row's start, before the value set at its end.
        tapAt(8.dp + 16.dp, (input.top + input.bottom) / 2)
        assertEquals(TextRange(0), state.selection, "a press on the row's label should put the caret at the value's start")
    }
}
