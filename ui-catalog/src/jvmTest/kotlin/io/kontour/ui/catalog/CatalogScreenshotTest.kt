package io.kontour.ui.catalog

import kotlin.test.AfterTest
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

    /**
     * The line this class was missing, and the reason a missing one is invisible.
     *
     * `Screenshot.render` *records* a mismatch and carries on — deliberately, so
     * that a change moving four schemes reports four rather than the first — and
     * `assertAllMatched` is the only thing that turns the record into a failure.
     * Every other class that renders has this method. This one asserted
     * `file.length() > 0` instead, which is true of a golden that matched, a
     * golden that drifted, and a golden written fresh a second ago: the shell
     * goldens could move by any amount and the run stayed green.
     *
     * What made it hard to see is that `mismatches` is a JVM-wide singleton and
     * the suite runs four forks. A drift here did not vanish — it waited, and
     * was reported by whichever *other* class ran next in the same fork, under
     * that class's name, or was dropped entirely when this class ran last.
     * Which of those happened was decided by fork scheduling, so the same defect
     * was a confusing failure on one machine and silence on the next.
     */
    @AfterTest
    fun allGoldensMatched() = Screenshot.assertAllMatched()

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
            Catalog(pinned())
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
            Catalog(pinned())
        }
        assertTrue(file.length() > 0, "catalog-large rendered an empty file")
    }

    /**
     * Settings pinned to light, not left to the host.
     *
     * `Catalog` used to hardcode `dark = false`. Now it follows the operating
     * system unless a reader says otherwise, which is right for an app and wrong
     * for a golden: a screenshot that depends on the machine's appearance
     * setting is not a golden. Same argument as the locale pinning in
     * `ui-catalog/build.gradle.kts`.
     */
    private fun pinned() = CatalogSettings().apply {
        dark = false
        highContrast = false
        reduceMotion = true
    }
}
