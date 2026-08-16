package io.kontour.ui.catalog

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The gallery, as something an Xcode project can put on screen.
 *
 * The whole iOS host is this function plus a `UIHostingController` — everything
 * the catalog decides belongs to [Catalog], and a host that decided anything
 * would be a second place for a rendering difference to come from.
 */
fun CatalogViewController(): UIViewController = ComposeUIViewController { Catalog() }
