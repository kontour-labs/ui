package io.kontour.ui.platform

import android.os.Build

/**
 * `RenderEffect` is API 31. Below it Compose still builds the layer and still
 * pays for it, and then draws the backdrop through unblurred.
 */
internal actual val platformSupportsBackdropBlur: Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
