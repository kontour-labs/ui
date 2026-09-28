package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import io.kontour.haptics.Haptics

/**
 * This platform's haptics player, remembered for as long as the call site is in
 * the composition and closed when it leaves.
 *
 * The one seam between the design system and the device's haptics, and all it
 * does is construct the player: which effect an interaction gets is decided in
 * common code (`FeedbackIntent.defaultEffect`) and how a platform plays an effect
 * is the `:haptics` module's business. Android is the only platform that needs
 * anything from the composition to make one — a `Context` and the `View`.
 */
@Composable
internal expect fun rememberPlatformHaptics(): Haptics
