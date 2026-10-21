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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
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
 * screen reader with their moves and their close, growing in and shrinking
 * away as they come and go, and opening a tab's or the "+"'s menu by a right
 * click, a finger held still, Shift+F10 and a screen reader.
 */
@OptIn(ExperimentalTestApi::class)
class DocumentTabsTest {

    private val documents = mutableStateListOf("Timetable", "Fares", "Route map")
    private val closed = mutableListOf<String>()
    private val dropped = mutableListOf<String>()
    private var chosen: String? = "Timetable"

    private fun ComposeUiTest.strip(
        width: Int = 600,
        reduceMotion: Boolean = false,
        below: Boolean = false,
        touch: Boolean = false,
        enabled: Boolean = true,
        closeOnMiddleClick: Boolean = true,
        menus: Boolean = false,
    ) {
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
                            onDrop = { dropped += it.payload },
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
                                enabled = enabled,
                                closeOnMiddleClick = closeOnMiddleClick,
                                tabMenu = if (menus) { tab -> item("Duplicate $tab") {} } else null,
                                addMenu = if (menus) { { item("Reopen closed tab") {} } } else null,
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
    fun withCloseOnMiddleClickOffAMiddleClickDoesNothing() = runComposeUiTest {
        strip(closeOnMiddleClick = false)
        onNodeWithText("Route map").middleClick()
        waitForIdle()
        assertEquals(emptyList(), closed, "a middle click closed a tab with the switch off")
        assertEquals("Timetable", chosen, "a middle click chose a tab")
    }

    /**
     * A right click on a tab opens its menu, with that tab, and leaves the
     * selected tab selected: right-clicking a tab behind it in a browser does
     * not open it.
     */
    @Test
    fun aRightClickOpensTheTabsMenuWithoutChoosingIt() = runComposeUiTest {
        strip(menus = true)
        onNodeWithText("Fares").rightClick()
        waitForIdle()
        onNodeWithText("Duplicate Fares").assertExists("a right click did not open the tab's menu")
        assertEquals("Timetable", chosen, "a right click chose the tab")
    }

    /**
     * A finger held still on a tab picks it up, as it always has, and opens
     * its menu there; let go where it was, the menu stays open, and nothing
     * is chosen or moved.
     */
    @Test
    fun aFingerHeldStillOnATabOpensItsMenu() = runComposeUiTest {
        strip(touch = true, menus = true)
        onNodeWithText("Fares").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            // A held finger is never quite still: a wobble well inside the
            // hold's budget, which also hands the test the time held.
            advanceEventTime(16)
            moveBy(Offset(2f, 0f))
        }
        waitForIdle()
        onNodeWithText("Duplicate Fares").assertExists("a finger held still did not open the tab's menu")
        onNodeWithText("Fares").performTouchInput { up() }
        waitForIdle()
        onNodeWithText("Duplicate Fares").assertExists("letting go of the hold closed the menu it opened")
        assertEquals("Timetable", chosen, "a hold let go of where it was chose the tab")
        assertEquals(emptyList(), dropped, "a hold let go of where it was moved the tab")
    }

