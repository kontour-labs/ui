package io.kontour.probes

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { KontourTheme { Text("Departures") } }
}
