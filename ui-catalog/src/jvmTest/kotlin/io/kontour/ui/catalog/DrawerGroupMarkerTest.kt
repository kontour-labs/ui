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
import io.kontour.ui.nav.NavDrawer
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The marker does not fly off when the group under it closes.
 *
 * Reported twice: collapse a drawer group while the current page is one of its
 * children, and the marker shoots up the list. Round 25 reproduced it against a
 * hand-built `SelectionIndicatorBox` in a plain `Column`, fixed what that
 * showed, and the fix was reverted when it changed nothing the reporter could
 * see. This drives the real thing — a `NavDrawer` with a real `group` — which is
 * the shape that was asked for.
 *
 * ### What it was, in rows
 *
 * The marker's vertical extent, frame by frame, from the collapse onward:
 *
 * ```
 * before  402..487
 * after   402..487, 402..487, 309..394, 263..348, 241..326, 231..316,
 *         226..311, 223..308, 222..307 … then gone
 * ```
 *
 * Fourteen frames of visible travel, 180px up the list, and only then a fade.
 * `SelectionIndicatorBox` has always had a branch for "the selected item stopped
 * reporting — fade out where it stands", and it was never reached: a collapsing
 * container does not stop composing its content, it clips it. So the rows inside
 * went on reporting smaller, higher rects every frame, the marker followed them
 * faithfully, and because it is drawn at the *list's* level rather than inside
 * the group, it followed them straight out through the clip that was hiding
 * them.
 *
 * The rows really are moving, and every row *below* a collapsing group moves too
 * — that motion should be followed, which is the second test here. What must not
 * be followed is a row moving because it is being clipped away. Only the
 * container can tell those apart, so the container says so, through
 * `LocalSelectionIndicatorLeaving`.
 */
class DrawerGroupMarkerTest {

    /** Nothing else in a drawer draws in this, so any of it on screen is the marker. */
    private val Marker = Color(0xFFFF00FF)

    /**
     * The marker's vertical extent, or `null` when it is not drawn at all.
     *
     * Magenta rather than the real `accent.container` for the reason
     * `NavRailGrowthTest` uses it: a token colour has to be told apart from the
     * surface it sits on and from the text over it, and a threshold that does
     * that today moves the day a colour does.
     */
    private fun BufferedImage.markerRows(): IntRange? {
        var top = -1
        var bottom = -1
        for (y in 0 until height) {
            var hit = false
            for (x in 0 until width) {
                val p = getRGB(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r > 180 && b > 180 && g < 90) {
                    hit = true
                    break
                }
            }
            if (hit) {
                if (top < 0) top = y
                bottom = y
            }
        }
        return if (top < 0) null else top..bottom
    }

    /**
     * Collapses the group and returns the marker's rows before, then each frame
     * after.
     *
     * @param selectedInside Whether the current page is inside the group that
     *   closes, or the row below it.
     */
    private fun collapse(selectedInside: Boolean): Pair<IntRange?, List<IntRange?>> {
        var open by mutableStateOf(true)
        val after = mutableListOf<IntRange?>()
        var before: IntRange? = null

        Scene(width = 360, height = 700) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavDrawer(width = 320.dp, indicatorColour = Marker) {
                    item(label = "Overview", selected = false) {}
                    item(label = "Alerts", selected = false) {}
                    group(label = "Content", expanded = open, onExpandedChange = { open = it }) {
                        item(label = "Routes", selected = false) {}
                        item(label = "Stops", selected = selectedInside) {}
                    }
                    item(label = "Settings", selected = !selectedInside) {}
                }
            }
        }.use { scene ->
            before = scene.frames(20).markerRows()
            open = false
            repeat(30) { after += scene.frame().markerRows() }
        }
        return before to after
    }

    @Test
    fun aMarkerInsideTheGroupFadesWhereItStandsRatherThanTravelling() {
        val (before, after) = collapse(selectedInside = true)
        val start = assertNotNull(before, "the marker was never drawn on the selected sub-item")

        // Only the frames where it is still on screen: once it has faded there is
        // nothing to be in the wrong place.
        val drawn = after.filterNotNull()
        val highest = drawn.minOf { it.first }

        assertTrue(
            drawn.isNotEmpty(),
            "the marker vanished in the same frame the group closed, so this run " +
                "cannot tell a fade-in-place from a fix that stopped drawing it",
        )
        assertTrue(
            start.first - highest <= 4,
            "while fading out, the marker climbed from row ${start.first} to row " +
                "$highest — ${start.first - highest}px up the list, across " +
                "${drawn.size} frames it was still visible. It is following rows " +
                "that are being clipped away, and it is drawn outside that clip, " +
                "so it walks up over the rows above them.",
        )
    }

    @Test
    fun aMarkerBelowTheGroupStillFollowsTheRowsUp() {
        // The motion that must survive. Everything under a collapsing group
        // genuinely moves, and a marker that stopped following *that* would be
        // left behind over whatever slid into its place — the same defect from
        // the other side.
        val (before, after) = collapse(selectedInside = false)
        val start = assertNotNull(before, "the marker was never drawn on the row below the group")
        val end = assertNotNull(after.last(), "the marker stopped being drawn on a row that is still there")

        assertTrue(
            start.first - end.first > 100,
            "the row below the group rose as the group closed, but the marker " +
                "only moved from ${start.first} to ${end.first}. A marker that " +
                "does not follow a row that really moved is stranded over " +
                "whatever took its place.",
        )
    }
}
