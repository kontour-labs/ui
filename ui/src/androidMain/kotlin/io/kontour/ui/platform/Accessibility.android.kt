package io.kontour.ui.platform

import android.app.UiModeManager
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
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
actual fun platformPrefersReducedMotion(): Boolean {
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
 * has no "increase contrast" flag at all before API 34. From 34 onwards
 * `UiModeManager.getContrast()` carries it; below that we report false and let the
 * in-app setting be the only route.
 *
 * ### This read used to crash the app on launch
 *
 * It went through `Settings.Secure.getFloat(resolver, "contrast_level")`, on the
 * reasoning that the key is `@hide` in the SDK but stable in the platform since
 * API 34. That reasoning has a hole in it, and the hole is four years older than
 * the setting: since **Android 12**, `SettingsProvider.enforceSettingReadable`
 * refuses any `@hide` key to a non-system app unless it also carries `@Readable`.
 * `contrast_level` does not, so the read threw
 *
 *     java.lang.SecurityException: Settings key: <contrast_level> is not readable.
 *
 * The `SDK_INT < 34` guard could not help — the throw happens *on* 34 and above,
 * which is exactly where the read ran. And this is called at the top of a screen,
 * before any content, so every launch on a modern device died. Reported from a
 * real device; nothing in this repository could have caught it, because there are
 * no Android host tests and every other target returns a constant.
 *
 * `UiModeManager.getContrast()` is the public accessor the platform added in the
 * same release as the setting, with `UiModeManager.ContrastChangeListener` for
 * observing it. It is not `@hide`, so it has no such failure mode — which is why
 * this does not defend itself with a `runCatching`. A read that cannot throw is
 * better than one that is caught.
 */
@Composable
actual fun platformPrefersHighContrast(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        contrastFromUiModeManager()
    } else {
        false
    }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun contrastFromUiModeManager(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(UiModeManager::class.java) }
        ?: return false

    var value by remember(manager) { mutableStateOf(manager.contrast >= HIGH_CONTRAST_THRESHOLD) }

    DisposableEffect(manager) {
        val listener = UiModeManager.ContrastChangeListener { contrast ->
            value = contrast >= HIGH_CONTRAST_THRESHOLD
        }
        manager.addContrastChangeListener(context.mainExecutor, listener)
        // Re-read on subscribe, for the same reason `observeSetting` does: the
        // value may have moved between the initial read and the listener landing.
        value = manager.contrast >= HIGH_CONTRAST_THRESHOLD
        onDispose { manager.removeContrastChangeListener(listener) }
    }
    return value
}

/** The platform reports -1f..1f; 0.5f is where its own "high contrast" step sits. */
private const val HIGH_CONTRAST_THRESHOLD = 0.5f

@Composable
private fun observeGlobalSetting(
    context: Context,
    key: String,
    read: (android.content.ContentResolver) -> Boolean,
): Boolean = observeSetting(context, Settings.Global.getUriFor(key), read)

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
