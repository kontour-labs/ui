package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Nothing to read.
 *
 * A desktop window's corner is drawn by the window manager and is not the
 * display's, so there is no "device corner" here for a receded screen to be
 * concentric with — the window's own rounding, where it has any, is already
 * outside everything this library draws.
 */
@Composable
internal actual fun platformDeviceCornerRadius(): Dp? = null
