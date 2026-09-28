package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.BottomSheetDefaults
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.KontourTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How wide a sheet is, and where it sits, in a window wider than it should be.
 *
 * `BottomSheetDefaults.MaxWidth` is 640dp and its KDoc has always said *"A sheet wider
 * than this is a panel; centre it rather than stretching it."* It did not work.
 * The sheet's box was `fillMaxWidth().widthIn(max = MaxWidth)`, and `fillMaxWidth`
 * hands its child *fixed* constraints — minimum and maximum both the window's
 * width — so the 640dp cap was coerced straight back up to the window. On a
 * desktop the sheet spanned the whole screen, which is what was reported.
 *
 * Nothing caught it because nothing had ever measured a sheet's width. Every
 * other test here is about the vertical — detents, the drag, the float — and the
 * goldens draw their sheets inside phone-sized frames, where the cap never binds.
 * The order is the whole fix: `widthIn` first lowers the maximum, and
 * `fillMaxWidth` then fills *that*.
 *
 * ### Measured from the caller's modifier
 *
 * The caller's `modifier` is the first link of the sheet's box's chain, so a
 * probe on it reports the box as its parent placed it: the width every later
 * modifier resolved to, and the x the alignment put it at.
 */
class SheetWidthTest {

    private class Placed(
        val sheetX: Float,
        val sheetWidth: Float,
        /** The leftmost and rightmost pixel any floating control reached. */
        val controlsLeft: Float,
        val controlsRight: Float,
    )

    /** Every probe reports into the same pair, so a row of several reads as one span. */
    private class Span {
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
    }

    private fun Modifier.probe(span: Span): Modifier = onGloballyPositioned {
        val x = it.positionInRoot().x
        span.left = min(span.left, x)
        span.right = max(span.right, x + it.size.width)
    }

