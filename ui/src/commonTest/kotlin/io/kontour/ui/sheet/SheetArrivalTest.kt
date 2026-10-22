package io.kontour.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A sheet resting at `Half` has arrived, as much as one at the top has: the
 * page behind a modal sheet is held back the whole way, with the whole corner,
 * as it is behind a side sheet. Only how much of the sheet shows is partial.
 */
@OptIn(ExperimentalTestApi::class)
class SheetArrivalTest {

    @Test
    fun aSheetRestingAtHalfHasArrivedTheWholeWay() = runComposeUiTest {
        lateinit var state: SheetState
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.size(400.dp, 800.dp)) {
                    state = rememberSheetState(
                        detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
                        initialDetent = SheetDetent.Half,
                    )
                    BottomSheet(state = state) { Box(Modifier.fillMaxWidth().height(1200.dp)) }
                }
            }
        }
        waitForIdle()
        assertTrue(state.visibleFraction < 0.9f, "the sheet at half shows ${state.visibleFraction} of itself, so this measures nothing")
        assertEquals(1f, state.arrivedFraction, "a sheet resting at half has arrived only ${state.arrivedFraction} of the way")
    }
}
