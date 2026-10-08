package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Parts pinned to the top of a sheet and to its collapsed size — the two uses of
 * the rethink `SheetSlotsTest` and `SheetFooterFollowTest` do not cover: *"an
 * apple maps-style search bar that's glued to the top of the header, that other
 * content scrolls under"*, and *"some content that's normally scrollable (e.g.
 * the search bar might be at the top of that scrollable list), but then when the
 * sheet collapses, it smoothly animates into being the only visible thing"*.
 */
@OptIn(ExperimentalTestApi::class)
class SheetPartPinTest {

    @Test
    fun aTopPartStaysPutWhileTheListScrollsUnderIt() {
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { Harness(SheetDetent.Half, SheetPartPin.Top) {} }
            settle()
            val before = onNodeWithTag(Part).getBoundsInRoot()
            val firstBefore = onNodeWithTag(row(0)).getBoundsInRoot()
            // The fixture: the list starts under the part, not under it already.
            near(before.bottom, firstBefore.top, "the first row's top against the part's foot")
            onNodeWithTag(List).performScrollToIndex(10)
            settle()
            val after = onNodeWithTag(Part).getBoundsInRoot()
            near(before.top, after.top, "the top part's top after the list scrolled")
        }
    }

    @Test
    fun aCollapsedPartIsUnderTheHandleOnceTheSheetCollapses() {
        runComposeUiTest {
            lateinit var sheet: SheetState
            var target by mutableStateOf(SheetDetent.Half)
            mainClock.autoAdvance = false
            setContent { Harness(target, SheetPartPin.Collapsed) { sheet = it } }
            settle()
            val open = onNodeWithTag(Part).getBoundsInRoot()
            val window = onRoot().getBoundsInRoot()
            val openTop = window.bottom - with(density) { sheet.visibleHeight.toDp() }
            // Open, it is the list's first row: under the handle, in the list.
            near(openTop + Band, open.top, "open, the part's top against the sheet's top and the handle")
            target = SheetDetent.parts()
            settle()
            val collapsed = onNodeWithTag(Part).getBoundsInRoot()
            val sheetTop = window.bottom - with(density) { sheet.visibleHeight.toDp() }
            near(sheetTop + Band, collapsed.top, "collapsed, the part's top against the sheet's top and the handle")
            // And the collapsed sheet is the handle and the part: nothing else of it shows.
            near(Band + PartHeight, window.bottom - sheetTop, "collapsed, the sheet's height")
        }
    }

    @Test
    fun aCollapsedPartScrolledAwayComesBackFromAboveAndKeepsItsState() {
        runComposeUiTest {
            lateinit var sheet: SheetState
            var target by mutableStateOf(SheetDetent.Expanded)
            mainClock.autoAdvance = false
            setContent { Harness(target, SheetPartPin.Collapsed) { sheet = it } }
            settle()
            // Something done to it while it is in the list: its own state.
            onNodeWithTag(Part).performClick()
            settle()
            onNodeWithTag(Count, useUnmergedTree = true).assertTextEquals("1")
            // Scrolled well away: a lazy list no longer has its place composed.
            onNodeWithTag(List).performScrollToIndex(Rows - 1)
            settle()
            target = SheetDetent.parts()
            settle()
            val window = onRoot().getBoundsInRoot()
            val sheetTop = window.bottom - with(density) { sheet.visibleHeight.toDp() }
            val collapsed = onNodeWithTag(Part).getBoundsInRoot()
            near(sheetTop + Band, collapsed.top, "collapsed from scrolled away, the part's top")
            onNodeWithTag(Count, useUnmergedTree = true).assertTextEquals("1")
        }
    }

    /**
     * A collapsed part taken out is gone: written under an `if`, it stayed
     * drawn after the condition went false, and kept its room in the sheet.
     */
    @Test
    fun aCollapsedPartTakenOutIsGone() {
        runComposeUiTest {
            var shown by mutableStateOf(true)
            mainClock.autoAdvance = false
            setContent { Harness(SheetDetent.Half, SheetPartPin.Collapsed, shown = shown) {} }
            settle()
            onNodeWithTag(Part).assertExists()
            shown = false
            settle()
            onNodeWithTag(Part).assertDoesNotExist()
        }
    }

    @Test
    fun collapsedToItsPartsNothingOfTheContentShowsRoundABottomPart() = nothingShowsRound(SheetPartPin.Bottom)

    @Test
    fun collapsedToItsPartsNothingOfTheContentShowsRoundATopPart() = nothingShowsRound(SheetPartPin.Top)

    /**
     * *"Make the content that goes behind the search bar … not show when it's
     * collapsed"*: the rows, red, are in the sheet at half, and nowhere in it
     * once it is only its handle and its pinned part.
     */
    private fun nothingShowsRound(pin: SheetPartPin) = runComposeUiTest {
        lateinit var sheet: SheetState
        var target by mutableStateOf(SheetDetent.Half)
        mainClock.autoAdvance = false
        setContent { Harness(target, pin, rows = Color.Red) { sheet = it } }
        settle()
        assertTrue(redInSheet(sheet) > 0, "the fixture: at half, the rows show")
        target = SheetDetent.parts()
        settle()
        assertEquals(0, redInSheet(sheet), "collapsed to its ${pin.name.lowercase()} part, the content still showed")
    }

    /** How many pixels of the sheet, from its top to the window's foot, are the rows' red. */
    private fun ComposeUiTest.redInSheet(sheet: SheetState): Int {
        val image = onRoot().captureToImage().toPixelMap()
        val top = (image.height - sheet.visibleHeight).toInt().coerceIn(0, image.height)
        var red = 0
        for (y in top until image.height) {
            for (x in 0 until image.width) {
                val c = image[x, y]
                if (c.red > 0.8f && c.green < 0.3f && c.blue < 0.3f) red++
            }
        }
        return red
    }

    private fun ComposeUiTest.settle() {
        repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
    }

    private fun near(expected: Dp, actual: Dp, what: String) {
        assertTrue(abs((expected - actual).value) < 1f, "$what: expected $expected, was $actual")
    }

    @Composable
    private fun Harness(target: SheetDetent, pin: SheetPartPin, rows: Color = Color.Unspecified, shown: Boolean = true, onState: (SheetState) -> Unit) {
        KontourTheme(reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    val state = rememberSheetState(
                        detents = listOf(SheetDetent.parts(), SheetDetent.Half, SheetDetent.Expanded),
                        initialDetent = SheetDetent.Half,
                    )
                    onState(state)
                    LaunchedEffect(target) { state.animateTo(target) }
                    BottomSheet(state = state, presentation = SheetPresentation.Edge) { padding ->
                        val sheetParts = this
                        val part: @Composable () -> Unit = {
                            var count by remember { mutableIntStateOf(0) }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(PartHeight)
                                    .testTag(Part)
                                    .clickable { count++ }
                                    .background(Color.Blue),
                            ) { Text("$count", modifier = Modifier.testTag(Count)) }
                        }
                        if (pin == SheetPartPin.Top) part(pin = SheetPartPin.Top) { part() }
                        if (pin == SheetPartPin.Bottom) part(pin = SheetPartPin.Bottom) { part() }
                        LazyColumn(Modifier.testTag(List), contentPadding = padding) {
                            if (pin == SheetPartPin.Collapsed && shown) {
                                item(key = "part") { sheetParts.part(pin = SheetPartPin.Collapsed) { part() } }
                            }
                            items(Rows) { index ->
                                Box(
                                    Modifier.fillMaxWidth().height(RowHeight).testTag(row(index))
                                        .then(if (rows.isSpecified) Modifier.background(rows) else Modifier),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val Part = "part"
        const val Count = "count"
        const val List = "list"
        const val Rows = 40
        const val SettleFrames = 90
        val PartHeight = 56.dp
        val RowHeight = 48.dp

        /** `DragHandleDefaults.Target`: the handle's band, the space above. */
        val Band = 24.dp

        fun row(index: Int) = "row$index"
    }
}
