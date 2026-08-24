package io.kontour.ui.docs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * The site, off the web.
 *
 * There is no address bar here, so the route is simply state and `navigate`
 * sets it. That is not a stub standing in for the real thing — it is the whole
 * behaviour the browser actual gets from `hashchange`, with the round trip
 * through the URL removed. A test can therefore drive the site the way a reader
 * does, by pressing the index, rather than by calling a router directly.
 */
private val route = mutableStateOf<Route>(Route.Home)

actual fun openExternal(url: String) {
    // Nothing sensible to do off the web, and throwing would turn a link in a
    // rendered page into a failed render.
}

@Composable
actual fun rememberRoute(): MutableState<Route> = route

actual fun navigate(target: Route) {
    route.value = target
}
