package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
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

    /** The inset under test, in pixels. 40dp at 2x, about a phone's status bar. */
    private val insetPx = 80f

    private class Measured(val sheetTop: Float, val contentTop: Float)

    private fun measure(openAt: SheetDetent): Measured {
        var contentTop = Float.NaN
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
                                }
                            ) {
                                Column { Box(Modifier.height(400.dp)) { Text("body") } }
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
        return Measured(canvasHeight - visible, contentTop)
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
}
