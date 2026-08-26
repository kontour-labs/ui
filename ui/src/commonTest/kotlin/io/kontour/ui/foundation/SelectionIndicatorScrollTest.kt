package io.kontour.ui.foundation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scrolling a group of selectable items recomposes none of them.
 *
 * `selectionIndicatorItem` used to write two pieces of **snapshot** state from
 * `onGloballyPositioned` — the item's coordinates and a `layoutVersion` counter —
 * and take that counter as a `LaunchedEffect` key. `onGloballyPositioned` fires
 * on every frame of a scroll, for every attached item, so each frame produced one
 * state write, one recomposition and one cancelled-and-relaunched coroutine *per
 * item*.
 *
 * The navigation drawer on the documentation site has 122 items in a plain
 * column, so that was 122 of each, sixty times a second, to move an indicator
 * that had not moved: the group scrolls with its own anchor, so the item's
 * position *relative to the anchor* — which is the only thing the indicator wants
 * — is exactly what does not change during a scroll.
 *
 * Forty items rather than 122 because the count is not the point; per-frame is.
 */
@OptIn(ExperimentalTestApi::class)
class SelectionIndicatorScrollTest {

    @Test
    fun scrollingDoesNotRecomposeTheItems() {
        val counted = Compositions()
        var scroll: ScrollState? = null

        runComposeUiTest {
            setContent {
              KontourTheme {
                val state = rememberSelectionIndicatorState()
                val scrollState = rememberScrollState().also { scroll = it }

                // The indicator box sits *inside* the scroll container, which is
                // where every caller in the library puts it: the anchor and the
                // items then scroll together and the offset never enters the
                // arithmetic.
                Box(Modifier.fillMaxWidth().height(200.dp).verticalScroll(scrollState)) {
                    SelectionIndicatorBox(
                        state = state,
                        sizing = IndicatorSizing.Fill,
                        indicator = {},
                    ) {
                        Column {
                            repeat(Items) { index ->
                                Item(index, counted)
                            }
                        }
                    }
                }
              }
            }
            waitForIdle()

            val settled = counted.count
            assertTrue(settled > 0, "nothing composed at all")

            repeat(Frames) { scroll!!.dispatchRawDelta(12f) }
            waitForIdle()

            assertTrue(scroll!!.value > 0, "the list never actually scrolled")
            assertEquals(
                settled,
                counted.count,
                "scrolling $Frames frames recomposed the items " +
                    "${counted.count - settled} time(s). Nothing about an item " +
                    "changed: the group scrolls with its own anchor, so the only " +
                    "thing the indicator measures is the one thing a scroll does " +
                    "not move.",
            )
        }
    }

    private companion object {
        const val Items = 40
        const val Frames = 20
    }
}

/**
 * One row, counting its own compositions.
 *
 * The count has to be taken in the scope that *calls* `selectionIndicatorItem`,
 * because that is the scope a state read inside it invalidates — and re-running
 * it is the cost. Counting inside a child instead measures nothing: a child
 * taking only stable parameters skips, so it stays put while its parent
 * recomposes around it, and the test passes against the very bug it names.
 */
@Composable
private fun Item(index: Int, counted: Compositions) {
    counted.count++
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .selectionIndicatorItem(index, selected = index == 0)
    )
}

/** Stable, so it is not itself a reason for anything to recompose. */
@Stable
private class Compositions {
    var count = 0
}
