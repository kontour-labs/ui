package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavSearch
import io.kontour.ui.nav.LocalNavExpansion
import io.kontour.ui.nav.NavExpandPlacement
import io.kontour.ui.nav.NavExpansion
import io.kontour.ui.nav.NavSearchState
import io.kontour.ui.nav.rememberNavSearchState
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A [NavSearch] collapsed in the bar, and expanded over everything.
 *
 * The bar has always had a `search` slot, and `searchIndex` has always been able
 * to put it between two destinations. What is new is that tapping it opens
 * something, and *where* that something goes — which is the one decision this
 * component defers to its caller, so it is the one worth pinning.
 *
 * ### Measured by which half of the screen the field is in
 *
 * Not by a screenshot: a still of the expanded state would be identical for the
 * two placements but for a y coordinate, and identical to a broken one that put
 * the field in the middle. The question is only ever "top or bottom", so the
 * measurement is the field's own ink, and the assertion is which half it lands
 * in.
 *
 * The scrim gives this for free. An expanded search dims what is behind it, so
 * the field is the one bright thing on a dark page and finding it is a matter of
 * looking for pixels that are not dimmed.
 */
@OptIn(ExperimentalTestApi::class)
class NavSearchTest {

    private val items = listOf(
        NavItem(label = "Nearby", icon = Tabler.Outline.MapPin, onClick = {}),
        NavItem(label = "Routes", icon = Tabler.Outline.Bus, onClick = {}),
    )

    @Test
    fun theCollapsedPillOpensOverTheKeyboardOrAtTheTop() {
        val overKeyboard = fieldCentre(NavExpandPlacement.AboveKeyboard)
        val atTop = fieldCentre(NavExpandPlacement.Top)

        assertTrue(
            overKeyboard > Height / 2,
            "an AboveKeyboard search put its field at y=${overKeyboard}px of " +
                "$Height — it belongs in the bottom half, where the thumb that " +
                "opened it is",
        )
        assertTrue(
            atTop < Height / 2,
            "a Top search put its field at y=${atTop}px of $Height — it belongs " +
                "in the top half, with the results reading downwards from it",
        )
    }

    /**
     * The collapsed control has an edge against whatever it is standing on.
     *
     * It was `surfaceSunken` on both surfaces at first, which is the right
     * ground for a text field in a form and the wrong one here twice over: in a
     * bar the pill floats over the content beside destinations that are raised
     * white circles, and in a rail there is a `surface` panel behind it and a
     * *sunken* well is the one thing that reads. The colour follows
     * `LocalNavExpansion.onSurface` now.
     *
     * ### Measured as the pill's own ground, not as ink
     *
     * The first version of this counted every pixel inside the control that
     * differed from the page, and **passed with the colour pinned to the wrong
     * one** — a magnifier, a word and a drop shadow clear any reasonable
     * threshold on their own, whatever the ground under them is doing. So it
     * reads the commonest colour strictly *inside* the control's bounds, which
     * is the fill and nothing else, and compares that.
     */
    @Test
    fun theCollapsedControlIsNotTheColourOfWhatItStandsOn() {
        for (onSurface in listOf(false, true)) {
            val (ground, page) = pillGroundAndPage(onSurface)
            assertTrue(
                ground != page,
                "a control on ${if (onSurface) "a rail's panel" else "a bar's page"} " +
                    "is filled with the same colour as the page under it " +
                    "(${ground.toString(16)}) — it has no edge at all",
            )
        }
    }

    /**
     * In a rail, the search grows with the rail rather than opening over it.
     *
     * The same line of code at the call site — `header = { NavSearch(state) }` —
     * is a 48dp pill at 88dp of rail and a full field at 280dp, because
     * `NavSearch` reads [io.kontour.ui.nav.LocalNavExpansion] rather than being
     * told. That is the generic half of this: any slot content can ask the same
     * question and get the same answer, and a profile row that gains a name or a
     * button that drops its label is the same mechanism with different content
     * in it.
     */
    @Test
    fun aRailsSearchGrowsWithTheRail() {
        val narrow = searchInRail(expanded = false)
        val wide = searchInRail(expanded = true)

        // Most of the rail, not merely more than before. The first version of
        // this asked for twice the collapsed width and **passed with the field
        // turned off**: a pill that gains the word "Search" when there is room
        // for it is already more than twice a pill that is only a magnifier.
        assertTrue(
            wide.width > wide.rail * FillsTheRail,
            "the search is ${wide.width}dp wide in a ${wide.rail}dp rail — an " +
                "in-place field fills what it is in, so this is still the pill",
        )
        assertTrue(
            wide.editable,
            "an expanded rail's search has nothing to type in — it is showing " +
                "the pill where it has room for the field",
        )

        // Counted rather than measured, because width does not separate these:
        // a 48dp pill is already more than half of an 88dp rail, and a field
        // squeezed into the same rail is 60. Whether there is somewhere to type
        // is the actual question, and it has a yes or a no.
        assertTrue(
            !narrow.editable,
            "a collapsed rail's search is an editable field in ${narrow.rail}dp " +
                "— there is nowhere to type in one that narrow, which is what the " +
                "panel exists for",
        )
    }

