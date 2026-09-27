package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.nav.NavDefaults
import io.kontour.ui.nav.NavRail
import java.awt.image.BufferedImage
import kotlin.math.abs
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
 * see. This drives the real thing — a sidebar with a real `group` — which is the
 * shape that was asked for. The drawer it was reported on is gone; a sidebar is
 * a `NavRail(expanded = true)` now, and its groups are the same disclosure rows.
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
 *
 * ### Where it goes instead, now
 *
 * The drawer faded the marker where it stood. A rail's group **carries** the
 * marker while the destination selected inside it is hidden, so the sidebar
 * always says where you are — which means the marker now has somewhere to go
 * when the group closes: up one row, onto the group, and no further. That is
 * what the first test asks, and it is why the current page is the group's
 * *first* destination. A marker following that row as it is clipped away rides
 * it up past the group and over the row above; one that lets go and lands on
 * the group stops at the group's own row.
 */
class NavRailGroupMarkerTest {

    /** Nothing else in a sidebar draws in this, so any of it on screen is the marker. */
    private val Marker = Color(0xFFFF00FF)

    /** Which destination is current. */
    private enum class Current {
        /** The group's first destination, the one nearest the group's own row. */
        InsideTheGroup,

        /** The row under the group, which moves up when the group closes. */
        BelowTheGroup,
    }

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
     * A fixed sidebar of text rows, the old drawer's width, with one group.
     *
     * Text rows — `icon = null` — because the drawer this was reported on drew
     * its rows without glyphs.
     */
    @Composable
    private fun Sidebar(current: Current, open: Boolean, onOpenChange: (Boolean) -> Unit) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            NavRail(
                expanded = true,
                expandedWidth = 320.dp,
                colours = NavDefaults.colours(indicator = Marker),
            ) {
                item(false, {}, key = "overview", icon = null) { +"Overview" }
                item(false, {}, key = "alerts", icon = null) { +"Alerts" }
                group(open, onOpenChange, key = "content", icon = null, header = { +"Content" }) {
                    item(current == Current.InsideTheGroup, {}, key = "routes", icon = null) { +"Routes" }
                    item(false, {}, key = "stops", icon = null) { +"Stops" }
                }
                item(current == Current.BelowTheGroup, {}, key = "settings", icon = null) { +"Settings" }
            }
        }
    }

    /**
     * Collapses the group and returns the marker's rows before, then each frame
     * after.
     */
    private fun collapse(current: Current): Pair<IntRange?, List<IntRange?>> {
        var open by mutableStateOf(true)
        val after = mutableListOf<IntRange?>()
        var before: IntRange? = null

        Scene(width = 360, height = 700) {
            Sidebar(current, open) { open = it }
        }.use { scene ->
            before = scene.frames(20).markerRows()
            open = false
            repeat(30) { after += scene.frame().markerRows() }
        }
        return before to after
    }

    /**
     * Where the marker rests on the group's own row: the group closed from the
     * start, with the current page hidden inside it.
     */
    private fun restingOnTheGroup(): IntRange? =
        Scene(width = 360, height = 700) {
            Sidebar(Current.InsideTheGroup, open = false) {}
        }.use { it.frames(30).markerRows() }

    @Test
    fun aMarkerInsideTheGroupGoesToTheGroupAndNoFurther() {
        val group = assertNotNull(
            restingOnTheGroup(),
            "a closed group holding the current page drew no marker on itself — " +
                "it has to carry it, or the sidebar says the page is nowhere",
        )
        val (before, after) = collapse(Current.InsideTheGroup)
        val start = assertNotNull(before, "the marker was never drawn on the selected sub-item")
        assertTrue(
            start.first > group.last,
            "the selected destination's marker at $start is not below its group's " +
                "row at $group, so this run cannot tell travelling to the group from " +
                "travelling past it",
        )

        // Only the frames where it is on screen: a frame mid-fade has nothing to
        // be in the wrong place.
        val drawn = after.filterNotNull()
        assertTrue(
            drawn.isNotEmpty(),
            "the marker vanished in the same frame the group closed and never " +
                "came back, so this run cannot tell a marker that went to the group " +
                "from one that stopped being drawn",
        )
        val highest = drawn.minOf { it.first }
        assertTrue(
            group.first - highest <= 4,
            "while the group closed the marker climbed to row $highest — " +
                "${group.first - highest}px above the group's own row, which starts " +
                "at ${group.first}. It is following a destination that is being " +
                "clipped away, and it is drawn outside that clip, so it walks up " +
                "over the rows above the group.",
        )

        val end = assertNotNull(
            after.last(),
            "the marker was gone once the group had closed. The group holds the " +
                "current page, so it should be carrying the marker.",
        )
        assertTrue(
            abs(end.first - group.first) <= 2 && abs(end.last - group.last) <= 2,
            "the marker settled at $end once the group had closed, not on the " +
                "group's own row at $group",
        )
    }

    @Test
    fun aMarkerBelowTheGroupStillFollowsTheRowsUp() {
        // The motion that must survive. Everything under a collapsing group
        // genuinely moves, and a marker that stopped following *that* would be
        // left behind over whatever slid into its place — the same defect from
        // the other side.
        val (before, after) = collapse(Current.BelowTheGroup)
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
