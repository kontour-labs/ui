package io.kontour.ui.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A tooltip never takes a tap: one aimed at what is under its bubble reaches
 * it. Its bubble scrolls, for text too long for its side at a large type size,
 * and the scroller took every tap on the bubble whether it had anywhere to
 * scroll or not, so a chart's tooltip swallowed the tap on the next sector.
 */
@OptIn(ExperimentalTestApi::class)
class TooltipPassThroughTest {

    @Test
    fun aTapOnTheBubbleReachesWhatIsUnderIt() = runComposeUiTest {
        var pressed = 0
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.size(400.dp)) {
                    Box(Modifier.fillMaxSize().clickable { pressed++ }) {
                        Box(Modifier.size(40.dp).align(Alignment.Center)) {
                            Tooltip(visible = true) { +"Food, 30%" }
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Food, 30%").performTouchInput { click(center) }
        waitForIdle()
        assertEquals(1, pressed, "the tap on the tooltip did not reach the page under it")
    }
}
