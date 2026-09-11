package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

/**
 * Nothing to tell: a desktop window has no chrome the application's own
 * appearance should drive.
 *
 * The title bar belongs to the window manager and follows the *operating
 * system's* appearance, not the application's — a macOS window does not get a
 * dark title bar because the app inside it drew dark, and should not. There is
 * no equivalent of a status bar to get wrong.
 *
 * It records what it was told all the same. See [reportedAppearances].
 */
/**
 * What was reported, in order, for the one platform whose wiring can be tested.
 *
 * The JVM has nothing to tell a window, so this actual would otherwise be `Unit`
 * — and an `expect` whose only testable `actual` does nothing is a piece of
 * plumbing that can be cut without any check noticing. `AppearanceReportTest`
 * flips a theme and reads this, which is the only place in the repository that
 * can see whether `KontourTheme` reports a change at all.
 */
internal val reportedAppearances = mutableListOf<Boolean>()

@Composable
internal actual fun platformReportAppearance(dark: Boolean) {
    SideEffect { reportedAppearances += dark }
}
