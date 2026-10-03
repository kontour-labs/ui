package io.kontour.ui.docs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState

/**
 * The four things the site needs from whatever is hosting it.
 *
 * Everything else about this module — the shell, the index, the prose, the
 * routes themselves — is ordinary Compose, and putting it in `commonMain`
 * behind this seam is what makes the site testable at all. Until now
 * `:ui-docs` was `wasmJs` only, so it had no test source set that could run,
 * and it shipped with a landing page that threw on any window narrower than
 * 600dp. Nothing looked, because nothing could.
 *
 * The seam is deliberately five functions wide. A larger one would start
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

/**
 * Puts [text] on the clipboard: a code block's Copy button.
 *
 * Not the framework's `Clipboard`, and on purpose. On the web its writes never
 * reach the browser — `docs/building/testing.md`, "Three fixes, no number
 * moved" — while `navigator.clipboard.writeText` called from the same page was
 * measured working. A button press is a user gesture, which is all that call
 * asks for.
 */
expect fun copyText(text: String)

/**
 * A file beside the site, as text: the icon browser's data, which
 * `docs/lay-out-site.py` lays out under `icons/`. Null when it cannot be had —
 * not there, or no network.
 *
 * In a browser a `fetch`, relative to the page. On the JVM a file under the
 * directory the `kontour.docs.files` property names, so a test or a render can
 * point the site at data `docs/icondata.py` wrote; without one, nothing is there.
 */
expect suspend fun fetchText(path: String): String?
