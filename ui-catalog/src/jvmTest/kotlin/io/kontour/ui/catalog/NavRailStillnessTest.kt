package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavDrawer
import io.kontour.ui.nav.NavRail
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The icons hold still while the rail grows around them.
 *
 * This is the complaint the rail's own documentation already claimed to have
 * answered — *"keeping the icon still and sliding the label out from behind
 * it"* — and did not. Three things moved, at three different moments of one
 * expansion:
 *
 * - **On frame 0**, the destination swapped a `Column` for a `Row`. The flag
 *   driving that is `expanded`, the target, even though the value computed
 *   beside it to delay the swap until there was room was never read. The item
 *   gained the inline layout's padding, and the 56dp glyph box was squeezed to
 *   whatever fitted an 88dp rail, so the box changed size under the icon.
 * - **At the halfway frame**, the rail's own `horizontalAlignment` flipped from
 *   centred to leading, teleporting the chevron, the header and the action from
 *   the middle of the rail to its edge.
 * - **Between the two resting states**, the icon's centre sat at 44dp collapsed
 *   and 48dp expanded, because those are different arrangements of different
 *   boxes that were never asked to agree.
 *
 * The selection pill reproduced every one of them faithfully: a rect change on
 * the *same* item takes `snapTo` rather than a spring, which is right for
 * tracking a smooth widening and offers no cover for a layout that jumps.
 *
 * ### Measured from the selected icon's leading edge
 *
 * Not its centroid, which the label drags rightwards the moment it appears, and
 * not the item's bounds, which are `fillMaxWidth` and would sit still through
 * any amount of movement inside them.
 *
 * Found by colour rather than by position. The first attempt took the leftmost
 * dark pixel below a fixed line meant to clear the expand toggle, and on two
 * frames of the swap the items moved far enough that no icon was in that band
 * at all — so it measured a *label*, reported an 88dp jump where the real one is
 * 4dp, and would have gone on reporting one after the fix. Only the selected
 * destination is drawn in the accent, its icon leads its label, and the toggle
 * is not accent-coloured: the leftmost accent pixel is that icon's leading edge
 * on every frame, with no band to get wrong.
 *
 * The page behind the rail is magenta so the rail's white surface has an edge,
 * and the indicator is transparent so the accent pill is not counted as ink —
 * both for the same reasons as [NavRailGrowthTest], which measures the other
 * half of this: that the labels do not arrive before there is room for them.
 */
class NavRailStillnessTest {

    private val items = listOf(
        NavItem(label = "Nearby", icon = Tabler.Outline.MapPin, onClick = {}),
        NavItem(label = "Routes", icon = Tabler.Outline.Bus, onClick = {}),
        NavItem(label = "Saved", icon = Tabler.Outline.Star, onClick = {}),
    )

    @Test
    fun theIconsHoldStillWhileTheRailGrows() {
        var expanded by mutableStateOf(false)
        val leadingEdges = mutableListOf<Int>()

        Scene(width = 800, height = 700) {
            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                NavRail(
                    items = items,
                    selectedIndex = 0,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    indicatorColor = Color.Transparent,
                )
            }
        }.use { scene ->
            leadingEdges += scene.frames(8).iconLeadingEdge()

            expanded = true
            repeat(Frames) { leadingEdges += scene.frame().iconLeadingEdge() }

            // And back, because a collapse is an expansion the other way round
            // and has its own frame on which things could flip.
            expanded = false
            repeat(Frames) { leadingEdges += scene.frame().iconLeadingEdge() }
        }

        val found = leadingEdges.filter { it >= 0 }
        assertTrue(
            found.size == leadingEdges.size,
            "${leadingEdges.count { it < 0 }} of ${leadingEdges.size} frames drew " +
                "nothing in the accent — the measurement is looking for the " +
                "wrong thing",
        )

        val travel = found.max() - found.min()
        assertTrue(
            travel <= Tolerance,
            "the icons move ${travel / Density}dp during the expansion — from " +
                "${found.min() / Density}dp to ${found.max() / Density}dp from the " +
                "rail's leading edge. They should not move at all: the rail grows " +
                "around them and the labels are revealed by the growing.",
        )
    }

    /**
     * A drawer puts its icons where the rail it replaces puts them.
     *
     * `NavigationSuiteScaffold` swaps one composable for the other at 840dp,
     * destroying one subtree and building the other — there is no animation to
     * hide a discrepancy behind, so the only thing that can make the swap
     * unremarkable is the two surfaces agreeing. `NavRailDefaults.ExpandedWidth`
     * already equals `NavDrawerDefaults.Width` for exactly this reason, and its
     * own doc says so: *"lining the two up is what stops the switch between them
     * reading as a jump."* The icon's x was the part nobody had lined up — 44dp
     * in a rail against 40dp in a drawer.
     */
    @Test
    fun aDrawerPutsItsIconsWhereTheRailDoes() {
        var rail = -1
        var drawer = -1

        Scene(width = 800, height = 700) {
            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                NavRail(
                    items = items,
                    selectedIndex = 0,
                    expanded = true,
                    onExpandedChange = {},
                    indicatorColor = Color.Transparent,
                )
            }
        }.use { scene -> rail = scene.frames(30).iconLeadingEdge() }

        Scene(width = 800, height = 700) {
            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                NavDrawer(indicatorColor = Color.Transparent) {
                    items.forEachIndexed { index, destination ->
                        item(
                            label = destination.label,
                            icon = destination.icon,
                            selected = index == 0,
                            onClick = destination.onClick,
                        )
                    }
                }
            }
        }.use { scene -> drawer = scene.frames(30).iconLeadingEdge() }

        assertTrue(rail >= 0 && drawer >= 0, "rail=$rail drawer=$drawer — one of " +
            "them drew nothing in the accent")
        assertTrue(
            kotlin.math.abs(rail - drawer) <= Tolerance,
            "the rail's selected icon starts at ${rail / Density}dp and the " +
                "drawer's at ${drawer / Density}dp. They are the two surfaces the " +
                "window size class swaps between, and the swap is not animated.",
        )
    }

    /** How far in from the window's edge the selected icon's first pixel is. */
    private fun BufferedImage.iconLeadingEdge(): Int {
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (isAccent(getRGB(x, y))) return x
            }
        }
        return -1
    }

    /**
     * Whether this pixel is the accent the selected destination is drawn in.
     *
     * Blue, and decisively so. The magenta page is as blue as it is red and
     * every other glyph on the rail is a grey, so nothing else here comes close.
     */
    private fun isAccent(rgb: Int): Boolean {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return b > 120 && b - r > 60 && b - g > 60
    }

    private companion object {
        const val Density = 2

        /** Long enough for `springGentle` to settle at 88dp or 280dp. */
        const val Frames = 60

        /** Antialiasing on a glyph's leading edge, and nothing more. */
        const val Tolerance = 2
    }
}
