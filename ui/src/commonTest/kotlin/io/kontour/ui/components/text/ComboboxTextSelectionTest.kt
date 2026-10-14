package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An open combobox's field is a text box: what has been typed in it can be
 * clicked into, and a word in it double-clicked, without the list closing.
 *
 * Reported as *"in something like the combobox, you can't double tap or click to
 * select text"*. The list's scrim lay over the whole page, the field included,
 * so the first press in the text — a click to move the caret, the first half of
 * a double-click — landed on the scrim. The list closed, the field went back to
 * showing its value, and what had been typed was thrown away with it. The text
 * box never saw a press at all, so no gesture of its own could work.
 */
@OptIn(ExperimentalTestApi::class)
class ComboboxTextSelectionTest {

    private fun ComposeUiTest.show() {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        var stop by remember { mutableStateOf<String?>(null) }
                        Combobox(
                            value = stop,
                            options = Stops,
                            onValueChange = { stop = it },
                            label = "From",
                            state = rememberSelectState(initialExpanded = true),
                            modifier = Modifier.width(320.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    /** The text box, which is there only while the list is open. */
    private val ComposeUiTest.input: SemanticsNodeInteraction
        get() = onNode(hasSetTextAction(), useUnmergedTree = true)

    /** How many of the list's options are showing: none once it has closed. */
    private fun ComposeUiTest.optionsShowing(): Int =
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .fetchSemanticsNodes().size

    /** Somewhere on the first word of what was typed, in the input's own coordinates. */
    private fun ComposeUiTest.onFirstWord(): Offset {
        val size = input.fetchSemanticsNode().size
        return Offset(12f, size.height / 2f)
    }

    private fun ComposeUiTest.typed(): String =
        input.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    @Test
    fun aClickInTheTextLeavesTheListOpen() = runComposeUiTest {
        show()
        input.performTextInput("Perth Station")
        waitForIdle()
        assertTrue(optionsShowing() > 0, "typing should leave the list open, narrowed to what matches")

        val at = onFirstWord()
        input.performMouseInput { click(at) }
        waitForIdle()

        assertTrue(
            optionsShowing() > 0,
            "a click in the typed text closed the list: the press went to the list's scrim rather " +
                "than to the text box under it",
        )
        assertEquals("Perth Station", typed(), "a click in the text should leave what was typed alone")
    }

    @Test
    fun aDoubleClickSelectsAWord() = runComposeUiTest {
        show()
        input.performTextInput("Perth Station")
        waitForIdle()

        val at = onFirstWord()
        input.performMouseInput { doubleClick(at) }
        waitForIdle()

        assertTrue(optionsShowing() > 0, "a double-click on a word closed the list")
        val selection = checkNotNull(input.fetchSemanticsNode().config.getOrNull(SemanticsProperties.TextSelectionRange)) {
            "the text box reported no selection at all"
        }
        assertTrue(!selection.collapsed, "a double-click on \"Perth\" should select it, and left a caret at $selection")
        assertEquals("Perth", typed().substring(selection.min, selection.max), "the word double-clicked")
    }

    @Test
    fun aTapInTheTextLeavesTheListOpen() = runComposeUiTest {
        show()
        input.performTextInput("Perth Station")
        waitForIdle()

        val at = onFirstWord()
        input.performTouchInput { click(at) }
        waitForIdle()

        assertTrue(optionsShowing() > 0, "a tap in the typed text closed the list")
        assertEquals("Perth Station", typed(), "a tap in the text should leave what was typed alone")
    }

    private companion object {
        val Stops = listOf("Perth Station", "Perth Underground", "Elizabeth Quay", "Esplanade")
    }
}
