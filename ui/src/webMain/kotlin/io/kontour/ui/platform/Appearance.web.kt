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
    // ### This reaches the document once and does not follow a later change
    //
    // Measured in a real browser, and it is the web actual specifically. Load the
    // site under `prefers-color-scheme: dark` and the document comes up
    // `color-scheme: dark`; load it light, turn the site's own Dark switch on,
    // and the canvas goes dark while the document stays `color-scheme: light`.
    //
    // It is not this effect and not `KontourTheme`'s wiring. Instrumented with a
    // counter written from the composable's *body*, `platformReportAppearance`
    // is called **exactly once** on the site across a change that visibly
    // repaints the whole app — while the JVM actual, instrumented the same way
    // and driven the same way, reports `[false, true]`. So `KontourTheme`'s body
    // is not re-running on wasm even as the scheme it provides changes, which is
    // a fact about that composition rather than about this file, and is worth
    // its own investigation rather than a guess here.
    //
    // What it costs today: the browser's scrollbars, caret and form controls
    // follow the *system* appearance rather than the site's own switch. Android
    // and iOS use different actuals and are unaffected.
    LaunchedEffect(dark) {
        val root = document.documentElement as? HTMLElement ?: return@LaunchedEffect
        root.style.setProperty("color-scheme", if (dark) "dark" else "light")
    }
}
