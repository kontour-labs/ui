package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.sheet.sheetPeekAnchor
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A sheet's measured geometry, taken from a real composition.
 *
 * `SheetAnchorTest` covers the arithmetic; this covers the wiring, which is
 * where sheets actually go wrong. Both bugs found while building this phase
 * were invisible to the pure tests and to a glance at the screenshot: the sheet
 * was double-offset by being bottom-aligned *and* offset, and the peek detent
 * silently fell back because `onGloballyPositioned` fires children-first and the
 * anchor had nothing to measure against yet.
 */
class SheetGeometryTest {

    private val density = 2f
    private val canvasHeight = 1120

    /**
     * What a rendered sheet reports about itself, and where it actually landed.
     *
     * Both, because they can disagree — and when they do, the state is the thing
     * that looks right. The double-offset bug had `offset` reporting exactly the
     * correct position while the sheet was drawn off the bottom of the screen.
     */
    private class Measured(val state: SheetState, val drawnTop: Float)

    private fun measure(
        detents: List<SheetDetent>,
        openAt: SheetDetent,
        dragHandle: (@androidx.compose.runtime.Composable () -> Unit)? = null,
        frames: Int = 20,
        body: @androidx.compose.runtime.Composable () -> Unit,
    ): Measured {
        lateinit var captured: SheetState
        var drawnTop = Float.NaN

        val scene = ImageComposeScene(
            width = 700,
            height = canvasHeight,
            density = Density(density),
        ) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        val sheet = rememberSheetState(
                            detents = detents,
                            initialDetent = SheetDetent.Hidden,
                        )
                        captured = sheet
                        LaunchedEffect(Unit) { sheet.animateTo(openAt) }
                        BottomSheet(sheet, dragHandle = dragHandle) {
                            // No handle by default, so the first content node's
                            // top *is* the sheet's top.
                            Box(
                                Modifier.onGloballyPositioned {
                                    drawnTop = it.positionInRoot().y
                                }
                            ) {
                                Column { body() }
                            }
                        }
                    }
                }
            }
        }
        try {
            repeat(frames) { frame -> scene.render(16_000_000L * frame) }
        } finally {
            scene.close()
        }
        return Measured(captured, drawnTop)
    }

    @Test
    fun aSheetOpenedBeforeLayoutStillOpens() {
        // `animateTo` from a LaunchedEffect runs before the first layout, so
        // there are no anchors to move to yet. Dropping the request is how a
        // screen that starts with its sheet open ends up with it shut.
        val sheet = measure(
            detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
            openAt = SheetDetent.Half,
        ) {
            SheetHeader(title = "Perth Underground")
            Box(Modifier.height(400.dp)) { Text("body") }
        }

        assertEquals(SheetDetent.Half, sheet.state.currentDetent)
        assertEquals(canvasHeight / 2f, sheet.state.visibleHeight)
        assertEquals(canvasHeight / 2f, sheet.drawnTop)
    }

    @Test
    fun halfMeansHalfTheContainerNotHalfTheSheet() {
        // The sheet is bottom-aligned *and* offset in the obvious
        // implementation, which double-counts and leaves it sitting far too low.
        val sheet = measure(
            detents = listOf(SheetDetent.Hidden, SheetDetent.Half),
            openAt = SheetDetent.Half,
        ) {
            SheetHeader(title = "Perth Underground")
            Box(Modifier.height(900.dp)) { Text("a very tall body") }
        }

        // The state and the placement have to agree. Bottom-aligning the sheet
        // *and* offsetting it double-counts: `offset` still reports the right
        // number while the sheet is drawn below the bottom of the screen.
        assertEquals(canvasHeight / 2f, sheet.state.offset)
        assertEquals(canvasHeight / 2f, sheet.drawnTop)
    }

    @Test
    fun peekShowsEverythingAboveTheAnchorsBottomEdge() {
        val fallback = 140.dp
        val headerHeight = 88.dp
        val handleHeight = 24.dp

        val sheet = measure(
            detents = listOf(SheetDetent.Hidden, SheetDetent.peek(fallback)),
            openAt = SheetDetent.peek(fallback),
            dragHandle = { Box(Modifier.height(handleHeight)) },
        ) {
            Box(Modifier.height(headerHeight).sheetPeekAnchor()) { Text("header") }
            Box(Modifier.height(600.dp)) { Text("body") }
        }

        // Not the fallback: the anchor was measured and replaced it.
        val fallbackPx = fallback.value * density
        assertTrue(
            abs(sheet.state.visibleHeight - fallbackPx) > 1f,
            "peek fell back to $fallbackPx rather than measuring its anchor",
        )

        // The anchor's height *plus the drag handle above it*. Measuring the
        // anchor alone cuts the last line of the header off by exactly the
        // handle's height.
        // Exactly the handle plus the anchor: everything above the anchor's
        // bottom edge and nothing below it. Measuring the anchor's own height
        // instead cuts the last line of the header off by the handle's height.
        val expected = (handleHeight.value + headerHeight.value) * density
        assertEquals(expected, sheet.state.visibleHeight)
    }

    @Test
    fun expandedIsTheContentsHeightNotTheWindows() {
        val sheet = measure(
            detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
            openAt = SheetDetent.Expanded,
        ) {
            Box(Modifier.height(200.dp)) { Text("short body") }
        }

        // A sheet with one short block in it should be that tall, not a
        // full-screen panel with a block at the top.
        assertTrue(
            sheet.state.visibleHeight < canvasHeight * 0.5f,
            "expanded filled ${sheet.state.visibleHeight}px of $canvasHeight " +
                "for a short sheet",
        )
        assertEquals(200f * density, sheet.state.visibleHeight)
        assertEquals(canvasHeight - 200f * density, sheet.drawnTop)
    }
}
