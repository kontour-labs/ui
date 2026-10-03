package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sheet dragged up from half to full height by its scrolling content, under
 * a status bar, climbs with the finger all the way: nothing in it jumps.
 *
 * Reported from the gallery's trip planner: *"with the fully-expanding sheet
 * in your demo apps (mainly in the "plan" sheet that you can drag up from
 * halfway), it sorta jumps around a bit as you expand it to the top,
 * especially if it has scrolling content"*. For the last status bar's height
 * of the climb the content waits below the bar while the sheet's edge goes on
 * to the top, and the peek detent — measured from the sheet's edge to the
 * header — grew with it, a pixel a pixel. A changed detent rebuilds the
 * anchors, and a rebuild with nothing holding the drag, which is every drag a
 * scroll in the content hands on, snaps the sheet to the nearest anchor: the
 * edge and its corners leapt the last 52px to the top in one frame.
 *
 * The window is 360 by 800 at a density of 1, with a 48px status bar.
 */
@OptIn(ExperimentalTestApi::class)
class SheetTopBandTest {

    private var sheetState: SheetState? = null
    private var scrollState: androidx.compose.foundation.ScrollState? = null

    private fun ComposeUiTest.planner(initial: SheetDetent = SheetDetent.Half) {
        setContent {
            KontourTheme(reduceMotion = true) {
                Box(Modifier.requiredSize(360.dp, 800.dp).background(Page).testTag("window")) {
                    val peek = remember { SheetDetent.peek(120.dp) }
                    val state = rememberSheetState(
                        detents = listOf(peek, SheetDetent.Half, SheetDetent.Expanded),
                        initialDetent = initial,
                    )
                    sheetState = state
                    BottomSheet(state = state, windowInsets = WindowInsets(top = StatusBar.dp)) { padding ->
                        val scroll = rememberScrollState()
                        scrollState = scroll
                        Column(Modifier.verticalScroll(scroll).padding(padding)) {
                            SheetHeader(Modifier.sheetPeekAnchor().testTag("header")) { +"Plan a trip" }
                            repeat(30) {
                                Box(Modifier.fillMaxWidth().height(48.dp).background(Color(0xFFEEEEEE)).testTag("row-$it"))
                            }
                        }
                    }
                }
            }
        }
        repeat(60) { mainClock.advanceTimeByFrame() }
    }

    private fun ComposeUiTest.rowTop(): Float = onNodeWithTag("row-6").fetchSemanticsNode().boundsInRoot.top

    @Test
    fun aContentDragToTheTopNeverRunsAheadOfTheFinger() = runComposeUiTest {
        mainClock.autoAdvance = false
        planner()
        var last = rowTop()
        var edge = sheetState!!.offset
        var finger = 0f
        onNodeWithTag("row-2").performTouchInput { down(Offset(centerX, centerY)) }
        // Slowly, a frame a step, from half up through the status bar and on
        // into the content's own scroll.
        repeat(80) { frame ->
            onNodeWithTag("row-2").performTouchInput { moveBy(Offset(0f, -Step)) }
            mainClock.advanceTimeByFrame()
            finger += Step
            val now = rowTop()
            val moved = last - now
            // Held for a status bar's height while the sheet's edge climbs
            // under it, which is the content keeping below the bar; never more
            // than the finger.
            assertTrue(
                moved <= Step + 1f,
                "frame $frame, ${finger}px of finger: the content jumped ${moved}px for the finger's ${Step}px " +
                    "(from $last to $now), the sheet snapped ahead of it",
            )
            last = now
            // The sheet's own edge, which climbs under the status bar while the
            // content waits below it, climbs with the finger too.
            val climbed = edge - sheetState!!.offset
            assertTrue(
                climbed <= Step + 1f,
                "frame $frame, ${finger}px of finger: the sheet's edge jumped ${climbed}px for the finger's ${Step}px " +
                    "(from $edge to ${sheetState!!.offset}), snapped to the top ahead of it",
            )
            edge = sheetState!!.offset
        }
        onNodeWithTag("row-2").performTouchInput { up() }
        assertTrue(sheetState!!.offset <= 0.5f, "the drag did not take the sheet to the top: ${sheetState!!.offset}")
        assertTrue(scrollState!!.value > 0, "the drag did not go on into the content's scroll")
    }

    private companion object {
        const val StatusBar = 48
        const val Step = 6f
        val Page = Color.White
    }
}
