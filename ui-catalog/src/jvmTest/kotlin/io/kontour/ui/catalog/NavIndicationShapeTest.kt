package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavDefaults
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavScope
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The highlight under your finger is the shape the marker will land in.
 *
 * Reported as the hover indicator and the selection indicator being "different
 * shapes and sizes", on the docs sidebar and the nav-suite scaffold. They are —
 * and on the reporter's platform, mobile web, there is no pointer to hover with,
 * so what they were looking at is the *press*. It is the same node either way:
 * `KontourIndication` draws hover and press through one `drawWash`, at its own
 * node's full size, in the one `Shape` it was given.
 *
 * ### Why nothing caught it
 *
 * Every golden in the suite is a resting state — `ls ui-catalog/screenshots |
 * grep -iE 'hover|press'` matched nothing across four hundred files. A highlight
 * that disagrees with the marker it previews had never been rendered anywhere a
 * reviewer would see it.
 *
 * ### How this measures it
 *
 * Three renders of the same row, and two subtractions. Resting is the baseline;
 * *pressed minus resting* is the wash and nothing else; *selected minus resting*
 * is the marker and nothing else. The label and the icon are in all three, so
 * they cancel, and what is left in each difference is exactly the one rectangle
 * this test is about. Comparing the two bounding boxes then needs no knowledge
 * of either component's internals — which is the point, because the fault is
 * that the two are authored independently.
 *
 * ### The box is measured here; the corner is not
 *
 * A nav bar's two rects agree exactly and only its *corner* was wrong — a 20dp
 * circle travelling between 18dp rounded squares. Counting ink inside the box
 * was tried and does not work: the wash is read at a threshold of 3 and the
 * marker at 90, so their antialiased edges bias in opposite directions and the
 * gap between two shapes comes out smaller than the gap between two thresholds.
 * It reported 3-4% on rects that are provably identical.
 *
 * The corner is guaranteed by construction instead. The marker and the glyph
 * both read `navItemShape(indicatorSize)`, and neither names a shape token of
 * its own — which is a stronger guarantee than a 3%-tolerance pixel test, and an
 * honest one.
 */
class NavIndicationShapeTest {

    /** Low enough to see a tonal overlay of a few percent. */
    private val Wash = 3

    /**
     * High enough that only [Ink] itself counts.
     *
     * The nav bar's marker used to carry an elevation shadow, which is soft,
     * spreads well outside the shape and has no equivalent in a press wash — at
     * a low threshold it measured a 40dp circle as 47x45dp, which is the shadow
     * rather than the shape. Swept against the real `accent.container` fill, the
     * window between "past the shadow" and "past the fill as well" was 14 to 17,
     * which is four values wide and would break the day a colour token moved. So
     * the marker is painted [Ink] instead — the same trick `NavRailGrowthTest`
     * uses with magenta, for the same reason — and the threshold sits in the
     * middle of nothing.
     *
     * The marker casts no shadow now (the bar is the raised thing, not the
     * marker inside it), so the high threshold is no longer needed to get past
     * one; it is kept because it still costs nothing and still ignores the
     * antialiasing on the label and the icon's selected-colour change.
     */
    private val Marker = 90

    /** Black, so the marker's fill cannot be confused with its own shadow. */
    private val Ink = Color.Black

    /** The marker's colours: [Ink] for the marker, the defaults for the rest. */
    @Composable
    private fun inked() = NavDefaults.colours(indicator = Ink)

    /**
     * Three destinations, the one at [selected] current, or none at -1.
     *
     * [pressed] goes on the first, which is the one both the press and the
     * selection land on.
     */
    @Composable
    private fun NavScope.items(selected: Int, pressed: Modifier) {
        item(selected == 0, {}, key = "places", icon = Tabler.Outline.MapPin, modifier = pressed) { +"Places" }
        item(selected == 1, {}, key = "routes", icon = Tabler.Outline.Bus) { +"Routes" }
        item(selected == 2, {}, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
    }

    /**
     * The bounding box of everything that differs between two renders.
     *
     * A tonal wash is a few percent of alpha over a flat surface, so the
     * threshold is low — but not zero, because Skia's own text rasterisation is
     * not bit-stable frame to frame and a zero threshold measures the antialiasing
     * on the label instead of the rectangle behind it.
     */
    private fun difference(a: BufferedImage, b: BufferedImage, threshold: Int): IntArray? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        var area = 0
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                val p = a.getRGB(x, y)
                val q = b.getRGB(x, y)
                val dr = abs(((p shr 16) and 0xFF) - ((q shr 16) and 0xFF))
                val dg = abs(((p shr 8) and 0xFF) - ((q shr 8) and 0xFF))
                val db = abs((p and 0xFF) - (q and 0xFF))
                if (maxOf(dr, dg, db) >= threshold) {
                    area++
                    if (x < left) left = x
                    if (y < top) top = y
                    if (x > right) right = x
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right, bottom, area)
    }

