package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * Null is the honest result for "square, or not saying", and the caller floors
 * its own shape against whatever comes back — so a display nobody can describe
 * keeps exactly the corner it has today and nothing is worse for the answer
 * being absent. [DeviceCorners.smoothing] is null in the same spirit, one level
 * down: the radii are known and the curve is not.
 *
 * ### Four corners, not one
 *
 * This read one corner — `POSITION_TOP_LEFT` — and threw the other three away,
 * on the reasoning that a device with differing corners is a device with a
 * camera housing in one of them. That is not the main case and the main case is
 * not rare. AOSP's own resources are a `_top` pair and a `_bottom` pair, and
 * some phones declare them differently: of the 27 devices in
 * `DeviceRadiusTable`, four do. The framework then **rotates the positions with
 * the window**, so on such a phone in landscape the window's top-left is the
 * device's physical bottom-left and a single reading floors the wrong corner
 * against the wrong number. Reading all four costs nothing and is simply right.
 *
 * ### Composable rather than a `val`
 *
 * Android needs a `View` to reach the window, which is the same reason
 * [platformReportAppearance] is composable and not an eager constant. The two
 * platforms that can answer both need something from the composition to do it.
 *
 * | | |
 * |---|---|
 * | Android | four `RoundedCorner` radii from API 31, a table below that, and a curated smoothing |
 * | iOS | `_displayCornerRadius` on the screen, by key, and the 0.6 the scale's own default came from |
 * | Desktop, web | null — a window's corner belongs to the window manager |
 */
@Composable
internal expect fun platformDeviceCorners(): DeviceCorners?

/**
 * A display's four corner radii, and how square-ish the curve between them is.
 *
 * **Physical, not logical.** These are the positions the platform reports, which
 * rotate with the window; mapping them onto a shape's start and end corners is
 * the shape's business and needs the layout direction, which is why
 * `CornerBasedShape.atLeast` takes one.
 *
 * @param smoothing How much of each corner's quarter turn is given over to the
 *   blends that make a squircle rather than an arc — the same number
 *   `SquircleShape` takes, in the same units. **Null means the radii are known
 *   and the curve is not**, which is the answer for almost every Android device:
 *   nothing below API 34 reports a display's shape, the API that does is backed
 *   by a resource essentially nobody sets, and a wrong curve is worse than the
 *   library's own. A null leaves the shape's own smoothing exactly as it was.
 */
@Immutable
internal data class DeviceCorners(
    val topLeft: Dp,
    val topRight: Dp,
    val bottomRight: Dp,
    val bottomLeft: Dp,
    val smoothing: Float? = null,
) {
    /** True when every corner is square, which is the same as knowing nothing. */
    val isSquare: Boolean
        get() = topLeft <= 0.dp && topRight <= 0.dp && bottomRight <= 0.dp && bottomLeft <= 0.dp

    companion object {
        /** The common case: one radius, four corners. */
        fun uniform(radius: Dp, smoothing: Float? = null): DeviceCorners =
            DeviceCorners(radius, radius, radius, radius, smoothing)
    }
}

/**
 * The generated radius table, read.
 *
 * Kept in commonMain although the data it parses is Android's, because a parser
 * is testable and a `Build.DEVICE` is not: `DeviceCornerTableTest` runs this on
 * the JVM against a fixture, and the Android actual is left with tier selection
 * and nothing worth a test source set. The table itself is one `const` string in
 * androidMain, so no other target carries the bytes.
 *
 * Records are `codename|topDp|bottomDp`, `;`-separated. A malformed record is
 * skipped rather than thrown on: this is device data scraped from a thousand
 * repositories by a script, and the failure mode for a bad row is the corner
 * nobody could describe anyway.
 *
 * @return the four radii, or null when the device is not listed. Left and right
 *   within a pair are always equal here, because AOSP's resources are a `_top`
 *   and a `_bottom` and not four numbers.
 */
internal fun deviceRadiiFrom(table: String, device: String): DeviceCorners? {
    if (device.isEmpty()) return null
    val key = device.lowercase()
    for (record in table.splitToSequence(';')) {
        val first = record.indexOf('|')
        if (first <= 0) continue
        if (!record.regionMatches(0, key, 0, first, ignoreCase = true)) continue
        if (first != key.length) continue
        val second = record.indexOf('|', first + 1)
        if (second <= first) continue
        val top = record.substring(first + 1, second).toIntOrNull() ?: continue
        val bottom = record.substring(second + 1).toIntOrNull() ?: continue
        if (top <= 0 || bottom <= 0) continue
        return DeviceCorners(top.dp, top.dp, bottom.dp, bottom.dp)
    }
    return null
}
