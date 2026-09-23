package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.windowSizeClass
import io.kontour.ui.demo.AdaptiveFrame
import io.kontour.ui.demo.DemoContentForTest
import io.kontour.ui.demo.PaneScaffoldDemo
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The frame the adaptive demos are hosted in, which the reader drags to resize.
 *
 * Three things it has to get right, each a bug it would otherwise have: a card
 * narrower than its minimum must not throw; the readout must follow the drag and
 * not only the size class, which leaves its dp out of `equals`; and the family's
 * richest demo must still open on two panes where there is room for two.
 */
@OptIn(ExperimentalTestApi::class)
class AdaptiveFrameTest {

    @Composable
    private fun Harness(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            KontourTheme { OverlayHost { content() } }
        }
    }

    @Test
    fun aFrameInACardNarrowerThanItsMinimumDoesNotThrow() = runDesktopComposeUiTest(width = 400, height = 600) {
        setContent {
            Harness {
                Box(Modifier.width(200.dp)) {
                    AdaptiveFrame { Text("inside") }
                }
            }
        }
        // 200dp less the 16dp grip is all there is, and the minimum is 240.
        onNodeWithText("Compact · 184dp").assertExists()
        onNodeWithText("inside").assertExists()
    }

    @Test
    fun theReadoutFollowsTheDragAndNotOnlyTheClass() = runDesktopComposeUiTest(width = 1200, height = 600) {
        setContent {
            Harness {
                AdaptiveFrame {
                    // What a readout reading the size class would say: it keeps
                    // its first dp for as long as the class does not change.
                    Text("class says ${windowSizeClass.widthDp.value.toInt()}")
                }
            }
        }
        onNodeWithText("Compact · 360dp").assertExists()

        // Forty dp wider, still compact — the case the size class cannot see.
        onNodeWithContentDescription("Frame width").performTouchInput {
            down(center)
            moveBy(Offset(20f, 0f))
            moveBy(Offset(40f, 0f))
            up()
        }
        waitForIdle()
        // How much of the first move the slop keeps is the detector's business;
        // what matters is that the readout moved and the class did not.
        val readout = onAllNodes(hasText("Compact · ", substring = true))
            .fetchSemanticsNodes().single()
            .config[SemanticsProperties.Text].single().text
        val dp = readout.removePrefix("Compact · ").removeSuffix("dp").toInt()
        assertTrue(dp in 380..420, "the readout said \"$readout\" after a 60dp drag")
        // And the proof that it had to be measured this way.
        onNodeWithText("class says 360").assertExists()

        // Past 600dp by the accessible route, which a screen reader uses.
        onNodeWithContentDescription("Frame width").performSemanticsAction(SemanticsActions.SetProgress) {
            it(700f)
        }
        waitForIdle()
        onNodeWithText("Medium · 700dp").assertExists()
    }

    @Test
    fun thePaneDemoOpensOnTwoPanesWhereThereIsRoom() = runDesktopComposeUiTest(width = 1200, height = 800) {
        setContent { Harness { DemoContentForTest(PaneScaffoldDemo, emptyMap()) } }
        // The list names the stop and so does the detail beside it: two panes.
        // At a phone's width the detail would not be composed until a row was
        // picked, which is the one-pane demo this frame must not turn it into.
        onNodeWithText("Departures, alerts and the route map would go here.").assertExists()
        onNodeWithText("Expanded · 880dp").assertExists()
    }
}
