package io.kontour.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.shape
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A row that can be both swiped and reordered does one or the other with a
 * gesture, never both: a sideways swipe opens its actions and does not lift it,
 * and a row held until it lifts goes where it is carried, a little sideways
 * wander and all, without swiping.
 */
@OptIn(ExperimentalTestApi::class)
class SwipeReorderTest {

    private val delete = SwipeAction(label = "Delete", icon = SystemIcons.Default.close, onAction = {}, background = Color.Red)

    private class Probe(rows: List<String>) {
        val order = mutableStateListOf(*rows.toTypedArray())
        val swipes = HashMap<String, SwipeActionsState>()
    }

    /**
     * [rows] rows in a list. [touchSlop], in pixels, is a phone's where given: the
     * test host's is 18px at a density of one, more than twice a phone's 8dp,
     * and against it a swipe has room to decide that it never has on a phone.
     */
    private fun ComposeUiTest.list(rows: List<String> = listOf("A", "B", "C", "D"), touchSlop: Float? = null): Probe {
        val probe = Probe(rows)
        setContent {
            val host = LocalViewConfiguration.current
            val configuration = touchSlop?.let { slop -> object : ViewConfiguration by host { override val touchSlop = slop } } ?: host
            CompositionLocalProvider(LocalViewConfiguration provides configuration) {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    val list = rememberLazyListState()
                    val reorder = rememberReorderableState(list) { from, to -> probe.order.add(to, probe.order.removeAt(from)) }
                    LazyColumn(Modifier.fillMaxSize(), state = list) {
                        itemsIndexed(probe.order, key = { _, it -> it }) { index, name ->
                            ReorderableItem(state = reorder, index = index, itemCount = probe.order.size) {
                                val swipe = rememberSwipeActionsState()
                                remember(swipe) { probe.swipes[name] = swipe }
                                SwipeActions(modifier = Modifier.fillMaxWidth().height(72.dp), end = listOf(delete), state = swipe) {
                                    Box(Modifier.fillMaxSize().background(Color.White).testTag(name))
                                }
                            }
                        }
                    }
                }
            }
            }
        }
        waitForIdle()
        return probe
    }

    @Test
    fun aSidewaysSwipeOpensTheRowAndDoesNotLiftIt() = runComposeUiTest {
        val probe = list()
        onNodeWithTag("B").performTouchInput {
            down(center)
            // Slow enough that the hold would have lifted it by the end.
            repeat(20) {
                advanceEventTime(40)
                moveBy(Offset(-12f, 1f))
            }
            up()
        }
        waitForIdle()
        assertEquals(listOf("A", "B", "C", "D"), probe.order.toList(), "a sideways swipe also lifted and moved the row")
        assertEquals(SwipeValue.End, probe.swipes.getValue("B").currentValue, "a sideways swipe on a reorderable row did not open its actions")
    }

    /**
     * A thumb's swipe, not a ruler's: twice as far across as up, about 27°, at a
     * phone's touch slop.
     *
     * In a row that can be held to pick it up, the swipe waits out the hold's
     * wander, 24dp, before it decides, and the list under it takes a finger at
     * 8dp up or down on a phone: at this slope the list has the finger first.
     * The row still has the swipe, because it sees each move before the list
     * does and claims on the next one; the list scrolls a pixel. Checked for
     * Anyways' stops on the way (November 2026), which looked as if they would
     * not swipe. The test above swipes at 12:1 against the host's slop of
     * 18px, where the list never comes into it.
     */
    @Test
    fun aSwipeAtAThumbsSlopeOpensTheRowOverAListThatScrolls() = runComposeUiTest {
        val probe = list(rows = ('A'..'T').map { it.toString() }, touchSlop = PhoneSlop)
        onNodeWithTag("B").performTouchInput {
            down(center)
            repeat(16) {
                advanceEventTime(16)
                moveBy(Offset(-6f, -3f))
            }
            up()
        }
        waitForIdle()
        assertEquals(SwipeValue.End, probe.swipes.getValue("B").currentValue, "a swipe at 2:1 on a reorderable row went to the list")
    }

    /**
     * Anyways' plan's stops on the way, as it builds them: rows with a grip, in a
     * list in an edge sheet, each row's swipe end action its Remove, swiped flat
     * towards the start at a phone's slop.
     */
    @Test
    fun aRowWithAGripInAListInASheetSwipesOpen() = runComposeUiTest {
        val order = mutableStateListOf("From", "Stop", "To")
        val swipe = HashMap<String, SwipeActionsState>()
        setContent {
            val host = LocalViewConfiguration.current
            CompositionLocalProvider(LocalViewConfiguration provides object : ViewConfiguration by host { override val touchSlop = PhoneSlop }) {
                KontourTheme(reduceMotion = true) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        val sheet = io.kontour.ui.sheet.rememberSheetState(
                            detents = listOf(io.kontour.ui.sheet.SheetDetent.Hidden, io.kontour.ui.sheet.SheetDetent.Half, io.kontour.ui.sheet.SheetDetent.Expanded),
                            initialDetent = io.kontour.ui.sheet.SheetDetent.Half,
                        )
                        io.kontour.ui.sheet.BottomSheet(state = sheet, presentation = io.kontour.ui.sheet.SheetPresentation.Edge) {
                            val list = rememberLazyListState()
                            val reorder = rememberReorderableState(list) { from, to -> order.add(to - 1, order.removeAt(from - 1)) }
                            LazyColumn(Modifier.fillMaxSize(), state = list) {
                                item(key = "header") { Box(Modifier.fillMaxWidth().height(56.dp)) }
                                itemsIndexed(order, key = { _, it -> it }) { index, name ->
                                    ReorderableItem(
                                        state = reorder,
                                        index = index + 1,
                                        itemCount = order.size + 1,
                                        handleIcon = SystemIcons.Default.close,
                                    ) {
                                        val state = rememberSwipeActionsState()
                                        remember(state) { swipe[name] = state }
                                        val position = io.kontour.ui.foundation.GroupPosition.of(index, order.size)
                                        SwipeActions(
                                            enabled = name == "Stop",
                                            shape = position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, androidx.compose.foundation.gestures.Orientation.Vertical),
                                            end = if (name == "Stop") listOf(delete) else emptyList(),
                                            state = state,
                                        ) {
                                            ListItem(position = position, modifier = Modifier.testTag(name)) { +name }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("Stop").performTouchInput {
            down(center)
            repeat(12) {
                advanceEventTime(16)
                moveBy(Offset(-10f, 1f))
            }
            up()
        }
        waitForIdle()
        assertEquals(SwipeValue.End, swipe.getValue("Stop").currentValue, "a row with a grip, in a list in a sheet, did not swipe open")
    }

    /** A hold's fingertip roll still lifts the row at a phone's slop, not only at the host's. */
    @Test
    fun aHoldWhoseFingertipWandersStillLiftsTheRowAtAPhonesSlop() = runComposeUiTest {
        val probe = list(touchSlop = PhoneSlop)
        onNodeWithTag("A").performTouchInput {
            down(center)
            repeat(10) {
                advanceEventTime(55)
                moveBy(Offset(-1.6f, 0.3f))
            }
            repeat(9) {
                advanceEventTime(16)
                moveBy(Offset(0f, 16f))
            }
            up()
        }
        waitForIdle()
        assertEquals("A", probe.order[2], "a hold whose fingertip wandered did not lift the row at a phone's slop: ${probe.order}")
        assertEquals(SwipeValue.Resting, probe.swipes.getValue("A").currentValue, "a hold's wander swiped the row")
    }

    @Test
    fun aRowHeldAndCarriedDownWithSomeWanderIsMovedNotSwiped() = runComposeUiTest {
        val probe = list()
        onNodeWithTag("A").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            // Down two rows of 72, drifting sideways as a thumb does.
            repeat(9) {
                advanceEventTime(16)
                moveBy(Offset(-6f, 16f))
            }
            up()
        }
        waitForIdle()
        assertEquals("A", probe.order[2], "a row held and carried down two rows ended up at ${probe.order}")
        assertEquals(SwipeValue.Resting, probe.swipes.getValue("A").currentValue, "a row being carried was swiped open")
    }

    @Test
    fun aHoldWhoseFingertipWandersSidewaysStillLiftsTheRow() = runComposeUiTest {
        val probe = list()
        onNodeWithTag("A").performTouchInput {
            down(center)
            // A fingertip rolling as it presses: sideways, a few px at a time,
            // all within the hold, then carried down two rows.
            repeat(10) {
                advanceEventTime(55)
                moveBy(Offset(-1.6f, 0.3f))
            }
            repeat(9) {
                advanceEventTime(16)
                moveBy(Offset(0f, 16f))
            }
            up()
        }
        waitForIdle()
        assertEquals("A", probe.order[2], "a hold whose fingertip wandered sideways did not lift the row: ${probe.order}")
        assertEquals(SwipeValue.Resting, probe.swipes.getValue("A").currentValue, "a hold's wander swiped the row")
    }

    private companion object {
        /** A phone's touch slop, 8dp, at the test's density of one. */
        const val PhoneSlop = 8f
    }
}
