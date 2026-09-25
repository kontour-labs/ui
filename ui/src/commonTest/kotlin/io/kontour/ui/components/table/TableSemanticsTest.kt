package io.kontour.ui.components.table

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** A table as a screen reader and a finger meet it: headings, sorting, rows and picking them. */
@OptIn(ExperimentalTestApi::class)
class TableSemanticsTest {

    private data class Departure(val id: Int, val route: String, val fare: Int)

    private val rows = listOf(Departure(1, "950", 330), Departure(2, "T1", 475), Departure(3, "103", 620))

    @Test
    fun headersAreHeadingsThatSayHowTheyAreSorted() = runComposeUiTest {
        val asked = mutableListOf<TableSort>()
        setContent {
            KontourTheme {
                Table(
                    items = rows,
                    modifier = Modifier.height(300.dp),
                    sort = TableSort("Route"),
                    onSortChange = { asked += it },
                ) {
                    column("Route") { +it.route }
                    column("Fare", numeric = true) { +"${it.fare}" }
                }
            }
        }
        onNode(hasText("Route") and isHeading())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sorted ascending"))
        onNode(hasText("Fare") and isHeading()).assertExists()
        onNodeWithText("Route").performClick()
        onNodeWithText("Fare").performClick()
        assertEquals(listOf(TableSort("Route", SortDirection.Descending), TableSort("Fare")), asked)
    }

    @Test
    fun aRowIsOneNodeThatOpensAndSaysWhereItIs() = runComposeUiTest {
        val opened = mutableListOf<Int>()
        setContent {
            KontourTheme {
                Table(items = rows, modifier = Modifier.height(300.dp), onRowClick = { opened += it.id }) {
                    column("Route") { +it.route }
                    column("Fare", numeric = true) { +"${it.fare}" }
                }
            }
        }
        onNodeWithText("T1").performClick()
        assertEquals(listOf(2), opened)
        val info = onNodeWithText("103").fetchSemanticsNode().config[SemanticsProperties.CollectionItemInfo]
        assertEquals(3, info.rowIndex, "the header is row nought, so the third row is row three")
        // Merged: the row's node carries every cell.
        onNode(hasText("103") and hasText("620")).assertExists()
    }

    /**
     * Multiple selection: each row toggles its own key, and the header's checkbox
     * shows none, some or all, and picks the rest from some.
     */
    @Test
    fun aHeaderCheckboxPicksAllFromSome() = runComposeUiTest {
        val changes = mutableListOf<Set<Any>>()
        setContent {
            KontourTheme {
                Table(
                    items = rows,
                    modifier = Modifier.height(300.dp),
                    key = { it.id },
                    selection = TableSelection.Multiple,
                    selected = setOf(2),
                    onSelectedChange = { changes += it },
                ) {
                    column("Route") { +it.route }
                }
            }
        }
        onNodeWithContentDescription("Select all")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate))
            .performClick()
        onNodeWithText("950").performClick()
        onNodeWithText("T1").performClick()
        assertEquals(listOf(setOf<Any>(1, 2, 3), setOf<Any>(2, 1), emptySet()), changes)
    }

    @Test
    fun aDisabledTablesRowsSaySo() = runComposeUiTest {
        setContent {
            KontourTheme {
                Table(items = rows, modifier = Modifier.height(300.dp), enabled = false, onRowClick = {}) {
                    column("Route") { +it.route }
                }
            }
        }
        onNodeWithText("950").assertIsNotEnabled()
    }
}
