package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.kontour.ui.overlay.OverlayHost
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A page you come back to is where you left it.
 *
 * The gallery composed `pages[selected].content` outright, so a destination was
 * built cold on arrival and thrown away on departure: every return to a page
 * started at the top of a list the reader had scrolled halfway down. On pages
 * that are twenty demos long, which is most of them, that is the whole page's
 * worth of scrolling to get back to where you were looking.
 *
 * Driven through [CatalogDestination] rather than by tapping a navigation item,
 * because the swap is the claim and a rail item's coordinates are not: the same
 * function is what both layouts compose, so a test that reaches it has tested
 * the rail and the drawer at once.
 *
 * **What this does not claim.** Arriving is no cheaper —
 * `DrawerSelectCostDiagnostic` puts a destination's first frame at 64.2ms and
 * this does not skip a line of that composition. Nor do a page's *knobs* come
 * back: `rememberKnobs` keeps `mutableStateOf<Any?>`, and a value of type `Any?`
 * has no saver. What comes back is what a `rememberSaveable` holds, which is
 * every scroller in the gallery.
 */
class PageStateTest {

    @Test
    fun aRevisitedPageIsWhereItWasLeft() {
        var selected by mutableIntStateOf(0)

        Scene(width = 420, height = 800) {
            val pageState = rememberSaveableStateHolder()
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    CatalogDestination(
                        selected = selected,
                        pageState = pageState,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }.use { scene ->
            val top = scene.frames(6)

            // A wheel rather than a drag: the About page is a column of text and
            // cards, and a drag through it would find something to press.
            repeat(6) { scene.scroll(Offset(210f, 400f), Offset(0f, 8f)) }
            val scrolled = scene.frames(8)
            assertTrue(
                !matches(top, scrolled),
                "the page did not move under the wheel, so this test cannot tell " +
                    "a page that came back scrolled from one that came back at the top",
            )

            selected = 1
            scene.frames(8)
            selected = 0
            val returned = scene.frames(8)

            assertTrue(
                matches(scrolled, returned),
                "the page came back at the top. Its scroller was composed fresh, " +
                    "which is what happens without a `SaveableStateHolder` keyed " +
                    "on the destination",
            )
        }
    }

    private fun matches(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) return false
            }
        }
        return true
    }
}
