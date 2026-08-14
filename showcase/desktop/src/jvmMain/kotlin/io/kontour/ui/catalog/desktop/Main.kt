package io.kontour.ui.catalog.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.kontour.ui.catalog.Catalog

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 1280.dp, height = 900.dp),
        title = "Kontour UI Catalog",
    ) {
        Catalog()
    }
}
