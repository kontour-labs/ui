package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A sheet collapsed to its header shows the header with the same space round it,
 * and the handle's band above it.
 *
 * Asked for as *"less of a forehead … there's a fair bit of extra space between the
 * top of the sheet header (or whatever content is peeking), and the sheet handle"*,
 * and *"padding should be uniform around that part, and whatever that padding value
 * is should stay throughout all sheets"*. The handle was a 24dp row, the header
 * added 4dp of its own, and its 48dp close button centred the title 12dp further
 * down: 40dp over the title against 16 beside it. Now the band is all there is
 * above: 24dp, the handle's room, since 16 left the handle sitting on the title —
 * *"the top of it looks a bit cramped now"*.
 *
 * Measured from the header's drawn row — the close button's 24dp icon, the
 * tallest thing in it, with the title's line centred on it: from the sheet's top
 * edge, and to the bottom of what the collapsed sheet shows. The title's own start
 * is the inset from the sheet's side. An edge sheet, so the window's bottom is the
 * sheet's.
 */
@OptIn(ExperimentalTestApi::class)
class SheetHeaderInsetTest {

    @Test
    fun aSheetCollapsedToItsHeaderHasTheSameSpaceAllRoundTheTitle() {
        runComposeUiTest {
            lateinit var sheet: SheetState
            mainClock.autoAdvance = false
            setContent { Harness(SheetHeaderStyle.Small) { sheet = it } }
            settle()
            val window = onRoot().getBoundsInRoot()
            val title = onNodeWithText(Title, useUnmergedTree = true).getBoundsInRoot()
            val body = onNodeWithTag(Body).getBoundsInRoot()
            val sheetTop = window.bottom - with(density) { sheet.visibleHeight.toDp() }
            // The row is the icon's height; the title's line sits centred in it.
            val centring = (Icon - (title.bottom - title.top)) / 2
            near(Band + centring, title.top - sheetTop, "above the title, from the sheet's top edge")
            near(Inset, title.left - body.left, "beside the title, from the sheet's side")
            near(Inset + centring, window.bottom - title.bottom, "below the title, to the bottom of the collapsed sheet")
        }
    }

    /** A centred title is the same, but for where it sits along the line. */
    @Test
    fun aCentredHeaderToo() {
        runComposeUiTest {
            lateinit var sheet: SheetState
            mainClock.autoAdvance = false
            setContent { Harness(SheetHeaderStyle.Centred) { sheet = it } }
            settle()
            val window = onRoot().getBoundsInRoot()
            val title = onNodeWithText(Title, useUnmergedTree = true).getBoundsInRoot()
            val sheetTop = window.bottom - with(density) { sheet.visibleHeight.toDp() }
            val centring = (Icon - (title.bottom - title.top)) / 2
            near(Band + centring, title.top - sheetTop, "above a centred title")
            near(Inset + centring, window.bottom - title.bottom, "below a centred title")
        }
    }

    /**
     * A back button leads the header as a top bar's does: its icon as far in from
     * its side as the close button's from the other, the title after it, and a
     * large title still in line with what it heads.
     */
    @Test
    fun aBackButtonLeadsTheHeader() {
        for (style in listOf(SheetHeaderStyle.Small, SheetHeaderStyle.Large)) runComposeUiTest {
            var backs = 0
            mainClock.autoAdvance = false
            setContent { Harness(style, onBack = { backs++ }) {} }
            settle()
            val body = onNodeWithTag(Body).getBoundsInRoot()
            val back = onNodeWithContentDescription("Back").getBoundsInRoot()
            val title = onNodeWithText(Title, useUnmergedTree = true).getBoundsInRoot()
            // As far in from its side as the close button's icon is from the other.
            val close = onNodeWithContentDescription("Close").getBoundsInRoot()
            near(
                body.right - (close.right - (close.right - close.left - Icon) / 2),
                back.left + (back.right - back.left - Icon) / 2 - body.left,
                "$style: the back icon from the sheet's side, against the close icon from the other",
            )
            near(close.top, back.top, "$style: the back button against the close button, vertically")
            if (style == SheetHeaderStyle.Large) {
                near(Inset, title.left - body.left, "$style: the large title from the sheet's side")
            } else {
                assertTrue(title.left >= back.right, "$style: the title at ${title.left} is not after the back button, which ends at ${back.right}")
            }
            onNodeWithContentDescription("Back").performClick()
            mainClock.advanceTimeByFrame()
            assertEquals(1, backs, "$style: the back button did not call onBack")
        }
    }

    private fun ComposeUiTest.settle() {
        repeat(90) { mainClock.advanceTimeByFrame() }
    }

    private fun near(expected: Dp, actual: Dp, what: String) {
        assertTrue(abs((expected - actual).value) < 1f, "$what: expected $expected, was $actual")
    }

    @Composable
    private fun Harness(style: SheetHeaderStyle, onBack: (() -> Unit)? = null, onState: (SheetState) -> Unit) {
        KontourTheme(reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    val peek = SheetDetent.peek(120.dp)
                    // Hidden in the list, so the header draws its close button:
                    // the 48dp that pushed the title down.
                    val state = rememberSheetState(
                        detents = listOf(SheetDetent.Hidden, peek, SheetDetent.Expanded),
                        initialDetent = peek,
                    )
                    onState(state)
                    BottomSheet(state = state, presentation = SheetPresentation.Edge) {
                        SheetHeader(modifier = Modifier.sheetPeekAnchor(), style = style, onBack = onBack) { +Title }
                        Box(Modifier.fillMaxWidth().height(400.dp).testTag(Body))
                    }
                }
            }
        }
    }

    private companion object {
        const val Title = "Perth Underground"
        const val Body = "body"
        val Inset = 16.dp

        /** `DragHandleDefaults.Target`: the handle's band, the space above. */
        val Band = 24.dp

        /** The close button's icon, the tallest thing the header draws. */
        val Icon = 24.dp
    }
}
