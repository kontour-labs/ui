package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An open tooltip goes at a press anywhere but on its control, and the press
 * still does what it was aimed at.
 *
 * Asked for for 0.5.1 as *"if a tooltip is open, then if you tap anywhere else
 * on the screen, it disappears"*. A touch tooltip went only after its four
 * seconds or at a tap on the control it was about.
 */
@OptIn(ExperimentalTestApi::class)
class TooltipPressElsewhereTest {

    private var pressed by mutableIntStateOf(0)
    private var controlPressed by mutableIntStateOf(0)

    @Test
    fun aTouchTooltipGoesAtATapElsewhereAndTheTapStillLands() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { Screen { IconControl() } }
        holdOnTheControl()
        onNodeWithText(Tip).assertExists("a long press on an icon button did not open its tooltip")
        assertEquals(0, controlPressed, "letting go of the long press that opened the tooltip pressed the button too")

        // A tap on the tooltip itself leaves it: *"if you tap the tooltip
        // itself, it disappears … let's change that"*.
        onNodeWithText(Tip).performTouchInput { click() }
        frames(30)
        onNodeWithText(Tip).assertExists("a tap on the tooltip itself put it away")
        assertEquals(0, controlPressed + pressed, "a tap on the tooltip pressed something under it")

        onNodeWithTag(Other).performTouchInput { click() }
        frames(30)
        onNodeWithText(Tip).assertDoesNotExist()
        assertEquals(1, pressed, "the tap that put the tooltip away did not press the button it landed on")
    }

    /**
     * A tooltip on a button opens from a long press at all. It did not: the
     * button's press stopped there, and the long press was looked for after
     * it. Found for 0.5.1, photographing the tap above.
     */
    @Test
    fun aTapOnTheControlPutsItsTooltipAwayAndPressesIt() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { Screen { IconControl() } }
        holdOnTheControl()
        onNodeWithText(Tip).assertExists("a long press on an icon button did not open its tooltip")

        onNodeWithTag(Control).performTouchInput { click() }
        frames(30)
        onNodeWithText(Tip).assertDoesNotExist()
        assertEquals(1, controlPressed, "a tap on the control with its tooltip open did not press it")
    }

    @androidx.compose.runtime.Composable
    private fun IconControl() {
        IconButton(
            onClick = { controlPressed++ },
            icon = Theme.icons.search,
            contentDescription = "Find a stop",
            modifier = Modifier.testTag(Control).tooltip(Tip),
        )
    }

    private fun ComposeUiTest.holdOnTheControl() {
        frames(10)
        onNodeWithTag(Control).performTouchInput { down(center) }
        frames(60)
        onNodeWithTag(Control).performTouchInput { up() }
        frames(30)
    }

    @Test
    fun aTooltipShownByItsCallerIsAskedToGoAtAPressElsewhereAndNotOnItsControl() = runComposeUiTest {
        var visible by mutableStateOf(true)
        var asked = 0
        setContent {
            Screen {
                Box(Modifier.size(48.dp).testTag(Control)) {
                    Tooltip(visible = visible, onDismissRequest = { asked++; visible = false }) { +Tip }
                }
            }
        }
        waitForIdle()
        onNodeWithText(Tip).assertExists()

        onNodeWithTag(Control).performTouchInput { click() }
        waitForIdle()
        assertEquals(0, asked, "a press on the tooltip's own control asked it to go")
        assertTrue(visible)

        onNodeWithText(Tip).performTouchInput { click() }
        waitForIdle()
        assertEquals(0, asked, "a press on the tooltip itself asked it to go")
        assertTrue(visible)

        onNodeWithTag(Other).performTouchInput { click() }
        waitForIdle()
        assertEquals(1, asked, "a press elsewhere did not ask the tooltip to go")
        assertFalse(visible)
        onNodeWithText(Tip).assertDoesNotExist()
        assertEquals(1, pressed, "the press that put the tooltip away did not press the button it landed on")
    }

    @androidx.compose.runtime.Composable
    private fun Screen(control: @androidx.compose.runtime.Composable () -> Unit) {
        KontourTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalInputModality provides InputModality.Touch) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(start = 40.dp, top = 160.dp)) {
                        control()
                        Spacer(Modifier.height(200.dp))
                        Button(onClick = { pressed++ }, modifier = Modifier.testTag(Other)) { +"Plan a trip" }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.frames(count: Int) = repeat(count) { mainClock.advanceTimeByFrame() }

    private companion object {
        const val Tip = "Filter routes"
        const val Control = "control"
        const val Other = "other"
    }
}
