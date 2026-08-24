package io.kontour.ui.docs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState

/**
 * The three things the site needs from whatever is hosting it.
 *
 * Everything else about this module — the shell, the index, the prose, the
 * routes themselves — is ordinary Compose, and putting it in `commonMain`
 * behind this seam is what makes the site testable at all. Until now
 * `:ui-docs` was `wasmJs` only, so it had no test source set that could run,
 * and it shipped with a landing page that threw on any window narrower than
 * 600dp. Nothing looked, because nothing could.
 *
 * The seam is deliberately three functions wide. A larger one would start
 * pulling browser concepts into the shell; a smaller one would mean faking a
 * `Window` on the JVM, which is a mock of the thing under test.
 */

/** Opens a URL outside the site — the repository, the API reference. */
expect fun openExternal(url: String)

/**
 * The current route, kept in step with wherever the host records history.
 *
 * In a browser that is the URL fragment, in both directions, which is what
 * makes the back button work. On the JVM it is a plain `MutableState`, which is
 * what makes a test able to walk all 84 pages without a browser.
 */
@Composable
expect fun rememberRoute(): MutableState<Route>

/** Goes to [target], leaving an entry the back button can return through. */
expect fun navigate(target: Route)
