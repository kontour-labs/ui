package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The rows a [rememberScrollbarListState] list measures ahead are let go of
 * once they have all been measured, whatever their heights: one row with no
 * height — a conditional item that draws nothing — was never "known", so the
 * measuring never finished and every row it had composed stayed composed.
 */
@OptIn(ExperimentalTestApi::class)
class ScrollbarListStateTest {

    @Test
    fun rowsMeasuredAheadAreLetGoEvenWithARowOfNoHeight() = runComposeUiTest {
        var live = 0
        setContent {
            KontourTheme {
                val state = rememberScrollbarListState()
                LazyColumn(Modifier.size(300.dp, 400.dp), state = state) {
                    items(100) { index ->
                        DisposableEffect(Unit) {
                            live++
                            onDispose { live-- }
                        }
                        Box(Modifier.fillMaxWidth().height(if (index == 50) 0.dp else 40.dp))
                    }
                }
            }
        }
        // Long enough for every row to be measured ahead, a slice a frame.
        repeat(300) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        assertTrue(live < 30, "$live rows are still composed, of a list showing ten")
    }
}
