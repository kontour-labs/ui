package io.kontour.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.dragdrop.DragType
import io.kontour.ui.dragdrop.rememberDropListState
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Open documents as tabs: sharing the strip's width and then scrolling,
 * closed by ×, middle click and Delete, walked by the arrow keys, offered to a
 * screen reader with their moves and their close, and growing in and shrinking
 * away as they come and go.
 */
@OptIn(ExperimentalTestApi::class)
class DocumentTabsTest {

    private val documents = mutableStateListOf("Timetable", "Fares", "Route map")
    private val closed = mutableListOf<String>()
    private var chosen: String? = "Timetable"

    private fun ComposeUiTest.strip(width: Int = 600, reduceMotion: Boolean = false, below: Boolean = false, touch: Boolean = false) {
        setContent {
            KontourTheme(reduceMotion = reduceMotion) {
                CompositionLocalProvider(LocalInputModality provides if (touch) InputModality.Touch else LocalInputModality.current) {
                    OverlayHost {
                        val state = rememberDropListState(
                            listState = rememberLazyListState(),
                            type = Document,
                            items = documents,
                            key = { it },
                            label = "Open documents",
                            itemLabel = { it },
                            onDrop = {},
                        )
                        Column(Modifier.width(width.dp).height(400.dp)) {
                            DocumentTabs(
                                state = state,
                                value = chosen,
                                onValueChange = { chosen = it },
                                onClose = { closing ->
                                    closed += closing
                                    documents.remove(closing)
                                },
                                onAdd = { documents.add("Untitled") },
                                modifier = Modifier.testTag("strip"),
                            )
                            if (below) Box(Modifier.fillMaxWidth().weight(1f).testTag("page"))
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.leftOf(tab: String) = onNodeWithText(tab).fetchSemanticsNode().boundsInRoot.left

    private fun ComposeUiTest.rightOf(tab: String) = onNodeWithText(tab).fetchSemanticsNode().boundsInRoot.right

    @Test
    fun tabsShareTheStripsWidth() = runComposeUiTest {
        strip()
        // 600dp, less the "+" and a foot at either end, between three.
        val share = with(density) { ((600.dp - 48.dp - 8.dp * 2) / 3).toPx() }
        assertEquals(share, leftOf("Fares") - leftOf("Timetable"), 1.5f, "the tabs do not share the strip")
        assertEquals(share, leftOf("Route map") - leftOf("Fares"), 1.5f, "the tabs do not share the strip")
    }

    /**
     * A lazy row offers its items all the height it is given, and a tab that
     * filled it took the whole of a column's room: the page under the strip
     * had none, and a tab carried off the strip never left it.
     */
    @Test
    fun theStripIsAsTallAsItsTabsAndThePageBelowHasTheRest() = runComposeUiTest {
        strip(below = true)
        val strip = onNodeWithTag("strip").fetchSemanticsNode().boundsInRoot
        val page = onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        // Well over a tab bar's height, and a quarter of the column's room.
        val tall = with(density) { 100.dp.toPx() }
        assertTrue(strip.height < tall, "the strip took ${strip.height}px of the column's room")
        assertEquals(strip.bottom, page.top, 0.5f, "the page should start where the strip ends")
    }

    /**
     * The "+" and a tab's × are level. On a touchscreen each icon button
     * reserves a finger's 48dp, more than a tab's face has under the gap at
     * the strip's top, and where each is held decides where that leaves it.
     */
    @Test
    fun theAddButtonIsLevelWithTheCross() = runComposeUiTest {
        strip()
        assertAddLevelWithCross()
    }

    @Test
    fun onATouchscreenTheAddButtonIsLevelWithTheCross() = runComposeUiTest {
        strip(touch = true)
        assertAddLevelWithCross()
    }

    private fun ComposeUiTest.assertAddLevelWithCross() {
        val add = onNodeWithContentDescription("New tab").fetchSemanticsNode().boundsInRoot.center.y
        val cross = onNodeWithContentDescription("Close Timetable").fetchSemanticsNode().boundsInRoot.center.y
        assertEquals(cross, add, 0.5f, "the \"+\" is not level with the tabs' ×")
    }

    @Test
    fun pastTheirNarrowestTheStripScrolls() = runComposeUiTest {
        documents.addAll((1..12).map { "Document $it" })
        strip()
        val narrowest = with(density) { DocumentTabsDefaults.MinTabWidth.toPx() }
        assertEquals(narrowest, leftOf("Fares") - leftOf("Timetable"), 1.5f, "tabs past the strip's room were not at their narrowest")
    }

    @Test
    fun theCrossClosesItsTab() = runComposeUiTest {
        strip()
        onNodeWithContentDescription("Close Fares").performClick()
        waitForIdle()
        assertEquals(listOf("Fares"), closed)
    }

    @Test
    fun aMiddleClickClosesATab() = runComposeUiTest {
        strip()
        onNodeWithText("Route map").performMouseInput {
            // A mouse starts where the last one left off, or at the window's
            // corner: brought over the tab first, as a hand brings it.
            moveTo(center)
            press(MouseButton.Tertiary)
            release(MouseButton.Tertiary)
        }
        waitForIdle()
        assertEquals(listOf("Route map"), closed)
        assertEquals("Timetable", chosen, "a middle click chose the tab it closed")
    }

    @Test
    fun deleteClosesTheFocusedTabAndTheArrowsWalkThem() = runComposeUiTest {
        strip()
        onNodeWithText("Timetable").requestFocus()
        onNodeWithText("Timetable").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        onNodeWithText("Fares").assertIsFocused()
        onNodeWithText("Fares").performKeyInput { pressKey(Key.MoveEnd) }
        waitForIdle()
        onNodeWithText("Route map").assertIsFocused()
        onNodeWithText("Route map").performKeyInput { pressKey(Key.Delete) }
        waitForIdle()
        assertEquals(listOf("Route map"), closed)
    }

    @Test
    fun aReaderHearsItsPlaceAndIsOfferedItsMovesAndItsClose() = runComposeUiTest {
        strip()
        val node = onNodeWithText("Fares").fetchSemanticsNode()
        assertEquals(1, node.config.getOrNull(SemanticsProperties.CollectionItemInfo)?.columnIndex, "the tab does not say it is the second")
        val actions = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
        assertEquals(listOf("Move left", "Move right", "Close Fares"), actions)
    }

    @Test
    fun aClosedTabShrinksAwayAndTheNextSlidesAlong() = runComposeUiTest {
        strip()
        val before = leftOf("Route map")
        val home = leftOf("Fares")
        mainClock.autoAdvance = false
        documents.remove("Fares")
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val during = leftOf("Route map")
        assertTrue(during > home + 4f, "the tab after a closed one jumped into its place: $before, $during, $home")
        mainClock.autoAdvance = true
        waitForIdle()
        // Flush against the tab before it: two tabs share the strip more widely
        // than three, so not at the closed one's old place.
        assertEquals(rightOf("Timetable"), leftOf("Route map"), 1.5f, "the tab after a closed one did not close up")
    }

    @Test
    fun anOpenedTabGrowsIn() = runComposeUiTest {
        strip()
        mainClock.autoAdvance = false
        documents.add(1, "Untitled")
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        // The room it has, between its neighbours: the opening tab is clipped
        // to what it shows, and at nearly nothing its own bounds read as none.
        val growing = leftOf("Fares") - rightOf("Timetable")
        mainClock.autoAdvance = true
        waitForIdle()
        val grown = leftOf("Fares") - rightOf("Timetable")
        assertTrue(growing < grown - 4f, "an opened tab arrived at its full width: $growing of $grown")
    }

    @Test
    fun underReducedMotionAClosedTabGoesAtOnce() = runComposeUiTest {
        strip(reduceMotion = true)
        val home = leftOf("Fares")
        mainClock.autoAdvance = false
        documents.remove("Fares")
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertTrue(abs(leftOf("Route map") - rightOf("Timetable")) < 1.5f, "under reduced motion a closed tab still shrank: $home")
    }

    private companion object {
        val Document = DragType<String>("document")
    }
}