    /** What a [NavSearch] came out as in a rail at one of its two widths. */
    private class InRail(val width: Int, val rail: Int, val editable: Boolean)

    private fun searchInRail(expanded: Boolean): InRail {
        var width = 0
        var rail = 0
        var editable = false

        runComposeUiTest {
            setContent {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        NavRail(
                            items = items,
                            selectedIndex = 0,
                            modifier = Modifier.testTag("rail"),
                            expanded = expanded,
                            onExpandedChange = {},
                            header = {
                                val search = rememberNavSearchState()
                                NavSearch(
                                    state = search,
                                    modifier = Modifier.testTag("search"),
                                    placeholder = "Search",
                                )
                            },
                        )
                    }
                }
            }
            // `reduceMotion` collapses the width spring to a fast tween and the
            // test clock is idle by the time the tree is queried, so these are
            // resting widths rather than a frame of the animation.
            width = onNodeWithTag("search").fetchSemanticsNode().boundsInRoot.width.toInt()
            rail = onNodeWithTag("rail").fetchSemanticsNode().boundsInRoot.width.toInt()
            editable = onAllNodes(hasSetTextAction(), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        return InRail(width, rail, editable)
    }

    /** The control's fill, and the colour of the page it stands on. */
    private fun pillGroundAndPage(onSurface: Boolean): Pair<Int, Int> {
        var ground = 0
        var page = 0

        runComposeUiTest {
            setContent {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    val behind = if (onSurface) {
                        Theme.colors.surface
                    } else {
                        Theme.colors.surfaceSunken
                    }
                    Box(Modifier.fillMaxSize().background(behind).testTag("page")) {
                        CompositionLocalProvider(
                            LocalNavExpansion provides NavExpansion(
                                expanded = false,
                                progress = 1f,
                                onSurface = onSurface,
                            )
                        ) {
                            val search = rememberNavSearchState()
                            NavSearch(
                                state = search,
                                modifier = Modifier.testTag("pill"),
                                placeholder = "Search",
                            )
                        }
                    }
                }
            }

            val bounds = onNodeWithTag("pill").fetchSemanticsNode().boundsInRoot
            val image = onNodeWithTag("page").captureToImage().toPixelMap()

            page = image[2, image.height - 3].toArgb() and 0xFFFFFF

            // Inset, because the outermost ring of a rounded pill is its own
            // antialiased edge blending into whatever is behind it.
            val counts = HashMap<Int, Int>()
            val inset = 6
            for (y in (bounds.top.toInt() + inset) until (bounds.bottom.toInt() - inset)) {
                for (x in (bounds.left.toInt() + inset) until (bounds.right.toInt() - inset)) {
                    if (x !in 0 until image.width || y !in 0 until image.height) continue
                    val rgb = image[x, y].toArgb() and 0xFFFFFF
                    counts[rgb] = (counts[rgb] ?: 0) + 1
                }
            }
            ground = counts.maxByOrNull { it.value }?.key ?: page
        }

        return ground to page
    }

    /**
     * Where the expanded field's ink sits, having tapped the pill open.
     *
     * The pill is found by its content description rather than by position,
     * because [NavBar] decides where in the row it goes and this test is not
     * about that — `NavBarLayoutTest.searchCanSitBetweenTwoItems` is.
     */
    private fun fieldCentre(placement: NavExpandPlacement): Int {
        var state: NavSearchState? = null

        Scene(width = Width, height = Height) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Search(placement) { state = it }
                }
            }
        }.use { scene ->
            scene.frames(6)
            requireNotNull(state).expand()
            val expanded = scene.frames(30)
            return expanded.brightestBand()
        }
    }

    @Composable
    private fun Search(placement: NavExpandPlacement, onState: (NavSearchState) -> Unit) {
        val search = rememberNavSearchState()
        onState(search)
        var selected by mutableStateOf(0)
        NavBar(
            items = items,
            selectedIndex = selected,
            searchIndex = 1,
            search = { NavSearch(state = search, placement = placement) },
        )
    }

    /**
     * The y of the widest run of undimmed pixels.
     *
     * The expanded field is a light pill on a scrimmed page, so the row with the
     * most near-white pixels is the row through the middle of it. A single
     * brightest *pixel* would find an antialiased edge anywhere; a whole row of
     * them is a field.
     */
    private fun BufferedImage.brightestBand(): Int {
        var best = 0
        var bestRow = 0
        for (y in 0 until height) {
            var bright = 0
            for (x in 0 until width) {
                val rgb = getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                if (r > 230 && g > 230 && b > 230) bright++
            }
            if (bright > best) {
                best = bright
                bestRow = y
            }
        }
        return bestRow
    }

    private companion object {
        /**
         * The share of the rail an in-place field takes up.
         *
         * Two paddings each side leave a field 252 of an expanded rail's 280dp;
         * the pill in the same rail is 71. Anywhere between separates them —
         * which is true at 280dp and, as the collapsed case found out, not at 88.
         */
        const val FillsTheRail = 0.7

        const val Width = 720
        const val Height = 1200

    }
}
