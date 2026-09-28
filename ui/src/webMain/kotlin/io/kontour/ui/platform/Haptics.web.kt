package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.kontour.haptics.Haptics

@Composable
internal actual fun rememberPlatformHaptics(): Haptics {
    val haptics = remember { Haptics() }
    DisposableEffect(haptics) { onDispose { haptics.close() } }
    return haptics
}