    /**
     * One sheet, open, in a window [windowDp] wide.
     *
     * [sheet] is handed the state, the probe modifier and the controls, and
     * draws the sheet — so the arms that exercise a parameter pass it here
     * rather than the harness growing an argument per parameter.
     */
    private fun place(
        windowDp: Int,
        fillControls: Boolean = false,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        sheet: @Composable (SheetState, Modifier, @Composable RowScope.() -> Unit) -> Unit =
            { state, modifier, controls ->
                BottomSheet(state, modifier = modifier, floatingControls = controls) {
                    Box(Modifier.height(200.dp))
                }
            },
    ): Placed {
        var sheetX = Float.NaN
        var sheetWidth = Float.NaN
        val controls = Span()

        val scene = ImageComposeScene(
            width = windowDp * Density,
            height = Height,
            density = Density(Density.toFloat()),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        val state = rememberSheetState(
                            detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                            initialDetent = SheetDetent.Hidden,
                        )
                        LaunchedEffect(Unit) { state.animateTo(SheetDetent.Expanded) }
                        sheet(
                            state,
                            Modifier.onGloballyPositioned {
                                sheetX = it.positionInRoot().x
                                sheetWidth = it.size.width.toFloat()
                            },
                        ) {
                            // A spacer that takes whatever the row has left makes
                            // the probes span the row's whole inside, so the pair
                            // measures the row rather than the one control in it.
                            if (fillControls) Spacer(Modifier.weight(1f).probe(controls))
                            Box(Modifier.size(40.dp).probe(controls))
                        }
                    }
                }
            }
        }
        try {
            // Closed every frame. `render` hands back a Skia image in *native*
            // memory, and a loop that only renders allocates almost nothing on
            // the heap — so the collector never runs to reclaim the images, and
            // at this window's size sixty of them are most of a gigabyte. Seven
            // of these in one executor took the whole suite's machine into swap.
            repeat(Frames) { frame -> scene.render(16_000_000L * frame).close() }
        } finally {
            scene.close()
        }
        return Placed(sheetX, sheetWidth, controls.left, controls.right)
    }

    @Test
    fun aSheetOnAWideWindowStopsAtItsMaxWidth() {
        val placed = place(windowDp = Wide)
        assertEquals(
            Cap,
            placed.sheetWidth,
            "a sheet in a ${Wide}dp window came out ${placed.sheetWidth / Density}dp " +
                "wide. `BottomSheetDefaults.MaxWidth` is ${Cap / Density}dp and says a sheet " +
                "wider than that is a panel — the cap is being coerced back up to the " +
                "window by the `fillMaxWidth` that runs before it",
        )
    }

    /**
     * And a phone is untouched: below the cap, the sheet is the window.
     *
     * Passes before the fix and after it, and that is its job. The only way to
     * get the cap right that a phone could notice is to get *this* wrong.
     */
    @Test
    fun aSheetOnANarrowWindowFillsIt() {
        val placed = place(windowDp = Phone)
        assertEquals(Phone * Density.toFloat(), placed.sheetWidth, "a sheet on a phone must fill it")
        assertEquals(0f, placed.sheetX, "a sheet on a phone starts at its left edge")
    }

    /**
     * The controls above the sheet are the sheet's, so they stop where it does.
     *
     * The row repeated the sheet's three width modifiers, in the same wrong order,
     * so it had the same defeated cap: a map's "recentre" button sat at the far
     * right of a desktop window while the sheet it rides on was — once capped — in
     * the middle of it.
     */
    @Test
    fun theFloatingControlsAreNoWiderThanTheSheet() {
        val placed = place(windowDp = Wide, fillControls = true)
        assertTrue(
            placed.controlsLeft >= placed.sheetX &&
                placed.controlsRight <= placed.sheetX + placed.sheetWidth,
            "the floating controls spanned ${placed.controlsLeft}..${placed.controlsRight}px " +
                "and the sheet ${placed.sheetX}..${placed.sheetX + placed.sheetWidth}px — " +
                "they ride on the sheet, and reach past it",
        )
    }

    @Test
    fun anEndAlignedSheetSitsAgainstTheTrailingEdge() {
        val placed = place(windowDp = Wide) { state, modifier, controls ->
            BottomSheet(
                state,
                modifier = modifier,
                alignment = OverlayAlignment.End,
                floatingControls = controls,
            ) { Box(Modifier.height(200.dp)) }
        }
        assertEquals(Cap, placed.sheetWidth, "an aligned sheet is still capped")
        assertEquals(
            Wide * Density - Cap,
            placed.sheetX,
            "an end-aligned sheet should meet the window's right edge",
        )
    }

    /**
     * End is the *trailing* edge, which in a right-to-left locale is the left.
     *
     * Free, because the mapping is to `Alignment.TopEnd` and Compose resolves that
     * against the layout direction — and worth an arm, because the free version is
     * the one a later hand "simplifies" to `AbsoluteAlignment` without noticing.
     */
    @Test
    fun anEndAlignedSheetSitsOnTheLeftInRightToLeft() {
        val placed = place(windowDp = Wide, layoutDirection = LayoutDirection.Rtl) { state, modifier, controls ->
            BottomSheet(
                state,
                modifier = modifier,
                alignment = OverlayAlignment.End,
                floatingControls = controls,
            ) { Box(Modifier.height(200.dp)) }
        }
        assertEquals(0f, placed.sheetX, "in RTL the trailing edge is the left one")
    }

    /**
     * Controls at the start *of the sheet*, not of the window.
     *
     * The sheet is against the right edge and its controls are asked for at their
     * start — so they belong over the sheet's own left corner, some 760dp in from
     * the window's. A row that took its arrangement from the window, or that was
     * still the window's width, puts them at the far left of the screen.
     */
    @Test
    fun controlsAtTheStartSitOverTheSheetsStartNotTheWindows() {
        val placed = place(windowDp = Wide) { state, modifier, controls ->
            BottomSheet(
                state,
                modifier = modifier,
                alignment = OverlayAlignment.End,
                floatingControlsAlignment = OverlayAlignment.Start,
                floatingControls = controls,
            ) { Box(Modifier.height(200.dp)) }
        }
        val fromSheetStart = placed.controlsLeft - placed.sheetX
        assertTrue(
            fromSheetStart in 0f..(ControlsInset * 2),
            "start-aligned controls began ${fromSheetStart}px from the sheet's own start " +
                "(the sheet is at ${placed.sheetX}px, the controls at ${placed.controlsLeft}px). " +
                "They belong over the sheet's left corner, not the window's",
        )
    }

    /**
     * A modal sheet that is already up follows a change to its alignment.
     *
     * `ModalBottomSheet` publishes an overlay entry whose content is captured when
     * it opens, so anything read directly inside it is frozen at that moment. Eight
     * parameters had already been bitten by exactly this and hoisted through
     * `rememberUpdatedState` one at a time; a new one that is not hoisted is the
     * ninth. A window resized under an open sheet is the real case.
     */
    @Test
    fun aModalSheetFollowsItsAlignmentWhileOpen() {
        var alignment by mutableStateOf(OverlayAlignment.Centre)
        var sheetX = Float.NaN
        val scene = ImageComposeScene(
            width = Wide * Density,
            height = Height,
            density = Density(Density.toFloat()),
        ) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    ModalBottomSheet(
                        visible = true,
                        onDismissRequest = {},
                        modifier = Modifier.onGloballyPositioned { sheetX = it.positionInRoot().x },
                        alignment = alignment,
                    ) { Box(Modifier.height(200.dp)) }
                }
            }
        }
        val centred: Float
        try {
            repeat(Frames) { frame -> scene.render(16_000_000L * frame).close() }
            centred = sheetX
            alignment = OverlayAlignment.End
            repeat(Frames) { frame -> scene.render(16_000_000L * (Frames + frame)).close() }
        } finally {
            scene.close()
        }
        assertEquals((Wide * Density - Cap) / 2f, centred, "the modal sheet opened centred")
        assertEquals(
            Wide * Density - Cap,
            sheetX,
            "the modal sheet stayed where it opened after its alignment changed to End — " +
                "the parameter is being read from the entry captured when it was shown",
        )
    }

    private companion object {
        const val Density = 2
        const val Height = 1120

        /** A desktop window: comfortably past the cap. */
        const val Wide = 1400

        /** A phone: comfortably under it. */
        const val Phone = 390

        /** `BottomSheetDefaults.MaxWidth`, in this scene's pixels. */
        const val Cap = 640f * Density

        /** Long enough for a reduced-motion open to come to rest. */
        const val Frames = 60

        /** `Theme.spacing.md`, the row's own side padding, in pixels. */
        const val ControlsInset = 16f * Density
    }
}
