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
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Catalog() }
    }
}
