package io.kontour.ui.docs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.browser.window
import org.w3c.dom.events.Event

actual fun openExternal(url: String) {
    window.open(url, "_blank")
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
 * The listener is what makes the browser's back button work: navigating sets
 * the hash, the browser records it, and going back changes the hash again —
 * which arrives here as a `hashchange` rather than as something this code has
 * to model.
 */
@Composable
actual fun rememberRoute(): MutableState<Route> {
    val route = remember { mutableStateOf(parseRoute(window.location.hash)) }

    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { route.value = parseRoute(window.location.hash) }
        window.addEventListener("hashchange", listener)
        onDispose { window.removeEventListener("hashchange", listener) }
    }

    return route
}

actual fun navigate(target: Route) {
    window.location.hash = target.hash
}
