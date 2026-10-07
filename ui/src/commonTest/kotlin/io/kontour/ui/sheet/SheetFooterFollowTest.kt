package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.kontour.ui.PhaseCounts
import io.kontour.ui.countPhases
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A part pinned to a sheet's bottom rests on the bottom of what the sheet
 * shows, in every presentation, above the inset — and stays there through a
 * drag, as a navigation bar stays at the foot of a screen.
 *
 * `SheetSlotsTest` asks the first of these of an edge sheet with no insets.
 * These are the cases it leaves out:
 *
 * - **Floating**, its surface is exactly as tall as it is shown, a margin off the
 *   window's bottom, and the footer belongs on the surface's own bottom edge.
 * - **Morphing** from floating to an edge sheet as it expands, it is the first
 *   at the bottom of its travel and the second at the top.
 * - **Above the inset** — the gesture bar, or the keyboard — and the content is
 *   told how much it now has to keep clear of.
 * - **A drag** leaves it where it is, at the foot, while the content appears
 *   above it.
 *
 * The window is 360 by 800 at a density of 1, so every number is a pixel and a
 * dp at once. The part is 56dp, with the sheet's inset, [Inset], round it.
 */
@OptIn(ExperimentalTestApi::class)
class SheetFooterFollowTest {

    @Test
    fun aFloatingSheetsPartSitsOnTheSheetsOwnBottomEdge() {
        var margin = Float.NaN
        val bottoms = partBottoms(SheetPresentation.Floating, edgeMorph = null) { margin = it.value }

        bottoms.forEach { (detent, bottom) ->
            assertEquals(
                WindowHeight - margin - Inset.value, bottom, Tolerance,
                "at $detent a floating sheet's footer ended at $bottom, where the sheet " +
                    "itself ends at ${WindowHeight - margin}, a margin off the window's bottom",
            )
        }
    }

    @Test
    fun anEdgeSheetsPartSitsOnTheWindowsBottomAtEveryDetent() {
        val bottoms = partBottoms(SheetPresentation.Edge)

        bottoms.forEach { (detent, bottom) ->
            assertEquals(
                WindowHeight - Inset.value, bottom, Tolerance,
                "at $detent an edge sheet's footer ended at $bottom in an " +
                    "${WindowHeight}px window: an edge sheet's surface runs off the window",
            )
        }
    }

    /** Floating at the bottom of its travel, an edge sheet at the top. */
    @Test
    fun aMorphingSheetsPartFollowsTheSheetFromFloatingToEdge() {
        var margin = Float.NaN
        val bottoms = partBottoms(SheetPresentation.Floating, edgeMorph = SheetEdgeMorph()) { margin = it.value }

        assertEquals(
            WindowHeight - margin - Inset.value, bottoms.getValue(Collapsed.id), Tolerance,
            "collapsed, a morphing sheet is still floating and its part belongs a margin " +
                "off the window's bottom; it was at ${bottoms.getValue(Collapsed.id)}",
        )
        assertEquals(
            WindowHeight - Inset.value, bottoms.getValue(SheetDetent.Expanded.id), Tolerance,
            "expanded, a morphing sheet has become an edge sheet and its part belongs at " +
                "the window's bottom; it was at ${bottoms.getValue(SheetDetent.Expanded.id)}",
        )
    }

