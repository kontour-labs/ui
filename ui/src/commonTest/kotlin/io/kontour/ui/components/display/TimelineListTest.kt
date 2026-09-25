package io.kontour.ui.components.display

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.Role
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * A stop list's rows are list rows, and each draws the right halves of the legs
 * either side of it.
 */
@OptIn(ExperimentalTestApi::class)
class TimelineListTest {

    @Test
    fun aStopWithAnActionIsAButtonThatRunsIt() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent {
            KontourTheme {
                TimelineList {
                    item(onClick = { opened += "Perth" }) { +"Perth Station" }
                    item("Elizabeth Quay", trailing = "08:21") { opened += "Quay" }
                }
            }
        }
        onNodeWithText("Perth Station").performClick()
        // The shorthand's trailing text is part of the same row, and the row is
        // what takes the tap.
        onNodeWithText("Elizabeth Quay", useUnmergedTree = true).performClick()
        assertEquals(listOf("Perth", "Quay"), opened)
    }

    @Test
    fun aDisabledListDisablesEveryRow() = runComposeUiTest {
        setContent {
            KontourTheme {
                TimelineList(enabled = false) {
                    item(onClick = {}) { +"Perth Station" }
                    item("Elizabeth Quay") {}
                }
            }
        }
        onNodeWithText("Perth Station").assertIsNotEnabled()
        onNodeWithText("Elizabeth Quay").assertIsNotEnabled()
    }

    @Test
    fun aSelectedStopSaysSo() = runComposeUiTest {
        setContent {
            KontourTheme {
                TimelineList {
                    item(selected = true, role = Role.RadioButton, onClick = {}) { +"Perth Station" }
                    item(onClick = {}) { +"Elizabeth Quay" }
                }
            }
        }
        onNodeWithText("Perth Station").assertIsSelected()
        onNodeWithText("Elizabeth Quay").assertIsEnabled()
    }

    @Test
    fun theLazyListDrawsTheSameRowsFromItsElements() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val stops = listOf("Perth", "Walk", "Quay")
        setContent {
            KontourTheme {
                LazyColumn {
                    timelineList(stops, key = { it }) { stop -> item(stop) { opened += stop } }
                }
            }
        }
        onNodeWithText("Quay").performClick()
        assertEquals(listOf("Quay"), opened)
    }

    /**
     * The leg between two stops is the first one's connector, drawn half in each
     * row; the first row's top and the last row's bottom are the list's own legs.
     */
    @Test
    fun eachRowDrawsHalfOfTheLegsEitherSideOfIt() {
        val first = stop(ConnectorStyle.Solid, Color.Red)
        val walk = stop(ConnectorStyle.Dashed, Color.Blue)
        val last = stop(ConnectorStyle.Dotted, Color.Green)
        val rows = timelineRows(listOf(first, walk, last), leadIn = ConnectorStyle.None, leadOut = ConnectorStyle.Solid)

        assertNull(rows[0].above, "nothing arrives at the first stop without a lead-in")
        assertEquals(ConnectorStyle.Solid, rows[0].below!!.style)
        assertEquals(ConnectorStyle.Solid, rows[1].above!!.style, "the leg into the walk is the first stop's")
        assertEquals(Color.Red, rows[1].above!!.colour)
        assertEquals(ConnectorStyle.Dashed, rows[1].below!!.style)
        assertEquals(ConnectorStyle.Dashed, rows[2].above!!.style, "the walk's leg reaches the last stop")
        // The last stop's own connector leads nowhere; the list's lead-out is what
        // leaves it, in that stop's colour.
        assertEquals(ConnectorStyle.Solid, rows[2].below!!.style)
        assertEquals(Color.Green, rows[2].below!!.colour)
        assertSame(last, rows[2].stop)
        assertEquals(listOf(3, 3, 3), rows.map { it.count })
    }

    private fun stop(connector: ConnectorStyle, colour: Color) = TimelineStop(
        nodeColour = Color.Unspecified,
        filled = true,
        loading = false,
        connector = connector,
        connectorColour = colour,
        connectorWidth = Dp.Unspecified,
        enabled = true,
        selected = false,
        role = Role.Button,
        onClick = null,
        content = {},
    )
}
