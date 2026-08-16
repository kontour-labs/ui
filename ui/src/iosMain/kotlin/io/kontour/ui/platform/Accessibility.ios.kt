package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityDarkerSystemColorsEnabled
import platform.UIKit.UIAccessibilityDarkerSystemColorsStatusDidChangeNotification
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

/** Apple's Human Interface Guidelines minimum. */
internal actual val platformMinTouchTarget: Dp = 44.dp

@Composable
actual fun platformPrefersReducedMotion(): Boolean =
    observeAccessibilityFlag(
        notificationName = UIAccessibilityReduceMotionStatusDidChangeNotification,
        read = { UIAccessibilityIsReduceMotionEnabled() },
    )

/**
 * "Increase Contrast" in Settings › Accessibility › Display & Text Size. UIKit
 * exposes it as *darker system colors*.
 */
@Composable
actual fun platformPrefersHighContrast(): Boolean =
    observeAccessibilityFlag(
        notificationName = UIAccessibilityDarkerSystemColorsStatusDidChangeNotification,
        read = { UIAccessibilityDarkerSystemColorsEnabled() },
    )

/**
 * UIKit's notification-name constants come through the Kotlin/Native bindings as
 * nullable, so a null name means the OS did not publish that notification on
 * this version. The flag is still read once — the value is correct, it just will
 * not update live.
 */
@Composable
private fun observeAccessibilityFlag(
    notificationName: String?,
    read: () -> Boolean,
): Boolean {
    var value by remember(notificationName) { mutableStateOf(read()) }

    DisposableEffect(notificationName) {
        if (notificationName == null) return@DisposableEffect onDispose {}

        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = notificationName,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            value = read()
        }
        // Re-read on subscribe: the setting may have changed between the initial
        // read above and the observer being attached.
        value = read()
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    return value
}
