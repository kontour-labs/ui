import SwiftUI
import UIKit
import Catalog

/// The gallery, on an iPhone or iPad.
///
/// Nothing here but the window, which is the rule the other two hosts follow:
/// the catalog is a single composable and every choice it makes belongs to
/// `:ui-catalog`, so a host that decided anything would be a second place for a
/// rendering difference to come from. `CatalogViewController()` is the Kotlin
/// side of that — see `ui-catalog/src/iosMain/…/CatalogViewController.kt`.
///
/// `CatalogViewControllerKt` is not a name anybody typed. Kotlin/Native exports
/// a file's top-level functions as static members of a class named after the
/// file plus `Kt`, so `CatalogViewController.kt` becomes
/// `CatalogViewControllerKt` and the function stays as it was.
struct CatalogView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        CatalogViewControllerKt.CatalogViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {
        // Nothing to push down. The gallery holds all of its own state.
    }
}

@main
struct KontourUIApp: App {
    var body: some Scene {
        WindowGroup {
            CatalogView()
                // Compose does its own keyboard avoidance — `WindowInsets.ime`
                // is a first-class token in this library and the sheets read it
                // — so letting SwiftUI also inset for the keyboard would move
                // the content twice. Every other safe-area edge is left alone
                // on purpose: `ComposeUIViewController` reports those to Compose
                // as window insets, which is what `NavBar` and `BottomSheet`
                // already consume.
                .ignoresSafeArea(.keyboard)
        }
    }
}
