package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
 * A part pinned to an edge of the sheet stays there, and the content passes behind.
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
class SheetPinnedTest {

    /**
     * And with the same space all round it: *"when the sheet is collapsed and only
     * the header (or the part that is pinned) is shown, padding should be uniform
     * around that part"*. The handle's band above, the sheet's inset beside and
     * below, all [Inset].
     */
    @Test
    fun collapsedTheSheetIsTheHandleAndThePinnedPart() {
        runComposeUiTest {
            lateinit var sheet: SheetState
            mainClock.autoAdvance = false
            setContent { Harness(SheetDetent.pinned()) { sheet = it } }
            settle()
            val window = onRoot().getBoundsInRoot()
            val bar = onNodeWithTag(Bar).getBoundsInRoot()
            val first = onNodeWithTag(row(0)).getBoundsInRoot()
            val sheetTop = window.bottom - with(density) { sheet.visibleHeight.toDp() }
            near(Inset, bar.top - sheetTop, "the space above the bar, from the sheet's top edge")
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
    fun atEveryDetentThePinnedPartIsOnTheBottomOfWhatTheSheetShows() {
        runComposeUiTest {
            var target by mutableStateOf(SheetDetent.pinned())
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
    fun aListGivenTheSheetsPaddingScrollsItsLastRowOutFromBehindThePinnedPart() {
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
    fun aPartPinnedToTheTopSitsUnderTheHandleAndTheListStartsBelowIt() {
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { Harness(SheetDetent.Half, pin = SheetPin.Top) {} }
            settle()
            val bar = onNodeWithTag(Bar).getBoundsInRoot()
            val first = onNodeWithTag(row(0)).getBoundsInRoot()
            near(bar.bottom + Inset, first.top, "the first row's top against the bar's bottom")
        }
    }

    /**
     * The control: with nothing pinned, `SheetDetent.pinned` keeps its fallback.
     * Without it the first arm could pass on a detent that happened to resolve to
     * the handle and the bar for some other reason.
     */
    @Test
    fun withNothingPinnedTheDetentIsItsFallback() {
        runComposeUiTest {
            lateinit var sheet: SheetState
            mainClock.autoAdvance = false
            setContent { Harness(SheetDetent.pinned(Fallback), pin = null) { sheet = it } }
            settle()
            val shown = with(density) { sheet.visibleHeight.toDp() }
            assertTrue(
                abs((shown - Fallback).value) < 1f,
                "with nothing pinned the sheet shows $shown, where the detent's fallback is $Fallback",
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
        pin: SheetPin? = SheetPin.Bottom,
        onState: (SheetState) -> Unit,
    ) {
        KontourTheme(reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    val state = rememberSheetState(
                        detents = listOf(SheetDetent.pinned(Fallback), SheetDetent.Half, SheetDetent.Expanded),
                        initialDetent = SheetDetent.pinned(Fallback),
                    )
                    onState(state)
                    LaunchedEffect(target) { state.animateTo(target) }
                    BottomSheet(state = state, presentation = SheetPresentation.Edge) { padding: PaddingValues ->
                        if (pin != null) {
                            pinned(to = pin) {
                                Box(Modifier.fillMaxWidth().height(BarHeight).testTag(Bar).background(Color.Blue))
                            }
                        }
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

    private companion object {
        const val Bar = "bar"
        const val List = "list"
        const val Rows = 30
        const val SettleFrames = 90
        val BarHeight = 56.dp

        /** `BottomSheetDefaults.ContentPadding` in the default theme. */
        val Inset = 16.dp
        val RowHeight = 48.dp

        /** Far from the handle and the bar together, so the control can tell them apart. */
        val Fallback = 200.dp

        fun row(index: Int) = "row$index"
    }
}