    /**
     * Held and then moved on, the finger carries the tab, as a drop list's
     * does, and the menu the hold opened closes as it sets off.
     */
    @Test
    fun aFingerHeldThenMovedCarriesTheTabAndLeavesNoMenu() = runComposeUiTest {
        strip(touch = true, menus = true)
        val pitch = leftOf("Route map") - leftOf("Fares")
        onNodeWithText("Fares").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            advanceEventTime(16)
            moveBy(Offset(2f, 0f))
        }
        waitForIdle()
        onNodeWithText("Duplicate Fares").assertExists("the hold did not open the menu, so this checks nothing")
        assertEquals("Timetable", chosen, "a tab held still was chosen before it set off")
        onRoot().performTouchInput {
            repeat(12) {
                advanceEventTime(16)
                moveBy(Offset(pitch * 1.1f / 12, 0f))
            }
        }
        waitForIdle()
        onNodeWithText("Duplicate Fares").assertDoesNotExist()
        assertEquals("Fares", chosen, "a tab carried off by a finger was not chosen")
        onRoot().performTouchInput { up() }
        waitForIdle()
        assertEquals(listOf("Fares"), dropped, "the hold that moved did not carry the tab")
    }

    @Test
    fun aRightClickOnTheAddButtonOpensItsMenu() = runComposeUiTest {
        strip(menus = true)
        onNodeWithContentDescription("New tab").rightClick()
        waitForIdle()
        onNodeWithText("Reopen closed tab").assertExists("a right click on the \"+\" did not open its menu")
        assertEquals(3, documents.size, "a right click on the \"+\" opened a tab")
    }

    @Test
    fun aFingerHeldStillOnTheAddButtonOpensItsMenuAndNoTab() = runComposeUiTest {
        strip(touch = true, menus = true)
        onNodeWithContentDescription("New tab").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            up()
        }
        waitForIdle()
        onNodeWithText("Reopen closed tab").assertExists("a finger held on the \"+\" did not open its menu")
        assertEquals(3, documents.size, "the hold on the \"+\" was also a press, and opened a tab")
    }

    @Test
    fun aScreenReaderAndTheKeyboardOpenTheMenus() = runComposeUiTest {
        strip(menus = true)
        val actions = onNodeWithText("Fares").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf("Move left", "Move right", "Close Fares", "More options"), actions.map { it.label })
        runOnIdle { actions.last().action() }
        waitForIdle()
        onNodeWithText("Duplicate Fares").assertExists("a screen reader's \"More options\" did not open the tab's menu")
        // The add button's, and Shift+F10 on the focused tab.
        val add = onNodeWithContentDescription("New tab").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf("More options"), add.map { it.label })
        onNodeWithText("Timetable").requestFocus()
        onNodeWithText("Timetable").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.F10) } }
        waitForIdle()
        onNodeWithText("Duplicate Timetable").assertExists("Shift+F10 did not open the focused tab's menu")
    }

    @Test
    fun disabledTheStripOpensNoMenu() = runComposeUiTest {
        strip(enabled = false, menus = true)
        onNodeWithText("Fares").rightClick()
        onNodeWithContentDescription("New tab").rightClick()
        waitForIdle()
        onNodeWithText("Duplicate Fares").assertDoesNotExist()
        onNodeWithText("Reopen closed tab").assertDoesNotExist()
        onNodeWithText("Route map").middleClick()
        waitForIdle()
        assertEquals(emptyList(), closed, "a disabled strip closed a tab")
    }

    // A mouse starts where the last one left off, or at the window's corner:
    // brought over the node first, as a hand brings it.
    private fun SemanticsNodeInteraction.rightClick() = performMouseInput {
        moveTo(center)
        press(MouseButton.Secondary)
        release(MouseButton.Secondary)
    }

    private fun SemanticsNodeInteraction.middleClick() = performMouseInput {
        moveTo(center)
        press(MouseButton.Tertiary)
        release(MouseButton.Tertiary)
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

    /**
     * Closed by Delete, the focused tab hands the focus to the tab after it,
     * or at the end to the one before, as a browser's strip does.
     *
     * Found in the 0.5.0 review, 2026-10-21: the focus went with the tab, to
     * the window, and a reader closing tabs from the keyboard had to find the
     * strip again after each one.
     */
    @Test
    fun deleteHandsTheFocusToTheNextTabOrAtTheEndThePrevious() = runComposeUiTest {
        strip()
        // The strip's one stop is the selected tab; the arrows walk on from it.
        onNodeWithText("Timetable").requestFocus()
        onNodeWithText("Timetable").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        onNodeWithText("Fares").assertIsFocused()
        onNodeWithText("Fares").performKeyInput { pressKey(Key.Delete) }
        waitForIdle()
        assertEquals(listOf("Fares"), closed)
        onNodeWithText("Route map").assertIsFocused()
        onNodeWithText("Route map").performKeyInput { pressKey(Key.Backspace) }
        waitForIdle()
        assertEquals(listOf("Fares", "Route map"), closed)
        onNodeWithText("Timetable").assertIsFocused()
    }

    /**
     * A closing tab starts from the width it was drawn at, not from a width
     * the strip had before it last changed.
     *
     * Found in the 0.5.0 review, 2026-10-21: the width a closing tab was
     * built at was noted on recomposition, and the tabs' width animates
     * without one, so after a "+" it was the width before the tabs narrowed.
     * Closed, a tab at 187dp began its shrink at 240, grew a third wider in
     * a frame and shoved its neighbours along.
     */
    @Test
    fun aClosedTabStartsItsShrinkAtTheWidthItWasDrawnAt() = runComposeUiTest {
        // 1000dp: three tabs at their widest, 240dp, and five at 187.
        strip(width = 1000)
        documents.add("Untitled")
        documents.add("Untitled 2")
        waitForIdle()
        val narrowed = with(density) { ((1000.dp - 48.dp - 8.dp * 2) / 5).toPx() }
        assertEquals(narrowed, leftOf("Route map") - leftOf("Fares"), 1.5f, "five tabs did not narrow, so this measured nothing")
        mainClock.autoAdvance = false
        documents.remove("Fares")
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        // Where it was, between its neighbours, which have not yet widened.
        val closing = leftOf("Route map") - rightOf("Timetable")
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(closing <= narrowed + 1.5f, "a tab closed at ${narrowed}px began its shrink ${closing}px wide")
    }

    /**
     * A strip keeps a focus requester for the tabs it has and the one being
     * carried, and lets go of the rest.
     *
     * Found in the 0.5.0 review, 2026-10-21: it kept one for every tab it had
     * ever shown. Driven as the strip drives it, after each composition.
     */
    @Test
    fun aStripLetsGoOfTheFocusOfTabsThatHaveGone() {
        val strip = StripState()
        val open = mutableListOf<String>()
        repeat(200) { opened ->
            open += "Document $opened"
            // Each tab's requester asked for as the strip composes it, and
            // all but the newest two closed.
            open.forEach { strip.requester(it) }
            while (open.size > 2) open.removeAt(0)
            strip.forgetGone(open.toSet(), held = null)
        }
        assertEquals(2, strip.requesters, "a strip of two tabs kept a requester for ${strip.requesters}")
        strip.requester("Carried")
        strip.forgetGone(open.toSet(), held = "Carried")
        assertEquals(3, strip.requesters, "the tab being carried lost its requester")
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
