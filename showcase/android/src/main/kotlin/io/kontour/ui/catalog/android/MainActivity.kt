package io.kontour.ui.catalog.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.kontour.ui.catalog.Catalog

/**
 * The gallery, on a phone.
 *
 * Nothing here but the window: the catalog is a single composable and every
 * choice it makes belongs to `:ui-catalog`, so this host stays the one thing a
 * host has to be. Edge-to-edge because the library draws its own window insets
 * and a host that leaves the system bars opaque would hide the bug where it
 * does not.
 *
 * **`enableEdgeToEdge()` and nothing else, and the "nothing else" is the point.**
 * Its default bar style is `SystemBarStyle.auto`, which picks light or dark
 * *icons* from the system's `uiMode` — the right guess for an app whose theme
 * follows the system and the wrong one for this one, where the whole purpose is
 * a dark switch a reader can move independently. Turning the gallery dark under
 * a light phone left the clock and the battery dark on a dark bar. That flag now
 * comes from `KontourTheme` instead, which is the thing that knows; see
 * `platformReportAppearance`. What is left here is the transparency, which is
 * what this call is actually for.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Catalog() }
    }
}
