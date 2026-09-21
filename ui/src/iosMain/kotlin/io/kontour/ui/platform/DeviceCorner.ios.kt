package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import platform.Foundation.valueForKey
import platform.UIKit.UIScreen

/**
 * The display's corner radius, read through a private key.
 *
 * `_displayCornerRadius` is what every iOS application that draws a concentric
 * inset reads, and there is no public equivalent — UIKit exposes the safe area
 * and the cutout and says nothing about the bezel's curvature. So this is a
 * judgement rather than an oversight, and the judgement is worth stating: the
 * key is read **by name through KVC, inside a `runCatching`**, and a failure of
 * any kind falls back to null and therefore to the library's own token.
 *
 * ### What that costs, and what it does not
 *
 * The risk of a private key is that it disappears and the call throws. That is
 * the case handled: a throw here is a null, a null is "unknown", and unknown is
 * the `extraLarge` corner every device drew before this was added. The failure
 * mode is the previous behaviour rather than a crash or a wrong shape.
 *
 * The other risk is review. Reading a private symbol by name is not a private
 * *API call* in the sense a static analyser flags — there is no linked symbol —
 * but it is the same thing by intent, and a reviewer may say so. If this has to
 * go, deleting the body and returning null is the whole of the change and
 * nothing above it moves.
 *
 * ### Points, not pixels
 *
 * `UIScreen` reports in points and Compose's `Dp` is points on iOS, so the number
 * crosses directly. `mainScreen` rather than the window's own: the corner belongs
 * to the display and an application in a Slide Over pane on iPad should not take
 * the iPad's bezel as its own — which is also why a zero, or a screen that is not
 * the device's, comes back as unknown rather than as a number to nest inside.
 */
@Composable
internal actual fun platformDeviceCorners(): DeviceCorners? = remember {
    runCatching {
        (UIScreen.mainScreen.valueForKey("_displayCornerRadius") as? Number)
            ?.toDouble()
            ?.takeIf { it > 0.0 }
            ?.dp
            // **Four equal corners and a stated curve.** Every iPhone and iPad
            // is symmetric, and the smoothing is not a guess here the way it is
            // on Android: `SquircleShape.DefaultSmoothing` is 0.6 *because* it
            // was matched to this bezel. Saying so out loud is the point —
            // inheriting it by luck reads identically until somebody re-tunes
            // the scale for a brand and quietly moves the one shape that is
            // supposed to sit inside a fixed piece of glass.
            ?.let { DeviceCorners.uniform(it, smoothing = IosDisplaySmoothing) }
    }.getOrNull()
}

/** See `SquircleShape.DefaultSmoothing`, which this is the origin of. */
private const val IosDisplaySmoothing = 0.6f
