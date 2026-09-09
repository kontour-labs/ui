package io.kontour.ui.demo

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.Click
import com.composables.icons.tabler.outline.Components
import com.composables.icons.tabler.outline.Forms
import com.composables.icons.tabler.outline.LayoutBottombar
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.LayoutList
import com.composables.icons.tabler.outline.LayoutSidebar
import com.composables.icons.tabler.outline.SquareRoundedLetterT
import com.composables.icons.tabler.outline.Stack2
import com.composables.icons.tabler.outline.Windmill

/**
 * A family of demos, and the icon the gallery gives it.
 *
 * The grouping already existed — `buildDemos` concatenated eleven per-family
 * lists — and was thrown away at the `associateBy`. Keeping it is what lets the
 * gallery be *generated from* the demos rather than be a second, hand-written
 * list of the same components that has to be remembered.
 *
 * The names match `familyOrder` in `:ui-docs`, checked there rather than here
 * because the dependency runs `:ui-docs` → `:ui-catalog` and this module cannot
 * see that list.
 */
@Immutable
class DemoFamily(
    val name: String,
    val icon: ImageVector,
    val demos: List<ComponentDemo>,
)

/**
 * Every family, in the order the gallery shows them.
 *
 * **This is the anti-staleness mechanism, and it is a structural one rather than
 * a check.** The gallery's pages were a separate list of thirteen that had not
 * changed since August while the library grew by three weeks of components; the
 * fix is not a rule that notices the drift but the removal of the second list.
 * A demo can only be added here, and everything downstream — `componentDemos`,
 * the documentation site's per-page demo, the gallery's pages — derives from it.
 */
// Declared **before** `componentDemos`, which derives from it. Top-level
// properties initialise in declaration order, and the other way round
// `componentDemos` ran against a null list and every demo failed at class-init
// with a `NoClassDefFoundError` — in production, on the first page a reader
// opened. Which is the failure this file's own note about building the map once
// was written about.
val demoFamilies: List<DemoFamily> = listOf(
    DemoFamily("Actions", Tabler.Outline.Click, actionDemos),
    DemoFamily("Selection", Tabler.Outline.Forms, selectionDemos),
    DemoFamily("Text editing", Tabler.Outline.SquareRoundedLetterT, textEditingDemos),
    DemoFamily("Date and time", Tabler.Outline.Calendar, dateTimeDemos),
    DemoFamily("Display", Tabler.Outline.LayoutGrid, displayDemos),
    DemoFamily("Collections", Tabler.Outline.LayoutList, collectionDemos),
    DemoFamily("Overlays", Tabler.Outline.Stack2, overlayDemos),
    DemoFamily("Sheets", Tabler.Outline.LayoutBottombar, sheetDemos),
    DemoFamily("Navigation", Tabler.Outline.Windmill, navigationDemos),
    DemoFamily("Adaptive", Tabler.Outline.LayoutSidebar, adaptiveDemos),
    DemoFamily("Foundation", Tabler.Outline.Components, foundationDemos),
)

/**
 * Every demo, by the page it belongs to.
 *
 * Keyed by slug rather than by symbol name, because the page is the thing a
 * demo is for and a page can be about more than one symbol — `chip.md` covers
 * three. The old specimen block matched `componentRegistry` entries against the
 * backticked words in a page's title, which is why `nav-surfaces` showed none of
 * its five registered specimens and `tab-bar` showed a `Tab` but never a
 * `TabBar`. A slug cannot near-miss.
 *
 * `check-components.py` holds this to a bijection with the pages: a demo whose
 * slug names no page, or a page with a component on it and no demo, fails the
 * build.
 */
val componentDemos: Map<String, ComponentDemo> = buildDemos()

/**
 * Built once, in a function, rather than twice in a property initialiser.
 *
 * The `require` below needs the flat list to spot a duplicate slug, and the
 * property used to concatenate all eleven lists a second time to get it — so
 * every one of the 102 demos and twenty intermediate lists were allocated twice
 * at class-init time, in production, on the first page a reader opened.
 */
private fun buildDemos(): Map<String, ComponentDemo> {
    val all = demoFamilies.flatMap { it.demos }
    val bySlug = all.associateBy { it.slug }

    // A duplicate slug would silently drop one of the two, and the count check
    // in `check-components.py` would then be satisfied by the wrong number.
    // Cheaper to notice here.
    require(all.size == bySlug.size) {
        val duplicates = all.groupBy { it.slug }.filterValues { it.size > 1 }.keys
        "two demos share a slug: ${duplicates.joinToString()}"
    }
    return bySlug
}
