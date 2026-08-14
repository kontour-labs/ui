package io.kontour.ui.platform

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal actual val platformMinTouchTarget: Dp = 48.dp

/**
 * Reads `TRANSITION_ANIMATION_SCALE`, which is what Android's "Remove
 * animations" accessibility toggle and the developer-options animation scales
 * both write to. A scale of zero is the platform's own signal that the user
 * does not want transitions.
 */
@Composable
internal actual fun platformPrefersReducedMotion(): Boolean {
    val context = LocalContext.current
    return observeGlobalSetting(
        context = context,
        key = Settings.Global.TRANSITION_ANIMATION_SCALE,
    ) { resolver ->
        Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
    }
}

/**
 * `ACCESSIBILITY_DISPLAY_INVERSION_ENABLED` is not the right signal, and Android
 * has no public "increase contrast" flag before API 34. From 34 onwards
 * `Settings.Secure.CONTRAST_LEVEL` carries it; below that we report false and
 * let the in-app setting be the only route.
 */
@Composable
internal actual fun platformPrefersHighContrast(): Boolean {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < 34) return false
    return observeSecureSetting(context = context, key = CONTRAST_LEVEL) { resolver ->
        Settings.Secure.getFloat(resolver, CONTRAST_LEVEL, 0f) >= HIGH_CONTRAST_THRESHOLD
    }
}

/** `Settings.Secure.CONTRAST_LEVEL`, which is `@hide` in the SDK but stable since API 34. */
private const val CONTRAST_LEVEL = "contrast_level"

/** The platform reports -1f..1f; 0.5f is where its own "high contrast" step sits. */
private const val HIGH_CONTRAST_THRESHOLD = 0.5f

@Composable
private fun observeGlobalSetting(
    context: Context,
    key: String,
    read: (android.content.ContentResolver) -> Boolean,
): Boolean = observeSetting(context, Settings.Global.getUriFor(key), read)

@Composable
private fun observeSecureSetting(
    context: Context,
    key: String,
    read: (android.content.ContentResolver) -> Boolean,
): Boolean = observeSetting(context, Settings.Secure.getUriFor(key), read)

@Composable
private fun observeSetting(
    context: Context,
    uri: android.net.Uri?,
    read: (android.content.ContentResolver) -> Boolean,
): Boolean {
    val resolver = context.contentResolver
    var value by remember(uri) { mutableStateOf(read(resolver)) }

    DisposableEffect(resolver, uri) {
        if (uri == null) return@DisposableEffect onDispose {}
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = read(resolver)
            }
        }
        resolver.registerContentObserver(uri, false, observer)
        // Re-read on subscribe: the setting may have changed between the initial
        // read above and the observer being attached.
        value = read(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return value
}
