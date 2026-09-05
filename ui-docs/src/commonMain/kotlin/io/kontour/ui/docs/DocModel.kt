package io.kontour.ui.docs

import androidx.compose.runtime.Immutable

/** What a page is, which decides what the site puts around it. */
enum class DocKind {
    /** Prose about the library: tokens, theming, accessibility, the DSLs. */
    Guide,

    /** The index of one family — its "which one" table and the family's own prose. */
    Family,

    /** One component: a live demo, the prose, and a generated parameter table. */
    Component,
}

/**
 * One documentation page, as blocks rather than as markdown.
 *
 * Built by `docs/generate-doc-pages.py` at compile time. The site ships no
 * parser: the reader downloads content, not a program for turning text into
 * content, and a page that stops parsing fails the build rather than the
 * browser.
 *
 * @param path The route — `#/<path>` — and the file it came from, relative to
 *   `ui-docs/content/` and without the suffix. A file *stem* was what this used
 *   to be, and stems are not unique across the tree: `overlays.md` is both the
 *   guide to the overlay mechanism and the family index.
 * @param symbols What the page is about, from its title. Used for the search
 *   and for the link into the API reference.
 * @param family Which index links to it, or `Guides` for a page that is not
 *   about a component at all.
 * @param order Where it sits within [family] — for a component, the position
 *   its index links it at; for a guide, its position in `doctree.GUIDES`. The
 *   family pages themselves carry `-1`, since a family page is drawn beside its
 *   family rather than inside it and its order is never read.
 * @param content Builds this page's blocks, and is not called until something
 *   asks for them. See [blocks].
 */
@Immutable
class DocPage(
    val path: String,
    val title: String,
    val symbols: List<String>,
    val family: String,
    val kind: DocKind,
    val order: Int,
    private val content: () -> List<Block>,
) {
    /**
     * The page's blocks, built on first read and kept.
     *
     * A thunk rather than a value, and it is the difference between a site that
     * starts and one that hangs. `docPages` is a top-level `val`, so the first
     * `remember` in the first composition forces it — and with the blocks built
     * eagerly that meant constructing **every heading, paragraph, span, code
     * block and table of all 122 pages**, 8,806 objects and 3,909 lists, on the
     * main thread, before a single pixel was drawn, to display one page whose
     * mean size is thirteen blocks.
     *
     * The index needs a page's title, family and kind, and none of those need
     * its prose. So the list of pages stays eager and cheap, and the prose
     * arrives when a reader actually opens something.
     *
     * Not thread-safe on purpose: composition is single-threaded, and the worst
     * a race could do here is build the same immutable list twice.
     */
    private val lazyBlocks = lazy(LazyThreadSafetyMode.NONE) { content() }

    val blocks: List<Block> get() = lazyBlocks.value

    /**
     * Whether this page's blocks have been built yet.
     *
     * A field so a test can ask, the way `SheetState.anchorRebuilds` exists so a
     * test can ask how often a sheet rebuilds its anchors. "The whole corpus is
     * constructed before the first frame" was invisible until something counted
     * it, and it will be invisible again the moment nothing does.
     */
    internal val blocksBuilt: Boolean get() = lazyBlocks.isInitialized()

    /** The file stem, which is what a demo is keyed by. */
    val slug: String get() = path.substringAfterLast('/')
}

/** A run of prose with one kind of formatting on it. */
@Immutable
sealed interface Span {
    val text: String

    data class Plain(override val text: String) : Span
    data class Code(override val text: String) : Span

    /**
     * Emphasis nests, because a link inside bold is the commonest thing on
     * these pages: every "**Reach for [`Chip`](chip.md) instead**" is one, and
     * a flat `String` here is why twelve of them rendered as literal markdown.
     */
    data class Strong(val spans: List<Span>) : Span {
        override val text: String get() = spans.joinToString("") { it.text }
    }

    data class Emphasis(val spans: List<Span>) : Span {
        override val text: String get() = spans.joinToString("") { it.text }
    }

