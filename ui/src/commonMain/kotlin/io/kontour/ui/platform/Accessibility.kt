package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * The smallest tappable area the host platform's guidelines call for.
 *
 * Android's Material guidance and Google's a11y docs say 48dp; Apple's HIG says
 * 44pt; on a pointer-driven surface the WCAG 2.2 "Target Size (Minimum)"
 * floor of 24 CSS px applies instead. Web resolves to the touch value, because
 * a browser window may be on a tablet — `Modifier.minimumTouchTarget()` narrows
 * it at runtime when the active input turns out to be a mouse.
 */
internal expect val platformMinTouchTarget: Dp

/**
 * Whether the user has asked the operating system to reduce motion.
 *
 * Observed, not sampled once: on every platform this recomposes if the setting
 * changes while the app is running, because users toggle it *because* something
 * on screen is making them uncomfortable and waiting for a relaunch is no help.
 */
@Composable
expect fun platformPrefersReducedMotion(): Boolean

/**
 * Whether the user has asked the operating system to increase contrast.
 *
 * Drives the default [io.kontour.ui.theme.ContrastLevel]. An in-app setting can
 * still override it.
 *
 * **Public**, because an app that supplies its own `ColorScheme` has to know
 * which tier to build — `KontourTheme` takes the scheme and the tier as two
 * separate parameters, and an app that could not read this would have to
 * reimplement the platform detection to keep them in step.
 */
@Composable
expect fun platformPrefersHighContrast(): Boolean
