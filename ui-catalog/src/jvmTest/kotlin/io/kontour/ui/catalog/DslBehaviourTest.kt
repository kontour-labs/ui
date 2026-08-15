package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.components.list.listGroup
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the shorthands promise, asserted rather than assumed.
 *
 * The showcases prove the DSLs *render* the same thing — two panels were
 * rewritten with them and their goldens did not move. These cover the two things
 * a golden cannot see: which position each row is handed, and whether a menu
 * closes itself.
 */
@OptIn(ExperimentalTestApi::class)
class DslBehaviourTest {

    @Test
    fun aGroupRoundsItsFirstAndLastRows() = runComposeUiTest {
        assertEquals(
            listOf(ListItemPosition.First, ListItemPosition.Middle, ListItemPosition.Last),
            positionsFromGroup(3),
        )
    }

    /** One row is [ListItemPosition.Only] — all four corners, not just the outside ones. */
    @Test
    fun aGroupOfOneRoundsEveryCorner() {
        runComposeUiTest {
            assertEquals(listOf(ListItemPosition.Only), positionsFromGroup(1))
        }
    }

    @Test
    fun aGroupOfTwoHasNoMiddle() {
        runComposeUiTest {
            assertEquals(
                listOf(ListItemPosition.First, ListItemPosition.Last),
                positionsFromGroup(2),
            )
        }
    }

    /**
     * The lazy builder numbers rows across elements, not within them.
     *
     * The arithmetic that is easy to get wrong: the offset each element starts at
     * has to be captured when the item is declared, not read when it composes.
     * Read late, every row gets the last element's offset — and because the first
     * and last elements are usually right anyway, it looks fine on a two-item list
     * and wrong on a three-item one.
     */
    @Test
    fun theLazyBuilderNumbersRowsAcrossElements() = runComposeUiTest {
        val seen = mutableListOf<ListItemPosition>()

        setContent {
            KontourTheme {
                LazyColumn(Modifier.fillMaxSize()) {
                    listGroup(listOf("a", "b", "c")) { element ->
                        row { position ->
                            seen += position
                            Text(element)
                        }
                    }
                }
            }
        }
        waitForIdle()

        assertEquals(
            listOf(ListItemPosition.First, ListItemPosition.Middle, ListItemPosition.Last),
            seen,
        )
    }

    /** Two rows per element still number 0…5, not 0…1 three times over. */
    @Test
    fun theLazyBuilderNumbersSeveralRowsPerElement() = runComposeUiTest {
        val seen = mutableListOf<ListItemPosition>()

        setContent {
            KontourTheme {
                LazyColumn(Modifier.fillMaxSize()) {
                    listGroup(listOf("a", "b", "c")) { element ->
                        row { position -> seen += position; Text("$element-1") }
                        row { position -> seen += position; Text("$element-2") }
                    }
                }
            }
        }
        waitForIdle()

        assertEquals(
            listOf(
                ListItemPosition.First,
                ListItemPosition.Middle,
                ListItemPosition.Middle,
                ListItemPosition.Middle,
                ListItemPosition.Middle,
                ListItemPosition.Last,
            ),
            seen,
        )
    }

    /**
     * A menu item closes the menu.
     *
     * The reason `MenuScope` exists. `MenuItem` cannot do this — it has never
     * been told how — so by hand every call site is
     * `onClick = { open = false; share() }`, and the one that forgets leaves a
     * menu hanging over the screen it just navigated away from.
     */
    @Test
    fun aMenuItemClosesTheMenu() = runComposeUiTest {
        var open by mutableStateOf(true)
        var ran = false

        setContent {
            KontourTheme {
                OverlayHost {
                    Box(Modifier.fillMaxSize()) {
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            item("Share") { ran = true }
                            item("Pin", closeOnClick = false) { ran = true }
                        }
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithText("Share").performClick()
        waitForIdle()

        assertTrue(ran, "the action did not run")
        assertTrue(!open, "the menu was left open")
    }

    /** …unless it is a row the user is likely to press again. */
    @Test
    fun closeOnClickFalseLeavesTheMenuOpen() = runComposeUiTest {
        var open by mutableStateOf(true)
        var presses = 0

        setContent {
            KontourTheme {
                OverlayHost {
                    Box(Modifier.fillMaxSize()) {
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            item("Only show delays", closeOnClick = false) { presses++ }
                        }
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithText("Only show delays").performClick()
        waitForIdle()
        onNodeWithText("Only show delays").performClick()
        waitForIdle()

        assertEquals(2, presses, "the menu closed and the second press never landed")
        assertTrue(open, "the menu closed despite closeOnClick = false")
    }
}

/** Renders a [ListGroup] of [count] rows and reports the position each one got. */
@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.positionsFromGroup(
    count: Int,
): List<ListItemPosition> {
    val seen = mutableListOf<ListItemPosition>()

    setContent {
        KontourTheme {
            ListGroup {
                repeat(count) { index ->
                    row { position ->
                        seen += position
                        Text("row $index")
                    }
                }
            }
        }
    }
    waitForIdle()

    return seen
}
