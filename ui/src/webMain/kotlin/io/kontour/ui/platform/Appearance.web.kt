package io.kontour.ui.platform

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
internal actual fun platformReportAppearance(dark: Boolean, background: Color) {
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
    // ### And the background, for what is round the canvas
    //
    // `color-scheme` paints the page white or black, which is not the theme's
    // background, and on a phone the page shows round the canvas: behind the
    // status bar and the address bar, and past the end of the page as it
    // bounces. Reported on an iPhone in November 2026 as the bars not being the
    // page's colour. The page's own background is what every browser paints
    // there, so it is set to the theme's: on `body` as well as the root,
    // because Safari 26 tints its bars from `body` before `html`, and an app's
    // own page may have given `body` a colour of its own. `theme-color` too,
    // which Chrome on Android tints its toolbar with and Safari now reads only
    // for a web app on the Home Screen.
    LaunchedEffect(background) {
        val css = cssHex(background)
        val root = document.documentElement as? HTMLElement ?: return@LaunchedEffect
        root.style.setProperty("background-color", css)
        document.body?.style?.setProperty("background-color", css)
        setThemeColour(css)
    }
}

/** `#rrggbb`, opaque: a page's background and a browser's toolbar have no alpha to show. */
private fun cssHex(colour: Color): String {
    val rgb = colour.toArgb() and 0xFFFFFF
    return "#" + rgb.toString(16).padStart(6, '0')
}

/** The page's `<meta name="theme-color">`, made if the page has none, and set to [css]. */
private fun setThemeColour(css: String) {
    val meta = document.querySelector("meta[name='theme-color']")
        ?: document.createElement("meta").also {
            it.setAttribute("name", "theme-color")
            document.head?.appendChild(it)
        }
    meta.setAttribute("content", css)
}

/**
 * `prefers-color-scheme`, through Compose.
 *
 * No loop: [platformReportAppearance] sets `color-scheme`, which tells the
 * browser what the page *can* render and does not feed `prefers-color-scheme`,
 * which is the user's setting.
 */
@Composable
internal actual fun platformSystemDark(): Boolean = isSystemInDarkTheme()
