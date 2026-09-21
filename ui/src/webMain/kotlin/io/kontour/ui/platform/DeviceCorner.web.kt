package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Nothing to read.
 *
 * A page has no access to the device's bezel, and the browser's own chrome is
 * between the two in any case. Even installed as a home-screen app on an iPhone,
 * where the display genuinely is rounded, the viewport is a rectangle and the
 * corner is the browser's to draw.
 */
@Composable
internal actual fun platformDeviceCorners(): DeviceCorners? = null
