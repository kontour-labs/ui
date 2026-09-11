package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow

/**
 * Overrides the key window's interface style, which is what UIKit reads when it
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
 */
@Composable
internal actual fun platformReportAppearance(dark: Boolean) {
    SideEffect {
        val style = if (dark) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
        @Suppress("DEPRECATION")
        UIApplication.sharedApplication.windows.forEach { window ->
            (window as? UIWindow)?.overrideUserInterfaceStyle = style
        }
    }
}
