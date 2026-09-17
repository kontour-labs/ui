package io.kontour.ui.platform

import androidx.compose.foundation.isSystemInDarkTheme
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

/**
 * What the "device" is set to, which on the JVM is whatever a test says.
 *
 * Desktop has no loop to break — nothing here writes anything a reader could
 * read back — so the honest actual would be `isSystemInDarkTheme()`. It is a
 * settable field instead, and that is the whole point of it: the invariant this
 * function exists for is **iOS-only and cannot be run anywhere in this
 * repository**. The iOS source set is type-checked here — that is what
 * `:ui:compileIosMainKotlinMetadata` is in the gate for — but there is no iOS
 * test source set and no simulator, so nothing executes it.
 *
 * What *can* be run is the contract: reporting an appearance must not change
 * what the device says it is. A field the test owns makes that a real assertion
 * instead of a comment, and `AppearanceReportTest` is where it is made.
 *
 * `null` means "nobody has said", and then this defers to Compose, which is what
 * a desktop application actually wants.
 */
internal var systemDarkOverride: Boolean? = null

@Composable
internal actual fun platformSystemDark(): Boolean =
    systemDarkOverride ?: isSystemInDarkTheme()
