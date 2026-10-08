package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sheet's header and footer stay at its top and its foot, and the content
 * passes behind.
 *
 * Asked for as *"a way to pin a component to certain parts of the sheet… a
 * [search] bar to the bottom of the sheet. Content should be scrollable behind it,
 * and when the sheet is collapsed, only the search bar should show."* Each of those
 * three clauses is an arm here, read from where things are laid out: collapsed,
 * the sheet is the handle and the bar; at every other detent the bar is on the
 * bottom of what the sheet shows; and a list given the sheet's padding scrolls its
 * last row out from behind the bar.
 *
 * An edge sheet, so "the bottom of what the sheet shows" is the window's own and
 * the positions compare exactly; the harness has no insets.
 */
@OptIn(ExperimentalTestApi::class)
class SheetSlotsTest {

    /**
     * And with the same space all round it: *"when the sheet is collapsed and only
     * the header (or the part that is pinned) is shown, padding should be uniform
     * around that part"*. The sheet's inset beside and below, [Inset], and the
     * handle's band above, [Band].
     */
    @Test
    fun collapsedTheSheetIsTheHandleAndTheFooter() {
        runComposeUiTest {
            lateinit var sheet: SheetState
            mainClock.autoAdvance = false
            setContent { Harness(SheetDetent.parts()) { sheet = it } }
            settle()
            val window = onRoot().getBoundsInRoot()
            val bar = onNodeWithTag(Bar).getBoundsInRoot()
            val first = onNodeWithTag(row(0)).getBoundsInRoot()
            val sheetTop = window.bottom - with(density) { sheet.visibleHeight.toDp() }
            near(Band, bar.top - sheetTop, "the space above the bar, from the sheet's top edge")
            // From the sheet's side, which the rows fill: on this wide window the
            // sheet is capped and centred.
            near(Inset, bar.left - first.left, "the space beside the bar")
            near(Inset, window.bottom - bar.bottom, "the space below the bar")
            // The content starts under the handle, and so does the bar: nothing of
            // the content shows above the bar or below it.
            near(first.top, bar.top, "the bar's top against where the content starts")
            assertTrue(
                bar.bottom - bar.top in BarHeight - 1.dp..BarHeight + 1.dp,
                "the bar is ${bar.bottom - bar.top} tall, where it was given $BarHeight",
            )
        }
    }

    @Test
    fun atEveryDetentTheFooterIsOnTheBottomOfWhatTheSheetShows() {
        runComposeUiTest {
            var target by mutableStateOf(SheetDetent.parts())
            lateinit var sheet: SheetState
            mainClock.autoAdvance = false
            setContent { Harness(target) { sheet = it } }
            settle()
            val collapsed = sheet.offset
            for (detent in listOf(SheetDetent.Half, SheetDetent.Expanded)) {
                target = detent
                settle()
                assertTrue(
                    collapsed - sheet.offset > 100f,
                    "the sheet did not open to $detent: from $collapsed to ${sheet.offset}",
                )
                val window = onRoot().getBoundsInRoot()
                val bar = onNodeWithTag(Bar).getBoundsInRoot()
                near(window.bottom - Inset, bar.bottom, "at $detent, the bar's bottom against the window's")
            }
        }
    }

