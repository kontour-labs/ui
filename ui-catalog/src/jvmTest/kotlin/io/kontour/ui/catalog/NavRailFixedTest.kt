package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavRailDefaults
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A rail with no `onExpandedChange` is fixed at whatever `expanded` says.
 *
 * `NavRail(expanded = true)` is how an app asks for a permanent sidebar, and it
 * is what `NavigationSuiteScaffold` puts up on a desktop. It used to be ignored
 * without a toggle — the flag only meant anything to a rail that could be
 * toggled — so a sidebar was a separate component, and the two drifted. Now the
 * flag is honoured either way and the toggle is what `onExpandedChange` buys:
 * so a fixed sidebar has to show its labels, be the expanded width, and have no
 * toggle that would change nothing.
 *
 * The header is a `ColumnScope` for the same reason the footer is: a logo or an
 * account row is laid out as a column's child, and `Modifier.align` inside it is
 * the column's alignment. That is a compile-time claim as much as a runtime one,
 * so the second test is written the way an app would write it.
 */
@OptIn(ExperimentalTestApi::class)
class NavRailFixedTest {

    @Test
    fun aFixedSidebarShowsItsLabelsAndNoToggle() = runComposeUiTest {
        var toggle by mutableStateOf(false)
        var expandedWidth = 0f
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                expandedWidth = with(density) { NavRailDefaults.ExpandedWidth.toPx() }
                Box(Modifier.fillMaxSize()) {
                    NavRail(
                        modifier = Modifier.testTag("rail"),
                        expanded = true,
                        onExpandedChange = if (toggle) ({}) else null,
                    ) {
                        item(true, {}, key = "stops", icon = Tabler.Outline.MapPin) { +"Stops" }
                        item(false, {}, key = "routes", icon = Tabler.Outline.Bus) { +"Routes" }
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithText("Stops").fetchSemanticsNode()
        onNodeWithText("Routes").fetchSemanticsNode()
        onAllNodesWithContentDescription("Collapse navigation").assertCountEquals(0)
        onAllNodesWithContentDescription("Expand navigation").assertCountEquals(0)
        val width = onNodeWithTag("rail").fetchSemanticsNode().boundsInRoot.width
        assertTrue(
            abs(width - expandedWidth) <= 1f,
            "a fixed NavRail(expanded = true) is ${width}px wide, not the ${expandedWidth}px of an expanded rail",
        )

        // The same rail given a toggle has one, so the absence above is the
        // missing callback's doing and not a finder that could never match.
        toggle = true
        waitForIdle()
        onNodeWithContentDescription("Collapse navigation").fetchSemanticsNode()
    }

    /**
     * `header = { Box(Modifier.align(Alignment.CenterHorizontally)) }` compiles,
     * and centres the box in the header's column.
     *
     * Against a wider sibling rather than against the rail: the header is a
     * column at the rows' leading edge, as wide as what is in it, and the rail's
     * documentation promises that rather than a column across the rail. A
     * start-aligned sibling is measured too, so a centre that is really the
     * column's start cannot pass.
     */
    @Test
    fun theHeaderIsAColumn() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize()) {
                    NavRail(
                        expanded = true,
                        header = {
                            Box(Modifier.width(160.dp).height(8.dp).testTag("wide"))
                            Box(Modifier.size(24.dp).align(Alignment.CenterHorizontally).testTag("centred"))
                            Box(Modifier.size(24.dp).testTag("start"))
                        },
                    ) {
                        item(true, {}, key = "stops", icon = Tabler.Outline.MapPin) { +"Stops" }
                    }
                }
            }
        }
        waitForIdle()

        val wide = onNodeWithTag("wide").fetchSemanticsNode().boundsInRoot
        val centred = onNodeWithTag("centred").fetchSemanticsNode().boundsInRoot
        val start = onNodeWithTag("start").fetchSemanticsNode().boundsInRoot

        assertTrue(
            abs(centred.center.x - wide.center.x) <= 1f,
            "a header child aligned CenterHorizontally is centred at x=${centred.center.x}, not on its " +
                "column's centre at x=${wide.center.x}",
        )
        assertTrue(
            abs(start.left - wide.left) <= 1f && centred.left - start.left > 10f,
            "the header's unaligned child starts at x=${start.left} and the centred one at " +
                "x=${centred.left} against a column from x=${wide.left} — alignment did nothing",
        )
    }
}
