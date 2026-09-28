package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.kontour.ui.foundation.Text
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
 * The frame a receding screen leaves, measured on the screen.
 *
 * `BackdropInsetTest` next door pins the arithmetic and is where the reasoning
 * lives. This is the other half of the same claim: that the arithmetic reaches
 * the glass. The two are worth keeping apart — a unit test on
 * `backdropFit` cannot see a call site that reads the wrong field, and the
 * call sites are the part that has been wrong before.
 *
 * ### Why this could not be read off an existing golden
 *
 * `sheets-leaving` draws receding pages, and it draws them as specimens in a
 * montage: cards in a grid, each clipped to its own stage, several of them
 * part-way through an animation. The gap between a page and the black around it
 * is in there, and so is the gap between one specimen and the next, and nothing
 * in the image distinguishes them. A measurement taken off it reported 25 and 42
 * on two edges of a frame that is symmetric by construction, which is a
 * measurement of the montage.
 *
 * So this renders one backdrop, full screen, and reads the four edges directly.
 *
 * ### A small centred panel, not a sheet
 *
 * Every sheet in the library covers at least one edge of the screen — that is
 * what a sheet is — so with one open the four gaps cannot all be photographed:
 * the first draft measured a `SideSheet` and reported no frame at all on the
 * left, because the sheet was sitting on it. An `OverlayEntry` asking for
 * [BackdropStyle.Scale] with a panel in the middle recedes the page exactly the
 * same way and leaves all four edges in view.
 *
 * ### Light theme on purpose
 *
 * The band is black. In dark mode so is the page behind it, and the edge this
 * measures would be an edge between two blacks.
 */
class BackdropFrameTest {

    @Test
    fun aRecedingScreenLeavesTheSameGapOnAllFourEdges() {
        for (window in Windows) {
            val gaps = frameGaps(window)
            val (left, right, top, bottom) = gaps

            assertTrue(
                left > 0,
                "a ${window.width}x${window.height} window left no frame at all on " +
                    "the left — the backdrop never engaged, so this measured nothing",
            )
            for ((edge, gap) in listOf("right" to right, "top" to top, "bottom" to bottom)) {
                assertTrue(
                    abs(gap - left) <= Tolerance,
                    "on a ${window.width}x${window.height} window the frame is " +
                        "${left}px on the left and ${gap}px on the $edge. Equal on " +
                        "every edge is the whole of this: one scale could not do it " +
                        "on a window that is not square, and the difference used to " +
                        "be spent at the bottom and then at the sides — which is " +
                        "what was reported, twice.",
                )
            }
        }
    }

    /** The black band's thickness on each edge, as `(left, right, top, bottom)`. */
    private fun frameGaps(window: Window): List<Int> {
        val host = OverlayHostState()
        var measured = listOf(0, 0, 0, 0)

        Scene(width = window.width, height = window.height) {
            OverlayHost(Modifier.fillMaxSize().background(Color.Black), host) {
                Box(Modifier.fillMaxSize().background(Theme.colours.background))
            }
        }.use { scene ->
            scene.frames(3)
            host.show(
                OverlayEntry(
                    key = "panel",
                    layer = OverlayLayer.Sheet,
                    backdrop = BackdropStyle.Scale,
                    content = { Text("Filters") },
                )
            )
            val image = scene.frames(SettleFrames)
            val cx = window.width / 2
            val cy = window.height / 2
            measured = listOf(
                image.bandRun((0 until window.width).map { it to cy }),
                image.bandRun((window.width - 1 downTo 0).map { it to cy }),
                image.bandRun((0 until window.height).map { cx to it }),
                image.bandRun((window.height - 1 downTo 0).map { cx to it }),
            )
        }
        return measured
    }

    /** How many pixels of band there are before the page starts, along a walk. */
    private fun BufferedImage.bandRun(walk: List<Pair<Int, Int>>): Int {
        var run = 0
        for ((x, y) in walk) {
            val rgb = getRGB(x, y)
            val r = rgb shr 16 and 0xFF
            val g = rgb shr 8 and 0xFF
            val b = rgb and 0xFF
            if (r > BandCeiling || g > BandCeiling || b > BandCeiling) break
            run++
        }
        return run
    }

    private companion object {
        /**
         * Three shapes, because a single scale fails differently on each.
         *
         * A tall phone is the reported device. A near-square window is where the
         * two scales nearly agree and a fault would hide. A landscape window is
         * where the old width-derived scale had no vertical slack at all, so it
         * is the one the previous arrangement got closest to right.
         */
        val Windows = listOf(
            Window(780, 1688),
            Window(1000, 900),
            Window(1600, 700),
        )

        /** Two antialiased edges and the seam pixel the band overlaps by. */
        const val Tolerance = 3

        /** Darker than this is the band; the page is `background`, which is light. */
        const val BandCeiling = 60

        /** Long enough for the backdrop's spring to settle at full recede. */
        const val SettleFrames = 60

        data class Window(val width: Int, val height: Int)
    }
}
