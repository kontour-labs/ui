package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Whether this platform can blur what is already on the canvas behind a layer.
 *
 * A performance guard, **not** a design switch. Where this is false the library
 * draws exactly the same picture minus the blur, because the blur is a texture
 * over one design rather than a second design: same shapes, same scrim, same
 * motion, same layout. Nothing else in the library branches on it.
 *
 * What it saves is real, though. Compose composes a `renderEffect` layer whether
 * or not the backend can apply the effect, so on a device that cannot blur, the
 * whole app content would be rendered to an offscreen buffer every frame a modal
 * is open and then drawn back unchanged.
 *
 * The one platform where this is false is Android below API 31. `RenderEffect`
 * arrived in Android 12, and below it Compose drops the effect silently — no
 * crash, no warning, a sharp copy of the backdrop. `minSdk` here is 29, so
 * Android 10 and 11 are inside the library's range and outside the blur's.
 */
internal expect val platformSupportsBackdropBlur: Boolean

/**
 * How much of the window's bottom edge carries something **opaque**, in `Dp`.
 *
 * A `WindowInsets` bottom is the wrong question and this is the right one. An
 * inset says the system has reserved the edge; it does not say whether anything
 * is painted on it, and a receding screen cares about nothing else. Gesture
 * navigation reserves a strip for its handle and paints nothing there, so a frame
 * that runs under it is a frame the reader can see. Three-button navigation
 * paints a bar, and a frame under *that* is no frame at all — which is the
 * reported defect, and the only case where the backdrop moves the page rather
 * than simply fitting it to the window.
 *
 * | | |
 * |---|---|
 * | Android | `tappableElement`'s bottom, API 30+ — zero under gesture navigation and the bar's height under three buttons, which is exactly the distinction |
 * | iOS | zero. The home indicator is drawn over the app and nothing is reserved under it; a sheet's own safe-area handling is a separate question from this one |
 * | Desktop, web | zero. Neither has a system bar inside the window |
 *
 * Zero is also the honest answer for "cannot tell", and it is the answer that
 * changes nothing: the page is fitted to the whole window, which is what every
 * platform did before this existed.
 */
@Composable
internal expect fun platformOpaqueBottomInset(): Dp
