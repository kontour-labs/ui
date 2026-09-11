package io.kontour.ui.catalog

import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one line that decided whether a phone's text-size setting reached the app.
 *
 * Both hosts built their density as `Density(platform.density, settings.textScale)`.
 * `Density`'s second parameter is the font scale, so that expression takes the
 * platform's density and *discards its font scale* — the field Android and iOS
 * both use to carry the user's text-size choice. With `textScale` defaulting to
 * a plain `1f`, a phone set to 130% ran the gallery at exactly 100%.
 *
 * Four assertions, and the first is the whole report: Auto has to come out as
 * the device's scale rather than as 1.
 */
class HostDensityTest {

    /** A phone with its text size turned up, before the app has an opinion. */
    private val phoneAt130 = Density(density = 3f, fontScale = 1.3f)

    @Test
    fun autoTakesTheDevicesFontScale() {
        assertEquals(
            1.3f,
            hostDensity(phoneAt130, textScale = null).fontScale,
            "the reader has not picked a text size, so the device's 130% is what " +
                "should reach the type. Coming back as 1.0 is the defect the phone " +
                "reported: `Density(density, textScale)` reads the platform density " +
                "and drops its fontScale, and `textScale` used to default to 1f.",
        )
    }

    @Test
    fun aPickedScaleReplacesTheDevices() {
        // Absolute rather than a multiplier of the device's, on purpose: the
        // accessibility page promises the library copes at 200%, and that is
        // uncheckable if 200% on a 130% phone means 260%.
        assertEquals(
            2f,
            hostDensity(phoneAt130, textScale = 2f).fontScale,
            "picking 200% has to mean 200%, not 200% of whatever the phone says",
        )
    }

    @Test
    fun theDevicesPixelDensityIsNeverTouched() {
        // The other half of the same expression, and the half that was right.
        // `density` is px-per-dp and has nothing to do with type size; a host
        // that scaled it would resize the whole layout rather than the text.
        assertEquals(3f, hostDensity(phoneAt130, textScale = null).density)
        assertEquals(3f, hostDensity(phoneAt130, textScale = 2f).density)
    }

    @Test
    fun aDeviceWithNoOpinionIsUnchangedByAuto() {
        val plain = Density(density = 2f, fontScale = 1f)
        assertEquals(1f, hostDensity(plain, textScale = null).fontScale)
    }
}
