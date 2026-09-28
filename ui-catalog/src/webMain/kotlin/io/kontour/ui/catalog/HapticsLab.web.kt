package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import io.kontour.haptics.Haptics

// One route here, so nothing to compare.
internal actual val HapticsRoutes: List<String> = emptyList()

internal actual val RumbleRoutes: List<String> = emptyList()

@Composable
internal actual fun rememberRoutedHaptics(route: Int, rumbleRoute: Int): Haptics? = null
