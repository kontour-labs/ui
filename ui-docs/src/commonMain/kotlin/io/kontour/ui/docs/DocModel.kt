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
 */
@Immutable
class DocPage(
    val path: String,
    val title: String,
    val symbols: List<String>,
    val family: String,
    val kind: DocKind,
    val blocks: List<Block>,
) {
    /** The file stem, which is what a demo is keyed by. */
    val slug: String get() = path.substringAfterLast('/')
}

/** A run of prose with one kind of formatting on it. */
@Immutable
sealed interface Span {
    val text: String

    data class Plain(override val text: String) : Span
    data class Code(override val text: String) : Span
    data class Strong(override val text: String) : Span
    data class Emphasis(override val text: String) : Span

    /**
     * @param target As written in the markdown — `button.md`, `../tokens.md`,
     *   `https://…`. Resolved to a route or an external link at render time,
     *   because the same page is read on GitHub *and* here and neither form can
     *   be the one stored.
     */
    data class Link(override val text: String, val target: String) : Span
}

/** One block of a page. */
@Immutable
sealed interface Block {
    data class Heading(val level: Int, val spans: List<Span>) : Block
    data class Paragraph(val spans: List<Span>) : Block
    data class Code(val language: String, val code: String) : Block
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
 * The index: families in [familyOrder], and within each the pages by title.
 *
 * A family's own page is excluded from its list and carried beside it instead —
 * a section headed "Overlays" whose first entry is also "Overlays" reads as a
 * duplicate rather than as the way in.
 */
val docPagesByFamily: List<DocFamily> =
    familyOrder.mapNotNull { family ->
        val pages = docPages.filter { it.family == family }
        val index = pages.firstOrNull { it.kind == DocKind.Family }
        val rest = pages.filter { it.kind != DocKind.Family }.sortedBy { it.title }
        if (rest.isEmpty()) null else DocFamily(family, index, rest)
    }

/** One group in the site index. */
@Immutable
class DocFamily(val name: String, val index: DocPage?, val pages: List<DocPage>)
