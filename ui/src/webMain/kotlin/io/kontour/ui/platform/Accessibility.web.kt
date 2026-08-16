package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * The touch guideline rather than WCAG's 24px pointer floor, because a browser
 * window may well be on a tablet. `Modifier.minimumTouchTarget()` narrows this
 * at runtime once the active input modality turns out to be a mouse.
 */
internal actual val platformMinTouchTarget: Dp = 44.dp

@Composable
actual fun platformPrefersReducedMotion(): Boolean =
    observeMediaQuery("(prefers-reduced-motion: reduce)")

@Composable
actual fun platformPrefersHighContrast(): Boolean =
    observeMediaQuery("(prefers-contrast: more)")

/**
 * Tracks a CSS media query, recomposing when it flips.
 *
 * Uses `addEventListener("change", …)` rather than the older `addListener`,
 * which is deprecated and absent from Safari's modern implementations.
 */
@Composable
private fun observeMediaQuery(query: String): Boolean {
    val mediaQuery = remember(query) { window.matchMedia(query) }
    var matches by remember(mediaQuery) { mutableStateOf(mediaQuery.matches) }

    DisposableEffect(mediaQuery) {
        val listener: (Event) -> Unit = { matches = mediaQuery.matches }
        mediaQuery.addEventListener("change", listener)
        matches = mediaQuery.matches
        onDispose { mediaQuery.removeEventListener("change", listener) }
    }
    return matches
}
