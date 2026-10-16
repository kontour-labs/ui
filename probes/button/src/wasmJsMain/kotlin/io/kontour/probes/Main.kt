package io.kontour.probes

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.kontour.ui.components.action.Button
import io.kontour.ui.theme.KontourTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { KontourTheme { Button(onClick = {}) { +"Departures" } } }
}
