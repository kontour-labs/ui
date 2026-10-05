package io.kontour.ui.components.text

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A search field has a name a screen reader can say.
 *
 * Reported from an app: *"SearchField has no accessible name (its placeholder
 * isn't used as one). I had to work around [it] in the tests."* A text field is
 * named by its label, and a search field shows none, so its input was an
 * unnamed edit box — and nothing passed from outside reached the input to name
 * it.
 */
@OptIn(ExperimentalTestApi::class)
class SearchFieldNameTest {

    private fun ComposeUiTest.input(): SemanticsNode = onNode(hasSetTextAction()).fetchSemanticsNode()

    private fun SemanticsNode.name(): String? =
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    @Test
    fun aSearchFieldIsNamedByItsPlaceholder() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                SearchField(state = rememberTextFieldState(), placeholder = "Search stops and routes")
            }
        }
        waitForIdle()
        assertEquals("Search stops and routes", input().name())
        assertEquals(
            0,
            onAllNodesWithText("Search stops and routes").fetchSemanticsNodes().size,
            "the placeholder should be said once, as the name, not again as a line of its own",
        )
    }

    @Test
    fun withNoPlaceholderItIsCalledSearch() = runComposeUiTest {
        var search = ""
        setContent {
            KontourTheme(reduceMotion = true) {
                search = Theme.strings.search
                SearchField(state = rememberTextFieldState(), placeholder = null)
            }
        }
        waitForIdle()
        assertEquals(search, input().name())
    }

    /** A text field is still named by its label, with its placeholder read as a hint. */
    @Test
    fun aLabelledTextFieldIsUnchanged() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                TextField(state = rememberTextFieldState(), label = "Origin", placeholder = "Station, stop or address")
            }
        }
        waitForIdle()
        assertEquals("Origin", input().name())
        assertEquals(1, onAllNodesWithText("Station, stop or address").fetchSemanticsNodes().size)
    }
}