    /**
     * Above the bottom inset, and the content is told to keep clear of the part
     * and the band of the sheet's colour above it.
     *
     * A search field on the window's bottom would be typed at from behind the
     * keyboard. Without a footer the content is owed the inset alone,
     * exactly as before pinning existed.
     */
    @Test
    fun thePartSitsAboveTheInsetAndTheContentIsToldToClearIt() {
        var partTop = Float.NaN
        var partBottom = Float.NaN
        var withPart: Dp = Dp.Unspecified
        var withoutPart: Dp = Dp.Unspecified

        runComposeUiTest {
            var showPart by mutableStateOf(true)
            mainClock.autoAdvance = false
            setContent {
                Harness(
                    target = SheetDetent.Half,
                    presentation = SheetPresentation.Edge,
                    windowInsets = WindowInsets(bottom = SafeArea),
                    showPart = showPart,
                    onPadding = { padding -> if (showPart) withPart = padding else withoutPart = padding },
                )
            }
            settle()
            partTop = partBounds().top.value
            partBottom = partBounds().bottom.value

            showPart = false
            settle()
        }

        assertEquals(
            WindowHeight - SafeArea.value - Inset.value, partBottom, Tolerance,
            "the part ended at $partBottom, which is not the inset and the safe area clear " +
                "of the window's bottom: it is sitting behind the gesture bar or the keyboard",
        )
        assertEquals(
            WindowHeight - (partTop - Inset.value), withPart.value, Tolerance,
            "with a footer, the content was handed $withPart of bottom padding; it has " +
                "to reach from the window's bottom to the top of the band above the part",
        )
        assertEquals(
            SafeArea, withoutPart,
            "without a part, the content was handed $withoutPart; it is still owed the " +
                "safe area, $SafeArea, exactly as it was before footers existed",
        )
    }

    /**
     * A drag leaves the bottom part at the foot of the window, and does nothing
     * else to it: no composition, and measured and drawn at most now and then
     * over a drag of eighty frames — the budget `SheetFramePressureTest` holds
     * the sheet's content to. Asked for as *"a search bar that's basically locked
     * at the bottom of a sheet, basically acting like a nav bar. content should
     * show up as the sheet expands"*: it used to be carried up with the sheet's
     * top edge, with the content showing below it.
     */
    @Test
    fun aDragLeavesTheBottomPartAtTheFootWithoutComposingOrMeasuringIt() {
        var frames = 0
        var compositions = 0
        val counts = PhaseCounts()

        runComposeUiTest {
            lateinit var sheet: SheetState
            mainClock.autoAdvance = false
            setContent {
                Harness(
                    target = SheetDetent.Half,
                    presentation = SheetPresentation.Edge,
                    counts = counts,
                    onPartComposed = { compositions++ },
                    onState = { sheet = it },
                )
            }
            settle()
            counts.reset()
            val composedBefore = compositions
            val sheetBefore = sheet.offset
            val foot = WindowHeight - Inset.value

            // By hand, from the handle's band, upwards, from Half towards
            // Expanded; held at the end.
            val x = WindowWidth / 2f
            val y = sheet.offset + 8f
            onNodeWithTag(WindowTag).performTouchInput { down(Offset(x, y)) }
            var at = y
            val bottoms = mutableListOf<Float>()
            repeat(DraggedFrames) {
                at -= 5f
                onNodeWithTag(WindowTag).performTouchInput { moveTo(Offset(x, at)) }
                mainClock.advanceTimeByFrame()
                bottoms += partBounds().bottom.value
                frames++
            }
            val moved = sheetBefore - sheet.offset
            // And back down past where it started, towards collapsed.
            repeat(DraggedFrames) {
                at += 8f
                onNodeWithTag(WindowTag).performTouchInput { moveTo(Offset(x, at)) }
                mainClock.advanceTimeByFrame()
                bottoms += partBounds().bottom.value
                frames++
            }
            compositions -= composedBefore

            assertTrue(moved > 40f, "the sheet moved ${moved}px, so this measured a sheet standing still")
            val wandered = bottoms.maxOf { abs(it - foot) }
            assertTrue(
                wandered <= 1f,
                "the bottom part left the foot of the window by ${wandered}px during the drag: it is being " +
                    "carried with the sheet rather than staying where a navigation bar would",
            )
            assertEquals(0, compositions, "the bottom part was recomposed $compositions times by a drag")
            assertTrue(
                counts.measures <= frames / 8 && counts.draws <= frames / 8,
                "the bottom part was measured ${counts.measures} and drawn ${counts.draws} times " +
                    "over $frames frames of dragging; a part that only stays put should cost neither",
            )

            // Let go, and settled: still at the foot.
            onNodeWithTag(WindowTag).performTouchInput { up() }
            settle()
            assertEquals(
                foot, partBounds().bottom.value, Tolerance,
                "landed, the bottom part ended at ${partBounds().bottom.value}, not at the foot, $foot",
            )
        }
    }

