package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `BottomSheet(scrollExpands = false)`: above its lowest size the list scrolls
 * where it is and the sheet stays put, as Apple Maps' does over its map.
 * Asked for by an app that had built it round the sheet with a drag of its own.
 */
@OptIn(ExperimentalTestApi::class)
class SheetScrollsInPlaceTest {

    private class Rig(val state: SheetState, val list: LazyListState)

    private fun ComposeUiTest.sheet(scrollExpands: Boolean, initial: (SheetDetent) -> SheetDetent = { SheetDetent.Half }): Rig {
        lateinit var rig: Rig
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        val peek = SheetDetent.peek(140.dp)
                        val state = rememberSheetState(
                            detents = listOf(peek, SheetDetent.Half, SheetDetent.Expanded),
                            initialDetent = initial(peek),
                        )
                        val list = rememberLazyListState()
                        rig = Rig(state, list)
                        BottomSheet(state = state, presentation = SheetPresentation.Edge, scrollExpands = scrollExpands) { padding ->
                            LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                                items(40) { Text("Stop $it", Modifier.fillMaxWidth().height(56.dp).testTag("row$it")) }
                            }
                        }
                    }
                }
            }
        }
        settle()
        return rig
    }

    private fun ComposeUiTest.settle() = repeat(90) { mainClock.advanceTimeByFrame() }

    /** A slow drag of [by] pixels (negative is up) from just above the screen's foot, let go still. */
    private fun ComposeUiTest.drag(by: Float, then: Float = 0f) {
        val root = onRoot().fetchSemanticsNode().size
        var at = Offset(root.width / 2f, root.height - 40f)
        onRoot().performTouchInput { down(at) }
        mainClock.advanceTimeByFrame()
        for (part in listOf(by, then)) {
            repeat(20) {
                at += Offset(0f, part / 20)
                onRoot().performTouchInput { moveTo(at) }
                mainClock.advanceTimeByFrame()
            }
        }
        mainClock.advanceTimeBy(300)
        onRoot().performTouchInput { up() }
        settle()
    }

    @Test
    fun aboveItsLowestTheListScrollsAndTheSheetStays() = runComposeUiTest {
        val rig = sheet(scrollExpands = false)
        drag(by = -200f)
        assertEquals(SheetDetent.Half, rig.state.currentDetent, "the sheet moved; the list should have")
        assertTrue(rig.list.firstVisibleItemIndex > 0 || rig.list.firstVisibleItemScrollOffset > 100, "the list did not scroll")
    }

    @Test
    fun byDefaultTheSheetOpensFirst() = runComposeUiTest {
        val rig = sheet(scrollExpands = true)
        val half = rig.state.offset
        drag(by = -(half * 0.8f))
        assertEquals(0, rig.list.firstVisibleItemIndex, "the list scrolled before the sheet opened")
        assertEquals(SheetDetent.Expanded, rig.state.currentDetent, "the sheet stayed at half")
    }

    @Test
    fun collapsedADragUpStillBringsItUp() = runComposeUiTest {
        val rig = sheet(scrollExpands = false) { peek -> peek }
        drag(by = -200f)
        assertTrue(rig.state.currentDetent != rig.state.detents.first(), "collapsed, a drag up left the sheet where it was")
    }

    @Test
    fun theListsFootCanBeScrolledOnToTheScreen() = runComposeUiTest {
        val rig = sheet(scrollExpands = false)
        runOnUiThread { kotlinx.coroutines.runBlocking { rig.list.scrollToItem(39) } }
        settle()
        repeat(6) { drag(by = -400f) }
        val last = onAllNodesWithTag("row39", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        val window = onRoot().fetchSemanticsNode().size.height
        assertEquals(SheetDetent.Half, rig.state.currentDetent, "the sheet moved")
        assertTrue(last != null && last.bottom <= window + 0.5f, "the last row is still under the screen: ${last?.bottom} of $window")
    }

    @Test
    fun pulledDownPastTheTopAndBackUpIsTheSheets() = runComposeUiTest {
        val rig = sheet(scrollExpands = false)
        val half = rig.state.offset
        // Down past the list's top, which moves the sheet, then back up.
        drag(by = 120f, then = -120f)
        assertTrue(abs(rig.state.offset - half) < 2f, "the sheet did not come back with the finger: ${rig.state.offset} against $half")
        assertEquals(0, rig.list.firstVisibleItemIndex, "coming back up scrolled the list")
    }
}
