package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.foundation.Surface
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavBarStyle
import io.kontour.ui.nav.NavItem
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * All three nav bar styles, at a width that spans and a width that does not.
 *
 * Item 28, and it needs two pictures because it is one claim about two ends of
 * the size range: *"on narrow windows all three should span the width, less
 * padding, with items evenly spaced; on wide windows they should not fill."*
 *
 * ### Why this is a new golden rather than a registry entry
 *
 * `NavBar` was not in `componentRegistry` at all — only `NavBarItem` was — so
 * the whole component's only coverage was the `nav-*` page sheets, which render
 * at one width. A per-component specimen would not have helped either: that
 * canvas is 300dp wide, which is compact, so it can only ever draw the spanning
 * half of the answer.
 *
 * ### What to look for
 *
 * In the **compact** frame, all three bars use the full width and their three
 * destinations are evenly spaced across it. In the **expanded** frame none of
 * them fills: the destinations sit together in the middle, and `Floating`'s pill
 * is only as wide as the items inside it rather than stretched edge to edge.
 *
 * A `Floating` pill that reaches both margins in the expanded frame is the
 * defect this exists to catch — that is a docked bar with a gap under it, and
 * the shape is the whole point of the style.
 */
class NavBarWidthScreenshotTest {

    @AfterTest
    fun allGoldensMatched() = Screenshot.assertAllMatched()

    @Test
    fun spansOnACompactWindow() {
        // 400dp at this density: a phone, and below the 600dp line the rest of
        // the library uses to decide there is no room for a rail beside content.
        val file = Screenshot.render(name = "navbar-compact", width = 800, height = 560) {
            KontourTheme(darkTheme = false) { ThreeStyles() }
        }
        assertTrue(file.length() > 0, "navbar-compact rendered an empty file")
    }

    @Test
    fun doesNotFillAnExpandedWindow() {
        // 1000dp: comfortably past the point where spreading three circles stops
        // reading as one bar.
        val file = Screenshot.render(name = "navbar-expanded", width = 2000, height = 560) {
            KontourTheme(darkTheme = false) { ThreeStyles() }
        }
        assertTrue(file.length() > 0, "navbar-expanded rendered an empty file")
    }

    /**
     * The same three destinations in each style, so the frames differ only in
     * the thing being compared.
     */
    @Composable
    private fun ThreeStyles() {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Measured from the canvas, so the width class is the real one
            // rather than the compact default a missing provider would give.
            WindowSizeClassProvider(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Theme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
                ) {
                    for (style in NavBarStyle.entries) {
                        NavBar(
                            items = listOf(
                                NavItem("Nearby", Tabler.Outline.MapPin, onClick = {}),
                                NavItem("Routes", Tabler.Outline.Bus, onClick = {}),
                                NavItem("Saved", Tabler.Outline.Star, onClick = {}),
                            ),
                            selectedIndex = 0,
                            style = style,
                        )
                    }
                }
            }
        }
    }
}
