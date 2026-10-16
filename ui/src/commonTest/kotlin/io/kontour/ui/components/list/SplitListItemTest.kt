package io.kontour.ui.components.list

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Phone
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Icon
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two rows side by side as one row of a list: the width shared as the split
 * says, both as tall as the taller, a square as wide as the row is tall, each
 * half its own control, and in a section as a row is.
 */
@OptIn(ExperimentalTestApi::class)
class SplitListItemTest {

    /** Read in the composition, which is the only place the theme's values are. */
    private var seam = 0.dp
    private var twoLineFloor = 0.dp

    private fun ComposeUiTest.row(
        split: ListItemSplit,
        width: Dp = 360.dp,
        rtl: Boolean = false,
        twoLines: Boolean = false,
        clicks: MutableList<String> = mutableListOf(),
    ) {
        setContent {
            KontourTheme(reduceMotion = true) {
                seam = ListItemDefaults.Spacing
                twoLineFloor = ListItemDefaults.TwoLineMinHeight
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    SplitListItem(modifier = Modifier.width(width).testTag("row"), split = split) {
                        start(onClick = { clicks += "start" }) {
                            +"Wi-Fi"
                            if (twoLines) supporting { +"Home" }
                        }
                        end(onClick = { clicks += "end" }, contentDescription = "Call") {
                            if (split == ListItemSplit.AutoSquare) {
                                leading { Icon(Tabler.Outline.Phone, contentDescription = null, modifier = Modifier.testTag("glyph")) }
                            } else {
                                +"Bluetooth"
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.halves(): Pair<DpRect, DpRect> {
        val buttons = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).fetchSemanticsNodes()
        assertEquals(2, buttons.size, "a split row should be two controls, not ${buttons.size}")
        val (a, b) = buttons.map { node ->
            val box = node.boundsInRoot
            with(density) { DpRect(box.left.toDp(), box.top.toDp(), box.right.toDp(), box.bottom.toDp()) }
        }.sortedBy { it.left }
        return a to b
    }

    private fun near(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs(expected.value - actual.value) <= 1f, "$what: expected $expected, was $actual")

    @Test
    fun halvesShareTheWidthEvenlyAndAreAsTallAsEachOther() = runComposeUiTest {
        row(ListItemSplit.Halves)
        val (start, end) = halves()
        near(start.right - start.left, end.right - end.left, "the two halves' widths")
        near(start.bottom - start.top, end.bottom - end.top, "the two halves' heights")
        near(seam, end.left - start.right, "the seam between them")
        near(360.dp, end.right - start.left, "the row's width")
    }

    @Test
    fun twoToOneGivesTheStartTwiceTheEnd() = runComposeUiTest {
        row(ListItemSplit.TwoToOne)
        val (start, end) = halves()
        near((start.right - start.left) / 2, end.right - end.left, "the end half beside a start twice its width")
    }

    @Test
    fun aSquareHalfIsAsWideAsTheRowIsTall() = runComposeUiTest {
        row(ListItemSplit.AutoSquare)
        val (start, end) = halves()
        near(end.bottom - end.top, end.right - end.left, "the square half")
        near(360.dp - (end.right - end.left) - seam, start.right - start.left, "the half beside the square")
    }

    @Test
    fun aSquareGrowsWithATallerRow() = runComposeUiTest {
        row(ListItemSplit.AutoSquare, twoLines = true)
        val (start, end) = halves()
        assertTrue(end.bottom - end.top >= twoLineFloor - 1.dp, "a two-line row was ${end.bottom - end.top} tall")
        near(start.bottom - start.top, end.bottom - end.top, "the halves' heights")
        near(end.bottom - end.top, end.right - end.left, "the square half of a two-line row")
    }

    @Test
    fun aSquaresGlyphSitsInItsMiddle() = runComposeUiTest {
        row(ListItemSplit.AutoSquare)
        val (_, end) = halves()
        val glyph = onNodeWithTag("glyph", useUnmergedTree = true).getBoundsInRoot()
        near((end.left + end.right) / 2, (glyph.left + glyph.right) / 2, "the square's centre and its content's")
    }

    @Test
    fun eachHalfIsItsOwnControl() = runComposeUiTest {
        val clicks = mutableListOf<String>()
        row(ListItemSplit.Halves, clicks = clicks)
        onNodeWithText("Bluetooth").performClick()
        onNodeWithText("Wi-Fi").performClick()
        waitForIdle()
        assertEquals(listOf("end", "start"), clicks)
    }

    @Test
    fun rightToLeftTheStartHalfIsOnTheRight() = runComposeUiTest {
        row(ListItemSplit.AutoSquare, rtl = true)
        val wifi = onNodeWithText("Wi-Fi", useUnmergedTree = true).getBoundsInRoot()
        val call = onNodeWithTag("glyph", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(wifi.left > call.right, "right to left, the start half's words at ${wifi.left} should be right of the square at ${call.right}")
    }

    @Test
    fun aSectionLinesItsTitleUpWithTheStartHalfsWords() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                ListSection(modifier = Modifier.width(360.dp), title = { +"Connections" }) {
                    SplitListItem(position = GroupPosition.Only) {
                        start(onClick = {}) { +"Wi-Fi" }
                        end(onClick = {}) { +"Bluetooth" }
                    }
                }
            }
        }
        waitForIdle()
        val title = onNodeWithText("Connections").getBoundsInRoot()
        // The words themselves, not the half they are merged into.
        val words = onNodeWithText("Wi-Fi", useUnmergedTree = true).getBoundsInRoot()
        near(words.left, title.left, "the section's title over a split row")
    }

    @Test
    fun aGroupsSplitIsOneRowBetweenItsNeighbours() = runComposeUiTest {
        val clicks = mutableListOf<String>()
        setContent {
            KontourTheme(reduceMotion = true) {
                seam = ListItemDefaults.Spacing
                ListGroup(modifier = Modifier.width(360.dp)) {
                    item("Notifications") { clicks += "notifications" }
                    split(ListItemSplit.Halves) {
                        start(onClick = { clicks += "start" }) { +"Wi-Fi" }
                        end(onClick = { clicks += "end" }) { +"Bluetooth" }
                    }
                    item("Sign out") { clicks += "sign out" }
                }
            }
        }
        waitForIdle()
        val above = onNodeWithText("Notifications").getBoundsInRoot()
        val start = onNodeWithText("Wi-Fi").getBoundsInRoot()
        val end = onNodeWithText("Bluetooth").getBoundsInRoot()
        val below = onNodeWithText("Sign out").getBoundsInRoot()
        near(start.top, end.top, "the two halves' tops")
        near(above.bottom + seam, start.top, "the split row under the row above it")
        near(start.bottom + seam, below.top, "the row below the split row")
        onNodeWithText("Bluetooth").performClick()
        onNodeWithText("Wi-Fi").performClick()
        waitForIdle()
        assertEquals(listOf("end", "start"), clicks)
    }
}
