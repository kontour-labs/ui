package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
    SideEffect {
        val root = document.documentElement as? HTMLElement ?: return@SideEffect
        root.style.setProperty("color-scheme", if (dark) "dark" else "light")
    }
}