    /** Where the part's bottom edge settles at each detent, by detent id. */
    private fun partBottoms(
        presentation: SheetPresentation,
        edgeMorph: SheetEdgeMorph? = SheetEdgeMorph(),
        onMargin: (Dp) -> Unit = {},
    ): Map<String, Float> {
        val bottoms = mutableMapOf<String, Float>()
        runComposeUiTest {
            var target by mutableStateOf(Collapsed)
            mainClock.autoAdvance = false
            setContent {
                Harness(target = target, presentation = presentation, edgeMorph = edgeMorph, onMargin = onMargin)
            }
            for (detent in listOf(Collapsed, SheetDetent.Half, SheetDetent.Expanded)) {
                target = detent
                settle()
                bottoms[detent.id] = partBounds().bottom.value
            }
        }
        return bottoms
    }

    /** The part's bounds within the harness's window rather than the test's root. */
    private fun ComposeUiTest.partBounds(): DpRect {
        val part = onNodeWithTag(PartTag).getUnclippedBoundsInRoot()
        val window = onNodeWithTag(WindowTag).getUnclippedBoundsInRoot()
        return DpRect(part.left - window.left, part.top - window.top, part.right - window.left, part.bottom - window.top)
    }

    private fun ComposeUiTest.settle() {
        repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
    }

    @Composable
    private fun Harness(
        target: SheetDetent,
        presentation: SheetPresentation,
        edgeMorph: SheetEdgeMorph? = SheetEdgeMorph(),
        windowInsets: WindowInsets = WindowInsets(0),
        showPart: Boolean = true,
        counts: PhaseCounts = PhaseCounts(),
        onPartComposed: () -> Unit = {},
        onPadding: (Dp) -> Unit = {},
        onState: (SheetState) -> Unit = {},
        onMargin: (Dp) -> Unit = {},
    ) {
        KontourTheme(reduceMotion = true) {
            onMargin(Theme.componentDefaults.sheetFloatingInset)
            Box(Modifier.requiredSize(WindowWidth.dp, WindowHeight.dp).testTag(WindowTag).background(Color.White)) {
                val state = rememberSheetState(
                    detents = listOf(Collapsed, SheetDetent.Half, SheetDetent.Expanded),
                    initialDetent = Collapsed,
                )
                onState(state)
                LaunchedEffect(target) { state.animateTo(target) }
                BottomSheet(
                    state = state,
                    presentation = presentation,
                    edgeMorph = edgeMorph,
                    windowInsets = windowInsets,
                ) { padding ->
                    if (showPart) {
                        part(pin = SheetPartPin.Bottom) {
                            SideEffect(onPartComposed)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(PartHeight)
                                    .countPhases(counts)
                                    .testTag(PartTag)
                                    .background(Color(0xFF3355AA))
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.reportBottom(padding, onPadding),
                        contentPadding = padding,
                    ) {
                        items(40) {
                            Box(Modifier.fillMaxWidth().height(48.dp).background(Color(0xFFEEEEEE)))
                        }
                    }
                }
            }
        }
    }

    /** The bottom padding as the content reads it: in its measure pass, as a list does. */
    private fun Modifier.reportBottom(padding: PaddingValues, into: (Dp) -> Unit) = layout { measurable, constraints ->
        into(padding.calculateBottomPadding())
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    private companion object {
        const val WindowWidth = 360
        const val WindowHeight = 800f
        const val PartTag = "part"
        const val WindowTag = "window"
        const val Tolerance = 0.5f
        const val SettleFrames = 90
        const val DraggedFrames = 40

        val PartHeight = 56.dp
        val SafeArea = 24.dp

        /** `BottomSheetDefaults.ContentPadding` in the default theme. */
        val Inset = 16.dp

        /** The handle's band and the bottom part, which is what collapsed to it is. */
        val Collapsed = SheetDetent.parts()
    }
}
