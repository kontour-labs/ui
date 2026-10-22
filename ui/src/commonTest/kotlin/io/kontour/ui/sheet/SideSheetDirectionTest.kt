package io.kontour.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A side sheet comes in from the side it names, in either direction: the end
 * is the right left to right and the left right to left.
 *
 * Right to left, an end sheet came in at the start. Its edge was worked out
 * once for the direction and then placed by `CenterEnd` and slid by `offset`,
 * which each work it out again.
 */
@OptIn(ExperimentalTestApi::class)
class SideSheetDirectionTest {

    /** Where the sheet's text starts, and how wide the window is, in pixels. */
    private fun ComposeUiTest.sheetLeft(direction: LayoutDirection, side: SheetSide): Pair<Float, Float> {
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box(Modifier.size(600.dp, 400.dp)) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            SideSheet(visible = true, side = side, width = 200.dp) { Text("Filters") }
                        }
                    }
                }
            }
        }
        waitForIdle()
        return onNodeWithText("Filters").fetchSemanticsNode().boundsInRoot.left to with(density) { 600.dp.toPx() }
    }

    @Test
    fun theEndIsTheRightLeftToRight() = runComposeUiTest {
        val (left, window) = sheetLeft(LayoutDirection.Ltr, SheetSide.End)
        assertTrue(left > window / 2, "an end sheet left to right should be on the right: its text starts at $left of $window")
    }

    @Test
    fun theEndIsTheLeftRightToLeft() = runComposeUiTest {
        val (left, window) = sheetLeft(LayoutDirection.Rtl, SheetSide.End)
        assertTrue(left < window / 2, "an end sheet right to left should be on the left: its text starts at $left of $window")
    }

    @Test
    fun theStartIsTheRightRightToLeft() = runComposeUiTest {
        val (left, window) = sheetLeft(LayoutDirection.Rtl, SheetSide.Start)
        assertTrue(left > window / 2, "a start sheet right to left should be on the right: its text starts at $left of $window")
    }
}
