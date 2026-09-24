package io.kontour.ui.catalog

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The gallery, as something an Xcode project can put on screen.
 *
 * The whole iOS host is this function plus a `UIHostingController` — everything
 * the catalog decides belongs to [Catalog], and a host that decided anything
 * would be a second place for a rendering difference to come from.
 */
fun CatalogViewController(): UIViewController = ComposeUIViewController(
    configure = {
        // **Compose's insets move the content for the keyboard; the view must not
        // move as well.** The default shifts the whole view up to keep a focused
        // field above the keyboard, and the catalog's `Scaffold` pads by
        // `safeDrawing`, which already includes the keyboard. Both answering was
        // reported as a text field near the bottom opening "2 IMEs" — a keyboard's
        // height of blank space between the keyboard and the field. The field
        // scaffold brings itself into view inside the padded content instead.
        onFocusBehavior = OnFocusBehavior.DoNothing
    },
) { Catalog() }
