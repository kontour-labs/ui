package io.kontour.ui.components.display

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ListSection
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test

/**
 * A [KeyValueList] in a [ListSection], and one asked for its intrinsic width.
 *
 * Reported against 0.4.5. A section asks its body for the alignment lines that
 * mark a row's edges as it measures, which places the body there and then; the
 * list was a `SubcomposeLayout`, composing its rows inside its measure, and a
 * body whose nodes are made during measure is not ready to be placed early.
 * And a `SubcomposeLayout` throws for an intrinsic measurement. It is plain
 * layouts now, composing nothing while it measures.
 */
@OptIn(ExperimentalTestApi::class)
class KeyValueInSectionTest {

    @Test
    fun aListInASectionLaysOutAndKeepsUpWithItsValues() = runComposeUiTest {
        var platform by mutableStateOf("2")
        var more by mutableStateOf(false)
        setContent {
            KontourTheme {
                ListSection(title = { +"Trip" }) {
                    KeyValueList(showDividers = true) {
                        item("Operator", "Transperth")
                        item("Platform", platform)
                        if (more) item("Accessible", "Yes")
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Transperth", useUnmergedTree = true).assertExists()
        platform = "3"
        more = true
        waitForIdle()
        onNodeWithText("3", useUnmergedTree = true).assertExists()
        onNodeWithText("Accessible", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aCompactListInASectionLaysOut() = runComposeUiTest {
        setContent {
            KontourTheme {
                ListSection(title = { +"Now" }) {
                    KeyValueList(compact = true) { item("Solar", "2.4 kW") }
                }
            }
        }
        waitForIdle()
        onNodeWithText("2.4 kW", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aListAskedForItsWidthAnswers() = runComposeUiTest {
        setContent {
            KontourTheme {
                Row(Modifier.width(IntrinsicSize.Max)) {
                    KeyValueList { item("Operator", "Transperth") }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Transperth", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aNarrowListStacks() = runComposeUiTest {
        setContent {
            KontourTheme {
                ListSection(title = { +"Trip" }) {
                    Row(Modifier.width(100.dp)) { KeyValueList { item("Operator", "Transperth") } }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Transperth", useUnmergedTree = true).assertExists()
    }
}