    /**
     * @param spans The label, which is itself formatted: 259 of the links in
     *   this tree are `[`Chip`](chip.md)`, and a flat string rendered the
     *   backticks.
     * @param target As written in the markdown — `button.md`, `../tokens.md`,
     *   `https://…`. Resolved to a route or an external link at render time,
     *   because the same page is read on GitHub *and* here and neither form can
     *   be the one stored.
     */
    data class Link(val spans: List<Span>, val target: String) : Span {
        override val text: String get() = spans.joinToString("") { it.text }
    }
}

/** One block of a page. */
@Immutable
sealed interface Block {
    data class Heading(val level: Int, val spans: List<Span>) : Block
    data class Paragraph(val spans: List<Span>) : Block
    /**
     * A fenced block, with its highlighting alongside rather than inside it.
     *
     * [spans] is a run-length map over [code], one entry per run, written as a
     * count and the kind it covers — `"11k1p3k17p"` is eleven keyword
     * characters, one plain, three keyword, seventeen plain. `p` plain, `k`
     * keyword, `s` literal, `c` comment; anything the map does not reach is
     * plain, and an empty string means no highlighting at all, which is what
     * every block that is not Kotlin gets.
     *
     * ### Why an encoding rather than a list of tokens
     *
     * The site ships no parser — the reader downloads content, not a program
     * for turning text into content — so the highlighting is worked out by
     * `docs/generate-doc-pages.py` at build time. That leaves the question of
     * how to carry it, and a `List<CodeToken>` is the obvious answer and the
     * wrong one: `DocPages.kt` is already chunked at thirty blocks per function
     * because of the JVM's 64 KB method limit, and a few thousand constructor
     * calls would push against it for no benefit.
     *
     * A string costs one constant. Measured over the whole corpus it is 5,793
     * characters against 53,435 of code — eleven per cent — and the decoder is
     * a dozen lines that know nothing about Kotlin.
     */
    data class Code(
        val language: String,
        val code: String,
        val spans: String = "",
    ) : Block
    data class Table(val rows: List<List<List<Span>>>) : Block
    data class Quote(val spans: List<Span>) : Block
    data class Bullets(val ordered: Boolean, val items: List<List<Span>>) : Block
    data object Rule : Block
}

/** Every page, by route. */
val docPagesByPath: Map<String, DocPage> = docPages.associateBy { it.path }

/**
 * The families, in the order the documentation's own index puts them.
 *
 * Alphabetical would put Adaptive first and Actions second, which is a list
 * sorted by a fact about spelling. This is the order a reader is led through.
 */
val familyOrder: List<String> = listOf(
    "Guides",
    "Actions",
    "Selection",
    "Text editing",
    "Date and time",
    "Display",
    "Collections",
    "Overlays",
    "Sheets",
    "Navigation",
    "Adaptive",
    "Foundation",
    "Other",
)

/**
 * The index: families in [familyOrder], and within each the pages in [DocPage.order].
 *
 * A family's own page is excluded from its list and carried beside it instead —
 * a section headed "Overlays" whose first entry is also "Overlays" reads as a
 * duplicate rather than as the way in.
 *
 * The order within a family is the family index's own, and the comment above
 * [familyOrder] is why: this sorted by `title` — the *raw markdown*, backticks
 * and all — so `` `ButtonGroup` `` beat `` `Button` `` on the strength of a
 * closing backtick sorting after `G`, and `NavigationSuiteScaffold`, which
 * `navigation.md` introduces with **Start here.**, arrived fifth. Sorting by a
 * better string would have been the same mistake spelled differently. The index
 * pages are already written in the order a reader should meet them in.
 */
val docPagesByFamily: List<DocFamily> =
    familyOrder.mapNotNull { family ->
        val pages = docPages.filter { it.family == family }
        val index = pages.firstOrNull { it.kind == DocKind.Family }
        val rest = pages.filter { it.kind != DocKind.Family }.sortedBy { it.order }
        if (rest.isEmpty()) null else DocFamily(family, index, rest)
    }

/** One group in the site index. */
@Immutable
class DocFamily(val name: String, val index: DocPage?, val pages: List<DocPage>)
