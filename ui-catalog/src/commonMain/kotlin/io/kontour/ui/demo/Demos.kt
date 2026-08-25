package io.kontour.ui.demo

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
val componentDemos: Map<String, ComponentDemo> =
    (actionDemos + selectionDemos + textEditingDemos + dateTimeDemos + displayDemos + collectionDemos + navigationDemos + adaptiveDemos + overlayDemos + sheetDemos + foundationDemos)
        .associateBy { it.slug }
        .also { demos ->
            // A duplicate slug would silently drop one of the two, and the
            // count check in `check-components.py` would then be satisfied by
            // the wrong number. Cheaper to notice here.
            val all = actionDemos + selectionDemos + textEditingDemos + dateTimeDemos + displayDemos + collectionDemos + navigationDemos + adaptiveDemos + overlayDemos + sheetDemos + foundationDemos
            require(all.size == demos.size) {
                val duplicates = all.groupBy { it.slug }.filterValues { it.size > 1 }.keys
                "two demos share a slug: ${duplicates.joinToString()}"
            }
        }
