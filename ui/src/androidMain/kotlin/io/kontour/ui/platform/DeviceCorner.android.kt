package io.kontour.ui.platform

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp

/**
 * The window's own top-left rounded corner, where the platform reports one.
 *
 * `RoundedCorner` is API 31. Below that there is no API at all — the corner is
 * drawn by the system and nothing asks it how big — so this is null and the
 * caller keeps the library's token, which is what every Android device got
 * before this existed.
 *
 * **Top-left specifically**, rather than the largest of the four. A device with
 * differing corners is one with a camera housing in one of them, and the top-left
 * is the one a receded screen's top edge is actually sitting inside. Reading the
 * bottom pair would also mean reading them through whatever is docked down there,
 * which is the [io.kontour.ui.overlay.overlayBackdrop] inset's problem and not
 * this one's.
 *
 * Null in edit mode and null before the view is attached: `rootWindowInsets` is
 * null until then, which is an ordinary state on the first composition rather
 * than an error.
 */
@Composable
internal actual fun platformDeviceCornerRadius(): Dp? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val view = LocalView.current
    if (view.isInEditMode) return null
    val radius = view.rootWindowInsets
        ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
        ?.radius
        ?: return null
    if (radius <= 0) return null
    return with(LocalDensity.current) { radius.toDp() }
}
