package io.kontour.ui.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opening a page builds a page, not a library.
 *
 * `docPages` is a top-level `val`, and the first `remember` in the first
 * composition forces it — `Site` asks `rememberRoute`, which asks
 * `parseRoute`, which asks whether the path is one this site has. With the
 * blocks built eagerly that single question constructed **every heading,
 * paragraph, span, code block and table of all 122 pages**: 8,806 objects and
 * 3,909 lists, on the main thread, before a pixel was drawn, to show one page
 * whose mean size is thirteen blocks.
 *
 * The index needs a title, a family and a kind. None of those needs the prose.
 *
 * ### Deltas, not totals
 *
 * `docPages` is a singleton and these tests share a JVM with the render sweep,
 * which opens every page there is. A test that asserted "one page is built"
 * would pass or fail on the order the runner happened to pick. Every assertion
 * here measures a *change* across an operation instead.
 *
 * That costs something worth naming: a delta cannot catch eagerness itself,
 * because if everything is already built every delta is zero. So laziness is
 * guarded by [blocksAreBuiltOnceOnFirstRead], which builds its own page and owes
 * nothing to the singleton, and the deltas guard the other failure — an index or
 * a route lookup that starts reading prose it does not need.
 */
class CorpusLazinessTest {

    private fun built() = docPages.count { it.blocksBuilt }

    @Test
    fun blocksAreBuiltOnceOnFirstRead() {
        var builds = 0
        val page = DocPage(
            path = "test/page",
            title = "Test",
            symbols = emptyList(),
            family = "Guides",
            kind = DocKind.Guide,
            // Spelled out rather than defaulted on the constructor. The only
            // other caller is the generator, and a default would let a real page
            // silently take position zero the day it stopped emitting one.
            order = 0,
            summary = "A page for a test to hold.",
        ) {
            builds++
            emptyList()
        }

        assertEquals(0, builds, "constructing a page must not build its blocks")
        page.blocks
        assertEquals(1, builds, "reading the blocks must build them")
        page.blocks
        page.blocks
        assertEquals(1, builds, "and must then keep them")
    }

    @Test
    fun askingWhetherARouteExistsBuildsNothing() {
        val before = built()
        assertTrue("components/button" in docPagesByPath)
        assertTrue("nonsense/nothing" !in docPagesByPath)
        assertEquals(
            before,
            built(),
            "resolving a route read some page's prose, which is the whole corpus " +
                "waiting to happen — it needs the path, and nothing else",
        )
    }

    @Test
    fun theIndexBuildsNothing() {
        val before = built()
        val families = docPagesByFamily
        val titles = families.flatMap { family -> family.pages.map { it.title } }

        assertTrue(families.isNotEmpty() && titles.isNotEmpty())
        assertEquals(
            before,
            built(),
            "building the site index read page prose. The index shows titles.",
        )
    }

    /**
     * The landing page shows seven guides. It must not read seven guides.
     *
     * `Home` lists the guides with `supporting = page.summary`, and `summary`
     * was derived from the page's first paragraph — so displaying the index
     * built the prose of every guide on it, on the first frame, which is the
     * exact work `DocPage.blocks` exists to defer and the reason it exists.
     *
     * Not a delta test. The others here measure a change because `docPages` is a
     * singleton this file shares with the render sweep, and a delta cannot catch
     * eagerness once everything is already built. This owns its page, counts its
     * own builds, and is therefore true whatever order the runner picks — the
     * same shape as [blocksAreBuiltOnceOnFirstRead], and for the same reason.
     */
    @Test
    fun theLandingPageSummaryBuildsNothing() {
        var builds = 0
        val page = DocPage(
            path = "guides/tokens",
            title = "Tokens",
            symbols = emptyList(),
            family = "guides",
            kind = DocKind.Guide,
            order = 0,
            summary = "Every colour, size and duration the library draws with.",
            content = {
                builds++
                listOf(Block.Paragraph(listOf(Span.Plain("Every colour, size and duration."))))
            },
        )

        assertTrue(page.shortSummary.isNotEmpty(), "the summary came out empty")
        assertEquals(
            0,
            builds,
            "reading a page's summary built its blocks. The landing page shows one " +
                "for every guide, so that is seven pages of prose constructed to draw " +
                "seven lines of it — which is what `DocPage.blocks` was made lazy to stop.",
        )
    }

    /**
     * The generated summary is what the blocks say, for every page.
     *
     * The extraction lives in `docs/generate-doc-pages.py` now, and its `plain`
     * is a hand-written mirror of the same file's `spans` — code keeps its
     * contents, a link keeps its label, an image is dropped, emphasis unwraps.
     * Two implementations of one rule is exactly the "summary written twice"
     * that deriving it was meant to avoid, so this is the thing that stops them
     * drifting: the Python answer has to equal the Kotlin one on all 121 pages.
     *
     * This builds the whole corpus, deliberately and in a test, which is the one
     * place that is free.
     */
    @Test
    fun everyGeneratedSummaryMatchesItsProse() {
        val wrong = docPages.mapNotNull { page ->
            val fromBlocks = page.blocks.asSequence()
                .filterIsInstance<Block.Paragraph>()
                .map { paragraph -> paragraph.spans.joinToString("") { it.text } }
                .firstOrNull { !it.startsWith("Also on this page") }
                .orEmpty()
            if (page.summary == fromBlocks) null else "${page.path}\n  generated: ${page.summary}\n  prose:     $fromBlocks"
        }

        assertTrue(
            wrong.isEmpty(),
            "${wrong.size} of ${docPages.size} pages have a summary that is not their " +
                "opening paragraph. `summary_of`/`plain` in docs/generate-doc-pages.py " +
                "and `spans` in the same file have to agree, and one of them has been " +
                "changed without the other:\n\n" + wrong.take(5).joinToString("\n"),
        )
    }

    @Test
    fun openingOnePageBuildsOnePage() {
        val page = docPagesByPath.getValue("components/switch")
        val before = built()
        page.blocks
        val delta = built() - before

        assertTrue(
            delta <= 1,
            "opening one page built $delta pages' worth of blocks",
        )
        assertTrue(page.blocks.isNotEmpty(), "the page came out empty")
    }
}
