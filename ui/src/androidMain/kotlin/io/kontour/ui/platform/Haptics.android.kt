package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import io.kontour.haptics.Haptics

/**
 * The view is the one the composition is attached to, so the system's feedback
 * constants — the route for a phone without composition primitives — play
 * through the window the finger is on, and follow that view's own
 * haptic-feedback flag.
 */
@Composable
internal actual fun rememberPlatformHaptics(): Haptics {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = remember(context, view) { Haptics(context, view) }
    DisposableEffect(haptics) { onDispose { haptics.close() } }
    return haptics
}
