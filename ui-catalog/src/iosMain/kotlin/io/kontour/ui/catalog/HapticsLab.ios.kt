package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import io.kontour.haptics.Haptics
import io.kontour.haptics.IosHapticsRoute

// UIKit's generators three ways, and no UIKit at all: haptics were reported to
// stop for good on iPhones, and which way of driving the Taptic Engine keeps
// working is only something a phone can say.
internal actual val HapticsRoutes: List<String> = IosHapticsRoute.entries.map { it.name }

// Rumbles are Core Haptics whichever route plays the one-offs.
internal actual val RumbleRoutes: List<String> = emptyList()

@Composable
internal actual fun rememberRoutedHaptics(route: Int, rumbleRoute: Int): Haptics? {
    if (route == 0) return null
    val view = LocalUIViewController.current.view
    val haptics = remember(view, route) { Haptics(IosHapticsRoute.entries[route], view) }
    DisposableEffect(haptics) { onDispose { haptics.close() } }
    return haptics
}
