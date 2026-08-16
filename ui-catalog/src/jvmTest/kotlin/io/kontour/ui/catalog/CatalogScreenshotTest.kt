package io.kontour.ui.catalog

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The gallery shell itself, at the two widths that pick different navigation.
 *
 * The per-area goldens render a showcase on a bare canvas, so none of them
 * exercise the shell: the scaffold, the navigation the window size picks, and a
 * showcase scrolling underneath it. This is the only golden where those three
 * meet, which makes it the one that catches a bar drawn over the content it is
 * meant to float above.
 *
 * 760px at 2× is 380dp — compact, so a bottom bar. 2400px is 1200dp — large, so
 * a drawer down the left. The two together are the placement guarantee in
 * `NavigationSuiteScaffold` made visible.
 */
class CatalogScreenshotTest {

    @Test
    fun rendersCompactWithABottomBar() {
        // The one page that is *meant* to overflow: this renders the whole app
        // shell, and its content scrolls.
        val file = Screenshot.render(
            name = "catalog-compact",
            width = 760,
            height = 1500,
            allowOverflow = true,
        ) {
            Catalog()
        }
        assertTrue(file.length() > 0, "catalog-compact rendered an empty file")
    }

    @Test
    fun rendersLargeWithADrawer() {
        val file = Screenshot.render(
            name = "catalog-large",
            width = 2400,
            height = 1500,
            allowOverflow = true,
        ) {
            Catalog()
        }
        assertTrue(file.length() > 0, "catalog-large rendered an empty file")
    }
}
