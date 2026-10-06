package io.kontour.ui.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A segmented control takes values, as `Select` and `RadioGroup` do.
 *
 * Reported from an app: *"`SegmentedControl` takes `List<String>` and an index
 * … so for the News app filter I had to map indices back to values myself."*
 */
@OptIn(ExperimentalTestApi::class)
class TypedSegmentedControlTest {

    private enum class Filter(val label: String) { All("All"), Local("Local"), World("World") }

    @Test
    fun itTakesAndGivesValues() = runComposeUiTest {
        var filter by mutableStateOf(Filter.All)
        setContent {
            KontourTheme(reduceMotion = true) {
                SegmentedControl(
                    value = filter,
                    options = Filter.entries,
                    onValueChange = { filter = it },
                    optionLabel = { it.label },
                )
            }
        }
        waitForIdle()
        assertEquals(true, selected("All"))
        onNodeWithText("World").performClick()
        waitForIdle()
        assertEquals(Filter.World, filter, "a press should hand back the value, not its index")
        assertEquals(true, selected("World"))
        assertEquals(false, selected("All"))
    }

    /** A value that is not among the options draws none as chosen, and does not throw. */
    @Test
    fun aValueNotAmongTheOptionsChoosesNone() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                SegmentedControl(value = "Night", options = listOf("Day", "Week"), onValueChange = {})
            }
        }
        waitForIdle()
        assertEquals(false, selected("Day"), "a segment not chosen says so, rather than saying nothing")
        assertEquals(false, selected("Week"))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.selected(text: String): Boolean? =
        onNodeWithText(text).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
}
