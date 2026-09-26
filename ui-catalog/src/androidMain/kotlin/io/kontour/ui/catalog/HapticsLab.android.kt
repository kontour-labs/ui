package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import io.kontour.haptics.AndroidHapticsRoute
import io.kontour.haptics.AndroidRumbleRoute
import io.kontour.haptics.Haptics

internal actual val HapticsRoutes: List<String> = AndroidHapticsRoute.entries.map { it.name }

internal actual val RumbleRoutes: List<String> = AndroidRumbleRoute.entries.map { it.name }

@Composable
internal actual fun rememberRoutedHaptics(route: Int, rumbleRoute: Int): Haptics? {
    if (route == 0 && rumbleRoute == 0) return null
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = remember(context, view, route, rumbleRoute) {
        Haptics(context, view, AndroidHapticsRoute.entries[route], AndroidRumbleRoute.entries[rumbleRoute])
    }
    DisposableEffect(haptics) { onDispose { haptics.close() } }
    return haptics
}
