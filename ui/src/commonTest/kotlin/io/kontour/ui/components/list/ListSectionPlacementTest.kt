package io.kontour.ui.components.list

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A section places nothing in its body while it is measuring.
 *
 * It asked its body for an alignment line during measure, to know whether the
 * body began with a row, and Compose answers that by laying the body out then
 * and there: its rows were placed inside the section's measure. In a lazy
 * list's row on a phone that placed a node Compose had not filed yet, and the
 * app crashed: *"LayoutNode … not found in RectList"*, from a row's touch
 * target, under the section's `startsWithRow`. The section now reads the line
 * where it places the body.
 */
@OptIn(ExperimentalTestApi::class)
class ListSectionPlacementTest {

    private var sectionMeasuring = false
    private var placedWhileMeasuring = false

    /** Marks the time the section spends measuring. */
    private val aroundTheSection = Modifier.layout { measurable, constraints ->
        sectionMeasuring = true
        val placeable = try {
            measurable.measure(constraints)
        } finally {
            sectionMeasuring = false
        }
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    /** Notices being placed while the section measures. */
    private val inTheBody = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            if (sectionMeasuring) placedWhileMeasuring = true
            placeable.place(0, 0)
        }
    }

    @Test
    fun aSectionPlacesNothingWhileItMeasures() = runComposeUiTest {
        setContent {
            KontourTheme {
                ListSection(aroundTheSection.width(320.dp), title = { +"Trip" }, footer = { +"Fares are capped daily" }) {
                    ListItem(inTheBody, position = GroupPosition.First) { +"From" }
                    ListItem(inTheBody, position = GroupPosition.Last) { +"To" }
                }
            }
        }
        waitForIdle()
        assertTrue(!placedWhileMeasuring, "a row in the section's body was placed while the section was still measuring")
    }

    /** And a body that is not rows still has its words flush, once it has been placed. */
    @Test
    fun aParagraphStillHasItsTitleFlushWithIt() = runComposeUiTest {
        setContent {
            KontourTheme {
                ListSection(aroundTheSection.width(320.dp), title = { +"About" }) {
                    Text("A paragraph, not a row.", modifier = inTheBody)
                }
            }
        }
        waitForIdle()
        assertTrue(!placedWhileMeasuring, "the paragraph was placed while the section was still measuring")
    }
}
