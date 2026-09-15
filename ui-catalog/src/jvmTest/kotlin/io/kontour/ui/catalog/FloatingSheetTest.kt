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
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
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

    private companion object {
        val Ground = Color(0xFF3355AA)
        val SheetColour = Color(0xFF11CC55)
        const val SheetRgb = 0x11CC55
    }
}
