package io.kontour.ui.docs

import androidx.compose.runtime.Immutable

/**
 * One documentation page, as blocks rather than as markdown.
 *
 * Built by `docs/generate-doc-pages.py` at compile time. The site ships no
 * parser: the reader downloads content, not a program for turning text into
 * content, and a page that stops parsing fails the build rather than the
 * browser.
 *
 * @param slug The route — `#/components/<slug>` — and the file it came from.
 * @param symbols What the page is about, from its title. Used for the search
 *   and for the link into the API reference.
 * @param family Which category index links to it.
 */
@Immutable
class DocPage(
    val slug: String,
    val title: String,
    val symbols: List<String>,
    val family: String,
    val blocks: List<Block>,
)

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

/** Every page, by slug. */
val docPagesBySlug: Map<String, DocPage> = docPages.associateBy { it.slug }

/**
 * The families, in the order the documentation's own index puts them.
 *
 * Alphabetical would put Adaptive first and Actions second, which is a list
 * sorted by a fact about spelling. This is the order a reader is led through.
 */
val familyOrder: List<String> = listOf(
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

/** Pages grouped by family, families in [familyOrder], pages by title within. */
val docPagesByFamily: List<Pair<String, List<DocPage>>> =
    familyOrder.mapNotNull { family ->
        val pages = docPages.filter { it.family == family }.sortedBy { it.title }
        if (pages.isEmpty()) null else family to pages
    }
