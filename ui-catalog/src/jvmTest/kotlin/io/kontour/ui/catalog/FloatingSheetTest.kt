package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetPresentation
import io.kontour.ui.sheet.rememberSheetState
import java.awt.image.BufferedImage
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A floating sheet clears the window on three sides; an edge sheet does not.
 *
 * The difference is the whole of the presentation, and it is one a golden would
 * show and not *assert* — a sheet a few pixels off the edge and a sheet on it
 * are two pictures that both look deliberate. So this measures the bottom row of
 * the window directly: with an edge sheet it is the sheet's own surface, and
 * with a floating one it is whatever the sheet is floating over.
 */
class FloatingSheetTest {

    private fun bottomRowIsSheet(presentation: SheetPresentation): Boolean {
        var open by mutableStateOf(false)
        val image = Scene(width = 600, height = 900) {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Ground))
                    ModalBottomSheet(
                        visible = open,
                        onDismissRequest = {},
                        presentation = presentation,
                        // A colour nothing else in the scene draws, so "is this
                        // the sheet" is a pixel comparison rather than an
                        // inference through a scrim and a receding backdrop.
                        containerColour = SheetColour,
                    ) {
                        Text("Departures")
                    }
                }
            }
        }.use { scene ->
            scene.frames(4)
            open = true
            scene.frames(60)
        }

        // The sheet's own colour, looked for on the very last row.
        //
        // Two earlier versions compared against the ground and then against the
        // scrim, and both passed for *both* presentations: a modal dims the
        // whole window and the receding backdrop leaves its own ground behind,
        // so the bottom row is never the bare ground either way and "not the
        // ground" says nothing. A colour only the sheet paints does.
        val bottom = image.getRGB(image.width / 2, image.height - 2) and 0xFFFFFF
        return bottom == SheetRgb
    }

    @Test
    fun anEdgeSheetReachesTheBottomAndAFloatingOneDoesNot() {
        assertTrue(
            bottomRowIsSheet(SheetPresentation.Edge),
            "an edge sheet did not reach the bottom of the window. That is the " +
                "presentation's whole definition — flush to the bottom and to " +
                "both sides — so if this fails the default has changed, not the " +
                "floating one.",
        )
        assertTrue(
            !bottomRowIsSheet(SheetPresentation.Floating),
            "a floating sheet is still painting the bottom row of the window. It " +
                "is supposed to sit `componentDefaults.sheetFloatingInset` clear of " +
                "all three edges — a panel over the screen rather than a drawer " +
                "out of it — and a bar-height one flush to the bottom reads as " +
                "a drawer that failed to open.",
        )
    }

    /**
     * A sheet with no `Hidden` detent collapses to its lowest one.
     *
     * Not a new mechanism and that is the point of pinning it: the anchors come
     * from the sheet's own detent list, so leaving `Hidden` out means there is
     * no anchor to be dragged away to. It is what makes "collapse around a
     * search bar instead of being swiped out" a detent question rather than a
     * presentation one, and nothing said so before.
     */
    @Test
    fun aSheetWithoutAHiddenDetentCannotBeDraggedAway() {
        var settled: SheetDetent? = null
        val bar = SheetDetent.height("bar", 64.dp)

        Scene(width = 600, height = 900) {
            KontourTheme(reduceMotion = true) {
                val state = rememberSheetState(
                    detents = listOf(bar, SheetDetent.Expanded),
                    initialDetent = bar,
                )
                settled = state.currentDetent
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Ground))
                    io.kontour.ui.sheet.BottomSheet(
                        state = state,
                        presentation = SheetPresentation.Floating,
                    ) {
                        Text("Search")
                    }
                }
            }
        }.use { scene -> scene.frames(40) }

        assertTrue(
            settled == bar,
            "a sheet whose detents are [bar, Expanded] settled at $settled. With " +
                "no `Hidden` in the list there is no anchor to go away to, which " +
                "is what makes a collapsing sheet possible without a new mode.",
        )
    }

    /**
     * **A floating sheet gives its margin back on the way out.**
     *
     * Reported from a phone as the sheet staying floating while it closes, and it
     * did: the bottom margin was applied at every offset, so a closing sheet kept
     * a strip of background under it the whole way down and then vanished with
     * the strip still there. It read as a panel drifting off the bottom rather
     * than as one leaving through it. Measured on this scene — 900px tall, 24px of
     * margin — the sheet's bottom edge sat at 875 on **every** frame of the close.
     *
     * Below the lowest detent that is somewhere to be, the sheet is on its way out
     * and there is nothing down there but hidden, so the margin is paid back and
     * the sheet lands on the window's edge as it goes: 875, 885, 895, 898, gone.
     *
     * The settled reading is asserted too, and is the ratchet. Paying the margin
     * back everywhere would pass the second assertion and make the sheet a
     * floating one in name only.
     */
    @Test
    fun aClosingFloatingSheetLandsOnTheWindowsEdge() {
        var open by mutableStateOf(false)
        var settledGap = -1
        var closingGap = -1

        Scene(width = 600, height = 900) {
            // A spring rather than a tween: the payback is linear in the sheet's
            // visible height rather than timed, so what it has to agree with
            // frame by frame is whatever is moving the sheet.
            KontourTheme(reduceMotion = false) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Ground))
                    ModalBottomSheet(
                        visible = open,
                        onDismissRequest = {},
                        presentation = SheetPresentation.Floating,
                        containerColour = SheetColour,
                    ) {
                        Text("Departures")
                    }
                }
            }
        }.use { scene ->
            scene.frames(4)
            open = true
            settledGap = scene.frames(70).gapUnderTheSheet()
            open = false
            // **Waited for rather than counted.** A modal sheet's close starts in
            // a coroutine and this scene's frame clock is not the wall clock the
            // coroutine runs on, so "twelve frames in" is a different point in
            // the close on a loaded machine than on an idle one — which is how
            // the first version of this passed alone and failed inside the suite.
            // The condition is the thing being asserted: the first frame on which
            // the sheet has given any of the margin back.
            closingGap = scene.renderUntil(timeoutMillis = CloseTimeout) { frame ->
                frame.gapUnderTheSheet() in 0 until FloatingMargin
            }?.gapUnderTheSheet() ?: NeverLanded
        }

        assertEquals(
            FloatingMargin,
            settledGap,
            "a settled floating sheet left ${settledGap}px under it, where the " +
                "margin is ${FloatingMargin}px. The payback is meant to apply " +
                "below the sheet's lowest detent and nowhere else",
        )
        assertTrue(
            closingGap != NeverLanded,
            "the sheet went from ${FloatingMargin}px of margin to off the window " +
                "without ever passing through less. It is leaving through the " +
                "bottom and should be landing on it, not drifting off it with a " +
                "strip of background underneath the whole way down",
        )
    }

    /** Rows between the sheet's lowest ink and the bottom of the window. */
    private fun BufferedImage.gapUnderTheSheet(): Int {
        for (y in height - 1 downTo 0) {
            if ((getRGB(width / 2, y) and 0xFFFFFF) == SheetRgb) return height - 1 - y
        }
        return height
    }

    private companion object {
        val Ground = Color(0xFF3355AA)
        val SheetColour = Color(0xFF11CC55)

        /**
         * `componentDefaults.sheetFloatingInset` in this scene's pixels.
         *
         * The default is 12dp and the scene runs at a density of 2. Written as
         * the number rather than resolved, because a settled floating sheet
         * keeping *exactly* its margin is the assertion — a version that read it
         * from the theme would agree with whatever the component did.
         */
        const val FloatingMargin = 24

        /**
         * How long to wait for the sheet to start giving the margin back.
         *
         * Real milliseconds, because what is being waited for is a coroutine.
         * Short: the close is a spring of well under a second, so a run that
         * reaches this has not been slow, it has not happened.
         */
        const val CloseTimeout = 6_000L

        /** No frame of the close ever showed less than the full margin. */
        const val NeverLanded = -1
        const val SheetRgb = 0x11CC55
    }
}
