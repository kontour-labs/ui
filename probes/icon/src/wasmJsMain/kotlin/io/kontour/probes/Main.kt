package io.kontour.probes

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Star
import androidx.compose.foundation.layout.Row
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Beside the same `Text` as `theme`, so the difference is the icon alone
    // and not the text it would otherwise leave out.
    ComposeViewport {
        KontourTheme {
            Row {
                Text("Departures")
                Icon(Tabler.Outline.Star, contentDescription = null)
            }
        }
    }
}
