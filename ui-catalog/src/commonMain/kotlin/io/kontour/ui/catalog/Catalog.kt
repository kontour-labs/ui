package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * The component gallery — every component, in every state, on every platform.
 *
 * Currently a stub; it grows an entry per component as the phases land.
 */
@Composable
fun Catalog() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Kontour UI",
            style = TextStyle(fontSize = 24.sp, color = Color(0xFF121212)),
        )
    }
}
