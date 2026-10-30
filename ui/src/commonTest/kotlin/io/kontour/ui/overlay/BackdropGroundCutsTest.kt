package io.kontour.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The receding page's corners are cut once, by the first sheet, and kept.
 *
 * The band round a receding page has a corner at each of the page's, cut from
 * the page's outline by four path differences for each of the thirteen steps
 * the corner ramps through. Those were cut again on every step a sheet's
 * arrival crossed, on every step of its departure, on every step a drag took it
 * across, and all over again for the next sheet: measured at 23 for one sheet
 * coming up, dragged down and back and going down, and as many again for each
 * sheet after it, each cut about 90µs of path operations on a desktop. Kept by the host, the first sheet cuts each
 * step it reaches once, and nothing after it cuts any.
 */
@OptIn(ExperimentalTestApi::class)
class BackdropGroundCutsTest {

    @Test
    fun aSheetComingAndGoingCutsThePagesCornersOnce() {
        var firstSheet = 0
        var afterwards = 0
        var travelled = 0f
        runComposeUiTest {
            val host = OverlayHostState()
            var visible by mutableStateOf(false)
            lateinit var sheet: SheetState
            mainClock.autoAdvance = false
            setContent {
                KontourTheme {
                    Box(Modifier.size(400.dp, 800.dp)) {
                        OverlayHost(Modifier.fillMaxSize(), state = host) {
                            Column(Modifier.fillMaxSize().background(Color.White)) {
                                repeat(20) { Text("Stop $it, Midland line") }
                            }
                            sheet = rememberSheetState(
                                detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                                initialDetent = SheetDetent.Hidden,
                            )
                            ModalBottomSheet(visible = visible, onDismissRequest = { visible = false }, state = sheet) {
                                Box(Modifier.fillMaxWidth().height(320.dp))
                            }
                        }
                    }
                }
            }
            repeat(4) { mainClock.advanceTimeByFrame() }

            // The first sheet: up, pulled most of the way down and let go, which
            // crosses the steps down and back up again, and closed. Each step it
            // reaches is cut once, whichever of those reaches it first.
            visible = true
            repeat(Frames) { mainClock.advanceTimeByFrame() }
            val from = sheet.offset
            repeat(30) {
                sheet.anchoredState.dispatchRawDelta(6f)
                mainClock.advanceTimeByFrame()
            }
            travelled = sheet.offset - from
            repeat(Frames) { mainClock.advanceTimeByFrame() }
            visible = false
            repeat(Frames) { mainClock.advanceTimeByFrame() }
            firstSheet = host.groundCuts

            // And the next two, which have nothing left to cut.
            repeat(2) {
                visible = true
                repeat(Frames) { mainClock.advanceTimeByFrame() }
                visible = false
                repeat(Frames) { mainClock.advanceTimeByFrame() }
            }
            afterwards = host.groundCuts - firstSheet
        }

        assertTrue(firstSheet > 0, "the page's corners were never cut, so nothing receded and this measured nothing")
        assertTrue(travelled > 100f, "the sheet was dragged ${travelled}px, so the drag crossed no steps")
        assertTrue(
            firstSheet <= Steps,
            "one sheet coming up, dragged and going down cut the page's corners $firstSheet times, for " +
                "$Steps steps: a step it had cut was cut again",
        )
        assertEquals(
            0,
            afterwards,
            "two more sheets coming and going cut the page's corners $afterwards more times, after the " +
                "first had cut them: they are being thrown away rather than kept",
        )
    }

    private companion object {
        const val Frames = 60

        /** The corner's ramp: twelve steps, thirteen shapes. */
        const val Steps = 13
    }
}
