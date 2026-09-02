package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Home
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavRail
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A rail too short for its destinations does not squash the last few.
 *
 * Reported as: cut the rail off at the bottom and the last item or two come out
 * shrunk. That is what a `Column` does when it is asked for more height than it
 * has — it measures its children in order against the room that is left, so the
 * ones at the end are measured against nothing and collapse to their minimum. A
 * destination half the height of the one above it reads as a rendering fault,
 * and one squashed to nothing cannot be pressed at all.
 *
 * Measured off the pixels rather than the semantics tree: a squashed row is
 * still a row as far as semantics are concerned, and its *height* is the whole
 * question. Each destination is a band of ink with a gap above and below it, so
 * counting the bands and comparing their heights asks exactly what was reported.
 */
class NavOverflowTest {

    @Test
    fun runningOutOfRoomDoesNotCrushTheDestinationsTogether() {
        // The gap between one destination and the next, from a rail with room
        // to spare and from one without. That is the measurement the report
        // describes: given too little height a `Column` measures its children in
        // order against what is left, so the ones at the end lose their spacing
        // and end up on top of each other. Rendered, the second and third
        // destinations sat 55px apart against the 96px everything else had.
        //
        // The gap rather than the height, because the *icons* keep their size
        // either way — it is the rows around them that collapse, which is why an
        // assertion on height alone passed with the defect still present.
        val roomy = gaps(items = 9, height = 1200)
        val cramped = gaps(items = 9, height = 300)

        assertTrue(roomy.isNotEmpty(), "nothing was drawn in the roomy rail")
        assertTrue(cramped.isNotEmpty(), "nothing was drawn in the cramped rail")

        val expected = roomy.min()
        assertTrue(
            cramped.min() >= expected - 2,
            "destinations came ${cramped.min()}px apart in a rail too short for " +
                "them, against ${expected}px in one with room — they are being " +
                "crushed together rather than scrolled. Roomy $roomy, cramped " +
                "$cramped.",
        )
    }

    /** The vertical distance between the start of each band of ink and the next. */
    private fun gaps(items: Int, height: Int): List<Int> {
        val starts = bandStarts(items, height)
        return starts.zipWithNext { a, b -> b - a }
    }

    /** The y of the top of every horizontal band of ink inside the rail. */
    private fun bandStarts(items: Int, height: Int): List<Int> {
        var image: BufferedImage? = null
        Scene(width = 260, height = height) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavRail(
                    items = List(items) { NavItem("Item ${it + 1}", Tabler.Outline.Home, {}) },
                    selectedIndex = 0,
                    expanded = true,
                )
            }
        }.use { scene -> image = scene.frames(12) }

        val frame = requireNotNull(image)
        // The rail's own surface is the background here, so a row of ink is a
        // row that differs from the *rail*, read a little in from its edge.
        val ground = frame.getRGB(4, 4)
        val inked = (0 until frame.height).map { y ->
            (0 until frame.width).any { x -> differs(frame.getRGB(x, y), ground) }
        }

        val starts = mutableListOf<Int>()
        var run = 0
        var top = 0
        for ((y, on) in inked.withIndex()) {
            if (on) {
                if (run == 0) top = y
                run++
            } else {
                // Bands shorter than a glyph are separators and antialiasing.
                if (run > 8) starts += top
                run = 0
            }
        }
        if (run > 8) starts += top
        return starts
    }

    private fun differs(a: Int, b: Int): Boolean =
        kotlin.math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > 24 ||
            kotlin.math.abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > 24 ||
            kotlin.math.abs((a and 0xFF) - (b and 0xFF)) > 24
}
