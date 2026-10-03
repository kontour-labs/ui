package io.kontour.ui.components.list

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bike
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A row that opens onto more of itself: the body appears inside the row's own
 * card, under the header's words, and the header says whether it is open.
 *
 * Asked for as *"a component that's like the expandable list item, but it all
 * expands in the same item, rather than one or more items underneath"*.
 */
@OptIn(ExperimentalTestApi::class)
class DisclosureListItemTest {

    private val header = "Can I take my bike?"
    private val body = "Outside the peaks, in the marked carriage."

    private val theHeader =
        hasText(header) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun aPressOpensTheBodyInsideTheSameRowUnderTheWords() = runComposeUiTest {
        var open by mutableStateOf(false)
        setContent {
            KontourTheme(reduceMotion = true) {
                DisclosureListItem(
                    expanded = open,
                    onExpandedChange = { open = it },
                    modifier = Modifier.width(360.dp).testTag("row"),
                    header = {
                        +header
                        leading { +Tabler.Outline.Bike }
                    },
                ) {
                    Text(body)
                }
            }
        }

        // Shut: the header says so, and there is nothing else to read.
        onNode(theHeader).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        onNodeWithText(body).assertDoesNotExist()
        val shut = onNodeWithTag("row").getBoundsInRoot()

        onNode(theHeader).performClick()
        waitForIdle()
        assertEquals(true, open)
        onNode(theHeader).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))

        // Open: the row itself grew, and the body is inside it, below the header.
        val row = onNodeWithTag("row").getBoundsInRoot()
        val text = onNodeWithText(body).getBoundsInRoot()
        val title = onNodeWithText(header, useUnmergedTree = true).getBoundsInRoot()
        assertTrue(row.bottom > shut.bottom, "the row did not grow: ${shut.bottom} to ${row.bottom}")
        assertTrue(
            text.top >= shut.bottom - 1.dp && text.bottom <= row.bottom,
            "the body is at ${text.top}–${text.bottom}, not inside the row between ${shut.bottom} and ${row.bottom}",
        )
        // Under the words, past the leading icon.
        assertTrue(
            abs((text.left - title.left).value) <= 1f,
            "the body starts at ${text.left} and the title at ${title.left}",
        )

        // And shut again.
        onNode(theHeader).performClick()
        waitForIdle()
        onNodeWithText(body).assertDoesNotExist()
        assertEquals(shut.bottom, onNodeWithTag("row").getBoundsInRoot().bottom)
    }
}
