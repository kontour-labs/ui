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
internal actual fun platformDeviceCorners(): DeviceCorners? = deviceCornersOverride

/**
 * What a test says the display is, since no desktop has one to report.
 *
 * The same seam `systemDarkOverride` is, for the same reason and with the same
 * limits: the two platforms that can answer have no test source set in this
 * repository, so the *shape* of the answer — four corners, a nullable curve, an
 * asymmetric device drawing differently top and bottom — can only be asserted
 * here. `DeviceCornerTest` is where it is asserted, and it puts this back to null
 * in a `finally`.
 */
internal var deviceCornersOverride: DeviceCorners? = null
