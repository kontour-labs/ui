package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * How round the *display* is, where the platform will say.
 *
 * A receded screen sits inside the device's own corners, so a rounded panel
 * inside a rounded display reads as concentric or it reads as a mistake — one
 * radius nested inside a different one is the single most legible way to make an
 * inset look accidental. The library's own `extraLarge` is a guess at that
 * radius; this is the platform's answer where it has one.
 *
 * ### Null means unknown, and so does zero
 *
 * Only two platforms can answer, and even they cannot always. Null is the honest
 * result for "square, or not saying", and the caller floors it against the
 * library's own token — so a square display keeps exactly the corner it has
 * today and nothing is worse for the answer being absent.
 *
 * ### Composable rather than a `val`
 *
 * Android needs a `View` to reach the window, which is the same reason
 * [platformReportAppearance] is composable and not an eager constant. The two
 * platforms that can answer both need something from the composition to do it.
 *
 * | | |
 * |---|---|
 * | Android | `rootWindowInsets.getRoundedCorner(POSITION_TOP_LEFT)?.radius`, API 31+ |
 * | iOS | `_displayCornerRadius` on the screen, by key, inside a `runCatching` |
 * | Desktop, web | null — a window's corner belongs to the window manager |
 */
@Composable
internal expect fun platformDeviceCornerRadius(): Dp?
