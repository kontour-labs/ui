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
                // Every edge and every region, which is this host's one real
                // decision and the reason it is spelled out.
                //
                // This library is edge-to-edge by construction. `TopBar` paints
                // its `Surface` across the full width and *then* applies
                // `windowInsetsPadding(WindowInsets.topEdges)` inside it, so the
                // background runs under the status bar while the title sits
                // below it; `NavBar` does the same at the bottom with the home
                // indicator. `MainActivity` gets there by calling
                // `enableEdgeToEdge()`, and this is the same statement in
                // SwiftUI's vocabulary.
                //
                // Anything less hurts twice, which is how the first version of
                // this file was found to be wrong. `.ignoresSafeArea(.keyboard)`
                // ignores the *keyboard* region only, so SwiftUI went on
                // shrinking the view to the safe area: the bars' backgrounds
                // stopped at the boundary instead of reaching under the system
                // chrome, and UIKit still propagated the window's
                // `safeAreaInsets` into the hosted controller, so Compose padded
                // for a status bar it had already been moved clear of. Reported
                // as "the top bar and bottom bar cut off the background" and
                // "the content is way too inset at the top" — two symptoms, one
                // modifier.
                //
                // The keyboard is included in `.all` and wants to be: Compose
                // has its own IME handling, `WindowInsets.ime` is a first-class
                // token here, and `sheetEdges` unions it in for exactly the
                // surfaces that hold a text field. SwiftUI insetting as well
                // would move those twice.
                .ignoresSafeArea()
        }
    }
}
