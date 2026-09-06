package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private const val Hint = "Optional"
private const val Error = "Enter a station on the Fremantle or Midland line"

/**
 * The message under a field arrives all at once, not left to right.
 *
 * The slot holding the helper and the error expands *downward* — that is the
 * whole of the motion it wants. It was also arriving from the left, and this is
 * the second report of that.
 *
 * ### The mechanism, and why the first fix was only half
 *
 * `AnimatedContent` measures its children against the incoming constraints and
 * sizes its inner layout to the largest, then reports an *interpolated* size
 * from an outer modifier and places the full-size child at `TopStart`. So the
 * message is laid out at its full width and overflows a container that is still
 * growing.
 *
 * `SizeTransform(clip = false)` stops `AnimatedContent` clipping that overflow,
 * and was the first fix. It leaves the container still animating its width — and
 * the `AnimatedSlot` around it is an `AnimatedVisibility`, whose
 * `expandVertically`/`shrinkVertically` default to `clip = true` and whose
 * clipping layer is sized to what its content measures. The reveal did not stop;
 * it moved one node out.
 *
 * ### What is measured
 *
 * `SemanticsNode.size` is the node's own measured size. `SemanticsNode.boundsInRoot`
 * goes through `localBoundingBoxOf` with `clipBounds` defaulted to `true`, so it
 * is the same box **after every ancestor clip has been applied**. The two
 * disagreeing is precisely "some of this text is not on screen", stated without
 * reference to a colour, a font, a density or a platform — all of which cancel,
 * because the comparison is one node against itself.
 *
 * ### Which of the two methods below is the failing-first one
 *
 * Not the one you would design first. An error arriving over an existing hint
 * has exactly one moving part — the slot stays visible, so no fade and no
 * vertical shrink, and only the width animates — which makes it the cleanest
 * possible statement of the defect. It was written first for that reason and
 * **it passes on the unfixed code**, because `SizeTransform(clip = false)`
 * already fixed that half a round ago.
 *
 * The case that still failed is the one that was reported: an error clearing
 * with nothing beneath it, where the target becomes `""` and the width travels
 * to zero. Measured on the unfixed code, 213 of 274px of the message was on
 * screen for the whole of the toggle back.
 *
 * So the clean one is a regression guard for the *previous* fix and the messy
 * one is the test for this one. Both are kept: the invariant survives the fade
 * and the vertical shrink either way, because it compares a width against
 * itself and neither of those touches width.
 */
@OptIn(ExperimentalTestApi::class)
class FieldMessageClipTest {

    @Test
    fun anErrorReplacingAHintIsNeverPartlyOffScreen() = runComposeUiTest {
        var error by mutableStateOf<String?>(null)
        mainClock.autoAdvance = false

        setContent {
            KontourTheme {
                Box(Modifier.fillMaxSize()) {
                    TextField(
                        state = rememberTextFieldState("Perth"),
                        label = "Origin",
                        supporting = Hint,
                        errorMessage = error,
                        modifier = Modifier.width(600.dp),
                    )
                }
            }
        }
        mainClock.advanceTimeByFrame()

        // The two messages have to differ enough for a partial reveal to be
        // visible at all. Without this the test measures nothing and says so.
        val hintWidth = onNodeWithText(Hint).fetchSemanticsNode().size.width
        error = Error
        mainClock.advanceTimeByFrame()
        val errorWidth = onNodeWithText(Error).fetchSemanticsNode().size.width
        assertTrue(
            errorWidth > hintWidth * 2,
            "the error ($errorWidth px) is not much wider than the hint ($hintWidth px), " +
                "so a container animating between them would barely move and this " +
                "test would pass on broken code",
        )

        var framesShowingBoth = 0
        val clipped = mutableListOf<String>()
        repeat(20) { frame ->
            mainClock.advanceTimeByFrame()
            if (onAllNodesWithText(Hint).fetchSemanticsNodes().isNotEmpty() &&
                onAllNodesWithText(Error).fetchSemanticsNodes().isNotEmpty()
            ) {
                framesShowingBoth++
            }
            val node = onAllNodesWithText(Error).fetchSemanticsNodes().firstOrNull()
                ?: return@repeat
            val onScreen = node.boundsInRoot.width
            val measured = node.size.width.toFloat()
            if (measured > 0f && abs(onScreen - measured) > 1f) {
                clipped += "frame $frame: ${onScreen.toInt()} of ${measured.toInt()}px on screen"
            }
        }

        // Proof the samples landed *inside* the transition rather than after it.
        // Holds identically before and after the fix, so it is a guard rather
        // than a second assertion.
        assertTrue(
            framesShowingBoth >= 3,
            "only $framesShowingBoth frames had both messages in the tree, so the " +
                "samples missed the crossfade and the assertion below was measuring " +
                "a settled field",
        )

        assertTrue(
            clipped.isEmpty(),
            "the error message was clipped to a container still growing under it, " +
                "which is what draws as a reveal from the left:\n  " +
                clipped.take(6).joinToString("\n  "),
        )
    }

    /**
     * The reported case: an error clearing with nothing to replace it.
     *
     * Three animations at once — the slot fades, the slot shrinks vertically and
     * the content's width collapses to zero because the target becomes `""`. The
     * invariant survives all three, because it is a width compared against
     * itself and neither alpha nor a vertical clip touches it.
     */
    @Test
    fun anErrorClearingWithNoHintBeneathIsNeverPartlyOffScreen() = runComposeUiTest {
        var error by mutableStateOf<String?>(Error)
        mainClock.autoAdvance = false

        setContent {
            KontourTheme {
                Box(Modifier.fillMaxSize()) {
                    TextField(
                        state = rememberTextFieldState("Perth"),
                        label = "Origin",
                        errorMessage = error,
                        modifier = Modifier.width(600.dp),
                    )
                }
            }
        }
        mainClock.advanceTimeByFrame()

        val settled = onNodeWithText(Error).fetchSemanticsNode().size.width
        assertTrue(settled > 100, "the error never laid out: $settled px")

        // Off, then straight back on — the "toggling quickly" in the report.
        error = null
        repeat(4) { mainClock.advanceTimeByFrame() }
        error = Error
        val clipped = mutableListOf<String>()
        repeat(16) { frame ->
            mainClock.advanceTimeByFrame()
            val node = onAllNodesWithText(Error).fetchSemanticsNodes().firstOrNull()
                ?: return@repeat
            val measured = node.size.width.toFloat()
            val onScreen = node.boundsInRoot.width
            if (measured > 0f && abs(onScreen - measured) > 1f) {
                clipped += "frame $frame: ${onScreen.toInt()} of ${measured.toInt()}px on screen"
            }
        }

        assertTrue(
            clipped.isEmpty(),
            "toggling the error off and straight back on revealed it left to right:\n  " +
                clipped.take(6).joinToString("\n  "),
        )
    }
}
