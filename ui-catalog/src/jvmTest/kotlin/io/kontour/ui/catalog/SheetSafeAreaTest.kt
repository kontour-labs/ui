package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
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
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A sheet's chrome against the status bar.
 *
 * Reported from a phone: at full height the drag bar and the header go outside
 * the safe area. The ask was that the sheet still reach just about full screen
 * height and only its chrome stop at the safe zone — so this is about where the
 * *content column* lands, not about how tall the sheet is.
 *
 * ### Why a 40dp inset is passed in by hand
 *
 * There is no status bar in an `ImageComposeScene`, so `WindowInsets.allEdges`
 * — which is what the sheet defaults to now, and which is the other half of the
 * fix — resolves to zero on every side here and can be photographed nowhere.
 * What is testable is the whole of the mechanism: `windowInsets` is a parameter,
 * a constant `WindowInsets(top = 40.dp)` goes through the identical path, and
 * the arithmetic under test does not care where the number came from.
 *
 * ### What it caught
 *
 * That `Modifier.windowInsetsPadding` is not the answer, which is not obvious
 * from its name or its documentation. It pads by the full unconsumed inset
 * wherever the node is — the only thing that reduces it is an ancestor calling
 * `consumeWindowInsets` — so the one-line version of this fix put a status bar's
 * worth of empty sheet above the handle of a sheet sitting at half height, in
 * the middle of the screen. Both cases below failed on it: `Half` reported 640
 * against the 560 it should be, and `Full` reported 104 against 80.
 */
class SheetSafeAreaTest {

    private val density = 2f
    private val canvasHeight = 1120

    /** A gesture bar's worth, in dp — 48px at this density. */
    private val BottomInset = 24
    private val insetPxBottom = BottomInset * density

    /** A list longer than the window, and a scroll longer than the list. */
    private val RowCount = 30
    private val RowHeight = 56
    private val MoreThanTheList = 100_000f

    /** The inset under test, in pixels. 40dp at 2x, about a phone's status bar. */
    private val insetPx = 80f

    private class Measured(
        val sheetTop: Float,
        val contentTop: Float,
        /**
         * Where the content column's bottom edge lands, in window coordinates.
         *
         * The one quantity none of the sheet tests looked at, and the one the
         * second report was about: the column was measured against the whole
         * window and then placed a status bar down, so this came out a status bar
         * *below* the window's own bottom edge. A scroller inside sizes its
         * viewport to that, and its last rows are unreachable.
         */
        val contentBottom: Float,
        /** What the sheet handed the content as its bottom safe area, in dp. */
        val handedBottom: Float = Float.NaN,
    )

