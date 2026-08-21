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
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavSearch
import io.kontour.ui.nav.NavSearchPlacement
import io.kontour.ui.nav.NavSearchState
import io.kontour.ui.nav.rememberNavSearchState
import io.kontour.ui.overlay.OverlayHost
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
class NavSearchTest {

    private val items = listOf(
        NavItem(label = "Nearby", icon = Tabler.Outline.MapPin, onClick = {}),
        NavItem(label = "Routes", icon = Tabler.Outline.Bus, onClick = {}),
    )

    @Test
    fun theCollapsedPillOpensOverTheKeyboardOrAtTheTop() {
        val overKeyboard = fieldCentre(NavSearchPlacement.AboveKeyboard)
        val atTop = fieldCentre(NavSearchPlacement.Top)

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
     * Where the expanded field's ink sits, having tapped the pill open.
     *
     * The pill is found by its content description rather than by position,
     * because [NavBar] decides where in the row it goes and this test is not
     * about that — `NavBarLayoutTest.searchCanSitBetweenTwoItems` is.
     */
    private fun fieldCentre(placement: NavSearchPlacement): Int {
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
    private fun Search(placement: NavSearchPlacement, onState: (NavSearchState) -> Unit) {
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
        const val Width = 720
        const val Height = 1200
    }
}
