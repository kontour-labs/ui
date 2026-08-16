package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Desktop is pointer-driven, so WCAG 2.2's 24 CSS px target-size floor applies
 * rather than a touch guideline.
 */
internal actual val platformMinTouchTarget: Dp = 24.dp

/**
 * The JVM has no cross-platform way to read the OS motion preference — it lives
 * behind AppKit on macOS, `SystemParametersInfo` on Windows and the desktop
 * portal on Linux, none of which are reachable from stdlib.
 *
 * Desktop is a development and test host rather than a shipping target, so
 * rather than three JNI paths this returns false and defers to the in-app
 * setting, which `KontourTheme` accepts as an override.
 */
@Composable
actual fun platformPrefersReducedMotion(): Boolean = false

/** Not readable from the JVM either — see [platformPrefersReducedMotion]. */
@Composable
actual fun platformPrefersHighContrast(): Boolean = false
