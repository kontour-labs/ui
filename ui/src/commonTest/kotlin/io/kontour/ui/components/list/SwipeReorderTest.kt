package io.kontour.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
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

    private class Probe {
        val order = mutableStateListOf("A", "B", "C", "D")
        val swipes = HashMap<String, SwipeActionsState>()
    }

    private fun ComposeUiTest.list(): Probe {
        val probe = Probe()
        setContent {
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
}
