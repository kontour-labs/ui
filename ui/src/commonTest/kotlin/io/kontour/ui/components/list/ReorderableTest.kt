package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A reorderable list's drag: where it may land, and that it always ends.
 */
@OptIn(ExperimentalTestApi::class)
class ReorderableTest {

    private class Probe {
        val order = mutableStateListOf("A", "B", "C")
        val moves = mutableListOf<Pair<Int, Int>>()
        var enabled by mutableStateOf(true)
        lateinit var reorder: ReorderableState
    }

    /**
     * Three rows, 72dp each, and with [footer] an item after them that is not
     * a row. [Probe.moves] records every `onMove`; only one inside the rows is
     * applied, so a move past them is recorded rather than thrown.
     */
    private fun ComposeUiTest.list(footer: Boolean = false): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                val list = rememberLazyListState()
                probe.reorder = rememberReorderableState(list) { from, to ->
                    probe.moves += from to to
                    if (from in probe.order.indices && to in probe.order.indices) {
                        probe.order.add(to, probe.order.removeAt(from))
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), state = list) {
                    itemsIndexed(probe.order, key = { _, it -> it }) { index, name ->
                        ReorderableItem(
                            state = probe.reorder,
                            index = index,
                            enabled = probe.enabled,
                            itemCount = probe.order.size,
                        ) {
                            Box(Modifier.fillMaxWidth().height(72.dp).testTag(name))
                        }
                    }
                    if (footer) {
                        item(key = "footer") {
                            Box(Modifier.fillMaxWidth().height(72.dp).testTag("footer"))
                        }
                    }
                }
            }
        }
        waitForIdle()
        return probe
    }

    /**
     * A row disabled while it is held is put down.
     *
     * Disabling the row takes its gesture away before the release. Were the
     * state to keep the row as held, the row would stay lifted, and as a drag
     * starts only when no row is held, no row in the list could be picked up
     * again.
     */
    @Test
    fun aRowDisabledWhileHeldIsPutDownAndTheNextDragWorks() = runComposeUiTest {
        val probe = list()
        onNodeWithTag("A").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            advanceEventTime(16)
            moveBy(Offset(0f, 8f))
        }
        waitForIdle()
        assertEquals(0, probe.reorder.draggingIndex, "the hold did not pick the row up, so this checks nothing")

        probe.enabled = false
        waitForIdle()
        onNodeWithTag("A").performTouchInput { up() }
        waitForIdle()
        assertNull(probe.reorder.draggingIndex, "the row disabled under the finger is still held")

        probe.enabled = true
        waitForIdle()
        onNodeWithTag("A").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            // Down two rows of 72.
            repeat(9) {
                advanceEventTime(16)
                moveBy(Offset(0f, 16f))
            }
            up()
        }
        waitForIdle()
        assertEquals(listOf("B", "C", "A"), probe.order.toList(), "the drag after it was refused")
    }

    /**
     * The last row carried over a footer stays the last row.
     *
     * The footer is an item of the list and not a row of the caller's
     * collection. The drag used to land on it, reporting a move from 2 to 3 in
     * a collection of three, which the `add(to, removeAt(from))` the
     * documentation shows throws on.
     */
    @Test
    fun aRowCarriedOverAFooterIsNotMovedOntoIt() = runComposeUiTest {
        val probe = list(footer = true)
        val row = onNodeWithTag("C").fetchSemanticsNode().size.height.toFloat()

        runOnIdle {
            probe.reorder.start(2)
            // The dragged row's middle well inside the footer.
            probe.reorder.drag(row * 0.8f)
        }
        waitForIdle()
        runOnIdle { probe.reorder.stop() }
        waitForIdle()

        assertTrue(probe.moves.none { it.second >= 3 }, "the row was moved onto the footer: ${probe.moves}")
        assertEquals(listOf("A", "B", "C"), probe.order.toList())
    }

    /** And a row above the footer still moves among the rows. */
    @Test
    fun aListWithAFooterStillReordersItsRows() = runComposeUiTest {
        val probe = list(footer = true)
        val row = onNodeWithTag("A").fetchSemanticsNode().size.height.toFloat()

        runOnIdle {
            probe.reorder.start(0)
            probe.reorder.drag(row)
        }
        waitForIdle()
        runOnIdle { probe.reorder.stop() }
        waitForIdle()

        assertEquals(listOf(0 to 1), probe.moves)
        assertEquals(listOf("B", "A", "C"), probe.order.toList())
    }

    /**
     * Disabled, a row offers no move up or move down.
     *
     * The drag was withheld and the actions were not, so a screen reader could
     * reorder a list that looked, and was meant to be, fixed.
     */
    @Test
    fun aDisabledRowOffersNoMoves() = runComposeUiTest {
        val probe = list()
        val withMoves = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)
        onAllNodes(withMoves).assertCountEquals(3)

        probe.enabled = false
        waitForIdle()

        onAllNodes(withMoves).assertCountEquals(0)
    }
}
