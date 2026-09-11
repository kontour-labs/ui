package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Sets the document's `color-scheme`, which is the browser's version of the same
 * flag.
 *
 * A browser paints chrome the canvas cannot reach — the scrollbars, the caret,
 * a `<select>`'s drop-down, form controls, and on a phone the address bar and
 * the notch area — and `color-scheme` is the one declaration that tells it which
 * way round to draw all of them. Without it the browser assumes light, so a dark
 * application gets white scrollbars down the side of it.
 *
 * On the root element rather than in a stylesheet, because the value follows the
 * theme and the theme is a runtime choice. Setting `color-scheme` also makes the
 * browser paint the canvas's default background to match, which is what stops
 * the white flash when a dark page is still loading.
 */
@Composable
internal actual fun platformReportAppearance(dark: Boolean) {
    // Keyed on the value rather than run after every composition: a DOM property
    // keeps whatever it was last set to, so there is nothing to re-assert.
    //
    // ### It does follow a later change, and this file used to say it did not
    //
    // The claim here was that the document was set once at startup and never
    // followed the app's own Dark switch, on the strength of a counter that read
    // `1`. **That was wrong**, and it is recorded rather than quietly deleted
    // because a false measurement written down confidently is worse than no
    // measurement at all.
    //
    // Re-measured on the built site in Chromium, driving the real control —
    // open the settings popover, tap Dark, read
    // `document.documentElement.style` — the document goes to
    // `color-scheme: dark`, and starting under `prefers-color-scheme: dark` and
    // tapping Dark off takes it to `color-scheme: light`. Both directions.
    //
    // The first experiment had one counter and nothing to compare it against, so
    // "the theme did not recompose" and "the counter did not record" looked
    // identical. The second had three — the site root, the theme provider and
    // `KontourTheme`'s own body — and a baseline run that opened the popover
    // without changing anything. Across the tap the site root stayed at 1, the
    // provider went 2 → 3 and the theme body 2 → 8. The theme recomposes; the
    // report reaches the document.
    LaunchedEffect(dark) {
        val root = document.documentElement as? HTMLElement ?: return@LaunchedEffect
        root.style.setProperty("color-scheme", if (dark) "dark" else "light")
    }
}