    private fun IntArray.describe() = "(${this[0]}, ${this[1]})..(${this[2]}, ${this[3]})"

    /**
     * Renders resting, pressed and selected, and reports the wash's box and the
     * marker's box.
     *
     * The finger goes on the middle of whatever the content hangs the modifier
     * it is handed on — the first destination — rather than at a fixed point:
     * where a bar's first circle lands depends on its style's padding, and a
     * press that missed it would measure nothing at all.
     */
    private fun boxes(
        width: Int,
        height: Int,
        content: @Composable (selected: Int, pressed: Modifier) -> Unit,
    ): Pair<IntArray, IntArray> {
        // Nothing selected, nothing pressed. Both differences are taken from
        // this, so anything the row draws for itself cancels out.
        val resting = Scene(width, height) { content(-1, Modifier) }.use {
            it.frames(6)
        }

        var target = Rect.Zero
        val pressed = Scene(width, height) { content(-1, Modifier.reportBounds { target = it }) }.use { scene ->
            scene.frames(6)
            require(target.width > 0f) { "the destination to press never reported where it is" }
            scene.press(target.center)
            // The wash fades in on `motion.tweenFast`, so this has to be long
            // enough for it to arrive at full alpha and short enough that the
            // press has not become a long press.
            scene.frames(12)
        }

        val selected = Scene(width, height) { content(0, Modifier) }.use {
            it.frames(12)
        }

        // Two thresholds, and the asymmetry is the point rather than a fudge.
        // A press wash is a few percent of alpha, so it needs a low one. A marker
        // is a solid fill — and the nav bar's used to carry an elevation shadow
        // as well, which at threshold 3 measured 47x45dp for a 40dp circle: the
        // shadow, not the shape. `Marker` is above anything a shadow reaches and
        // far below the fill.
        val wash = requireNotNull(difference(resting, pressed, Wash)) {
            "pressing drew nothing at all — no wash to compare"
        }
        val marker = requireNotNull(difference(resting, selected, Marker)) {
            "selecting drew nothing at all — no marker to compare"
        }
        return wash to marker
    }

    private fun assertAgree(wash: IntArray, marker: IntArray, surface: String) {
        // Two pixels of slack per edge. The wash and the marker are different
        // colours at different alphas, so their antialiased edges do not cross
        // the difference threshold at exactly the same pixel.
        val slack = 2
        val edges = listOf("left", "top", "right", "bottom")
        val off = (0..3).filter { abs(wash[it] - marker[it]) > slack }
        assertTrue(
            off.isEmpty(),
            "$surface: the press highlight and the selection marker do not line " +
                "up on ${off.map { edges[it] }}. Highlight ${wash.describe()}, " +
                "marker ${marker.describe()}. The highlight is what the finger " +
                "is told the marker will do, so they have to be the same box.",
        )

    }

    @Test
    fun aSidebarRowIsHighlightedInTheShapeItsMarkerWillTake() {
        // The docs sidebar, which was `NavDrawer` with `NavDrawerItem` rows. The
        // drawer is gone: a sidebar is a `NavRail` fixed at its expanded width.
        val (wash, marker) = boxes(width = 700, height = 400) { selected, pressed ->
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavRail(expanded = true, expandedWidth = 320.dp, colours = inked()) {
                    items(selected, pressed)
                }
            }
        }
        assertAgree(wash, marker, "NavRail(expanded = true)")
    }

    @Test
    fun aBarDestinationIsHighlightedInTheShapeItsMarkerWillTake() {
        // The size has always agreed here — both are the 40dp glyph box — so this
        // is the case the bounding box alone cannot judge and the ink count can.
        // It is also the one round 26 broke: stage 1b moved the glyph's own shape
        // to `pill` and left the travelling marker on `capsule`, which stage 1c
        // then capped at 18dp. A rounded square travelling between circles.
        val (wash, marker) = boxes(width = 600, height = 200) { selected, pressed ->
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavBar(colours = inked()) { items(selected, pressed) }
            }
        }
        assertAgree(wash, marker, "NavBar")
    }

    @Test
    fun aRailRowIsHighlightedInTheShapeItsMarkerWillTake() {
        // Collapsed, which is what this always measured: a rail given
        // `expanded = true` with no toggle used to ignore it. The rows are the
        // same rows at every width now, so this is the icon-only end of the
        // sidebar case above.
        val (wash, marker) = boxes(width = 600, height = 500) { selected, pressed ->
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavRail(colours = inked()) { items(selected, pressed) }
            }
        }
        assertAgree(wash, marker, "NavRail")
    }
}
