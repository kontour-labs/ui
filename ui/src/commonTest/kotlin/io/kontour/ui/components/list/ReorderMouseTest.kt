package io.kontour.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A mouse on a reorderable row: a click with a pixel of jitter in it is a
 * click, a drag sideways is the swipe inside the row, and a drag up or down
 * picks the row up and carries it.
 *
 * Reported as *"sometimes it's difficult to pick them up and drag them with a
 * mouse"* and *"it interferes sometimes with swipe actions and even click
 * actions"*: the row took a mouse at an eighth of a dp, in any direction.
 */
@OptIn(ExperimentalTestApi::class)
class ReorderMouseTest {

    private val delete = SwipeAction(label = "Delete", icon = SystemIcons.Default.close, onAction = {}, background = Color.Red)

    private class Probe {
        val order = mutableStateListOf("A", "B", "C", "D")
        val swipes = HashMap<String, SwipeActionsState>()
        val clicked = mutableListOf<String>()
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
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(Color.White)
                                            .clickable { probe.clicked += name }
                                            .testTag(name),
                                    )
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
    fun aClickWithAPixelOfJitterIsAClick() = runComposeUiTest {
        val probe = list()
        onNodeWithTag("B").performMouseInput {
            moveTo(center)
            press()
            advanceEventTime(30)
            moveBy(Offset(0f, 1f))
            advanceEventTime(30)
            moveBy(Offset(1f, 0f))
            release()
        }
        waitForIdle()
        assertEquals(listOf("B"), probe.clicked, "a click with a pixel of jitter on a reorderable row did not click")
        assertEquals(listOf("A", "B", "C", "D"), probe.order.toList(), "a click moved the row")
    }

    @Test
    fun aSidewaysDragIsTheSwipeNotACarry() = runComposeUiTest {
        val probe = list()
        onNodeWithTag("B").performMouseInput {
            moveTo(center)
            press()
            repeat(20) {
                advanceEventTime(16)
                moveBy(Offset(-12f, 2f))
            }
            release()
        }
        waitForIdle()
        assertEquals(listOf("A", "B", "C", "D"), probe.order.toList(), "a sideways mouse drag lifted and moved the row")
        assertEquals(SwipeValue.End, probe.swipes.getValue("B").currentValue, "a sideways mouse drag did not open the row's actions")
        assertEquals(emptyList<String>(), probe.clicked, "a drag clicked")
    }

    @Test
    fun aDragDownCarriesTheRow() = runComposeUiTest {
        val probe = list()
        onNodeWithTag("A").performMouseInput {
            moveTo(center)
            press()
            // Down two rows of 72, with a little sideways in it.
            repeat(12) {
                advanceEventTime(16)
                moveBy(Offset(-2f, 14f))
            }
            release()
        }
        waitForIdle()
        assertEquals("A", probe.order[2], "a mouse dragged down two rows left the list at ${probe.order}")
        assertEquals(SwipeValue.Resting, probe.swipes.getValue("A").currentValue, "a carried row was swiped open")
        assertEquals(emptyList<String>(), probe.clicked, "a carry clicked")
    }
}
