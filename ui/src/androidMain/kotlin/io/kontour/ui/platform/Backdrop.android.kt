package io.kontour.ui.platform

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * `RenderEffect` is API 31. Below it Compose still builds the layer and still
 * pays for it, and then draws the backdrop through unblurred.
 */
internal actual val platformSupportsBackdropBlur: Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * `tappableElement`, which is the one inset that answers "is a bar drawn here".
 *
 * API 30, where `WindowInsets.Type` arrived; `minSdk` is 29, so Android 10 gets
 * zero and is fitted to the whole window exactly as it was.
 *
 * Read off the view's `rootWindowInsets` rather than through Compose's
 * `WindowInsets`, for the reason `platformDeviceCornerRadius` reads the corner
 * the same way: the composition's insets are a consumed, possibly-padded view of
 * the window, and this is a question about the window.
 *
 * Null before the view is attached, which is an ordinary first-composition state
 * rather than an error.
 */
@Composable
internal actual fun platformOpaqueBottomInset(): Dp {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0.dp
    val view = LocalView.current
    if (view.isInEditMode) return 0.dp
    val bottom = view.rootWindowInsets
        ?.getInsets(android.view.WindowInsets.Type.tappableElement())
        ?.bottom
        ?: return 0.dp
    return with(LocalDensity.current) { bottom.toDp() }
}