    @Test
    fun aListGivenTheSheetsPaddingScrollsItsLastRowOutFromBehindTheFooter() {
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { Harness(SheetDetent.Expanded) {} }
            settle()
            onNodeWithTag(List).performScrollToIndex(Rows - 1)
            settle()
            val bar = onNodeWithTag(Bar).getBoundsInRoot()
            val last = onNodeWithTag(row(Rows - 1)).getBoundsInRoot()
            // Clear of the bar, and of the inset of the sheet's colour above it.
            near(bar.top - Inset, last.bottom, "the last row's bottom against the bar's top, scrolled to the end")
        }
    }

    @Test
    fun aHeaderSitsUnderTheHandleAndTheListStartsBelowIt() {
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { Harness(SheetDetent.Half, slot = Slot.Header) {} }
            settle()
            val bar = onNodeWithTag(Bar).getBoundsInRoot()
            val first = onNodeWithTag(row(0)).getBoundsInRoot()
            // A header is laid out as the content is, with no inset of its own:
            // a `SheetHeader` in it brings its own.
            near(bar.bottom, first.top, "the first row's top against the bar's bottom")
        }
    }

    /**
     * Collapsed to a **header**, an edge sheet still clears the
     * safe area under it: the part sits above the home indicator, with the
     * sheet's inset between them as there is round a footer.
     *
     * Reported: *"the "edge" bottom sheet style now doesn't clear the safe area
     * when it's collapsed with a "pinned" item"*. The pinned detent was the
     * handle and the parts, and only a part pinned to the bottom carried the
     * safe area in its own height; with the part at the top, the sheet came to
     * rest a home indicator short, the part's foot behind it.
     */
    @Test
    fun collapsedToAHeaderTheSheetClearsTheSafeArea() {
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { Harness(SheetDetent.parts(), slot = Slot.Header, insets = WindowInsets(bottom = SafeArea)) {} }
            settle()
            val window = onRoot().getBoundsInRoot()
            val bar = onNodeWithTag(Bar).getBoundsInRoot()
            assertTrue(
                window.bottom - bar.bottom >= SafeArea,
                "the header ends ${window.bottom - bar.bottom} above the window's foot, " +
                    "inside the $SafeArea of safe area under it",
            )
        }
    }

    /** And a footer, as it always did: the control for the arm above. */
    @Test
    fun collapsedToAFooterTheSheetClearsTheSafeArea() {
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { Harness(SheetDetent.parts(), slot = Slot.Footer, insets = WindowInsets(bottom = SafeArea)) {} }
            settle()
            val window = onRoot().getBoundsInRoot()
            val bar = onNodeWithTag(Bar).getBoundsInRoot()
            assertTrue(
                window.bottom - bar.bottom >= SafeArea,
                "the footer ends ${window.bottom - bar.bottom} above the window's foot",
            )
        }
    }

    /**
     * The control: with neither, `SheetDetent.parts` keeps its fallback.
     * Without it the first arm could pass on a detent that happened to resolve to
     * the handle and the bar for some other reason.
     */
    @Test
    fun withNeitherTheDetentIsItsFallback() {
        runComposeUiTest {
            lateinit var sheet: SheetState
            mainClock.autoAdvance = false
            setContent { Harness(SheetDetent.parts(Fallback), slot = null) { sheet = it } }
            settle()
            val shown = with(density) { sheet.visibleHeight.toDp() }
            assertTrue(
                abs((shown - Fallback).value) < 1f,
                "with neither the sheet shows $shown, where the detent's fallback is $Fallback",
            )
        }
    }

    private fun ComposeUiTest.settle() {
        repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
    }

    private fun near(expected: Dp, actual: Dp, what: String) {
        assertTrue(abs((expected - actual).value) < 1f, "$what: expected $expected, was $actual")
    }

    @Composable
    private fun Harness(
        target: SheetDetent,
        slot: Slot? = Slot.Footer,
        insets: WindowInsets = WindowInsets(0, 0, 0, 0),
        onState: (SheetState) -> Unit,
    ) {
        KontourTheme(reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    val state = rememberSheetState(
                        detents = listOf(SheetDetent.parts(Fallback), SheetDetent.Half, SheetDetent.Expanded),
                        initialDetent = SheetDetent.parts(Fallback),
                    )
                    onState(state)
                    LaunchedEffect(target) { state.animateTo(target) }
                    val bar: @Composable ColumnScope.() -> Unit = {
                        Box(Modifier.fillMaxWidth().height(BarHeight).testTag(Bar).background(Color.Blue))
                    }
                    BottomSheet(
                        state = state,
                        presentation = SheetPresentation.Edge,
                        windowInsets = insets,
                    ) { padding: PaddingValues ->
                        if (slot == Slot.Header) part(pin = SheetPartPin.Top, content = bar)
                        if (slot == Slot.Footer) part(pin = SheetPartPin.Bottom, content = bar)
                        LazyColumn(Modifier.testTag(List), contentPadding = padding) {
                            items(Rows) { index ->
                                Box(Modifier.fillMaxWidth().height(RowHeight).testTag(row(index)))
                            }
                        }
                    }
                }
            }
        }
    }

    private enum class Slot { Header, Footer }

    private companion object {
        const val Bar = "bar"
        const val List = "list"
        const val Rows = 30
        const val SettleFrames = 90
        val BarHeight = 56.dp

        /** An iPhone's home indicator strip. */
        val SafeArea = 34.dp

        /** `BottomSheetDefaults.ContentPadding` in the default theme. */
        val Inset = 16.dp

        /** `DragHandleDefaults.Target`: the handle's band, the space above. */
        val Band = 24.dp
        val RowHeight = 48.dp

        /** Far from the handle and the bar together, so the control can tell them apart. */
        val Fallback = 200.dp

        fun row(index: Int) = "row$index"
    }
}
