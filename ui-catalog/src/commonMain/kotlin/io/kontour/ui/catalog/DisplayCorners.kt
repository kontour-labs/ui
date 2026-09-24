package io.kontour.ui.catalog

import androidx.compose.runtime.Composable

/**
 * What the display says about its own corners, line by line, or null where the
 * platform has nothing to say.
 *
 * Here because a corner that looks wrong on one phone cannot be checked from a
 * desk. Reported from a Pixel 11 Pro XL as the sheet and the receding page
 * having corners too tight next to a Pixel 9's; the library now re-reads the
 * window's corners whenever it lays out, where it used to read them once on the
 * first frame, and this card shows the numbers it reads — the device's
 * codename, its density, and the radius the platform reports at each corner.
 * If a phone's corners still look wrong, this card is what to send back: the
 * codename and the radii are what a per-device override needs.
 *
 * Android only, where `WindowInsets.getRoundedCorner` exists. iOS's corner is a
 * private lookup the library makes itself, and the desktop and the web have no
 * corner to report.
 */
@Composable
internal expect fun platformCornerReadout(): List<String>?
