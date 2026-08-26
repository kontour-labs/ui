package io.kontour.ui.platform

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
