package io.kontour.ui.docs

/** Where the reader is. */
sealed interface Route {
    /** The landing page — an index of everything, which is not a markdown file. */
    data object Home : Route

    /** The component gallery, which is `:ui-catalog` rather than a page. */
    data object Gallery : Route

    /**
     * One page of `ui-docs/content/`, by its path without the extension:
     * `components/button`, `tokens`, `components`.
     *
     * One case where there were two. `Component(slug)` could only name a page
     * under `components/`, so `tokens.md`, `theming.md`, `accessibility.md`,
     * `dsls.md`, `installing.md`, `overlays.md` and `sheets.md` — 2,084 lines,
     * most of the reasoning in the documentation — had no route at all, and
     * every cross-reference to them threw the reader out to raw markdown on
     * GitHub mid-sentence.
     *
     * A stem was also not unique once those pages existed beside the component
     * ones: `overlays.md` is both the guide to the overlay mechanism and the
     * family index. The path is unique by construction.
     */
    data class Doc(val path: String) : Route

    val hash: String
        get() = when (this) {
            Home -> "#/"
            Gallery -> "#/gallery"
            is Doc -> "#/$path"
        }
}

/**
 * Routing through the URL fragment, because a static host has no rewrite rules.
 *
 * GitHub Pages serves files. Ask it for `/components/button` and it looks for a
 * file of that name and returns its 404 page; ask it for
 * `/#/components/button` and it serves `index.html` and hands the rest to the
 * page. Every deep link works, the back button works, and nothing has to be
 * configured on the host — which for a site whose whole deployment story is
 * "push to main" is the difference between working and not.
 *
 * Links published before the content moved still resolve: a component's path
 * *is* `components/<slug>`, so `#/components/button` means what it always did.
 */
fun parseRoute(hash: String): Route {
    val path = hash.removePrefix("#").removePrefix("/").trim('/')
    return when {
        path.isEmpty() -> Route.Home
        path == "gallery" -> Route.Gallery
        path in docPagesByPath -> Route.Doc(path)
        else -> Route.Home
    }
}

/** Where the pages live, as a path in the repository. */
const val ContentRoot: String = "ui-docs/content"

/**
 * A relative markdown link, folded against the page that wrote it.
 *
 * Returns a repository path, not a site path, and is allowed to leave the
 * content directory — `../../docs/building/testing.md` is a real link on a real
 * page, and where it lands is the answer to whether it stays on the site.
 */
internal fun resolveFrom(from: String, target: String): String {
    val base = "$ContentRoot/$from".split('/').dropLast(1).toMutableList()
    for (part in target.split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> if (base.isNotEmpty()) base.removeAt(base.lastIndex)
            else -> base += part
        }
    }
    return base.joinToString("/")
}

/**
 * The route a link in the prose points at, or null when it leaves the site.
 *
 * The pages are read on GitHub as well as here, so their links are relative
 * markdown paths — `button.md`, `../tokens.md`. Both readings are legitimate
 * and neither can be the one stored, so the resolution happens here.
 *
 * [from] is the linking page's own path, without which `../tokens.md` cannot be
 * resolved at all. The previous version had no such parameter and answered
 * `null` for every `../` link — which is why a cross-reference between
 * families, or from a component to the guide that explains it, ejected the
 * reader to GitHub. That was one line, and it was the whole defect.
 */
fun routeForLink(target: String, from: String): Route? {
    if (target.startsWith("http") || target.startsWith("#")) return null
    if (!target.endsWith(".md") && !target.contains(".md#")) return null
    val resolved = resolveFrom(from, target.substringBefore("#")).removeSuffix(".md")
    if (!resolved.startsWith("$ContentRoot/")) return null
    val path = resolved.removePrefix("$ContentRoot/")
    return if (path in docPagesByPath) Route.Doc(path) else null
}

/** Where a link that leaves the site goes: the file, on GitHub. */
fun externalUrl(target: String, from: String): String = when {
    target.startsWith("http") -> target
    else -> "$Repository/blob/main/" + resolveFrom(from, target.substringBefore("#"))
}

const val Repository: String = "https://github.com/kontour-labs/ui"