    private fun measure(openAt: SheetDetent): Measured {
        var contentTop = Float.NaN
        var contentBottom = Float.NaN
        var visible = Float.NaN

        val scene = ImageComposeScene(
            width = 700,
            height = canvasHeight,
            density = Density(density),
        ) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        val sheet = rememberSheetState(
                            detents = listOf(
                                SheetDetent.Hidden,
                                SheetDetent.Half,
                                SheetDetent.Full,
                            ),
                            initialDetent = SheetDetent.Hidden,
                        )
                        LaunchedEffect(Unit) { sheet.animateTo(openAt) }
                        BottomSheet(
                            sheet,
                            // No handle, so the first content node's top is
                            // where the chrome would begin.
                            dragHandle = null,
                            windowInsets = WindowInsets(top = 40.dp),
                        ) {
                            visible = sheet.visibleHeight
                            Box(
                                Modifier.onGloballyPositioned {
                                    contentTop = it.positionInRoot().y
                                    contentBottom = contentTop + it.size.height
                                }
                            ) {
                                // Taller than the window, which is the case that
                                // matters: content that fits needs no scrolling
                                // and cannot demonstrate an unreachable tail.
                                Column { Box(Modifier.height(2000.dp)) { Text("body") } }
                            }
                        }
                    }
                }
            }
        }
        try {
            repeat(60) { frame -> scene.render(16_000_000L * frame) }
        } finally {
            scene.close()
        }
        return Measured(canvasHeight - visible, contentTop, contentBottom)
    }

    /**
     * At full height the chrome starts where the inset ends, not where the sheet
     * does.
     *
     * The sheet's own top edge is `SheetTopGap` below the window's — 12dp, 24px
     * here — which is a long way above a status bar, so the chrome owes the
     * difference and the content lands exactly at the bottom of the inset band.
     * Not at 24 (no inset at all, the reported defect) and not at 104 (the full
     * inset added to a sheet already 24px down, which is what
     * `windowInsetsPadding` does on its own).
     */
    @Test
    fun aFullHeightSheetStartsItsChromeBelowTheInset() {
        val sheet = measure(SheetDetent.Full)

        assertEquals(
            24f, sheet.sheetTop,
            "the surface should still reach to within `SheetTopGap` of the top " +
                "of the window — the ask was that the sheet go to just about " +
                "full height and only its chrome stop at the safe zone",
        )
        assertEquals(
            insetPx, sheet.contentTop,
            "the chrome landed at ${sheet.contentTop} rather than at the bottom " +
                "of the inset band. Below it is under the status bar, which is " +
                "what was reported; above it is a gap of bare sheet.",
        )
    }

    /**
     * And its content ends at the bottom of the window, not below it.
     *
     * Reported after the inset above shipped: a full-height sheet could not be
     * scrolled to the end of its content. The column is shifted down by the part
     * of the inset the sheet is under, and the measurement beneath it did not
     * know — it took the whole container — so the column's bottom edge landed a
     * status bar's worth below the window's, a `verticalScroll` inside sized its
     * viewport to the oversized measurement, and at the end of its range the last
     * rows were still off-screen.
     *
     * Invisible rather than obviously wrong, which is why nothing caught it:
     * `Surface` clips, and the layout reports a shorter height than the placeable
     * it places. So this measures the placed node itself rather than what it
     * reports, and compares it against the window's own edge.
     *
     * A pixel of slack for the rounding — the reservation is a `Dp` resolved to
     * whole pixels and the canvas is not.
     */
    @Test
    fun aFullHeightSheetsContentEndsAtTheWindowsBottomEdge() {
        val sheet = measure(SheetDetent.Full)

        assertTrue(
            sheet.contentBottom <= canvasHeight + 1f,
            "the content column runs from ${sheet.contentTop} to " +
                "${sheet.contentBottom} in a ${canvasHeight}px window, so " +
                "${sheet.contentBottom - canvasHeight}px of it is below the " +
                "bottom edge. A scroller inside measures its viewport from this, " +
                "so that much of its content cannot be scrolled to at all.",
        )
        assertTrue(
            sheet.contentBottom >= canvasHeight - 1f,
            "the content column stops at ${sheet.contentBottom}, " +
                "${canvasHeight - sheet.contentBottom}px short of the window's " +
                "bottom edge — so the sheet is giving away room it has. The " +
                "reservation is for the inset above the column, not below it; the " +
                "bottom inset is applied inside it.",
        )
    }

    /**
     * And a sheet nowhere near the top pays nothing for it.
     *
     * The case a plain `windowInsetsPadding` gets wrong, and the reason this
     * arithmetic exists at all rather than one extra side on a token. A sheet at
     * half height has its top edge at the middle of the window; there is no
     * status bar there, and a status bar's worth of empty sheet above its handle
     * is a more visible defect than the one being fixed.
     */
    @Test
    fun aHalfHeightSheetIsUntouched() {
        val sheet = measure(SheetDetent.Half)

        assertEquals(canvasHeight / 2f, sheet.sheetTop)
        assertEquals(
            canvasHeight / 2f, sheet.contentTop,
            "a sheet at half height was padded by ${sheet.contentTop - sheet.sheetTop}px " +
                "for a status bar it is nowhere near",
        )
    }

    /**
     * A bottom inset no longer shortens the content area — it is handed over.
     *
     * Reported: *"in bottom sheets, we currently just cut content off at the safe
     * zone at the bottom of the screen. Rather than cutting it off, could we make
     * it so it keeps going, but there's just some content padding so all content
     * is scrollable above the safe zone."*
     *
     * The sheet used to apply the bottom inset as padding on the content column,
     * so the column — and any scroller's viewport inside it — ended a gesture bar
     * above the window. That is the cut. The column now runs to the window's own
     * bottom edge and the inset is handed to the content, which is the only place
     * that knows whether it belongs outside a scroller or inside one.
     *
     * Measured with the same `Measured.contentBottom` probe the top-inset cases
     * use, against a **bottom** inset instead. Fails on today's code by exactly
     * the inset: 48px at this density.
     */
    @Test
    fun aBottomInsetDoesNotShortenTheContentArea() {
        val measured = measureBottomInset()
        assertEquals(
            canvasHeight.toFloat(),
            measured.contentBottom,
            1f,
            "the content column ends at ${measured.contentBottom} in a " +
                "${canvasHeight}px window with a ${BottomInset}dp bottom inset. " +
                "The sheet is not supposed to reserve that band any more: a " +
                "scroller inside sizes its viewport to this, and a viewport that " +
                "stops above the gesture bar is a last row that cannot travel " +
                "through it.",
        )
    }

    /**
     * And the content is told how deep the band is.
     *
     * The other half, and the one that makes the first half safe rather than a
     * regression: a caller that reads the value can put it wherever it belongs —
     * `contentPadding` on a `LazyColumn`, `padding` on a `Column` — and a caller
     * that does not is drawing into the gesture bar on purpose.
     */
    @Test
    fun theContentIsHandedTheBottomInset() {
        val measured = measureBottomInset()
        assertEquals(
            BottomInset.toFloat(),
            measured.handedBottom,
            0.5f,
            "the sheet handed its content ${measured.handedBottom}dp of bottom " +
                "padding under a ${BottomInset}dp inset",
        )
    }

    /** The same scene as [measure], with the inset at the other end. */
    private fun measureBottomInset(): Measured {
        var contentTop = Float.NaN
        var contentBottom = Float.NaN
        var handed = Float.NaN
        var visible = Float.NaN

        val scene = ImageComposeScene(
            width = 700,
            height = canvasHeight,
            density = Density(density),
        ) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        val sheet = rememberSheetState(
                            detents = listOf(
                                SheetDetent.Hidden,
                                SheetDetent.Half,
                                SheetDetent.Full,
                            ),
                            initialDetent = SheetDetent.Hidden,
                        )
                        LaunchedEffect(Unit) { sheet.animateTo(SheetDetent.Full) }
                        BottomSheet(
                            sheet,
                            dragHandle = null,
                            windowInsets = WindowInsets(bottom = BottomInset.dp),
                        ) { padding ->
                            visible = sheet.visibleHeight
                            handed = padding.calculateBottomPadding().value
                            Box(
                                Modifier.onGloballyPositioned {
                                    contentTop = it.positionInRoot().y
                                    contentBottom = contentTop + it.size.height
                                }
                            ) {
                                Column { Box(Modifier.height(2000.dp)) { Text("body") } }
                            }
                        }
                    }
                }
            }
        }
        try {
            repeat(60) { frame -> scene.render(16_000_000L * frame) }
        } finally {
            scene.close()
        }
        return Measured(canvasHeight - visible, contentTop, contentBottom, handed)
    }

    /**
     * A list handed the padding scrolls its last row clear of the gesture bar.
     *
     * The end-to-end claim, and the reason the value is handed over rather than
     * applied: the same number in two different places produces two different
     * results, and only the caller knows which one they want.
     *
     * Measured as **where the last row comes to rest** once the list is scrolled
     * as far as it goes, with the padding inside the scroller and without it.
     * Without, the last row stops at the window's bottom edge, under the bar.
     * With, it stops the inset short of it — having travelled *through* the band
     * on the way, which is what could not happen when the sheet padded the column
     * from outside.
     */
    @Test
    fun aListHandedThePaddingScrollsItsLastRowClearOfTheBar() {
        val bare = lastRowBottom(usePadding = false)
        val padded = lastRowBottom(usePadding = true)

        assertEquals(
            canvasHeight.toFloat(),
            bare,
            2f,
            "a list that ignored the handed padding rested its last row at " +
                "$bare in a ${canvasHeight}px window. It is supposed to run to " +
                "the bottom edge — that is the half of this the sheet no longer " +
                "does for the caller",
        )
        assertEquals(
            canvasHeight - insetPxBottom,
            padded,
            2f,
            "a list given the handed padding as `contentPadding` rested its last " +
                "row at $padded, against ${canvasHeight - insetPxBottom} — the " +
                "window's bottom less the inset. A row that stops anywhere else " +
                "is either under the gesture bar or was never able to reach it.",
        )
    }

    /** Where the last row of a scrolled-to-the-end list settles, in window pixels. */
    private fun lastRowBottom(usePadding: Boolean): Float {
        var bottom = Float.NaN

        val scene = ImageComposeScene(
            width = 700,
            height = canvasHeight,
            density = Density(density),
        ) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        val sheet = rememberSheetState(
                            detents = listOf(SheetDetent.Hidden, SheetDetent.Full),
                            initialDetent = SheetDetent.Hidden,
                        )
                        val rows = rememberLazyListState()
                        LaunchedEffect(Unit) {
                            sheet.animateTo(SheetDetent.Full)
                            // Further than the list is long, so it ends against
                            // its own end rather than wherever a measured scroll
                            // happened to stop.
                            rows.scrollBy(MoreThanTheList)
                        }
                        BottomSheet(
                            sheet,
                            dragHandle = null,
                            windowInsets = WindowInsets(bottom = BottomInset.dp),
                        ) { padding ->
                            LazyColumn(
                                state = rows,
                                contentPadding = if (usePadding) padding else PaddingValues(),
                            ) {
                                items(RowCount) { index ->
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(RowHeight.dp)
                                            .then(
                                                if (index == RowCount - 1) {
                                                    Modifier.onGloballyPositioned {
                                                        bottom = it.positionInRoot().y +
                                                            it.size.height
                                                    }
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    ) { Text("row $index") }
                                }
                            }
                        }
                    }
                }
            }
        }
        try {
            repeat(90) { frame -> scene.render(16_000_000L * frame) }
        } finally {
            scene.close()
        }
        return bottom
    }
}
