package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A part is hidden by the sheet's own edge, and by nothing else.
 *
 * `part(from = SheetDetent.Half) { DepartureBoard() }` is the sheet saying which
 * of its sizes a piece of content belongs to, once, rather than the caller
 * re-deciding on every frame of a drag.
 *
 * **What it must not do is appear.** Two versions of this did: one composed the
 * part when the sheet settled at its detent, and one composed it always and
 * revealed it with a clipped height when the drag committed. Both were reported
 * the same way — *"it still just appears partway through the animation ... it
 * needs to be almost as if the content existed all along, as soon as the user
 * starts dragging it"*.
 *
 * So it does exist all along. A sheet is a column pinned to the top of a card and
 * the card is only so tall: a part further down the column is already laid out and
 * already drawn, below the edge the card shows, and dragging the sheet up uncovers
 * it at the speed of the finger. The arms below are the two halves of that — it is
 * not on screen when the sheet is short, and it is on screen on the *first frame*
 * of a drag, long before any detent has been reached.
 *
 * ### What it is measured by
 *
 * A flat colour nothing else in the scene paints, counted. Each part is a
 * fixed-height box of it, so "is it there" is a pixel count with two answers far
 * apart rather than a threshold — and counting rather than looking for an edge is
 * what makes the reading survive a part that is half uncovered.
 */
class SheetPartsTest {

    @Test
    fun aPartBelowTheFoldIsHiddenUntilTheSheetIsTallEnough() {
        var target by mutableStateOf<SheetDetent>(Bar)
        var atBar = 0
        var atHalf = 0
        var backAtBar = 0

        scene(onState = { }, target = { target }) { scene ->
            atBar = scene.frames(40).count(BoardRgb)
            target = SheetDetent.Half
            atHalf = scene.frames(60).count(BoardRgb)
            target = Bar
            backAtBar = scene.frames(60).count(BoardRgb)
        }

        assertTrue(
            atBar == 0,
            "the departure board drew ${atBar}px with the sheet at its bar detent, " +
                "where the header above it is taller than the bar. The sheet's own " +
                "edge is what hides a part, so a part below the fold is not on screen",
        )
        assertTrue(
            atHalf > Substantial,
            "the board drew ${atHalf}px at Half, where a 120dp band across a 600px " +
                "sheet is about ${120 * 2 * 600}px. Either the sheet did not get " +
                "there or the part is not in the column at all",
        )
        assertTrue(
            backAtBar == 0,
            "the board drew ${backAtBar}px after the sheet went back to the bar " +
                "detent — it goes back under the edge it came out from",
        )
    }

    /**
     * **The first frame of a drag already has it.**
     *
     * This is the arm the report is about, and the one both earlier versions fail:
     * neither had drawn a single pixel of the part this early, because both were
     * waiting for a detent — one for the sheet to settle at it, one for the drag to
     * commit to it. The finger here has travelled a fraction of the way to `Half`
     * and has not let go.
     */
    @Test
    fun aPartIsOnScreenOnTheFirstFrameOfADrag() {
        var target by mutableStateOf<SheetDetent>(Bar)
        lateinit var sheet: SheetState
        var dragged = 0
        var movedBy = 0f

        scene(onState = { sheet = it }, target = { target }) { scene ->
            scene.frames(40)
            val before = sheet.offset
            // On the sheet's own surface, just below its top edge, which is where
            // the drag handle is.
            val grip = Offset(300f, 900f - BarHeight + Grip)
            scene.press(grip)
            scene.move(Offset(grip.x, grip.y - DragUp))
            dragged = scene.frame().count(BoardRgb)
            movedBy = before - sheet.offset
            scene.release(Offset(grip.x, grip.y - DragUp))
        }

        assertTrue(
            movedBy > DragUp / 2f,
            "the sheet only moved ${movedBy}px for a ${DragUp}px drag — the gesture " +
                "did not reach it, so nothing below is being measured",
        )
        assertTrue(
            dragged > Substantial,
            "the departure board drew ${dragged}px on the first frame of a drag that " +
                "had already uncovered ${movedBy}px of sheet. It is meant to be " +
                "there all along, below the sheet's edge, rather than arriving when " +
                "a detent is reached",
        )
    }

    /** A part with no `from` is there at every size, which is the other half. */
    @Test
    fun aPartWithoutADetentIsAlwaysThere() {
        var target by mutableStateOf<SheetDetent>(Bar)
        var atBar = 0
        var atHalf = 0

        scene(onState = { }, target = { target }) { scene ->
            atBar = scene.frames(40).count(HeaderRgb)
            target = SheetDetent.Half
            atHalf = scene.frames(60).count(HeaderRgb)
        }

        assertTrue(atBar > Substantial, "a part with no detent drew ${atBar}px at the bar detent")
        assertTrue(atHalf > Substantial, "a part with no detent drew ${atHalf}px at Half")
    }

    /**
     * A sheet with a header and a departure board under it.
     *
     * The header is **taller than the bar detent**, so the board below it starts
     * under the sheet's edge — which is what makes "hidden" mean something here
     * without anything hiding it.
     */
    private fun scene(
        onState: (SheetState) -> Unit,
        target: () -> SheetDetent,
        body: (Scene) -> Unit,
    ) {
        Scene(width = 600, height = 900) {
            KontourTheme(reduceMotion = true) {
                val state = rememberSheetState(
                    detents = listOf(Bar, SheetDetent.Half),
                    initialDetent = Bar,
                )
                onState(state)
                val wanted = target()
                // Driven from inside the scene: `animateTo` suspends, and what is
                // under test is where the sheet ends up rather than how it was
                // asked to get there.
                LaunchedEffect(wanted) { state.animateTo(wanted) }
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Ground))
                    BottomSheet(state = state, containerColour = Ground) {
                        part {
                            Box(Modifier.fillMaxWidth().height(80.dp).background(HeaderColour))
                        }
                        part(from = SheetDetent.Half) {
                            Box(Modifier.fillMaxWidth().height(120.dp).background(BoardColour))
                        }
                    }
                }
            }
        }.use { scene -> body(scene) }
    }

    private fun BufferedImage.count(rgb: Int): Int {
        var found = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((getRGB(x, y) and 0xFFFFFF) == rgb) found++
            }
        }
        return found
    }

    private companion object {
        /**
         * Shorter than the header above the board, which is the whole arrangement.
         *
         * 72dp of sheet against an 80dp header means the board starts 8dp below the
         * edge — enough to be plainly off screen, little enough that a short drag
         * brings a lot of it out.
         */
        val Bar = SheetDetent.height("bar", 72.dp)

        /** The bar detent in scene pixels, at the scene's density of 2. */
        const val BarHeight = 144f

        /** Into the sheet from its top edge, onto the drag handle. */
        const val Grip = 16f

        /** Far enough to clear touch slop and uncover most of the board. */
        const val DragUp = 200f

        val Ground = Color(0xFF3355AA)
        val HeaderColour = Color(0xFF22EE11)
        const val HeaderRgb = 0x22EE11
        val BoardColour = Color(0xFFEE2211)
        const val BoardRgb = 0xEE2211

        /**
         * More ink than an antialiased edge and far less than a whole band.
         *
         * A 120dp band across a 600px-wide sheet is about 144,000px at this
         * density, so anything in the thousands is the band and nothing else is.
         */
        const val Substantial = 5_000
    }
}
