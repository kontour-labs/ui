package io.kontour.ui.input

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A press elsewhere is seen, and still does what it was aimed at; a press on
 * the node itself is not elsewhere; and switched off, nothing is seen.
 *
 * On the desktop beside the other input tests: CI runs this package's tests
 * on Android's host as well, where a composition cannot be set up.
 */
@OptIn(ExperimentalTestApi::class)
class PressOutsideTest {

    @Test
    fun aPressElsewhereIsSeenAndStillPresses() = runComposeUiTest {
        var outside = 0
        var pressed = 0
        var watching by mutableStateOf(true)
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Row {
                        Text("Picked", Modifier.size(100.dp).testTag("picked").onPressOutside(enabled = watching) { outside++ })
                        Text("Other", Modifier.size(100.dp).testTag("other").clickable { pressed++ })
                    }
                }
            }
        }
        onNodeWithTag("picked").performClick()
        assertEquals(0, outside, "a press on the node is not one outside it")
        onNodeWithTag("other").performClick()
        assertEquals(1, outside, "a press elsewhere was not seen")
        assertEquals(1, pressed, "the press elsewhere was taken, not watched")
        watching = false
        waitForIdle()
        onNodeWithTag("other").performClick()
        assertEquals(1, outside, "switched off, it still saw a press")
    }
}
