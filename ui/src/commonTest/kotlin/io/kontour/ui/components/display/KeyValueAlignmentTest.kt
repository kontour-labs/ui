package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every value in one column, whatever the labels: asked for as *"can we make it
 * so in key-value list, all keys/values are aligned properly by default (i.e.
 * in two columns)?… people don't seem to know how to use it properly"*. The
 * label width was a floor each row took for itself, so a label longer than it
 * pushed its own value out of line with the rest.
 */
@OptIn(ExperimentalTestApi::class)
class KeyValueAlignmentTest {

    @Test
    fun aLongLabelMovesEveryValueNotOnlyItsOwn() = runComposeUiTest {
        setContent {
            KontourTheme {
                Box(Modifier.size(420.dp, 400.dp)) {
                    KeyValueList {
                        item("Fare", announcement = "3.20") { Box(Modifier.size(8.dp).testTag("short")) }
                        item("Wheelchair-accessible entrance", announcement = "yes") { Box(Modifier.size(8.dp).testTag("long")) }
                        item("Platform", announcement = "2") { Box(Modifier.size(8.dp).testTag("middle")) }
                    }
                }
            }
        }
        waitForIdle()
        fun left(tag: String) = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        val lefts = listOf("short", "long", "middle").map(::left)
        assertTrue(lefts.max() - lefts.min() < 1f, "the values start at $lefts: not one column")
        // And the column was pushed out for the long label, not left at the floor.
        assertTrue(lefts.first() > 120f, "the values start at ${lefts.first()}px, inside a long label")
    }

    @Test
    fun shortLabelsKeepTheFloor() = runComposeUiTest {
        var floor = 0f
        setContent {
            KontourTheme {
                floor = with(androidx.compose.ui.platform.LocalDensity.current) { KeyValueListDefaults.LabelWidth.toPx() }
                Box(Modifier.size(420.dp, 400.dp)) {
                    KeyValueList {
                        item("Fare", announcement = "3.20") { Box(Modifier.size(8.dp).testTag("short")) }
                        item("Platform", announcement = "2") { Box(Modifier.size(8.dp).testTag("middle")) }
                    }
                }
            }
        }
        waitForIdle()
        val left = onNodeWithTag("short", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        val gap = left - floor
        assertTrue(abs(left - onNodeWithTag("middle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left) < 1f)
        assertTrue(gap in 0f..20f, "short labels moved the values to $left from a ${floor}px floor")
    }

    /** In a row that scrolls sideways, which has no width to give: it is as wide as what it shows, and does not throw. */
    @Test
    fun inARowThatScrollsItTakesItsNaturalWidth() = runComposeUiTest {
        setContent {
            KontourTheme {
                androidx.compose.foundation.layout.Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                    KeyValueList {
                        item("Fare", announcement = "3.20") { Box(Modifier.size(8.dp).testTag("short")) }
                        item("Platform", announcement = "2") { Box(Modifier.size(8.dp).testTag("middle")) }
                    }
                }
            }
        }
        waitForIdle()
        // One row a reader finds — the copies measured for the widths are not
        // read — and it is laid out, with room for its value.
        val rows = onAllNodes(androidx.compose.ui.test.hasContentDescription("Fare, 3.20")).fetchSemanticsNodes()
        assertTrue(rows.size == 1, "a reader finds ${rows.size} rows for Fare")
        assertTrue(rows.single().boundsInRoot.width > 100f, "the row is ${rows.single().boundsInRoot.width}px wide")
    }
}
