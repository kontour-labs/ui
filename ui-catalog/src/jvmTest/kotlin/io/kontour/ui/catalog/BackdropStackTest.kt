package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.BackdropStyle
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.OverlayHostState
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * One overlay opened over another leaves what is behind them where it was.
 *
 * Reported as *"when a modal sheet is open and a dialog appears, the backdrop
 * from the modal sheet snaps back to normal size. When the dialog is closed, it
 * goes back to being shrunk and darkened."* The host took the backdrop from the
 * topmost overlay that dims and from nothing else, so a dialog opening over a
 * sheet swapped the sheet's recede for its own blur, at its own fraction of
 * zero: the page came forward to full size in one frame. The scrims did the
 * same thing more slowly — only the topmost drew colour, and the sheet's went
 * clear on a 220ms clock of its own while the dialog's arrived on its spring —
 * so the page lightened and darkened again round the dialog.
 *
 * Every frame is read, opening and closing, because both faults were in the
 * frames between rather than at rest.
 */
class BackdropStackTest {

    @Test
    fun aDialogOverASheetLeavesThePageBackAndAsDark() {
        stack(
            under = sheet("sheet", 200.dp),
            over = dialog("dialog", 160.dp),
            recedes = true,
        )
    }

    @Test
    fun aSheetOverASheetLeavesThePageBack() {
        stack(
            // Taller than the one over it, so there is some of it left to see.
            under = sheet("first", 400.dp),
            over = sheet("second", 200.dp),
            recedes = true,
        )
    }

    @Test
    fun aDialogOverADialogLeavesThePageAsDark() {
        stack(
            // Larger than the one over it, for the same reason.
            under = dialog("first", 240.dp),
            over = dialog("second", 160.dp),
            recedes = false,
        )
    }

    /**
     * Opens [under], lets it settle, then opens [over] and closes it again,
     * reading the page on every frame of both.
     */
    private fun stack(under: OverlayEntry, over: OverlayEntry, recedes: Boolean) {
        val host = OverlayHostState()
        Scene(width = Width, height = Height) {
            OverlayHost(Modifier.fillMaxSize().background(Color.Black), host) {
                Box(Modifier.fillMaxSize().background(Theme.colours.background))
            }
        }.use { scene ->
            scene.frames(3)
            val bare = scene.frame()
            host.show(under)
            val rest = scene.frames(SettleFrames)
            val band = rest.band()
            val page = rest.page()

            // What this is measuring has to be there to measure.
            if (recedes) {
                assertTrue(band > 4, "the page did not recede under ${under.key} at all: a ${band}px band")
            }
            assertTrue(
                page < bare.page() - 10,
                "the page did not dim under ${under.key}: ${bare.page()} bare, $page under it",
            )

            host.show(over)
            val opening = List(ReadFrames) { scene.frame() }
            host.hide(over.key)
            val closing = List(ReadFrames) { scene.frame() }

            for ((phase, frames) in listOf("opening" to opening, "closing" to closing)) {
                frames.forEachIndexed { i, frame ->
                    if (recedes) {
                        assertTrue(
                            frame.band() >= band - BandTolerance,
                            "frame $i of ${over.key} $phase over ${under.key}: the band round the " +
                                "page was ${frame.band()}px against ${band}px at rest — the page came " +
                                "forward, which is the snap that was reported",
                        )
                    }
                    assertTrue(
                        abs(frame.page() - page) <= DimTolerance,
                        "frame $i of ${over.key} $phase over ${under.key}: the page read " +
                            "${frame.page()} against $page at rest. One scrim's worth of dark " +
                            "either way, so it should not move while the other comes and goes.",
                    )
                }
            }

            // And the one underneath is dimmed with the page while the other is
            // over it, rather than left bright.
            host.show(over)
            val covered = scene.frames(SettleFrames)
            assertTrue(
                covered.panel(under) < rest.panel(under) - 10,
                "${under.key} stayed as bright under ${over.key} as on its own: " +
                    "${rest.panel(under)} against ${covered.panel(under)}",
            )
        }
    }

    private fun sheet(key: String, tall: Dp) = OverlayEntry(
        key = key,
        layer = OverlayLayer.Sheet,
        backdrop = BackdropStyle.Scale,
        content = {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(tall).background(Color.White))
            }
        },
    )

    private fun dialog(key: String, across: Dp) = OverlayEntry(
        key = key,
        layer = OverlayLayer.Dialog,
        content = {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.Center).size(across).background(Color.White))
            }
        },
    )

    /** How wide the black band left of the page is, across the middle. */
    private fun BufferedImage.band(): Int {
        var run = 0
        val y = height / 4
        for (x in 0 until width / 2) {
            if (lightness(getRGB(x, y)) > BandCeiling) break
            run++
        }
        return run
    }

    /**
     * The page, well clear of the band, the panels and the blur's reach from
     * any of them: a quarter of the way down, in the middle.
     */
    private fun BufferedImage.page(): Int = lightness(getRGB(width / 2, height / 4))

    /**
     * [entry]'s own panel: just inside the top of a sheet, or of a dialog —
     * the part the smaller one over it leaves showing.
     */
    private fun BufferedImage.panel(entry: OverlayEntry): Int =
        if (entry.layer == OverlayLayer.Sheet) {
            val tall = if (entry.key == "first") 400 else 200
            lightness(getRGB(width / 2, height - tall * Density + 40))
        } else {
            val across = if (entry.key == "first") 240 else 160
            lightness(getRGB(width / 2, height / 2 - across * Density / 2 + 20))
        }

    private fun lightness(rgb: Int): Int =
        ((rgb shr 16 and 0xFF) + (rgb shr 8 and 0xFF) + (rgb and 0xFF)) / 3

    private companion object {
        const val Width = 780
        const val Height = 1688
        /** The scene's density: dp to pixels. */
        const val Density = 2

        const val SettleFrames = 60
        const val ReadFrames = 30

        /** Darker than this is the band; the page is `background`, which is light. */
        const val BandCeiling = 60

        /** A pixel of antialiasing on the band's edge. */
        const val BandTolerance = 1

        /** Rounding, and the scrim's own dither. */
        const val DimTolerance = 3
    }
}
