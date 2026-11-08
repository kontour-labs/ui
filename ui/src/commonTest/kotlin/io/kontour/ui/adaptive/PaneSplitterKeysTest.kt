package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The seam between two panes moves from the keyboard. Its progress range let a
 * screen reader set it, and nothing let a keyboard: it took no focus. Found in
 * the November 2026 audit.
 */
@OptIn(ExperimentalTestApi::class)
class PaneSplitterKeysTest {

    private var label = ""

    private fun ComposeUiTest.panes(direction: LayoutDirection) {
        setContent {
            KontourTheme(reduceMotion = true) {
                label = Theme.strings.resizePanes
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box(Modifier.size(1200.dp, 600.dp)) {
                        ListDetailPaneScaffold(
                            focus = PaneFocus.List,
                            onBack = {},
                            list = { Box(Modifier.fillMaxSize()) },
                            detail = { Box(Modifier.fillMaxSize()) },
                            twoPane = true,
                            listWeight = 0.4f,
                            resizable = true,
                        )
                    }
                }
            }
        }
        waitForIdle()
        onNode(hasContentDescription(label)).requestFocus()
    }

    private fun ComposeUiTest.seam(): Float =
        onNode(hasContentDescription(label)).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun ComposeUiTest.range() =
        onNode(hasContentDescription(label)).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range

    private fun ComposeUiTest.press(key: Key): Float {
        onNode(hasContentDescription(label)).performKeyInput { pressKey(key) }
        waitForIdle()
        return seam()
    }

    private fun near(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) < 0.001f, "$what: expected $expected, was $actual")

    @Test
    fun theArrowsAndPageKeysMoveItAndHomeAndEndGoToTheEnds() = runComposeUiTest {
        panes(LayoutDirection.Ltr)
        near(0.42f, press(Key.DirectionRight), "Right")
        near(0.40f, press(Key.DirectionLeft), "Left")
        near(0.50f, press(Key.PageUp), "Page Up")
        near(0.40f, press(Key.PageDown), "Page Down")
        near(range().start, press(Key.MoveHome), "Home")
        near(range().endInclusive, press(Key.MoveEnd), "End")
    }

    @Test
    fun rightToLeftTheLeftArrowWidensTheFirstPane() = runComposeUiTest {
        panes(LayoutDirection.Rtl)
        near(0.42f, press(Key.DirectionLeft), "Left")
    }
}
