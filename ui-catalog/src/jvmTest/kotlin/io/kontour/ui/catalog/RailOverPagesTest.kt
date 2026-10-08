package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Home
import io.kontour.icons.tabler.outline.Star
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.NavigationSuiteType
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A page sliding towards the rail goes under it.
 *
 * Reported: *"if it does the slide animation from one screen to another and
 * both of those screens keep the nav rail/drawer shown, then it does this weird
 * thing where the old screen goes above the nav rail briefly and then cuts out
 * of existence. it should go behind the nav rail/drawer"*. The scaffold lays the
 * rail and the content out in a row, the rail first, so the content drew after
 * it: a page pushed off towards the rail's side — the parallax of the one going
 * as the next comes in — was over the rail until the transition ended and took
 * it away.
 *
 * Drawn rather than asked of the tree, as the defect was in the drawing order:
 * a page offset half its width towards the rail, in a colour nothing in the
 * library uses, must not show anywhere on the rail.
 */
class RailOverPagesTest {

    @Test
    fun aPageMovingTowardsTheRailGoesUnderIt() {
        for (type in listOf(NavigationSuiteType.Rail, NavigationSuiteType.ExpandedRail)) {
            Scene(width = 1200, height = 800) {
                NavigationSuiteScaffold(
                    destinations = {
                        item(true, {}, key = "home", icon = Tabler.Outline.Home) { +"Home" }
                        item(false, {}, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
                    },
                    type = type,
                ) {
                    // The page going, partway through a push: moved towards the
                    // rail, and over its whole box.
                    Box(Modifier.fillMaxSize().offset(x = (-200).dp).background(Page))
                }
            }.use { scene ->
                val frame = scene.frames(30)
                // The rail's full width at the left edge, below its top padding.
                val railWidth = if (type == NavigationSuiteType.Rail) 60 else 200
                var leaked = 0
                for (y in 200 until frame.height step 8) {
                    for (x in 0 until railWidth * 2 step 4) {
                        if ((frame.getRGB(x, y) and 0xFFFFFF) == (Page.toArgb() and 0xFFFFFF)) leaked++
                    }
                }
                assertTrue(leaked == 0, "$type: the page was drawn over the rail at $leaked sampled pixels")
            }
        }
    }

    private companion object {
        /** A colour no surface in the library is. */
        val Page = Color(0xFFFF00FF)
    }
}
