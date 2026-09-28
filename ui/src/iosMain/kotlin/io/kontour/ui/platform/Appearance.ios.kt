package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow

/**
 * Overrides the windows' interface style, which is what UIKit reads when it
 * decides what colour to draw the status bar's contents.
 *
 * A view controller's `preferredStatusBarStyle` is the other route and it is the
 * wrong one here: it belongs to the host's controller, which in a Compose
 * Multiplatform application is created by the framework, and a design system
 * cannot reach it. `overrideUserInterfaceStyle` is a property of the window and
 * says the thing that is actually true — *this application is currently dark* —
 * which additionally puts any UIKit chrome the app still has, and any system
 * sheet it presents, on the same side as the canvas.
 *
 * `windows` rather than `connectedScenes`: it is deprecated but present, it is
 * one line, and the alternative walks a scene graph to reach the same objects.
 *
 * ### It only overrides when it has something to say
 *
 * The property is not only an instruction to UIKit. It replaces the trait
 * environment for everything beneath the window, and Compose Multiplatform's
 * iOS scene reads its own system theme out of exactly that — the hosting view
 * controller's `traitCollection.userInterfaceStyle`, pushed into
 * `LocalSystemTheme` from `traitCollectionDidChange`. So an unconditional
 * override makes `isSystemInDarkTheme()` report the application to itself, and
 * a reader who turns the app dark and then asks it to follow the device gets
 * dark, permanently, because by then the device *is* dark as far as anything
 * can tell. Reported from an iPhone.
 *
 * So an application that already agrees with the device installs **no
 * override**: the windows are handed `Unspecified` and go on following the
 * device live, which is precisely the case where the read has to be right. An
 * application that disagrees is pinned deliberately, and the one moment its
 * device value matters again — the reader asking to follow the device — is a
 * tap, which recomposes, which reads [platformSystemDark] below.
 *
 * `LaunchedEffect(dark)` rather than a bare `SideEffect`, which is what this
 * was. A `SideEffect` runs after **every** successful composition, so the
 * override was re-asserted continuously and a genuine appearance change made in
 * Control Centre could never win even for a frame. The web actual has always
 * been keyed this way.
 */
@Composable
internal actual fun platformReportAppearance(dark: Boolean) {
    val device = platformSystemDark()
    LaunchedEffect(dark, device) {
        val style = when {
            dark == device -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            dark -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
            else -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
        @Suppress("DEPRECATION")
        UIApplication.sharedApplication.windows.forEach { window ->
            (window as? UIWindow)?.overrideUserInterfaceStyle = style
        }
    }
}

/**
 * The **screen's** interface style, which no window override can reach.
 *
 * `UIScreen` sits above every window, so `overrideUserInterfaceStyle` — which is
 * a property of a window — cannot change what it reports. That is the whole
 * reason this reads the screen rather than `isSystemInDarkTheme()`, which reads
 * the view controller's traits and therefore reads the override. See
 * [platformSystemDark]'s declaration for the loop in full.
 *
 * `UIUserInterfaceStyleUnspecified` is the answer on a device that has never
 * been asked, and light is the right reading of it: it is what UIKit itself
 * falls back to.
 *
 * **Compiled here, not run here.** `:ui:compileIosMainKotlinMetadata`
 * type-checks this source set on a Linux host, UIKit cinterop included — a
 * mistyped `userInterfaceStyle` fails it — and that task is in the verification
 * gate for exactly this reason. What no task in this repository can do is
 * *execute* it: there is no iOS test source set and no simulator, so whether
 * `UIScreen` really is above the override is settled by an iPhone and by
 * nothing else. The contract the two functions have to keep is asserted on the
 * JVM instead — see `systemDarkOverride` in the JVM actual.
 */
@Composable
internal actual fun platformSystemDark(): Boolean =
    UIScreen.mainScreen.traitCollection.userInterfaceStyle ==
        UIUserInterfaceStyle.UIUserInterfaceStyleDark
