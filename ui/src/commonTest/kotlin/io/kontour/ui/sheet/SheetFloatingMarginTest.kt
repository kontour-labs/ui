package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.HandleInsets
import io.kontour.ui.adaptive.LocalHandleInsets
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A floating sheet keeps the same margin at its foot as at its sides, from what
 * can be seen: from the window's edge under a home indicator or a gesture
 * handle, and beyond Android's three buttons or the keyboard.
 *
 * Asked for: *"can we have uniform padding around edges of the screen. i think
 * right now it's bumped up at the bottom by the screen's safe area. however we
 * have to make sure if there is like 3-button navigation on android, it should
 * be even padding under that and around the edges, because that is opaque"*. It
 * kept the larger of its margin and the inset, so it stood 34dp up on an iPhone
 * against 12 at the sides, and sat on three buttons with no margin under it.
 *
 * And the content, which now stands in part of the strip a handle reserves, is
 * handed that part to keep clear of — and none of it where the margin clears
 * the whole inset.
 *
 * The window is 360 by 800 at a density of 1, so every number is a pixel and a
 * dp at once.
 */
@OptIn(ExperimentalTestApi::class)
class SheetFloatingMarginTest {

    @Test
    fun overAHomeIndicatorTheFootKeepsTheSidesMargin() = runComposeUiTest {
        val (gaps, padding) = measure(WindowInsets(bottom = 34.dp), HomeIndicator)
        assertNear(Margin, gaps.bottom, "a floating sheet's foot over a home indicator")
        assertNear(Margin, gaps.left, "its start side")
        assertNear(34.dp - Margin, padding, "the content's share of the home indicator's strip")
    }

    @Test
    fun overAGestureHandleTheFootKeepsTheSidesMargin() = runComposeUiTest {
        val (gaps, padding) = measure(WindowInsets(bottom = 24.dp), GestureHandle)
        assertNear(Margin, gaps.bottom, "a floating sheet's foot over Android's gesture handle")
        assertNear(24.dp - Margin, padding, "the content's share of the gesture handle's strip")
    }

    @Test
    fun overThreeButtonsTheMarginIsAboveTheBar() = runComposeUiTest {
        val (gaps, padding) = measure(WindowInsets(bottom = 48.dp), ThreeButtons)
        assertNear(48.dp + Margin, gaps.bottom, "a floating sheet's foot over three buttons")
        assertNear(0.dp, padding, "the content's padding with the bar cleared whole")
    }

    @Test
    fun aboveTheKeyboardTheMarginIsKeptToo() = runComposeUiTest {
        val (gaps, padding) = measure(WindowInsets(bottom = 300.dp), HomeIndicator)
        assertNear(300.dp + Margin, gaps.bottom, "a floating sheet's foot over the keyboard")
        assertNear(0.dp, padding, "the content's padding above the keyboard")
    }

    @Test
    fun threeButtonsAtTheSideAreClearedWholeToo() = runComposeUiTest {
        val side = HandleInsets(WindowInsets(left = 48.dp), WindowInsets(left = 48.dp))
        val (gaps, _) = measure(WindowInsets(left = 48.dp), side)
        assertNear(48.dp + Margin, gaps.left, "a floating sheet beside three buttons in landscape")
        assertNear(Margin, gaps.right, "its other side")
    }

    @Test
    fun aSideInsetThatTakesNoTapsIsClearedAsBefore() = runComposeUiTest {
        // A landscape iPhone's notch: the larger of the margin and the inset.
        val (gaps, _) = measure(WindowInsets(left = 47.dp), HomeIndicator)
        assertNear(47.dp, gaps.left, "a floating sheet beside a notch")
    }

    private data class Gaps(val left: Dp, val right: Dp, val bottom: Dp)

    /**
     * Opens a floating sheet at half height with [insets] and [system] as the
     * platform's, and says how far its surface stands off the window's edges and
     * what bottom padding its content was handed.
     */
    private fun ComposeUiTest.measure(insets: WindowInsets, system: HandleInsets): Pair<Gaps, Dp> {
        var padding = Dp.Unspecified
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalHandleInsets provides system) {
                    Box(Modifier.requiredSize(WindowWidth.dp, WindowHeight.dp).testTag(WindowTag).background(Color.White)) {
                        val state = rememberSheetState(
                            detents = listOf(SheetDetent.Half),
                            initialDetent = SheetDetent.Half,
                        )
                        BottomSheet(
                            state = state,
                            presentation = SheetPresentation.Floating,
                            edgeMorph = null,
                            windowInsets = insets,
                            // Found by this: it is set on the node the surface
                            // is placed in, inside the margin, where a tag on
                            // the sheet's modifier would be outside it.
                            paneTitle = SheetTitle,
                        ) { contentPadding ->
                            Box(
                                Modifier
                                    .reportBottom(contentPadding) { padding = it }
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }
                }
            }
        }
        repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        val bounds = onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, SheetTitle), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        // From the window's own box, which the test's root may not be the size of.
        val window = onNodeWithTag(WindowTag).getUnclippedBoundsInRoot()
        return Gaps(
            left = bounds.left - window.left,
            right = window.right - bounds.right,
            bottom = window.bottom - bounds.bottom,
        ) to padding
    }

    /** The bottom padding as the content reads it: in its measure pass, as a list does. */
    private fun Modifier.reportBottom(padding: PaddingValues, into: (Dp) -> Unit) = layout { measurable, constraints ->
        into(padding.calculateBottomPadding())
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    private fun assertNear(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs(expected.value - actual.value) <= 1f, "$what: expected about $expected, was $actual")

    private companion object {
        const val WindowWidth = 360
        const val WindowHeight = 800
        const val SheetTitle = "Sheet"
        const val WindowTag = "window"
        const val SettleFrames = 60

        /** `componentDefaults.sheetFloatingInset` in the default theme. */
        val Margin = 12.dp

        /** iOS: the whole foot is the home indicator, and none of it takes taps. */
        val HomeIndicator = HandleInsets(WindowInsets(bottom = 34.dp), WindowInsets(0))

        /** Android under gestures: the handle's strip, which takes no taps. */
        val GestureHandle = HandleInsets(WindowInsets(bottom = 24.dp), WindowInsets(0))

        /** Android's three buttons: the whole foot takes taps. */
        val ThreeButtons = HandleInsets(WindowInsets(bottom = 48.dp), WindowInsets(bottom = 48.dp))
    }
}
