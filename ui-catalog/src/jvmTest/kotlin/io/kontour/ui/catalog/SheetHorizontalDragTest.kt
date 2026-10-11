package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.KontourTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Across a sheet, a swipe as much across as it is up — a carousel's — is the
 * carousel's, and one that is mostly up or down is still the sheet's, from over
 * the carousel too.
 *
 * A carousel inside a Compose draggable that is waiting on the other axis
 * yields to it on any swipe over thirty degrees from across, whatever that
 * draggable's slop, and the sheet's drag was one: "horizontal dragging in a
 * sheet is sometimes a bit difficult to do".
 */
class SheetHorizontalDragTest {

    private class Probe {
        var row: LazyListState? = null
        var sheet: SheetState? = null
    }

    private fun scene(probe: Probe) = Scene(width = 600, height = 900) {
        KontourTheme(reduceMotion = true) {
            val state = rememberSheetState(detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded), initialDetent = SheetDetent.Half)
            probe.sheet = state
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color(0xFF3355AA)))
                BottomSheet(state = state, windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) {
                    Column {
                        val row = rememberLazyListState()
                        probe.row = row
                        LazyRow(Modifier.fillMaxWidth().height(160.dp), state = row) {
                            items(30) { Box(Modifier.width(140.dp).height(160.dp).background(Color(0xFF11CC55 + it * 0x101))) }
                        }
                        Box(Modifier.fillMaxWidth().height(1200.dp))
                    }
                }
            }
        }
    }

    /** A swipe from the middle of the carousel, [degrees] from across, toward the top left. */
    private fun swipe(degrees: Double): Pair<Int, SheetDetent> {
        val probe = Probe()
        return scene(probe).use { scene ->
            scene.frames(40)
            val rowTop = 900 - 450 + 30f
            val from = Offset(400f, rowTop + 100f)
            val length = 160f
            val rad = Math.toRadians(degrees)
            val to = from + Offset(-(length * cos(rad)).toFloat(), -(length * sin(rad)).toFloat())
            scene.drag(from = from, to = to, steps = 16)
            scene.frames(40)
            val row = probe.row!!
            (row.firstVisibleItemIndex * 1000 + row.firstVisibleItemScrollOffset) to probe.sheet!!.currentDetent
        }
    }

    @Test
    fun aSwipeAsMuchAcrossAsUpIsTheCarousels() {
        val (scrolled, detent) = swipe(degrees = 50.0)
        assertTrue(scrolled > 0, "a swipe fifty degrees from across did not scroll the carousel")
        assertEquals(SheetDetent.Half, detent, "a swipe fifty degrees from across moved the sheet")
    }

    @Test
    fun aSwipeMostlyUpIsStillTheSheets() {
        val (scrolled, detent) = swipe(degrees = 80.0)
        assertEquals(SheetDetent.Expanded, detent, "a swipe up, eighty degrees from across, over the carousel did not move the sheet")
        assertEquals(0, scrolled, "a swipe mostly up scrolled the carousel")
    }
}
