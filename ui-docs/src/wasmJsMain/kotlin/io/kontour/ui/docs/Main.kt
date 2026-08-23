package io.kontour.ui.docs

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { Site() }
}

/** Opens a URL in a new tab. */
fun openExternal(url: String) {
    window.open(url, "_blank")
}
