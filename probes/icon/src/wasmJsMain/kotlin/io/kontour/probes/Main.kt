package io.kontour.probes

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Star
import io.kontour.ui.foundation.Icon
import io.kontour.ui.theme.KontourTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { KontourTheme { Icon(Tabler.Outline.Star, contentDescription = null) } }
}
