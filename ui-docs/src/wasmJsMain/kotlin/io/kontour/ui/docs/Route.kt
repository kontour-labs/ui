package io.kontour.ui.docs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.browser.window
import org.w3c.dom.events.Event

/** Where the reader is. */
sealed interface Route {
    data object Home : Route
    data object Gallery : Route
    data class Component(val slug: String) : Route

    val hash: String
        get() = when (this) {
            Home -> "#/"
            Gallery -> "#/gallery"
            is Component -> "#/components/$slug"
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
 */
fun parseRoute(hash: String): Route {
    val path = hash.removePrefix("#").removePrefix("/").trim('/')
    return when {
        path.isEmpty() -> Route.Home
        path == "gallery" -> Route.Gallery
        path.startsWith("components/") -> Route.Component(path.removePrefix("components/"))
        else -> Route.Home
    }
}

/**
 * The current route, kept in step with the address bar in both directions.
 *
 * The listener is what makes the browser's back button work: navigating sets
 * the hash, the browser records it, and going back changes the hash again —
 * which arrives here as a `hashchange` rather than as something this code has
 * to model.
 */
@Composable
fun rememberRoute(): MutableState<Route> {
    val route = remember { mutableStateOf(parseRoute(window.location.hash)) }

    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { route.value = parseRoute(window.location.hash) }
        window.addEventListener("hashchange", listener)
        onDispose { window.removeEventListener("hashchange", listener) }
    }

    return route
}

/** Goes to [target], leaving an entry the back button can return through. */
fun navigate(target: Route) {
    window.location.hash = target.hash
}

/**
 * The route a link in the prose points at, or null when it leaves the site.
 *
 * The pages are read on GitHub as well as here, so their links are relative
 * markdown paths — `button.md`, `../tokens.md`. Both readings are legitimate
 * and neither can be the one stored, so the resolution happens here: a sibling
 * `.md` under `components/` is a page this site has, and anything else is a
 * link out to the repository.
 */
fun routeForLink(target: String): Route? {
    if (target.startsWith("http") || target.startsWith("#")) return null
    if (!target.endsWith(".md") && !target.contains(".md#")) return null
    if (target.startsWith("../")) return null
    val slug = target.substringBefore("#").removeSuffix(".md")
    return if (slug in docPagesBySlug) Route.Component(slug) else null
}

/** Where a link that leaves the site goes: the file, on GitHub. */
fun externalUrl(target: String): String = when {
    target.startsWith("http") -> target
    else -> "$Repository/blob/main/docs/using/components/${target.removePrefix("./")}"
}

const val Repository: String = "https://github.com/kontour-labs/ui"
