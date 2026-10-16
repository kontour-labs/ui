package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The seam between two panes goes where it is moved, by a hand or by
 * assistive technology.
 *
 * At a density of 1, so a dp is a pixel and the window's width is the number
 * in the size. A *required* size, because the test's own window is 1024px wide
 * and a plain `size` is held to it: a "2000px" window was 1024px, and every
 * share of it came out at half what was asked.
 */
@OptIn(ExperimentalTestApi::class)
class PaneSeamTest {

    @Composable
    private fun Window(width: Int, modality: InputModality? = null, content: @Composable () -> Unit) {
        KontourTheme(darkTheme = false, reduceMotion = true) {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(width.dp, 600.dp)) {
                    if (modality == null) {
                        content()
                    } else {
                        CompositionLocalProvider(LocalInputModality provides modality, content = content)
                    }
                }
            }
        }
    }

    /**
     * A new `supportingWeight` from the caller, and then a drag.
     *
     * The handle's gesture is started by the first pointer event and runs from
     * then on, with whatever it was given at the time. The weight it moved was
     * remembered *against* `supportingWeight`, so a new one made a new weight,
     * and every drag after it went on writing the old one: the seam stood still
     * under the finger. The first drag is what starts the gesture, so it is
     * here to make the second one the case.
     */
    @Test
    fun theSeamFollowsADragAfterANewWeight() = runComposeUiTest {
        var weight by mutableStateOf(0.32f)
        var mainRight = 0f
        setContent {
            Window(1600) {
                SupportingPaneScaffold(
                    main = { Box(Modifier.fillMaxSize().onGloballyPositioned { mainRight = it.boundsInRoot().right }) },
                    supporting = { Box(Modifier.fillMaxSize()) },
                    twoPane = true,
                    supportingWeight = weight,
                    resizable = true,
                )
            }
        }
        waitForIdle()
        val first = mainRight
        onNodeWithContentDescription("Resize panes").performTouchInput {
            down(center)
            moveBy(Offset(-120f, 0f))
            up()
        }
        waitForIdle()
        assertTrue(mainRight < first - 50f, "the first drag did not move the seam: $first, then $mainRight")

        runOnUiThread { weight = 0.5f }
        waitForIdle()
        val before = mainRight
        onNodeWithContentDescription("Resize panes").performTouchInput {
            down(center)
            moveBy(Offset(-200f, 0f))
            up()
        }
        waitForIdle()
        assertTrue(
            mainRight < before - 100f,
            "after a new supportingWeight a drag of 200px moved the seam from $before to $mainRight",
        )
    }

    /**
     * `setProgress` puts the seam at the fraction asked for.
     *
     * It moved the seam by the difference in pixels *as if the window were
     * 1000px wide*, so on a 2000px window a request for 0.6 from 0.38 went to
     * 0.49. The range it published, 0.2 to 0.8, was not the clamp the drag used
     * either: on this window a pane's 280dp minimum is 0.14 of it.
     */
    @Test
    fun setProgressPutsTheSeamAtTheFractionAsked() = runComposeUiTest {
        // The list's width rather than its right edge: a window wider than the
        // test's own is centred on it and cut off at its sides, and the edge
        // with it.
        var listWidth = 0f
        setContent {
            // A mouse, so the handle is its narrow self and the arithmetic is
            // the panes' alone.
            Window(2000, InputModality.Mouse) {
                ListDetailPaneScaffold(
                    focus = PaneFocus.List,
                    onBack = {},
                    list = { Box(Modifier.fillMaxSize().onGloballyPositioned { listWidth = it.size.width.toFloat() }) },
                    detail = { Box(Modifier.fillMaxSize()) },
                    twoPane = true,
                    resizable = true,
                )
            }
        }
        waitForIdle()

        onNodeWithContentDescription("Resize panes").performSemanticsAction(SemanticsActions.SetProgress) { it(0.6f) }
        waitForIdle()
        val panes = 2000f - PaneScaffoldDefaults.HandleWidth.value
        assertEquals(0.6f * panes, listWidth, 2f, "setProgress(0.6) on a 2000px window")

        val range = onNodeWithContentDescription("Resize panes").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.6f, range.current, 0.001f, "the handle reports where it is")
        assertEquals(280f / 2000f, range.range.start, 0.001f, "the range starts where a pane meets its minimum")
        assertEquals(1f - 280f / 2000f, range.range.endInclusive, 0.001f, "and ends where the other does")

        // Past the end is the end, as a drag there is.
        onNodeWithContentDescription("Resize panes").performSemanticsAction(SemanticsActions.SetProgress) { it(0.95f) }
        waitForIdle()
        assertEquals((1f - 280f / 2000f) * panes, listWidth, 2f, "setProgress(0.95) is held at the detail's minimum")
    }
}
