package io.kontour.ui.components.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `ListGroup` and `KeyValueList` builders are composable: the theme's formats
 * and strings are read where a row is written.
 *
 * Reported from an app: *"`KeyValueList`'s `item(label, value)` and
 * `ListGroup`'s `item(...)` builders aren't composable, so `Theme.formats`
 * (dates, numbers) has to be computed before the builder. That was the most
 * common compile error in P2."*
 *
 * A composable lambda on an ordinary function is a restartable group of its
 * own, which is what kept these builders plain: a state read in one restarts the
 * builder alone and the group draws what it had. Both are `inline` now, as
 * `Column` is, so the builder is the caller's composition. The last two tests
 * are the failure that was documented, kept as guards.
 */
@OptIn(ExperimentalTestApi::class)
class ComposableBuilderTest {

    @Test
    fun aBuilderReadsTheTheme() = runComposeUiTest {
        var time = ""
        var date = ""
        setContent {
            KontourTheme(reduceMotion = true) {
                time = Theme.formats.time(LocalTime(9, 5))
                date = Theme.formats.date(LocalDate(2026, 3, 14))
                ListGroup {
                    item("Departs", supporting = Theme.formats.time(LocalTime(9, 5)))
                }
                KeyValueList {
                    item("Updated", Theme.formats.date(LocalDate(2026, 3, 14)))
                }
            }
        }
        waitForIdle()
        onNodeWithText(time, substring = true).fetchSemanticsNode()
        onNodeWithText(date, substring = true).fetchSemanticsNode()
    }

    /** The catalog's grouped rows: a selection written in the builder redraws. */
    @Test
    fun aSelectionWiredThroughTheBuilderRedraws() = runComposeUiTest {
        var current by mutableStateOf(0)
        setContent {
            KontourTheme(reduceMotion = true) {
                ListGroup {
                    repeat(3) { index ->
                        item("Row $index", selected = current == index, onClick = { current = index })
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Row 2").performClick()
        waitForIdle()
        assertEquals(2, current)
        assertEquals(true, selectedOf("Row 2"), "the row tapped should draw selected")
        assertTrue(selectedOf("Row 0") != true, "and the one selected before it should not")
    }

    @Test
    fun aStateReadInTheBuilderRecomposesIt() = runComposeUiTest {
        var signedIn by mutableStateOf(false)
        var fare by mutableStateOf("$3.20")
        setContent {
            KontourTheme(reduceMotion = true) {
                ListGroup {
                    item("Help")
                    if (signedIn) item("Sign out")
                }
                KeyValueList { item("Fare", fare) }
            }
        }
        waitForIdle()
        assertEquals(0, onAllNodesWithText("Sign out").fetchSemanticsNodes().size)
        signedIn = true
        fare = "$4.80"
        waitForIdle()
        onNodeWithText("Sign out").fetchSemanticsNode()
        onNodeWithText("$4.80", substring = true).fetchSemanticsNode()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.selectedOf(text: String): Boolean? =
        onNodeWithText(text).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
}
